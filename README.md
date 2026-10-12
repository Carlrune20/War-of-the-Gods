# War of the Gods

An RTS mod for Minecraft 1.7.10 (Forge 10.13.4.1614). Players log onto a server, join teams and fight
for territory, with armies commanded from a web GUI.

Install the mod on the server and on every player's Forge 1.7.10 client: the server refuses clients without it,
since units are a mod entity that the client draws.

## Commands

Everyone can use `/rts`; commands marked (op) need operator rights.

| Command | What it does |
|---|---|
| `/rts info` | Show the current game: map area, teams, resources, alliances. |
| `/rts newgame [teams] [mapSize] [seed]` | (op) End the current game and start a new one on a freshly generated map, moving everyone online onto it. Defaults: 2 teams, 1000x1000 map, random seed. Teams are named after their color: Red, Blue, Green... |
| `/rts endgame` | (op) End the current game. |
| `/rts spawn [player]` | Go to the middle of the game map (sending someone else needs op). |
| `/rts team list` | List teams with their members and resources. |
| `/rts team create <name> [color]` | (op) Add a team. |
| `/rts team remove <name>` | (op) Remove a team and its alliances. |
| `/rts team color <name> <color>` | (op) Change a team's color. |
| `/rts team join <team> [player]` | Join a team (moving someone else needs op). |
| `/rts team leave [player]` | Leave your team (removing someone else needs op). |
| `/rts team base <team> [x z]` | (op) Move a team's base, where its units are trained, to x z or to where you stand. |
| `/rts ally <team> <team>` / `/rts unally <team> <team>` | (op) Make or break an alliance. |
| `/rts resources [team]` | Show a team's Food, Wood, Stone and Metal (your own team by default). |
| `/rts resources <give\|take\|set> <team> <resource> <amount>` | (op) Change a team's resources. |
| `/rts units [team]` | List a team's units with where they are and what they are doing (your own team by default), or every team's unit count. |
| `/rts units spawn <team> [count]` | (op) Spawn units for a team at its base, for free. |
| `/rts units move <team> <x> <z>` | (op) Send all of a team's units somewhere. |
| `/rts units stop <team>` | (op) Make a team's units stop and guard where they stand. |
| `/rts units kill <team\|all>` | (op) Remove units. |
| `/rts web` | Get a link to the live web map. From the server console it gives an observer link. |
| `/rts web <player>` | (op) Send a player their own web map link. |
| `/rts web observer` | (op) Get a link that shows the map without being on a team. |

A game with no enemy teams is fine: build and fight the wilderness mobs.

## The game map

Games are played in their own dimension (id 77 by default). Each `/rts newgame` throws away the previous game's
world and generates a new one from the game's seed: grassland, hills, lakes and forests over the playable square,
centred on 0,0. Around it a margin of terrain (48 blocks by default) slopes down into a sea that goes on forever, so
the map is an island.

An invisible wall at the edge of the playable square stops players and mobs: anything that crosses it is put back
inside, arrows are removed, mobs do not spawn outside it and blocks outside it cannot be broken or placed.

Players who log in while a game is running are moved onto its map (unless they are already on it), and players
who die on the map respawn in its middle. The overworld is untouched.

Game worlds are saved under `world/warofthegods/game-<number>`; the folders of older games are deleted when a new
game starts.

The game state is saved with the overworld (`world/data/warofthegods.dat`) and survives restarts. Only one game runs
at a time; it lasts until an op starts a new one.

## Units

Units are soldiers in their team's colours with an iron sword, trained at their team's base from the web map and
commanded from there. Each team gets a base when a game starts, spread around the middle of the map. Training a unit
costs 50 Food and 20 Wood by default, and a team can have up to 50.

A unit that has no orders guards the spot where it stopped: it fights enemies that come within 10 blocks and walks
back once they are dead or more than 20 blocks from its post. Units also fight back when hit. Enemies are units and
players of teams that are not allied, and hostile mobs. Ordered to attack, a unit chases its target until one of
them dies.

Units keep the ground around them loaded, so they keep walking and fighting with no player nearby, and they are
saved with the game. Each unit keeps the 3x3 chunks around it loaded, which is worth keeping in mind on a small
server with many units.


The mod runs a small web server (port 8124 by default) with a live 3D map of the game, drawn the way the beta map
editor draws worlds: every block as a flat-coloured box, seen from above at an angle through an RTS camera. It shows
the terrain, the map border, team bases, every player and unit on the map in their team's colour (units wear a grey
helmet), and each team's resources, members, units and alliances. Players on a team command their units here.

Type `/rts web` in game and click the link. Each link works once and for 10 minutes, and logs that browser in for
72 hours; restarting the server logs everyone out. A new map is read in the background after `/rts newgame` (or a
server start) and fills in on the page as it goes; after that, changes to the world show up within a few seconds.

| Control | What it does |
|---|---|
| Drag (left or middle button, or one finger) | Pan |
| Right-drag | Tilt and turn the view |
| Wheel or pinch | Zoom |
| Two fingers twisting | Turn the view |
| WASD or the arrow keys | Pan |
| Q / E, or the ⟲ ⟳ buttons | Turn a quarter round |
| Top | Look straight down with north up, like a paper map |
| Follow me / Whole map | Keep yourself in the middle, or show the whole map |

Commanding units, from the bar at the bottom of the page:

| Control | What it does |
|---|---|
| Train unit | Train a unit at your base (shift-click trains 5) |
| Click one of your units | Select it; shift-click adds or removes it |
| Shift-drag, or Box then drag | Select your units inside the box |
| Double-click one of your units | Select all of your units in view |
| All / Clear / Esc | Select all your units, or none |
| Right-click the ground | Send the selected units there, in formation |
| Right-click an enemy unit or player | Attack it |
| Stop | Stop and guard where they stand |
| On a touch screen | Tap your units to select them, then tap the ground or an enemy to give the order |

The page needs a browser with WebGL 2 (any current Chrome, Edge, Firefox or Safari). Zoomed all the way out on a
large map it draws a lot of blocks, which older phones may find slow.

Two settings matter when hosting:

- `web.port` (default `8124`): the port to open in your firewall or publish from Docker.
- `web.publicUrl`: the address players' browsers use, such as `http://play.example.com:8124`. Links use it, so it
  must be set on a real server; when empty, links point at `http://localhost:8124`, which only works on the server
  machine itself.

The page uses plain HTTP. To serve it over HTTPS, put it behind a reverse proxy (Caddy, nginx) that also forwards
WebSocket upgrades for `/ws`, and set `publicUrl` to the `https://` address.

### Docker (itzg/minecraft-server)

Publish the web port next to the game port, then set `publicUrl` in `config/warofthegods.cfg` inside the server's
data volume (the file is created on first start) and restart:

```yaml
services:
  mc:
    image: itzg/minecraft-server:java8
    environment:
      EULA: "TRUE"
      TYPE: FORGE
      VERSION: "1.7.10"
      FORGE_VERSION: "10.13.4.1614"
    ports:
      - "25565:25565"
      - "8124:8124"   # War of the Gods web map
    volumes:
      - ./data:/data
```

```
# ./data/config/warofthegods.cfg
web {
    S:publicUrl=http://your.server.address:8124
}
```

To use another port, change both sides together, for example `"8200:8200"` with `I:port=8200`, or keep the
container port and map a different host port (`"8200:8124"`) with `publicUrl` ending in `:8200`.

## Config

`config/warofthegods.cfg`: default map size, default team count, whether players may join teams themselves,
starting resources per team, the game dimension id and the width of the terrain margin around the map. The `units`
section sets what a unit costs, how many a team may have and their health. The `web`
section turns the web map on or off and sets its port, listen address, public address, how long links and logins
last, and how much of each server tick may go to drawing the map.

## Building

Built with the [GTNH ExampleMod](https://github.com/GTNewHorizons/ExampleMod1.7.10) Gradle setup
(RetroFuturaGradle). Gradle needs JDK 25 to run and finds or downloads the JDKs it needs.

```
./gradlew build        # jar in build/libs
./gradlew runServer    # dev dedicated server in run/server
./gradlew runClient    # dev client
```

CI builds the mod and boots a dedicated server three times to check that a game set up with `/rts` comes back from
the save intact with the same map, that a new game replaces the old map, and that the web map serves its page,
logs in with a one-time link, serves the map's blocks and streams live state over its WebSocket
(`.github/scripts/server-smoke-test.sh`).
