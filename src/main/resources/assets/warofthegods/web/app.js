// War of the Gods web map. Plain JavaScript, no build step: blocks.js has the block colours and shapes,
// render.js draws the map in 3D, and this file runs the page.
//
// The server sends JSON messages over a WebSocket at /ws:
//   {type: "game", game: {number, minX, minZ, maxX, maxZ, margin, tileSize, seaLevel, unitKinds, unitsPerTeam,
//                         trainSeconds, buildingGap, buildingTypes} | null}
//   {type: "tiles", tiles: {"x,z": version}}   map tiles to (re)load from /api/tile, see MapTiles.java
//   {type: "state", teams: [...], players: [...], units: [...], buildings: [...], mobs: [...], trees: [...]}
//   {type: "error", error, replyTo}
// The page sends {type: "hello"} once connected, and commands such as unit orders as {type, id, ...};
// see command(), UnitCommands.java and BuildingCommands.java.
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
        mobs: new Map(), // id -> {name, hostile, x, y, z, width, height, health, maxHealth, drawX...}
        selected: new Set(), // ids of the viewer's units that orders go to
        // id -> {type, team, x, z, sizeX, sizeZ, y, rotation, complete, built, builders, health, maxHealth, queue,
        // progress, workers}
        buildings: new Map(),
        trees: [], // {x, y, z, top, wood, maxWood}: trees workers have started chopping
        selectedBuilding: null, // id of the viewer's building whose controls the bar shows
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

    // A new game, or the same game after a server restart, comes with a new map id. Everything drawn from the
    // old map is thrown away then: tile versions start over, so old tiles would otherwise look current.
    function setGame(game) {
        const newGame = !state.game || !game || state.game.number !== game.number;
        const changed = newGame || state.game.mapId !== game.mapId;
        state.game = game;
        if (changed) {
            state.tiles.clear();
            tileQueue.length = 0;
            state.players.clear();
            state.units.clear();
            state.mobs.clear();
            state.selected.clear();
            state.buildings.clear();
            state.selectedBuilding = null;
            placing = null;
            view.setGame(game);
            if (game && newGame) {
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
            const mapId = state.game && state.game.mapId;
            const [x, z] = key.split(",").map(Number);
            tileFetches++;
            fetchTile(x, z, version, mapId).then((buffer) => {
                // A tile of an older game, or an older version finishing late, is dropped.
                if (buffer && state.game && state.game.mapId === mapId && tile.version === version) {
                    view.setTile(x, z, buffer);
                    tile.loaded = true;
                    updateProgress();
                }
            }).catch((error) => {
                console.warn("Map tile " + key + " failed:", error);
                tile.version = -1;
                setTimeout(() => {
                    if (state.game && state.game.mapId === mapId && state.tiles.get(key) === tile) {
                        updateTile(key, version);
                    }
                }, 3000);
            }).finally(() => {
                tileFetches--;
                pumpTiles();
            });
        }
    }

    async function fetchTile(x, z, version, mapId) {
        const response = await fetch("api/tile?x=" + x + "&z=" + z + "&m=" + encodeURIComponent(mapId) + "&v=" + version);
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
        const seenMobs = new Set();
        for (const m of message.mobs || []) {
            seenMobs.add(m.id);
            const old = state.mobs.get(m.id);
            m.drawX = old ? old.drawX : m.x;
            m.drawY = old ? old.drawY : m.y;
            m.drawZ = old ? old.drawZ : m.z;
            state.mobs.set(m.id, m);
        }
        for (const id of [...state.mobs.keys()]) {
            if (!seenMobs.has(id)) {
                state.mobs.delete(id);
            }
        }
        state.buildings.clear();
        for (const b of message.buildings || []) {
            state.buildings.set(b.id, b);
        }
        state.trees = message.trees || [];
        // Dead units, and units of a team the viewer has left, drop out of the selection, as do lost buildings.
        const team = myTeam();
        for (const id of [...state.selected]) {
            const u = state.units.get(id);
            if (!u || !team || u.team !== team.id) {
                state.selected.delete(id);
            }
        }
        const building = state.buildings.get(state.selectedBuilding);
        if (!building || !team || building.team !== team.id) {
            state.selectedBuilding = null;
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
                state.me.observer ? "Observer" : team ? "Team " + team.name + " \u00B7 " + ageName(team.age) : "Not on a team"));
        }
        if (!state.game) {
            me.append(el("div", "muted", "No game is running."));
        }

        // Built aside and swapped in only when it changed, so links do not move under the pointer mid-click.
        const teams = el("div");
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
                const units = el("div", "muted", "Units " + team.units + " of " + (team.population || 0));
                const hall = townHallOf(team.id);
                if (hall) {
                    const link = el("a", "", "town center");
                    link.href = "#";
                    link.addEventListener("click", (event) => {
                        event.preventDefault();
                        focusBuilding(hall);
                    });
                    units.append(document.createTextNode(", "), link);
                }
                box.append(units);
            }

            if (team.upgrades && team.upgrades.length && state.game && state.game.upgrades) {
                box.append(el("div", "muted", "Upgrades: " + team.upgrades
                    .map((id) => state.game.upgrades[id] ? state.game.upgrades[id].name.toLowerCase() : id).join(", ")));
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
        swapIfChanged(document.getElementById("teams"), teams);
    }

    // Replaces target's children with those of fresh, unless they would look the same.
    // Compares with what was put in last time rather than with what is there now, since progress bars are filled
    // in after the swap: otherwise every update would swap again, replacing a button under the pointer before a
    // click on it could land.
    function swapIfChanged(target, fresh) {
        const html = fresh.innerHTML;
        if (target.wotgHtml !== html) {
            target.wotgHtml = html;
            target.replaceChildren(...fresh.childNodes);
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
    const barActions = document.getElementById("bar-actions");
    const boxButton = document.getElementById("box-select");
    const toast = document.getElementById("toast");
    let boxMode = false;
    let selectionBox = null; // {x0, y0, x1, y1} in CSS pixels while dragging one
    let orderMark = null; // {x, y, z, color, time} where the last order went
    let lastClick = {time: 0, id: null};
    // A building being placed: {type, rotation, cx, cz} with the footprint centred on cx, cz under the pointer.
    let placing = null;
    // Workers waiting for the two corners of an area to put torches in: {corner: null, or the first [x, y, z]}.
    let lighting = null;
    // How units line up where they are sent: box, line or wedge.
    let formation = "box";
    const FORMATIONS = [["box", "Box", "A square block"], ["line", "Line", "A wide line, two or three deep"],
        ["wedge", "Wedge", "A wedge pointing the way they go"]];
    const STANCES = [["aggressive", "Aggressive", "Look further for enemies and chase them far"],
        ["defensive", "Defensive", "Fight what comes near, then go back"],
        ["hold", "Hold", "Never leave the spot: fight only what is in reach"],
        ["passive", "Passive", "Never fight unless ordered, not even back"]];

    function costText(cost) {
        cost = cost || {};
        const parts = RESOURCES.filter(([id]) => cost[id]).map(([id, label]) => cost[id] + " " + label.toLowerCase());
        return parts.length ? parts.join(", ") : "free";
    }

    function canAfford(team, cost) {
        cost = cost || {};
        return RESOURCES.every(([id]) => !cost[id] || team.resources[id] >= cost[id]);
    }

    function buildingType(id) {
        return state.game && state.game.buildingTypes ? state.game.buildingTypes[id] : null;
    }

    function unitKind(id) {
        return state.game && state.game.unitKinds ? state.game.unitKinds[id] : null;
    }

    function townHallOf(teamId) {
        for (const b of state.buildings.values()) {
            if (b.team === teamId && b.type === "town_hall") {
                return b;
            }
        }
        return null;
    }

    function focusBuilding(b) {
        setFollow(false);
        view.focus(b.x + b.sizeX / 2, b.y + 1, b.z + b.sizeZ / 2);
        view.cam.zoom = Math.min(view.cam.zoom, 40);
    }

    function selectedWorkers() {
        return [...state.selected].filter((id) => {
            const u = state.units.get(id);
            return u && u.kind === "worker";
        });
    }

    // Units the team has, plus the ones waiting in training queues.
    function teamUnitCount(team) {
        let queued = 0;
        for (const b of state.buildings.values()) {
            if (b.team === team.id && b.queue) {
                queued += b.queue.length;
            }
        }
        return team.units + queued;
    }

    function button(label, title, onClick) {
        const b = el("button", "", label);
        b.type = "button";
        if (title) {
            b.title = title;
        }
        b.addEventListener("click", onClick);
        return b;
    }

    // The building and unit icons in icons.png, one 16 pixel square each, in this order.
    const SPRITES = ["town_hall", "house", "barracks", "storehouse", "quarry", "farm", "armory", "tower", "worker",
        "soldier", "archer", "tree", "upgrade", "level"];

    function sprite(name) {
        const span = el("span", "sprite");
        span.style.backgroundPosition = -16 * Math.max(0, SPRITES.indexOf(name)) + "px 0";
        return span;
    }

    function iconButton(icon, label, title, onClick) {
        const b = button("", title, onClick);
        b.append(sprite(icon), document.createTextNode(label));
        b.classList.add("with-icon");
        return b;
    }

    function roman(level) {
        return level === 1 ? "I" : level === 2 ? "II" : level === 3 ? "III" : String(level);
    }

    function ageName(age) {
        return "Age " + roman(age || 1);
    }

    function researchName(research) {
        if (research === "level") {
            return "Going up a level";
        }
        const upgrade = state.game && state.game.upgrades ? state.game.upgrades[research] : null;
        return upgrade ? upgrade.name : research;
    }

    // The left of the bar changes with what is going on: placing a building, a building selected, workers
    // selected, or nothing in particular.
    function renderUnitBar() {
        const team = myTeam();
        unitBar.hidden = !(team && state.game);
        if (unitBar.hidden) {
            return;
        }
        const actions = el("span");
        const building = state.buildings.get(state.selectedBuilding);
        if (placing) {
            const type = buildingType(placing.type);
            actions.append(el("span", "note", "Place " + type.name.toLowerCase() + ": click to place, R to rotate"));
            actions.append(button("Rotate", "Turn it a quarter round (R)", () => rotatePlacing()));
            actions.append(button("Cancel", "Esc or right-click", () => stopPlacing()));
        } else if (lighting) {
            actions.append(el("span", "note", lighting.corner ? "Torches: now click the opposite corner"
                : "Torches: click one corner of the area to light"));
            actions.append(button("Cancel", "Esc or right-click", () => stopLighting()));
        } else if (building) {
            renderBuildingActions(actions, team, building);
        } else if (selectedWorkers().length) {
            for (const [id, type] of Object.entries(state.game.buildingTypes || {})) {
                if (!type.buildable) {
                    continue;
                }
                const aged = (team.age || 1) >= (type.age || 1);
                const b = iconButton(id, type.name, aged ? "Build a " + type.name.toLowerCase() + " for "
                    + costText(type.cost) + ", then click on the map where it goes"
                    : "Needs " + ageName(type.age) + ": take your town center up a level", () => startPlacing(id));
                b.disabled = !aged || !canAfford(team, type.cost);
                actions.append(b);
            }
            actions.append(button("Torches", "Light up an area so no mobs spawn there: click two corners."
                + " Each torch costs wood", () => startLighting()));
        } else {
            const hall = townHallOf(team.id);
            if (hall) {
                actions.append(iconButton("town_hall", "Town center", "Select your town center to train workers", () => {
                    selectBuilding(hall.id);
                    focusBuilding(hall);
                }));
            }
        }
        if (!placing && !lighting && !building) {
            fighterActions(actions);
        }
        swapIfChanged(barActions, actions);
        for (const fill of barActions.querySelectorAll(".progress span")) {
            if (!building) {
                break;
            }
            const value = fill.parentNode.classList.contains("research") ? building.researched
                : building.complete ? building.progress : building.built;
            fill.style.width = Math.round(100 * (value || 0)) + "%";
        }
        const count = state.selected.size;
        const workers = selectedWorkers().length;
        let info = team.units ? "None selected" : "No units yet";
        if (count) {
            info = count + " selected" + (workers === count ? (count === 1 ? ", a worker" : ", all workers")
                : workers ? ", " + workers + " workers" : "");
        }
        document.getElementById("selection-info").textContent = info;
        document.getElementById("stop-units").disabled = !count;
        document.getElementById("clear-selection").disabled = !count && !building;
        boxButton.classList.toggle("on", boxMode);
    }

    // With warriors or archers selected: how they line up when sent somewhere, and how they fight.
    function fighterActions(actions) {
        const fighters = [...state.selected].map((id) => state.units.get(id)).filter((u) => u && u.kind !== "worker");
        if (!fighters.length) {
            return;
        }
        const stances = new Set(fighters.map((u) => u.stance || "defensive"));
        actions.append(el("span", "note", "Formation"));
        for (const [id, name, tip] of FORMATIONS) {
            const b = button(name, tip + ", where you send them", () => {
                formation = id;
                renderUnitBar();
            });
            b.classList.toggle("on", formation === id);
            actions.append(b);
        }
        actions.append(el("span", "note", "Stance"));
        for (const [id, name, tip] of STANCES) {
            const b = button(name, tip, () => {
                send("units.stance", {units: fighters.map((u) => u.id), stance: id}).then((reply) => {
                    if (reply) {
                        showToast(name + " stance", false);
                    }
                });
            });
            b.classList.toggle("on", stances.size === 1 && stances.has(id));
            actions.append(b);
        }
    }

    function renderBuildingActions(actions, team, building) {
        const type = buildingType(building.type);
        if (!building.complete) {
            const coming = [...state.units.values()].filter((u) => u.building === building.id && u.order === "build")
                .length - (building.builders || 0);
            actions.append(el("span", "", type.name + " site"));
            const bar = el("span", "progress build");
            bar.append(el("span"));
            actions.append(bar);
            let note = Math.floor(100 * (building.built || 0)) + "% built";
            if (building.builders) {
                note += ", " + building.builders + (building.builders === 1 ? " worker building" : " workers building");
            }
            if (coming > 0) {
                note += ", " + coming + " on the way";
            }
            if (!building.builders && coming <= 0) {
                note += ": select a worker and right-click it to build";
            }
            actions.append(el("span", "note", note));
            return;
        }
        const title = el("span", "");
        title.append(sprite(building.type), document.createTextNode(type.name
            + (building.level > 1 ? " " + roman(building.level) : "")));
        actions.append(title);
        actions.append(el("span", "note", "Health " + building.health + "/" + building.maxHealth));
        if (type.workers) {
            actions.append(el("span", "note", (building.workers || 0) + " of " + type.workers + " workers"));
        }
        const full = teamUnitCount(team) >= (team.population || 0);
        for (const kindId of type.trains || []) {
            const kind = unitKind(kindId);
            if (!kind) {
                continue;
            }
            const train = iconButton(kindId, kind.name + " (" + costText(kind.cost) + ")",
                full ? (team.population < state.game.unitsPerTeam ? "Build houses to house more units"
                    : "Your team has the most units it can have")
                    : "Train a " + kind.name.toLowerCase() + "; shift-click for 5", async (event) => {
                    const reply = await send("buildings.train",
                        {building: building.id, unit: kindId, count: event.shiftKey ? 5 : 1});
                    if (reply) {
                        showToast(reply.count === 1 ? "Training a " + kind.name.toLowerCase()
                            : "Training " + reply.count + " " + kind.name.toLowerCase() + "s");
                    }
                });
            train.disabled = full || !canAfford(team, kind.cost) || (building.queue || []).length >= 10;
            actions.append(train);
        }
        if (building.queue && building.queue.length) {
            const bar = el("span", "progress");
            bar.append(el("span"));
            actions.append(bar);
            actions.append(el("span", "note", building.queue.length + " in training"));
        }
        if (building.research) {
            const bar = el("span", "progress research");
            bar.append(el("span"));
            actions.append(bar);
            actions.append(el("span", "note", researchName(building.research)));
            return;
        }
        const next = type.levelUps ? type.levelUps[building.level + 1] : null;
        if (next) {
            const center = building.type === "town_hall";
            const ready = center || (team.age || 1) > building.level;
            const up = iconButton("level", (center ? "Advance to " + ageName(building.level + 1)
                : "Level " + (building.level + 1)) + " (" + costText(next.cost) + ")",
                ready ? "Takes " + next.seconds + "s; health goes up to " + next.health
                    : "Your town has to reach " + ageName(building.level + 1) + " first", async () => {
                    if (await send("buildings.upgrade", {building: building.id})) {
                        showToast("Going up a level");
                    }
                });
            up.disabled = !ready || !canAfford(team, next.cost);
            actions.append(up);
        }
        for (const upgradeId of type.researches || []) {
            const upgrade = state.game.upgrades[upgradeId];
            if (!upgrade || (team.upgrades || []).includes(upgradeId)) {
                continue;
            }
            const busy = [...state.buildings.values()].some((b) => b.team === team.id && b.research === upgradeId);
            const levelOk = building.level >= upgrade.level;
            const research = iconButton("upgrade", upgrade.name + " (" + costText(upgrade.cost) + ")",
                !levelOk ? "Needs a level " + upgrade.level + " " + type.name.toLowerCase()
                    : busy ? "Being researched" : upgrade.description + "; takes " + upgrade.seconds + "s",
                async () => {
                    if (await send("buildings.research", {building: building.id, upgrade: upgradeId})) {
                        showToast("Researching " + upgrade.name.toLowerCase());
                    }
                });
            research.disabled = !levelOk || busy || !canAfford(team, upgrade.cost);
            actions.append(research);
        }
    }

    function selectBuilding(id) {
        state.selectedBuilding = id;
        state.selected.clear();
        changedSelection();
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

    // ---------------------------------------------------------------- lighting areas

    function startLighting() {
        placing = null;
        lighting = {corner: null};
        renderUnitBar();
    }

    function stopLighting() {
        lighting = null;
        renderUnitBar();
    }

    // The first click picks one corner of the area to light; the second the other, and the workers go.
    function pickLightCorner(px, py) {
        const ground = groundUnder(px, py);
        if (!ground) {
            return;
        }
        if (!lighting.corner) {
            lighting.corner = ground;
            renderUnitBar();
            return;
        }
        const [x1, , z1] = lighting.corner;
        const [x2, y2, z2] = ground;
        stopLighting();
        orderMark = {x: (x1 + x2) / 2, y: y2, z: (z1 + z2) / 2, color: [1, 0.85, 0.3, 1], time: performance.now()};
        send("units.light", {units: selectedWorkers(), x1, z1, x2, z2}).then((reply) => {
            if (reply) {
                showToast("Putting up " + reply.torches + (reply.torches === 1 ? " torch" : " torches"), false);
            }
        });
        view.dirty = true;
    }

    // ---------------------------------------------------------------- placing buildings
    //
    // RTS style: pick a building from the bar with workers selected, and a see-through copy of it follows the
    // pointer, green where it fits and red where it does not. R or the Rotate button turns it a quarter round;
    // a click places it and sends the selected workers, shift-click places more; right-click or Esc cancels.

    function startPlacing(type) {
        lighting = null;
        placing = {type, rotation: placing ? placing.rotation : 0, cx: null, cz: null};
        renderUnitBar();
        view.dirty = true;
    }

    function stopPlacing() {
        placing = null;
        renderUnitBar();
        view.dirty = true;
    }

    function rotatePlacing() {
        if (placing) {
            placing.rotation = (placing.rotation + 1) % 4;
            view.dirty = true;
        }
    }

    function footprintSize(type, rotation) {
        return rotation % 2 === 0 ? [type.width, type.length] : [type.length, type.width];
    }

    // Where the footprint of the building being placed goes and whether it fits: inside the map and clear of
    // other buildings. The server also checks the ground is dry and level enough.
    function placingFootprint() {
        if (!placing || placing.cx === null) {
            return null;
        }
        const type = buildingType(placing.type);
        const [sizeX, sizeZ] = footprintSize(type, placing.rotation);
        const x = placing.cx - Math.floor(sizeX / 2);
        const z = placing.cz - Math.floor(sizeZ / 2);
        const g = state.game;
        let ok = x >= g.minX && z >= g.minZ && x + sizeX - 1 <= g.maxX && z + sizeZ - 1 <= g.maxZ;
        const gap = g.buildingGap || 0;
        for (const b of state.buildings.values()) {
            if (x < b.x + b.sizeX + gap && b.x < x + sizeX + gap && z < b.z + b.sizeZ + gap && b.z < z + sizeZ + gap) {
                ok = false;
            }
        }
        return {x, z, sizeX, sizeZ, ok, y: surfaceY(placing.cx, placing.cz) - 1};
    }

    function movePlacing(px, py) {
        const ground = groundUnder(px, py);
        if (!ground) {
            return;
        }
        const cx = Math.floor(ground[0]);
        const cz = Math.floor(ground[2]);
        if (cx !== placing.cx || cz !== placing.cz) {
            placing.cx = cx;
            placing.cz = cz;
            view.dirty = true;
        }
    }

    async function place(px, py, keep) {
        movePlacing(px, py);
        const spot = placingFootprint();
        if (!spot) {
            return;
        }
        if (!spot.ok) {
            showToast("It does not fit there", true);
            return;
        }
        const workers = selectedWorkers();
        if (!workers.length) {
            showToast("Select a worker to build with", true);
            stopPlacing();
            return;
        }
        const {type, rotation, cx, cz} = placing;
        if (!keep) {
            stopPlacing();
        }
        const reply = await send("buildings.place", {building: type, x: cx, z: cz, rotation, units: workers});
        if (reply) {
            showToast(buildingType(type).name + " site laid out");
        }
    }

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
        state.selectedBuilding = null;
        changedSelection();
    });

    boxButton.addEventListener("click", () => {
        boxMode = !boxMode;
        renderUnitBar();
    });

    function changedSelection() {
        if (state.selected.size) {
            state.selectedBuilding = null;
        }
        if (placing && !selectedWorkers().length) {
            placing = null;
        }
        if (lighting && !selectedWorkers().length) {
            lighting = null;
        }
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

    // CSS pixels one block takes up on screen at the current zoom.
    function pixelsPerBlock() {
        return canvas.clientHeight / (2 * view.cam.zoom);
    }

    // How big units, players and mobs are drawn: life size up close, up to a little larger when zoomed out so they
    // stay visible. Further out they are drawn as dots instead, see markerMode().
    function figureScale() {
        return Math.min(MAX_FIGURE_SCALE, Math.max(1, MIN_FIGURE_PIXELS / (1.8 * pixelsPerBlock())));
    }

    const MAX_FIGURE_SCALE = 1.6;
    const MIN_FIGURE_PIXELS = 14;

    // Whether figures would be too small to make out even enlarged: then units and players are drawn as dots in their
    // team's colour, hostile mobs as faint dots, and animals not at all.
    function markerMode() {
        return 1.8 * MAX_FIGURE_SCALE * pixelsPerBlock() < MIN_FIGURE_PIXELS;
    }

    // The unit, player or mob under a screen point: {kind: "unit"|"player"|"mob", thing}, or null. The pointer has to
    // be on the figure (or dot) as drawn, give or take a few pixels, so a click never lands on something blocks away.
    function entityAt(px, py) {
        const s = figureScale();
        const ppb = pixelsPerBlock();
        const dots = markerMode();
        let best = null;
        let bestDistance = Infinity;
        const consider = (kind, thing, height) => {
            if (dots && kind === "mob" && !thing.hostile) {
                return;
            }
            // Up close the middle of the figure, with half its height as the reach; zoomed out the dot.
            const middle = dots ? 0 : height * s / 2;
            const reach = dots ? 6 : Math.max(5, middle * ppb + 3);
            const [x, y] = view.project(thing.drawX, thing.drawY + middle, thing.drawZ);
            const distance = Math.hypot(x - px, y - py);
            if (distance <= reach && distance < bestDistance) {
                bestDistance = distance;
                best = {kind, thing};
            }
        };
        for (const u of state.units.values()) {
            consider("unit", u, 1.8);
        }
        for (const p of state.players.values()) {
            consider("player", p, 1.8);
        }
        for (const m of state.mobs.values()) {
            consider("mob", m, Math.max(0.4, m.height || 1));
        }
        return best;
    }

    function entityId(hit) {
        return hit.kind === "player" ? hit.thing.uuid : hit.thing.id;
    }

    // Whether the selected soldiers would attack what was hit on a right-click: enemies, and any mob.
    function isEnemy(hit) {
        if (hit.kind === "mob") {
            return true;
        }
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

    // The building or building site under a screen point, or null.
    function buildingAt(px, py) {
        const ground = groundUnder(px, py);
        if (!ground) {
            return null;
        }
        for (const b of state.buildings.values()) {
            if (ground[0] >= b.x && ground[0] < b.x + b.sizeX && ground[2] >= b.z && ground[2] < b.z + b.sizeZ) {
                return b;
            }
        }
        return null;
    }

    // Selects or orders for a click (or tap) that did not drag.
    function click(px, py, button, shift, touch) {
        if (!myTeam()) {
            return;
        }
        if (placing) {
            if (button === 2) {
                stopPlacing();
            } else if (button === 0) {
                place(px, py, shift);
            }
            return;
        }
        if (lighting) {
            if (button === 2) {
                stopLighting();
            } else if (button === 0) {
                pickLightCorner(px, py);
            }
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
            return;
        }
        const building = hit ? null : buildingAt(px, py);
        if (building && building.team === myTeam().id) {
            selectBuilding(building.id);
        } else if (!shift && !touch && (state.selected.size || state.selectedBuilding !== null)) {
            state.selected.clear();
            state.selectedBuilding = null;
            changedSelection();
        }
    }

    function order(px, py, hit) {
        const units = [...state.selected];
        if (hit && isEnemy(hit)) {
            const t = hit.thing;
            orderMark = {x: t.drawX, y: t.drawY, z: t.drawZ, color: [1, 0.3, 0.25, 1], time: performance.now()};
            send("units.attack", {units, target: entityId(hit)});
        } else if (!hit && (orderBuild(px, py) || orderAttackBuilding(px, py) || orderChop(px, py))) {
            return;
        } else {
            const ground = groundUnder(px, py);
            if (!ground) {
                return;
            }
            orderMark = {x: ground[0], y: ground[1], z: ground[2], color: [0.45, 1, 0.5, 1], time: performance.now()};
            send("units.move", {units, x: ground[0], z: ground[2], formation});
        }
        view.dirty = true;
    }

    // Right-clicking an enemy building sends the selected soldiers to attack it.
    function orderAttackBuilding(px, py) {
        const building = buildingAt(px, py);
        const fighters = [...state.selected].filter((id) => {
            const u = state.units.get(id);
            return u && u.kind !== "worker";
        });
        if (!building || !fighters.length || !isEnemyTeam(building.team)) {
            return false;
        }
        orderMark = {x: building.x + building.sizeX / 2, y: building.y + 1, z: building.z + building.sizeZ / 2,
            color: [1, 0.3, 0.25, 1], time: performance.now()};
        send("units.attack", {units: fighters, building: building.id});
        view.dirty = true;
        return true;
    }

    // Right-clicking one of the team's building sites, or a farm or quarry, sends the selected workers to build or
    // work it.
    function orderBuild(px, py) {
        const building = buildingAt(px, py);
        const workers = selectedWorkers();
        const type = building && buildingType(building.type);
        if (!building || building.team !== myTeam().id || !workers.length
            || (building.complete && !(type && type.workers))) {
            return false;
        }
        orderMark = {x: building.x + building.sizeX / 2, y: building.y + 1, z: building.z + building.sizeZ / 2,
            color: [0.45, 1, 0.5, 1], time: performance.now()};
        send("units.build", {units: workers, building: building.id});
        view.dirty = true;
        return true;
    }

    const TREE_BLOCKS = new Set([17, 18, 161, 162]);

    // The log or leaves under a screen point, off any building, as {x, y, z}; null if it is no tree.
    function treeUnder(px, py) {
        const hit = view.pick(px, py);
        if (!hit || !TREE_BLOCKS.has(hit.block)) {
            return null;
        }
        for (const b of state.buildings.values()) {
            if (hit.x >= b.x && hit.x < b.x + b.sizeX && hit.z >= b.z && hit.z < b.z + b.sizeZ) {
                return null;
            }
        }
        return hit;
    }

    // The tracked tree nearest a block of a tree, or null.
    function trackedTreeNear(block) {
        let best = null;
        let bestDistance = Infinity;
        for (const t of state.trees) {
            const distance = (t.x - block.x) ** 2 + (t.z - block.z) ** 2;
            if (distance <= 9 && block.y >= t.y - 1 && block.y <= t.top + 4 && distance < bestDistance) {
                best = t;
                bestDistance = distance;
            }
        }
        return best;
    }

    // Right-clicking a tree sends the selected workers to chop it.
    function orderChop(px, py) {
        const tree = treeUnder(px, py);
        const workers = selectedWorkers();
        if (!tree || !workers.length) {
            return false;
        }
        orderMark = {x: tree.x + 0.5, y: tree.y + 0.5, z: tree.z + 0.5, color: [0.45, 1, 0.5, 1],
            time: performance.now()};
        send("units.gather", {units: workers, x: tree.x + 0.5, y: tree.y, z: tree.z + 0.5});
        view.dirty = true;
        return true;
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
            point.box = event.button === 0 && (event.shiftKey || boxMode) && Boolean(myTeam()) && !placing && !lighting;
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
        pointer = event.pointerType === "touch" ? null : {x: event.offsetX, y: event.offsetY};
        updateCursor(event.offsetX, event.offsetY);
        if (placing) {
            movePlacing(event.offsetX, event.offsetY);
        }
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

    // ---------------------------------------------------------------- hover tooltips
    //
    // A small black box by the pointer says what is under it, with an icon: what a right-click would do with the
    // selected units (attack, build, work), or else what the unit or building is.

    const tip = document.getElementById("tip");
    let pointer = null; // {x, y} over the map, or null when the pointer is elsewhere
    let tipTimer = 0;

    const ICONS = {
        attack: "\u2694\uFE0F",
        build: "\uD83D\uDD28",
        work: "\uD83C\uDF3E",
        soldier: "\uD83D\uDEE1\uFE0F",
        worker: "\uD83E\uDE93",
        player: "\uD83D\uDC64",
        town_hall: "\uD83C\uDFDB\uFE0F",
        farm: "\uD83C\uDF3E",
        barracks: "\u2694\uFE0F",
        storehouse: "\uD83D\uDCE6",
        quarry: "\u26CF\uFE0F",
        site: "\uD83D\uDEA7",
        mob: "\uD83E\uDDDF",
        animal: "\uD83D\uDC04",
    };

    const RESOURCE_ORDER = ["food", "wood", "stone", "metal"];

    function amountsText(amounts) {
        return RESOURCE_ORDER.filter((id) => amounts[id]).map((id) => amounts[id] + " " + id).join(", ");
    }

    // What a unit is doing, in a few words.
    function doingText(u) {
        const b = state.buildings.get(u.building);
        const where = b ? buildingType(b.type).name.toLowerCase() : "";
        switch (u.order) {
            case "move":
                return "walking";
            case "attack":
                return "fighting";
            case "attack_building":
                return "attacking " + (b ? buildingName(b) : "a building");
            case "build":
                return u.toX === undefined ? "building " + where : "on the way to build " + where;
            case "work":
                if (u.delivering) {
                    return "taking " + amountsText(u.carrying || {}) + " to store";
                }
                return u.toX === undefined ? "working at " + where : "on the way to " + where;
            case "chop":
                if (u.delivering) {
                    return "taking " + amountsText(u.carrying || {}) + " to store";
                }
                return u.chopping ? "chopping a tree" : "on the way to a tree";
            case "light":
                return "putting up torches, " + (u.torches || 0) + " to go";
            default:
                return u.kind === "worker" ? "idle" : "on guard";
        }
    }

    function unitName(u) {
        const team = state.teamsById[u.team];
        const kind = unitKind(u.kind);
        return (team ? team.name + " " : "") + (kind ? kind.name.toLowerCase() : u.kind) + " " + u.number;
    }

    function buildingName(b) {
        const team = state.teamsById[b.team];
        const type = buildingType(b.type);
        return (team ? team.name + " " : "") + (type ? type.name.toLowerCase() : b.type)
            + (b.level > 1 ? " (level " + b.level + ")" : "");
    }

    // What the pointer at px, py is over, as {icon, text, action}, or null for nothing worth a tooltip.
    function hint(px, py) {
        if (!state.game || placing) {
            return null;
        }
        if (lighting) {
            return {icon: "\uD83D\uDD25", text: lighting.corner ? "Click the opposite corner"
                : "Click one corner of the area to light", action: "act"};
        }
        const team = myTeam();
        const selected = [...state.selected].map((id) => state.units.get(id)).filter(Boolean);
        const fighters = selected.filter((u) => u.kind !== "worker").length;
        const workers = selected.length - fighters;
        const hit = entityAt(px, py);
        if (hit) {
            if (fighters && isEnemy(hit)) {
                const name = hit.kind === "unit" ? unitName(hit.thing) : hit.thing.name;
                return {icon: ICONS.attack, text: "Attack " + name + (hit.kind === "mob"
                    ? " \u00B7 " + Math.ceil(hit.thing.health) + "/" + Math.ceil(hit.thing.maxHealth) : ""),
                action: "attack"};
            }
            if (hit.kind === "mob") {
                const m = hit.thing;
                return {icon: m.hostile ? ICONS.mob : ICONS.animal, text: m.name + (m.hostile ? "" : " (passive)")
                    + " \u00B7 " + Math.ceil(m.health) + "/" + Math.ceil(m.maxHealth)};
            }
            if (hit.kind === "player") {
                const t = state.teamsById[hit.thing.team];
                return {icon: ICONS.player, text: hit.thing.name + (t ? " (" + t.name + ")" : "")};
            }
            const u = hit.thing;
            let text = unitName(u) + " \u00B7 " + doingText(u);
            if (u.carrying && !u.delivering) {
                text += " \u00B7 carrying " + amountsText(u.carrying);
            }
            return {icon: u.kind,
                text: text + " \u00B7 " + Math.ceil(u.health) + "/" + Math.ceil(u.maxHealth)};
        }
        const b = buildingAt(px, py);
        if (!b) {
            const block = treeUnder(px, py);
            if (!block) {
                return null;
            }
            const tree = trackedTreeNear(block);
            const wood = tree ? " \u00B7 " + tree.wood + "/" + tree.maxWood + " wood" : "";
            if (workers) {
                return {icon: "tree", text: "Chop tree" + wood, action: "build"};
            }
            return {icon: "tree", text: "Tree" + wood + (team ? " \u00B7 select workers to chop it" : "")};
        }
        const type = buildingType(b.type);
        const mine = team && b.team === team.id;
        if (fighters && isEnemyTeam(b.team)) {
            return {icon: ICONS.attack, text: "Attack " + buildingName(b) + " \u00B7 " + b.health + "/" + b.maxHealth,
                action: "attack"};
        }
        if (mine && workers && !b.complete) {
            return {icon: "worker", text: "Build " + type.name.toLowerCase() + " \u00B7 "
                + Math.floor(100 * (b.built || 0)) + "% built", action: "build"};
        }
        if (mine && workers && type.workers) {
            return {icon: b.type, text: "Work at " + type.name.toLowerCase() + " ("
                + (b.workers || 0) + " of " + type.workers + " workers)", action: "build"};
        }
        let text = buildingName(b);
        if (!b.complete) {
            text += " \u00B7 building site, " + Math.floor(100 * (b.built || 0)) + "% built";
        } else if (b.queue && b.queue.length) {
            text += " \u00B7 training " + b.queue.length;
        } else if (b.research) {
            text += " \u00B7 " + researchName(b.research).toLowerCase() + " "
                + Math.floor(100 * (b.researched || 0)) + "%";
        } else if (type.workers) {
            text += " \u00B7 " + (b.workers || 0) + " working";
        }
        text += " \u00B7 " + b.health + "/" + b.maxHealth;
        return {icon: b.complete ? b.type : ICONS.site, text};
    }

    function updateTip() {
        const h = pointer && !pointers.size ? hint(pointer.x, pointer.y) : null;
        canvas.classList.toggle("attack", Boolean(h && h.action === "attack"));
        canvas.classList.toggle("act", Boolean(h && h.action && h.action !== "attack"));
        if (!h) {
            tip.hidden = true;
            return;
        }
        tip.replaceChildren(SPRITES.includes(h.icon) ? sprite(h.icon) : el("span", "icon", h.icon),
            document.createTextNode(h.text));
        tip.hidden = false;
        const rect = canvas.getBoundingClientRect();
        const x = Math.min(pointer.x + 16, rect.width - tip.offsetWidth - 4);
        const y = pointer.y + 20 + tip.offsetHeight > rect.height ? pointer.y - tip.offsetHeight - 8 : pointer.y + 20;
        tip.style.left = x + "px";
        tip.style.top = y + "px";
    }

    canvas.addEventListener("pointerleave", () => {
        pointer = null;
        updateTip();
    });

    function updateCursor(px, py) {
        // Picking walks the blocks under the pointer, so it runs at most every 50 ms.
        const now = performance.now();
        if (now - cursorTimer < 50) {
            return;
        }
        cursorTimer = now;
        const hit = view.pick(px, py);
        document.getElementById("cursor").textContent = hit ? hit.x + ", " + hit.y + ", " + hit.z : "";
        if (pointer) {
            tipTimer = now;
            updateTip();
        }
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
        } else if (key === "r" && placing) {
            rotatePlacing();
        } else if (key === "escape" && placing) {
            stopPlacing();
        } else if (key === "escape" && lighting) {
            stopLighting();
        } else if (key === "escape" && (state.selected.size || state.selectedBuilding !== null)) {
            state.selected.clear();
            state.selectedBuilding = null;
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

    const STRAW = [0.86, 0.74, 0.42, 1];
    const MOB = [0.24, 0.42, 0.25, 1];
    const ANIMAL = [0.72, 0.6, 0.45, 1];

    // Column heights of a building type turned a number of quarter turns clockwise, the way the server turns
    // structures: {sizeX, sizeZ, heights} with heights[x + z * sizeX].
    const turnedShapes = new Map();

    function turnedShape(typeId, rotation) {
        const key = typeId + "/" + rotation;
        if (turnedShapes.has(key)) {
            return turnedShapes.get(key);
        }
        const type = buildingType(typeId);
        let w = type.width;
        let l = type.length;
        let heights = type.heights;
        for (let r = 0; r < rotation; r++) {
            const turned = new Array(w * l);
            for (let z = 0; z < l; z++) {
                for (let x = 0; x < w; x++) {
                    turned[(l - 1 - z) + x * l] = heights[x + z * w];
                }
            }
            heights = turned;
            [w, l] = [l, w];
        }
        const shape = {sizeX: w, sizeZ: l, heights};
        turnedShapes.set(key, shape);
        return shape;
    }

    // See-through boxes in the shape of a building: one per run of equal-height columns along x.
    function ghostBoxes(boxes, typeId, rotation, minX, minZ, groundY, color) {
        const shape = turnedShape(typeId, rotation);
        for (let z = 0; z < shape.sizeZ; z++) {
            let x = 0;
            while (x < shape.sizeX) {
                const h = shape.heights[x + z * shape.sizeX];
                let run = 1;
                while (x + run < shape.sizeX && shape.heights[x + run + z * shape.sizeX] === h) {
                    run++;
                }
                if (h > 0) {
                    boxes.push({min: [minX + x, groundY + 1, minZ + z], size: [run, Math.max(0.2, h - 1), 1], color});
                }
                x += run;
            }
        }
    }

    // Players as a body in their team's colour with a bare head on top; soldiers the same with a steel helmet
    // and workers with a straw hat, so they tell apart. All are drawn larger when zoomed out so they stay
    // visible. Building sites and the building being placed are see-through copies of the building.
    function figureBoxes() {
        const boxes = [];
        const s = figureScale();
        const figure = (x, y, z, color, head) => {
            const w = 0.6 * s;
            boxes.push({min: [x - w / 2, y, z - w / 2], size: [w, 1.35 * s, w], color});
            const hw = 0.5 * s;
            boxes.push({min: [x - hw / 2, y + 1.35 * s, z - hw / 2], size: [hw, hw, hw], color: head});
        };
        // Zoomed far out they are dots, drawn with the labels.
        const dots = markerMode();
        for (const p of dots ? [] : state.players.values()) {
            figure(p.drawX, p.drawY, p.drawZ, teamColor(p.team), SKIN);
        }
        for (const u of dots ? [] : state.units.values()) {
            if (u.kind === "worker") {
                figure(u.drawX, u.drawY, u.drawZ, teamColor(u.team), SKIN);
                const hw = 0.8 * s;
                boxes.push({min: [u.drawX - hw / 2, u.drawY + 1.8 * s, u.drawZ - hw / 2], size: [hw, 0.12 * s, hw], color: STRAW});
            } else {
                figure(u.drawX, u.drawY, u.drawZ, teamColor(u.team), STEEL);
            }
        }
        // Mobs as a box their own size, hostile ones dark green with red eyes, others a sandy brown.
        for (const m of dots ? [] : state.mobs.values()) {
            const w = Math.max(0.4, m.width || 0.6) * s;
            const h = Math.max(0.4, m.height || 1) * s;
            boxes.push({min: [m.drawX - w / 2, m.drawY, m.drawZ - w / 2], size: [w, h, w],
                color: m.hostile ? MOB : ANIMAL});
            if (m.hostile) {
                boxes.push({min: [m.drawX - w * 0.3, m.drawY + h * 0.78, m.drawZ - w * 0.3], size: [w * 0.6, h * 0.08,
                    w * 0.6], color: ATTACK});
            }
        }
        // Building sites need no see-through copy: their scaffolding stands in the world.
        const spot = placingFootprint();
        if (spot) {
            const color = spot.ok ? [0.45, 1, 0.5, 0.4] : [1, 0.3, 0.25, 0.4];
            ghostBoxes(boxes, placing.type, placing.rotation, spot.x, spot.z, spot.y, color);
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

    function rect(lines, x0, z0, x1, z1, y, color) {
        lines.push([[x0, y, z0], [x1, y, z0], color], [[x1, y, z0], [x1, y, z1], color],
            [[x1, y, z1], [x0, y, z1], color], [[x0, y, z1], [x0, y, z0], color]);
    }

    // Rings under selected units with a line to where each is headed, the last order's marker, and the outlines
    // of buildings in their team's colour.
    function unitLines(now) {
        const lines = [];
        const s = figureScale();
        for (const id of state.selected) {
            const u = state.units.get(id);
            if (!u) {
                continue;
            }
            ring(lines, u.drawX, u.drawY + 0.05, u.drawZ, 0.7 * s, SELECTED);
            const target = u.target ? state.units.get(u.target) || state.players.get(u.target)
                || state.mobs.get(u.target) : null;
            if (u.order === "attack" && target) {
                lines.push([[u.drawX, u.drawY + 0.1, u.drawZ], [target.drawX, target.drawY + 0.1, target.drawZ], ATTACK]);
            } else if (u.order !== "attack" && u.order !== "idle" && u.toX !== undefined) {
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
        for (const b of state.buildings.values()) {
            const color = b.id === state.selectedBuilding ? SELECTED : teamColor(b.team);
            rect(lines, b.x - 0.5, b.z - 0.5, b.x + b.sizeX + 0.5, b.z + b.sizeZ + 0.5, b.y + 1.1, color);
        }
        const spot = placingFootprint();
        if (spot) {
            const color = spot.ok ? SELECTED : ATTACK;
            rect(lines, spot.x, spot.z, spot.x + spot.sizeX, spot.z + spot.sizeZ, spot.y + 1.1, color);
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
        // Town halls are always named; other buildings when selected, still a site, training or damaged.
        for (const b of state.buildings.values()) {
            const type = buildingType(b.type);
            const team = state.teamsById[b.team];
            const hurt = b.complete && b.health < b.maxHealth;
            if (!type || !team || (b.type !== "town_hall" && b.id !== state.selectedBuilding && b.complete && !b.queue
                && !b.research && !hurt)) {
                continue;
            }
            const shape = turnedShape(b.type, b.rotation);
            const top = b.y + Math.max(...shape.heights) + 1;
            const [x, y] = view.project(b.x + b.sizeX / 2, top, b.z + b.sizeZ / 2);
            const text = team.name + " " + type.name.toLowerCase()
                + (b.complete ? (b.level > 1 ? " " + roman(b.level) : "") : " (site)");
            labelCtx.lineWidth = 3;
            labelCtx.strokeStyle = "rgba(0, 0, 0, 0.8)";
            labelCtx.strokeText(text, x, y - 6);
            labelCtx.fillStyle = team.color;
            labelCtx.fillText(text, x, y - 6);
            // Bars under the name: how far a site is built, how far training is, how hurt it is.
            const bars = [];
            if (!b.complete) {
                bars.push(["#f0b43c", b.built || 0]);
            } else if (b.queue && b.queue.length) {
                bars.push(["#5fdc6a", b.progress || 0]);
            }
            if (b.research) {
                bars.push(["#6fa8ff", b.researched || 0]);
            }
            if (hurt) {
                const health = b.health / b.maxHealth;
                bars.push([health > 0.5 ? "#5fdc6a" : health > 0.25 ? "#f0b43c" : "#e8493f", health]);
            }
            bars.forEach(([color, fraction], i) => {
                const width = 56;
                const top = y - 2 + i * 8;
                labelCtx.fillStyle = "rgba(0, 0, 0, 0.75)";
                labelCtx.fillRect(x - width / 2 - 1, top, width + 2, 6);
                labelCtx.fillStyle = color;
                labelCtx.fillRect(x - width / 2, top + 1, width * Math.max(0, Math.min(1, fraction)), 4);
            });
        }
        const dots = markerMode();
        if (dots) {
            drawDots();
        }
        // Wood left in trees being chopped.
        for (const t of dots ? [] : state.trees) {
            if (t.wood >= t.maxWood) {
                continue;
            }
            const [x, y] = view.project(t.x + 0.5, t.top + 2, t.z + 0.5);
            const width = 28;
            labelCtx.fillStyle = "rgba(0, 0, 0, 0.75)";
            labelCtx.fillRect(x - width / 2 - 1, y - 1, width + 2, 5);
            labelCtx.fillStyle = "#c8963c";
            labelCtx.fillRect(x - width / 2, y, width * Math.max(0, Math.min(1, t.wood / t.maxWood)), 3);
        }
        // Health bars over units that are hurt or selected; zoomed far out only over selected ones.
        for (const u of state.units.values()) {
            const selected = state.selected.has(u.id);
            if (!selected && (dots || u.health >= u.maxHealth)) {
                continue;
            }
            const [x, y] = dots ? view.project(u.drawX, u.drawY, u.drawZ).map((v, i) => v - i * 2)
                : view.project(u.drawX, u.drawY + 1.85 * s, u.drawZ);
            const width = 24;
            const fraction = Math.max(0, Math.min(1, u.health / u.maxHealth));
            labelCtx.fillStyle = "rgba(0, 0, 0, 0.75)";
            labelCtx.fillRect(x - width / 2 - 1, y - 9, width + 2, 5);
            labelCtx.fillStyle = fraction > 0.5 ? "#5fdc6a" : fraction > 0.25 ? "#e8c547" : "#e8553f";
            labelCtx.fillRect(x - width / 2, y - 8, width * fraction, 3);
        }
        // And over hurt mobs, in red.
        for (const m of dots ? [] : state.mobs.values()) {
            if (m.health >= m.maxHealth) {
                continue;
            }
            const [x, y] = view.project(m.drawX, m.drawY + (m.height || 1) * s + 0.3, m.drawZ);
            const width = 20;
            labelCtx.fillStyle = "rgba(0, 0, 0, 0.75)";
            labelCtx.fillRect(x - width / 2 - 1, y - 9, width + 2, 5);
            labelCtx.fillStyle = "#e8553f";
            labelCtx.fillRect(x - width / 2, y - 8, width * Math.max(0, Math.min(1, m.health / m.maxHealth)), 3);
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

    // Units, players and hostile mobs as dots, for when the map is zoomed too far out to make out figures. Animals
    // are left out, and selected units get a ring.
    function drawDots() {
        const dot = (thing, radius, fill, stroke) => {
            const [x, y] = view.project(thing.drawX, thing.drawY, thing.drawZ);
            labelCtx.beginPath();
            labelCtx.arc(x, y, radius, 0, 2 * Math.PI);
            labelCtx.fillStyle = fill;
            labelCtx.fill();
            labelCtx.lineWidth = 1;
            labelCtx.strokeStyle = stroke;
            labelCtx.stroke();
        };
        for (const m of state.mobs.values()) {
            if (m.hostile) {
                dot(m, 2, "rgba(61, 107, 64, 0.55)", "rgba(0, 0, 0, 0.35)");
            }
        }
        for (const u of state.units.values()) {
            const team = state.teamsById[u.team];
            dot(u, 3, team ? team.color : "#cccccc", "rgba(0, 0, 0, 0.8)");
            if (state.selected.has(u.id)) {
                dot(u, 5.5, "rgba(0, 0, 0, 0)", "#73ff80");
            }
        }
        for (const p of state.players.values()) {
            const team = state.teamsById[p.team];
            dot(p, 3.5, team ? team.color : "#cccccc", "#ffffff");
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
        // Players and units glide toward their latest reported position between updates, which come every
        // server tick (50 ms), so they move smoothly at any frame rate.
        const glide = 1 - Math.exp(-dt * 15);
        for (const p of [...state.players.values(), ...state.units.values(), ...state.mobs.values()]) {
            const dx = p.x - p.drawX;
            const dy = p.y - p.drawY;
            const dz = p.z - p.drawZ;
            if (Math.abs(dx) > 0.01 || Math.abs(dy) > 0.01 || Math.abs(dz) > 0.01) {
                const jump = Math.abs(dx) > 64 || Math.abs(dz) > 64;
                p.drawX = jump ? p.x : p.drawX + dx * glide;
                p.drawY = jump ? p.y : p.drawY + dy * glide;
                p.drawZ = jump ? p.z : p.drawZ + dz * glide;
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
        // Things move under a still pointer too.
        if (pointer && now - tipTimer > 150) {
            tipTimer = now;
            updateTip();
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
