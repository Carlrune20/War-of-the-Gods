#!/usr/bin/env bash
# Boots the dev dedicated server five times. The first boot sets up a game through /rts and saves it; the second
# boot checks the game and its map came back from the save intact, then starts a new game to check numbering
# continues and the old map is replaced by a fresh one. The third checks the web map, units and buildings: town
# centers, a worker building a farm, working it and carrying the harvest home, a barracks training warriors and
# archers, a storehouse and building health down to 0, a house, a tower, levels, upgrades, chopping a tree, mining gold, an explosion and torches. The fourth checks that units and buildings come back after a restart, then that a team losing its
# town center gets a countdown and a team with neither town center nor workers is defeated, leaving a winner. The
# fifth has the computer play a team.
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

boot boot-1-setup "rts quickgame 3 1000 12345
rts team auto
rts team create Zombies dark_green
rts ally red blue
rts resources give red wood 50
rts resources take blue food 25
rts resources set green gold 7
rts team remove Zombies
rts info
stop"

expect boot-1-setup.log "Game #1 has started on a 1000x1000 map"
expect boot-1-setup.log "Created team Zombies"
expect boot-1-setup.log "Red and Blue are now allies."
expect boot-1-setup.log "Removed team Zombies"
expect boot-1-setup.log "Everyone online is on a team already"
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
rts quickgame 2
rts info
stop"

# Game #1 as reloaded from the save.
expect boot-2-reload.log "Game #1"
expect boot-2-reload.log "Map: 1000x1000 from -500,-500 to 499,499"
expect boot-2-reload.log "World: dimension 77, seed 12345"
expect boot-2-reload.log "$spawn1"
expect boot-2-reload.log "Team Red [Food 200, Wood 250, Gold 100] members: none"
expect boot-2-reload.log "Team Blue [Food 175, Wood 200, Gold 100] members: none"
expect boot-2-reload.log "Team Green [Food 200, Wood 200, Gold 7] members: none"
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
echo "rts quickgame 2 256 4242" >&3
# Daylight all through, so no hostile mobs come after the units being counted.
echo "gamerule doDaylightCycle false" >&3
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
wait_for_log "Built Red town center #1" 30
wait_for_log "Built Blue town center #2" 30
# Once both town centers are up, each gets four towers round it, which do not shoot yet.
wait_for_log "Raised 4 towers round the town center of Blue" 60
expect "$WEB_LOG" "Raised 4 towers round the town center of Red"
expect "$WEB_LOG" "Built Blue tower #10"
curl -sf -b "$jar" "$WEB/api/tile?x=0&z=0&v=1" -o run/tile.bin || web_fail "tile 0,0 not served"
python3 .github/scripts/map-tile-check.py run/tile.bin || web_fail "tile 0,0 does not hold the blocks of the map"
[ "$(http_status -b "$jar" "$WEB/api/tile?x=0&z=0&v=1&m=1-old")" = 404 ] || web_fail "tile of an old map was served"
echo "ok: tiles of an old map are refused"
curl -sf "$WEB/blocks.js" > /dev/null && curl -sf "$WEB/render.js" > /dev/null || web_fail "map scripts not served"

# Units: spawn some, check they show on the web map, and that they walk where they are sent.
echo "rts units spawn red 3" >&3
echo "rts units spawn blue 2" >&3
wait_for_log "Spawned 2 units for Blue" 30
expect "$WEB_LOG" "Spawned 3 units for Red"
echo "rts units red" >&3
wait_for_log "Red warrior 3 at" 30
expect "$WEB_LOG" "Team Red has 3 units, room for 10"

session=$(awk '$6 == "wotg_session" {print $7}' "$jar")
python3 .github/scripts/web-socket-check.py 127.0.0.1 "$WEB_PORT" "$session" 3 5 || web_fail "web socket check failed"

red1_before=$(grep -oE 'Red warrior 1 at -?[0-9]+,-?[0-9]+,-?[0-9]+' "$WEB_LOG" | tail -1)
echo "rts route -100 -100 100 100" >&3
wait_for_log "Route from -100,-100 to 100,100" 30
grep -qE "Route from -100,-100 to 100,100: [1-9][0-9]* waypoints" "$WEB_LOG" || web_fail "no route across the map"
echo "ok: $(grep -oE 'Route from -100,-100 to 100,100: [0-9]+ waypoints' "$WEB_LOG" | tail -1)"
# A short way within Red's own town, where no mobs spawn, and out of reach of Blue's towers, which shoot once Blue
# has watch towers: out in the wilds, mobs can pick the warriors off before the next boot counts them.
echo "rts units move red 20 -100 wedge" >&3
wait_for_log "Sent 3 units of Red to 20,-100" 30
echo "rts units stance red hold" >&3
wait_for_log "Set 3 units of Red to hold ground" 30
sleep 15
echo "rts units red" >&3
echo "rts info" >&3
wait_for_log "Units: Red 3, Blue 2" 30
red1_after=$(grep -oE 'Red warrior 1 at -?[0-9]+,-?[0-9]+,-?[0-9]+' "$WEB_LOG" | tail -1)
[ -n "$red1_before" ] && [ "$red1_before" != "$red1_after" ] || web_fail "Red 1 did not move: $red1_before, then $red1_after"
echo "ok: $red1_before, then $red1_after"

# Buildings: a worker walks to a farm site, builds it, and stays to work it, carrying the harvest to the town
# center. A worker trained at the town center builds a barracks, which trains a warrior, and a storehouse, which is
# then damaged until it is gone. Archers come from the archery range, tested with the AI below; one joins here.
echo "rts units spawn blue 1 worker" >&3
wait_for_log "Spawned 1 units for Blue" 30
echo "rts build blue farm" >&3
wait_for_log "Laid out Blue farm #11" 30
expect "$WEB_LOG" "1 workers on the way"
wait_for_log "Built Blue farm #11" 120
echo "rts train blue worker 1" >&3
wait_for_log "trained Blue worker 4" 60
for _ in $(seq 30); do
    echo "rts buildings blue" >&3
    sleep 2
    grep -qF "Blue farm #11 at" "$WEB_LOG" && grep -q "Blue farm #11 at .*, 1 working" "$WEB_LOG" && break
done
grep -q "Blue farm #11 at .*, 1 working" "$WEB_LOG" || web_fail "the worker is not working the farm"
echo "ok: the worker works the farm"
echo "rts build blue barracks" >&3
wait_for_log "Laid out Blue barracks #12" 30
for _ in $(seq 30); do
    echo "rts buildings blue" >&3
    echo "rts units blue" >&3
    sleep 2
    grep -q "Blue barracks #12 at .*% built by 1 workers" "$WEB_LOG" && break
done
grep -q "Blue barracks #12 at .*(building site), health [0-9]*/800, [0-9]*% built by 1 workers" "$WEB_LOG" \
    || web_fail "the barracks site does not show the worker building it"
echo "ok: $(grep -oE 'Blue barracks #12 at .*% built by 1 workers' "$WEB_LOG" | tail -1)"
wait_for_log "Built Blue barracks #12" 120
echo "rts train blue warrior 1" >&3
wait_for_log "trained Blue warrior 5" 60
echo "rts units spawn blue 1 archer" >&3
wait_for_log "Spawned 1 units for Blue" 30
# The farm worker carries what it gathers to the town center once its hands are full.
food=0
for _ in $(seq 45); do
    echo "rts resources blue" >&3
    sleep 2
    food=$(grep -oE 'Team Blue resources: Food [0-9]+' "$WEB_LOG" | tail -1 | grep -oE '[0-9]+$')
    [ "${food:-0}" -gt 200 ] && break
done
[ "${food:-0}" -gt 200 ] || web_fail "the farm worker brought in no food: $food"
echo "ok: Blue has $food food from the farm"

echo "rts build blue storehouse" >&3
wait_for_log "Laid out Blue storehouse #13" 30
wait_for_log "Built Blue storehouse #13" 120
echo "rts damage 13 100" >&3
wait_for_log "has 500/600 health" 30
echo "rts damage 13 1000" >&3
wait_for_log "Destroyed Blue storehouse #13" 30
echo "ok: the storehouse lost health and was destroyed at 0"

# New buildings, levels and upgrades: a house and a tower go up, the town center goes up a level, and an upgrade
# is given.
echo "rts build blue house" >&3
wait_for_log "Laid out Blue house #14" 30
wait_for_log "Built Blue house #14" 120
echo "rts units blue" >&3
wait_for_log "units, room for 15" 30
echo "ok: the house makes room for more units"
echo "rts build blue tower" >&3
wait_for_log "Laid out Blue tower #15" 30
wait_for_log "Built Blue tower #15" 120
echo "rts upgrade 2" >&3
wait_for_log "Upgraded Blue town center (level 2) #2" 30
# The rest of the town follows the town center into Age II, looking it but gaining nothing else.
echo "rts buildings blue" >&3
wait_for_log "Blue tower (level 2) #15 at" 30
sleep 1
grep -q "Blue house (level 2) #14 at .*health 300/300" "$WEB_LOG" || web_fail "the house did not follow into Age II as it was"
echo "ok: the town followed its town center into Age II"
echo "rts research blue forged_blades" >&3
wait_for_log "Team Blue has iron swords" 30
echo "rts research blue fortified_walls" >&3
wait_for_log "Team Blue has fortified walls" 30
echo "rts buildings blue" >&3
sleep 2
grep -q "Blue house (level 2) #14 at .*health 450/450" "$WEB_LOG" || web_fail "fortified walls did not raise the house's health"
echo "ok: fortified walls give buildings half again as much health"
echo "rts research blue watch_tower" >&3
wait_for_log "Team Blue has watch towers" 30
echo "rts research blue guard_tower" >&3
wait_for_log "Team Blue has guard towers" 30

# A worker chops a tree near the town center and brings the wood home.
echo "rts units spawn blue 1 worker" >&3
sleep 2
echo "rts resources blue" >&3
sleep 2
wood_before=$(grep -oE 'Team Blue resources: .*Wood [0-9]+' "$WEB_LOG" | tail -1 | grep -oE '[0-9]+$')
echo "rts chop blue" >&3
wait_for_log "to chop the tree at" 30
expect "$WEB_LOG" "workers of Blue to chop the tree"
wood=0
for _ in $(seq 60); do
    echo "rts resources blue" >&3
    sleep 2
    wood=$(grep -oE 'Team Blue resources: .*Wood [0-9]+' "$WEB_LOG" | tail -1 | grep -oE '[0-9]+$')
    [ "${wood:-0}" -gt "${wood_before:-0}" ] && break
done
[ "${wood:-0}" -gt "${wood_before:-0}" ] || web_fail "no wood came in from the tree: $wood_before, then $wood"
echo "ok: Blue wood went from $wood_before to $wood"

# Settlements and gold mines are laid out once both teams have town centers; a worker mines gold and brings it home.
echo "rts info" >&3
wait_for_log "Settlements: " 30
grep -qE "Settlements: [0-9]+, [1-9][0-9]* unclaimed; gold mines: [1-9]" "$WEB_LOG" || web_fail "no unclaimed settlements or gold mines"
echo "ok: $(grep -oE 'Settlements: .*gold' "$WEB_LOG" | tail -1)"
expect "$WEB_LOG" "Built 3 bridges over the river"
expect "$WEB_LOG" "Laid out 4 settlements"
echo "rts units spawn blue 1 worker" >&3
sleep 2
echo "rts resources blue" >&3
sleep 2
gold_before=$(grep -oE 'Team Blue resources: .*Gold [0-9]+' "$WEB_LOG" | tail -1 | grep -oE '[0-9]+$')
echo "rts mine blue" >&3
wait_for_log "to the gold mine at" 30
gold=0
for _ in $(seq 60); do
    echo "rts resources blue" >&3
    sleep 2
    gold=$(grep -oE 'Team Blue resources: .*Gold [0-9]+' "$WEB_LOG" | tail -1 | grep -oE '[0-9]+$')
    [ "${gold:-0}" -gt "${gold_before:-0}" ] && break
done
[ "${gold:-0}" -gt "${gold_before:-0}" ] || web_fail "no gold came in from the mine: $gold_before, then $gold"
echo "ok: Blue gold went from $gold_before to $gold"

# An explosion beside the tower takes health off it but breaks none of its blocks.
echo "rts explode 15" >&3
wait_for_log "The explosion took" 30
grep -qE "The explosion took [1-9][0-9]? health off Blue tower (\(level 2\) )?#15 .* and broke 0 of its blocks|The explosion took 1[0-2][0-9] health off Blue tower (\(level 2\) )?#15 .* and broke 0 of its blocks" "$WEB_LOG" \
    || web_fail "the explosion did not just damage the tower: $(grep -oE 'The explosion took .*' "$WEB_LOG" | tail -1)"
echo "ok: $(grep -oE 'The explosion took .*' "$WEB_LOG" | tail -1)"

# A worker puts torches up around the town center; afterwards fewer spots are left dark.
echo "rts units spawn blue 1 worker" >&3
sleep 2
echo "rts light blue" >&3
wait_for_log "torches" 30
dark_before=$(grep -oE 'workers of Blue to put up [0-9]+ torches' "$WEB_LOG" | tail -1 | grep -oE '[0-9]+ torches' | grep -oE '[0-9]+')
grep -qE "Sent 1 workers of Blue to put up [1-9][0-9]* torches" "$WEB_LOG" || web_fail "no worker went to put up torches"
dark_after=$dark_before
for _ in $(seq 30); do
    sleep 2
    echo "rts light blue" >&3
    sleep 1
    dark_after=$(grep -oE 'workers of Blue to put up [0-9]+ torches' "$WEB_LOG" | tail -1 | grep -oE '[0-9]+ torches' | grep -oE '[0-9]+')
    [ "${dark_after:-0}" -lt "${dark_before:-0}" ] && break
done
[ "${dark_after:-0}" -lt "${dark_before:-0}" ] || web_fail "no torches went up: $dark_before spots, then $dark_after"
echo "ok: $dark_before dark spots, then $dark_after"

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
echo "rts buildings" >&3
echo "rts units blue" >&3
wait_for_log "Team Blue has" 30
sleep 2

# Win conditions. Red loses its town center but still has a worker, so it gets a countdown to raise a new one; a new
# town center stops it. Then with no workers and no town center Red is defeated at once, and Blue wins.
echo "rts units spawn red 1 worker" >&3
wait_for_log "Spawned 1 units for Red" 30
sleep 2
echo "rts damage 1 100000" >&3
wait_for_log "Destroyed Red town center #1" 30
wait_for_log "Team Red has lost its last town center! It has 2:00 to raise a new one" 30
echo "rts info" >&3
wait_for_log "left to raise a new town center" 30
echo "rts build red town_center" >&3
wait_for_log "Laid out Red town center #16" 30
echo "rts finish red" >&3
wait_for_log "Team Red has a town center again." 30

# Walls: a wall runs out from Blue's tower, away from the town center, and is built; a segment of it becomes a gate,
# and stone walls rebuild it all a level up.
read -r hall_x _ < <(grep -oE "Blue town center (\(level [0-9]\) )?#2 at -?[0-9]+,-?[0-9]+" "$WEB_LOG" | tail -1 | grep -oE -- "-?[0-9]+,-?[0-9]+$" | tr , ' ')
read -r tower_x tower_z < <(grep -oE "Blue tower (\(level [0-9]\) )?#15 at -?[0-9]+,-?[0-9]+" "$WEB_LOG" | tail -1 | grep -oE -- "-?[0-9]+,-?[0-9]+$" | tr , ' ')
wall_x=$((tower_x + 1))
wall_z=$((tower_z + 1))
if [ "$tower_x" -ge "$hall_x" ]; then wall_to=$((wall_x + 15)); else wall_to=$((wall_x - 15)); fi
# A worker sent to the first piece goes on along the rest of the wall.
echo "rts units spawn blue 1 worker" >&3
wait_for_log "Spawned 1 units for Blue" 30
sleep 1
echo "rts wall blue $wall_x $wall_z $wall_to $wall_z" >&3
wait_for_log "Laid out a wall of" 30
grep -qE "Laid out a wall of [3-9] pieces for Blue, [1-9][0-9]* of them posts" "$WEB_LOG" \
    || fail "boot-4-units: the wall was not laid out: $(grep -oE '(Laid out a wall|That would|runs into|off the map).*' "$WEB_LOG" | tail -1)"
echo "ok: $(grep -oE 'Laid out a wall of .*' "$WEB_LOG" | tail -1)"
echo "rts units blue" >&3
wait_for_log "more queued)" 30
echo "ok: $(grep -oE 'Blue worker [0-9]+ at .*more queued\)' "$WEB_LOG" | tail -1)"
echo "rts finish blue" >&3
wait_for_log "Built Blue wall #" 30
echo "rts buildings blue" >&3
wait_for_log "Blue wall post #" 30
sleep 1
wall_id=$(grep -oE "Blue wall #[0-9]+ at" "$WEB_LOG" | tail -1 | grep -oE "[0-9]+")
echo "rts gate $wall_id" >&3
wait_for_log "Made wall #$wall_id " 30
echo "ok: wall #$wall_id is a gate"
echo "rts research blue stone_walls" >&3
wait_for_log "Team Blue has stone walls" 30
echo "rts buildings blue" >&3
wait_for_log "Blue gate (level 2) #" 30
echo "ok: stone walls take the walls up a level"
# A wall started two blocks short of the end of that one, back toward it: a full piece and a 2 block segment that
# closes the gap.
if [ "$tower_x" -ge "$hall_x" ]; then gap_from=$((tower_x + 22)); gap_to=$((tower_x + 17)); else gap_from=$((tower_x - 22)); gap_to=$((tower_x - 17)); fi
echo "rts wall blue $gap_from $wall_z $gap_to $wall_z" >&3
wait_for_log "Laid out a wall of 2 pieces for Blue, 1 of them posts and 1 short" 30
echo "ok: $(grep -oE 'Laid out a wall of .*' "$WEB_LOG" | tail -1)"
short_id=$(grep -oE "Laid out a wall of 2 pieces for Blue, 1 of them posts and 1 short, from #[0-9]+ to #[0-9]+" "$WEB_LOG" | tail -1 | grep -oE "[0-9]+$")
echo "rts finish blue" >&3
wait_for_log "Built Blue wall #$short_id " 30
echo "ok: the short segment #$short_id is built"
# Army orders: Blue's warriors and archers go at Red's units, shared out among them, then on an attack move.
echo "rts units spawn red 2" >&3
wait_for_log "Spawned 2 units for Red" 30
echo "rts units attack blue red" >&3
wait_for_log " units of Blue at " 30
echo "ok: $(grep -oE 'Sent [0-9]+ units of Blue at .*' "$WEB_LOG" | tail -1)"
echo "rts units blue" >&3
sleep 2
grep -qE "Blue (warrior|archer) [0-9]+ at -?[0-9]+,-?[0-9]+,-?[0-9]+ attack" "$WEB_LOG" \
    || fail "boot-4-units: no Blue soldier is attacking"
echo "ok: Blue's soldiers attack"
echo "rts units attackmove blue $hall_x $wall_z" >&3
wait_for_log " units of Blue on an attack move to " 30
echo "rts units blue" >&3
wait_for_log "(attack move)" 30
echo "ok: Blue's soldiers are on an attack move"
# A worker repairs a damaged tower.
echo "rts damage 15 200" >&3
sleep 1
echo "rts units spawn blue 1 worker" >&3
sleep 2
echo "rts units repair blue 15" >&3
wait_for_log " workers of Blue to repair " 30
wait_for_log "Repaired Blue tower (level 2) #15" 120
echo "ok: a worker repaired Blue's tower"
echo "rts units kill red" >&3
sleep 2
echo "rts damage 16 100000" >&3
wait_for_log "Team Red has no town center and no workers left and has been defeated!" 30
wait_for_log "Game #3 is over! Blue wins!" 30
echo "rts info" >&3
wait_for_log "The game is over. Won: Blue" 30
echo "ok: Red was defeated and Blue won"
stop_server
cat "$WEB_LOG"
if grep -qE 'This crash report has been saved|Encountered an unexpected exception' "$WEB_LOG"; then
    fail "boot-4-units: server crashed"
fi
expect "$WEB_LOG" "Units: Red 3, Blue 9"
expect "$WEB_LOG" "Team Red has 3 units, room for 10"
expect "$WEB_LOG" "Red warrior 1 at "
grep -q "Red warrior 1 at .*, hold" "$WEB_LOG" || fail "boot-4-units: Red warrior 1 lost its stance"
echo "ok: stances are saved"
expect "$WEB_LOG" "Red town center #1 at"
expect "$WEB_LOG" "Blue town center (level 2) #2 at"
expect "$WEB_LOG" "Blue tower (level 2) #15 at"
expect "$WEB_LOG" "Blue barracks (level 2) #12 at"
grep -q "Blue worker 3 at .* work" "$WEB_LOG" || fail "boot-4-units: the farm lost its worker"
if grep -q "Blue storehouse #13" "$WEB_LOG"; then
    fail "boot-4-units: the destroyed storehouse came back"
fi
echo "ok: buildings and the farm worker came back"

# Boot 5: the computer plays Blue on a fresh map: it trains workers and puts them to gathering, without errors.
WEB_LOG=boot-5-ai.log
rm -f run/web-input
mkfifo run/web-input
echo "=== Booting server: boot-5-ai"
timeout "$TIMEOUT" ./gradlew runServer < run/web-input > "$WEB_LOG" 2>&1 &
server_pid=$!
exec 3> run/web-input
wait_for_log "Done (" "$TIMEOUT"
echo "rts quickgame 2 256 777" >&3
wait_for_log "has started on a 256x256 map with 2 teams" 120
echo "rts ai blue on" >&3
wait_for_log "Team Blue is now played by the computer" 30
echo "rts ai" >&3
wait_for_log "AI teams: Blue" 30
wait_for_log "trained Blue worker" 120
wait_for_log "AI Blue put idle workers to work" 60
echo "ok: $(grep -oE 'AI Blue put idle workers to work.*' "$WEB_LOG" | head -1)"
# Give it a minute to play, then show where it got to.
sleep 60
# Knights' fortresses: Red follows Zamorak, so it can raise the Black Knights' fortress in Age III but not the White
# Knights'. The fortress trains a Black Knight and Sagittare, and only one Sagittare at a time.
red_hall=$(grep -oE "Built Red town center #[0-9]+" "$WEB_LOG" | head -1 | grep -oE "[0-9]+$")
echo "rts upgrade $red_hall" >&3
wait_for_log "Upgraded Red town center (level 2) #$red_hall" 30
echo "rts upgrade $red_hall" >&3
wait_for_log "Upgraded Red town center (level 3) #$red_hall" 30
echo "rts build red white_knights_fortress" >&3
wait_for_log "Only Saradomin's teams can build a White Knights' fortress" 30
echo "rts build red black_knights_fortress" >&3
wait_for_log "Laid out Red black knights' fortress #" 30
echo "rts finish red" >&3
wait_for_log "Built Red black knights' fortress #" 60
echo "ok: Red raised the Black Knights' fortress"
echo "rts train red black_knight 1" >&3
wait_for_log "trained Red black knight " 60
echo "rts train red sagittare 3" >&3
wait_for_log "Queued 1 Sagittare at" 30
echo "rts train red sagittare" >&3
wait_for_log "Team Red already has Sagittare" 30
wait_for_log "trained Sagittare" 90
echo "ok: the fortress trained a Black Knight and Sagittare, one hero at a time"
# The archery range trains archers, the stables cavalry and a horse for players to ride, which takes no room.
echo "rts build red archery_range" >&3
wait_for_log "Laid out Red archery range #" 30
echo "rts build red stables" >&3
wait_for_log "Laid out Red stables #" 30
echo "rts finish red" >&3
wait_for_log "Built Red archery range #" 60
wait_for_log "Built Red stables #" 60
echo "ok: Red raised an archery range and stables"
echo "rts train red archer 1" >&3
wait_for_log "trained Red archer " 60
echo "rts train red cavalry 1" >&3
wait_for_log "trained Red cavalryman " 60
echo "rts train red horse 1" >&3
wait_for_log "trained a horse" 60
echo "rts units spawn red 1 horse" >&3
wait_for_log "A horse is no unit; train one at the stables" 30
echo "ok: the archery range trained an archer, the stables a cavalryman and a horse"
echo "rts resources blue" >&3
echo "rts buildings blue" >&3
echo "rts units blue" >&3
sleep 2
# The lobby ends the game and starts the next with the same teams, the computer still on Blue.
echo "rts newgame" >&3
wait_for_log "opened the lobby for a new game" 30
echo "rts newgame start" >&3
wait_for_log "has started on Misthalin, 256x256, with 2 teams." 120
wait_for_log "The computer plays on team " 30
echo "rts ai" >&3
sleep 2
echo "ok: the lobby started a new game with the computer on Blue"
stop_server
cat "$WEB_LOG"
if grep -qE 'This crash report has been saved|Encountered an unexpected exception' "$WEB_LOG"; then
    fail "boot-5-ai: server crashed"
fi
if grep -q "at com.warofthegods.ai." "$WEB_LOG"; then
    fail "boot-5-ai: the AI threw an exception"
fi
echo "ok: the AI played Blue without errors"

echo "Server smoke test passed"
