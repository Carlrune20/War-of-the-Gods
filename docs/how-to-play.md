# How to play

This guide covers the flow of a game: starting one, the map, gathering, ages, and how a game ends.
For the buildings and units, see [Buildings and units](buildings-and-units.md). For giving orders, see
[Orders and combat](orders-and-combat.md).

## Starting a game

Ops start a game in one of two ways:

- **`/rts newgame`** ends the current game and opens the [lobby](#the-lobby) for everyone.
- **`/rts quickgame [teams] [mapSize] [seed] [map]`** skips the lobby and starts a new game on a freshly generated
  map straight away. The map is `misthalin` (the default) or `forinthry`. The gods take turns, so each follows half the teams (Red Zamorak, Blue Saradomin, Green
  Zamorak...), and everyone online is shuffled evenly onto the teams. Defaults: 2 teams, 1000x1000 map, random
  seed. Teams are named after their colour: Red, Blue, Green...

A game with no enemy teams is fine too: build and fight the wilderness mobs.

## The lobby

An op opens the lobby with `/rts newgame` or the **New game** button in the team menu (press **B**). That ends the
running game, and the lobby comes up on everyone's screen. Anyone who closes it, or logs in while it is open, gets
it back with B. It starts with the teams of the game that just ended, and everyone on the team they were on.

Only ops change the settings:

- the map (Misthalin or [Forinthry](#forinthry-the-wilderness)), its size, and its seed (blank for a random one)
- how many teams there are (1 to 8)
- which god each team follows
- whether the computer plays on a team
- the most units a team may have at once (**-** and **+** on the second row; it starts at the configured
  `units.perTeam`, or the last game's number)
- which alliance each team is in: click a team's **Solo** button to cycle it through **Ally A** to **Ally D**. Teams in
  the same alliance start the game allied. **Players vs AI** puts every team the players are on in one alliance and every
  computer team in another; **No alliances** makes it a free for all. All the teams cannot be in one alliance, as
  nobody would be left to fight. A new lobby keeps the alliances of the game that just ended.

Teams are numbered, and every player picks their own with **Join**. Ops can also click a player and then **Move
here**. Any change to the settings takes everyone's Ready away, so they see it before they agree.

The game starts on its own once every player online has clicked **Ready**; the computer needs no click. Players
still on no team then go on the team with the fewest players, the computer counting as one.

**The computer as a player.** It can play a team on its own or share one with players. When sharing, it plays as one
more player: it spends the team's resources and commands only the units it trained itself. Players can still order any
of those about, to reassign workers, build or fight a battle themselves: the computer leaves a unit be while it carries
out a player's orders and takes it back once it stands idle. The same goes for workers while it runs only the economy.

Once it has three towers round its town center and an armory, it walls the town in: a closed ring of wall from tower to
tower, bent out round any building in the way, with a gate in the middle of each side. Any piece of it knocked down, a
gate included, it builds again before anything else.

Players who change team, in the lobby, in the team menu, or with `/rts team join`, come back after dying in front of
their new team's main town center.

## The game map

Games are played in their own dimension (id 77 by default). Each new game throws away the previous game's world and
generates a new one from the game's seed.

The first map is **Misthalin**:

- Grassland, low hills and forests cover the playable square, centred on 0,0. There is no sand, beach or lake.
- The land goes on across a margin round the square (48 blocks by default), then stops, cut off sheer, with nothing
  beyond.
- **The river.** A river 7 blocks wide always runs right across the map, straight along x or z wherever it passes
  furthest from the teams' town centers. With two teams it runs between them. Units can swim it, slowly, but three
  stone bridges cross it: one in the middle and one either side. Bridges cannot be broken, nothing can be built right
  next to them, and the river runs straight where it passes under them.
- **Settlements.** The map is laid out the same way round for every team. Besides the settlement each team starts on,
  each team gets more settlements spread over its side of the map: one for every 250 blocks of map width (4 on a
  1000 block map, and at least one). They lie out toward the edge far round either side, and nearer the middle in
  between.
- **Gold mines.** There is a gold mine behind every settlement, including the ones the teams start on, plus one more
  for each team between it and the middle. Wild gold mines lie over each team's side of the map, one for every 150
  blocks of map width (7 on a 1000 block map).
- **Animals.** A flock of chickens and a herd of pigs, cows or sheep (the same for every team) graze either side of
  each town center. Wild herds, one for every 125 blocks of map width, roam each team's side. No other animals spawn.
  Animals that wander onto a building or building site are put back outside it.
- **The edge.** An invisible wall at the edge of the playable square stops players and mobs. Anything that crosses it
  is put back inside. Arrows are removed, mobs do not spawn outside it, and blocks outside it cannot be broken or
  placed.
- **Time and weather.** Every new game starts at sunrise, and it never rains on the game map.

Players online when a game starts are shared out over the teams. Players who log in later with no team join the team
with the fewest players; press **B** for the team menu to change that.

Players who log in while a game is running are moved onto its map, unless they are already on it, and placed in front
of their team's town center. Everyone's inventory is emptied the first time they come into each new game, on any map,
so nothing is carried over from the last one. So are players when a new game starts, and players who die on the map. The overworld is
untouched.

Game worlds are saved under `world/warofthegods/game-<number>`. The folders of older games are deleted when a new game
starts. The game state is saved with the overworld (`world/data/warofthegods.dat`) and survives restarts. Only one
game runs at a time, and it lasts until an op starts a new one.

### Forinthry, the Wilderness

**Forinthry** is the burnt north of the world, where the God Wars were fought. Teams start and settle as on
Misthalin, but there is no river, and the land is hostile:

- **Charred terrain.** Mostly black ground of charred earth, scorched stone and glowing ember rock, with rocky crags,
  patches of bare earth where no grass grows, and dead dark oak trees without leaves, alone or in groves. Its biome,
  the Wilderness, never rains, and its few small ponds are a murky green-blue. Hills rise here and there.
- **Lava.** Rivers of lava wind across the land and cross each other, now and then leaving a small island between
  them, with fords to cross them. Where a river runs through the hills it cuts a canyon, its cliffs overhanging the
  lava. There are lava lakes and small lava potholes too.
- **No animals.** Nothing grazes on Forinthry: instead a ring of mushroom patches grows round each team's town
  center for its first food.
- **Always night.** The sun never rises on Forinthry, until it gets shaders of its own.
- **The old fortress.** One great walled ruin per game, 55 blocks across (on a big map now and then a second), in
  one of three layouts: a curtain wall with towers and a gatehouse round the burnt-out keep, with barracks, a chapel,
  smithy, prison, stable or storehouse in its yard, all hung with cobwebs and full of chests. No god's banner flies
  over it: the Wilderness is no one's. Ghosts, skeletons, zombies and deadly red spiders haunt it.
- **Ruins**, more of them and more dangerous than Misthalin's: the great **Graveyards of Shadows** where ghosts walk,
  their graves, crypts and mausoleums laid out at random, **bandit camps** of tents, a lookout, a cage and a stash round
  a fire, laid out anew each time, with up to four bandits, and smaller ruins (haunted graveyards, burnt chapels and
  houses, broken watchtowers and altars of Zamorak) with ghosts, hellhounds or deadly red spiders in their cobwebs.
  Every ruin has a chest of loot.
- **Mushrooms**, red and brown, grow in clumps over the ground and are Forinthry's food. Select workers and
  right-click one to harvest it with hoes, just as trees are chopped: each holds 50 food, and the workers carry it
  home and go on to the next mushroom.

Its creatures replace the zombies and skeletons, twice as many and spawning twice as often, anywhere outside towns:

| Creature | Health | Hit | Notes |
| --- | --- | --- | --- |
| Ghost | 24 | 4 | Arrows and spells mostly pass through it. Comes in twos and threes. |
| Deadly red spider | 18 | 3 | Its bite poisons. Twos and threes. |
| Hellhound | 50 | 7 | Fireproof. Twos and threes. |
| Hill giant | 90 | 10 | Its cleaver throws you back. Alone or in pairs. |
| Gargoyle | 80 | 9 | Stone: takes less from blows and arrows. Fireproof. Alone or in pairs. |
| Green / blue / red / black dragon | 200 / 240 / 280 / 360 | 12 / 13 / 14 / 16 | Always alone. They do not fly or breathe fire: they wander, and bite and claw, throwing you back. |
| Bandit | 26 | 5 | Only in bandit camps. |

## Mobs

On Misthalin the only hostile mobs are zombies and skeletons, and they are not common. Every 30 seconds, day or night, a group of
two or three may turn up somewhere out of sight of players and units, mostly among trees and now and then in the open.
There are up to 24 on the whole map.

- None spawn within 40 blocks of a town center, fewer the closer they are out to 96 blocks, and none on or right next
  to a building.
- There are no zombie villagers or baby zombies, and hurt zombies call no friends. They do not burn in daylight.
- They notice players and units only within 10 blocks. Once they have, they follow them up to 32 blocks away.
- One that a player, warrior or archer hurts goes after them from up to 32 blocks away.
- Many wear armor, and some skeletons carry swords.

Nothing else hostile spawns. All of this can be changed in the config (see [Hosting](hosting.md#configuration)).

## Teams and gods

Every team follows a god: Saradomin, Zamorak or Bandos. Ops pick each team's god in the lobby, clicking the god's
button to go through them. `/rts quickgame` shares the gods out in turn: red Zamorak, blue Saradomin, green Bandos. A
team made with `/rts team create` follows Zamorak if its colour is red or dark, Bandos if it is green, and Saradomin
otherwise. An op can change it at any time with `/rts team faction`. Bandos's teams play differently; see
[Bandos](buildings-and-units.md#bandos).

Each god's buildings change with the ages:

- **Saradomin's towns** grow like Falador. They are oak in Age I, then white plaster in an oak frame over stone brick
  and cobble in Age II, and white quartz in Age III. Their houses have red brick roofs from Age II.
- **Zamorak**, god of chaos and strength, has his people build hard and plain. Buildings are spruce in Age I, spruce
  over stone brick and cobble in Age II, and in Age III graystone, like Varrock's walls, for the town center, the
  temple and the military buildings. They have dark oak roofs.
- **Bandos's goblins** build ugly and in a hurry: heaps of cobblestone and mossy cobble propped up with dark wood,
  with more cracked and mossy stone brick in later ages.

Each god's banners hang on its buildings: Saradomin's star in white on blue or blue on white, and Zamorak's mark in red
on black or black on red. Zamorak's buildings have plain wooden doors anyone can open, so players can walk into the
armory to shop.

Units wear their god's colours too. Saradomin's warriors have bright steel and a blue tabard. Zamorak's are black iron
and red. Each unit has a band of its team's colour round its arms and waist, and its name above it in that colour.

## Gathering

**Food** comes from farms and from hunting.

- Workers at a farm gather food: 5 every 5 seconds, one worker per farm. Farms need Age II.
- To hunt, select workers and right-click a cow, pig, sheep or chicken. They run it down, then carve food off the
  carcass, 6 every 3 seconds, and carry it home 20 at a time, coming back until it is gone. A cow gives 300 food, a
  pig 250, a sheep 200 and a chicken 100. Then they go after the nearest animal around.
- Animals that warriors and archers kill leave carcasses too. An animal units kill drops nothing: it lies where it
  fell, as a dead cow, pig, sheep or chicken, until its food is carved off. Right-click one with workers to send them
  to carve it. Carcasses are not kept when the server restarts.

**Wood** comes from trees.

- Select workers and right-click a tree to chop it, from as far as a player reaches or from anywhere among its leaves.
- A tree holds 25 wood per log, between 100 and 200. A bar over it shows how much is left, as cracks spread up the
  trunk.
- When a tree is chopped out it falls, and the workers go on to the nearest tree around.
- A worker carries up to 20 wood, then walks it to the nearest town center or storehouse, where it goes to the team,
  and walks back to work. A worker that built a farm stays to work it.

**Gold** comes from mines, laid out like Old School RuneScape's surface mining sites: bare brown ground with rocks
showing their ore. The layouts are an open patch of rocks (Rimmington), rocks under a ridge (Al Kharid), or a rocky
mound (south-west of Varrock).

- Whatever their ores, mines yield gold, and the rarer the best ore, the more gold they hold:

  | Ore | Gold in the mine |
  |---|---|
  | Coal | 1200 |
  | Iron | 1600 |
  | Gold | 2000 |
  | Adamantite (emerald ore) | 2600 |
  | Runite (diamond ore) | 3200 |

- Runite is the rarest, about one mine in twelve. The extra mine each team gets is the same kind for every team, and
  it is never runite.
- Select workers and right-click a mine. Each worker there mines 4 gold every 3 seconds and carries it to the nearest
  town center or storehouse. A bar over the mine shows how much is left.
- A mine that runs out is left as bare grey rock. Its workers go on to the nearest mine within 64 blocks.
- Training takes 5 seconds per unit, one at a time per building.

## Ages

A team's town center sets its age. Taking it up a level advances the whole town:

- **Age II** costs 200 Food, 200 Wood and 150 Gold.
- **Age III** costs 400 Food, 300 Wood and 400 Gold.

Every other building of the team takes on the new age's look at once, and buildings put up later look the age too.
Nothing else about them changes.

- **Age I** has no military and no farms. Workers build, chop, mine and hunt. The town center shoots arrows at enemies
  in range. Towers stand watch but do not shoot.
- **Age II** brings the town center 5 more housing for units, opens the second row of upgrades, and unlocks barracks,
  archery ranges, stables, farms, armories, and each god's temples and towers. For more health, research fortified
  walls at the town center.

## Shops, caves and selling ores

Commanders kit themselves out at shop chests, paid for from their team's resources. The whole team is told what was
bought. Right-click the chest in your own finished town center or armory to open its shop, then click a ware to buy
it.

| Shop | Age | Wares |
|---|---|---|
| Town center | I | Cooked beef, porkchops or chicken x8 (25, 25, 20 food), bread or baked potatoes x8 (15 food), apples x8 (10 food); torches x16 (5 wood), planks x32 (15 wood), cobblestone x32 (10 gold), ladders x8 (5 wood), fences x16 (10 wood), crafting table (5 wood), furnace (5 gold); stone sword (10 wood, 5 gold), stone pickaxe, axe and shovel (10 wood each); leather armor dyed in the team's colour, the cap a leather cowl (12, 20, 16 and 12 gold) |
| Armory | II | Iron sword (40 gold), iron pickaxe, axe and shovel (30 gold each), iron armor, a silver full helm and plate; Zamorak's teams get dark iron instead (50, 80, 70 and 40 gold), bow (20 wood, 10 gold), arrows x16 (10 wood) |
| Armory | III | Runite sword (250 gold), runite pickaxe, axe and shovel (200 gold each), runite armor, a blue-green full helm and plate (250, 400, 350 and 200 gold) |

**Selling ores.** The town center buys ores for gold:

- coal 2, iron ore 6, iron ingots 7, gold ore 12, gold ingots 13, emeralds 30, diamonds 50
- ore blocks mined with silk touch count the same

Click the hopper to sell all you carry, or shift-click a stack in your inventory to sell just that. Nothing a shop sells
can be sold back.

**Caves under ruins.** Ores come from under the ruins. Most old wells have gone dry, but climb down the ladder in one
to find a small cave. Towers, chapels, manors, farmsteads, huts, old forts and graveyards now and then have a cellar or
crypt with a ladder down inside them. Their walls are thick with coal and iron ore, and about one in four also holds a
diamond or a few, low down. Sell what you dig out, or smelt it and make armor yourself.

**Chests in ruins.** Most ruins also have an old chest of loot: ores, food, building materials, weapons, armor and
arrows. A few hold something rare: the dark bow, a godsword blade, a prayer book, the dragon scimitar, a Zamorak staff
or dragon armor.
See [Ruin chests and artifacts](buildings-and-units.md#ruin-chests-and-artifacts).

## Winning and losing

- **Losing a town center.** A team that loses its last town center has 2 minutes to raise a new one, or it is
  defeated. Everyone is told when the countdown starts, and again at 1 minute, 30 and 10 seconds left. The web map and
  the in-game view show it across the top. A new town center stops the countdown.
- **Losing outright.** A team with no town center and no workers left cannot raise one, so it is defeated at once. A
  team that gives up with `/rts surrender` is defeated too.
- **What defeat means.** A defeated team's units die and its buildings fall to ruins. Its players can still watch the
  game.
- **Winning.** Once every team still standing is allied with all the others, they have won. Everyone is told, the web
  map and the in-game view show it, and the game is over until an op starts a new one.

A team's age stays when it loses its town centers.
