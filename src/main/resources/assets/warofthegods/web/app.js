// War of the Gods web map. Plain JavaScript, no build step: blocks.js has the block colours and shapes,
// render.js draws the map in 3D, and this file runs the page.
//
// The server sends JSON messages over a WebSocket at /ws:
//   {type: "game", game: {number, minX, minZ, maxX, maxZ, margin, tileSize, seaLevel, unitCost, unitsPerTeam} | null}
//   {type: "tiles", tiles: {"x,z": version}}   map tiles to (re)load from /api/tile, see MapTiles.java
//   {type: "state", teams: [...], players: [...], units: [...]}
//   {type: "error", error, replyTo}
// The page sends {type: "hello"} once connected, and commands such as unit orders as {type, id, ...};
// see command() and UnitCommands.java.
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
        units: new Map(), // id -> {team, number, x, y, z, health, maxHealth, order, toX, toZ, target, drawX...}
        selected: new Set(), // ids of the viewer's units that orders go to
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

    // Sends a command to the server; resolves with its reply, or rejects with the error it gave.
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
            state.units.clear();
            state.selected.clear();
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
        renderUnitBar();
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
        const seenUnits = new Set();
        for (const u of message.units || []) {
            seenUnits.add(u.id);
            const old = state.units.get(u.id);
            u.drawX = old ? old.drawX : u.x;
            u.drawY = old ? old.drawY : u.y;
            u.drawZ = old ? old.drawZ : u.z;
            state.units.set(u.id, u);
        }
        for (const id of [...state.units.keys()]) {
            if (!seenUnits.has(id)) {
                state.units.delete(id);
            }
        }
        // Dead units, and units of a team the viewer has left, drop out of the selection.
        const team = myTeam();
        for (const id of [...state.selected]) {
            const u = state.units.get(id);
            if (!u || !team || u.team !== team.id) {
                state.selected.delete(id);
            }
        }
        renderPanel();
        renderUnitBar();
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

            if (state.game && team.units !== undefined) {
                const units = el("div", "muted", "Units " + team.units + " of " + state.game.unitsPerTeam);
                if (team.base) {
                    const link = el("a", "", "base");
                    link.href = "#";
                    link.addEventListener("click", (event) => {
                        event.preventDefault();
                        setFollow(false);
                        view.focus(team.base.x + 0.5, surfaceY(team.base.x, team.base.z), team.base.z + 0.5);
                        view.cam.zoom = Math.min(view.cam.zoom, 40);
                    });
                    units.append(document.createTextNode(", "), link);
                }
                box.append(units);
            }

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

    // ---------------------------------------------------------------- units
    //
    // Like an RTS: click one of your units to select it (shift-click adds), shift-drag a box around several, or
    // double-click one to select all of yours in view. Right-click the ground to send them there, or an enemy to
    // attack it. On a touch screen, tap your units to select them, then tap the ground or an enemy to give the
    // order; the Box button makes the next drag select instead of pan.

    const unitBar = document.getElementById("unit-bar");
    const trainButton = document.getElementById("train");
    const boxButton = document.getElementById("box-select");
    const toast = document.getElementById("toast");
    let boxMode = false;
    let selectionBox = null; // {x0, y0, x1, y1} in CSS pixels while dragging one
    let orderMark = null; // {x, y, z, color, time} where the last order went
    let lastClick = {time: 0, id: null};

    function costText() {
        const cost = state.game && state.game.unitCost ? state.game.unitCost : {};
        const parts = RESOURCES.filter(([id]) => cost[id]).map(([id, label]) => cost[id] + " " + label.toLowerCase());
        return parts.length ? parts.join(", ") : "free";
    }

    function canAfford(team) {
        const cost = state.game && state.game.unitCost ? state.game.unitCost : {};
        return RESOURCES.every(([id]) => !cost[id] || team.resources[id] >= cost[id]);
    }

    function renderUnitBar() {
        const team = myTeam();
        unitBar.hidden = !(team && state.game);
        if (unitBar.hidden) {
            return;
        }
        const full = team.units >= state.game.unitsPerTeam;
        trainButton.textContent = "Train unit (" + costText() + ")";
        trainButton.disabled = full || !canAfford(team);
        trainButton.title = full ? "Your team has the most units it can have" : "Shift-click to train 5";
        const count = state.selected.size;
        document.getElementById("selection-info").textContent =
            count ? count + " selected" : team.units ? "None selected" : "No units yet";
        document.getElementById("stop-units").disabled = !count;
        document.getElementById("clear-selection").disabled = !count;
        boxButton.classList.toggle("on", boxMode);
    }

    let toastTimer = 0;

    function showToast(text, bad) {
        toast.textContent = text;
        toast.className = bad ? "bad" : "";
        toast.hidden = false;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => {
            toast.hidden = true;
        }, 3000);
    }

    function send(type, body) {
        return command(type, body).catch((error) => {
            showToast(error.message, true);
            return null;
        });
    }

    trainButton.addEventListener("click", async (event) => {
        const reply = await send("units.train", {count: event.shiftKey ? 5 : 1});
        if (reply) {
            showToast(reply.count === 1 ? "Trained a unit" : "Trained " + reply.count + " units");
        }
    });

    document.getElementById("select-all").addEventListener("click", () => {
        const team = myTeam();
        state.selected.clear();
        for (const u of state.units.values()) {
            if (team && u.team === team.id) {
                state.selected.add(u.id);
            }
        }
        changedSelection();
    });

    document.getElementById("stop-units").addEventListener("click", () => {
        if (state.selected.size) {
            send("units.stop", {units: [...state.selected]});
        }
    });

    document.getElementById("clear-selection").addEventListener("click", () => {
        state.selected.clear();
        changedSelection();
    });

    boxButton.addEventListener("click", () => {
        boxMode = !boxMode;
        renderUnitBar();
    });

    function changedSelection() {
        renderUnitBar();
        view.dirty = true;
    }

    function isMine(u) {
        const team = myTeam();
        return Boolean(team && u.team === team.id);
    }

    function isEnemyTeam(teamId) {
        const team = myTeam();
        if (!team || !teamId) {
            return false;
        }
        return teamId !== team.id && !team.allies.includes(teamId);
    }

    // How big a unit or player is drawn, so they stay visible when zoomed out.
    function figureScale() {
        return Math.max(1, view.cam.zoom / 40);
    }

    // The unit or player under a screen point: {kind: "unit"|"player", thing}, or null.
    function entityAt(px, py) {
        const s = figureScale();
        const pixelsPerBlock = canvas.clientHeight / (2 * view.cam.zoom);
        const radius = Math.max(12, 0.9 * s * pixelsPerBlock);
        let best = null;
        let bestDistance = radius;
        const consider = (kind, thing) => {
            const [x, y] = view.project(thing.drawX, thing.drawY + 0.9 * s, thing.drawZ);
            const distance = Math.hypot(x - px, y - py);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = {kind, thing};
            }
        };
        for (const u of state.units.values()) {
            consider("unit", u);
        }
        for (const p of state.players.values()) {
            consider("player", p);
        }
        return best;
    }

    function entityId(hit) {
        return hit.kind === "unit" ? hit.thing.id : hit.thing.uuid;
    }

    function isEnemy(hit) {
        if (hit.kind === "player" && state.me && hit.thing.uuid === state.me.uuid) {
            return false;
        }
        return isEnemyTeam(hit.thing.team);
    }

    // The ground under a screen point, or null.
    function groundUnder(px, py) {
        const hit = view.pick(px, py);
        if (hit) {
            return [hit.x + 0.5, hit.y + 1, hit.z + 0.5];
        }
        return state.game ? view.groundAt(px, py, state.game.seaLevel + 1) : null;
    }

    // Selects or orders for a click (or tap) that did not drag.
    function click(px, py, button, shift, touch) {
        if (!myTeam()) {
            return;
        }
        const hit = entityAt(px, py);
        const ordering = button === 2 || (touch && state.selected.size && !(hit && hit.kind === "unit" && isMine(hit.thing)));
        if (ordering) {
            if (state.selected.size) {
                order(px, py, hit);
            }
            return;
        }
        if (button !== 0) {
            return;
        }
        if (hit && hit.kind === "unit" && isMine(hit.thing)) {
            const id = hit.thing.id;
            const now = performance.now();
            if (lastClick.id === id && now - lastClick.time < 400) {
                selectInView();
            } else if (shift || touch) {
                if (state.selected.has(id)) {
                    state.selected.delete(id);
                } else {
                    state.selected.add(id);
                }
            } else {
                state.selected.clear();
                state.selected.add(id);
            }
            lastClick = {time: now, id};
            changedSelection();
        } else if (!shift && !touch && state.selected.size) {
            state.selected.clear();
            changedSelection();
        }
    }

    function order(px, py, hit) {
        const units = [...state.selected];
        if (hit && isEnemy(hit)) {
            const t = hit.thing;
            orderMark = {x: t.drawX, y: t.drawY, z: t.drawZ, color: [1, 0.3, 0.25, 1], time: performance.now()};
            send("units.attack", {units, target: entityId(hit)});
        } else {
            const ground = groundUnder(px, py);
            if (!ground) {
                return;
            }
            orderMark = {x: ground[0], y: ground[1], z: ground[2], color: [0.45, 1, 0.5, 1], time: performance.now()};
            send("units.move", {units, x: ground[0], z: ground[2]});
        }
        view.dirty = true;
    }

    function selectInView() {
        const width = canvas.clientWidth;
        const height = canvas.clientHeight;
        selectIn(0, 0, width, height, false);
    }

    // Selects the viewer's units drawn inside a screen rectangle; adds to the selection if asked.
    function selectIn(x0, y0, x1, y1, add) {
        const [left, right] = [Math.min(x0, x1), Math.max(x0, x1)];
        const [top, bottom] = [Math.min(y0, y1), Math.max(y0, y1)];
        if (!add) {
            state.selected.clear();
        }
        const s = figureScale();
        for (const u of state.units.values()) {
            if (!isMine(u)) {
                continue;
            }
            const [x, y] = view.project(u.drawX, u.drawY + 0.9 * s, u.drawZ);
            if (x >= left && x <= right && y >= top && y <= bottom) {
                state.selected.add(u.id);
            }
        }
        changedSelection();
    }

    // ---------------------------------------------------------------- camera and input
    //
    // As in the map editor: drag with the left button (or one finger) to pan, drag with the right button to tilt
    // and turn, middle button pans too, the wheel or a pinch zooms, two fingers twisting turn the view,
    // WASD or the arrow keys pan and Q/E turn a quarter round. A click that does not drag selects or orders units.

    const pointers = new Map(); // pointerId -> {x, y, button, startX, startY, moved, box}
    let pinch = null; // {distance, angle}
    let multiTouch = false; // a second finger came down since the first, so lifting is not a tap

    canvas.addEventListener("contextmenu", (event) => event.preventDefault());

    canvas.addEventListener("pointerdown", (event) => {
        canvas.setPointerCapture(event.pointerId);
        const point = {
            x: event.offsetX,
            y: event.offsetY,
            button: event.button,
            startX: event.offsetX,
            startY: event.offsetY,
            moved: false,
            box: false,
        };
        if (!pointers.size) {
            multiTouch = false;
            point.box = event.button === 0 && (event.shiftKey || boxMode) && Boolean(myTeam());
        }
        pointers.set(event.pointerId, point);
        canvas.classList.add("dragging");
        if (pointers.size === 2) {
            multiTouch = true;
            selectionBox = null;
            pinch = pinchState();
        }
    });

    canvas.addEventListener("pointermove", (event) => {
        updateCursor(event.offsetX, event.offsetY);
        const last = pointers.get(event.pointerId);
        if (!last) {
            return;
        }
        const point = Object.assign({}, last, {x: event.offsetX, y: event.offsetY});
        point.moved = last.moved || Math.hypot(point.x - last.startX, point.y - last.startY) > 6;
        const dx = point.x - last.x;
        const dy = point.y - last.y;
        pointers.set(event.pointerId, point);
        if (pointers.size === 1 && point.box) {
            selectionBox = point.moved ? {x0: point.startX, y0: point.startY, x1: point.x, y1: point.y} : null;
            view.dirty = true;
        } else if (pointers.size === 1) {
            if (last.button === 2) {
                if (point.moved) {
                    view.orbit(dx, dy);
                }
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
        const point = pointers.get(event.pointerId);
        pointers.delete(event.pointerId);
        pinch = pointers.size === 2 ? pinchState() : null;
        if (!pointers.size) {
            canvas.classList.remove("dragging");
        }
        if (!point || event.type === "pointercancel" || multiTouch) {
            selectionBox = null;
            return;
        }
        if (point.box && point.moved) {
            selectIn(point.startX, point.startY, point.x, point.y, event.shiftKey);
            selectionBox = null;
            if (boxMode) {
                boxMode = false;
                renderUnitBar();
            }
        } else if (!point.moved) {
            click(point.x, point.y, point.button, event.shiftKey, event.pointerType === "touch");
        }
        selectionBox = null;
        view.dirty = true;
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
        } else if (key === "escape" && state.selected.size) {
            state.selected.clear();
            changedSelection();
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
    const STEEL = [0.62, 0.65, 0.7, 1];

    function teamColor(teamId) {
        const team = teamId ? state.teamsById[teamId] : null;
        return team ? hexColor(team.color, 1) : [0.8, 0.8, 0.8, 1];
    }

    // Players as a body in their team's colour with a bare head on top; units the same with a steel helmet, so
    // the two tell apart. Both are drawn larger when zoomed out so they stay visible.
    function figureBoxes() {
        const boxes = [];
        const s = figureScale();
        const figure = (x, y, z, color, head) => {
            const w = 0.6 * s;
            boxes.push({min: [x - w / 2, y, z - w / 2], size: [w, 1.35 * s, w], color});
            const hw = 0.5 * s;
            boxes.push({min: [x - hw / 2, y + 1.35 * s, z - hw / 2], size: [hw, hw, hw], color: head});
        };
        for (const p of state.players.values()) {
            figure(p.drawX, p.drawY, p.drawZ, teamColor(p.team), SKIN);
        }
        for (const u of state.units.values()) {
            figure(u.drawX, u.drawY, u.drawZ, teamColor(u.team), STEEL);
        }
        for (const team of state.teams) {
            if (team.base) {
                // A flag on the pole baseLines() draws.
                const y = surfaceY(team.base.x, team.base.z) + 9 * s;
                boxes.push({min: [team.base.x + 0.5, y, team.base.z + 0.45], size: [3 * s, 2 * s, 0.1], color: hexColor(team.color, 1)});
            }
        }
        return boxes;
    }

    // The y just above the highest block at x, z, or above sea level where the map is not loaded.
    function surfaceY(x, z) {
        const map = view.map;
        if (map.chunk(x >> 4, z >> 4)) {
            for (let y = 255; y > 0; y--) {
                const value = map.block(x, y, z);
                if (value && !window.WotgBlocks.isInvisible(window.WotgBlocks.index(value >> 4))) {
                    return y + 1;
                }
            }
        }
        return state.game ? state.game.seaLevel + 1 : 64;
    }

    function ring(lines, x, y, z, radius, color) {
        const segments = 16;
        for (let i = 0; i < segments; i++) {
            const a = 2 * Math.PI * i / segments;
            const b = 2 * Math.PI * (i + 1) / segments;
            lines.push([[x + Math.cos(a) * radius, y, z + Math.sin(a) * radius],
                [x + Math.cos(b) * radius, y, z + Math.sin(b) * radius], color]);
        }
    }

    const SELECTED = [0.45, 1, 0.5, 1];
    const ATTACK = [1, 0.3, 0.25, 1];

    // Rings under selected units with a line to where each is headed, the last order's marker and team bases.
    function unitLines(now) {
        const lines = [];
        const s = figureScale();
        for (const id of state.selected) {
            const u = state.units.get(id);
            if (!u) {
                continue;
            }
            ring(lines, u.drawX, u.drawY + 0.05, u.drawZ, 0.7 * s, SELECTED);
            const target = u.target ? state.units.get(u.target) || state.players.get(u.target) : null;
            if (u.order === "attack" && target) {
                lines.push([[u.drawX, u.drawY + 0.1, u.drawZ], [target.drawX, target.drawY + 0.1, target.drawZ], ATTACK]);
            } else if (u.order === "move" && u.toX !== undefined) {
                const y = surfaceY(Math.floor(u.toX), Math.floor(u.toZ));
                lines.push([[u.drawX, u.drawY + 0.1, u.drawZ], [u.toX, y + 0.1, u.toZ], SELECTED]);
            }
        }
        if (orderMark) {
            const age = (now - orderMark.time) / 1000;
            if (age > 1) {
                orderMark = null;
            } else {
                const color = orderMark.color.slice(0, 3).concat([1 - age]);
                ring(lines, orderMark.x, orderMark.y + 0.1, orderMark.z, (0.5 + 1.5 * age) * s, color);
            }
        }
        for (const team of state.teams) {
            if (team.base) {
                const x = team.base.x + 0.5;
                const z = team.base.z + 0.5;
                const y = surfaceY(team.base.x, team.base.z);
                const color = hexColor(team.color, 1);
                lines.push([[x, y, z], [x, y + 11 * s, z], [0.9, 0.85, 0.7, 1]]);
                ring(lines, x, y + 0.1, z, 4, color);
            }
        }
        return lines;
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
        const s = figureScale();
        for (const team of state.teams) {
            if (team.base) {
                const [x, y] = view.project(team.base.x + 0.5, surfaceY(team.base.x, team.base.z) + 11 * s, team.base.z + 0.5);
                labelCtx.lineWidth = 3;
                labelCtx.strokeStyle = "rgba(0, 0, 0, 0.8)";
                labelCtx.strokeText(team.name + " base", x, y - 6);
                labelCtx.fillStyle = team.color;
                labelCtx.fillText(team.name + " base", x, y - 6);
            }
        }
        // Health bars over units that are hurt or selected.
        for (const u of state.units.values()) {
            const selected = state.selected.has(u.id);
            if (!selected && u.health >= u.maxHealth) {
                continue;
            }
            const [x, y] = view.project(u.drawX, u.drawY + 1.85 * s, u.drawZ);
            const width = 24;
            const fraction = Math.max(0, Math.min(1, u.health / u.maxHealth));
            labelCtx.fillStyle = "rgba(0, 0, 0, 0.75)";
            labelCtx.fillRect(x - width / 2 - 1, y - 9, width + 2, 5);
            labelCtx.fillStyle = fraction > 0.5 ? "#5fdc6a" : fraction > 0.25 ? "#e8c547" : "#e8553f";
            labelCtx.fillRect(x - width / 2, y - 8, width * fraction, 3);
        }
        if (selectionBox) {
            const b = selectionBox;
            labelCtx.fillStyle = "rgba(115, 255, 128, 0.12)";
            labelCtx.strokeStyle = "rgba(115, 255, 128, 0.9)";
            labelCtx.lineWidth = 1;
            labelCtx.fillRect(Math.min(b.x0, b.x1), Math.min(b.y0, b.y1), Math.abs(b.x1 - b.x0), Math.abs(b.y1 - b.y0));
            labelCtx.strokeRect(Math.min(b.x0, b.x1) + 0.5, Math.min(b.y0, b.y1) + 0.5, Math.abs(b.x1 - b.x0), Math.abs(b.y1 - b.y0));
        }
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
        // Players and units glide toward their latest reported position between updates.
        for (const p of [...state.players.values(), ...state.units.values()]) {
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
        if (orderMark) {
            view.dirty = true;
        }
        if (view.dirty && state.game) {
            view.draw(figureBoxes(), borderLines().concat(unitLines(now)));
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
            renderUnitBar();
            setFollow(false);
            connect();
            requestAnimationFrame(frame);
        }
    }).catch((error) => showLogin("Could not reach the server: " + error.message));
})();
