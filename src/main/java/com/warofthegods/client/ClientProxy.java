package com.warofthegods.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.MinecraftForgeClient;

import com.warofthegods.CommonProxy;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.client.rts.RtsClient;
import com.warofthegods.item.ItemGodsword;
import com.warofthegods.rts.EntityCommanderBody;
import com.warofthegods.rts.PrayerMessage;
import com.warofthegods.rts.TerrainMessage;
import com.warofthegods.unit.EntityCarcass;
import com.warofthegods.unit.EntitySpell;
import com.warofthegods.unit.EntityTeamArrow;
import com.warofthegods.unit.EntityUnit;
import com.warofthegods.unit.EntityWildMob;

import cpw.mods.fml.client.registry.RenderingRegistry;

/**
 * Client setup: how units, commanders' bodies, carcasses, armor and the mod's own items are drawn, the in-game RTS
 * view, and the shader pack.
 */
public class ClientProxy extends CommonProxy {

    /** How big Zamorak's staff and Silverlight are drawn in hand: a staff a little taller than a man, a long sword. */
    private static final float ZAMORAK_STAFF_SCALE = 0.0205F;
    private static final float SILVERLIGHT_SCALE = 0.0237F;
    /** The artifacts from ruins' chests are drawn as Silverlight is: a great godsword, a bow, a scimitar. */
    private static final float ARTIFACT_SCALE = 0.0237F;

    @Override
    public void init() {
        RenderingRegistry.registerEntityRenderingHandler(EntityUnit.class, new RenderUnit());
        RenderingRegistry.registerEntityRenderingHandler(EntityCommanderBody.class, new RenderCommanderBody());
        RenderingRegistry.registerEntityRenderingHandler(EntityTeamArrow.class, new RenderTeamArrow());
        RenderingRegistry.registerEntityRenderingHandler(EntityWildMob.class, new RenderWildMob());
        RenderingRegistry.registerEntityRenderingHandler(EntityCarcass.class, new RenderCarcass());
        RenderingRegistry.registerEntityRenderingHandler(EntitySpell.class, new RenderSpell());
        MinecraftForgeClient.registerItemRenderer(
            WarOfTheGods.zamorakStaff,
            new RenderModelItem("zamorak_staff", ZAMORAK_STAFF_SCALE, -17));
        MinecraftForgeClient
            .registerItemRenderer(WarOfTheGods.silverlight, new RenderModelItem("silverlight", SILVERLIGHT_SCALE, -22));
        for (ItemGodsword godsword : WarOfTheGods.godswords) {
            MinecraftForgeClient.registerItemRenderer(
                godsword,
                new RenderModelItem(
                    ItemGodsword.registryName(godsword.godId),
                    ARTIFACT_SCALE,
                    -29,
                    RenderModelItem.Hold.HEAVY));
        }
        MinecraftForgeClient.registerItemRenderer(
            WarOfTheGods.armadylCrossbow,
            new RenderModelItem("armadyl_crossbow", ARTIFACT_SCALE, -6));
        MinecraftForgeClient.registerItemRenderer(
            WarOfTheGods.darkBow,
            new RenderModelItem("dark_bow", ARTIFACT_SCALE, 0, RenderModelItem.Hold.BOW));
        MinecraftForgeClient.registerItemRenderer(
            WarOfTheGods.dragonScimitar,
            new RenderModelItem("dragon_scimitar", ARTIFACT_SCALE, -22));
        ArmorLooks.get()
            .register();
        MobRenderers.register();
        PrayerHud.register();
        GodswordPose.register();
        MurkyWater.register();
        RtsClient.init();
        ShaderPack.install(Minecraft.getMinecraft().mcDataDir);
        ShaderPack.register();
        ChunkCatchUp.register();
    }

    @Override
    public void loadComplete() {
        SoundStartup.awaitFirstStart();
    }

    @Override
    public void receiveRts(String json) {
        RtsClient client = RtsClient.get();
        if (client != null) {
            client.receive(json);
        }
    }

    @Override
    public void receiveTerrain(TerrainMessage message) {
        RtsClient client = RtsClient.get();
        if (client != null) {
            client.receiveTerrain(message);
        }
    }

    @Override
    public void receivePrayer(PrayerMessage message) {
        PrayerHud.receive(message);
    }
}
