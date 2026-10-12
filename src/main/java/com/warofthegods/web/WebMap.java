package com.warofthegods.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.ChunkEvent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.building.Buildings;
import com.warofthegods.building.Schematic;
import com.warofthegods.game.Building;
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Team;
import com.warofthegods.game.UnitKind;
import com.warofthegods.rts.Commanders;
import com.warofthegods.unit.EntityUnit;
import com.warofthegods.unit.Units;
import com.warofthegods.world.ChunkProviderRts;
import com.warofthegods.world.GameWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The server-thread side of the web map: keeps the map drawn for the running game, sends browsers the live state of
 * the game a few times a second, and runs the commands they send. Players in the in-game RTS view get the same game
 * and state messages, through {@link Commanders}.
 */
public final class WebMap {

    /** Ticks between sending browsers the changed map tiles. */
    private static final int TILES_INTERVAL = 10;
    /** Commands from browsers run per tick at most, so a flood cannot stall the server. */
    private static final int COMMANDS_PER_TICK = 64;

    private MapRenderer renderer;
    private boolean webAnnounced;
    /** The game and world the game message last sent to RTS views was for. */
    private Game announcedGame;
    private WorldServer announcedWorld;
    private boolean announced;
    private int ticks;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        WebServer server = WebServer.get();
        Commanders commanders = Commanders.get();
        Game game = GameManager.getGame();
        WorldServer world = DimensionManager.getWorld(GameWorld.dimensionId());
        Game drawn = world == null ? null : game;
        if (drawn != announcedGame || world != announcedWorld || !announced) {
            announcedGame = drawn;
            announcedWorld = world;
            announced = true;
            commanders.setGame(gameMessage(drawn));
        }
        if (server != null) {
            tickWeb(server, drawn, world);
        } else {
            renderer = null;
        }
        commanders.tick();

        ticks++;
        boolean toBrowsers = server != null && ticks % Config.webStateTicks == 0;
        boolean toCommanders = commanders.any() && ticks % Config.rtsStateTicks == 0;
        if (toBrowsers || toCommanders) {
            JsonObject state = stateMessage(game, world);
            if (toBrowsers) {
                server.setState(state);
            }
            if (toCommanders) {
                commanders.setState(state);
            }
        }
    }

    /** Keeps the map drawn for browsers, sends them changed tiles and runs their commands. */
    private void tickWeb(WebServer server, Game game, WorldServer world) {
        if (game == null) {
            if (renderer != null || !webAnnounced) {
                renderer = null;
                server.setGame(null, gameMessage(null));
                webAnnounced = true;
            }
        } else if (renderer == null || renderer.world != world || renderer.gameNumber != game.getNumber()) {
            renderer = new MapRenderer(world, game);
            JsonObject message = gameMessage(game);
            message.getAsJsonObject("game")
                .addProperty("mapId", renderer.tiles.getId());
            server.setGame(renderer.tiles, message);
            webAnnounced = true;
        }

        if (renderer != null) {
            boolean wasDone = renderer.isWalkDone();
            renderer.tick(TimeUnit.MILLISECONDS.toNanos(Config.webRenderBudgetMs));
            if (!wasDone && renderer.isWalkDone()) {
                WarOfTheGods.LOG.info("Web map of game #{} drawn", renderer.gameNumber);
            }
        }
        runCommands(server);

        if (renderer != null && ticks % TILES_INTERVAL == 0) {
            server.tilesChanged(renderer.tiles.takeChanged());
        }
    }

    /** Catches the last changes to a chunk before it unloads and stops being reread. */
    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        MapRenderer current = renderer;
        if (current != null && event.world == current.world) {
            current.scan(event.getChunk());
        }
    }

    private static void runCommands(WebServer server) {
        for (int i = 0; i < COMMANDS_PER_TICK; i++) {
            WebRequest request = server.getInbox()
                .poll();
            if (request == null) {
                return;
            }
            WebCommand command = WebCommand.REGISTRY.get(request.type);
            if (command == null) {
                request.replyError("Unknown command " + request.type);
                continue;
            }
            try {
                command.run(request);
            } catch (RuntimeException e) {
                WarOfTheGods.LOG.warn("Web command {} from {} failed", request.type, request.viewer.getName(), e);
                request.replyError("Command failed");
            }
        }
    }

    /** {"type": "game", "game": null} or the game's number, map bounds and sea level. */
    private static JsonObject gameMessage(Game game) {
        JsonObject message = WebJson.message("game");
        if (game == null) {
            message.add("game", null);
            return message;
        }
        JsonObject json = new JsonObject();
        json.addProperty("number", game.getNumber());
        json.addProperty("minX", game.getMinX());
        json.addProperty("minZ", game.getMinZ());
        json.addProperty("maxX", game.getMaxX());
        json.addProperty("maxZ", game.getMaxZ());
        json.addProperty("margin", Config.mapMargin);
        json.addProperty("tileSize", MapTiles.TILE_SIZE);
        json.addProperty("seaLevel", ChunkProviderRts.SEA_LEVEL);
        JsonObject unitKinds = new JsonObject();
        for (UnitKind kind : UnitKind.values()) {
            JsonObject kindJson = new JsonObject();
            kindJson.addProperty("name", kind.displayName);
            kindJson.add("cost", costJson(kind.cost()));
            unitKinds.add(kind.id, kindJson);
        }
        json.add("unitKinds", unitKinds);
        json.addProperty("unitsPerTeam", Config.unitsPerTeam);
        json.addProperty("trainSeconds", Config.trainSeconds);
        json.addProperty("buildingGap", Buildings.GAP);
        json.addProperty("carryCapacity", Config.carryCapacity);
        JsonObject buildingTypes = new JsonObject();
        for (BuildingType type : BuildingType.values()) {
            Schematic schematic = Buildings.get()
                .schematic(type);
            JsonObject typeJson = new JsonObject();
            typeJson.addProperty("name", type.displayName);
            typeJson.add("cost", costJson(type.cost()));
            typeJson.addProperty("buildable", type.buildable);
            typeJson.addProperty("health", type.maxHealth());
            if (type.buildable) {
                typeJson.addProperty("buildSeconds", type.buildTicks() / 20.0);
            }
            if (type.storesResources) {
                typeJson.addProperty("stores", true);
            }
            if (type.isWorkplace()) {
                typeJson.addProperty("workers", type.maxWorkers());
                long[] yield = type.yield();
                JsonObject yieldJson = new JsonObject();
                for (Resource resource : Resource.values()) {
                    if (yield[resource.ordinal()] > 0) {
                        yieldJson.addProperty(resource.id, yield[resource.ordinal()]);
                    }
                }
                typeJson.add("yield", yieldJson);
            }
            if (type.trains != null) {
                typeJson.addProperty("trains", type.trains.id);
            }
            typeJson.addProperty("width", schematic.width);
            typeJson.addProperty("length", schematic.length);
            JsonArray heights = new JsonArray();
            for (int height : schematic.columnHeights()) {
                heights.add(new JsonPrimitive(height));
            }
            typeJson.add("heights", heights);
            buildingTypes.add(type.id, typeJson);
        }
        json.add("buildingTypes", buildingTypes);
        message.add("game", json);
        return message;
    }

    private static JsonObject costJson(Map<Resource, Long> cost) {
        JsonObject json = new JsonObject();
        for (Map.Entry<Resource, Long> entry : cost.entrySet()) {
            json.addProperty(entry.getKey().id, entry.getValue());
        }
        return json;
    }

    /** Teams with their resources, alliances and bases, and where every player, unit and building on the map is. */
    private static JsonObject stateMessage(Game game, WorldServer world) {
        JsonObject message = WebJson.message("state");
        JsonArray teams = new JsonArray();
        JsonArray players = new JsonArray();
        if (game != null) {
            for (Team team : game.getTeams()) {
                JsonObject json = new JsonObject();
                json.addProperty("id", team.getId());
                json.addProperty("name", team.getName());
                json.addProperty("color", colorHex(team.getColor()));
                JsonObject resources = new JsonObject();
                for (Resource resource : Resource.values()) {
                    resources.addProperty(resource.id, team.getResource(resource));
                }
                json.add("resources", resources);
                JsonArray members = new JsonArray();
                for (Map.Entry<UUID, String> member : team.getMembers()
                    .entrySet()) {
                    JsonObject memberJson = new JsonObject();
                    memberJson.addProperty(
                        "uuid",
                        member.getKey()
                            .toString());
                    memberJson.addProperty("name", member.getValue());
                    members.add(memberJson);
                }
                json.add("members", members);
                JsonArray allies = new JsonArray();
                List<Team> allyList = game.getAllies(team);
                for (Team ally : allyList) {
                    allies.add(new JsonPrimitive(ally.getId()));
                }
                json.add("allies", allies);
                int[] base = team.getBase();
                if (base != null) {
                    JsonObject baseJson = new JsonObject();
                    baseJson.addProperty("x", base[0]);
                    baseJson.addProperty("z", base[1]);
                    json.add("base", baseJson);
                }
                json.addProperty("units", game.countUnits(team));
                teams.add(json);
            }
            if (world != null) {
                for (Object entity : world.playerEntities) {
                    EntityPlayer player = (EntityPlayer) entity;
                    Team team = game.getTeamOf(player.getUniqueID());
                    JsonObject json = new JsonObject();
                    json.addProperty(
                        "uuid",
                        player.getUniqueID()
                            .toString());
                    json.addProperty("eid", player.getEntityId());
                    json.addProperty("name", player.getCommandSenderName());
                    if (Commanders.get()
                        .isCommanding(player.getUniqueID())) {
                        json.addProperty("commanding", true);
                    }
                    if (team != null) {
                        json.addProperty("team", team.getId());
                    }
                    json.addProperty("x", round(player.posX));
                    json.addProperty("y", round(player.posY));
                    json.addProperty("z", round(player.posZ));
                    json.addProperty("yaw", round(player.rotationYaw));
                    json.addProperty("health", round(player.getHealth()));
                    players.add(json);
                }
            }
        }
        message.add("teams", teams);
        message.add("players", players);
        message.add("units", game == null ? new JsonArray() : unitsJson());
        message.add("mobs", game == null || world == null ? new JsonArray() : mobsJson(game, world));
        message.add("buildings", game == null ? new JsonArray() : buildingsJson(game));
        return message;
    }

    /** Every building and building site, with what it is training. */
    private static JsonArray buildingsJson(Game game) {
        JsonArray buildings = new JsonArray();
        double ticksPerUnit = Math.max(1, Config.trainSeconds * 20);
        for (Building building : game.getBuildings()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", building.id);
            json.addProperty("type", building.type.id);
            json.addProperty("team", building.team);
            json.addProperty("x", building.minX);
            json.addProperty("z", building.minZ);
            json.addProperty("sizeX", building.sizeX);
            json.addProperty("sizeZ", building.sizeZ);
            json.addProperty("y", building.groundY);
            json.addProperty("rotation", building.rotation);
            json.addProperty("complete", building.isComplete());
            json.addProperty("health", building.getHealth());
            json.addProperty("maxHealth", building.getMaxHealth());
            if (!building.isComplete()) {
                json.addProperty("built", Math.round(building.getBuildProgress() * 100) / 100.0);
                json.addProperty("builders", Buildings.countBuilders(building));
            }
            if (!building.getQueue()
                .isEmpty()) {
                JsonArray queue = new JsonArray();
                for (UnitKind kind : building.getQueue()) {
                    queue.add(new JsonPrimitive(kind.id));
                }
                json.add("queue", queue);
                json.addProperty("progress", round(Math.min(1, building.getTrainingTicks() / ticksPerUnit)));
            }
            if (building.type.isWorkplace() && building.isComplete()) {
                json.addProperty("workers", Buildings.countWorkers(building));
            }
            buildings.add(json);
        }
        return buildings;
    }

    /** Every loaded unit: where it is, how it is doing and what it is up to. */
    private static JsonArray unitsJson() {
        JsonArray units = new JsonArray();
        for (EntityUnit unit : Units.get()
            .getLoaded()) {
            JsonObject json = new JsonObject();
            json.addProperty(
                "id",
                unit.getUniqueID()
                    .toString());
            json.addProperty("eid", unit.getEntityId());
            json.addProperty("team", unit.getTeamId());
            json.addProperty("kind", unit.getKind().id);
            json.addProperty("number", unit.getNumber());
            json.addProperty("x", round(unit.posX));
            json.addProperty("y", round(unit.posY));
            json.addProperty("z", round(unit.posZ));
            json.addProperty("yaw", round(unit.rotationYaw));
            json.addProperty("health", round(unit.getHealth()));
            json.addProperty("maxHealth", round(unit.getMaxHealth()));
            json.addProperty(
                "order",
                unit.getOrder()
                    .name()
                    .toLowerCase());
            double[] heading = unit.getHeading();
            if (heading != null) {
                json.addProperty("toX", round(heading[0]));
                json.addProperty("toZ", round(heading[1]));
            }
            if (unit.getBuildingId() != 0) {
                json.addProperty("building", unit.getBuildingId());
            }
            if (unit.isWorker()) {
                JsonObject carrying = new JsonObject();
                for (Resource resource : Resource.values()) {
                    if (unit.getCarrying(resource) > 0) {
                        carrying.addProperty(resource.id, unit.getCarrying(resource));
                    }
                }
                if (carrying.entrySet()
                    .size() > 0) {
                    json.add("carrying", carrying);
                }
                if (unit.isDelivering()) {
                    json.addProperty("delivering", true);
                }
            }
            if (unit.getTargetId() != null) {
                json.addProperty(
                    "target",
                    unit.getTargetId()
                        .toString());
            }
            units.add(json);
        }
        return units;
    }

    /** Every mob on the map, hostile or not; units can be sent to attack any of them. */
    private static JsonArray mobsJson(Game game, WorldServer world) {
        JsonArray mobs = new JsonArray();
        for (EntityLiving mob : Units.get()
            .getMobs(game, world)) {
            JsonObject json = new JsonObject();
            json.addProperty(
                "id",
                mob.getUniqueID()
                    .toString());
            json.addProperty("eid", mob.getEntityId());
            json.addProperty("name", mob.getCommandSenderName());
            json.addProperty("hostile", mob instanceof IMob);
            json.addProperty("x", round(mob.posX));
            json.addProperty("y", round(mob.posY));
            json.addProperty("z", round(mob.posZ));
            json.addProperty("width", round(mob.width));
            json.addProperty("height", round(mob.height));
            json.addProperty("health", round(mob.getHealth()));
            json.addProperty("maxHealth", round(mob.getMaxHealth()));
            mobs.add(json);
        }
        return mobs;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /** The color chat shows for a formatting code, as #rrggbb. */
    static String colorHex(EnumChatFormatting color) {
        return String.format("#%06x", Team.rgbOf(color));
    }
}
