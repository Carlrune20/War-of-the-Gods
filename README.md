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

## Web map

The mod runs a small web server (port 8124 by default) with a live top-down map of the game: the terrain drawn from
the world itself, the map border, every player on the map in their team's color, and each team's resources,
members and alliances. Armies will be commanded from this page in later versions.

Type `/rts web` in game and click the link. Each link works once and for 10 minutes, and logs that browser in for
72 hours; restarting the server logs everyone out. Drag to pan, scroll or pinch to zoom, and use "Follow me" to keep
yourself centred. A new map is drawn in the background after `/rts newgame` (or a server start) and fills in on the
page as it goes; after that, changes to the world show up within a few seconds.

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
starting resources per team, the game dimension id and the width of the terrain margin around the map. The `web`
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
logs in with a one-time link, draws map tiles and streams live state over its WebSocket
(`.github/scripts/server-smoke-test.sh`).
