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
        settlements: [], // {id, x, z, sizeX, sizeZ, y, rotation, free}: where town centers can stand
        mines: [], // {id, x, z, sizeX, sizeZ, y, gold, maxGold, kind, name}: mining sites
        selectedBuilding: null, // id of the viewer's building whose controls the bar shows
        selectedBuildings: new Set(), // ids of every selected building, selectedBuilding among them
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
            setBuildingSelection([]);
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

    // ---------------------------------------------------------------- fog of war

    // As in the game's RTS view: the map is cut into cells FOG_CELL blocks across. A cell is visible while one of
    // the viewer's team's or its allies' units, players or buildings is near it, and stays explored once seen, so
    // its ground and the enemy buildings found there show after they move on, while what moves there is hidden.
    // Cells nobody has been near are black. Observers, and everyone once the game is over or while /rts seeall
    // lifts it, see everything; /rts reveal marks the whole map explored. What was explored is kept in the browser
    // for the game, so reloading the page does not forget it.
    const FOG_CELL = 4;
    const UNIT_SIGHT = 36;
    const PLAYER_SIGHT = 48;
    const BUILDING_SIGHT = 40;
    const LOOKOUT_SIGHT = 60;
    const FOG_INTERVAL_MS = 250;
    const FOG_SAVE_MS = 5000;

    const fog = {
        key: null, // game number, map corner and team the cells are for
        on: false,
        minX: 0,
        minZ: 0,
        columns: 0,
        rows: 0,
        explored: new Uint8Array(0), // 255 where explored, as the map view takes it
        visible: new Uint8Array(0),
        version: 0,
        time: 0,
        saved: 0,
        savedVersion: 0,
    };

    function fogStorageKey() {
        return "wotg-fog:" + fog.key;
    }

    function saveFog() {
        try {
            let text = "";
            for (let i = 0; i < fog.explored.length; i += 8) {
                let bits = 0;
                for (let j = 0; j < 8; j++) {
                    bits |= fog.explored[i + j] ? 1 << j : 0;
                }
                text += String.fromCharCode(bits);
            }
            window.localStorage.setItem(fogStorageKey(), btoa(text));
        } catch (error) {
            // Storage may be full or blocked; the fog is just forgotten on reload then.
        }
        fog.savedVersion = fog.version;
    }

    function loadFog() {
        try {
            const saved = window.localStorage.getItem(fogStorageKey());
            if (!saved) {
                return;
            }
            const text = atob(saved);
            for (let i = 0; i < fog.explored.length; i++) {
                if (text.charCodeAt(i >> 3) & (1 << (i & 7))) {
                    fog.explored[i] = 255;
                }
            }
        } catch (error) {
            // Nothing saved, or storage is blocked.
        }
    }

    // Marks the cells within range of x, z in cells; whether any was not marked yet.
    function fogSee(cells, x, z, range) {
        const c0 = Math.max(0, Math.floor((x - range - fog.minX) / FOG_CELL));
        const c1 = Math.min(fog.columns - 1, Math.floor((x + range - fog.minX) / FOG_CELL));
        const r0 = Math.max(0, Math.floor((z - range - fog.minZ) / FOG_CELL));
        const r1 = Math.min(fog.rows - 1, Math.floor((z + range - fog.minZ) / FOG_CELL));
        const rangeSq = range * range;
        let changed = false;
        for (let r = r0; r <= r1; r++) {
            const dz = fog.minZ + r * FOG_CELL + FOG_CELL / 2 - z;
            for (let c = c0; c <= c1; c++) {
                const dx = fog.minX + c * FOG_CELL + FOG_CELL / 2 - x;
                if (dx * dx + dz * dz <= rangeSq && !cells[r * fog.columns + c]) {
                    cells[r * fog.columns + c] = 255;
                    changed = true;
                }
            }
        }
        return changed;
    }

    function fogIndex(x, z) {
        const c = Math.floor((x - fog.minX) / FOG_CELL);
        const r = Math.floor((z - fog.minZ) / FOG_CELL);
        return c < 0 || r < 0 || c >= fog.columns || r >= fog.rows ? -1 : r * fog.columns + c;
    }

    function fogVisible(x, z) {
        if (!fog.on) {
            return true;
        }
        const i = fogIndex(x, z);
        return i >= 0 && fog.visible[i] > 0;
    }

    function fogExplored(x, z) {
        if (!fog.on) {
            return true;
        }
        const i = fogIndex(x, z);
        return i >= 0 && fog.explored[i] > 0;
    }

    function fogExploredArea(x, z, sizeX, sizeZ) {
        if (!fog.on) {
            return true;
        }
        for (let bx = x; bx < x + sizeX; bx += FOG_CELL) {
            for (let bz = z; bz < z + sizeZ; bz += FOG_CELL) {
                if (fogExplored(bx, bz)) {
                    return true;
                }
            }
        }
        return fogExplored(x + sizeX - 1, z + sizeZ - 1);
    }

    // Works out what the viewer's team sees from a state message, before anything is hidden from it.
    function updateFog(message) {
        const g = state.game;
        const team = myTeam();
        fog.on = !!(g && team && !message.winners && !message.seeAll);
        if (!fog.on) {
            view.setFog(null);
            return;
        }
        const key = g.number + ":" + g.minX + ":" + g.minZ + ":" + team.id;
        if (key !== fog.key) {
            if (fog.key && fog.version !== fog.savedVersion) {
                saveFog();
            }
            fog.key = key;
            fog.minX = g.minX;
            fog.minZ = g.minZ;
            fog.columns = Math.floor((g.maxX - g.minX) / FOG_CELL) + 1;
            fog.rows = Math.floor((g.maxZ - g.minZ) / FOG_CELL) + 1;
            fog.explored = new Uint8Array(fog.columns * fog.rows);
            fog.visible = new Uint8Array(fog.columns * fog.rows);
            fog.time = 0;
            loadFog();
            fog.version++;
            fog.savedVersion = fog.version;
        }
        if (team.revealed && fog.explored.indexOf(0) >= 0) {
            fog.explored.fill(255);
            fog.version++;
        }
        const now = performance.now();
        if (now - fog.time >= FOG_INTERVAL_MS) {
            fog.time = now;
            const friends = new Set(team.allies || []);
            friends.add(team.id);
            const visible = new Uint8Array(fog.explored.length);
            for (const u of message.units || []) {
                if (friends.has(u.team)) {
                    fogSee(visible, u.x, u.z, UNIT_SIGHT);
                }
            }
            for (const p of message.players || []) {
                if (friends.has(p.team)) {
                    fogSee(visible, p.x, p.z, PLAYER_SIGHT);
                }
            }
            for (const b of message.buildings || []) {
                if (friends.has(b.team)) {
                    const lookout = b.complete && (b.type === "tower" || b.type === "town_hall" || b.range > 0);
                    fogSee(visible, b.x + b.sizeX / 2, b.z + b.sizeZ / 2,
                        (lookout ? LOOKOUT_SIGHT : BUILDING_SIGHT) + Math.max(b.sizeX, b.sizeZ) / 2);
                }
            }
            let changed = false;
            for (let i = 0; i < visible.length; i++) {
                if (visible[i] && !fog.explored[i]) {
                    fog.explored[i] = 255;
                    changed = true;
                }
            }
            fog.visible = visible;
            if (changed) {
                fog.version++;
            }
            if (fog.version !== fog.savedVersion && now - fog.saved >= FOG_SAVE_MS) {
                fog.saved = now;
                saveFog();
            }
        }
        view.setFog(fog);
    }

    // Whether the viewer may see a unit, player or building: their own team's and allies' always, the rest only
    // out of the fog.
    function friendly(teamId) {
        const team = myTeam();
        return !!team && (teamId === team.id || (team.allies || []).includes(teamId));
    }

    function setState(message) {
        state.teams = message.teams;
        state.teamsById = {};
        for (const team of message.teams) {
            state.teamsById[team.id] = team;
        }
        updateFog(message);
        if (fog.on) {
            message.players = message.players.filter((p) => friendly(p.team) || fogVisible(p.x, p.z));
            message.units = (message.units || []).filter((u) => friendly(u.team) || fogVisible(u.x, u.z));
            message.mobs = (message.mobs || []).filter((m) => fogVisible(m.x, m.z));
            message.buildings = (message.buildings || []).filter((b) => friendly(b.team)
                || fogExploredArea(b.x, b.z, b.sizeX, b.sizeZ));
            message.trees = (message.trees || []).filter((t) => fogExplored(t.x, t.z));
            message.mines = (message.mines || []).filter((m) => fogExploredArea(m.x, m.z, m.sizeX, m.sizeZ));
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
        state.settlements = message.settlements || [];
        state.mines = message.mines || [];
        state.winners = message.winners || null;
        renderBanner();
        joinRallyGroups();
        tidyGroups();
        // Dead units, and units of a team the viewer has left, drop out of the selection, as do lost buildings.
        const team = myTeam();
        for (const id of [...state.selected]) {
            const u = state.units.get(id);
            if (!u || !team || u.team !== team.id) {
                state.selected.delete(id);
            }
        }
        setBuildingSelection([...state.selectedBuildings].filter((id) => {
            const b = state.buildings.get(id);
            return b && team && b.team === team.id;
        }), state.selectedBuilding);
        renderPanel();
        renderUnitBar();
        view.dirty = true;
    }

    // m:ss, such as 1:05.
    function clock(seconds) {
        return Math.floor(seconds / 60) + ":" + String(seconds % 60).padStart(2, "0");
    }

    // Across the top of the map: who won once the game is over, otherwise how long each team that lost its last
    // town center has left to raise a new one.
    function renderBanner() {
        const banner = document.getElementById("banner");
        const lines = [];
        if (state.winners) {
            const names = state.winners.map((id) => state.teamsById[id] ? state.teamsById[id].name : id);
            lines.push(names.length ? "Game over: " + names.join(" and ") + (names.length === 1 ? " wins!" : " win!")
                : "Game over: nobody won");
        } else {
            for (const team of state.teams) {
                if (team.countdown !== undefined) {
                    lines.push(team.name + " lost its last town center: " + clock(team.countdown)
                        + " to raise a new one");
                }
            }
        }
        const text = lines.join("\n");
        if (banner.textContent !== text) {
            banner.textContent = text;
        }
        banner.hidden = !lines.length;
    }

    function myTeam() {
        if (!state.me || state.me.observer) {
            return null;
        }
        return state.teams.find((t) => t.members.some((m) => m.uuid === state.me.uuid)) || null;
    }

    // ---------------------------------------------------------------- side panel

    const RESOURCES = [["food", "Food"], ["wood", "Wood"], ["gold", "Gold"], ["favor", "Favor"]];

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
                cell.title = label;
                cell.append(sprite(id), document.createTextNode(String(team.resources[id])));
                resources.append(cell);
            }
            box.append(resources);

            if (team.defeated) {
                box.append(el("div", "bad", "Defeated"));
            } else if (team.countdown !== undefined) {
                box.append(el("div", "bad", "No town center: " + clock(team.countdown) + " to raise a new one"));
            }

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
    let selectionBox = null; // {x0, y0, x1, y1, attack} in CSS pixels while dragging one; attack draws it red
    // Whether the next click sends the selected warriors and archers on an attack move.
    let attackMoving = false;
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

    function selectedFighters() {
        return [...state.selected].filter((id) => {
            const u = state.units.get(id);
            return u && u.kind !== "worker";
        });
    }

    // The next click sends the selected warriors and archers on an attack move.
    function startAttackMove() {
        if (placing) {
            stopPlacing();
        }
        if (lighting) {
            stopLighting();
        }
        attackMoving = true;
        showToast("Attack move: click where to go", false);
        renderUnitBar();
    }

    // Sends the selected warriors and archers on an attack move to the ground clicked, fighting whatever enemies
    // they meet; an enemy clicked they attack straight away.
    function attackMove(px, py, hit, shift) {
        attackMoving = false;
        renderUnitBar();
        const units = selectedFighters();
        if (!units.length) {
            return;
        }
        if (hit && isEnemy(hit)) {
            const t = hit.thing;
            orderMark = {x: t.drawX, y: t.drawY, z: t.drawZ, color: [1, 0.3, 0.25, 1], time: performance.now()};
            send("units.attack", {units, target: entityId(hit), queue: Boolean(shift)});
            return;
        }
        const ground = groundUnder(px, py);
        if (!ground) {
            return;
        }
        orderMark = {x: ground[0], y: ground[1], z: ground[2], color: [1, 0.3, 0.25, 1], time: performance.now()};
        // Shift adds it as a waypoint after the moves they are on.
        const body = {units, x: ground[0], z: ground[2], formation, attack: true, queue: Boolean(shift)};
        send("units.move", body).then((reply) => {
            if (reply) {
                showToast("Attack move", false);
            }
        });
        view.dirty = true;
    }

    // Sends the selected warriors and archers at the enemies drawn inside a screen rectangle, shared out among them,
    // after the enemies they were already ordered at with add. With no enemy units or players in it, at the hostile
    // mobs in it. Returns whether there was anything to attack.
    function attackIn(x0, y0, x1, y1, add) {
        const [left, right] = [Math.min(x0, x1), Math.max(x0, x1)];
        const [top, bottom] = [Math.min(y0, y1), Math.max(y0, y1)];
        const s = figureScale();
        const inside = (t) => {
            const [x, y] = view.project(t.drawX, t.drawY + 0.9 * s, t.drawZ);
            return x >= left && x <= right && y >= top && y <= bottom;
        };
        const enemies = [];
        for (const u of state.units.values()) {
            if (isEnemyTeam(u.team) && inside(u)) {
                enemies.push(u);
            }
        }
        for (const p of state.players.values()) {
            if (isEnemyTeam(p.team) && inside(p)) {
                enemies.push(p);
            }
        }
        const targets = enemies.length ? enemies
            : [...state.mobs.values()].filter((m) => m.hostile && !m.carcass && inside(m));
        if (!targets.length) {
            return false;
        }
        const units = selectedFighters();
        const n = targets.length;
        orderMark = {x: targets.reduce((a, t) => a + t.drawX, 0) / n, y: targets.reduce((a, t) => a + t.drawY, 0) / n,
            z: targets.reduce((a, t) => a + t.drawZ, 0) / n, color: [1, 0.3, 0.25, 1], time: performance.now()};
        send("units.attack", {units, targets: targets.map((t) => t.uuid || t.id), queue: Boolean(add)})
            .then((reply) => {
                if (reply) {
                    showToast("Attacking " + n + (n === 1 ? " enemy" : " enemies"), false);
                }
            });
        view.dirty = true;
        return true;
    }

    function selectedWorkers() {
        return [...state.selected].filter((id) => {
            const u = state.units.get(id);
            return u && u.kind === "worker";
        });
    }

    // Who builds what is placed: the selected workers, or with none, the selected dark wizards, who raise stone
    // circles and nothing else.
    function selectedBuilders() {
        const workers = selectedWorkers();
        return workers.length ? workers : [...state.selected].filter((id) => {
            const u = state.units.get(id);
            return u && u.kind === "dark_wizard";
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

    // The building, unit and resource icons in icons.png, one 16 pixel square each, in this order.
    const SPRITES = ["town_hall", "house", "barracks", "storehouse", "quarry", "farm", "armory", "tower", "worker",
        "soldier", "archer", "tree", "upgrade", "level", "food", "wood", "gold", "sword", "axe", "pickaxe", "hoe", "wall",
        "gate", "white_knights_fortress", "black_knights_fortress", "white_knight", "black_knight", "tradigee",
        "sagittare", "archery_range", "stables", "cavalry", "horse", "favor", "chaos_temple", "monastery",
        "wizards_tower", "stone_circle", "chaos_priest", "monk", "dark_wizard", "zamorak_mage", "blue_wizard",
        "lesser_demon", "centaur"];

    function sprite(name) {
        const span = el("span", "sprite");
        span.style.backgroundPosition = -16 * Math.max(0, SPRITES.indexOf(name)) + "px 0";
        return span;
    }

    // The square icons of the build, train and upgrade buttons in actions.png, named in actions.json; the RTS view
    // draws the same ones.
    let actionIcons = null;
    fetch("actions.json").then((response) => response.ok ? response.json() : null).then((json) => {
        if (json) {
            actionIcons = {size: json.size, columns: json.columns, rows: Math.ceil(json.names.length / json.columns),
                index: new Map(json.names.map((name, i) => [name, i]))};
            renderUnitBar();
        }
    }).catch(() => {});

    const SQUARE = 36;

    // A square button showing an icon, with what it does in its tooltip; fallback is one of the small icons, for
    // before actions.json is in or when it has no icon of that name.
    function squareButton(icon, fallback, title, onClick) {
        const b = button("", title, onClick);
        b.classList.add("square");
        const i = actionIcons ? actionIcons.index.get(icon) : undefined;
        if (i === undefined) {
            b.append(sprite(fallback));
        } else {
            const span = el("span", "action-icon");
            let rows = 1;
            while (rows < actionIcons.rows) {
                rows *= 2;
            }
            span.style.backgroundSize = actionIcons.columns * SQUARE + "px " + rows * SQUARE + "px";
            span.style.backgroundPosition = -(i % actionIcons.columns) * SQUARE + "px "
                + -Math.floor(i / actionIcons.columns) * SQUARE + "px";
            b.append(span);
        }
        return b;
    }

    function withBadge(b, text) {
        if (text) {
            b.append(el("span", "badge", text));
        }
        return b;
    }

    function withBar(b, kind) {
        const bar = el("span", "progress " + kind);
        bar.append(el("span"));
        b.append(bar);
        return b;
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
        if (placing && placing.type === "bridge") {
            const bridge = placingWall();
            actions.append(el("span", "note", placing.start
                ? "Bridge: click the far bank" + (bridge ? ", " + bridge.pieces.length + " blocks for "
                    + costText(wallCost(bridge.pieces.length, "bridge")) : "")
                : "Bridge: click the bank it starts from"));
            actions.append(button("Cancel", "Esc or right-click", () => stopPlacing()));
        } else if (placing && placing.type === "wall") {
            const wall = placingWall();
            actions.append(el("span", "note", placing.start
                ? "Wall: click where it ends" + (wall ? ", " + wall.pieces.length + " pieces for "
                    + costText(wallCost(wall.pieces.length)) : "")
                : "Wall: click where it starts, or on one of your towers to run it from there"));
            actions.append(button("Cancel", "Esc or right-click", () => stopPlacing()));
        } else if (placing) {
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
        } else if (selectedBuilders().length) {
            const wizards = !selectedWorkers().length;
            for (const [id, type] of Object.entries(state.game.buildingTypes || {})) {
                // Each god's buildings belong to its own teams; dark wizards raise stone circles, workers the rest.
                if (!type.buildable || (type.faction && type.faction !== team.faction) || Boolean(type.wizards) !== wizards) {
                    continue;
                }
                const aged = (team.age || 1) >= (type.age || 1);
                const needed = type.requires && !(team.upgrades || []).includes(type.requires)
                    ? (state.game.upgrades || {})[type.requires] || {name: type.requires} : null;
                const title = type.name + " (" + costText(type.cost)
                    + (id === "wall" ? " a piece)" : id === "bridge" ? " a block)" : ")") + "\n"
                    + (!aged ? "Needs " + ageName(type.age) + ": take your town center up a level"
                        : needed ? "Needs " + needed.name + ": research it first"
                        : id === "wall" ? "Build a wall: click where it starts and where it ends, or on two of your towers"
                            : id === "bridge" ? "Build a wooden bridge: click one bank, then the other"
                                : "Build a " + type.name.toLowerCase() + ", then click on the map where it goes");
                const b = squareButton("building/" + team.faction + "/" + id, id, title, () => startPlacing(id));
                b.disabled = !aged || Boolean(needed) || !canAfford(team, type.cost);
                actions.append(b);
            }
            if (!wizards) {
                actions.append(squareButton("torches", "wood", "Torches\nLight up an area so no mobs spawn there: click"
                    + " two corners. Each torch costs wood", () => startLighting()));
            }
        } else {
            const hall = townHallOf(team.id);
            if (hall) {
                actions.append(squareButton("building/" + team.faction + "/town_hall", "town_hall",
                    "Town center\nSelect your town center to train workers", () => {
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
        const info = el("span");
        for (const [n, group] of [...groups].sort((a, b) => a[0] - b[0])) {
            const size = group.units.size + group.buildings.size;
            if (size) {
                const b = button(n + "\u00B7" + size, "Control group " + n + ": press " + n + " to select it, again"
                    + " to go to it", () => recallGroup(n));
                b.classList.add("group");
                info.append(b);
            }
        }
        const idle = idleWorkers().length;
        if (idle) {
            const idleButton = iconButton("worker", idle + " idle", "Go to the next idle worker; shift-click selects"
                + " every idle worker", (event) => selectIdleWorkers(event.shiftKey));
            idleButton.classList.add("idle");
            info.append(idleButton);
        }
        if (count) {
            // How many of each kind are selected; clicking one narrows the selection, as in Age of Mythology.
            const kinds = new Map();
            for (const id of state.selected) {
                const u = state.units.get(id);
                if (u) {
                    kinds.set(u.kind, (kinds.get(u.kind) || 0) + 1);
                }
            }
            for (const [kind, n] of kinds) {
                const k = unitKind(kind);
                const name = k ? k.name.toLowerCase() + "s" : kind;
                info.append(iconButton(kind, String(n), kinds.size > 1 ? "Click or ctrl-click: keep only the " + name
                    + "; shift-click: drop the " + name : n + " " + name, (event) => {
                    if (kinds.size > 1) {
                        filterSelection(kind, event.shiftKey);
                    }
                }));
            }
        } else if (building && state.selectedBuildings.size > 1) {
            info.append(document.createTextNode(state.selectedBuildings.size + " buildings selected"));
        } else {
            info.append(document.createTextNode(team.units ? "None selected" : "No units yet"));
        }
        swapIfChanged(document.getElementById("selection-info"), info);
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
        const move = button("Attack move (Space)", "Then click where to go: they fight any enemy they meet on the way",
            startAttackMove);
        move.classList.toggle("on", attackMoving);
        actions.append(move);
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
        const alike = selectedBuildingList().filter((b) => b.type === building.type).length;
        title.append(sprite(building.type), document.createTextNode(type.name
            + (building.level > 1 ? " " + roman(building.level) : "") + (alike > 1 ? " \u00D7" + alike : "")));
        actions.append(title);
        actions.append(el("span", "note", "Health " + building.health + "/" + building.maxHealth));
        if (building.type === "wall" && building.team === team.id) {
            const gateCost = buildingType("gate").cost || {};
            const gate = squareButton("gate", "gate", "Make gate (" + costText(gateCost) + ")\n"
                + "Make this piece of wall a gate at once, which opens for your units and your allies'", async () => {
                    if (await send("buildings.gate", {building: building.id})) {
                        showToast("Gate made");
                    }
                });
            gate.disabled = !canAfford(team, gateCost);
            actions.append(gate);
        }
        if (type.workers) {
            actions.append(el("span", "note", (building.workers || 0) + " of " + type.workers + " workers"));
        }
        const teamFull = teamUnitCount(team) >= (team.population || 0);
        for (const kindId of type.trains || []) {
            const kind = unitKind(kindId);
            if (!kind) {
                continue;
            }
            // A horse is no unit, and needs no room.
            const full = !kind.animal && teamFull;
            // With several buildings selected, training is shared out among those that train this kind.
            const trainers = selectedBuildingList().filter((b) => b.complete
                && (buildingType(b.type).trains || []).includes(kindId));
            // A team has one of each hero at a time, and may train him again once he falls.
            const heroTaken = kind.hero && hasUnitOf(team, kindId);
            const train = squareButton("face/" + team.faction + "/" + kindId, kindId,
                kind.name + " (" + costText(kind.cost) + ")\n"
                + (heroTaken ? kind.name + " is already with your team; train him again once he falls"
                    : full ? (team.population < state.game.unitsPerTeam ? "Build houses to house more units"
                        : "Your team has the most units it can have")
                        : kind.hero ? "Train " + kind.name + ", a hero; your team has one at a time"
                            : "Train a " + kind.name.toLowerCase() + "; shift-click for 5"
                            + (trainers.length > 1 ? ", shared out over the " + trainers.length + " selected" : "")),
                async (event) => {
                    const {total, buildings} = await trainSpread(trainers, kindId, kind.hero ? 1 : event.shiftKey ? 5 : 1);
                    if (total) {
                        showToast(kind.hero ? "Training " + kind.name
                            : (total === 1 ? "Training a " + kind.name.toLowerCase()
                                : "Training " + total + " " + kind.name.toLowerCase() + "s")
                            + (buildings > 1 ? " at " + buildings + " buildings" : ""));
                    }
                });
            train.disabled = heroTaken || full || !canAfford(team, kind.cost)
                || !trainers.some((b) => (b.queue || []).length < 10);
            const waiting = trainers.reduce((n, b) => n + (b.queue || []).filter((k) => k === kindId).length, 0);
            actions.append(withBadge(train, waiting ? String(waiting) : ""));
        }
        if (building.queue && building.queue.length) {
            // The queue, the one in training first; clicking one takes it off and gives back what it cost.
            actions.append(el("span", "note", "Training"));
            building.queue.forEach((kindId, i) => {
                const kind = unitKind(kindId);
                const name = !kind ? kindId : kind.hero ? kind.name : kind.name.toLowerCase();
                const b = squareButton("face/" + team.faction + "/" + kindId, kindId,
                    (i === 0 ? "Training " + name : "Waiting to train " + name)
                    + "\nClick to cancel and get back what it cost", () => cancelAt(building, i));
                b.classList.add("small");
                actions.append(i === 0 ? withBar(b, "") : b);
            });
        }
        if ((type.trains || []).length) {
            const n = rallyGroups.get(building.id);
            const rallied = n !== undefined || building.rallyX !== undefined;
            actions.append(el("span", "note", n !== undefined ? "New units join group " + n
                : rallied ? "Rally point set" : "Right-click to set a rally point"));
            if (rallied) {
                actions.append(button("Clear rally", "New units stay at the door", () => clearRally(selectedBuildingList())));
            }
        }
        if (building.research) {
            const research = building.research;
            actions.append(el("span", "note", "Researching"));
            actions.append(withBar(squareButton(research === "level" ? "level" : "upgrade/" + research,
                research === "level" ? "level" : "upgrade",
                researchName(research) + "\nClick to cancel and get back what it cost", () => cancelAt(building, -1)),
            "research"));
            return;
        }
        const next = type.levelUps ? type.levelUps[building.level + 1] : null;
        if (next) {
            const center = building.type === "town_hall";
            const ready = center || (team.age || 1) > building.level;
            const up = squareButton("level", "level", (center ? "Advance to " + ageName(building.level + 1)
                : "Level " + (building.level + 1)) + " (" + costText(next.cost) + ")\n"
                + (ready ? "Takes " + next.seconds + "s; every building takes on the new age's look"
                    : "Your town has to reach " + ageName(building.level + 1) + " first"), async () => {
                    if (await send("buildings.upgrade", {building: building.id})) {
                        showToast("Going up a level");
                    }
                });
            up.disabled = !ready || !canAfford(team, next.cost);
            actions.append(up);
        }
        for (const upgradeId of type.researches || []) {
            const upgrade = state.game.upgrades[upgradeId];
            // Ones that need another upgrade first only show up once the team has it.
            if (!upgrade || (team.upgrades || []).includes(upgradeId)
                || (upgrade.requires && !(team.upgrades || []).includes(upgrade.requires))) {
                continue;
            }
            const busy = [...state.buildings.values()].some((b) => b.team === team.id && b.research === upgradeId);
            const levelOk = (team.age || 1) >= upgrade.level;
            const research = squareButton("upgrade/" + upgradeId, "upgrade",
                upgrade.name + " (" + costText(upgrade.cost) + ")\n"
                + (!levelOk ? "Needs " + ageName(upgrade.level)
                    : busy ? "Being researched" : upgrade.description + "; takes " + upgrade.seconds + "s"),
                async () => {
                    if (await send("buildings.research", {building: building.id, upgrade: upgradeId})) {
                        showToast("Researching " + upgrade.name.toLowerCase());
                    }
                });
            research.disabled = !levelOk || busy || !canAfford(team, upgrade.cost);
            actions.append(research);
        }
    }

    // Takes the unit at index off a building's training queue, or with index -1 stops its research, getting the
    // cost back.
    async function cancelAt(building, index) {
        const body = index < 0 ? {building: building.id, research: true} : {building: building.id, index};
        if (await send("buildings.cancel", body)) {
            showToast("Cancelled");
        }
    }

    // Delete: kills your selected units and destroys your selected buildings.
    async function deleteSelection() {
        const team = myTeam();
        if (!team) {
            return;
        }
        const units = [...state.selected].filter((id) => {
            const u = state.units.get(id);
            return u && u.team === team.id;
        });
        const buildings = selectedBuildingList().filter((b) => b.team === team.id).map((b) => b.id);
        if (units.length) {
            for (const id of units) {
                state.selected.delete(id);
            }
            changedSelection();
            if (await send("units.kill", {units})) {
                showToast(units.length === 1 ? "Unit killed" : units.length + " units killed");
            }
        }
        if (buildings.length) {
            setBuildingSelection([]);
            changedSelection();
            if (await send("buildings.destroy", {buildings})) {
                showToast(buildings.length === 1 ? "Building destroyed" : buildings.length + " buildings destroyed");
            }
        }
    }

    function selectBuilding(id) {
        setBuildingSelection([id]);
        state.selected.clear();
        changedSelection();
    }

    // Selects these buildings; the bar shows the controls of the first, or of primary if it is among them.
    function setBuildingSelection(ids, primary) {
        state.selectedBuildings = new Set(ids);
        state.selectedBuilding = state.selectedBuildings.has(primary) ? primary : ids.length ? ids[0] : null;
    }

    function selectedBuildingList() {
        return [...state.selectedBuildings].map((id) => state.buildings.get(id)).filter(Boolean);
    }

    // Workers with nothing to do.
    function idleWorkers() {
        return [...state.units.values()].filter((u) => isMine(u) && u.kind === "worker" && doingText(u) === "idle")
            .sort((a, b) => a.number - b.number);
    }

    let lastIdle = 0; // number of the idle worker the idle button last went to

    // Selects and goes to the next idle worker, or with shift every idle worker.
    function selectIdleWorkers(all) {
        const idle = idleWorkers();
        if (!idle.length) {
            return;
        }
        const next = idle.find((u) => u.number > lastIdle) || idle[0];
        state.selected.clear();
        for (const u of all ? idle : [next]) {
            state.selected.add(u.id);
        }
        lastIdle = next.number;
        setFollow(false);
        view.focus(next.drawX, next.drawY, next.drawZ);
        changedSelection();
    }

    // Narrows the selection by kind: keep only that kind, or with drop set, keep every other kind.
    function filterSelection(kind, drop) {
        for (const id of [...state.selected]) {
            const u = state.units.get(id);
            if (!u || (u.kind === kind) === drop) {
                state.selected.delete(id);
            }
        }
        changedSelection();
    }

    // Queues count units of a kind over the buildings, each going to whichever has the shortest queue then.
    async function trainSpread(buildings, kindId, count) {
        const queued = new Map(buildings.map((b) => [b.id, (b.queue || []).length]));
        const plan = new Map();
        for (let i = 0; i < count; i++) {
            let best = null;
            for (const b of buildings) {
                const length = queued.get(b.id);
                if (length < 10 && (best === null || length < queued.get(best))) {
                    best = b.id;
                }
            }
            if (best === null) {
                break;
            }
            queued.set(best, queued.get(best) + 1);
            plan.set(best, (plan.get(best) || 0) + 1);
        }
        let total = 0;
        for (const [id, n] of plan) {
            const reply = await send("buildings.train", {building: id, unit: kindId, count: n});
            if (!reply) {
                break;
            }
            total += reply.count || n;
        }
        return {total, buildings: plan.size};
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
        return command(type, body).then((reply) => {
            // Done, but not all of it, such as when some workers found no room at a farm.
            if (reply && reply.notice) {
                showToast(reply.notice, true);
            }
            return reply;
        }, (error) => {
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
        placing = {type, rotation: placing ? placing.rotation : 0, cx: null, cz: null, start: null};
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
        if (!placing || placing.cx === null || placing.type === "wall" || placing.type === "bridge") {
            return null;
        }
        const type = buildingType(placing.type);
        if (placing.type === "town_hall") {
            // A town center snaps onto the free settlement under the pointer, facing the way it does.
            const settlement = freeSettlementNear(placing.cx, placing.cz, 4);
            if (settlement) {
                return {x: settlement.x, z: settlement.z, sizeX: settlement.sizeX, sizeZ: settlement.sizeZ, ok: true,
                    y: settlement.y, rotation: settlement.rotation};
            }
        }
        if (placing.type === "quarry") {
            // A quarry snaps into the middle of the mined out mine under the pointer.
            const mine = freeMineNear(placing.cx, placing.cz, 4);
            if (mine) {
                const [qx, qz] = footprintSize(type, placing.rotation);
                return {x: mine.x + Math.floor((mine.sizeX - qx) / 2), z: mine.z + Math.floor((mine.sizeZ - qz) / 2),
                    sizeX: qx, sizeZ: qz, ok: true, y: mine.y, rotation: placing.rotation};
            }
        }
        const [sizeX, sizeZ] = footprintSize(type, placing.rotation);
        const x = placing.cx - Math.floor(sizeX / 2);
        const z = placing.cz - Math.floor(sizeZ / 2);
        const g = state.game;
        let ok = placing.type !== "town_hall" && placing.type !== "quarry" && x >= g.minX && z >= g.minZ && x + sizeX - 1 <= g.maxX
            && z + sizeZ - 1 <= g.maxZ;
        const gap = g.buildingGap || 0;
        const overlaps = (b, bx, bz) => x < bx + b.sizeX + gap && bx < x + sizeX + gap && z < bz + b.sizeZ + gap
            && bz < z + sizeZ + gap;
        for (const b of state.buildings.values()) {
            if (overlaps(b, b.x, b.z)) {
                ok = false;
            }
        }
        // Settlements are kept for town centers, and gold mines clear.
        for (const f of state.settlements.concat(state.mines)) {
            if (overlaps(f, f.x, f.z)) {
                ok = false;
            }
        }
        return {x, z, sizeX, sizeZ, ok, y: surfaceY(placing.cx, placing.cz) - 1, rotation: placing.rotation};
    }

    // The wall being laid out, from where it starts to the pointer (or the pointer alone before the first click), as
    // its pieces and whether it fits; see WallLine.java, which the server lays the wall out with.
    function placingWall() {
        if (placing && placing.type === "bridge" && placing.cx !== null) {
            return placingBridge();
        }
        if (!placing || placing.type !== "wall" || placing.cx === null) {
            return null;
        }
        const team = myTeam();
        const towerAt = (x, z) => [...state.buildings.values()].find((b) => team && b.team === team.id
            && b.type === "tower" && x >= b.x - 1 && x < b.x + b.sizeX + 1 && z >= b.z - 1 && z < b.z + b.sizeZ + 1);
        const end = (x, z) => {
            const tower = towerAt(x, z);
            return tower ? [Math.floor(tower.x + tower.sizeX / 2), Math.floor(tower.z + tower.sizeZ / 2)] : [x, z];
        };
        const [x1, z1] = placing.start ? end(placing.start.x, placing.start.z) : end(placing.cx, placing.cz);
        const [x2, z2] = end(placing.cx, placing.cz);
        const own = (b) => team && b.team === team.id && (b.type === "tower" || isWallType(b.type));
        const S = WALL_SIZE;
        const blocker = (minX, minZ) => {
            let box = null;
            for (const b of state.buildings.values()) {
                if (own(b) && minX < b.x + b.sizeX && b.x < minX + S && minZ < b.z + b.sizeZ && b.z < minZ + S) {
                    box = box ? union(box, b) : {x: b.x, z: b.z, sizeX: b.sizeX, sizeZ: b.sizeZ};
                }
            }
            return box;
        };
        const g = state.game;
        const pieces = [];
        for (const p of wallPieces(x1, z1, x2, z2, blocker)) {
            p.y = surfaceY(p.x + p.sizeX / 2, p.z + p.sizeZ / 2) - 1;
            let fits = p.x >= g.minX && p.z >= g.minZ && p.x + p.sizeX - 1 <= g.maxX && p.z + p.sizeZ - 1 <= g.maxZ;
            const hits = (f, gap) => p.x < f.x + f.sizeX + gap && f.x < p.x + p.sizeX + gap
                && p.z < f.z + f.sizeZ + gap && f.z < p.z + p.sizeZ + gap;
            for (const b of state.buildings.values()) {
                if (hits(b, 0)) {
                    fits = false;
                }
            }
            for (const f of state.settlements.concat(state.mines)) {
                if (hits(f, 1)) {
                    fits = false;
                }
            }
            // The server leaves out a short segment that does not fit, as the wall stands without it.
            if (!fits && (p.sizeX < S || p.sizeZ < S)) {
                continue;
            }
            p.ok = fits;
            pieces.push(p);
        }
        const ok = pieces.length > 0 && pieces.every((p) => p.ok);
        return {pieces, ok};
    }

    // The bridge being laid out, as the server will lay it (see BridgeLine.java): straight along x or z from the
    // first point, a block at a time, leaving out what the team has built of it already. Its deck lies a block over
    // the water, or in the ground on land.
    function placingBridge() {
        const team = myTeam();
        const x1 = placing.start ? placing.start.x : placing.cx;
        const z1 = placing.start ? placing.start.z : placing.cz;
        const alongX = Math.abs(placing.cx - x1) >= Math.abs(placing.cz - z1);
        const from = alongX ? x1 : z1;
        const to = alongX ? placing.cx : placing.cz;
        const step = to >= from ? 1 : -1;
        const g = state.game;
        const pieces = [];
        let water = false;
        for (let at = from; pieces.length < 48; at += step) {
            const p = alongX ? {x: at, z: z1 - 2, sizeX: 1, sizeZ: 5, rotation: 0}
                : {x: x1 - 2, z: at, sizeX: 5, sizeZ: 1, rotation: 1};
            p.type = "bridge";
            const hits = (f) => p.x < f.x + f.sizeX && f.x < p.x + p.sizeX && p.z < f.z + f.sizeZ && f.z < p.z + p.sizeZ;
            let fits = p.x >= g.minX && p.z >= g.minZ && p.x + p.sizeX - 1 <= g.maxX && p.z + p.sizeZ - 1 <= g.maxZ;
            let built = false;
            for (const b of state.buildings.values()) {
                if (hits(b)) {
                    if (team && b.team === team.id && b.type === "bridge") {
                        built = true;
                    } else {
                        fits = false;
                    }
                }
            }
            for (const f of state.settlements.concat(state.mines)) {
                if (hits(f)) {
                    fits = false;
                }
            }
            if (!built) {
                const cx = Math.floor(p.x + p.sizeX / 2);
                const cz = Math.floor(p.z + p.sizeZ / 2);
                const top = surfaceY(cx, cz) - 1;
                const value = view.map.chunk(cx >> 4, cz >> 4) ? view.map.block(cx, top, cz) : 0;
                const wet = value >> 4 === 8 || value >> 4 === 9;
                water = water || wet;
                // The ghost goes a block over its ground, so a block under the deck.
                p.y = (wet ? top + 1 : top) - 1;
                p.ok = fits;
                pieces.push(p);
            }
            if (at === to) {
                break;
            }
        }
        const ok = water && pieces.length > 0 && pieces.every((p) => p.ok);
        return {pieces, ok, dry: !water};
    }

    function isWallType(type) {
        return type === "wall" || type === "wall_post" || type === "gate";
    }

    function wallCost(pieces, type = "wall") {
        const cost = {};
        for (const [resource, amount] of Object.entries(buildingType(type).cost || {})) {
            cost[resource] = amount * pieces;
        }
        return cost;
    }

    const WALL_SIZE = 5;

    // The box holding box a and box b.
    function union(a, b) {
        const x = Math.min(a.x, b.x);
        const z = Math.min(a.z, b.z);
        return {x, z, sizeX: Math.max(a.x + a.sizeX, b.x + b.sizeX) - x, sizeZ: Math.max(a.z + a.sizeZ, b.z + b.sizeZ) - z};
    }

    // The 5 by 5 pieces of a wall from x1, z1 to x2, z2: straight where it can, a zigzag of single steps on a slant,
    // posts where it turns or ends, left out where blocker finds a tower or wall, with short segments closing any gap
    // left against one. The same as WallLine.java.
    function wallPieces(x1, z1, x2, z2, blocker) {
        const S = WALL_SIZE;
        const ox = x1 - Math.floor(S / 2);
        const oz = z1 - Math.floor(S / 2);
        const endI = Math.floor((x2 - ox) / S);
        const endJ = Math.floor((z2 - oz) / S);
        const cells = [[0, 0]];
        let i = 0;
        let j = 0;
        while ((i !== endI || j !== endJ) && cells.length < 64) {
            let si = Math.sign(endI - i);
            let sj = Math.sign(endJ - j);
            if (si !== 0 && sj !== 0) {
                const alongI = Math.abs(endJ * (i + si) - endI * j);
                const alongJ = Math.abs(endJ * i - endI * (j + sj));
                if (alongJ < alongI) {
                    si = 0;
                } else {
                    sj = 0;
                }
            }
            i += si;
            j += sj;
            cells.push([i, j]);
        }
        const boxes = cells.map(([ci, cj]) => blocker(ox + ci * S, oz + cj * S));
        const kept = boxes.map((box) => !box);
        cells.forEach(([ci, cj], k) => {
            if (kept[k]) {
                boxes[k] = {x: ox + ci * S, z: oz + cj * S, sizeX: S, sizeZ: S};
            }
        });
        const pieces = [];
        cells.forEach(([ci, cj], k) => {
            if (kept[k]) {
                const before = k > 0 && kept[k - 1] ? [ci - cells[k - 1][0], cj - cells[k - 1][1]] : null;
                const after = k < cells.length - 1 && kept[k + 1] ? [cells[k + 1][0] - ci, cells[k + 1][1] - cj] : null;
                const post = !before || !after || before[0] !== after[0] || before[1] !== after[1];
                pieces.push({x: boxes[k].x, z: boxes[k].z, sizeX: S, sizeZ: S, type: post ? "wall_post" : "wall",
                    rotation: post ? 0 : before[0] !== 0 ? 0 : 1});
            }
            if (k === cells.length - 1 || (kept[k] && kept[k + 1])) {
                return;
            }
            // Short segments closing the gap, if any, to the next box.
            const di = cells[k + 1][0] - ci;
            const dj = cells[k + 1][1] - cj;
            const a = boxes[k];
            const b = boxes[k + 1];
            const from = di > 0 ? a.x + a.sizeX : di < 0 ? b.x + b.sizeX : dj > 0 ? a.z + a.sizeZ : b.z + b.sizeZ;
            const to = di > 0 ? b.x : di < 0 ? a.x : dj > 0 ? b.z : a.z;
            if (to - from <= 0 || to - from > 2 * S) {
                return;
            }
            const run = [];
            for (let at = from; at < to; at += S) {
                const length = Math.min(S, to - at);
                run.push(di !== 0 ? {x: at, z: oz + cj * S, sizeX: length, sizeZ: S, type: "wall", rotation: 0}
                    : {x: ox + ci * S, z: at, sizeX: S, sizeZ: length, type: "wall", rotation: 1});
            }
            if (di < 0 || dj < 0) {
                run.reverse();
            }
            pieces.push(...run);
        });
        return pieces;
    }

    // The free settlement whose footprint holds x, z or comes within margin blocks of it, nearest first.
    function freeSettlementNear(x, z, margin) {
        return freeFeatureNear(state.settlements, x, z, margin);
    }

    // The mined out mine with no quarry on it whose footprint holds x, z or comes within margin blocks of it.
    function freeMineNear(x, z, margin) {
        return freeFeatureNear(state.mines, x, z, margin);
    }

    function freeFeatureNear(features, x, z, margin) {
        let best = null;
        let bestDistance = Infinity;
        for (const f of features) {
            if (!f.free || x < f.x - margin || x >= f.x + f.sizeX + margin || z < f.z - margin
                || z >= f.z + f.sizeZ + margin) {
                continue;
            }
            const distance = (f.x + f.sizeX / 2 - x) ** 2 + (f.z + f.sizeZ / 2 - z) ** 2;
            if (distance < bestDistance) {
                best = f;
                bestDistance = distance;
            }
        }
        return best;
    }

    // The gold mine whose footprint holds x, z, or null.
    function mineAt(x, z) {
        return state.mines.find((m) => x >= m.x && x < m.x + m.sizeX && z >= m.z && z < m.z + m.sizeZ) || null;
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
        if (placing.type === "wall" || placing.type === "bridge") {
            placeWall(keep);
            return;
        }
        const spot = placingFootprint();
        if (!spot) {
            return;
        }
        if (!spot.ok) {
            showToast(placing.type === "town_hall" ? "A town center can only go on a settlement"
                : placing.type === "quarry" ? "A quarry can only go on a mined out gold mine"
                : "It does not fit there", true);
            return;
        }
        const workers = selectedBuilders();
        if (!workers.length) {
            showToast("Select a worker to build with", true);
            stopPlacing();
            return;
        }
        const {type, rotation, cx, cz} = placing;
        if (!keep) {
            stopPlacing();
        }
        // Shift lays out more sites, which the workers build one after another.
        const reply = await send("buildings.place", {building: type, x: cx, z: cz, rotation, units: workers,
            queue: Boolean(keep)});
        if (reply) {
            showToast(buildingType(type).name + " site laid out");
        }
    }

    // The first click says where a wall starts, the second where it ends, and the selected workers go to build it.
    async function placeWall(keep) {
        if (placing.cx === null) {
            return;
        }
        if (!placing.start) {
            placing.start = {x: placing.cx, z: placing.cz};
            renderUnitBar();
            view.dirty = true;
            return;
        }
        const bridge = placing.type === "bridge";
        const wall = placingWall();
        if (!wall || !wall.ok) {
            showToast(!bridge ? "The wall does not fit there"
                : wall && wall.dry ? "A bridge has to cross water" : "The bridge does not fit there", true);
            return;
        }
        const workers = selectedWorkers();
        if (!workers.length) {
            showToast("Select a worker to build with", true);
            stopPlacing();
            return;
        }
        const {start, cx, cz} = placing;
        // Shift starts the next wall where this one ends.
        if (keep) {
            placing.start = {x: cx, z: cz};
        } else {
            stopPlacing();
        }
        const reply = await send(bridge ? "buildings.bridge" : "buildings.wall", {x1: start.x, z1: start.z, x2: cx,
            z2: cz, units: workers, queue: Boolean(keep)});
        if (reply) {
            showToast(bridge ? "Bridge of " + reply.pieces + " blocks laid out" : "Wall of " + reply.pieces + " pieces laid out");
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
        setBuildingSelection([]);
        changedSelection();
    });

    boxButton.addEventListener("click", () => {
        boxMode = !boxMode;
        renderUnitBar();
    });

    function changedSelection() {
        if (state.selected.size) {
            setBuildingSelection([]);
        }
        if (placing && !selectedBuilders().length) {
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
            return !hit.thing.carcass;
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
        if (attackMoving && (button === 0 || button === 2)) {
            attackMove(px, py, hit, shift);
            return;
        }
        const ordering = button === 2 || (touch && state.selected.size && !(hit && hit.kind === "unit" && isMine(hit.thing)));
        if (ordering) {
            if (state.selected.size) {
                order(px, py, hit, shift);
            } else if (button === 2 && state.selectedBuildings.size) {
                setRally(px, py, hit);
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
            const now = performance.now();
            const key = "building " + building.id;
            if (lastClick.id === key && now - lastClick.time < 400) {
                // A double click picks every building of that kind in view.
                selectBuildingsInView(building);
            } else if (shift || touch) {
                const ids = [...state.selectedBuildings];
                setBuildingSelection(state.selectedBuildings.has(building.id) ? ids.filter((id) => id !== building.id)
                    : ids.concat([building.id]), state.selectedBuilding);
                state.selected.clear();
                changedSelection();
            } else {
                selectBuilding(building.id);
            }
            lastClick = {time: now, id: key};
        } else if (!shift && !touch && (state.selected.size || state.selectedBuilding !== null)) {
            state.selected.clear();
            setBuildingSelection([]);
            changedSelection();
        }
    }

    function order(px, py, hit, shift) {
        const units = [...state.selected];
        const team = myTeam();
        if (hit && hit.kind === "player" && team && hit.thing.team === team.id) {
            // Right-clicking a player of the team, such as yourself, has the units follow them in formation.
            const t = hit.thing;
            orderMark = {x: t.drawX, y: t.drawY, z: t.drawZ, color: [0.45, 1, 0.5, 1], time: performance.now()};
            send("units.follow", {units, target: entityId(hit), formation}).then((reply) => {
                if (reply) {
                    showToast("Following " + t.name);
                }
            });
        } else if (hit && hit.kind === "mob" && hit.thing.carcass && selectedWorkers().length) {
            // Workers carve a carcass; anyone else just goes there.
            const t = hit.thing;
            orderMark = {x: t.drawX, y: t.drawY, z: t.drawZ, color: [0.45, 1, 0.5, 1], time: performance.now()};
            send("units.attack", {units: selectedWorkers(), target: t.id});
        } else if (hit && isEnemy(hit)) {
            const t = hit.thing;
            orderMark = {x: t.drawX, y: t.drawY, z: t.drawZ, color: [1, 0.3, 0.25, 1], time: performance.now()};
            // Shift-clicking enemies one after another lines them up to attack in turn.
            send("units.attack", {units, target: entityId(hit), queue: Boolean(shift)});
        } else if (!hit && (orderBuild(px, py, shift) || orderAttackBuilding(px, py) || orderMine(px, py)
            || orderChop(px, py))) {
            return;
        } else {
            const ground = groundUnder(px, py);
            if (!ground) {
                return;
            }
            orderMark = {x: ground[0], y: ground[1], z: ground[2], color: [0.45, 1, 0.5, 1], time: performance.now()};
            // Shift-clicking ground one spot after another lays out waypoints to walk in turn, as for scouting.
            send("units.move", {units, x: ground[0], z: ground[2], formation, queue: Boolean(shift)});
        }
        view.dirty = true;
    }

    // ---------------------------------------------------------------- control groups and rally points
    //
    // Ctrl (or alt, as browsers keep ctrl + number for their tabs) and a number makes the selection that control
    // group; shift and the number adds the selection to it; the number alone selects it, and again goes to it.
    // Groups live in this page only.

    const groups = new Map(); // number -> {units: Set, buildings: Set}
    const rallyGroups = new Map(); // building id -> control group number its new units join
    const knownUnits = new Set(); // ids of the viewer's units seen so far, to spot new ones
    let knownReady = false;

    function groupOf(n) {
        if (!groups.has(n)) {
            groups.set(n, {units: new Set(), buildings: new Set()});
        }
        return groups.get(n);
    }

    // Drops dead units and lost buildings from the groups.
    function tidyGroups() {
        for (const group of groups.values()) {
            for (const id of [...group.units]) {
                if (!state.units.has(id)) {
                    group.units.delete(id);
                }
            }
            for (const id of [...group.buildings]) {
                if (!state.buildings.has(id)) {
                    group.buildings.delete(id);
                }
            }
        }
    }

    function setGroup(n, add) {
        if (!state.selected.size && !state.selectedBuildings.size) {
            return;
        }
        const group = groupOf(n);
        if (!add) {
            group.units.clear();
            group.buildings.clear();
        }
        for (const id of state.selected) {
            group.units.add(id);
        }
        for (const id of state.selectedBuildings) {
            group.buildings.add(id);
        }
        showToast((add ? "Added to" : "Made") + " group " + n);
        renderUnitBar();
    }

    function sameSet(a, b) {
        return a.size === b.size && [...a].every((id) => b.has(id));
    }

    function recallGroup(n) {
        const group = groups.get(n);
        if (!group || (!group.units.size && !group.buildings.size)) {
            return;
        }
        if (sameSet(state.selected, group.units) && sameSet(state.selectedBuildings, group.buildings)) {
            focusGroup(group);
            return;
        }
        state.selected = new Set(group.units);
        setBuildingSelection([...group.buildings]);
        if (group.units.size) {
            changedSelection();
        } else {
            renderUnitBar();
            view.dirty = true;
        }
    }

    function focusGroup(group) {
        let x = 0;
        let z = 0;
        let y = 0;
        let count = 0;
        for (const id of group.units) {
            const u = state.units.get(id);
            if (u) {
                x += u.drawX;
                y += u.drawY;
                z += u.drawZ;
                count++;
            }
        }
        for (const id of group.buildings) {
            const b = state.buildings.get(id);
            if (b) {
                x += b.x + b.sizeX / 2;
                y += b.y + 1;
                z += b.z + b.sizeZ / 2;
                count++;
            }
        }
        if (count) {
            setFollow(false);
            view.focus(x / count, y / count, z / count);
        }
    }

    // The control group a unit is in, or null.
    function groupNumberOf(unitId) {
        for (const [n, group] of groups) {
            if (group.units.has(unitId)) {
                return n;
            }
        }
        return null;
    }

    // Right-click with buildings selected: their new units go there, or join the control group of the unit clicked.
    function setRally(px, py, hit) {
        const buildings = selectedBuildingList().filter((b) => (buildingType(b.type).trains || []).length);
        if (!buildings.length) {
            return;
        }
        const ids = buildings.map((b) => b.id);
        const n = hit && hit.kind === "unit" && isMine(hit.thing) ? groupNumberOf(hit.thing.id) : null;
        if (n !== null) {
            for (const id of ids) {
                rallyGroups.set(id, n);
            }
            send("buildings.rally", {buildings: ids});
            showToast("New units join group " + n);
            renderUnitBar();
            return;
        }
        const ground = hit ? [hit.thing.drawX, hit.thing.drawY, hit.thing.drawZ] : groundUnder(px, py);
        if (!ground) {
            return;
        }
        for (const id of ids) {
            rallyGroups.delete(id);
        }
        orderMark = {x: ground[0], y: ground[1], z: ground[2], color: [1, 0.85, 0.35, 1], time: performance.now()};
        send("buildings.rally", {buildings: ids, x: ground[0], z: ground[2]}).then((reply) => {
            if (reply) {
                showToast("Rally point set");
            }
        });
        view.dirty = true;
    }

    function clearRally(buildings) {
        for (const b of buildings) {
            rallyGroups.delete(b.id);
        }
        send("buildings.rally", {buildings: buildings.map((b) => b.id)});
        renderUnitBar();
    }

    // New units from a building that rallies to a control group join it and walk over to it.
    function joinRallyGroups() {
        const team = myTeam();
        const fresh = [];
        for (const u of state.units.values()) {
            if (team && u.team === team.id && !knownUnits.has(u.id)) {
                knownUnits.add(u.id);
                fresh.push(u);
            }
        }
        if (!knownReady) {
            knownReady = true;
            return;
        }
        for (const u of fresh) {
            const n = rallyGroups.get(u.from);
            if (n === undefined) {
                continue;
            }
            const group = groupOf(n);
            const others = [...group.units].map((id) => state.units.get(id)).filter(Boolean);
            group.units.add(u.id);
            if (others.length) {
                const x = others.reduce((sum, o) => sum + o.x, 0) / others.length;
                const z = others.reduce((sum, o) => sum + o.z, 0) / others.length;
                send("units.move", {units: [u.id], x, z, formation});
            }
        }
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

    // Right-clicking one of the team's building sites, or a farm, sends the selected workers to build or
    // work it.
    function orderBuild(px, py, queue) {
        const building = buildingAt(px, py);
        const workers = selectedWorkers();
        const type = building && buildingType(building.type);
        if (!building || building.team !== myTeam().id || !workers.length
            || (building.complete && building.health >= building.maxHealth && !(type && type.workers))) {
            return false;
        }
        orderMark = {x: building.x + building.sizeX / 2, y: building.y + 1, z: building.z + building.sizeZ / 2,
            color: [0.45, 1, 0.5, 1], time: performance.now()};
        send("units.build", {units: workers, building: building.id, queue: Boolean(queue)});
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

    // Right-clicking a gold mine sends the selected workers to mine it.
    function orderMine(px, py) {
        const hit = view.pick(px, py);
        const mine = hit && mineAt(hit.x, hit.z);
        const workers = selectedWorkers();
        if (!mine || !workers.length) {
            return false;
        }
        orderMark = {x: mine.x + mine.sizeX / 2, y: mine.y + 2, z: mine.z + mine.sizeZ / 2, color: [0.45, 1, 0.5, 1],
            time: performance.now()};
        send("units.gather", {units: workers, x: mine.x + mine.sizeX / 2, y: mine.y, z: mine.z + mine.sizeZ / 2});
        view.dirty = true;
        return true;
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

    // Selects the viewer's buildings of the same kind as this one that are in view.
    function selectBuildingsInView(building) {
        const width = canvas.clientWidth;
        const height = canvas.clientHeight;
        const ids = [building.id];
        for (const b of state.buildings.values()) {
            if (b.id === building.id || b.type !== building.type || b.team !== building.team || !b.complete) {
                continue;
            }
            const [x, y] = view.project(b.x + b.sizeX / 2, b.y + 1, b.z + b.sizeZ / 2);
            if (x >= 0 && x <= width && y >= 0 && y <= height) {
                ids.push(b.id);
            }
        }
        setBuildingSelection(ids, building.id);
        state.selected.clear();
        changedSelection();
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
        // With no units in it, the box picks the viewer's buildings in it instead.
        const team = myTeam();
        if (!state.selected.size && team) {
            const ids = [];
            for (const b of state.buildings.values()) {
                if (b.team !== team.id) {
                    continue;
                }
                const [x, y] = view.project(b.x + b.sizeX / 2, b.y + 1, b.z + b.sizeZ / 2);
                if (x >= left && x <= right && y >= top && y <= bottom) {
                    ids.push(b.id);
                }
            }
            if (ids.length) {
                setBuildingSelection(add ? [...state.selectedBuildings, ...ids] : ids, state.selectedBuilding);
                renderUnitBar();
                view.dirty = true;
                return;
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
            attackBox: false,
        };
        if (!pointers.size) {
            multiTouch = false;
            point.box = event.button === 0 && (event.shiftKey || boxMode) && Boolean(myTeam()) && !placing && !lighting;
            // With warriors or archers selected, a right drag draws a box of enemies to attack; ctrl and a right
            // drag still turns the view.
            point.attackBox = event.button === 2 && !event.ctrlKey && Boolean(myTeam()) && !placing && !lighting
                && selectedFighters().length > 0;
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
        if (pointers.size === 1 && (point.box || point.attackBox)) {
            selectionBox = point.moved ? {x0: point.startX, y0: point.startY, x1: point.x, y1: point.y,
                attack: point.attackBox} : null;
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
        } else if (point.attackBox && point.moved) {
            if (!attackIn(point.startX, point.startY, point.x, point.y, event.shiftKey)) {
                showToast("There are no enemies there", true);
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
    let hoverBuilding = null; // id of the shooting building under the pointer, whose range shows
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

    const RESOURCE_ORDER = ["food", "wood", "gold", "favor"];

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
                return (u.toX === undefined ? "building " + where : "on the way to build " + where)
                    + (u.queued ? ", " + u.queued + " more queued" : "");
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
            case "mine":
                if (u.delivering) {
                    return "taking " + amountsText(u.carrying || {}) + " to store";
                }
                return u.mining ? "mining gold" : "on the way to a gold mine";
            case "light":
                return "putting up torches, " + (u.torches || 0) + " to go";
            case "follow": {
                const leader = state.players.get(u.target);
                return "following " + (leader ? leader.name : "a player");
            }
            case "hunt":
                if (u.delivering) {
                    return "taking " + amountsText(u.carrying || {}) + " to store";
                }
                return u.carving ? "carving a carcass" : u.target ? "hunting" : "on the way to a carcass";
            default:
                return u.kind === "worker" ? "idle" : "on guard";
        }
    }

    // Whether the team has a unit of the kind alive or in training, as a hero it can have only one of.
    function hasUnitOf(team, kindId) {
        return [...state.units.values()].some((u) => u.team === team.id && u.kind === kindId)
            || [...state.buildings.values()].some((b) => b.team === team.id && (b.queue || []).includes(kindId));
    }

    function unitName(u) {
        const team = state.teamsById[u.team];
        const kind = unitKind(u.kind);
        // A hero goes by his own name.
        if (kind && kind.hero) {
            return kind.name + (team ? " (" + team.name + ")" : "");
        }
        return (team ? team.name + " " : "") + (kind ? kind.name.toLowerCase() : u.kind) + " " + u.number;
    }

    function buildingName(b) {
        const team = state.teamsById[b.team];
        const type = buildingType(b.type);
        return (team ? team.name + " " : "") + (type ? type.name.toLowerCase() : b.type)
            + (b.level > 1 ? " (level " + b.level + ")" : "");
    }

    // The tool workers use at a building: a hoe at a farm, a pickaxe at a quarry, else an axe.
    function toolFor(type) {
        return type === "farm" ? "hoe" : type === "quarry" ? "pickaxe" : "axe";
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
        if (hit && selected.length && hit.kind === "player" && team && hit.thing.team === team.id) {
            return {icon: ICONS.player, text: "Follow " + hit.thing.name + " in formation", action: "act"};
        }
        if (!selected.length && state.selectedBuildings.size && selectedBuildingList().some((b) => b.complete
            && (buildingType(b.type).trains || []).length)) {
            const n = hit && hit.kind === "unit" && isMine(hit.thing) ? groupNumberOf(hit.thing.id) : null;
            return {icon: "\u2691", text: n !== null ? "New units join group " + n : "Set rally point", action: "act"};
        }
        if (hit) {
            if (hit.kind === "mob" && hit.thing.carcass) {
                return {icon: "food", text: (workers ? "Carve " + hit.thing.name.toLowerCase() : hit.thing.name)
                    + " \u00B7 " + hit.thing.food + " food", action: workers ? "build" : undefined};
            }
            if (workers && !fighters && hit.kind === "mob" && hit.thing.food) {
                return {icon: "sword", text: "Hunt " + hit.thing.name + " \u00B7 " + hit.thing.food + " food",
                    action: "attack"};
            }
            if (fighters && isEnemy(hit)) {
                const name = hit.kind === "unit" ? unitName(hit.thing) : hit.thing.name;
                return {icon: "sword", text: "Attack " + name + (hit.kind === "mob"
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
            const ground = view.pick(px, py);
            const mine = ground && mineAt(ground.x, ground.z);
            if (mine) {
                const gold = mine.gold > 0 ? mine.gold + "/" + mine.maxGold + " gold" : "mined out";
                if (workers && mine.gold > 0) {
                    return {icon: "pickaxe", text: "Mine gold \u00B7 " + (mine.name || "Gold mine") + " \u00B7 " + gold,
                        action: "build"};
                }
                return {icon: "gold", text: (mine.name || "Gold mine") + " \u00B7 " + gold
                    + (team && mine.gold > 0 ? " \u00B7 select workers to mine it" : "")
                    + (team && mine.free ? " \u00B7 workers can dig a quarry here" : "")};
            }
            const settlement = ground && freeSettlementNear(ground.x, ground.z, 0);
            if (settlement) {
                return {icon: "town_hall", text: "Settlement \u00B7 workers can raise a town center here"};
            }
            const block = treeUnder(px, py);
            if (!block) {
                return null;
            }
            const tree = trackedTreeNear(block);
            const wood = tree ? " \u00B7 " + tree.wood + "/" + tree.maxWood + " wood" : "";
            if (workers) {
                return {icon: "axe", text: "Chop tree" + wood, action: "build"};
            }
            return {icon: "tree", text: "Tree" + wood + (team ? " \u00B7 select workers to chop it" : "")};
        }
        const type = buildingType(b.type);
        const mine = team && b.team === team.id;
        if (fighters && isEnemyTeam(b.team)) {
            return {icon: "sword", text: "Attack " + buildingName(b) + " \u00B7 " + b.health + "/" + b.maxHealth,
                action: "attack"};
        }
        if (mine && workers && !b.complete) {
            return {icon: "axe", text: "Build " + type.name.toLowerCase() + " \u00B7 "
                + Math.floor(100 * (b.built || 0)) + "% built", action: "build"};
        }
        if (mine && workers && type.workers) {
            return {icon: toolFor(b.type), text: "Work at " + type.name.toLowerCase() + " ("
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
        if (hoverBuilding !== null) {
            hoverBuilding = null;
            view.dirty = true;
        }
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
        const over = pointer && !placing ? buildingAt(px, py) : null;
        const hover = over && over.range ? over.id : null;
        if (hover !== hoverBuilding) {
            hoverBuilding = hover;
            view.dirty = true;
        }
        if (pointer) {
            tipTimer = now;
            updateTip();
        }
    }

    const keys = new Set();
    const PAN_KEYS = ["w", "a", "s", "d", "arrowup", "arrowdown", "arrowleft", "arrowright"];

    window.addEventListener("keydown", (event) => {
        if (event.target instanceof HTMLInputElement) {
            return;
        }
        const digit = /^Digit([0-9])$/.exec(event.code);
        if (digit && myTeam()) {
            const n = Number(digit[1]);
            if (event.ctrlKey || event.altKey || event.metaKey) {
                setGroup(n, false);
            } else if (event.shiftKey) {
                setGroup(n, true);
            } else {
                recallGroup(n);
            }
            event.preventDefault();
            return;
        }
        if (event.ctrlKey || event.metaKey || event.altKey) {
            return;
        }
        const key = event.key.toLowerCase();
        if (key === " " && myTeam() && selectedFighters().length) {
            startAttackMove();
            event.preventDefault();
        } else if (PAN_KEYS.includes(key)) {
            keys.add(key);
            setFollow(false);
            event.preventDefault();
        } else if (key === "q") {
            view.turn(-1);
        } else if (key === "e") {
            view.turn(1);
        } else if (key === "r" && placing) {
            rotatePlacing();
        } else if (key === "delete") {
            deleteSelection();
        } else if (key === "escape" && attackMoving) {
            attackMoving = false;
            renderUnitBar();
        } else if (key === "escape" && placing) {
            stopPlacing();
        } else if (key === "escape" && lighting) {
            stopLighting();
        } else if (key === "escape" && (state.selected.size || state.selectedBuilding !== null)) {
            state.selected.clear();
            setBuildingSelection([]);
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
    const CARCASS = [0.62, 0.36, 0.32, 1];

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

    // See-through boxes in the shape of a building: one per run of equal-height columns along x. Cut to footX by
    // footZ when given, for a short segment of wall.
    function ghostBoxes(boxes, typeId, rotation, minX, minZ, groundY, color, footX = Infinity, footZ = Infinity) {
        const shape = turnedShape(typeId, rotation);
        const endX = Math.min(shape.sizeX, footX);
        const endZ = Math.min(shape.sizeZ, footZ);
        for (let z = 0; z < endZ; z++) {
            let x = 0;
            while (x < endX) {
                const h = shape.heights[x + z * shape.sizeX];
                let run = 1;
                while (x + run < endX && shape.heights[x + run + z * shape.sizeX] === h) {
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
            if (m.carcass) {
                // Lying on its side.
                const l = 0.9 * s;
                boxes.push({min: [m.drawX - l / 2, m.drawY, m.drawZ - l / 4], size: [l, 0.4 * s, l / 2],
                    color: CARCASS});
                continue;
            }
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
            // As the building will look, see-through, or red where it does not fit.
            const color = spot.ok ? [0.93, 0.91, 0.86, 0.35] : [1, 0.3, 0.25, 0.4];
            ghostBoxes(boxes, placing.type, spot.rotation, spot.x, spot.z, spot.y, color);
        }
        const wall = placingWall();
        if (wall) {
            const color = wall.ok ? [0.93, 0.91, 0.86, 0.35] : [1, 0.3, 0.25, 0.4];
            for (const p of wall.pieces) {
                ghostBoxes(boxes, p.type, p.rotation, p.x, p.z, p.y, p.ok ? color : [1, 0.3, 0.25, 0.4], p.sizeX, p.sizeZ);
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

    function ring(lines, x, y, z, radius, color, segments = 16) {
        for (let i = 0; i < segments; i++) {
            const a = 2 * Math.PI * i / segments;
            const b = 2 * Math.PI * (i + 1) / segments;
            lines.push([[x + Math.cos(a) * radius, y, z + Math.sin(a) * radius],
                [x + Math.cos(b) * radius, y, z + Math.sin(b) * radius], color]);
        }
    }

    const SELECTED = [0.45, 1, 0.5, 1];
    const ATTACK = [1, 0.3, 0.25, 1];
    const RANGE = [1, 0.85, 0.35, 0.9];
    const WAYPOINT = [0.45, 1, 0.5, 0.6];

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
            // On from there to each queued waypoint.
            const waypoints = u.waypoints || [];
            let fromX = u.toX;
            let fromZ = u.toZ;
            for (let i = 0; i + 1 < waypoints.length && fromX !== undefined; i += 2) {
                const [x, z] = [waypoints[i], waypoints[i + 1]];
                lines.push([[fromX, surfaceY(Math.floor(fromX), Math.floor(fromZ)) + 0.1, fromZ],
                    [x, surfaceY(Math.floor(x), Math.floor(z)) + 0.1, z], WAYPOINT]);
                fromX = x;
                fromZ = z;
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
            const color = state.selectedBuildings.has(b.id) ? SELECTED : teamColor(b.team);
            rect(lines, b.x - 0.5, b.z - 0.5, b.x + b.sizeX + 0.5, b.z + b.sizeZ + 0.5, b.y + 1.1, color);
            if (state.selectedBuildings.has(b.id) && b.rallyX !== undefined && !rallyGroups.has(b.id)) {
                const y = surfaceY(Math.floor(b.rallyX), Math.floor(b.rallyZ));
                lines.push([[b.x + b.sizeX / 2, b.y + 1.1, b.z + b.sizeZ / 2], [b.rallyX, y + 0.1, b.rallyZ], RANGE]);
                ring(lines, b.rallyX, y + 0.1, b.rallyZ, 0.6 * s, RANGE);
            }
            // How far towers shoot, for the ones selected and the one under the pointer.
            if (b.range && (state.selectedBuildings.has(b.id) || b.id === hoverBuilding)) {
                ring(lines, b.x + b.sizeX / 2, b.y + 1.1, b.z + b.sizeZ / 2, b.range, RANGE, 64);
            }
        }
        const spot = placingFootprint();
        if (spot) {
            const color = spot.ok ? SELECTED : ATTACK;
            rect(lines, spot.x, spot.z, spot.x + spot.sizeX, spot.z + spot.sizeZ, spot.y + 1.1, color);
        }
        const wall = placingWall();
        for (const p of wall ? wall.pieces : []) {
            rect(lines, p.x, p.z, p.x + p.sizeX, p.z + p.sizeZ, p.y + 1.1, p.ok ? SELECTED : ATTACK);
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
            if (!type || !team || (b.type !== "town_hall" && !state.selectedBuildings.has(b.id) && b.complete && !b.queue
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
        // Gold left in mines that have been mined.
        for (const m of dots ? [] : state.mines) {
            if (m.gold >= m.maxGold || m.gold <= 0) {
                continue;
            }
            const [x, y] = view.project(m.x + m.sizeX / 2, m.y + 7, m.z + m.sizeZ / 2);
            const width = 32;
            labelCtx.fillStyle = "rgba(0, 0, 0, 0.75)";
            labelCtx.fillRect(x - width / 2 - 1, y - 1, width + 2, 5);
            labelCtx.fillStyle = "#f2c94c";
            labelCtx.fillRect(x - width / 2, y, width * Math.max(0, Math.min(1, m.gold / m.maxGold)), 3);
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
            labelCtx.fillStyle = b.attack ? "rgba(255, 64, 64, 0.12)" : "rgba(115, 255, 128, 0.12)";
            labelCtx.strokeStyle = b.attack ? "rgba(255, 64, 64, 0.9)" : "rgba(115, 255, 128, 0.9)";
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
