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
| `/rts newgame [close\|start]` | (op) End the current game and open the new game lobby for everyone (see below); `close` closes it without a game, `start` starts the game at once without waiting for everyone to be ready. |
| `/rts quickgame [teams] [mapSize] [seed]` | (op) Skip the lobby: end the current game and start a new one on a freshly generated map at once. The gods take turns, so each follows half the teams (Red Zamorak, Blue Saradomin, Green Zamorak...), and everyone online is shuffled evenly onto the teams. Defaults: 2 teams, 1000x1000 map, random seed. Teams are named after their color: Red, Blue, Green... |
| `/rts endgame` | (op) End the current game. |
| `/rts spawn [player]` | Go to your team's town center on the game map, or its middle on no team (sending someone else needs op). |
| `/rts surrender` | Give up: after `/rts surrender confirm`, your team is defeated. An op can make any team give up with `/rts surrender <team>`. |
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
| `/rts units attackmove <team> <x> <z> [box\|line\|wedge]` | (op) Send a team's units somewhere, fighting any enemy they meet on the way. |
| `/rts units attack <team> <enemy team>` | (op) Send a team's warriors and archers at all of another team's units, shared out by health as an attack box does. |
| `/rts units repair <team> <building id>` | (op) Send a team's idle workers to repair one of its damaged buildings. |
| `/rts units stance <team> <aggressive\|defensive\|hold\|passive>` | (op) Set how a team's units fight when not ordered to. |
| `/rts route <x1> <z1> <x2> <z2>` | (op) Show the way a unit would walk between two points, as waypoints. |
| `/rts units stop <team>` | (op) Make a team's units stop and guard where they stand. |
| `/rts units kill <team\|all>` | (op) Remove units. |
| `/rts buildings [team]` | List buildings and building sites with their health, how far each site is built and how many workers work each farm. |
| `/rts build <team> <type> [x z] [rotation]` | (op) Lay out a building site for free (farm, house, barracks, archery_range, stables, storehouse, armory, tower, town center, or for their own god's teams white_knights_castle, monastery and wizards_tower or kinshra_fortress, chaos_temple and stone_circle), near the town center unless x z is given (a town center goes on the nearest settlement nobody holds), and send the team's idle workers to build it, or for a stone circle its dark wizards. |
| `/rts train <team> <worker\|warrior\|archer\|cavalry\|horse\|white_knight\|black_knight\|tragidee\|sagittare\|chaos_priest\|dark_wizard\|zamorak_mage\|lesser_demon\|monk\|blue_wizard> [count]` | (op) Queue units for free at the team's town center, barracks, archery range, stables, White Knights' castle, Kinshra fortress, chaos temple, stone circle, monastery or wizards' tower; only one of a hero. A lesser demon is only summoned with four dark wizards at the stone circle. |
| `/rts upgrade <building id>` | (op) Take a town center, and with it its whole town, into the next age at once, for free. |
| `/rts research <team> <upgrade>` | (op) Give a team an upgrade at once, such as `forged_blades` or `fortified_walls`. |
| `/rts wall <team> <x1> <z1> <x2> <z2>` | (op) Lay out a wall from one point to another for free, running up to the team's towers at either end, and send the team's idle workers to build it. |
| `/rts gate <building id>` | (op) Make a finished wall segment a gate, for free. |
| `/rts mine <team>` | (op) Send a team's idle workers to the gold mine nearest its town center. |
| `/rts chop <team>` | (op) Send a team's idle workers to chop the tree nearest its town center. |
| `/rts light <team> [x1 z1 x2 z2]` | (op) Send a team's idle workers to put torches in the dark parts of an area, or around its town center. |
| `/rts explode <building id>` | (op) Set off a creeper-sized explosion beside a building, hurting no one, to see what it does to the building. |
| `/rts finish [team]` | (op) Finish every building site at once. |
| `/rts damage <building id> <amount>` | (op) Take health off a building; at 0 it is gone. The id is the `#` number `/rts buildings` shows. |
| `/rts ai [team] [on\|off]` | List the teams the computer plays on, or (op) have it play on a team or stop. On a team with players it plays alongside them. |
| `/rts web` | Get a link to the live web map. From the server console it gives an observer link. |
| `/rts web <player>` | (op) Send a player their own web map link. |
| `/rts web observer` | (op) Get a link that shows the map without being on a team. |

A game with no enemy teams is fine: build and fight the wilderness mobs.

## The new game lobby

An op opens the lobby with `/rts newgame` or the **New game** button in the team menu (B). That ends the running game,
and the lobby comes up on everyone's screen; anyone who closes it, or logs in while it is open, gets it back with B.
It starts with the teams of the game that just ended and everyone on the team they were on.

Only ops change the settings: the map (Misthalin is the only one so far), its size, its seed (blank for a random one),
how many teams there are (1 to 8), which god each team follows, and whether the computer plays on a team. Teams are
numbered, and every player picks their own with **Join**; ops can also click a player and then **Move here**. Any change to the settings takes everyone's Ready away, so they see it before they agree.

The game starts on its own once every player online has clicked **Ready**; the computer needs no click. Players still
on no team then go on the team with the fewest players, the computer counting as one.

The computer can play a team on its own or share one with players. Sharing, it plays as one more player: it spends
the team's resources, and commands only the units it trained itself, which players cannot order about.

Players who change team, in the lobby, the team menu or with `/rts team join`, come back after dying in front of
their new team's main town center.

## The game map

Games are played in their own dimension (id 77 by default). Each new game throws away the previous game's
world and generates a new one from the game's seed. The first map is Misthalin: grassland, low hills and forests over
the playable square, centred on 0,0, with no sand, beaches or lakes. The land goes on across a margin round the
square (48 blocks by default) and then stops, cut off sheer, with nothing beyond.

A river 7 blocks wide always runs right across the map, straight along x or z wherever it passes furthest from the
teams' town centers, so with two teams it runs between them. Units can swim it, slowly, but three stone bridges cross
it: one in the middle and one either side. The bridges cannot be broken, nothing can be built right next to them, and
the river runs straight where it passes under them.

The map is laid out the same way round every team. Besides the settlement each team starts on, it gets more
settlements spread over its side of the map, one for every 250 blocks of map width (4 on a 1000 block map, at least
one): out toward the edge far round either side and nearer the middle in between. There is a gold mine behind every
settlement and one more for each team. A flock of chickens and a herd of pigs, cows or sheep (the same for every team)
graze either side of each town center, and wild herds, one for every 125 blocks of map width, roam each team's side.
No other animals spawn, and animals that wander onto a building or building site are put back outside it.

An invisible wall at the edge of the playable square stops players and mobs: anything that crosses it is put back
inside, arrows are removed, mobs do not spawn outside it and blocks outside it cannot be broken or placed.

Every new game starts at sunrise, and it never rains on the game map. Players online when a game starts are shared
out over the teams, and players who log in later with no team join the team with the fewest players; press **B**
for the team menu to change.

The only hostile mobs are zombies and skeletons, and they are not common: every 30 seconds, day or night, a group of two or
three may turn up somewhere out of sight of players and units, mostly among trees and now and then in the open, up
to 24 on the whole map. None spawn within 40 blocks of a town center, fewer the closer they are out to 96 blocks, and
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

Every team follows a god: ops pick it for each team in the lobby, `/rts quickgame` shares the gods out evenly, a
team made with `/rts team create` follows Zamorak if red or dark and Saradomin otherwise, and an op can change it
with `/rts team faction`. Each god's buildings change with the ages. Saradomin's towns grow like Falador: oak in
Age I, white plaster in an oak frame over stone brick and cobble in Age II and white quartz in Age III, the houses
under red brick roofs from Age II. Zamorak, god of chaos and strength, has his people build hard and plain: spruce in
Age I, spruce over stone brick and cobble in Age II, and in Age III graystone, like Varrock's walls, for the town
center, the temple and the military buildings, under dark oak roofs. Each god's banners hang on its buildings:
Saradomin's star in white on blue or blue on white, Zamorak's mark in red on black or black on red. Zamorak's
buildings have plain wooden doors anyone can open, so players can walk into the armory to shop. Units wear their god's colors too: Saradomin's warriors bright steel and a blue tabard, Zamorak's black iron
and red. Each unit has a band of its team's color round its arms and waist, and its name above it in that color.

Each team gets a town center when a game starts, spread around the middle of the map and facing it. A town center is
a camp in a fenced ring round a campfire, with a longhouse, a lodge and a granary: log cabins with thatch in Age I,
framed houses over stone in Age II and stone halls, quartz or graystone, behind a low wall in Age III. It trains
workers and takes in what they gather.

Town centers stand on settlements. A settlement nobody holds is an abandoned camp laid out like a town center: three
plank huts with rotted walls and sagging roofs, one half fallen in, round a cold fireplace in a ring of mossy stones,
with a white flag at the gate. Once every team's first town
center is up, the rest of the settlements and the gold mines are laid out (see the map above). Workers can raise a
town center (300 Wood, 200 Gold) only on a settlement nobody holds, and a town center that is destroyed leaves an
abandoned settlement again. Settlements and gold mines cannot be built on or broken. Trees are cleared from a
settlement and a few blocks round it, so none grows into a town center or blocks the way.

Workers build: pick a building, place it on the web map or in the RTS view, and the selected workers walk to the site
and build it. A site goes up as a wooden framework in three stages as it
is built, and the finished building stands once the site is done; more workers build faster.

| Building | What it does | Costs (default) | Health | Build time (one worker) |
|---|---|---|---|---|
| Town center | Trains workers, takes in what they gather, houses 10 units and 5 more each age; shoots arrows from Age II; researches fortified walls; goes on a settlement nobody holds | 300 Wood, 200 Gold | 1500 | 60 s |
| House | Houses 5 more units | 60 Wood | 300 | 12 s |
| Farm | A worker there gathers food: 5 every 5 s, one worker per farm; needs Age II | 75 Wood | 300 | 15 s |
| Storehouse | Takes in what workers gather; researches economic upgrades | 100 Wood | 600 | 20 s |
| Barracks | Trains warriors; needs Age II | 150 Wood, 50 Gold | 800 | 25 s |
| Archery range | Trains archers; needs Age II | 150 Wood | 700 | 25 s |
| Stables | Trains cavalry, and tame horses for players to ride; needs Age II | 175 Wood, 50 Gold | 800 | 30 s |
| Chaos temple | Zamorak's teams only, Age II: trains chaos priests, who pray there for favor, dark wizards and Zamorak mages | 150 Wood, 100 Gold | 900 | 30 s |
| Stone circle | Zamorak's teams only, Age II: raised by dark wizards, not workers; four dark wizards standing in it summon a lesser demon | 100 Gold | 600 | 20 s |
| Monastery | Saradomin's teams only, Age II: trains monks, who earn favor just by living | 150 Wood, 75 Gold | 800 | 30 s |
| Wizards' tower | Saradomin's teams only, Age II: trains blue wizards | 100 Wood, 150 Gold | 900 | 30 s |
| Armory | Researches military upgrades; needs Age II | 120 Wood, 60 Gold | 700 | 25 s |
| Tower | Watches over the land round it; with watch towers (Age II) shoots arrows at enemies within 16 blocks, and with guard towers (Age III) within 24; researches both | 50 Wood, 100 Gold | 900 | 25 s |
| Wall | Keeps enemies out; laid out from one point to another, a 5 by 5 piece at a time | 10 Gold a piece | 600 a segment, 800 a post | 10 s a piece |
| Gate | A wall segment your own units and allies walk through; it opens as they come near | 40 Gold | 600 | at once |
| White Knights' castle | Saradomin's teams only, Age III: a white stone castle that trains White Knights and Sir Tragidee, and shoots a volley of 4 arrows a second at the nearest enemies within 20 blocks | 300 Wood, 400 Gold | 4000 | 90 s |
| Kinshra fortress | Zamorak's teams only, Age III: the Black Knights' graystone keep that trains Black Knights and Sagittare, and shoots a volley of 4 arrows a second at the nearest enemies within 20 blocks | 300 Wood, 400 Gold | 4000 | 90 s |

| Unit | Costs (default) | |
|---|---|---|
| Worker | 50 Food | Builds, farms, mines gold, chops trees and hunts; never fights |
| Warrior | 50 Food, 20 Gold | Fights up close with a sword |
| Archer | 40 Food, 40 Wood | Shoots arrows from up to 14 blocks; trained at the archery range |
| Cavalryman | 80 Food, 60 Gold | Rides a horse with a sword: half again as fast as a warrior, twice a warrior's health, hits 2 harder; trained at the stables (see below) |
| Horse | 60 Food, 20 Gold | Not a unit: a tame, saddled horse that comes out of the stables for anyone to ride, white for Saradomin and black for Zamorak; it takes no room |
| Chaos priest | 50 Food, 30 Gold | Never fights; prays at its team's chaos temple while idle there, 1 favor every 4 s, up to 5 priests a temple |
| Monk | 60 Food, 20 Gold | Never fights; earns 1 favor every 8 s wherever it is |
| Dark wizard | 60 Food, 50 Gold | Casts air (4), water (5), earth (6) and fire (7, and sets alight) strikes from archer range, and now and then weakens or confuses an enemy instead; raises stone circles; trained at the chaos temple |
| Zamorak mage | 80 Food, 120 Gold | Twice a dark wizard's health, carries a Zamorak staff and casts only fire strikes, 4 harder, from 2 blocks farther; trained at the chaos temple |
| Blue wizard | 60 Food, 50 Gold | Saradomin's wizard, casting the same spells as a dark wizard; trained at the wizards' tower |
| Lesser demon | 300 Food, 200 Wood, 300 Gold, 150 Favor | Huge, horned and tailed: ten times a warrior's health, hits 8 harder; summoned at a stone circle while four of its team's dark wizards stand in it |
| White Knight | 60 Food, 75 Gold | Saradomin's knight in white plate: twice a warrior's health, hits 3 harder; trained at the White Knights' castle |
| Black Knight | 60 Food, 75 Gold | Zamorak's knight in black plate: twice a warrior's health, hits 3 harder; trained at the Kinshra fortress |
| Sir Tragidee | 200 Food, 350 Gold | A justiciar hero who fights up close with a diamond sword: eight times a warrior's health, hits 6 harder; trained at the White Knights' castle |
| Sagittare | 200 Food, 350 Gold | A ranger hero, the boss of Daemonheim: six times a warrior's health, arrows 6 harder and 14 blocks farther than an archer's (28 blocks to their 14); trained at the Kinshra fortress |

A team has at most one of each hero, alive or in training; once he falls it can train him again. Heroes stand a head
taller than other units and go by their own names. Knights take twice as long as a warrior to train and heroes four
times as long. Knights and heroes keep their own look: the armor upgrades make them stronger but do not change what
they wear. Each knights' stronghold belongs to its god: only Saradomin's teams can build the White Knights' castle and
only Zamorak's the Kinshra fortress.

Cavalry ride into battle: once a rider has been on the move for over a second, every other arrow misses it and flies
on past, until it stops. Arrows that do hit do three quarters of their damage, and warriors' and players' blows up
close do a quarter more. The horse is part of the rider, so hitting the horse hurts the rider.

### Favor and magic

Favor is the fourth resource, shown with a nether star. It is not gathered but given by the gods for worship: Zamorak
grants it for chaos priests praying at a chaos temple, one favor every 4 seconds for each priest standing idle by the
temple (up to five a temple), and Saradomin for monks, one favor every 8 seconds for each monk alive, wherever it is.
Favor pays for the lesser demon; more of Saradomin's favor units are still to come.

Wizards and mages fight from a distance with spells: bolts of air, water, earth or fire that fly straight and trail
their element's particles. Fire sets what it hits alight. One time in three, against an enemy not already under it, a
wizard casts a curse instead: weaken puts weakness on the enemy for 3 seconds, so its blows land softer, and confuse
gives it nausea for 6 seconds. Like arrows, spells fly on past friends. Zamorak mages carry a Zamorak staff and know
only fire. A player can buy a Zamorak staff at the armory in Age III (50 Wood, 150 Gold): right-click to cast a fire
strike, once a second.

Dark wizards raise stone circles: select dark wizards (and no workers), and the build menu offers the stone circle.
Once it stands, gather four dark wizards inside it and train a lesser demon there: the wizards summon it in a swirl of
portal sparks, and it rises amid lava and smoke with a wither's roar. Training waits while fewer than four stand in
the circle.

Units walk as fast as a player. Warriors carry stone swords until the armory researches iron and then diamond ones,
and warriors and archers wear the armor upgrades from helmet to boots: chainmail with padded armor and iron with
plate. Workers hold the tool for their job, a hoe at a farm, a pickaxe at a gold mine, an axe for building and chopping and a sword for hunting, and swing it as they
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
or storehouse, where it goes to the team, and walks back to work. A worker that built a farm stays to work it.

Gold comes from mines, laid out like Old School RuneScape's surface mining sites: bare brown ground with rocks
showing their ore, as an open patch of rocks (Rimmington), rocks under a ridge (Al Kharid) or a rocky mound (south-west
of Varrock). Whatever their ores, mines yield gold, and the rarer the best ore, the more: coal mines hold 1200 gold,
iron 1600, gold 2000, adamantite (emerald ore) 2600 and runite (diamond ore) 3200. Runite is the rarest, about one mine
in twelve, and the extra mine each team gets is the same kind for every team and never runite. Select workers and
right-click a mine: each worker there mines 4 gold every 3 seconds and carries it to the nearest town center or
storehouse, and a bar over the mine shows how much is left. A mine that runs out is left as bare grey rock, and its
workers go on to the nearest mine within 64 blocks. Training takes 5 seconds per unit, one at a time per
building.

A team's town center sets its age: taking it up a level advances the whole town from Age I to Age II, for 200 Food,
200 Wood and 150 Gold, and then to Age III, for 400 Food, 300 Wood and 400 Gold. Every other building
of the team takes on the new age's look with it, at once, and buildings put up later look the age too; nothing else
about them changes. Age I has no military and no farms: workers build, chop, mine and hunt, and towers stand watch
but do not shoot. In Age II the town center houses 5 more units and starts shooting, the second row of upgrades opens
up, and barracks, archery ranges, stables, farms and armories can be built, and each god's temples and towers. For more health, research fortified walls at the town center.

Each team starts with four towers, one well off each corner of its town center, so between them they cover the
ground all round it. Towers can be built from Age I. Researching watch towers at a tower in Age II makes every tower of the
team shoot: one arrow a second, flying as fast and hitting as hard as an archer's. Guard towers, in Age III, let them
see enemies and shoot 8 blocks farther.

Walls are laid out from one point to another and built of 5 by 5 pieces: straight where the line runs straight, and a
zigzag of 5 block steps where it runs on a slant. A segment is three blocks thick and five to seven high with
battlements, or a palisade of stakes in wood; a post, at each end and turn, stands a block higher. Towers are
optional: a tower is 5 by 5 too, so a wall that starts on one lines up with it. Where there is not room for a full
piece against a tower or wall it runs up to, a shorter segment, down to a block long, closes the gap, so a wall
between two towers always joins them. A gate has an opening three wide and three high, and only a full segment can
be made one. Every piece costs gold, and workers build the pieces one after another along the line. Select
a finished segment and click Make gate to turn it into a gate at once, with no worker, for gold. Gates open for the
team's own units and its allies' and stay shut to enemies, who have to break them down. Walls start as wood; stone
walls at the town center in Age II rebuild every wall of the team in cobblestone, and
citadel walls in Age III in stone brick (graystone for Zamorak), with two and three times the health.

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
| Iron pickaxes | Storehouse | Workers mine gold half again as fast |
| Iron hoes | Storehouse | Farms yield half again as much |
| Diamond axes / pickaxes / hoes | Storehouse, Age III, after the iron one | Twice as fast or as much as with stone tools |
| Masonry | Storehouse, Age II | Buildings have a fifth more health |
| Fortified walls | Town center, Age II, for 150 Wood and 150 Gold | Buildings have half again as much health |
| Watch towers | Tower, Age II, for 100 Wood and 100 Gold | Towers shoot arrows at enemies |
| Guard towers | Tower, Age III, after watch towers, for 150 Wood and 200 Gold | Towers see and shoot 8 farther |
| Stone walls | Town center, Age II, for 100 Wood and 150 Gold | Walls and gates are rebuilt in stone, with twice the health |
| Citadel walls | Town center, Age III, after stone walls, for 150 Wood and 300 Gold | Walls and gates are rebuilt in stone brick, or graystone, with three times the health |

Buildings have health. A building site starts with a tenth of it and gains the rest as it is built. As a building
loses health it looks it: at three quarters, half and a quarter it shows holes, cracks, cobwebs and rubble. Enemy
warriors take 10 off with every hit and archers 5 with every arrow: ordered to, or on their own when an enemy building
is near where they guard. Players cannot break a building's blocks: when an enemy player tries, the block stays and
the building loses 5 health, except for blocks that break in a moment, such as torches, glass, beds, ladders and
wool, which do it no harm. Explosions, such as creepers going off, break nothing within 2 blocks of a building or
building site; the building loses up to 120 health instead, the more the closer they go off. Below half health a
building smokes and fires break out on it, and below a quarter it is ablaze; the fire is only for show, spreads nowhere,
burns nothing away and does no more damage. The fires of hearths, braziers and the chaos temple's altar are the same.
A building that falls goes down with explosions, which hurt no one. Players in creative mode break blocks as usual. At 0 health a building falls to a ruin,
and units training there are lost.

## Shops, caves and selling ores

Commanders kit themselves out at shop chests, paid for from their team's resources; the whole team is told what was
bought. Right-click the chest in your own finished town center or armory to open its shop, and click a ware to buy it.

| Shop | Age | Wares |
|---|---|---|
| Town center | I | Cooked beef, porkchops or chicken x8 (25, 25, 20 food), bread or baked potatoes x8 (15 food), apples x8 (10 food); torches x16 (5 wood), planks x32 (15 wood), cobblestone x32 (10 gold), ladders x8 (5 wood), fences x16 (10 wood), crafting table (5 wood), furnace (5 gold); stone sword (10 wood, 5 gold), stone pickaxe, axe and shovel (10 wood each); leather armor dyed in the team's color (12, 20, 16 and 12 gold) |
| Armory | II | Iron sword (40 gold), iron pickaxe, axe and shovel (30 gold each), iron armor (50, 80, 70 and 40 gold), bow (20 wood, 10 gold), arrows x16 (10 wood) |
| Armory | III | Diamond sword (250 gold), diamond pickaxe, axe and shovel (200 gold each), diamond armor (250, 400, 350 and 200 gold) |

The town center also buys ores for gold: coal 2, iron ore 6, iron ingots 7, gold ore 12, gold ingots 13, emeralds 30
and diamonds 50 (ore blocks mined with silk touch count the same). Click the hopper to sell all you carry, or
shift-click a stack in your inventory to sell just that. Nothing a shop sells can be sold back.

Ores come from under the ruins. Most old wells have gone dry: climb down the ladder in one to a small cave, and towers,
chapels, manors, farmsteads, huts, old forts and graveyards now and then have a cellar or crypt with a ladder down
inside them. Their walls are thick with coal and iron ore, and about one in four also holds a diamond or a few, low
down. Sell what you dig out, or smelt it and make armor yourself.

## Winning and losing

A team that loses its last town center has 2 minutes to raise a new one, or it is defeated. Everyone is told when the
countdown starts and again at 1 minute, 30 and 10 seconds left, and the web map and the in-game view show it across
the top; a new town center stops it. A team with no town center and no workers left cannot raise one, so it is
defeated at once. A team that gives up with `/rts surrender` is defeated too. A defeated team's units die and its
buildings fall to ruins; its players can still watch the game.

Once every team still standing is allied with all the others, they have won: everyone is told, the web map and the
in-game view show it, and the game is over until an op starts a new one. A team's age stays when it loses its town
centers.

A team can have as many units as its town center and houses house, up to 100. Buildings need a spot inside the map,
two blocks clear of other buildings, on ground that is mostly dry and not too steep; foundations fill in dips and
anything above the building is cleared. The structures are drawn by `tools/structures/generate.py` into
`assets/warofthegods/structures/<faction>`, the skins and small icons by `tools/textures/generate.py` (the resource and tool icons are copied from the Minecraft 1.7.10 client jar), and the square icons on the build, train and upgrade buttons by `tools/textures/actions.py`, which draws each building from its level 1 structure with Minecraft's block textures, each unit's face from its skin, and an item or block for each upgrade. Run it again after changing a structure.

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
plan their whole way first, around lakes, cliffs, trees and buildings, and walk it a stretch at a time. A unit that
gets stuck, wedged in a block or making no headway for a few seconds, hops out to the nearest open ground within 8
blocks, off any building, and carries on.

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
72 hours; restarting the server logs everyone out. A new map is read in the background after a new game starts (or a
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
| Click one of your buildings | Select it and see its health; a site shows how far it is built, a town center, barracks, archery range or stables its Train buttons and training progress. Each click queues one more, shift-click 5, up to 10 waiting; they train one at a time. Level takes it up a level (on the town center, Advance takes the town to the next age), and an armory or storehouse shows the upgrades it researches |
| Farm / House / Barracks / ... | With workers selected: a see-through copy follows the pointer, pale where it fits and red where it does not (in the in-game view it shows the building's own blocks). R or Rotate turns it a quarter round, click places it, right-click or Esc cancels. Shift-click places more, and the workers build them one after another in the order they were placed; shift-right-click a building site to add it to their list |
| Wall | With workers selected: click where the wall starts, or one of your towers, then where it ends (or another tower). A see-through wall follows the pointer, red where it does not fit, and the bar shows its pieces and cost. Shift-click to go on with the next wall from where this one ended |
| Make gate | With one of your finished wall segments selected: turn it into a gate at once, for gold |
| Build, train and upgrade buttons | Square icons: a building's picture, a unit's face, an item for an upgrade. Hover one to see its name, cost and what it does |
| The queue and research icons on a building | Click a unit waiting or in training, or what the building researches or its level-up, to cancel it and get back what it cost. A number on a Train button shows how many of that unit are waiting |
| Delete | Kill your selected units and destroy your selected buildings or building sites, at once |
| Right-click one of your building sites or farms, or a gold mine | Send the selected workers to build or work it, or to mine gold. Sent to an unbuilt piece of wall, they go on to build the rest of that wall after it. Right-click one of your damaged buildings to have them repair it, as fast as they would build it, for free |
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
| Right-click an enemy unit, player or building, or any mob | Attack it (workers stay out of it). Shift-right-click more enemies to have them attacked one after another |
| Right-drag, with warriors or archers selected | A red box: every enemy unit and player in it (or with none, the hostile mobs) is attacked. The enemies are shared out by their health, so a strong one draws more attackers than a weak one and every one draws at least one while there are units enough; each unit then goes on to the rest, nearest first. Shift adds them after the enemies already ordered. Ctrl and a right drag still turns the view |
| Space, or Attack move on the bar, then click the ground | With warriors or archers selected: they go there and fight any enemy or enemy building they meet on the way, going on once it is down |
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
way and goes through the same commands, so the web map and the in-game view can be used side by side. No clouds
are drawn while the view is open.

| Control | What it does |
|---|---|
| WASD or the arrow keys, or the mouse at the edge of the window | Move the camera (hold shift for faster) |
| Q / E | Turn a quarter round |
| Wheel / Ctrl + wheel | Zoom / tilt |
| Middle-drag / shift + middle-drag | Turn and tilt the camera / move it; right-click always gives orders |
| Click, shift-click, double-click, drag a box | Select your units, as on the web map |
| Delete | Kill your selected units and destroy your selected buildings, as on the web map |
| 1 … 0, Ctrl/Shift + number | Control groups, as on the web map (Ctrl + number makes a group) |
| Right-click with buildings selected / a player of your team | Rally point / follow them, as on the web map |
| Right-click | Move, attack (enemies and mobs), build or work, as on the web map; shift-right-click enemies to attack them in turn |
| Right-drag / Space then click | With warriors or archers selected: a red box of enemies to attack, shared out by their health / an attack move, as on the web map |
| Click one of your buildings | Its controls in the bar, such as Train |
| H / Space | Go to your town center / to the selected units (Space starts an attack move instead when warriors or archers are selected) |
| Minimap (bottom right) | Click or drag to move the camera there; right-click to send the selected units there. It shows the map as far as your client has seen it, with buildings, units and hostile mobs, and the part in view outlined |
| Torches | With workers selected: click two corners of an area for them to light up |
| Wall / Make gate | With workers selected: click where a wall starts and where it ends, or two of your towers; with a wall segment selected, Make gate. As on the web map |
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
section sets what each unit costs, how many a team may have, their health, how long training takes and how often
priests and monks earn favor; the `buildings` section sets what each building costs, its health and build time, what going up a level
costs, how many units the town center and houses house, how far and hard towers shoot, how much wood trees hold and
how fast workers chop, how much farms and gold mines bring in, how many settlements and gold mines a map gets, how much a worker carries and how much damage warriors
and enemy players and explosions do to buildings. The `games` section sets whether players are shared out over the
teams, whether it rains, the time of day games start at and how long a team that lost its last town center has to raise
a new one; the `mobs` section how rare creepers are, whether
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
tree for wood, and that a team losing its town center gets a countdown and a team with neither town center nor workers
is defeated, leaving a winner
(`.github/scripts/server-smoke-test.sh`).
