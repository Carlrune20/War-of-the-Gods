package com.warofthegods.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
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
import com.warofthegods.game.GoldMine;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Settlement;
import com.warofthegods.game.Team;
import com.warofthegods.game.Tree;
import com.warofthegods.game.UnitKind;
import com.warofthegods.game.Upgrade;
import com.warofthegods.rts.Commanders;
import com.warofthegods.unit.EntityUnit;
import com.warofthegods.unit.Hunting;
import com.warofthegods.unit.Prayer;
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

    private static WebMap instance;

    private MapRenderer renderer;
    /** Whether changed tiles go out on the next tick rather than at the next interval. */
    private boolean flushTiles;
    private boolean webAnnounced;
    /** The game and world the game message last sent to RTS views was for. */
    private Game announcedGame;
    private WorldServer announcedWorld;
    private boolean announced;
    private int ticks;

    public WebMap() {
        instance = this;
    }

    /**
     * Rereads the map under an area of the game world at once, such as where a building just went up or came down,
     * and sends browsers the changed tiles on the next tick, so the map shows it as soon as the live state does.
     */
    public static void blocksChanged(World world, int minX, int minZ, int maxX, int maxZ) {
        WebMap map = instance;
        MapRenderer current = map == null ? null : map.renderer;
        if (current == null || current.world != world) {
            return;
        }
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                if (current.world.getChunkProvider()
                    .chunkExists(chunkX, chunkZ)) {
                    current.scan(current.world.getChunkFromChunkCoords(chunkX, chunkZ));
                }
            }
        }
        map.flushTiles = true;
    }

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
        // Built only when someone is watching, as it holds every unit, mob and building, and written out once for both.
        boolean toBrowsers = server != null && server.hasViewers() && ticks % Config.webStateTicks == 0;
        boolean toCommanders = commanders.any() && ticks % Config.rtsStateTicks == 0;
        if (toBrowsers || toCommanders) {
            String state = stateMessage(game, world).toString();
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

        if (renderer != null && (flushTiles || ticks % TILES_INTERVAL == 0)) {
            flushTiles = false;
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
            kindJson.addProperty("fights", kind.fights());
            if (kind.hero) {
                kindJson.addProperty("hero", true);
            }
            if (kind.isAnimal()) {
                kindJson.addProperty("animal", true);
            }
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
            if (type.faction() != null) {
                typeJson.addProperty("faction", type.faction().id);
            }
            typeJson.addProperty("health", type.maxHealth());
            if (type.buildable) {
                typeJson.addProperty("buildSeconds", type.buildsInstantly() ? 0 : type.buildTicks() / 20.0);
            }
            if (type.storesResources) {
                typeJson.addProperty("stores", true);
            }
            if (type.raisedByWizards()) {
                typeJson.addProperty("wizards", true);
            }
            if (type.isWorkplace()) {
                typeJson.addProperty("workers", type.maxWorkers());
                long[] yield = type.yield(1);
                JsonObject yieldJson = new JsonObject();
                for (Resource resource : Resource.values()) {
                    if (yield[resource.ordinal()] > 0) {
                        yieldJson.addProperty(resource.id, yield[resource.ordinal()]);
                    }
                }
                typeJson.add("yield", yieldJson);
            }
            if (!type.trains.isEmpty()) {
                JsonArray trains = new JsonArray();
                for (UnitKind kind : type.trains) {
                    trains.add(new JsonPrimitive(kind.id));
                }
                typeJson.add("trains", trains);
            }
            typeJson.addProperty("levels", type.levels);
            typeJson.addProperty("age", type.age());
            if (type.requiredUpgrade() != null) {
                typeJson.addProperty("requires", type.requiredUpgrade().id);
            }
            JsonObject upgradeCosts = new JsonObject();
            JsonArray population = new JsonArray();
            JsonArray ranges = new JsonArray();
            for (int level = 1; level <= type.levels; level++) {
                // Only the town center goes up a level, taking the town into the next age.
                if (level > 1 && type == BuildingType.TOWN_HALL) {
                    JsonObject levelJson = new JsonObject();
                    levelJson.add("cost", costJson(type.upgradeCost(level)));
                    levelJson.addProperty("seconds", type.upgradeTicks(level) / 20);
                    levelJson.addProperty("health", type.maxHealth(level));
                    upgradeCosts.add(String.valueOf(level), levelJson);
                }
                population.add(new JsonPrimitive(type.population(level)));
                ranges.add(new JsonPrimitive(type.range(level)));
            }
            typeJson.add("levelUps", upgradeCosts);
            typeJson.add("population", population);
            typeJson.add("range", ranges);
            if (type.researches()) {
                JsonArray researches = new JsonArray();
                for (Upgrade upgrade : Upgrade.values()) {
                    if (upgrade.at == type) {
                        researches.add(new JsonPrimitive(upgrade.id));
                    }
                }
                typeJson.add("researches", researches);
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
        JsonObject upgrades = new JsonObject();
        for (Upgrade upgrade : Upgrade.values()) {
            JsonObject upgradeJson = new JsonObject();
            upgradeJson.addProperty("name", upgrade.displayName);
            upgradeJson.addProperty("description", upgrade.description);
            upgradeJson.addProperty("at", upgrade.at.id);
            upgradeJson.addProperty("level", upgrade.level);
            if (upgrade.requires() != null) {
                upgradeJson.addProperty("requires", upgrade.requires().id);
            }
            upgradeJson.add("cost", costJson(upgrade.cost()));
            upgradeJson.addProperty("seconds", upgrade.seconds);
            upgrades.add(upgrade.id, upgradeJson);
        }
        json.add("upgrades", upgrades);
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
                json.addProperty("population", game.getPopulationCap(team));
                json.addProperty("age", game.getAge(team));
                json.addProperty("faction", team.getFaction().id);
                JsonArray upgrades = new JsonArray();
                for (Upgrade upgrade : team.getUpgrades()) {
                    upgrades.add(new JsonPrimitive(upgrade.id));
                }
                json.add("upgrades", upgrades);
                if (team.isRevealed()) {
                    json.addProperty("revealed", true);
                }
                if (team.isAi()) {
                    // "full" or "economy": how much of the team the computer plays.
                    json.addProperty("ai", team.getAiMode());
                }
                if (team.isDefeated()) {
                    json.addProperty("defeated", true);
                } else if (team.getDefeatTicks() >= 0) {
                    // Seconds left to raise a new town center.
                    json.addProperty("countdown", (team.getDefeatTicks() + 19) / 20);
                }
                teams.add(json);
            }
            // Seconds the game has run, stopping once it is over.
            message.addProperty("elapsed", game.getElapsedTicks() / 20);
            if (game.isOver()) {
                JsonArray winners = new JsonArray();
                for (Team team : game.getWinners()) {
                    winners.add(new JsonPrimitive(team.getId()));
                }
                message.add("winners", winners);
            }
            if (game.isSeeAll()) {
                message.addProperty("seeAll", true);
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
                    // A commanding player is where their body stands, not where they hover.
                    Entity body = Commanders.get()
                        .lastBodyOf(player.getUniqueID());
                    Entity at = body == null ? player : body;
                    json.addProperty("eid", at.getEntityId());
                    json.addProperty("name", player.getCommandSenderName());
                    if (Commanders.get()
                        .isCommanding(player.getUniqueID())) {
                        json.addProperty("commanding", true);
                    }
                    if (team != null) {
                        json.addProperty("team", team.getId());
                    }
                    json.addProperty("x", round(at.posX));
                    json.addProperty("y", round(at.posY));
                    json.addProperty("z", round(at.posZ));
                    json.addProperty("yaw", round(at.rotationYaw));
                    json.addProperty("health", round(player.getHealth()));
                    json.addProperty("maxHealth", round(player.getMaxHealth()));
                    players.add(json);
                }
            }
        }
        message.add("teams", teams);
        message.add("players", players);
        message.add("units", game == null ? new JsonArray() : unitsJson());
        message.add("mobs", game == null || world == null ? new JsonArray() : mobsJson(game, world));
        message.add("buildings", game == null ? new JsonArray() : buildingsJson(game, unitsAt()));
        message.add("trees", game == null ? new JsonArray() : treesJson(game));
        message.add("settlements", game == null ? new JsonArray() : settlementsJson(game));
        message.add("mines", game == null ? new JsonArray() : minesJson(game));
        return message;
    }

    /** Trees workers have started chopping, with the wood left in them. */
    private static JsonArray treesJson(Game game) {
        JsonArray trees = new JsonArray();
        for (Tree tree : game.getTrees()) {
            JsonObject json = new JsonObject();
            json.addProperty("x", tree.x);
            json.addProperty("y", tree.y);
            json.addProperty("z", tree.z);
            json.addProperty("top", tree.top);
            json.addProperty("wood", tree.getWood());
            json.addProperty("maxWood", tree.maxWood);
            trees.add(json);
        }
        return trees;
    }

    /** Settlements, and whether each is free for a town center. */
    private static JsonArray settlementsJson(Game game) {
        JsonArray settlements = new JsonArray();
        for (Settlement settlement : game.getSettlements()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", settlement.id);
            json.addProperty("x", settlement.minX);
            json.addProperty("z", settlement.minZ);
            json.addProperty("sizeX", settlement.sizeX);
            json.addProperty("sizeZ", settlement.sizeZ);
            json.addProperty("y", settlement.groundY);
            json.addProperty("rotation", settlement.rotation);
            json.addProperty("free", game.townCenterOn(settlement) == null);
            settlements.add(json);
        }
        return settlements;
    }

    /** Mining sites, with what they are and the gold left in them. */
    private static JsonArray minesJson(Game game) {
        JsonArray mines = new JsonArray();
        for (GoldMine mine : game.getMines()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", mine.id);
            json.addProperty("x", mine.minX);
            json.addProperty("z", mine.minZ);
            json.addProperty("sizeX", mine.size);
            json.addProperty("sizeZ", mine.size);
            json.addProperty("y", mine.groundY);
            json.addProperty("gold", mine.getGold());
            json.addProperty("maxGold", mine.maxGold);
            json.addProperty("kind", mine.kind.id);
            json.addProperty("name", mine.getName());
            // Mined out with nothing dug into it yet, so a quarry can go on it.
            json.addProperty("free", mine.isDepleted() && game.quarryOn(mine) == null);
            mines.add(json);
        }
        return mines;
    }

    /**
     * How many units build at, and how many work at, each building, by its id, as {builders, workers}: counted in one
     * pass over the units rather than one per building.
     */
    private static Map<Integer, int[]> unitsAt() {
        Map<Integer, int[]> counts = new HashMap<>();
        for (EntityUnit unit : Units.get()
            .getLoaded()) {
            int id = unit.getBuildingId();
            boolean building = unit.isBuildingAt(id);
            boolean working = unit.isWorkingAt(id);
            if (building || working) {
                int[] count = counts.computeIfAbsent(id, key -> new int[2]);
                count[building ? 0 : 1]++;
            }
        }
        return counts;
    }

    /** Every building and building site, with what it is training. */
    private static JsonArray buildingsJson(Game game, Map<Integer, int[]> unitsAt) {
        JsonArray buildings = new JsonArray();
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
            json.addProperty("level", building.getLevel());
            double[] rally = building.getRally();
            if (rally != null) {
                json.addProperty("rallyX", round(rally[0]));
                json.addProperty("rallyZ", round(rally[1]));
            }
            int range = building.isComplete() ? Buildings.rangeOf(game.getTeam(building.team), building) : 0;
            if (range > 0) {
                json.addProperty("range", range);
            }
            if (building.getResearch() != null) {
                json.addProperty("research", building.getResearch());
                json.addProperty(
                    "researched",
                    round(Math.min(1, building.getResearchTicks() / (double) Buildings.researchTicks(building))));
            }
            if (!building.isComplete()) {
                json.addProperty("built", Math.round(building.getBuildProgress() * 100) / 100.0);
                int[] count = unitsAt.get(building.id);
                json.addProperty("builders", count == null ? 0 : count[0]);
            }
            if (!building.getQueue()
                .isEmpty()) {
                JsonArray queue = new JsonArray();
                for (UnitKind kind : building.getQueue()) {
                    queue.add(new JsonPrimitive(kind.id));
                }
                json.add("queue", queue);
                double ticksPerUnit = building.getQueue()
                    .get(0)
                    .trainTicks();
                json.addProperty("progress", round(Math.min(1, building.getTrainingTicks() / ticksPerUnit)));
            }
            if (building.type.isWorkplace() && building.isComplete()) {
                int[] count = unitsAt.get(building.id);
                json.addProperty("workers", Math.min(count == null ? 0 : count[1], building.type.maxWorkers()));
            }
            if (building.getAutoTrain() != null) {
                json.addProperty("autoTrain", building.getAutoTrain().id);
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
            json.addProperty("stance", unit.getStance().id);
            if (unit.getPrayer() != Prayer.NONE) {
                json.addProperty("prayer", unit.getPrayer().id);
            }
            double[] heading = unit.getHeading();
            if (heading != null) {
                json.addProperty("toX", round(heading[0]));
                json.addProperty("toZ", round(heading[1]));
            }
            List<double[]> moves = unit.getQueuedMoves();
            if (!moves.isEmpty()) {
                // Where it walks after its goal, from shift-clicks: x, z, x, z...
                JsonArray waypoints = new JsonArray();
                for (double[] move : moves) {
                    waypoints.add(new JsonPrimitive(round(move[0])));
                    waypoints.add(new JsonPrimitive(round(move[1])));
                }
                json.add("waypoints", waypoints);
            }
            if (unit.getBuildingId() != 0) {
                json.addProperty("building", unit.getBuildingId());
                if (unit.getQueuedBuilds() > 0) {
                    json.addProperty("queued", unit.getQueuedBuilds());
                }
            }
            if (unit.getTrainedAt() != 0) {
                json.addProperty("from", unit.getTrainedAt());
            }
            int[] tree = unit.getTree();
            if (tree != null) {
                JsonArray treeJson = new JsonArray();
                for (int coordinate : tree) {
                    treeJson.add(new JsonPrimitive(coordinate));
                }
                json.add("tree", treeJson);
                if (unit.isChopping()) {
                    json.addProperty("chopping", true);
                }
            }
            if (unit.getMineId() != 0) {
                json.addProperty("mine", unit.getMineId());
                if (unit.isMining()) {
                    json.addProperty("mining", true);
                }
            }
            if (unit.getTorchesLeft() > 0) {
                json.addProperty("torches", unit.getTorchesLeft());
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
                if (unit.isCarving()) {
                    json.addProperty("carving", true);
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
            int food = Hunting.foodOf(mob);
            if (food > 0) {
                json.addProperty("food", food);
            }
            json.addProperty("x", round(mob.posX));
            json.addProperty("y", round(mob.posY));
            json.addProperty("z", round(mob.posZ));
            json.addProperty("width", round(mob.width));
            json.addProperty("height", round(mob.height));
            json.addProperty("health", round(mob.getHealth()));
            json.addProperty("maxHealth", round(mob.getMaxHealth()));
            mobs.add(json);
        }
        // Carcasses lie among them, for workers to be sent to carve.
        for (Hunting.Carcass carcass : Hunting.getCarcasses()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", "carcass-" + carcass.id);
            json.addProperty("eid", carcass.entityId);
            json.addProperty("name", carcass.displayName());
            json.addProperty("carcass", true);
            json.addProperty("hostile", false);
            json.addProperty("food", carcass.getFood());
            json.addProperty("x", round(carcass.x));
            json.addProperty("y", round(carcass.y));
            json.addProperty("z", round(carcass.z));
            json.addProperty("width", 0.9);
            json.addProperty("height", 0.5);
            json.addProperty("health", 1);
            json.addProperty("maxHealth", 1);
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
