#!/usr/bin/env bash
# Boots the dev dedicated server twice. The first boot sets up a game through /rts and saves it; the second
# boot checks the game came back from the save intact, then starts a new game to check numbering continues.
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

boot boot-1-setup "rts newgame 3 1000
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

boot boot-2-reload "rts info
rts newgame 2
rts info
stop"

# Game #1 as reloaded from the save.
expect boot-2-reload.log "Game #1"
expect boot-2-reload.log "Map: 1000x1000 from"
expect boot-2-reload.log "Team Red [Food 200, Wood 250, Stone 100, Metal 0] members: none"
expect boot-2-reload.log "Team Blue [Food 175, Wood 200, Stone 100, Metal 0] members: none"
expect boot-2-reload.log "Team Green [Food 200, Wood 200, Stone 100, Metal 7] members: none"
expect boot-2-reload.log "Alliance: Blue + Red"
reject boot-2-reload.log "Team Zombies"
# A new game replaces it and keeps counting.
expect boot-2-reload.log "Game #1 has ended."
expect boot-2-reload.log "Game #2 has started on a 1000x1000 map"
expect boot-2-reload.log "Alliances: none"

echo "Server smoke test passed"
