package com.warofthegods.client;

import net.minecraftforge.client.MinecraftForgeClient;

import com.warofthegods.CommonProxy;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.client.rts.RtsClient;
import com.warofthegods.rts.EntityCommanderBody;
import com.warofthegods.rts.TerrainMessage;
import com.warofthegods.unit.EntityCarcass;
import com.warofthegods.unit.EntitySpell;
import com.warofthegods.unit.EntityTeamArrow;
import com.warofthegods.unit.EntityUnit;

import cpw.mods.fml.client.registry.RenderingRegistry;

/**
 * Client setup: how units, commanders' bodies, carcasses and the mod's own items are drawn, and the in-game RTS view.
 */
public class ClientProxy extends CommonProxy {

    /** How big Zamorak's staff and Silverlight are drawn in hand: a staff a little taller than a man, a long sword. */
    private static final float ZAMORAK_STAFF_SCALE = 0.0205F;
    private static final float SILVERLIGHT_SCALE = 0.0237F;

    @Override
    public void init() {
        RenderingRegistry.registerEntityRenderingHandler(EntityUnit.class, new RenderUnit());
        RenderingRegistry.registerEntityRenderingHandler(EntityCommanderBody.class, new RenderCommanderBody());
        RenderingRegistry.registerEntityRenderingHandler(EntityTeamArrow.class, new RenderTeamArrow());
        RenderingRegistry.registerEntityRenderingHandler(EntityCarcass.class, new RenderCarcass());
        RenderingRegistry.registerEntityRenderingHandler(EntitySpell.class, new RenderSpell());
        MinecraftForgeClient.registerItemRenderer(
            WarOfTheGods.zamorakStaff,
            new RenderModelItem("zamorak_staff", ZAMORAK_STAFF_SCALE, -17));
        MinecraftForgeClient
            .registerItemRenderer(WarOfTheGods.silverlight, new RenderModelItem("silverlight", SILVERLIGHT_SCALE, -22));
        RtsClient.init();
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
}
