package com.warofthegods.client.rts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * What the client knows of the game for the RTS view, read from the same game and live state messages the web map
 * gets (see WebMap.java). Positions here are as of the last update; units, players and mobs near the camera are drawn
 * from the client's own entities, matched by entity id.
 */
public final class RtsState {

    public static final String[] RESOURCES = { "food", "wood", "gold" };

    public static final class UnitKindInfo {

        public String id;
        public String name;
        public Map<String, Long> cost;
        public boolean fights;
        /** Whether it is a hero, who goes by his own name and of whom a team has one at a time. */
        public boolean hero;
    }

    public static final class LevelInfo {

        public Map<String, Long> cost;
        public int seconds;
        public int health;
    }

    public static final class UpgradeInfo {

        public String id;
        public String name;
        public String description;
        /** The building type it is researched at. */
        public String at;
        public int level;
        /** The upgrade the team needs first, or "" for none; until then this one is not offered. */
        public String requires;
        public Map<String, Long> cost;
        public int seconds;
    }

    public static final class BuildingTypeInfo {

        public String id;
        public String name;
        public Map<String, Long> cost;
        public boolean buildable;
        /** The only faction whose teams can build it, or null when every team can. */
        public String faction;
        public int health;
        public int workers;
        public final List<String> trains = new ArrayList<>();
        public int levels;
        /** What going up to each level costs, by level. */
        public final Map<Integer, LevelInfo> levelUps = new HashMap<>();
        /** Units it houses at each level, from level 1. */
        public int[] population;
        /** The age a team needs before it can build this. */
        public int age = 1;
        public final List<String> researches = new ArrayList<>();
        public int width;
        public int length;
        public int[] heights;
    }

    public static final class GameInfo {

        public int number;
        public int minX;
        public int minZ;
        public int maxX;
        public int maxZ;
        public int seaLevel;
        public int unitsPerTeam;
        public int buildingGap;
        public final Map<String, UnitKindInfo> unitKinds = new LinkedHashMap<>();
        public final Map<String, BuildingTypeInfo> buildingTypes = new LinkedHashMap<>();
        public final Map<String, UpgradeInfo> upgrades = new LinkedHashMap<>();
    }

    public static final class TeamInfo {

        public String id;
        public String name;
        public int color;
        public final Map<String, Long> resources = new HashMap<>();
        public final List<String> members = new ArrayList<>();
        public final List<String> allies = new ArrayList<>();
        public int units;
        public int population;
        public int age = 1;
        public String faction;
        public final List<String> upgrades = new ArrayList<>();
        public boolean defeated;
        /** Seconds left to raise a new town center, or -1 when it has one. */
        public int countdown = -1;
    }

    /** Something with a place in the world: a unit, player or mob. */
    public abstract static class Thing {

        public String id;
        public int eid;
        public double x;
        public double y;
        public double z;
        public double health;
        public double maxHealth;
    }

    public static final class UnitInfo extends Thing {

        public String team;
        public String kind;
        public int number;
        public String order;
        /** Where it is headed, or NaN. */
        public double toX = Double.NaN;
        public double toZ = Double.NaN;
        public int building;
        /** The building that trained it, or 0. */
        public int from;
        public final Map<String, Long> carrying = new HashMap<>();
        public boolean delivering;
        public String target;
        /** The foot of the tree it chops, or null. */
        public int[] tree;
        public boolean chopping;
        public boolean mining;
        public boolean carving;
        /** Torches a worker still has to put up. */
        public int torches;
        /** Building sites a worker builds after the one it is on. */
        public int queued;
        public String stance = "defensive";
        /** The protection prayer it has up: melee, missiles or none. */
        public String prayer = "none";

        public boolean isWorker() {
            return "worker".equals(kind);
        }

        public boolean isArcher() {
            return "archer".equals(kind);
        }
    }

    public static final class PlayerInfo extends Thing {

        public String name;
        public String team;
        public boolean commanding;
    }

    public static final class MobInfo extends Thing {

        public String name;
        public boolean hostile;
        /** Food it leaves for workers who hunt it, or a carcass has left; 0 when it cannot be hunted. */
        public int food;
        /** Whether it is the carcass of an animal units killed, such as a dead pig, for workers to carve. */
        public boolean carcass;
        public double width;
        public double height;
    }

    public static final class BuildingInfo {

        public int id;
        public String type;
        public String team;
        public int x;
        public int z;
        public int sizeX;
        public int sizeZ;
        public int y;
        public int rotation;
        public boolean complete;
        public double built;
        public int builders;
        public int health;
        public int maxHealth;
        /** How far it shoots, with its team's upgrades; 0 if it does not. */
        public int range;
        /** Where units it trains walk to, or NaN. */
        public double rallyX = Double.NaN;
        public double rallyZ = Double.NaN;
        public final List<String> queue = new ArrayList<>();
        public double progress;
        public int workers;
        public int level = 1;
        /** An upgrade id, or "level" while it goes up a level; null when it researches nothing. */
        public String research;
        public double researched;

        public boolean contains(double px, double pz) {
            return px >= x && px < x + sizeX && pz >= z && pz < z + sizeZ;
        }
    }

    public static final class TreeInfo {

        public int x;
        public int y;
        public int z;
        public int top;
        public int wood;
        public int maxWood;
    }

    /** A settlement, where a town center stands or can go up, or a gold mine: a footprint on the map. */
    public static final class FeatureInfo {

        public int id;
        public int x;
        public int z;
        public int sizeX;
        public int sizeZ;
        public int y;
        public int rotation;
        /** For a settlement: whether no town center stands on it. */
        public boolean free;
        /** For a gold mine: the gold left in it, and what it started with. */
        public int gold;
        public int maxGold;

        public boolean contains(double px, double pz, double margin) {
            return px >= x - margin && px < x + sizeX + margin && pz >= z - margin && pz < z + sizeZ + margin;
        }
    }

    public GameInfo game;
    public final List<TeamInfo> teams = new ArrayList<>();
    /** The ids of the teams that won, once the game is over; null while it goes on. */
    public List<String> winners;
    public final Map<String, TeamInfo> teamsById = new HashMap<>();
    public final Map<String, UnitInfo> units = new LinkedHashMap<>();
    public final Map<String, PlayerInfo> players = new LinkedHashMap<>();
    public final Map<String, MobInfo> mobs = new LinkedHashMap<>();
    public final Map<Integer, BuildingInfo> buildings = new LinkedHashMap<>();
    public final List<TreeInfo> trees = new ArrayList<>();
    public final List<FeatureInfo> settlements = new ArrayList<>();
    public final List<FeatureInfo> mines = new ArrayList<>();
    /** Entity id to unit, player or mob. */
    public final Map<Integer, Thing> byEntityId = new HashMap<>();
    /** The viewer's UUID, from the server. */
    public String me;

    public void setGame(JsonElement element) {
        clearState();
        if (element == null || !element.isJsonObject()) {
            game = null;
            return;
        }
        JsonObject json = element.getAsJsonObject();
        GameInfo info = new GameInfo();
        info.number = integer(json, "number");
        info.minX = integer(json, "minX");
        info.minZ = integer(json, "minZ");
        info.maxX = integer(json, "maxX");
        info.maxZ = integer(json, "maxZ");
        info.seaLevel = integer(json, "seaLevel");
        info.unitsPerTeam = integer(json, "unitsPerTeam");
        info.buildingGap = integer(json, "buildingGap");
        for (Map.Entry<String, JsonElement> entry : object(json, "unitKinds").entrySet()) {
            JsonObject kind = entry.getValue()
                .getAsJsonObject();
            UnitKindInfo kindInfo = new UnitKindInfo();
            kindInfo.id = entry.getKey();
            kindInfo.name = string(kind, "name");
            kindInfo.cost = amounts(object(kind, "cost"));
            kindInfo.fights = bool(kind, "fights");
            kindInfo.hero = bool(kind, "hero");
            info.unitKinds.put(kindInfo.id, kindInfo);
        }
        for (Map.Entry<String, JsonElement> entry : object(json, "buildingTypes").entrySet()) {
            JsonObject type = entry.getValue()
                .getAsJsonObject();
            BuildingTypeInfo typeInfo = new BuildingTypeInfo();
            typeInfo.id = entry.getKey();
            typeInfo.name = string(type, "name");
            typeInfo.cost = amounts(object(type, "cost"));
            typeInfo.buildable = bool(type, "buildable");
            typeInfo.faction = type.has("faction") ? string(type, "faction") : null;
            typeInfo.age = type.has("age") ? integer(type, "age") : 1;
            typeInfo.health = integer(type, "health");
            typeInfo.workers = integer(type, "workers");
            for (JsonElement kind : array(type, "trains")) {
                typeInfo.trains.add(kind.getAsString());
            }
            typeInfo.levels = Math.max(1, integer(type, "levels"));
            for (Map.Entry<String, JsonElement> level : object(type, "levelUps").entrySet()) {
                JsonObject levelJson = level.getValue()
                    .getAsJsonObject();
                LevelInfo levelInfo = new LevelInfo();
                levelInfo.cost = amounts(object(levelJson, "cost"));
                levelInfo.seconds = integer(levelJson, "seconds");
                levelInfo.health = integer(levelJson, "health");
                try {
                    typeInfo.levelUps.put(Integer.parseInt(level.getKey()), levelInfo);
                } catch (NumberFormatException e) {
                    // Not a level; leave it out.
                }
            }
            List<Integer> population = new ArrayList<>();
            for (JsonElement count : array(type, "population")) {
                population.add(count.getAsInt());
            }
            typeInfo.population = new int[population.size()];
            for (int i = 0; i < population.size(); i++) {
                typeInfo.population[i] = population.get(i);
            }
            for (JsonElement upgrade : array(type, "researches")) {
                typeInfo.researches.add(upgrade.getAsString());
            }
            typeInfo.width = integer(type, "width");
            typeInfo.length = integer(type, "length");
            JsonArray heights = type.has("heights") ? type.getAsJsonArray("heights") : new JsonArray();
            typeInfo.heights = new int[heights.size()];
            for (int i = 0; i < heights.size(); i++) {
                typeInfo.heights[i] = heights.get(i)
                    .getAsInt();
            }
            info.buildingTypes.put(typeInfo.id, typeInfo);
        }
        for (Map.Entry<String, JsonElement> entry : object(json, "upgrades").entrySet()) {
            JsonObject upgrade = entry.getValue()
                .getAsJsonObject();
            UpgradeInfo upgradeInfo = new UpgradeInfo();
            upgradeInfo.id = entry.getKey();
            upgradeInfo.name = string(upgrade, "name");
            upgradeInfo.description = string(upgrade, "description");
            upgradeInfo.at = string(upgrade, "at");
            upgradeInfo.level = integer(upgrade, "level");
            upgradeInfo.requires = string(upgrade, "requires");
            upgradeInfo.cost = amounts(object(upgrade, "cost"));
            upgradeInfo.seconds = integer(upgrade, "seconds");
            info.upgrades.put(upgradeInfo.id, upgradeInfo);
        }
        game = info;
    }

    private void clearState() {
        teams.clear();
        teamsById.clear();
        units.clear();
        players.clear();
        mobs.clear();
        buildings.clear();
        trees.clear();
        settlements.clear();
        mines.clear();
        byEntityId.clear();
    }

    public void setState(JsonObject json) {
        clearState();
        winners = null;
        if (json.has("winners")) {
            winners = new ArrayList<>();
            for (JsonElement winner : array(json, "winners")) {
                winners.add(winner.getAsString());
            }
        }
        for (JsonElement element : array(json, "teams")) {
            JsonObject team = element.getAsJsonObject();
            TeamInfo info = new TeamInfo();
            info.id = string(team, "id");
            info.name = string(team, "name");
            try {
                info.color = Integer.parseInt(string(team, "color").substring(1), 16);
            } catch (RuntimeException e) {
                info.color = 0xcccccc;
            }
            info.resources.putAll(amounts(object(team, "resources")));
            for (JsonElement member : array(team, "members")) {
                info.members.add(string(member.getAsJsonObject(), "uuid"));
            }
            for (JsonElement ally : array(team, "allies")) {
                info.allies.add(ally.getAsString());
            }
            info.units = integer(team, "units");
            info.population = integer(team, "population");
            info.age = team.has("age") ? integer(team, "age") : 1;
            info.faction = string(team, "faction");
            for (JsonElement upgrade : array(team, "upgrades")) {
                info.upgrades.add(upgrade.getAsString());
            }
            info.defeated = team.has("defeated");
            info.countdown = team.has("countdown") ? integer(team, "countdown") : -1;
            teams.add(info);
            teamsById.put(info.id, info);
        }
        for (JsonElement element : array(json, "players")) {
            JsonObject player = element.getAsJsonObject();
            PlayerInfo info = new PlayerInfo();
            readThing(info, player);
            info.id = string(player, "uuid");
            info.name = string(player, "name");
            info.team = player.has("team") ? string(player, "team") : null;
            info.commanding = bool(player, "commanding");
            players.put(info.id, info);
            byEntityId.put(info.eid, info);
        }
        for (JsonElement element : array(json, "units")) {
            JsonObject unit = element.getAsJsonObject();
            UnitInfo info = new UnitInfo();
            readThing(info, unit);
            info.team = string(unit, "team");
            info.kind = string(unit, "kind");
            info.number = integer(unit, "number");
            info.order = string(unit, "order");
            if (unit.has("toX") && unit.has("toZ")) {
                info.toX = number(unit, "toX");
                info.toZ = number(unit, "toZ");
            }
            info.building = integer(unit, "building");
            info.from = integer(unit, "from");
            info.carrying.putAll(amounts(object(unit, "carrying")));
            info.delivering = bool(unit, "delivering");
            info.target = unit.has("target") ? string(unit, "target") : null;
            JsonElement tree = unit.get("tree");
            if (tree != null && tree.isJsonArray()
                && tree.getAsJsonArray()
                    .size() == 3) {
                info.tree = new int[3];
                for (int i = 0; i < 3; i++) {
                    info.tree[i] = tree.getAsJsonArray()
                        .get(i)
                        .getAsInt();
                }
            }
            info.chopping = bool(unit, "chopping");
            info.mining = bool(unit, "mining");
            info.carving = bool(unit, "carving");
            info.stance = unit.has("stance") ? unit.get("stance")
                .getAsString() : "defensive";
            info.prayer = unit.has("prayer") ? string(unit, "prayer") : "none";
            info.torches = unit.has("torches") ? unit.get("torches")
                .getAsInt() : 0;
            info.queued = unit.has("queued") ? integer(unit, "queued") : 0;
            units.put(info.id, info);
            byEntityId.put(info.eid, info);
        }
        for (JsonElement element : array(json, "mobs")) {
            JsonObject mob = element.getAsJsonObject();
            MobInfo info = new MobInfo();
            readThing(info, mob);
            info.name = string(mob, "name");
            info.hostile = bool(mob, "hostile");
            info.food = integer(mob, "food");
            info.carcass = bool(mob, "carcass");
            info.width = number(mob, "width");
            info.height = number(mob, "height");
            mobs.put(info.id, info);
            byEntityId.put(info.eid, info);
        }
        for (JsonElement element : array(json, "buildings")) {
            JsonObject building = element.getAsJsonObject();
            BuildingInfo info = new BuildingInfo();
            info.id = integer(building, "id");
            info.type = string(building, "type");
            info.team = string(building, "team");
            info.x = integer(building, "x");
            info.z = integer(building, "z");
            info.sizeX = integer(building, "sizeX");
            info.sizeZ = integer(building, "sizeZ");
            info.y = integer(building, "y");
            info.rotation = integer(building, "rotation");
            info.complete = bool(building, "complete");
            info.built = number(building, "built");
            info.builders = integer(building, "builders");
            info.health = integer(building, "health");
            info.maxHealth = integer(building, "maxHealth");
            info.range = integer(building, "range");
            if (building.has("rallyX") && building.has("rallyZ")) {
                info.rallyX = number(building, "rallyX");
                info.rallyZ = number(building, "rallyZ");
            }
            for (JsonElement kind : array(building, "queue")) {
                info.queue.add(kind.getAsString());
            }
            info.progress = number(building, "progress");
            info.workers = integer(building, "workers");
            info.level = Math.max(1, integer(building, "level"));
            info.research = building.has("research") ? string(building, "research") : null;
            info.researched = number(building, "researched");
            buildings.put(info.id, info);
        }
        for (JsonElement element : array(json, "trees")) {
            JsonObject tree = element.getAsJsonObject();
            TreeInfo info = new TreeInfo();
            info.x = integer(tree, "x");
            info.y = integer(tree, "y");
            info.z = integer(tree, "z");
            info.top = integer(tree, "top");
            info.wood = integer(tree, "wood");
            info.maxWood = integer(tree, "maxWood");
            trees.add(info);
        }
        for (JsonElement element : array(json, "settlements")) {
            settlements.add(feature(element.getAsJsonObject()));
        }
        for (JsonElement element : array(json, "mines")) {
            mines.add(feature(element.getAsJsonObject()));
        }
    }

    // ---------------------------------------------------------------- questions

    /** The viewer's team, or null. */
    public TeamInfo myTeam() {
        if (me == null) {
            return null;
        }
        for (TeamInfo team : teams) {
            if (team.members.contains(me)) {
                return team;
            }
        }
        return null;
    }

    public boolean isMine(UnitInfo unit) {
        TeamInfo team = myTeam();
        return team != null && team.id.equals(unit.team);
    }

    public boolean isEnemyTeam(String teamId) {
        TeamInfo team = myTeam();
        return team != null && teamId != null && !teamId.equals(team.id) && !team.allies.contains(teamId);
    }

    public BuildingTypeInfo buildingType(String id) {
        return game == null ? null : game.buildingTypes.get(id);
    }

    public UnitKindInfo unitKind(String id) {
        return game == null ? null : game.unitKinds.get(id);
    }

    private static FeatureInfo feature(JsonObject json) {
        FeatureInfo info = new FeatureInfo();
        info.id = integer(json, "id");
        info.x = integer(json, "x");
        info.z = integer(json, "z");
        info.sizeX = integer(json, "sizeX");
        info.sizeZ = integer(json, "sizeZ");
        info.y = integer(json, "y");
        info.rotation = integer(json, "rotation");
        info.free = bool(json, "free");
        info.gold = integer(json, "gold");
        info.maxGold = integer(json, "maxGold");
        return info;
    }

    /** The free settlement whose footprint holds x, z or comes within margin blocks of it, nearest first; or null. */
    public FeatureInfo freeSettlementNear(double x, double z, double margin) {
        FeatureInfo best = null;
        double bestDistance = Double.MAX_VALUE;
        for (FeatureInfo settlement : settlements) {
            if (!settlement.free || !settlement.contains(x, z, margin)) {
                continue;
            }
            double dx = settlement.x + settlement.sizeX / 2.0 - x;
            double dz = settlement.z + settlement.sizeZ / 2.0 - z;
            if (dx * dx + dz * dz < bestDistance) {
                best = settlement;
                bestDistance = dx * dx + dz * dz;
            }
        }
        return best;
    }

    /** The gold mine whose footprint holds x, z, or null. */
    public FeatureInfo mineAt(double x, double z) {
        for (FeatureInfo mine : mines) {
            if (mine.contains(x, z, 0)) {
                return mine;
            }
        }
        return null;
    }

    public BuildingInfo buildingAt(double x, double z) {
        for (BuildingInfo building : buildings.values()) {
            if (building.contains(x, z)) {
                return building;
            }
        }
        return null;
    }

    /** The tracked tree with its trunk's foot at x, y, z, or null. */
    public TreeInfo treeAt(int x, int y, int z) {
        for (TreeInfo tree : trees) {
            if (tree.x == x && tree.y == y && tree.z == z) {
                return tree;
            }
        }
        return null;
    }

    public UpgradeInfo upgrade(String id) {
        return game == null ? null : game.upgrades.get(id);
    }

    /** Whether one of the team's buildings researches the upgrade now. */
    public boolean isResearching(TeamInfo team, String upgrade) {
        for (BuildingInfo building : buildings.values()) {
            if (building.team.equals(team.id) && upgrade.equals(building.research)) {
                return true;
            }
        }
        return false;
    }

    public BuildingInfo townHallOf(String teamId) {
        for (BuildingInfo building : buildings.values()) {
            if (building.team.equals(teamId) && "town_hall".equals(building.type)) {
                return building;
            }
        }
        return null;
    }

    /** Units the team has, plus the ones waiting in training queues. */
    /** Whether the team has a unit of the kind alive or in training, as a hero it can have only one of. */
    public boolean hasUnitOf(TeamInfo team, String kind) {
        for (UnitInfo unit : units.values()) {
            if (team.id.equals(unit.team) && kind.equals(unit.kind)) {
                return true;
            }
        }
        for (BuildingInfo building : buildings.values()) {
            if (building.team.equals(team.id) && building.queue.contains(kind)) {
                return true;
            }
        }
        return false;
    }

    public int teamUnitCount(TeamInfo team) {
        int queued = 0;
        for (BuildingInfo building : buildings.values()) {
            if (building.team.equals(team.id)) {
                queued += building.queue.size();
            }
        }
        return team.units + queued;
    }

    public static boolean canAfford(TeamInfo team, Map<String, Long> cost) {
        for (Map.Entry<String, Long> entry : cost.entrySet()) {
            Long has = team.resources.get(entry.getKey());
            if (has == null || has < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public static String costText(Map<String, Long> cost) {
        if (cost.isEmpty()) {
            return "free";
        }
        return amountsText(cost);
    }

    public static String amountsText(Map<String, Long> amounts) {
        StringBuilder text = new StringBuilder();
        for (String resource : RESOURCES) {
            Long amount = amounts.get(resource);
            if (amount != null && amount > 0) {
                if (text.length() > 0) {
                    text.append(", ");
                }
                text.append(amount)
                    .append(' ')
                    .append(resource);
            }
        }
        return text.toString();
    }

    /**
     * Column heights of a building type turned a number of quarter turns clockwise, the way the server turns
     * structures, as {sizeX, sizeZ, heights...} with heights at 2 + x + z * sizeX.
     */
    public static int[] turnedShape(BuildingTypeInfo type, int rotation) {
        int w = type.width;
        int l = type.length;
        int[] heights = type.heights.length == w * l ? type.heights : new int[w * l];
        for (int r = 0; r < (rotation & 3); r++) {
            int[] turned = new int[w * l];
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    turned[(l - 1 - z) + x * l] = heights[x + z * w];
                }
            }
            heights = turned;
            int swap = w;
            w = l;
            l = swap;
        }
        int[] shape = new int[2 + heights.length];
        shape[0] = w;
        shape[1] = l;
        System.arraycopy(heights, 0, shape, 2, heights.length);
        return shape;
    }

    public static int tallest(int[] shape) {
        int top = 0;
        for (int i = 2; i < shape.length; i++) {
            top = Math.max(top, shape[i]);
        }
        return top;
    }

    // ---------------------------------------------------------------- reading JSON

    private static void readThing(Thing thing, JsonObject json) {
        thing.id = string(json, "id");
        thing.eid = json.has("eid") ? integer(json, "eid") : -1;
        thing.x = number(json, "x");
        thing.y = number(json, "y");
        thing.z = number(json, "z");
        thing.health = number(json, "health");
        thing.maxHealth = json.has("maxHealth") ? number(json, "maxHealth") : 20;
    }

    private static Map<String, Long> amounts(JsonObject json) {
        Map<String, Long> amounts = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            try {
                amounts.put(
                    entry.getKey(),
                    entry.getValue()
                        .getAsLong());
            } catch (RuntimeException e) {
                // Not a number; leave it out.
            }
        }
        return amounts;
    }

    private static JsonObject object(JsonObject json, String name) {
        JsonElement element = json.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static Iterable<JsonElement> array(JsonObject json, String name) {
        JsonElement element = json.get(name);
        return element != null && element.isJsonArray() ? element.getAsJsonArray()
            : Collections.<JsonElement>emptyList();
    }

    private static String string(JsonObject json, String name) {
        JsonElement element = json.get(name);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static int integer(JsonObject json, String name) {
        JsonElement element = json.get(name);
        try {
            return element == null || element.isJsonNull() ? 0 : element.getAsInt();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static double number(JsonObject json, String name) {
        JsonElement element = json.get(name);
        try {
            return element == null || element.isJsonNull() ? 0 : element.getAsDouble();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static boolean bool(JsonObject json, String name) {
        JsonElement element = json.get(name);
        try {
            return element != null && !element.isJsonNull() && element.getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
