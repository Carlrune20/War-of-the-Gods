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
    /** Food, Wood, Stone and Metal one worker costs to train at the town hall. */
    public static final long[] workerCost = { 50, 0, 0, 0 };
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
    }

    private static void defaults(BuildingType type, long[] cost, int health, int seconds) {
        buildingCost.put(type, cost);
        buildingHealth.put(type, health);
        buildSeconds.put(type, seconds);
    }

    public static int farmWorkers = 3;
    public static int farmYieldSeconds = 5;
    public static int farmFood = 5;
    public static int farmWood = 3;
    public static int quarryWorkers = 3;
    public static int quarryStone = 4;
    public static int quarryMetal = 1;
    /** Most a worker carries before taking it to the town hall or a storehouse. */
    public static int carryCapacity = 20;
    /** Health a building loses to one hit of a soldier. */
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
            "Units teams train and command from the web map: soldiers at a barracks, workers at the town hall.");
        for (Resource resource : Resource.values()) {
            unitCost[resource.ordinal()] = configuration.getInt(
                "cost" + resource.displayName,
                units,
                (int) unitCost[resource.ordinal()],
                0,
                Integer.MAX_VALUE,
                resource.displayName + " a soldier costs to train.");
            workerCost[resource.ordinal()] = configuration.getInt(
                "workerCost" + resource.displayName,
                units,
                (int) workerCost[resource.ordinal()],
                0,
                Integer.MAX_VALUE,
                resource.displayName + " a worker costs to train.");
        }
        unitsPerTeam = configuration.getInt(
            "perTeam",
            units,
            unitsPerTeam,
            0,
            1000,
            "Most units, soldiers and workers together, a team can have at once.");
        unitHealth = configuration.getInt("health", units, unitHealth, 1, 1000, "Health of a unit; 20 is a player's.");
        trainSeconds = configuration
            .getInt("trainSeconds", units, trainSeconds, 0, 3600, "Seconds it takes to train one unit.");

        String buildings = "buildings";
        configuration.setCategoryComment(
            buildings,
            "Buildings: the town hall every team starts with, and the farms, barracks, storehouses and quarries"
                + " workers build.");
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
        farmWood = configuration
            .getInt("farmWood", buildings, farmWood, 0, 100000, "Wood each worker at a farm gathers per harvest.");
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
            "Resources a worker carries before taking them to the town hall or a storehouse.");
        unitBuildingDamage = configuration.getInt(
            "unitDamage",
            buildings,
            unitBuildingDamage,
            0,
            100000,
            "Health a building loses to one hit of an enemy soldier.");
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

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
