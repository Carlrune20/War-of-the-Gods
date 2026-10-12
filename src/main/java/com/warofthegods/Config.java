package com.warofthegods;

import java.io.File;
import java.util.EnumMap;

import net.minecraftforge.common.config.Configuration;

import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Resource;

public class Config {

    public static int defaultMapSize = 1000;
    public static int defaultTeamCount = 2;
    public static boolean allowSelfJoin = true;
    public static int gameDimensionId = 77;
    public static int mapMargin = 48;
    public static final long[] startingResources = { 200, 200, 100, 0 };

    /** Food, Wood, Stone and Metal one unit costs to train from the web map. */
    public static final long[] unitCost = { 50, 20, 0, 0 };
    public static int unitsPerTeam = 50;
    public static int unitHealth = 20;
    /** Food, Wood, Stone and Metal one worker costs to train at the town center. */
    public static final long[] workerCost = { 50, 0, 0, 0 };
    /** Food, Wood, Stone and Metal one archer costs to train at a barracks. */
    public static final long[] archerCost = { 40, 40, 0, 0 };
    public static int trainSeconds = 5;

    /** Food, Wood, Stone and Metal each kind of building costs. */
    public static final EnumMap<BuildingType, long[]> buildingCost = new EnumMap<>(BuildingType.class);
    public static final EnumMap<BuildingType, Integer> buildingHealth = new EnumMap<>(BuildingType.class);
    /** Seconds one worker takes to build each kind of building. */
    public static final EnumMap<BuildingType, Integer> buildSeconds = new EnumMap<>(BuildingType.class);

    static {
        defaults(BuildingType.TOWN_HALL, new long[4], 1500, 0);
        defaults(BuildingType.FARM, new long[] { 0, 75, 0, 0 }, 300, 15);
        defaults(BuildingType.BARRACKS, new long[] { 0, 150, 50, 0 }, 800, 25);
        defaults(BuildingType.STOREHOUSE, new long[] { 0, 100, 0, 0 }, 600, 20);
        defaults(BuildingType.QUARRY, new long[] { 0, 100, 0, 0 }, 500, 20);
        defaults(BuildingType.HOUSE, new long[] { 0, 60, 0, 0 }, 300, 12);
        defaults(BuildingType.ARMORY, new long[] { 0, 120, 60, 0 }, 700, 25);
        defaults(BuildingType.TOWER, new long[] { 0, 50, 100, 0 }, 900, 25);
    }

    /** Food, Wood, Stone and Metal the town center costs to go up to level 2, and to level 3. */
    public static final long[] townCenterLevel2Cost = { 200, 200, 150, 0 };
    public static final long[] townCenterLevel3Cost = { 400, 300, 300, 100 };
    /** Seconds going up from level 1 to 2 takes; from 2 to 3 takes twice as long. */
    public static int upgradeSeconds = 30;
    public static int townCenterPopulation = 10;
    public static int housePopulation = 5;
    public static int towerRange = 16;
    public static int towerDamage = 3;
    public static int towerShotTicks = 30;
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

    public static int farmWorkers = 3;
    public static int farmYieldSeconds = 5;
    public static int farmFood = 5;
    public static int quarryWorkers = 3;
    public static int quarryStone = 4;
    public static int quarryMetal = 1;
    /** Most a worker carries before taking it to the town center or a storehouse. */
    public static int carryCapacity = 20;
    /** Health a building loses to one hit of a warrior. */
    public static int unitBuildingDamage = 10;
    /** Health a building loses when an enemy player breaks one of its blocks. */
    public static int blockBreakDamage = 5;

    public static boolean webEnabled = true;
    public static String webBindAddress = "0.0.0.0";
    public static int webPort = 8124;
    public static String webPublicUrl = "";
    public static int webLinkMinutes = 10;
    public static int webSessionHours = 72;
    public static int webRenderBudgetMs = 10;
    public static int webStateTicks = 1;

    public static int rtsStateTicks = 2;
    public static int rtsCameraFov = 40;
    public static boolean rtsEdgeScroll = true;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

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
        trainSeconds = configuration
            .getInt("trainSeconds", units, trainSeconds, 0, 3600, "Seconds it takes to train one unit.");

        String buildings = "buildings";
        configuration.setCategoryComment(
            buildings,
            "Buildings: the town center every team starts with, and the farms, barracks, storehouses, quarries,"
                + " houses, armories and towers workers build.");
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
            "Blocks a tower shoots at enemies; level 2 towers and town centers from level 2 shoot 4 farther.");
        towerDamage = configuration
            .getInt("towerDamage", buildings, towerDamage, 0, 1000, "Damage of a tower's arrow, before upgrades.");
        towerShotTicks = configuration.getInt(
            "towerShotTicks",
            buildings,
            towerShotTicks,
            5,
            1200,
            "Ticks between a tower's shots; level 2 shoots a third faster.");
        woodPerLog = configuration
            .getInt("woodPerLog", buildings, woodPerLog, 1, 10000, "Wood each log of a tree is worth.");
        treeWoodMin = configuration
            .getInt("treeWoodMin", buildings, treeWoodMin, 1, 100000, "Least wood a tree holds.");
        treeWoodMax = configuration.getInt("treeWoodMax", buildings, treeWoodMax, 1, 100000, "Most wood a tree holds.");
        chopWood = configuration
            .getInt("chopWood", buildings, chopWood, 1, 10000, "Wood a worker chops off a tree at a time.");
        chopSeconds = configuration
            .getInt("chopSeconds", buildings, chopSeconds, 1, 3600, "Seconds a worker takes to chop that much.");
        quarryWorkers = configuration.getInt(
            "quarryWorkers",
            buildings,
            quarryWorkers,
            1,
            64,
            "Most workers that can work one quarry at a time.");
        quarryStone = configuration.getInt(
            "quarryStone",
            buildings,
            quarryStone,
            0,
            100000,
            "Stone each worker at a quarry gathers per harvest; harvests come as often as a farm's.");
        quarryMetal = configuration.getInt(
            "quarryMetal",
            buildings,
            quarryMetal,
            0,
            100000,
            "Metal each worker at a quarry gathers per harvest.");
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
}
