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
| `/rts team base <team> [x z]` | (op) Move a team's base to x z or to where you stand; a new town hall goes up there. |
| `/rts ally <team> <team>` / `/rts unally <team> <team>` | (op) Make or break an alliance. |
| `/rts resources [team]` | Show a team's Food, Wood, Stone and Metal (your own team by default). |
| `/rts resources <give\|take\|set> <team> <resource> <amount>` | (op) Change a team's resources. |
| `/rts units [team]` | List a team's units with where they are and what they are doing (your own team by default), or every team's unit count. |
| `/rts units spawn <team> [count] [soldier\|worker]` | (op) Spawn units for a team at its town hall, for free. |
| `/rts units move <team> <x> <z>` | (op) Send all of a team's units somewhere. |
| `/rts units stop <team>` | (op) Make a team's units stop and guard where they stand. |
| `/rts units kill <team\|all>` | (op) Remove units. |
| `/rts buildings [team]` | List buildings and building sites with their health, how far each site is built and how many workers work each farm or quarry. |
| `/rts build <team> <farm\|barracks\|storehouse\|quarry> [x z] [rotation]` | (op) Lay out a building site for free, near the town hall unless x z is given, and send the team's idle workers to build it. |
| `/rts train <team> <worker\|soldier> [count]` | (op) Queue units for free at the team's town hall or barracks. |
| `/rts damage <building id> <amount>` | (op) Take health off a building; at 0 it is gone. The id is the `#` number `/rts buildings` shows. |
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

## Buildings and units

Each team gets a town hall when a game starts, spread around the middle of the map and facing it. The town hall
trains workers. Workers build farms, barracks, storehouses and quarries: pick the building, place it on the web map,
and the selected workers walk to the site and build it. Building takes time, shown as a progress bar; more workers
build faster. The building goes up in the world once the site is finished.

Workers at a farm gather food and wood (5 Food and 3 Wood per worker every 5 seconds, up to 3 workers); workers at a
quarry gather stone and a little metal (4 Stone and 1 Metal per worker every 5 seconds, up to 3 workers). A worker
carries up to 20 and then walks it to the nearest town hall or storehouse, where it goes to the team, and walks back
to work. Workers hold what they carry most of in their hands. A worker that built a farm or quarry stays to work it.
A barracks trains soldiers. Training takes 5 seconds per unit, one at a time per building.

| | Costs (default) | Health | Build time (one worker) |
|---|---|---|---|
| Worker | 50 Food | | |
| Soldier | 50 Food, 20 Wood | | |
| Town hall | | 1500 | |
| Farm | 75 Wood | 300 | 15 s |
| Barracks | 150 Wood, 50 Stone | 800 | 25 s |
| Storehouse | 100 Wood | 600 | 20 s |
| Quarry | 100 Wood | 500 | 20 s |

Buildings have health. A building site starts with a tenth of it and gains the rest as it is built. Enemy soldiers
take 10 off with every hit: ordered to, or on their own when an enemy building is within 10 blocks of where they
guard. Players cannot break a building's blocks: when an enemy player tries, the block stays and the building loses
5 health. Players in creative mode break blocks as usual. At 0 health a building is gone: its blocks are cleared down
to the ground, and units training there are lost. A destroyed town hall does not come back.

A team can have up to 50 units, soldiers and workers together. Buildings need a spot inside the map, two blocks
clear of other buildings, on ground that is mostly dry and not too steep; foundations fill in dips and anything
above the building is cleared. The structures come from the beta map editor (`assets/warofthegods/structures`).

Soldiers are in their team's colours with an iron sword; workers carry an axe and never fight.

A unit that has no orders guards the spot where it stopped: it fights enemies that come within 10 blocks and walks
back once they are dead or more than 20 blocks from its post. Units also fight back when hit. Enemies are units and
players of teams that are not allied, and hostile mobs. Ordered to attack, a unit chases its target until one of
them dies.

Units keep the ground around them loaded, so they keep walking and fighting with no player nearby, and they are
saved with the game. Each unit keeps the 3x3 chunks around it loaded, which is worth keeping in mind on a small
server with many units.


The mod runs a small web server (port 8124 by default) with a live 3D map of the game, drawn the way the beta map
editor draws worlds: every block as a flat-coloured box, seen from above at an angle through an RTS camera. It shows
the terrain, the map border, buildings and building sites, every player and unit on the map in their team's colour (units wear a grey
helmet, workers a straw hat), every mob (hostile ones dark green with red eyes, animals sandy brown), and each team's resources, members, units and alliances. Zoomed far out, units and players turn into dots in their
team's colour and hostile mobs into faint dots, and animals are left out. Players on a team command their units
here.

Type `/rts web` in game and click the link. Each link works once and for 10 minutes, and logs that browser in for
72 hours; restarting the server logs everyone out. A new map is read in the background after `/rts newgame` (or a
server start) and fills in on the page as it goes; after that, changes to the world show up within a few seconds. Units, players and buildings update every server tick, 20 times a second.

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
| Town hall | Select your town hall |
| Click one of your buildings | Select it and see its health; a site shows how far it is built, a town hall or barracks its Train button and training progress. Each click queues one more, shift-click 5, up to 10 waiting; they train one at a time |
| Build farm / barracks / storehouse / quarry | With workers selected: a see-through copy follows the pointer, green where it fits and red where it does not. R or Rotate turns it a quarter round, click places it (shift-click to place more), right-click or Esc cancels |
| Right-click one of your building sites, farms or quarries | Send the selected workers to build or work it |
| Click one of your units | Select it; shift-click adds or removes it |
| Shift-drag, or Box then drag | Select your units inside the box |
| Double-click one of your units | Select all of your units in view |
| All / Clear / Esc | Select all your units, or none |
| Right-click the ground | Send the selected units there, in formation |
| Right-click an enemy unit, player or building, or any mob | Attack it (workers stay out of it) |
| Hover a unit or building | A tooltip says what it is and what it is doing, or what a right-click would do with the selected units, such as ⚔️ Attack Blue soldier 3 |
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

## The in-game RTS view

Players with the mod can command from inside Minecraft too: press **V** on the game map (rebind it under Controls,
War of the Gods) and the camera lifts above the world, looking down at an angle like the web map. The mouse is free,
and the same panels, buttons and tooltips as on the web map are drawn over the real world. Everything works the same
way and goes through the same commands, so the web map and the in-game view can be used side by side.

| Control | What it does |
|---|---|
| WASD or the arrow keys, or the mouse at the edge of the window | Move the camera (hold shift for faster) |
| Q / E | Turn a quarter round |
| Wheel / Ctrl + wheel | Zoom / tilt |
| Middle-drag / right-drag | Move the camera / turn and tilt it |
| Click, shift-click, double-click, drag a box | Select your units, as on the web map |
| Right-click | Move, attack (enemies and mobs), build or work, as on the web map |
| Click one of your buildings | Its controls in the bar, such as Train |
| H / Space | Go to your town hall / to the selected units |
| T or / | Chat, without leaving the view |
| V, or Esc with nothing selected | Back to playing in person |

While you command, your body stays where you stood, in your skin, armor and gear, and everyone sees it, in the
view, in person and on the web map. Enemy units and players can attack it, and what hurts it hurts you, through your
armor; the team panel shows your health. If it dies, you die there, drop what you carry there and get the death
screen. Pressing V again puts you back into it, wherever it was pushed. Meanwhile the real you floats unseen and
unhurt over the spot the camera looks at, so the world there stays loaded for you, and you see
in the dark. The view only shows what your client has loaded, so the camera stays within your render distance of the
spot it looks at; the web map shows the whole map. Hostile mobs such as zombies only go after real players, so they
leave a commander's body alone for now.

## Config

`config/warofthegods.cfg`: default map size, default team count, whether players may join teams themselves,
starting resources per team, the game dimension id and the width of the terrain margin around the map. The `units`
section sets what soldiers and workers cost, how many a team may have, their health and how long training takes;
the `buildings` section sets what each building costs, its health and build time, how much farms and quarries bring
in, how much a worker carries and how much damage soldiers and enemy players do to buildings. The `web`
section turns the web map on or off and sets its port, listen address, public address, how long links and logins
last, and how much of each server tick may go to drawing the map. The `rts` section sets how often players in the in-game
RTS view get live updates, and, in a player's own client config, the camera's field of view and whether the mouse at
the window's edge moves it.

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
logs in with a one-time link, serves the map's blocks and streams live state over its WebSocket, and that
workers build a farm, a barracks and a storehouse, the farm worker carries food home, units train, and a building
is destroyed at 0 health
(`.github/scripts/server-smoke-test.sh`).
