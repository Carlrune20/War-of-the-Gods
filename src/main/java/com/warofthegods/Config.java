package com.warofthegods;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Resource;

public class Config {

    public static int defaultMapSize = 1000;
    public static int defaultTeamCount = 2;
    public static boolean allowSelfJoin = true;
    public static int gameDimensionId = 77;
    public static int mapMargin = 48;
    public static final long[] startingResources = { 200, 200, 100 };

    /** Food, Wood and Gold one unit costs to train from the web map. */
    public static final long[] unitCost = { 50, 20, 0 };
    public static int unitsPerTeam = 100;
    public static int unitHealth = 20;
    /** Food, Wood and Gold one worker costs to train at the town center. */
    public static final long[] workerCost = { 50, 0, 0 };
    /** Food, Wood and Gold one archer costs to train at a barracks. */
    public static final long[] archerCost = { 40, 40, 0 };
    public static int trainSeconds = 5;

    /** Food, Wood and Gold each kind of building costs. */
    public static final EnumMap<BuildingType, long[]> buildingCost = new EnumMap<>(BuildingType.class);
    public static final EnumMap<BuildingType, Integer> buildingHealth = new EnumMap<>(BuildingType.class);
    /** Seconds one worker takes to build each kind of building. */
    public static final EnumMap<BuildingType, Integer> buildSeconds = new EnumMap<>(BuildingType.class);

    static {
        defaults(BuildingType.TOWN_HALL, new long[] { 0, 300, 200 }, 1500, 60);
        defaults(BuildingType.FARM, new long[] { 0, 75, 0 }, 300, 15);
        defaults(BuildingType.BARRACKS, new long[] { 0, 150, 50 }, 800, 25);
        defaults(BuildingType.STOREHOUSE, new long[] { 0, 100, 0 }, 600, 20);
        defaults(BuildingType.HOUSE, new long[] { 0, 60, 0 }, 300, 12);
        defaults(BuildingType.ARMORY, new long[] { 0, 120, 60 }, 700, 25);
        defaults(BuildingType.TOWER, new long[] { 0, 50, 100 }, 900, 25);
    }

    /** Food, Wood and Gold the town center costs to go up to level 2, and to level 3. */
    public static final long[] townCenterLevel2Cost = { 200, 200, 150 };
    public static final long[] townCenterLevel3Cost = { 400, 300, 400 };
    /** Seconds going up from level 1 to 2 takes; from 2 to 3 takes twice as long. */
    public static int upgradeSeconds = 30;
    public static int townCenterPopulation = 10;
    public static int housePopulation = 5;
    public static int towerRange = 16;
    public static int towerDamage = 6;
    /** Damage of an archer's arrow before upgrades; a crit adds up to half again, as for a player's full draw. */
    public static int archerDamage = 6;
    public static int towerShotTicks = 20;
    /** Blocks guard towers see and shoot farther than watch towers. */
    public static int guardTowerRange = 8;
    /** Wood one log of a tree holds; a tree holds between treeWoodMin and treeWoodMax in all. */
    public static int woodPerLog = 25;
    public static int treeWoodMin = 100;
    public static int treeWoodMax = 200;
    /** Wood a worker chops per chopSeconds. */
    public static int chopWood = 4;
    public static int chopSeconds = 3;

    private static void defaults(BuildingType type, long[] cost, int health, int seconds) {
        buildingCost.put(type, cost);
        buildingHealth.put(type, health);
        buildSeconds.put(type, seconds);
    }

    public static int farmWorkers = 1;
    public static int farmYieldSeconds = 5;
    public static int farmFood = 5;
    /** Gold a gold mine holds when the game starts. */
    public static int goldMineGold = 2000;
    /** Gold a worker mines per mineSeconds. */
    public static int mineGold = 4;
    public static int mineSeconds = 3;
    /**
     * Settlements no team starts on, and gold mines away from any settlement, laid out on each new map for each team,
     * the same way round every team's town center.
     */
    public static int extraSettlements = 1;
    public static int extraGoldMines = 1;
    /** Animals that graze near each team's first town center: a flock of chickens and a herd of pigs, cows or sheep. */
    public static int startingChickens = 6;
    public static int startingLivestock = 4;
    /** Most a worker carries before taking it to the town center or a storehouse. */
    public static int carryCapacity = 20;
    /** Health a building loses to one hit of a warrior. */
    public static int unitBuildingDamage = 10;
    /** Health a building loses when an enemy player breaks one of its blocks. */
    public static int blockBreakDamage = 5;
    /** Health a building loses to a creeper going off right next to it; less the further away it goes off. */
    public static int explosionBuildingDamage = 120;
    /** Blocks around a building that explosions leave standing. */
    public static int explosionProtection = 2;
    /** Wood each torch a worker puts up costs. */
    public static int torchWood = 1;
    /** Food a cow, pig, sheep or chicken leaves for workers to carve off once killed. */
    public static int cowFood = 300;
    public static int pigFood = 250;
    public static int sheepFood = 200;
    public static int chickenFood = 100;
    /** Food a worker carves off a carcass per huntSeconds. */
    public static int huntFood = 6;
    public static int huntSeconds = 3;

    public static boolean webEnabled = true;
    public static String webBindAddress = "0.0.0.0";
    public static int webPort = 8124;
    public static String webPublicUrl = "";
    public static int webLinkMinutes = 10;
    public static int webSessionHours = 72;
    public static int webRenderBudgetMs = 10;
    public static int webStateTicks = 1;

    public static boolean autoAssignTeams = true;
    public static boolean rain = false;
    public static int startTime = 0;
    /** Seconds a team that lost its last town center, but still has workers, has to raise a new one. */
    public static int defeatCountdownSeconds = 120;

    /** Seconds between tries at spawning a group of zombies or skeletons somewhere in the wilderness. */
    public static int mobSpawnSeconds = 45;
    /** Most zombies and skeletons on the whole map at once. */
    public static int maxMobs = 16;
    /** Fewest and most zombies or skeletons in a group. */
    public static int mobGroupMin = 2;
    public static int mobGroupMax = 3;
    /** Chance a spawn spot out in the open is used; spots among trees always are. */
    public static double openGroundChance = 0.25;
    /** Blocks a zombie or skeleton notices enemies from, and how far it chases one once it has. */
    public static int mobDetectRange = 10;
    public static int mobChaseRange = 32;
    /** No hostile mob spawns this close to a town center. */
    public static int townSafeRadius = 40;
    /** Out to this far from a town center, fewer hostile mobs spawn the closer they are; past it is wilderness. */
    public static int townRadius = 96;
    public static double mobArmorChance = 0.45;
    public static double skeletonSwordChance = 0.3;

    public static int rtsStateTicks = 2;
    public static int rtsCameraFov = 40;
    public static boolean rtsEdgeScroll = true;

    /** Bumped when defaults change in a way that should replace what older config files hold. */
    private static final int CONFIG_VERSION = 4;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);
        Property version = configuration.get(
            Configuration.CATEGORY_GENERAL,
            "configVersion",
            0,
            "Which defaults this file was written with. Leave it alone.");
        int oldVersion = version.getInt();
        if (oldVersion < 2) {
            // These defaults changed; the old values make way for the new ones, and settings that are gone are
            // dropped.
            forget(configuration, "units", "perTeam");
            forget(configuration, "buildings", "farmWorkers");
            forget(configuration, "buildings", "towerDamage");
            forget(configuration, "mobs", "groupExtra");
            forget(configuration, "mobs", "creeperChance");
            forget(configuration, "mobs", "endermen");
        }
        if (oldVersion < 3) {
            // Stone and Metal became Gold; their settings go, and costs come back as Gold.
            for (String category : new String[] { "startingResources", "units", "buildings" }) {
                forgetStoneAndMetal(configuration, category);
            }
        }
        if (oldVersion < 4) {
            // Watch towers shoot as often as archers.
            forget(configuration, "buildings", "towerShotTicks");
        }
        version.set(CONFIG_VERSION);

        defaultMapSize = configuration.getInt(
            "defaultMapSize",
            Configuration.CATEGORY_GENERAL,
            defaultMapSize,
            64,
            100000,
            "Side length in blocks of the square map a new game is played on.");
        defaultTeamCount = configuration.getInt(
            "defaultTeamCount",
            Configuration.CATEGORY_GENERAL,
            defaultTeamCount,
            0,
            16,
            "Teams created by /rts newgame when no team count is given.");
        allowSelfJoin = configuration.getBoolean(
            "allowSelfJoin",
            Configuration.CATEGORY_GENERAL,
            allowSelfJoin,
            "Whether players who are not ops may join and leave teams themselves.");

        gameDimensionId = configuration.getInt(
            "gameDimensionId",
            Configuration.CATEGORY_GENERAL,
            gameDimensionId,
            2,
            100000,
            "Dimension id of the world games are played in. Change it if another mod uses the same id.");
        mapMargin = configuration.getInt(
            "mapMargin",
            Configuration.CATEGORY_GENERAL,
            mapMargin,
            0,
            512,
            "Blocks of terrain generated beyond the map edge, sloping down into the sea around the map.");

        for (Resource resource : Resource.values()) {
            startingResources[resource.ordinal()] = configuration.getInt(
                resource.id,
                "startingResources",
                (int) startingResources[resource.ordinal()],
                0,
                Integer.MAX_VALUE,
                "Amount of " + resource.displayName + " each team starts a game with.");
        }

        String units = "units";
        configuration.setCategoryComment(
            units,
            "Units teams train and command: warriors and archers at a barracks, workers at the town center.");
        for (Resource resource : Resource.values()) {
            unitCost[resource.ordinal()] = configuration.getInt(
                "cost" + resource.displayName,
                units,
                (int) unitCost[resource.ordinal()],
                0,
                Integer.MAX_VALUE,
                resource.displayName + " a warrior costs to train.");
            workerCost[resource.ordinal()] = configuration.getInt(
                "workerCost" + resource.displayName,
                units,
                (int) workerCost[resource.ordinal()],
                0,
                Integer.MAX_VALUE,
                resource.displayName + " a worker costs to train.");
            archerCost[resource.ordinal()] = configuration.getInt(
                "archerCost" + resource.displayName,
                units,
                (int) archerCost[resource.ordinal()],
                0,
                Integer.MAX_VALUE,
                resource.displayName + " an archer costs to train.");
        }
        unitsPerTeam = configuration.getInt(
            "perTeam",
            units,
            unitsPerTeam,
            0,
            1000,
            "Most units a team can have at once, however many houses it builds.");
        unitHealth = configuration.getInt("health", units, unitHealth, 1, 1000, "Health of a unit; 20 is a player's.");
        archerDamage = configuration.getInt(
            "archerDamage",
            units,
            archerDamage,
            0,
            1000,
            "Damage of an archer's arrow, before upgrades; a crit adds up to half again, as for a player's full draw.");
        trainSeconds = configuration
            .getInt("trainSeconds", units, trainSeconds, 0, 3600, "Seconds it takes to train one unit.");

        String buildings = "buildings";
        configuration.setCategoryComment(
            buildings,
            "Buildings: the town center every team starts with, the farms, barracks, storehouses, houses, armories,"
                + " towers and town centers workers build, and the settlements and gold mines on the map.");
        for (BuildingType type : BuildingType.values()) {
            String name = type.displayName.toLowerCase();
            if (type.buildable) {
                long[] cost = buildingCost.get(type);
                for (Resource resource : Resource.values()) {
                    cost[resource.ordinal()] = configuration.getInt(
                        type.configName + "Cost" + resource.displayName,
                        buildings,
                        (int) cost[resource.ordinal()],
                        0,
                        Integer.MAX_VALUE,
                        resource.displayName + " a " + name + " costs.");
                }
                buildSeconds.put(
                    type,
                    configuration.getInt(
                        type.configName + "BuildSeconds",
                        buildings,
                        buildSeconds.get(type),
                        0,
                        3600,
                        "Seconds one worker takes to build a " + name + "; more workers build faster."));
            }
            buildingHealth.put(
                type,
                configuration.getInt(
                    type.configName + "Health",
                    buildings,
                    buildingHealth.get(type),
                    1,
                    1000000,
                    "Health of a " + name + ". It is gone when enemies bring it to 0."));
        }
        farmWorkers = configuration
            .getInt("farmWorkers", buildings, farmWorkers, 1, 64, "Most workers that can work one farm at a time.");
        farmYieldSeconds = configuration
            .getInt("farmYieldSeconds", buildings, farmYieldSeconds, 1, 3600, "Seconds between a farm's harvests.");
        farmFood = configuration
            .getInt("farmFood", buildings, farmFood, 0, 100000, "Food each worker at a farm gathers per harvest.");
        townCenterPopulation = configuration.getInt(
            "townCenterPopulation",
            buildings,
            townCenterPopulation,
            0,
            1000,
            "Units a town center houses; each level above the first houses half as many again.");
        housePopulation = configuration.getInt(
            "housePopulation",
            buildings,
            housePopulation,
            0,
            1000,
            "Units a house houses; its second level houses half as many again.");
        upgradeSeconds = configuration.getInt(
            "upgradeSeconds",
            buildings,
            upgradeSeconds,
            0,
            3600,
            "Seconds a building takes to go up to level 2; level 3 takes twice as long.");
        for (Resource resource : Resource.values()) {
            townCenterLevel2Cost[resource.ordinal()] = configuration.getInt(
                "townCenterLevel2Cost" + resource.displayName,
                buildings,
                (int) townCenterLevel2Cost[resource.ordinal()],
                0,
                Integer.MAX_VALUE,
                resource.displayName + " the town center costs to go up to level 2.");
            townCenterLevel3Cost[resource.ordinal()] = configuration.getInt(
                "townCenterLevel3Cost" + resource.displayName,
                buildings,
                (int) townCenterLevel3Cost[resource.ordinal()],
                0,
                Integer.MAX_VALUE,
                resource.displayName + " the town center costs to go up to level 3.");
        }
        towerRange = configuration.getInt(
            "towerRange",
            buildings,
            towerRange,
            1,
            64,
            "Blocks a watch tower, or a town center from Age II, sees and shoots enemies at.");
        towerDamage = configuration.getInt(
            "towerDamage",
            buildings,
            towerDamage,
            0,
            1000,
            "Damage of a tower's arrow, before upgrades. Arrows fly as a player's fully drawn bow shoots them, and a"
                + " crit adds up to half again.");
        towerShotTicks = configuration.getInt(
            "towerShotTicks",
            buildings,
            towerShotTicks,
            5,
            1200,
            "Ticks between the shots of a watch tower or a town center from Age II.");
        guardTowerRange = configuration.getInt(
            "guardTowerRange",
            buildings,
            guardTowerRange,
            0,
            64,
            "Blocks guard towers see and shoot farther than watch towers.");
        woodPerLog = configuration
            .getInt("woodPerLog", buildings, woodPerLog, 1, 10000, "Wood each log of a tree is worth.");
        treeWoodMin = configuration
            .getInt("treeWoodMin", buildings, treeWoodMin, 1, 100000, "Least wood a tree holds.");
        treeWoodMax = configuration.getInt("treeWoodMax", buildings, treeWoodMax, 1, 100000, "Most wood a tree holds.");
        chopWood = configuration
            .getInt("chopWood", buildings, chopWood, 1, 10000, "Wood a worker chops off a tree at a time.");
        chopSeconds = configuration
            .getInt("chopSeconds", buildings, chopSeconds, 1, 3600, "Seconds a worker takes to chop that much.");
        goldMineGold = configuration.getInt(
            "goldMineGold",
            buildings,
            goldMineGold,
            1,
            1000000,
            "Gold each gold mine holds when a game starts.");
        mineGold = configuration.getInt(
            "mineGold",
            buildings,
            mineGold,
            1,
            100000,
            "Gold a worker mines from a gold mine per mineSeconds.");
        mineSeconds = configuration
            .getInt("mineSeconds", buildings, mineSeconds, 1, 3600, "Seconds a worker takes to mine mineGold gold.");
        extraSettlements = configuration.getInt(
            "extraSettlements",
            buildings,
            extraSettlements,
            0,
            32,
            "Free settlements laid out on each new map for each team, besides the ones the teams start on; workers can"
                + " raise a town center on any free settlement.");
        extraGoldMines = configuration.getInt(
            "extraGoldMines",
            buildings,
            extraGoldMines,
            0,
            64,
            "Gold mines laid out on each new map for each team, besides the one by each settlement.");
        startingChickens = configuration.getInt(
            "startingChickens",
            buildings,
            startingChickens,
            0,
            32,
            "Chickens that graze near each team's first town center on a new map.");
        startingLivestock = configuration.getInt(
            "startingLivestock",
            buildings,
            startingLivestock,
            0,
            32,
            "Pigs, cows or sheep (the same for every team) that graze near each team's first town center on a new map.");
        carryCapacity = configuration.getInt(
            "carryCapacity",
            buildings,
            carryCapacity,
            1,
            100000,
            "Resources a worker carries before taking them to the town center or a storehouse.");
        unitBuildingDamage = configuration.getInt(
            "unitDamage",
            buildings,
            unitBuildingDamage,
            0,
            100000,
            "Health a building loses to one hit of an enemy warrior; an archer's arrow does half.");
        blockBreakDamage = configuration.getInt(
            "blockBreakDamage",
            buildings,
            blockBreakDamage,
            0,
            100000,
            "Health a building loses when an enemy player breaks one of its blocks. The block comes back.");

        explosionBuildingDamage = configuration.getInt(
            "explosionDamage",
            buildings,
            explosionBuildingDamage,
            0,
            100000,
            "Health a building loses to a creeper exploding right next to it; less the further off it goes off.");
        explosionProtection = configuration.getInt(
            "explosionProtection",
            buildings,
            explosionProtection,
            0,
            16,
            "Blocks around a building where explosions break nothing; the building only loses health.");
        torchWood = configuration
            .getInt("torchWood", buildings, torchWood, 0, 100000, "Wood each torch a worker puts up costs.");

        String hunting = "hunting";
        configuration.setCategoryComment(
            hunting,
            "Workers hunt cows, pigs, sheep and chickens, then carve food off the carcass and carry it home.");
        cowFood = configuration.getInt("cowFood", hunting, cowFood, 0, 100000, "Food a cow leaves.");
        pigFood = configuration.getInt("pigFood", hunting, pigFood, 0, 100000, "Food a pig leaves.");
        sheepFood = configuration.getInt("sheepFood", hunting, sheepFood, 0, 100000, "Food a sheep leaves.");
        chickenFood = configuration.getInt("chickenFood", hunting, chickenFood, 0, 100000, "Food a chicken leaves.");
        huntFood = configuration
            .getInt("carveFood", hunting, huntFood, 1, 10000, "Food a worker carves off a carcass at a time.");
        huntSeconds = configuration
            .getInt("carveSeconds", hunting, huntSeconds, 1, 3600, "Seconds a worker takes to carve that much.");

        String games = "games";
        configuration.setCategoryComment(games, "What happens when a new game starts.");
        autoAssignTeams = configuration.getBoolean(
            "autoAssignTeams",
            games,
            autoAssignTeams,
            "Whether players are put on the smallest team when a game starts, and when they join without a team.");
        rain = configuration.getBoolean("rain", games, rain, "Whether it ever rains on the game map.");
        startTime = configuration.getInt(
            "startTime",
            games,
            startTime,
            0,
            23999,
            "Time of day each new game starts at, in ticks: 0 is sunrise, 6000 noon, 13000 nightfall.");
        defeatCountdownSeconds = configuration.getInt(
            "defeatCountdownSeconds",
            games,
            defeatCountdownSeconds,
            10,
            3600,
            "Seconds a team that lost its last town center has to raise a new one before it is defeated. A team with"
                + " no town center and no workers is defeated at once.");

        String mobs = "mobs";
        configuration.setCategoryComment(mobs, "Hostile mobs on the game map.");
        townSafeRadius = configuration.getInt(
            "townSafeRadius",
            mobs,
            townSafeRadius,
            0,
            1000,
            "No zombies or skeletons spawn this close to a town center.");
        townRadius = configuration.getInt(
            "townRadius",
            mobs,
            townRadius,
            0,
            2000,
            "Between townSafeRadius and this far from a town center, fewer zombies and skeletons spawn the closer"
                + " it is. Further out is wilderness.");
        mobSpawnSeconds = configuration.getInt(
            "spawnSeconds",
            mobs,
            mobSpawnSeconds,
            1,
            3600,
            "Seconds between tries at spawning a group of zombies or skeletons in the wilderness, day or night."
                + " Nothing else hostile spawns.");
        maxMobs = configuration
            .getInt("maxMobs", mobs, maxMobs, 0, 1000, "Most zombies and skeletons on the whole map at once.");
        mobGroupMin = configuration
            .getInt("groupMin", mobs, mobGroupMin, 1, 16, "Fewest zombies or skeletons in a group.");
        mobGroupMax = configuration
            .getInt("groupMax", mobs, mobGroupMax, 1, 16, "Most zombies or skeletons in a group.");
        openGroundChance = configuration
            .get(
                mobs,
                "openGroundChance",
                openGroundChance,
                "Chance a group may spawn out in the open, 0 to 1; among trees they always may.")
            .getDouble();
        mobDetectRange = configuration.getInt(
            "detectRange",
            mobs,
            mobDetectRange,
            1,
            128,
            "Blocks a zombie or skeleton notices players and units from.");
        mobChaseRange = configuration.getInt(
            "chaseRange",
            mobs,
            mobChaseRange,
            1,
            128,
            "Blocks a zombie or skeleton follows an enemy once it has noticed one.");
        mobArmorChance = configuration
            .get(mobs, "armorChance", mobArmorChance, "Chance a zombie or skeleton spawns wearing armor, 0 to 1.")
            .getDouble();
        skeletonSwordChance = configuration
            .get(
                mobs,
                "skeletonSwordChance",
                skeletonSwordChance,
                "Chance a skeleton carries a sword instead of a bow.")
            .getDouble();

        String web = "web";
        configuration.setCategoryComment(web, "The web map players open in a browser with a link from /rts web.");
        webEnabled = configuration.getBoolean("enabled", web, webEnabled, "Whether to run the web map server.");
        webBindAddress = configuration.getString(
            "bindAddress",
            web,
            webBindAddress,
            "Address the web server listens on. 0.0.0.0 listens on every network interface.");
        webPort = configuration
            .getInt("port", web, webPort, 1, 65535, "TCP port of the web server. Open or publish it next to 25565.");
        webPublicUrl = configuration.getString(
            "publicUrl",
            web,
            webPublicUrl,
            "Address players' browsers reach the web server on, used in /rts web links, such as"
                + " http://play.example.com:8124. Empty means http://localhost:<port>, which only works on the"
                + " server machine itself.");
        webLinkMinutes = configuration.getInt(
            "linkMinutes",
            web,
            webLinkMinutes,
            1,
            1440,
            "Minutes a /rts web link stays valid. Links work once.");
        webSessionHours = configuration.getInt(
            "sessionHours",
            web,
            webSessionHours,
            1,
            8760,
            "Hours a browser stays logged in after opening a link. Restarting the server logs everyone out.");
        webRenderBudgetMs = configuration.getInt(
            "renderBudgetMs",
            web,
            webRenderBudgetMs,
            1,
            40,
            "Milliseconds per server tick spent drawing the map. A new map fills in faster with more, at some cost"
                + " to tick rate.");

        webStateTicks = configuration.getInt(
            "stateTicks",
            web,
            webStateTicks,
            1,
            100,
            "Server ticks between live updates of units, players and buildings sent to browsers. 1 is every tick,"
                + " 20 times a second, the fastest the game itself changes.");

        String rts = "rts";
        configuration.addCustomCategoryComment(
            rts,
            "The in-game RTS view, which players open with V (rebindable under Controls). The camera settings only"
                + " matter in a player's own client config.");
        rtsStateTicks = configuration.getInt(
            "stateTicks",
            rts,
            rtsStateTicks,
            1,
            100,
            "Server ticks between live updates of units, players and buildings sent to players in the RTS view.");
        rtsCameraFov = configuration.getInt(
            "cameraFov",
            rts,
            rtsCameraFov,
            20,
            90,
            "Field of view of the RTS camera, in degrees. Lower looks flatter, more like the web map.");
        rtsEdgeScroll = configuration.getBoolean(
            "edgeScroll",
            rts,
            rtsEdgeScroll,
            "Whether the RTS camera moves when the mouse touches the edge of the window.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    private static void forgetStoneAndMetal(Configuration configuration, String category) {
        if (!configuration.hasCategory(category)) {
            return;
        }
        ConfigCategory settings = configuration.getCategory(category);
        for (String key : new ArrayList<>(settings.keySet())) {
            String name = key.toLowerCase();
            if (name.endsWith("stone") || name.endsWith("metal")) {
                settings.remove(key);
            }
        }
    }

    private static void forget(Configuration configuration, String category, String key) {
        if (configuration.hasCategory(category)) {
            configuration.getCategory(category)
                .remove(key);
        }
    }
}
