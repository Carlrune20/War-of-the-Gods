package com.warofthegods;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

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

    public static final long[] farmCost = { 0, 75, 0, 0 };
    public static final long[] barracksCost = { 0, 150, 50, 0 };
    public static int farmWorkers = 3;
    public static int farmYieldSeconds = 5;
    public static int farmFood = 5;
    public static int farmWood = 3;

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
        configuration.setCategoryComment(buildings, "Buildings workers build: farms and barracks.");
        for (Resource resource : Resource.values()) {
            farmCost[resource.ordinal()] = configuration.getInt(
                "farmCost" + resource.displayName,
                buildings,
                (int) farmCost[resource.ordinal()],
                0,
                Integer.MAX_VALUE,
                resource.displayName + " a farm costs.");
            barracksCost[resource.ordinal()] = configuration.getInt(
                "barracksCost" + resource.displayName,
                buildings,
                (int) barracksCost[resource.ordinal()],
                0,
                Integer.MAX_VALUE,
                resource.displayName + " a barracks costs.");
        }
        farmWorkers = configuration
            .getInt("farmWorkers", buildings, farmWorkers, 1, 64, "Most workers that can work one farm at a time.");
        farmYieldSeconds = configuration
            .getInt("farmYieldSeconds", buildings, farmYieldSeconds, 1, 3600, "Seconds between a farm's harvests.");
        farmFood = configuration
            .getInt("farmFood", buildings, farmFood, 0, 100000, "Food each worker at a farm brings in per harvest.");
        farmWood = configuration
            .getInt("farmWood", buildings, farmWood, 0, 100000, "Wood each worker at a farm brings in per harvest.");

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
