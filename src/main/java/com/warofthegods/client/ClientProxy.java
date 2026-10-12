package com.warofthegods.client;

import com.warofthegods.CommonProxy;
import com.warofthegods.client.rts.RtsClient;
import com.warofthegods.unit.EntityUnit;

import cpw.mods.fml.client.registry.RenderingRegistry;

/** Client setup: how units are drawn, and the in-game RTS view. */
public class ClientProxy extends CommonProxy {

    @Override
    public void init() {
        RenderingRegistry.registerEntityRenderingHandler(EntityUnit.class, new RenderUnit());
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
