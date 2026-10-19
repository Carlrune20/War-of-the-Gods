# Hosting

Install the mod on the server and on every player's Forge 1.7.10 client. The server refuses clients without it, since
units are a mod entity that the client draws.

- [Configuration](#configuration)
- [Web map settings](#web-map-settings)
- [HTTPS](#https)
- [Docker](#docker-itzgminecraft-server)

## Configuration

Settings live in `config/warofthegods.cfg`. The file is created on first start.

| Section | What it sets |
|---|---|
| Top level | Default map size and team count, whether players may join teams themselves, starting resources per team, the game dimension id, and the width of the terrain margin around the map. |
| `units` | What each unit costs, how many a team may have, their health, how long training takes, and how often priests and monks earn favor. |
| `buildings` | What each building costs, its health and build time, what going up a level costs, how many units the town center and houses house, how far and hard towers shoot, how much wood trees hold and how fast workers chop, how much farms and gold mines bring in, how many settlements and gold mines a map gets, how much a worker carries, and how much damage warriors, enemy players and explosions do to buildings. |
| `games` | Whether players are shared out over the teams, whether it rains, the time of day games start at, and how long a team that lost its last town center has to raise a new one. |
| `mobs` | How rare creepers are, whether endermen spawn, how far from town centers mobs keep, how big wilderness groups get, and how often mobs wear armor or carry swords. |
| `web` | Turns the web map on or off, and sets its port, listen address, public address, how long links and logins last, and how much of each server tick may go to drawing the map. See [Web map settings](#web-map-settings). |
| `rts` | How often players in the in-game RTS view get live updates. In a player's own client config, also the camera's field of view and whether the mouse at the window's edge moves it. |

## Web map settings

Two settings matter when hosting:

- **`web.port`** (default `8124`): the port to open in your firewall or publish from Docker.
- **`web.publicUrl`**: the address players' browsers use, such as `http://play.example.com:8124`. Links use it, so it
  must be set on a real server. When it is empty, links point at `http://localhost:8124`, which only works on the server
  machine itself.

## HTTPS

The page uses plain HTTP. To serve it over HTTPS, put it behind a reverse proxy (Caddy or nginx) that also forwards
WebSocket upgrades for `/ws`, and set `publicUrl` to the `https://` address.

## Docker (itzg/minecraft-server)

Publish the web port next to the game port. Then set `publicUrl` in `config/warofthegods.cfg` inside the server's data
volume, and restart.

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

**Using another port.** Change both sides together, for example `"8200:8200"` with `I:port=8200`. Or keep the container
port and map a different host port, `"8200:8124"`, with `publicUrl` ending in `:8200`.
