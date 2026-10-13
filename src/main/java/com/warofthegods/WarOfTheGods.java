package com.warofthegods;

import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.warofthegods.building.BuildingCommands;
import com.warofthegods.building.Buildings;
import com.warofthegods.command.CommandRts;
import com.warofthegods.command.TeamCommands;
import com.warofthegods.game.PlayerEvents;
import com.warofthegods.rts.Commanders;
import com.warofthegods.rts.EntityCommanderBody;
import com.warofthegods.rts.RtsNetwork;
import com.warofthegods.unit.Combat;
import com.warofthegods.unit.EntityTeamArrow;
import com.warofthegods.unit.EntityUnit;
import com.warofthegods.unit.Hunting;
import com.warofthegods.unit.Mobs;
import com.warofthegods.unit.UnitCommands;
import com.warofthegods.unit.Units;
import com.warofthegods.web.WebMap;
import com.warofthegods.web.WebServer;
import com.warofthegods.world.DimensionSync;
import com.warofthegods.world.GameWorld;
import com.warofthegods.world.MapBorder;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.registry.EntityRegistry;

@Mod(
    modid = WarOfTheGods.MODID,
    version = Tags.VERSION,
    name = "War of the Gods",
    // Players need the mod too: it draws the units.
    acceptedMinecraftVersions = "[1.7.10]")
public class WarOfTheGods {

    public static final String MODID = "warofthegods";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static WarOfTheGods instance;

    @SidedProxy(clientSide = "com.warofthegods.client.ClientProxy", serverSide = "com.warofthegods.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        LOG.info("War of the Gods " + Tags.VERSION + " loading");
        GameWorld.register();
        EntityRegistry.registerModEntity(EntityUnit.class, "Unit", 0, this, 96, 3, true);
        EntityRegistry.registerModEntity(EntityCommanderBody.class, "CommanderBody", 1, this, 160, 3, true);
        EntityRegistry.registerModEntity(EntityTeamArrow.class, "TeamArrow", 2, this, 64, 20, true);
        RtsNetwork.register();
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
        WebMap webMap = new WebMap();
        FMLCommonHandler.instance()
            .bus()
            .register(webMap);
        MinecraftForge.EVENT_BUS.register(webMap);
        Units units = new Units();
        FMLCommonHandler.instance()
            .bus()
            .register(units);
        ForgeChunkManager.setForcedChunkLoadingCallback(this, units.chunkLoadingCallback());
        MinecraftForge.EVENT_BUS.register(new Combat());
        Mobs mobs = new Mobs();
        FMLCommonHandler.instance()
            .bus()
            .register(mobs);
        MinecraftForge.EVENT_BUS.register(mobs);
        MinecraftForge.EVENT_BUS.register(new Hunting());
        UnitCommands.register();
        Buildings buildings = new Buildings();
        FMLCommonHandler.instance()
            .bus()
            .register(buildings);
        MinecraftForge.EVENT_BUS.register(buildings);
        BuildingCommands.register();
        TeamCommands.register();
        FMLCommonHandler.instance()
            .bus()
            .register(Commanders.get());
        MinecraftForge.EVENT_BUS.register(Commanders.get());
        proxy.init();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandRts());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        WebServer.start();
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        Commanders.get()
            .leaveAll();
        WebServer.stop();
    }
}
