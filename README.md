# War of the Gods

An RTS for Minecraft 1.7.10 (Forge 10.13.4.1614). Players log onto a server, join teams, and fight for territory.
Armies are commanded from a live web map or from inside Minecraft.

> **Install on everyone.** The mod goes on the server and on every player's Forge 1.7.10 client. The server refuses
> clients without it, because units are a mod entity that the client draws.

## Quick start

1. Install the mod on the server and on every player's client.
2. As an op, start a game:
   - `/rts quickgame` starts a game straight away, with 2 teams on a random map.
   - `/rts newgame` opens a lobby where players pick their teams and click **Ready**.
3. Type `/rts web` for a link to the live map. Click it to open the map in your browser.

A game with no enemy teams is fine too. You can build and fight the wilderness mobs.

## Documentation

| Guide | What is in it |
|---|---|
| [How to play](docs/how-to-play.md) | Starting a game, the lobby, the map, gods and teams, gathering, ages, shops, and winning |
| [Buildings and units](docs/buildings-and-units.md) | Every building, unit, upgrade and hero, plus walls, health, favor, magic and Silverlight |
| [Orders and combat](docs/orders-and-combat.md) | Stances, formations, fighting, archers, cavalry, torches |
| [Commands](docs/commands.md) | The full `/rts` command reference, grouped by what it does |
| [Web map and in-game view](docs/web-map-and-rts-view.md) | How to command your army from the browser or from inside the game |
| [Hosting](docs/hosting.md) | The config file, web map settings, HTTPS, and Docker |
| [Developing](docs/developing.md) | Building, tests, CI, generated assets, and editing models in Blockbench |

## Building

```
./gradlew build        # jar in build/libs
./gradlew test         # unit tests
./gradlew runServer    # dev dedicated server in run/server
./gradlew runClient    # dev client
```

Gradle needs JDK 25 to run. See [Developing](docs/developing.md) for more.
