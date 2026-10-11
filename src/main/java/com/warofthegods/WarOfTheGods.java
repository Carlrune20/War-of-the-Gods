package com.warofthegods;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.warofthegods.command.CommandRts;
import com.warofthegods.game.PlayerEvents;
import com.warofthegods.world.DimensionSync;
import com.warofthegods.world.GameWorld;
import com.warofthegods.world.MapBorder;

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
    // Server side only, so players can join with a plain Forge client.
    acceptableRemoteVersions = "*")
public class WarOfTheGods {

    public static final String MODID = "warofthegods";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        LOG.info("War of the Gods " + Tags.VERSION + " loading");
        GameWorld.register();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus()
            .register(new PlayerEvents());
        FMLCommonHandler.instance()
            .bus()
            .register(new DimensionSync());
        MapBorder border = new MapBorder();
        FMLCommonHandler.instance()
            .bus()
            .register(border);
        MinecraftForge.EVENT_BUS.register(border);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandRts());
    }
}
