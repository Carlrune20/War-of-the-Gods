package com.warofthegods.client;

import net.minecraft.client.renderer.entity.RenderArrow;

import com.warofthegods.CommonProxy;
import com.warofthegods.client.rts.RtsClient;
import com.warofthegods.rts.EntityCommanderBody;
import com.warofthegods.unit.EntityTeamArrow;
import com.warofthegods.unit.EntityUnit;

import cpw.mods.fml.client.registry.RenderingRegistry;

/** Client setup: how units and commanders' bodies are drawn, and the in-game RTS view. */
public class ClientProxy extends CommonProxy {

    @Override
    public void init() {
        RenderingRegistry.registerEntityRenderingHandler(EntityUnit.class, new RenderUnit());
        RenderingRegistry.registerEntityRenderingHandler(EntityCommanderBody.class, new RenderCommanderBody());
        RenderingRegistry.registerEntityRenderingHandler(EntityTeamArrow.class, new RenderArrow());
        RtsClient.init();
    }

    @Override
    public void receiveRts(String json) {
        RtsClient client = RtsClient.get();
        if (client != null) {
            client.receive(json);
        }
    }
}
