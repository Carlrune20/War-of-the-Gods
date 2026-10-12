#!/usr/bin/env bash
# Boots the dev dedicated server four times. The first boot sets up a game through /rts and saves it; the second
# boot checks the game and its map came back from the save intact, then starts a new game to check numbering
# continues and the old map is replaced by a fresh one. The third checks the web map and units, and the fourth
# that units come back after a restart.
set -uo pipefail

TIMEOUT="${SERVER_TIMEOUT:-600}"
mkdir -p run/server
for dir in run run/server; do
    echo "eula=true" > "$dir/eula.txt"
    printf 'level-type=FLAT\nonline-mode=false\nspawn-protection=0\n' > "$dir/server.properties"
done

fail() {
    echo "::error::$1"
    exit 1
}

boot() {
    local name="$1" commands="$2"
    printf '%s\n' "$commands" > "run/$name.txt"
    echo "=== Booting server: $name"
    timeout "$TIMEOUT" ./gradlew runServer < "run/$name.txt" 2>&1 | tee "$name.log"
    grep -q 'Done (' "$name.log" || fail "$name: server did not finish starting"
    if grep -qE 'This crash report has been saved|Encountered an unexpected exception' "$name.log"; then
        fail "$name: server crashed"
    fi
    if grep -q "Couldn't process command" "$name.log"; then
        fail "$name: a command threw an exception"
    fi
    grep -q 'Stopping server' "$name.log" || fail "$name: server did not stop cleanly"
}

expect() {
    local log="$1" pattern="$2"
    grep -qF -- "$pattern" "$log" || fail "$log: expected to find: $pattern"
    echo "ok: $pattern"
}

reject() {
    local log="$1" pattern="$2"
    if grep -qF -- "$pattern" "$log"; then
        fail "$log: did not expect: $pattern"
    fi
    echo "ok (absent): $pattern"
}

GAMES_DIR=run/server/world/warofthegods

boot boot-1-setup "rts newgame 3 1000 12345
rts team create Zombies dark_green
rts ally red blue
rts resources give red wood 50
rts resources take blue food 25
rts resources set green metal 7
rts team remove Zombies
rts info
stop"

expect boot-1-setup.log "Game #1 has started on a 1000x1000 map"
expect boot-1-setup.log "Created team Zombies"
expect boot-1-setup.log "Red and Blue are now allies."
expect boot-1-setup.log "Removed team Zombies"
expect boot-1-setup.log "Game world ready in dimension 77"
expect boot-1-setup.log "World: dimension 77, seed 12345"
spawn1=$(grep -oE 'Spawn: -?[0-9]+,-?[0-9]+,-?[0-9]+' boot-1-setup.log | head -1)
[ -n "$spawn1" ] || fail "boot-1-setup: no spawn point in /rts info"
spawn_y=$(echo "$spawn1" | cut -d, -f2)
[ "$spawn_y" -gt 62 ] || fail "boot-1-setup: spawn $spawn1 is not above sea level"
echo "ok: $spawn1"
ls "$GAMES_DIR"/game-1/region/*.mca > /dev/null 2>&1 || fail "game 1 world has no saved terrain"
echo "ok: game-1 terrain saved"

boot boot-2-reload "rts info
rts newgame 2
rts info
stop"

# Game #1 as reloaded from the save.
expect boot-2-reload.log "Game #1"
expect boot-2-reload.log "Map: 1000x1000 from -500,-500 to 499,499"
expect boot-2-reload.log "World: dimension 77, seed 12345"
expect boot-2-reload.log "$spawn1"
expect boot-2-reload.log "Team Red [Food 200, Wood 250, Stone 100, Metal 0] members: none"
expect boot-2-reload.log "Team Blue [Food 175, Wood 200, Stone 100, Metal 0] members: none"
expect boot-2-reload.log "Team Green [Food 200, Wood 200, Stone 100, Metal 7] members: none"
expect boot-2-reload.log "Alliance: Blue + Red"
reject boot-2-reload.log "Team Zombies"
# A new game replaces it and keeps counting.
expect boot-2-reload.log "Game #1 has ended."
expect boot-2-reload.log "Game #2 has started on a 1000x1000 map"
expect boot-2-reload.log "Alliances: none"
[ -d "$GAMES_DIR/game-2" ] || fail "game 2 has no world folder"
[ ! -d "$GAMES_DIR/game-1" ] || fail "game 1 world was not deleted"
echo "ok: game-1 world replaced by game-2"

# Boot 3: the web map. The server keeps running on commands fed through a pipe while curl and a WebSocket client
# check the page, logging in with a link, the map tiles and the live state.
WEB_PORT=8124
WEB="http://127.0.0.1:$WEB_PORT"
WEB_LOG=boot-3-web.log
rm -f run/web-input
mkfifo run/web-input
echo "=== Booting server: boot-3-web"
timeout "$TIMEOUT" ./gradlew runServer < run/web-input > "$WEB_LOG" 2>&1 &
server_pid=$!
exec 3> run/web-input

stop_server() {
    echo "stop" >&3
    exec 3>&-
    wait "$server_pid"
}

web_fail() {
    stop_server
    cat "$WEB_LOG"
    fail "$1"
}

wait_for_log() {
    local pattern="$1" seconds="$2"
    for _ in $(seq "$seconds"); do
        grep -qF -- "$pattern" "$WEB_LOG" && return 0
        sleep 1
    done
    web_fail "boot-3-web: timed out waiting for: $pattern"
}

http_status() {
    curl -s -o /dev/null -w '%{http_code}' "$@"
}

wait_for_log "Done (" "$TIMEOUT"
wait_for_log "Web map listening on 0.0.0.0:$WEB_PORT" 5
echo "ok: web map listening"
echo "rts newgame 2 256 4242" >&3
echo "rts web" >&3
wait_for_log "Web map observer link" 60
token=$(grep -oE 'localhost:8124/\?token=[0-9a-f]+' "$WEB_LOG" | tail -1 | cut -d= -f2)
[ -n "$token" ] || web_fail "boot-3-web: no token in the /rts web link"
echo "ok: /rts web link"

curl -sf "$WEB/" | grep -q "<title>War of the Gods</title>" || web_fail "web page not served"
curl -sf "$WEB/app.js" > /dev/null || web_fail "app.js not served"
echo "ok: page served"
[ "$(http_status "$WEB/api/me")" = 401 ] || web_fail "/api/me without a session was not refused"
[ "$(http_status "$WEB/api/tile?x=0&z=0")" = 401 ] || web_fail "tile without a session was not refused"
[ "$(http_status -X POST -d '{"token":"nope"}' "$WEB/api/login")" = 401 ] || web_fail "bad token was accepted"
[ "$(http_status -X POST -H "Origin: http://evil.example" -d "{\"token\":\"$token\"}" "$WEB/api/login")" = 403 ] \
    || web_fail "login from another origin was not refused"
echo "ok: requests without a session refused"

jar=run/web-cookies.txt
rm -f "$jar"
curl -sf -c "$jar" -X POST -d "{\"token\":\"$token\"}" "$WEB/api/login" | grep -q '"observer":true' \
    || web_fail "login with the link token failed"
[ "$(http_status -X POST -d "{\"token\":\"$token\"}" "$WEB/api/login")" = 401 ] || web_fail "link token worked twice"
curl -sf -b "$jar" "$WEB/api/me" | grep -q '"name":"Observer"' || web_fail "/api/me after login failed"
echo "ok: login with a one-time link"

wait_for_log "Web map of game #3 drawn" 300
echo "ok: map drawn"
curl -sf -b "$jar" "$WEB/api/tile?x=0&z=0&v=1" -o run/tile.bin || web_fail "tile 0,0 not served"
python3 .github/scripts/map-tile-check.py run/tile.bin || web_fail "tile 0,0 does not hold the blocks of the map"
curl -sf "$WEB/blocks.js" > /dev/null && curl -sf "$WEB/render.js" > /dev/null || web_fail "map scripts not served"

# Units: spawn some, check they show on the web map, and that they walk where they are sent.
echo "rts units spawn red 3" >&3
echo "rts units spawn blue 2" >&3
wait_for_log "Spawned 2 units for Blue" 30
expect "$WEB_LOG" "Spawned 3 units for Red"
echo "rts units red" >&3
wait_for_log "Red 3 at" 30
expect "$WEB_LOG" "Team Red has 3 of 50 units"

session=$(awk '$6 == "wotg_session" {print $7}' "$jar")
python3 .github/scripts/web-socket-check.py 127.0.0.1 "$WEB_PORT" "$session" 3 5 || web_fail "web socket check failed"

red1_before=$(grep -oE 'Red 1 at -?[0-9]+,-?[0-9]+,-?[0-9]+' "$WEB_LOG" | tail -1)
echo "rts units move red 128 128" >&3
wait_for_log "Sent 3 units of Red to 128,128" 30
sleep 15
echo "rts units red" >&3
echo "rts info" >&3
wait_for_log "Units: Red 3, Blue 2" 30
red1_after=$(grep -oE 'Red 1 at -?[0-9]+,-?[0-9]+,-?[0-9]+' "$WEB_LOG" | tail -1)
[ -n "$red1_before" ] && [ "$red1_before" != "$red1_after" ] || web_fail "Red 1 did not move: $red1_before, then $red1_after"
echo "ok: $red1_before, then $red1_after"

stop_server
cat "$WEB_LOG"
grep -q 'Stopping server' "$WEB_LOG" || fail "boot-3-web: server did not stop cleanly"
if grep -qE 'This crash report has been saved|Encountered an unexpected exception' "$WEB_LOG"; then
    fail "boot-3-web: server crashed"
fi
expect "$WEB_LOG" "Web map stopped"

# Boot 4: units are saved with the game and come back where they were, with their chunks loaded again.
WEB_LOG=boot-4-units.log
rm -f run/web-input
mkfifo run/web-input
echo "=== Booting server: boot-4-units"
timeout "$TIMEOUT" ./gradlew runServer < run/web-input > "$WEB_LOG" 2>&1 &
server_pid=$!
exec 3> run/web-input
wait_for_log "Done (" "$TIMEOUT"
# Long enough for the units' chunks to load, and for a missing unit's record to be dropped.
sleep 10
echo "rts units" >&3
echo "rts units red" >&3
wait_for_log "Team Red has" 30
sleep 2
stop_server
cat "$WEB_LOG"
if grep -qE 'This crash report has been saved|Encountered an unexpected exception' "$WEB_LOG"; then
    fail "boot-4-units: server crashed"
fi
expect "$WEB_LOG" "Units: Red 3, Blue 2"
expect "$WEB_LOG" "Team Red has 3 of 50 units"
expect "$WEB_LOG" "Red 1 at "

echo "Server smoke test passed"
