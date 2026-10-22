package com.warofthegods;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.warofthegods.ai.AiPlayers;
import com.warofthegods.block.ModBlocks;
import com.warofthegods.building.BuildingCommands;
import com.warofthegods.building.Buildings;
import com.warofthegods.command.CommandRts;
import com.warofthegods.command.Lobby;
import com.warofthegods.command.TeamCommands;
import com.warofthegods.game.Faction;
import com.warofthegods.game.PlayerEvents;
import com.warofthegods.game.SafeMode;
import com.warofthegods.game.WinConditions;
import com.warofthegods.item.ArmorKnockback;
import com.warofthegods.item.ItemArtifact;
import com.warofthegods.item.ItemDarkBow;
import com.warofthegods.item.ItemDarkIronArmor;
import com.warofthegods.item.ItemDragonArmor;
import com.warofthegods.item.ItemDragonScimitar;
import com.warofthegods.item.ItemGodsword;
import com.warofthegods.item.ItemPrayerBook;
import com.warofthegods.item.ItemSilverlight;
import com.warofthegods.item.ItemZamorakStaff;
import com.warofthegods.rts.Commanders;
import com.warofthegods.rts.EntityCommanderBody;
import com.warofthegods.rts.RtsNetwork;
import com.warofthegods.shop.Shop;
import com.warofthegods.unit.Combat;
import com.warofthegods.unit.EntityCarcass;
import com.warofthegods.unit.EntitySpell;
import com.warofthegods.unit.EntityTeamArrow;
import com.warofthegods.unit.EntityUnit;
import com.warofthegods.unit.Hunting;
import com.warofthegods.unit.Mobs;
import com.warofthegods.unit.PlayerPrayers;
import com.warofthegods.unit.UnitCommands;
import com.warofthegods.unit.Units;
import com.warofthegods.unit.WarFavor;
import com.warofthegods.web.WebMap;
import com.warofthegods.web.WebServer;
import com.warofthegods.world.DimensionSync;
import com.warofthegods.world.GameWorld;
import com.warofthegods.world.MapBorder;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

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

    /** The staff Zamorak mages carry, which players can cast fire strikes with too. */
    public static ItemZamorakStaff zamorakStaff;
    /** The demon-slaying sword, one of which lies in a shrine on each game's map. */
    public static ItemSilverlight silverlight;
    /** Zamorak's dark iron armor, from the helmet (0) to the boots (3). */
    public static final ItemDarkIronArmor[] darkIron = new ItemDarkIronArmor[4];
    /** The artifacts found in old ruins' chests: the dark bow, the godsword blade and the prayer book. */
    public static ItemDarkBow darkBow;
    public static ItemArtifact godswordBlade;
    public static ItemPrayerBook prayerBook;
    /** The dragon scimitar and dragon armor, from the helmet (0) to the boots (3), also found in ruins' chests. */
    public static ItemDragonScimitar dragonScimitar;
    public static final ItemDragonArmor[] dragonArmor = new ItemDragonArmor[4];
    /** By the god's ordinal: the hilts armories sell, and the godswords made with them and a blade. */
    public static final ItemArtifact[] hilts = new ItemArtifact[Faction.values().length];
    public static final ItemGodsword[] godswords = new ItemGodsword[Faction.values().length];

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
        EntityRegistry.registerModEntity(EntityCarcass.class, "Carcass", 3, this, 80, 40, false);
        EntityRegistry.registerModEntity(EntitySpell.class, "Spell", 4, this, 64, 10, true);
        zamorakStaff = new ItemZamorakStaff();
        GameRegistry.registerItem(zamorakStaff, "zamorak_staff");
        ModBlocks.register();
        silverlight = new ItemSilverlight();
        GameRegistry.registerItem(silverlight, "silverlight");
        for (int slot = 0; slot < darkIron.length; slot++) {
            darkIron[slot] = new ItemDarkIronArmor(slot);
            GameRegistry.registerItem(darkIron[slot], darkIron[slot].registryName());
        }
        registerArtifacts();
        RtsNetwork.register();
    }

    /** The things found in ruins' chests, the hilts and the godswords, and the recipes that make a godsword. */
    private static void registerArtifacts() {
        darkBow = new ItemDarkBow();
        GameRegistry.registerItem(darkBow, "dark_bow");
        godswordBlade = new ItemArtifact("godsword_blade", "Fit a god's hilt to it to make a godsword");
        GameRegistry.registerItem(godswordBlade, "godsword_blade");
        prayerBook = new ItemPrayerBook();
        GameRegistry.registerItem(prayerBook, "prayer_book");
        dragonScimitar = new ItemDragonScimitar();
        GameRegistry.registerItem(dragonScimitar, "dragon_scimitar");
        for (int slot = 0; slot < dragonArmor.length; slot++) {
            dragonArmor[slot] = new ItemDragonArmor(slot);
            GameRegistry.registerItem(dragonArmor[slot], dragonArmor[slot].registryName());
        }
        for (Faction god : Faction.values()) {
            hilts[god.ordinal()] = new ItemArtifact(god.id + "_hilt", "Fit it to a godsword blade to make a godsword");
            GameRegistry.registerItem(hilts[god.ordinal()], god.id + "_hilt");
            godswords[god.ordinal()] = new ItemGodsword(god);
            GameRegistry.registerItem(godswords[god.ordinal()], ItemGodsword.registryName(god));
            GameRegistry
                .addShapelessRecipe(new ItemStack(godswords[god.ordinal()]), godswordBlade, hilts[god.ordinal()]);
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus()
            .register(new SafeMode());
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
        MinecraftForge.EVENT_BUS.register(silverlight);
        MinecraftForge.EVENT_BUS.register(darkBow);
        MinecraftForge.EVENT_BUS.register(new ArmorKnockback());
        MinecraftForge.EVENT_BUS.register(godswords[Faction.SARADOMIN.ordinal()]);
        PlayerPrayers prayers = new PlayerPrayers();
        FMLCommonHandler.instance()
            .bus()
            .register(prayers);
        MinecraftForge.EVENT_BUS.register(prayers);
        Mobs mobs = new Mobs();
        FMLCommonHandler.instance()
            .bus()
            .register(mobs);
        MinecraftForge.EVENT_BUS.register(mobs);
        MinecraftForge.EVENT_BUS.register(new Hunting());
        MinecraftForge.EVENT_BUS.register(new WarFavor());
        UnitCommands.register();
        Buildings buildings = new Buildings();
        FMLCommonHandler.instance()
            .bus()
            .register(buildings);
        MinecraftForge.EVENT_BUS.register(buildings);
        BuildingCommands.register();
        MinecraftForge.EVENT_BUS.register(new Shop());
        FMLCommonHandler.instance()
            .bus()
            .register(new WinConditions());
        FMLCommonHandler.instance()
            .bus()
            .register(new AiPlayers());
        TeamCommands.register();
        Lobby.register();
        FMLCommonHandler.instance()
            .bus()
            .register(Commanders.get());
        MinecraftForge.EVENT_BUS.register(Commanders.get());
        proxy.init();
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        proxy.loadComplete();
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
