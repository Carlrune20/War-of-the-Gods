package com.warofthegods.client;

import com.warofthegods.CommonProxy;
import com.warofthegods.unit.EntityUnit;

import cpw.mods.fml.client.registry.RenderingRegistry;

/** Client setup: how units are drawn. */
public class ClientProxy extends CommonProxy {

    @Override
    public void init() {
        RenderingRegistry.registerEntityRenderingHandler(EntityUnit.class, new RenderUnit());
    }
}
