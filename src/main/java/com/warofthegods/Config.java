package com.warofthegods;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.warofthegods.game.Resource;

public class Config {

    public static int defaultMapSize = 1000;
    public static int defaultTeamCount = 2;
    public static boolean allowSelfJoin = true;
    public static final long[] startingResources = { 200, 200, 100, 0 };

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

        for (Resource resource : Resource.values()) {
            startingResources[resource.ordinal()] = configuration.getInt(
                resource.id,
                "startingResources",
                (int) startingResources[resource.ordinal()],
                0,
                Integer.MAX_VALUE,
                "Amount of " + resource.displayName + " each team starts a game with.");
        }

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
