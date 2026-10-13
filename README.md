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
| `/rts newgame [teams] [mapSize] [seed]` | (op) End the current game and start a new one on a freshly generated map, moving everyone online onto it and sharing them out over the teams. Defaults: 2 teams, 1000x1000 map, random seed. Teams are named after their color: Red, Blue, Green... |
| `/rts endgame` | (op) End the current game. |
| `/rts spawn [player]` | Go to your team's town center on the game map, or its middle on no team (sending someone else needs op). |
| `/rts team list` | List teams with their members and resources. |
| `/rts team create <name> [color]` | (op) Add a team. |
| `/rts team remove <name>` | (op) Remove a team and its alliances. |
| `/rts team color <name> <color>` | (op) Change a team's color. |
| `/rts team faction <name> <saradomin\|zamorak>` | (op) Change which god a team follows, and so how its buildings and units look. |
| `/rts team join <team> [player]` | Join a team (moving someone else needs op). |
| `/rts team leave [player]` | Leave your team (removing someone else needs op). |
| `/rts team auto [all]` | (op) Put everyone online who is on no team on the team with the fewest players; with `all`, share everyone out afresh. |
| `/rts team base <team> [x z]` | (op) Move a team's base to x z or to where you stand; a new town center goes up there. |
| `/rts ally <team> <team>` / `/rts unally <team> <team>` | (op) Make or break an alliance. |
| `/rts resources [team]` | Show a team's Food, Wood and Gold (your own team by default). |
| `/rts resources <give\|take\|set> <team> <resource> <amount>` | (op) Change a team's resources. |
| `/rts units [team]` | List a team's units with where they are and what they are doing (your own team by default), or every team's unit count. |
| `/rts units spawn <team> [count] [warrior\|archer\|worker]` | (op) Spawn units for a team at its town center, for free, even with no room for them. |
| `/rts units move <team> <x> <z> [box\|line\|wedge]` | (op) Send all of a team's units somewhere, in formation. |
| `/rts units stance <team> <aggressive\|defensive\|hold\|passive>` | (op) Set how a team's units fight when not ordered to. |
| `/rts route <x1> <z1> <x2> <z2>` | (op) Show the way a unit would walk between two points, as waypoints. |
| `/rts units stop <team>` | (op) Make a team's units stop and guard where they stand. |
| `/rts units kill <team\|all>` | (op) Remove units. |
| `/rts buildings [team]` | List buildings and building sites with their health, how far each site is built and how many workers work each farm or quarry. |
| `/rts build <team> <type> [x z] [rotation]` | (op) Lay out a building site for free (farm, house, barracks, storehouse, quarry, armory or tower), near the town center unless x z is given, and send the team's idle workers to build it. |
| `/rts train <team> <worker\|warrior\|archer> [count]` | (op) Queue units for free at the team's town center or barracks. |
| `/rts upgrade <building id>` | (op) Take a town center, and with it its whole town, into the next age at once, for free. |
| `/rts research <team> <upgrade>` | (op) Give a team an upgrade at once, such as `forged_blades` or `fortified_walls`. |
| `/rts chop <team>` | (op) Send a team's idle workers to chop the tree nearest its town center. |
| `/rts light <team> [x1 z1 x2 z2]` | (op) Send a team's idle workers to put torches in the dark parts of an area, or around its town center. |
| `/rts explode <building id>` | (op) Set off a creeper-sized explosion beside a building, hurting no one, to see what it does to the building. |
| `/rts finish [team]` | (op) Finish every building site at once. |
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

Every new game starts at sunrise, and it never rains on the game map. Players online when a game starts are shared
out over the teams, and players who log in later with no team join the team with the fewest players; press **B**
for the team menu to change.

The only hostile mobs are zombies and skeletons, and they are rare: every 45 seconds, day or night, a group of two or
three may turn up somewhere out of sight of players and units, mostly among trees and now and then in the open, up
to 16 on the whole map. None spawn within 40 blocks of a town center, fewer the closer they are out to 96 blocks, and
none on or right next to a building; no zombie villagers or baby zombies, and hurt zombies call no friends. They do
not burn in daylight. They notice players and units only within 10 blocks, but once they have, they follow them up
to 32. One a player, warrior or archer hurts goes after them from up to 32 blocks away. Many wear armor and some skeletons carry swords. Nothing else hostile spawns. All of this can be changed in
the config.

Players who log in while a game is running are moved onto its map (unless they are already on it), in front of their
team's town center, and so are players when a new game starts and players who die on the map. The overworld is untouched.

Game worlds are saved under `world/warofthegods/game-<number>`; the folders of older games are deleted when a new
game starts.

The game state is saved with the overworld (`world/data/warofthegods.dat`) and survives restarts. Only one game runs
at a time; it lasts until an op starts a new one.

## Buildings and units

Every team follows a god. Teams follow Saradomin or Zamorak by their color, red and the dark colors Zamorak and the
rest Saradomin, and an op can change it with `/rts team faction`. Saradomin's people build plain, human stone and
timber with blue banners; Zamorak's build in nether brick, obsidian and blackened wood, with red banners, spikes and
skulls. Units wear their god's colors too: Saradomin's warriors bright steel and a blue tabard, Zamorak's black iron
and red. Each unit has a band of its team's color round its arms and waist, and its name above it in that color.

Each team gets a town center when a game starts, spread around the middle of the map and facing it. It trains
workers and takes in what they gather. Workers build: pick a building, place it on the web map or in the RTS view,
and the selected workers walk to the site and build it. A site goes up as a wooden framework in three stages as it
is built, and the finished building stands once the site is done; more workers build faster.

| Building | What it does | Costs (default) | Health | Build time (one worker) |
|---|---|---|---|---|
| Town center | Trains workers, takes in what they gather, houses 10 units and 5 more each age; shoots arrows from Age II; researches fortified walls | | 1500 | |
| House | Houses 5 more units | 60 Wood | 300 | 12 s |
| Farm | A worker there gathers food: 5 every 5 s, one worker per farm | 75 Wood | 300 | 15 s |
| Quarry | A pit with ore in its walls; workers there gather 5 gold every 5 s, up to 3 workers | 100 Wood | 500 | 20 s |
| Storehouse | Takes in what workers gather; researches economic upgrades | 100 Wood | 600 | 20 s |
| Barracks | Trains warriors and archers | 150 Wood, 50 Gold | 800 | 25 s |
| Armory | Researches military upgrades; needs Age II | 120 Wood, 60 Gold | 700 | 25 s |
| Tower | Shoots arrows at enemies within 16 blocks; needs Age II | 50 Wood, 100 Gold | 900 | 25 s |

| Unit | Costs (default) | |
|---|---|---|
| Worker | 50 Food | Builds, farms, quarries, chops trees and hunts; never fights |
| Warrior | 50 Food, 20 Wood | Fights up close with a sword |
| Archer | 40 Food, 40 Wood | Shoots arrows from up to 14 blocks |

Units walk as fast as a player. Warriors carry stone swords until the armory researches iron and then diamond ones,
and warriors and archers wear the armor upgrades from helmet to boots: chainmail with padded armor and iron with
plate. Workers hold the tool for their job, a hoe at a farm, a pickaxe at a quarry, an axe for building and chopping and a sword for hunting, and swing it as they
work. Their tools are stone until the storehouse researches iron ones, and diamond after that in Age III. They hold
what they carry only on the way to a store.

Archers draw their bow as a player does, a second for a full draw, and loose arrows as fast and deadly as a player's
fully drawn bow, trailing crit sparks: 6 damage before upgrades, and up to half again on a crit. Ordered to attack,
they first spread out in a line facing the target, a few blocks inside their range, with a second row in the gaps if
there are more than 12. Their arrows fly on past friends. Hits from warriors and arrows from archers and towers all
land, however recently the target was last hit, so four archers on one enemy do four times the harm of one. Archers
shoot at buildings too, taking 5 off with each arrow.

Food also comes from hunting. Select workers and right-click a cow, pig, sheep or chicken: they run it down, then
carve food off the carcass, 6 every 3 seconds, and carry it home 20 at a time, coming back until it is gone. A cow
gives 300 food, a pig 250, a sheep 200 and a chicken 100. Then they go after the nearest animal around. Animals
warriors and archers kill leave carcasses too. An animal units kill drops nothing: it lies on its side where it fell,
as a dead cow, dead pig, dead sheep or dead chicken, until its food is carved off; right-click one with workers to send
them to carve it. Carcasses are not kept when the server restarts.

Wood comes from trees. Select workers and right-click a tree to chop it from as far as a player reaches, or from
anywhere in among its leaves: a tree holds 25 wood per log, between 100 and 200, and a bar over it shows how much is left as cracks spread up its trunk. When it is chopped out it falls, and
the workers go on to the nearest tree around. A worker carries up to 20 and then walks it to the nearest town center
or storehouse, where it goes to the team, and walks back to work. A worker that built a farm or quarry stays to work it. Training takes 5 seconds per unit, one at a time per
building.

A team's town center sets its age: taking it up a level advances the whole town from Age I to Age II, for 200 Food,
200 Wood and 150 Gold, and then to Age III, for 400 Food, 300 Wood and 400 Gold. Every other building
of the team takes on the new age's look with it, at once, and buildings put up later look the age too; nothing else
about them changes. In Age II the town center houses 5 more units and starts shooting, and the second row of upgrades
opens up. Armories and towers need Age II. For more health, research fortified walls at the town center.

An upgrade that needs another one first only shows up once the team has that one.

| Upgrade | At | Does |
|---|---|---|
| Iron swords | Armory | Warriors carry iron swords and hit 2 harder |
| Diamond swords | Armory, Age III, after iron swords | Warriors carry diamond swords and hit 2 harder again |
| Fletching | Armory | Archers and towers shoot 1 harder and 4 farther |
| Fire arrows | Armory, Age II, after fletching | Archers' arrows set enemies they hit on fire for a few seconds; they never set the team or its allies alight |
| Padded armor | Armory | Warriors and archers have 6 more health, and wear chainmail |
| Plate armor | Armory, Age II, after padded armor | Warriors and archers have 8 more health, and wear iron |
| Bigger baskets | Storehouse | Workers carry 10 more |
| Iron axes | Storehouse | Workers chop half again as fast |
| Iron pickaxes | Storehouse | Quarries yield half again as much |
| Iron hoes | Storehouse | Farms yield half again as much |
| Diamond axes / pickaxes / hoes | Storehouse, Age III, after the iron one | Twice as fast or as much as with stone tools |
| Masonry | Storehouse, Age II | Buildings have a fifth more health |
| Fortified walls | Town center, Age II, for 150 Wood and 150 Gold | Buildings have half again as much health |

Buildings have health. A building site starts with a tenth of it and gains the rest as it is built. As a building
loses health it looks it: at three quarters, half and a quarter it shows holes, cracks, cobwebs and rubble. Enemy
warriors take 10 off with every hit and archers 5 with every arrow: ordered to, or on their own when an enemy building
is near where they guard. Players cannot break a building's blocks: when an enemy player tries, the block stays and
the building loses 5 health, except for blocks that break in a moment, such as torches, glass, beds, ladders and
wool, which do it no harm. Explosions, such as creepers going off, break nothing within 2 blocks of a building or
building site; the building loses up to 120 health instead, the more the closer they go off. Below half health a
building smokes, and below a quarter it burns, though the fire spreads nowhere. Players in creative mode break blocks as usual. At 0 health a building falls to a ruin,
and units training there are lost. A destroyed town center does not come back.

A team can have as many units as its town center and houses house, up to 100. Buildings need a spot inside the map,
two blocks clear of other buildings, on ground that is mostly dry and not too steep; foundations fill in dips and
anything above the building is cleared. The structures are drawn by `tools/structures/generate.py` into
`assets/warofthegods/structures/<faction>`, and the skins and icons by `tools/textures/generate.py` (the resource and tool icons are copied from the Minecraft 1.7.10 client jar).

A unit that has no orders guards the spot where it stopped. How it fights depends on its stance, set from the bar
with warriors or archers selected:

| Stance | What a unit does when not ordered to fight |
|---|---|
| Aggressive | Looks for enemies half again as far and chases them up to 40 blocks from its post |
| Defensive (the default) | Fights enemies that come within 10 blocks (archers within their range), and walks back once they are dead or more than 20 blocks from its post |
| Hold | Never leaves its spot: fights only what it can reach from there |
| Passive | Never fights, not even back |

Units sent somewhere line up there in the formation picked on the bar, facing the way they went, with warriors in
front, archers behind them and workers at the back: a box (the default), a wide line, or a wedge. Units going far
plan their whole way first, around lakes, cliffs, trees and buildings, and walk it a stretch at a time.

Workers put up torches to light the town up: select workers, press Torches and click two corners of an area,
up to 64 blocks each way. They put a torch, for 1 wood, wherever the area is dark enough for mobs, leaving buildings
alone. Units also fight back when hit. Enemies are units and
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
| Town center | Select your town center |
| Click one of your buildings | Select it and see its health; a site shows how far it is built, a town center or barracks its Train buttons and training progress. Each click queues one more, shift-click 5, up to 10 waiting; they train one at a time. Level takes it up a level (on the town center, Advance takes the town to the next age), and an armory or storehouse shows the upgrades it researches |
| Farm / House / Barracks / ... | With workers selected: a see-through copy follows the pointer, pale where it fits and red where it does not (in the in-game view it shows the building's own blocks). R or Rotate turns it a quarter round, click places it, right-click or Esc cancels. Shift-click places more, and the workers build them one after another in the order they were placed; shift-right-click a building site to add it to their list |
| Right-click one of your building sites, farms or quarries | Send the selected workers to build or work it |
| Right-click a tree | Send the selected workers to chop it |
| Torches | With workers selected: click two corners of an area for them to light up against mobs |
| Click one of your units | Select it; shift-click adds or removes it |
| Shift-drag, or Box then drag | Select your units inside the box, or with none in it your buildings |
| Double-click one of your units | Select all of your units in view |
| Shift-click / double-click one of your buildings | Add it to the selected buildings or take it out / select all your buildings of that kind in view. Train buttons then share the units out, each going to the building with the shortest queue |
| The worker, warrior and archer counts on the bar | How many of each are selected. Click or ctrl-click one to keep only those, shift-click to drop them |
| N idle | Go to the next worker with nothing to do; shift-click selects them all |
| Hover or select a tower | A ring on the ground shows how far it shoots |
| Ctrl + 1 … 0 (or Alt + number, since browsers keep Ctrl + number for tabs) | Make the selected units and buildings control group 1 … 0 |
| Shift + number | Add the selection to that control group |
| Number | Select that control group; press it again to move the camera to it. The groups also show on the bar |
| Right-click with only buildings selected | Set a rally point: units they train walk there. Right-click one of your units that is in a control group and new units join that group instead. Clear rally on the bar removes it |
| Right-click a player of your team, such as yourself | The selected units follow them, keeping the formation they were in, with a block of space between you and the front row |
| All / Clear / Esc | Select all your units, or none |
| Right-click the ground | Send the selected units there, in the formation picked on the bar |
| Formation / Stance | With warriors or archers selected: how they line up where they are sent (box, line, wedge), and how they fight on their own (aggressive, defensive, hold, passive) |
| Right-click an enemy unit, player or building, or any mob | Attack it (workers stay out of it) |
| Hover a unit or building | A tooltip says what it is and what it is doing, or what a right-click would do with the selected units, such as Attack Blue warrior 3 |
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
| Middle-drag / shift + middle-drag | Turn and tilt the camera / move it; right-click always gives orders |
| Click, shift-click, double-click, drag a box | Select your units, as on the web map |
| 1 … 0, Ctrl/Shift + number | Control groups, as on the web map (Ctrl + number makes a group) |
| Right-click with buildings selected / a player of your team | Rally point / follow them, as on the web map |
| Right-click | Move, attack (enemies and mobs), build or work, as on the web map |
| Click one of your buildings | Its controls in the bar, such as Train |
| H / Space | Go to your town center / to the selected units |
| Minimap (bottom right) | Click or drag to move the camera there; right-click to send the selected units there. It shows the map as far as your client has seen it, with buildings, units and hostile mobs, and the part in view outlined |
| Torches | With workers selected: click two corners of an area for them to light up |
| B | The team menu, in the view or in person: join or leave a team; ops click a player to move them, or fill or shuffle the teams |
| T or / | Chat, without leaving the view |
| V, or Esc with nothing selected | Back to playing in person |

While you command, your body stays where you stood, in your skin, armor and gear, and everyone sees it, in the
view, in person and on the web map. Enemy units and players can attack it, and what hurts it hurts you, through your
armor; the team panel shows your health. If it dies, you die there, drop what you carry there and get the death
screen. Pressing V again puts you back into it, wherever it was pushed. Meanwhile the real you floats unseen and
unhurt over the spot the camera looks at, so the world there stays loaded for you, and you see
in the dark. The view only shows what your client has loaded, so the camera stays within your render distance of the
spot it looks at; the web map shows the whole map. Hostile mobs go after real players and units, but leave a commander's
body alone for now.

## Config

`config/warofthegods.cfg`: default map size, default team count, whether players may join teams themselves,
starting resources per team, the game dimension id and the width of the terrain margin around the map. The `units`
section sets what warriors, archers and workers cost, how many a team may have, their health and how long training
takes; the `buildings` section sets what each building costs, its health and build time, what going up a level
costs, how many units the town center and houses house, how far and hard towers shoot, how much wood trees hold and
how fast workers chop, how much farms and quarries bring in, how much a worker carries and how much damage warriors
and enemy players and explosions do to buildings. The `games` section sets whether players are shared out over the
teams, whether it rains and the time of day games start at; the `mobs` section how rare creepers are, whether
endermen spawn, how far from town centers mobs keep, how big wilderness groups get and how often mobs wear armor or
carry swords. The `web`
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
workers build a farm, a barracks and a storehouse, the farm worker carries food home, warriors and archers train, a
building is destroyed at 0 health, a house and a tower go up, the town center goes up a level and a worker chops a
tree for wood
(`.github/scripts/server-smoke-test.sh`).
