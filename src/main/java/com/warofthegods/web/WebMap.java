package com.warofthegods.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Team;
import com.warofthegods.unit.EntityUnit;
import com.warofthegods.unit.Units;
import com.warofthegods.world.ChunkProviderRts;
import com.warofthegods.world.GameWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The server-thread side of the web map: keeps the map drawn for the running game, sends browsers the live state of
 * the game a few times a second, and runs the commands they send.
 */
public final class WebMap {

    /** Ticks between live state updates sent to browsers. */
    private static final int STATE_INTERVAL = 10;
    /** Commands from browsers run per tick at most, so a flood cannot stall the server. */
    private static final int COMMANDS_PER_TICK = 64;

    private MapRenderer renderer;
    private boolean announced;
    private int ticks;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        WebServer server = WebServer.get();
        if (event.phase != TickEvent.Phase.END || server == null) {
            return;
        }
        Game game = GameManager.getGame();
        WorldServer world = DimensionManager.getWorld(GameWorld.dimensionId());
        if (game == null || world == null) {
            if (renderer != null || !announced) {
                renderer = null;
                server.setGame(null, gameMessage(null));
                announced = true;
            }
        } else if (renderer == null || renderer.world != world || renderer.gameNumber != game.getNumber()) {
            renderer = new MapRenderer(world, game);
            server.setGame(renderer.tiles, gameMessage(game));
            announced = true;
        }

        if (renderer != null) {
            boolean wasDone = renderer.isWalkDone();
            renderer.tick(TimeUnit.MILLISECONDS.toNanos(Config.webRenderBudgetMs));
            if (!wasDone && renderer.isWalkDone()) {
                WarOfTheGods.LOG.info("Web map of game #{} drawn", renderer.gameNumber);
            }
        }
        runCommands(server);

        if (++ticks % STATE_INTERVAL == 0) {
            if (renderer != null) {
                server.tilesChanged(renderer.tiles.takeChanged());
            }
            server.setState(stateMessage(game, world));
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
        JsonObject cost = new JsonObject();
        for (Map.Entry<Resource, Long> entry : Units.unitCost()
            .entrySet()) {
            cost.addProperty(entry.getKey().id, entry.getValue());
        }
        json.add("unitCost", cost);
        json.addProperty("unitsPerTeam", Config.unitsPerTeam);
        message.add("game", json);
        return message;
    }

    /** Teams with their resources, alliances and bases, and where every player and unit on the map is. */
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
                    json.addProperty("name", player.getCommandSenderName());
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
        return message;
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
            json.addProperty("team", unit.getTeamId());
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

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /** The color chat shows for a formatting code, as #rrggbb. */
    static String colorHex(EnumChatFormatting color) {
        return String.format("#%06x", Team.rgbOf(color));
    }
}
