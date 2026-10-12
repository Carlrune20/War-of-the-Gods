// War of the Gods web map. Plain JavaScript, no build step: blocks.js has the block colours and shapes,
// render.js draws the map in 3D, and this file runs the page.
//
// The server sends JSON messages over a WebSocket at /ws:
//   {type: "game", game: {number, minX, minZ, maxX, maxZ, margin, tileSize, seaLevel} | null}
//   {type: "tiles", tiles: {"x,z": version}}   map tiles to (re)load from /api/tile, see MapTiles.java
//   {type: "state", teams: [...], players: [...]}
//   {type: "error", error, replyTo}
// The page sends {type: "hello"} once connected, and later commands as {type, id, ...}; see command().
"use strict";

(function () {
    const canvas = document.getElementById("map");
    const labels = document.getElementById("labels");
    const labelCtx = labels.getContext("2d");

    const state = {
        me: null,
        game: null,
        teams: [],
        teamsById: {},
        players: new Map(), // uuid -> {name, team, x, y, z, yaw, drawX, drawY, drawZ}
        tiles: new Map(), // "x,z" -> {version, loaded}
        tilesExpected: 0,
    };

    let view = null;
    let follow = false;
    let socket = null;
    let nextCommandId = 1;
    const pending = new Map();

    // ---------------------------------------------------------------- login

    async function login() {
        const params = new URLSearchParams(location.search);
        const token = params.get("token");
        let linkError = null;
        if (token) {
            history.replaceState(null, "", location.pathname);
            const response = await fetch("api/login", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({token}),
            });
            if (!response.ok) {
                linkError = await response.text();
            }
        }
        const response = await fetch("api/me");
        if (!response.ok) {
            // A used link is fine if this browser is still logged in from opening it before.
            showLogin(linkError || "You are not logged in.");
            return false;
        }
        state.me = await response.json();
        return true;
    }

    function showLogin(message) {
        document.getElementById("login-message").textContent = message;
        document.getElementById("login").hidden = false;
    }

    document.getElementById("logout").addEventListener("click", async () => {
        await fetch("api/logout", {method: "POST"});
        if (socket) {
            socket.onclose = null;
            socket.close();
        }
        showLogin("You have logged out.");
    });

    // ---------------------------------------------------------------- socket

    let retryDelay = 1000;

    function connect() {
        const scheme = location.protocol === "https:" ? "wss:" : "ws:";
        const base = location.pathname.replace(/[^/]*$/, "");
        socket = new WebSocket(scheme + "//" + location.host + base + "ws");
        socket.onopen = () => {
            retryDelay = 1000;
            setConnection(true);
            socket.send(JSON.stringify({type: "hello"}));
        };
        socket.onmessage = (event) => handle(JSON.parse(event.data));
        socket.onclose = () => {
            setConnection(false);
            for (const reject of pending.values()) {
                reject.reject(new Error("Disconnected"));
            }
            pending.clear();
            setTimeout(async () => {
                // The session may have expired or the server restarted, which logs everyone out.
                const response = await fetch("api/me").catch(() => null);
                if (response && response.status === 401) {
                    showLogin("Your login has expired. Get a new link with /rts web.");
                    return;
                }
                connect();
            }, retryDelay);
            retryDelay = Math.min(retryDelay * 2, 15000);
        };
    }

    function setConnection(ok) {
        const el = document.getElementById("connection");
        el.textContent = ok ? "Live" : "Reconnecting";
        el.className = ok ? "good" : "bad";
    }

    // Sends a command to the server; resolves with its reply. Unit orders will use this.
    function command(type, body) {
        const id = nextCommandId++;
        socket.send(JSON.stringify(Object.assign({}, body, {type, id})));
        return new Promise((resolve, reject) => pending.set(id, {resolve, reject}));
    }

    window.wotg = {command, state, get view() {
        return view;
    }};

    function handle(message) {
        if (message.replyTo !== undefined && pending.has(message.replyTo)) {
            const {resolve, reject} = pending.get(message.replyTo);
            pending.delete(message.replyTo);
            if (message.type === "error") {
                reject(new Error(message.error));
            } else {
                resolve(message);
            }
            return;
        }
        switch (message.type) {
            case "game":
                setGame(message.game);
                break;
            case "tiles":
                for (const [key, version] of Object.entries(message.tiles)) {
                    updateTile(key, version);
                }
                updateProgress();
                break;
            case "state":
                setState(message);
                break;
            case "error":
                console.warn("Server error:", message.error);
                break;
        }
    }

    // ---------------------------------------------------------------- game data

    function setGame(game) {
        const changed = !state.game || !game || state.game.number !== game.number;
        state.game = game;
        if (changed) {
            state.tiles.clear();
            tileQueue.length = 0;
            state.players.clear();
            view.setGame(game);
            if (game) {
                view.fitMap();
            }
        }
        if (game) {
            const size = game.tileSize;
            const tilesX = Math.floor((game.maxX + game.margin) / size) - Math.floor((game.minX - game.margin) / size) + 1;
            const tilesZ = Math.floor((game.maxZ + game.margin) / size) - Math.floor((game.minZ - game.margin) / size) + 1;
            state.tilesExpected = tilesX * tilesZ;
            view.game = game;
        }
        updateProgress();
        renderPanel();
    }

    // Tiles waiting to be fetched, and how many fetches are running.
    const tileQueue = [];
    let tileFetches = 0;
    const MAX_TILE_FETCHES = 4;

    function updateTile(key, version) {
        let tile = state.tiles.get(key);
        if (!tile) {
            tile = {version: -1, loaded: false};
            state.tiles.set(key, tile);
        }
        if (tile.version === version) {
            return;
        }
        tile.version = version;
        if (!tileQueue.includes(key)) {
            tileQueue.push(key);
        }
        pumpTiles();
    }

    function pumpTiles() {
        while (tileFetches < MAX_TILE_FETCHES && tileQueue.length) {
            const key = tileQueue.shift();
            const tile = state.tiles.get(key);
            if (!tile) {
                continue;
            }
            const version = tile.version;
            const gameNumber = state.game && state.game.number;
            const [x, z] = key.split(",").map(Number);
            tileFetches++;
            fetchTile(x, z, version).then((buffer) => {
                // A tile of an older game, or an older version finishing late, is dropped.
                if (buffer && state.game && state.game.number === gameNumber && tile.version === version) {
                    view.setTile(x, z, buffer);
                    tile.loaded = true;
                    updateProgress();
                }
            }).catch((error) => {
                console.warn("Map tile " + key + " failed:", error);
                setTimeout(() => updateTile(key, version), 3000);
                tile.version = -1;
            }).finally(() => {
                tileFetches--;
                pumpTiles();
            });
        }
    }

    async function fetchTile(x, z, version) {
        const response = await fetch("api/tile?x=" + x + "&z=" + z + "&v=" + version);
        if (response.status === 404) {
            return null;
        }
        if (!response.ok) {
            throw new Error("HTTP " + response.status);
        }
        const inflated = response.body.pipeThrough(new DecompressionStream("deflate"));
        return new Response(inflated).arrayBuffer();
    }

    function updateProgress() {
        const el = document.getElementById("progress");
        if (!state.game) {
            el.textContent = "No game running";
            return;
        }
        let loaded = 0;
        for (const tile of state.tiles.values()) {
            loaded += tile.loaded ? 1 : 0;
        }
        if (state.tiles.size < state.tilesExpected) {
            el.textContent = "Reading map " + Math.floor(100 * state.tiles.size / state.tilesExpected) + "%";
        } else if (loaded < state.tiles.size || view.pendingChunks) {
            el.textContent = "Loading map " + Math.floor(100 * loaded / Math.max(1, state.tiles.size)) + "%";
        } else {
            el.textContent = "Game #" + state.game.number;
        }
    }

    function setState(message) {
        state.teams = message.teams;
        state.teamsById = {};
        for (const team of message.teams) {
            state.teamsById[team.id] = team;
        }
        const seen = new Set();
        for (const p of message.players) {
            seen.add(p.uuid);
            const old = state.players.get(p.uuid);
            p.drawX = old ? old.drawX : p.x;
            p.drawY = old ? old.drawY : p.y;
            p.drawZ = old ? old.drawZ : p.z;
            state.players.set(p.uuid, p);
        }
        for (const uuid of [...state.players.keys()]) {
            if (!seen.has(uuid)) {
                state.players.delete(uuid);
            }
        }
        renderPanel();
        view.dirty = true;
    }

    function myTeam() {
        if (!state.me || state.me.observer) {
            return null;
        }
        return state.teams.find((t) => t.members.some((m) => m.uuid === state.me.uuid)) || null;
    }

    // ---------------------------------------------------------------- side panel

    const RESOURCES = [["food", "Food"], ["wood", "Wood"], ["stone", "Stone"], ["metal", "Metal"]];

    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) {
            node.className = className;
        }
        if (text !== undefined) {
            node.textContent = text;
        }
        return node;
    }

    function renderPanel() {
        const me = document.getElementById("me");
        me.replaceChildren();
        if (state.me) {
            const team = myTeam();
            me.append(el("div", "", state.me.name));
            me.append(el("div", "muted",
                state.me.observer ? "Observer" : team ? "Team " + team.name : "Not on a team"));
        }
        if (!state.game) {
            me.append(el("div", "muted", "No game is running."));
        }

        const teams = document.getElementById("teams");
        teams.replaceChildren();
        for (const team of state.teams) {
            const box = el("div", "team");
            const name = el("div", "team-name");
            const swatch = el("span", "swatch");
            swatch.style.background = team.color;
            name.append(swatch, document.createTextNode(team.name));
            box.append(name);

            const resources = el("div", "resources");
            for (const [id, label] of RESOURCES) {
                const cell = el("div");
                cell.append(el("span", "", label), document.createTextNode(String(team.resources[id])));
                resources.append(cell);
            }
            box.append(resources);

            if (team.allies.length) {
                const names = team.allies.map((id) => state.teamsById[id] ? state.teamsById[id].name : id);
                box.append(el("div", "muted", "Allied with " + names.join(", ")));
            }

            const members = el("ul", "members");
            if (!team.members.length) {
                members.append(el("li", "muted", "No players"));
            }
            for (const member of team.members) {
                const li = el("li");
                const online = state.players.has(member.uuid);
                li.append(el("span", online ? "dot online" : "dot"));
                if (online) {
                    const link = el("a", "", member.name);
                    link.href = "#";
                    link.addEventListener("click", (event) => {
                        event.preventDefault();
                        const p = state.players.get(member.uuid);
                        if (p) {
                            setFollow(false);
                            view.focus(p.x, p.y, p.z);
                            view.cam.zoom = Math.min(view.cam.zoom, 40);
                        }
                    });
                    li.append(link);
                } else {
                    li.append(document.createTextNode(member.name));
                }
                members.append(li);
            }
            box.append(members);
            teams.append(box);
        }
        if (!state.teams.length && state.game) {
            teams.append(el("div", "muted", "No teams."));
        }
    }

    const followButton = document.getElementById("follow");
    followButton.addEventListener("click", () => setFollow(!view.follow));

    function setFollow(on) {
        follow = Boolean(on && state.me && !state.me.observer);
        followButton.classList.toggle("on", follow);
        view.dirty = true;
    }

    document.getElementById("fit").addEventListener("click", () => {
        setFollow(false);
        view.fitMap();
    });

    const panel = document.getElementById("panel");
    const showPanel = document.getElementById("show-panel");
    document.getElementById("toggle-panel").addEventListener("click", () => {
        panel.hidden = true;
        showPanel.hidden = false;
    });
    showPanel.addEventListener("click", () => {
        panel.hidden = false;
        showPanel.hidden = true;
    });

    document.getElementById("turn-left").addEventListener("click", () => view.turn(-1));
    document.getElementById("turn-right").addEventListener("click", () => view.turn(1));
    const topButton = document.getElementById("top-view");
    topButton.addEventListener("click", () => {
        view.setTopView(!view.isTopView);
        topButton.classList.toggle("on", view.isTopView);
    });

    // ---------------------------------------------------------------- camera and input
    //
    // As in the map editor: drag with the left button (or one finger) to pan, drag with the right button to tilt
    // and turn, middle button pans too, the wheel or a pinch zooms, two fingers twisting turn the view,
    // WASD or the arrow keys pan and Q/E turn a quarter round.

    const pointers = new Map(); // pointerId -> {x, y, button}
    let pinch = null; // {distance, angle}

    canvas.addEventListener("contextmenu", (event) => event.preventDefault());

    canvas.addEventListener("pointerdown", (event) => {
        canvas.setPointerCapture(event.pointerId);
        pointers.set(event.pointerId, {x: event.offsetX, y: event.offsetY, button: event.button});
        canvas.classList.add("dragging");
        if (pointers.size === 2) {
            pinch = pinchState();
        }
    });

    canvas.addEventListener("pointermove", (event) => {
        updateCursor(event.offsetX, event.offsetY);
        const last = pointers.get(event.pointerId);
        if (!last) {
            return;
        }
        const point = {x: event.offsetX, y: event.offsetY, button: last.button};
        const dx = point.x - last.x;
        const dy = point.y - last.y;
        pointers.set(event.pointerId, point);
        if (pointers.size === 1) {
            if (last.button === 2) {
                view.orbit(dx, dy);
            } else if (dx || dy) {
                setFollow(false);
                view.pan(dx, dy);
            }
        } else if (pointers.size === 2 && pinch) {
            const now = pinchState();
            const [a, b] = [...pointers.values()];
            if (pinch.distance > 0) {
                view.zoomAt((a.x + b.x) / 2, (a.y + b.y) / 2, now.distance / pinch.distance);
            }
            view.orbit(-(now.angle - pinch.angle) / 0.008, 0);
            pinch = now;
        }
    });

    function endPointer(event) {
        pointers.delete(event.pointerId);
        pinch = pointers.size === 2 ? pinchState() : null;
        if (!pointers.size) {
            canvas.classList.remove("dragging");
        }
    }

    canvas.addEventListener("pointerup", endPointer);
    canvas.addEventListener("pointercancel", endPointer);

    function pinchState() {
        const [a, b] = [...pointers.values()];
        return {distance: Math.hypot(a.x - b.x, a.y - b.y), angle: Math.atan2(b.y - a.y, b.x - a.x)};
    }

    canvas.addEventListener("wheel", (event) => {
        event.preventDefault();
        view.zoomAt(event.offsetX, event.offsetY, Math.pow(1.0015, -event.deltaY));
    }, {passive: false});

    let cursorTimer = 0;

    function updateCursor(px, py) {
        // Picking walks the blocks under the pointer, so it runs at most every 50 ms.
        const now = performance.now();
        if (now - cursorTimer < 50) {
            return;
        }
        cursorTimer = now;
        const hit = view.pick(px, py);
        document.getElementById("cursor").textContent = hit ? hit.x + ", " + hit.y + ", " + hit.z : "";
    }

    const keys = new Set();
    const PAN_KEYS = ["w", "a", "s", "d", "arrowup", "arrowdown", "arrowleft", "arrowright"];

    window.addEventListener("keydown", (event) => {
        if (event.target instanceof HTMLInputElement || event.ctrlKey || event.metaKey || event.altKey) {
            return;
        }
        const key = event.key.toLowerCase();
        if (PAN_KEYS.includes(key)) {
            keys.add(key);
            setFollow(false);
            event.preventDefault();
        } else if (key === "q") {
            view.turn(-1);
        } else if (key === "e") {
            view.turn(1);
        }
    });

    window.addEventListener("keyup", (event) => keys.delete(event.key.toLowerCase()));
    window.addEventListener("blur", () => keys.clear());

    function panWithKeys(dt) {
        let right = 0;
        let forward = 0;
        if (keys.has("w") || keys.has("arrowup")) forward += 1;
        if (keys.has("s") || keys.has("arrowdown")) forward -= 1;
        if (keys.has("d") || keys.has("arrowright")) right += 1;
        if (keys.has("a") || keys.has("arrowleft")) right -= 1;
        if (right || forward) {
            const speed = view.cam.zoom * 1.6 * dt;
            view.move(right * speed, forward * speed);
        }
    }

    window.addEventListener("resize", () => {
        if (view) {
            view.dirty = true;
        }
    });

    // ---------------------------------------------------------------- drawing

    function hexColor(hex, alpha) {
        const n = parseInt(hex.slice(1), 16);
        return [(n >> 16 & 255) / 255, (n >> 8 & 255) / 255, (n & 255) / 255, alpha];
    }

    const SKIN = [0.94, 0.76, 0.6, 1];

    // Players as a body in their team's colour with a head on top, drawn larger when zoomed out so they stay
    // visible.
    function playerBoxes() {
        const boxes = [];
        const s = Math.max(1, view.cam.zoom / 40);
        for (const p of state.players.values()) {
            const team = p.team ? state.teamsById[p.team] : null;
            const color = team ? hexColor(team.color, 1) : [0.8, 0.8, 0.8, 1];
            const w = 0.6 * s;
            boxes.push({min: [p.drawX - w / 2, p.drawY, p.drawZ - w / 2], size: [w, 1.35 * s, w], color});
            const hw = 0.5 * s;
            boxes.push({min: [p.drawX - hw / 2, p.drawY + 1.35 * s, p.drawZ - hw / 2], size: [hw, hw, hw], color: SKIN});
        }
        return boxes;
    }

    function borderLines() {
        const g = state.game;
        if (!g) {
            return [];
        }
        const y = g.seaLevel + 1.5;
        const red = [1, 0.32, 0.24, 0.9];
        const corners = [[g.minX, g.minZ], [g.maxX + 1, g.minZ], [g.maxX + 1, g.maxZ + 1], [g.minX, g.maxZ + 1]];
        const lines = [];
        for (let i = 0; i < 4; i++) {
            const [ax, az] = corners[i];
            const [bx, bz] = corners[(i + 1) % 4];
            lines.push([[ax, y, az], [bx, y, bz], red]);
            lines.push([[ax, y, az], [ax, y + 24, az], red]);
        }
        return lines;
    }

    function drawLabels() {
        const ratio = window.devicePixelRatio || 1;
        const width = labels.clientWidth;
        const height = labels.clientHeight;
        if (labels.width !== Math.round(width * ratio) || labels.height !== Math.round(height * ratio)) {
            labels.width = Math.round(width * ratio);
            labels.height = Math.round(height * ratio);
        }
        labelCtx.setTransform(ratio, 0, 0, ratio, 0, 0);
        labelCtx.clearRect(0, 0, width, height);
        labelCtx.font = "600 12px system-ui, sans-serif";
        labelCtx.textAlign = "center";
        const s = Math.max(1, view.cam.zoom / 40);
        for (const p of state.players.values()) {
            const [x, y] = view.project(p.drawX, p.drawY + 1.85 * s, p.drawZ);
            const isMe = state.me && p.uuid === state.me.uuid;
            labelCtx.lineWidth = 3;
            labelCtx.strokeStyle = "rgba(0, 0, 0, 0.8)";
            labelCtx.strokeText(p.name, x, y - 6);
            labelCtx.fillStyle = isMe ? "#ffe9a8" : "#ffffff";
            labelCtx.fillText(p.name, x, y - 6);
        }
    }

    let lastFrame = performance.now();
    let progressTimer = 0;

    function frame(now) {
        const dt = Math.min(0.1, (now - lastFrame) / 1000);
        lastFrame = now;
        panWithKeys(dt);
        view.animate(dt);
        if (view.meshSome() && now - progressTimer > 250) {
            progressTimer = now;
            updateProgress();
        } else if (!view.pendingChunks && now - progressTimer > 1000) {
            progressTimer = now;
            updateProgress();
        }
        // Players glide toward their latest reported position between updates.
        for (const p of state.players.values()) {
            const dx = p.x - p.drawX;
            const dy = p.y - p.drawY;
            const dz = p.z - p.drawZ;
            if (Math.abs(dx) > 0.01 || Math.abs(dy) > 0.01 || Math.abs(dz) > 0.01) {
                const jump = Math.abs(dx) > 64 || Math.abs(dz) > 64;
                p.drawX = jump ? p.x : p.drawX + dx * 0.2;
                p.drawY = jump ? p.y : p.drawY + dy * 0.2;
                p.drawZ = jump ? p.z : p.drawZ + dz * 0.2;
                view.dirty = true;
            }
        }
        if (follow && state.me) {
            const me = state.players.get(state.me.uuid);
            const t = view.cam.target;
            if (me && (t[0] !== me.drawX || t[2] !== me.drawZ)) {
                view.focus(me.drawX, me.drawY, me.drawZ);
            }
        }
        if (view.dirty && state.game) {
            view.draw(playerBoxes(), borderLines());
            drawLabels();
        } else if (view.dirty) {
            view.draw([], []);
            drawLabels();
        }
        requestAnimationFrame(frame);
    }

    // ---------------------------------------------------------------- start

    if (window.matchMedia("(max-width: 600px)").matches) {
        panel.hidden = true;
        showPanel.hidden = false;
    }

    function start() {
        if (typeof DecompressionStream === "undefined") {
            throw new Error("This browser is too old for the 3D map. Update it, or try another browser.");
        }
        view = new window.WotgRender.MapView(canvas);
    }

    try {
        start();
    } catch (error) {
        showLogin(error.message);
        return;
    }

    login().then((ok) => {
        if (ok) {
            renderPanel();
            setFollow(false);
            connect();
            requestAnimationFrame(frame);
        }
    }).catch((error) => showLogin("Could not reach the server: " + error.message));
})();
