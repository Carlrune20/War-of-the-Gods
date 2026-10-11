// War of the Gods web map. Plain JavaScript, no build step.
//
// The server sends JSON messages over a WebSocket at /ws:
//   {type: "game", game: {number, minX, minZ, maxX, maxZ, margin, tileSize} | null}
//   {type: "tiles", tiles: {"x,z": version}}   map tiles to (re)load from /api/tile
//   {type: "state", teams: [...], players: [...]}
//   {type: "error", error, replyTo}
// The page sends {type: "hello"} once connected, and later commands as {type, id, ...}; see command().
"use strict";

(function () {
    const canvas = document.getElementById("map");
    const ctx = canvas.getContext("2d");

    const state = {
        me: null,
        game: null,
        teams: [],
        teamsById: {},
        players: new Map(), // uuid -> {name, team, x, z, yaw, drawX, drawZ}
        tiles: new Map(), // "x,z" -> {version, image, loading}
        tilesExpected: 0,
    };

    // Camera: the block at the middle of the screen and screen pixels per block.
    const view = {x: 0, z: 0, scale: 1, follow: false, placed: false};
    const MIN_SCALE = 1 / 8;
    const MAX_SCALE = 16;

    let dirty = true;
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

    // Sends a command to the server; resolves with its reply. Later phases use this for unit orders.
    function command(type, body) {
        const id = nextCommandId++;
        socket.send(JSON.stringify(Object.assign({}, body, {type, id})));
        return new Promise((resolve, reject) => pending.set(id, {resolve, reject}));
    }

    window.wotg = {command, state, view};

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
            state.players.clear();
            view.placed = false;
        }
        if (game) {
            const size = game.tileSize;
            const tilesX = Math.floor((game.maxX + game.margin) / size) - Math.floor((game.minX - game.margin) / size) + 1;
            const tilesZ = Math.floor((game.maxZ + game.margin) / size) - Math.floor((game.minZ - game.margin) / size) + 1;
            state.tilesExpected = tilesX * tilesZ;
            if (!view.placed) {
                fitMap();
                view.placed = true;
            }
        }
        updateProgress();
        renderPanel();
        dirty = true;
    }

    function updateTile(key, version) {
        let tile = state.tiles.get(key);
        if (!tile) {
            tile = {version: -1, image: null};
            state.tiles.set(key, tile);
        }
        if (tile.version === version) {
            return;
        }
        tile.version = version;
        const [x, z] = key.split(",");
        const image = new Image();
        image.onload = () => {
            // An older request finishing late must not replace a newer image.
            if (tile.version === version) {
                tile.image = image;
                dirty = true;
            }
        };
        image.src = "api/tile?x=" + x + "&z=" + z + "&v=" + version;
    }

    function updateProgress() {
        const el = document.getElementById("progress");
        if (!state.game) {
            el.textContent = "No game running";
            return;
        }
        const drawn = state.tiles.size;
        el.textContent = drawn < state.tilesExpected
            ? "Drawing map " + Math.floor(100 * drawn / state.tilesExpected) + "%"
            : "Game #" + state.game.number;
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
            p.drawZ = old ? old.drawZ : p.z;
            state.players.set(p.uuid, p);
        }
        for (const uuid of [...state.players.keys()]) {
            if (!seen.has(uuid)) {
                state.players.delete(uuid);
            }
        }
        renderPanel();
        dirty = true;
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
                            view.x = p.x;
                            view.z = p.z;
                            view.scale = Math.max(view.scale, 2);
                            dirty = true;
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
        view.follow = on && state.me && !state.me.observer;
        followButton.classList.toggle("on", view.follow);
        dirty = true;
    }

    document.getElementById("fit").addEventListener("click", () => {
        setFollow(false);
        fitMap();
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

    // ---------------------------------------------------------------- camera and input

    function fitMap() {
        if (!state.game) {
            return;
        }
        const g = state.game;
        const width = g.maxX - g.minX + 1 + 2 * g.margin;
        const height = g.maxZ - g.minZ + 1 + 2 * g.margin;
        view.x = (g.minX + g.maxX + 1) / 2;
        view.z = (g.minZ + g.maxZ + 1) / 2;
        const scale = Math.min(canvas.clientWidth / width, canvas.clientHeight / height) * 0.95;
        view.scale = clampScale(scale);
        dirty = true;
    }

    function clampScale(scale) {
        return Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale));
    }

    function toWorld(px, py) {
        return {
            x: view.x + (px - canvas.clientWidth / 2) / view.scale,
            z: view.z + (py - canvas.clientHeight / 2) / view.scale,
        };
    }

    function zoomAt(px, py, factor) {
        const before = toWorld(px, py);
        view.scale = clampScale(view.scale * factor);
        const after = toWorld(px, py);
        view.x += before.x - after.x;
        view.z += before.z - after.z;
        dirty = true;
    }

    const pointers = new Map();
    let pinchDistance = 0;

    canvas.addEventListener("pointerdown", (event) => {
        canvas.setPointerCapture(event.pointerId);
        pointers.set(event.pointerId, {x: event.offsetX, y: event.offsetY});
        canvas.classList.add("dragging");
        if (pointers.size === 2) {
            pinchDistance = distanceBetweenPointers();
        }
    });

    canvas.addEventListener("pointermove", (event) => {
        const world = toWorld(event.offsetX, event.offsetY);
        document.getElementById("cursor").textContent = Math.floor(world.x) + ", " + Math.floor(world.z);
        const last = pointers.get(event.pointerId);
        if (!last) {
            return;
        }
        const point = {x: event.offsetX, y: event.offsetY};
        if (pointers.size === 1) {
            if (view.follow && (point.x !== last.x || point.y !== last.y)) {
                setFollow(false);
            }
            view.x -= (point.x - last.x) / view.scale;
            view.z -= (point.y - last.y) / view.scale;
            dirty = true;
        }
        pointers.set(event.pointerId, point);
        if (pointers.size === 2) {
            const distance = distanceBetweenPointers();
            if (pinchDistance > 0) {
                const [a, b] = [...pointers.values()];
                zoomAt((a.x + b.x) / 2, (a.y + b.y) / 2, distance / pinchDistance);
            }
            pinchDistance = distance;
        }
    });

    function endPointer(event) {
        pointers.delete(event.pointerId);
        pinchDistance = pointers.size === 2 ? distanceBetweenPointers() : 0;
        if (!pointers.size) {
            canvas.classList.remove("dragging");
        }
    }

    canvas.addEventListener("pointerup", endPointer);
    canvas.addEventListener("pointercancel", endPointer);

    function distanceBetweenPointers() {
        const [a, b] = [...pointers.values()];
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    canvas.addEventListener("wheel", (event) => {
        event.preventDefault();
        zoomAt(event.offsetX, event.offsetY, Math.pow(1.0015, -event.deltaY));
    }, {passive: false});

    window.addEventListener("resize", () => {
        dirty = true;
    });

    // ---------------------------------------------------------------- drawing

    function resizeCanvas() {
        const ratio = window.devicePixelRatio || 1;
        const width = Math.round(canvas.clientWidth * ratio);
        const height = Math.round(canvas.clientHeight * ratio);
        if (canvas.width !== width || canvas.height !== height) {
            canvas.width = width;
            canvas.height = height;
        }
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    }

    function frame() {
        // Players glide toward their latest reported position between updates.
        for (const p of state.players.values()) {
            const dx = p.x - p.drawX;
            const dz = p.z - p.drawZ;
            if (Math.abs(dx) > 0.01 || Math.abs(dz) > 0.01) {
                const jump = Math.abs(dx) > 64 || Math.abs(dz) > 64;
                p.drawX = jump ? p.x : p.drawX + dx * 0.2;
                p.drawZ = jump ? p.z : p.drawZ + dz * 0.2;
                dirty = true;
            }
        }
        if (view.follow && state.me) {
            const me = state.players.get(state.me.uuid);
            if (me && (view.x !== me.drawX || view.z !== me.drawZ)) {
                view.x = me.drawX;
                view.z = me.drawZ;
                dirty = true;
            }
        }
        if (dirty) {
            dirty = false;
            draw();
        }
        requestAnimationFrame(frame);
    }

    function screenX(x) {
        return (x - view.x) * view.scale + canvas.clientWidth / 2;
    }

    function screenY(z) {
        return (z - view.z) * view.scale + canvas.clientHeight / 2;
    }

    function draw() {
        resizeCanvas();
        const width = canvas.clientWidth;
        const height = canvas.clientHeight;
        ctx.fillStyle = "#1d3a5c";
        ctx.fillRect(0, 0, width, height);
        if (!state.game) {
            return;
        }
        drawTiles(width, height);
        drawBorder();
        drawPlayers();
    }

    function drawTiles(width, height) {
        const size = state.game.tileSize;
        ctx.imageSmoothingEnabled = view.scale < 1;
        const topLeft = toWorld(0, 0);
        const bottomRight = toWorld(width, height);
        const minTx = Math.floor(topLeft.x / size);
        const maxTx = Math.floor(bottomRight.x / size);
        const minTz = Math.floor(topLeft.z / size);
        const maxTz = Math.floor(bottomRight.z / size);
        const pixels = size * view.scale;
        for (let tz = minTz; tz <= maxTz; tz++) {
            for (let tx = minTx; tx <= maxTx; tx++) {
                const tile = state.tiles.get(tx + "," + tz);
                if (tile && tile.image) {
                    // Round edges outward so neighbouring tiles leave no hairline gaps.
                    const x = Math.floor(screenX(tx * size));
                    const y = Math.floor(screenY(tz * size));
                    ctx.drawImage(tile.image, x, y, Math.ceil(pixels) + 1, Math.ceil(pixels) + 1);
                }
            }
        }
    }

    function drawBorder() {
        const g = state.game;
        ctx.strokeStyle = "rgba(255, 80, 60, 0.85)";
        ctx.lineWidth = 2;
        ctx.setLineDash([8, 6]);
        ctx.strokeRect(screenX(g.minX), screenY(g.minZ),
            (g.maxX - g.minX + 1) * view.scale, (g.maxZ - g.minZ + 1) * view.scale);
        ctx.setLineDash([]);
    }

    function drawPlayers() {
        ctx.font = "600 12px system-ui, sans-serif";
        ctx.textAlign = "center";
        for (const p of state.players.values()) {
            const team = p.team ? state.teamsById[p.team] : null;
            const color = team ? team.color : "#cccccc";
            const x = screenX(p.drawX);
            const y = screenY(p.drawZ);
            const isMe = state.me && p.uuid === state.me.uuid;
            const radius = isMe ? 7 : 6;

            // Facing: Minecraft yaw 0 looks toward +z (down on the map), increasing clockwise seen from above.
            const angle = (p.yaw * Math.PI) / 180;
            const fx = -Math.sin(angle);
            const fz = Math.cos(angle);
            ctx.beginPath();
            ctx.moveTo(x + fx * (radius + 7), y + fz * (radius + 7));
            ctx.lineTo(x - fz * radius * 0.8, y + fx * radius * 0.8);
            ctx.lineTo(x + fz * radius * 0.8, y - fx * radius * 0.8);
            ctx.closePath();
            ctx.fillStyle = color;
            ctx.fill();

            ctx.beginPath();
            ctx.arc(x, y, radius, 0, Math.PI * 2);
            ctx.fillStyle = color;
            ctx.fill();
            ctx.lineWidth = isMe ? 3 : 2;
            ctx.strokeStyle = isMe ? "#ffffff" : "rgba(0, 0, 0, 0.7)";
            ctx.stroke();

            ctx.lineWidth = 3;
            ctx.strokeStyle = "rgba(0, 0, 0, 0.8)";
            ctx.strokeText(p.name, x, y - radius - 6);
            ctx.fillStyle = "#ffffff";
            ctx.fillText(p.name, x, y - radius - 6);
        }
    }

    // ---------------------------------------------------------------- start

    if (window.matchMedia("(max-width: 600px)").matches) {
        panel.hidden = true;
        showPanel.hidden = false;
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
