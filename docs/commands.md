# Commands

Everyone can use `/rts`. Commands marked **(op)** need operator rights.

- [Game](#game)
- [Teams and players](#teams-and-players)
- [Resources and units](#resources-and-units)
- [Buildings and mining](#buildings-and-mining)
- [Computer players and the web map](#computer-players-and-the-web-map)

## Game

| Command | What it does |
|---|---|
| `/rts info` | Show the current game: map area, teams, resources, alliances. |
| `/rts newgame [close\|start]` | (op) End the current game and open the new game lobby for everyone (see [How to play](how-to-play.md#the-lobby)); `close` closes it without a game, `start` starts the game at once without waiting for everyone to be ready. |
| `/rts quickgame [teams] [mapSize] [seed]` | (op) Skip the lobby: end the current game and start a new one on a freshly generated map at once. The gods take turns, so each follows half the teams (Red Zamorak, Blue Saradomin, Green Zamorak...), and everyone online is shuffled evenly onto the teams. Defaults: 2 teams, 1000x1000 map, random seed. Teams are named after their color: Red, Blue, Green... |
| `/rts endgame` | (op) End the current game. |
| `/rts finish [team]` | (op) Finish every building site at once. |
| `/rts memory` | (op) Show the server's heap use (used, committed and maximum), how often and how long the garbage collector has run, and the current game's unit, building and tree counts. See [Memory](hosting.md#memory). |

## Teams and players

| Command | What it does |
|---|---|
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

## Resources and units

| Command | What it does |
|---|---|
| `/rts resources [team]` | Show a team's Food, Wood and Gold (your own team by default). |
| `/rts resources <give\|take\|set> <team> <resource> <amount>` | (op) Change a team's resources. |
| `/rts units [team]` | List a team's units with where they are and what they are doing (your own team by default), or every team's unit count. |
| `/rts units spawn <team> [count] [warrior\|archer\|worker]` | (op) Spawn units for a team at its town center, for free, even with no room for them. |
| `/rts units move <team> <x> <z> [box\|line\|wedge]` | (op) Send all of a team's units somewhere, in formation. |
| `/rts units attackmove <team> <x> <z> [box\|line\|wedge]` | (op) Send a team's units somewhere, fighting any enemy they meet on the way. |
| `/rts units attack <team> <enemy team>` | (op) Send a team's warriors and archers at all of another team's units, shared out by health as an attack box does. |
| `/rts units repair <team> <building id>` | (op) Send a team's idle workers to repair one of its damaged buildings. |
| `/rts units stance <team> <aggressive\|defensive\|hold\|passive>` | (op) Set how a team's units fight when not ordered to. |
| `/rts units stop <team>` | (op) Make a team's units stop and guard where they stand. |
| `/rts units kill <team\|all>` | (op) Remove units. |
| `/rts route <x1> <z1> <x2> <z2>` | (op) Show the way a unit would walk between two points, as waypoints. |
| `/rts train <team> <worker\|warrior\|archer\|cavalry\|horse\|white_knight\|black_knight\|tradigee\|sagittare\|chaos_priest\|dark_wizard\|zamorak_mage\|lesser_demon\|monk\|centaur\|blue_wizard> [count]` | (op) Queue units for free at the team's town center, barracks, archery range, stables, White Knights' castle, Kinshra fortress, chaos temple, stone circle, monastery or wizards' tower; only one of a hero. A lesser demon is only summoned with four dark wizards at the stone circle. |

## Buildings and mining

| Command | What it does |
|---|---|
| `/rts buildings [team]` | List buildings and building sites with their health, how far each site is built and how many workers work each farm. |
| `/rts build <team> <type> [x z] [rotation]` | (op) Lay out a building site for free (farm, house, barracks, archery_range, stables, storehouse, armory, tower, town center, or for their own god's teams white_knights_castle, monastery and wizards_tower or kinshra_fortress, chaos_temple and stone_circle), near the town center unless x z is given (a town center goes on the nearest settlement nobody holds), and send the team's idle workers to build it, or for a stone circle its dark wizards. |
| `/rts upgrade <building id>` | (op) Take a town center, and with it its whole town, into the next age at once, for free. |
| `/rts research <team> <upgrade>` | (op) Give a team an upgrade at once, such as `forged_blades` or `fortified_walls`. |
| `/rts wall <team> <x1> <z1> <x2> <z2>` | (op) Lay out a wall from one point to another for free, running up to the team's towers at either end, and send the team's idle workers to build it. |
| `/rts gate <building id>` | (op) Make a finished wall segment a gate, for free. |
| `/rts mine <team>` | (op) Send a team's idle workers to the gold mine nearest its town center. |
| `/rts deplete <team>` | (op) Mine out the gold mine nearest a team's town center at once, so a quarry can go on it. |
| `/rts chop <team>` | (op) Send a team's idle workers to chop the tree nearest its town center. |
| `/rts light <team> [x1 z1 x2 z2]` | (op) Send a team's idle workers to put torches in the dark parts of an area, or around its town center. |
| `/rts damage <building id> <amount>` | (op) Take health off a building; at 0 it is gone. The id is the `#` number `/rts buildings` shows. |
| `/rts explode <building id>` | (op) Set off a creeper-sized explosion beside a building, hurting no one, to see what it does to the building. |

## Computer players and the web map

| Command | What it does |
|---|---|
| `/rts ai [team] [on\|off]` | List the teams the computer plays on, or (op) have it play on a team or stop. On a team with players it plays alongside them. |
| `/rts web` | Get a link to the live web map. From the server console it gives an observer link. |
| `/rts web <player>` | (op) Send a player their own web map link. |
| `/rts web observer` | (op) Get a link that shows the map without being on a team. |

See [Web map and in-game view](web-map-and-rts-view.md) for what the map shows and how to use it.
