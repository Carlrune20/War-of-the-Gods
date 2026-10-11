# War of the Gods

An RTS mod for Minecraft 1.7.10 (Forge 10.13.4.1614). Players log onto a server, join teams and fight
for territory, with armies commanded from a web GUI.

The mod is server side only: players can join with a plain Forge 1.7.10 client.

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
| `/rts ally <team> <team>` / `/rts unally <team> <team>` | (op) Make or break an alliance. |
| `/rts resources [team]` | Show a team's Food, Wood, Stone and Metal (your own team by default). |
| `/rts resources <give\|take\|set> <team> <resource> <amount>` | (op) Change a team's resources. |

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

## Config

`config/warofthegods.cfg`: default map size, default team count, whether players may join teams themselves,
starting resources per team, the game dimension id and the width of the terrain margin around the map.

## Building

Built with the [GTNH ExampleMod](https://github.com/GTNewHorizons/ExampleMod1.7.10) Gradle setup
(RetroFuturaGradle). Gradle needs JDK 25 to run and finds or downloads the JDKs it needs.

```
./gradlew build        # jar in build/libs
./gradlew runServer    # dev dedicated server in run/server
./gradlew runClient    # dev client
```

CI builds the mod and boots a dedicated server twice to check that a game set up with `/rts` comes back from the
save intact with the same map, and that a new game replaces the old map (`.github/scripts/server-smoke-test.sh`).
