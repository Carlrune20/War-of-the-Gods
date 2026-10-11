package com.warofthegods;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.warofthegods.command.CommandRts;
import com.warofthegods.game.PlayerEvents;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = WarOfTheGods.MODID,
    version = Tags.VERSION,
    name = "War of the Gods",
    acceptedMinecraftVersions = "[1.7.10]",
    // Phase 0 is server side only, so players can join with a plain Forge client.
    acceptableRemoteVersions = "*")
public class WarOfTheGods {

    public static final String MODID = "warofthegods";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        LOG.info("War of the Gods " + Tags.VERSION + " loading");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus()
            .register(new PlayerEvents());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandRts());
    }
}
