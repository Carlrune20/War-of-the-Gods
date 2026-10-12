package com.warofthegods.world;

import net.minecraftforge.common.network.ForgeMessage;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLEmbeddedChannel;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.relauncher.Side;

/**
 * Tells each connecting Forge client about the game dimension before the player joins, since its id comes from the
 * server's config. The client is told it is an overworld-type dimension (provider type 0), which it knows how to draw.
 */
public class DimensionSync {

    private static final int OVERWORLD_PROVIDER_TYPE = 0;

    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ServerConnectionFromClientEvent event) {
        if (event.isLocal) {
            return;
        }
        FMLEmbeddedChannel channel = NetworkRegistry.INSTANCE.getChannel("FORGE", Side.SERVER);
        synchronized (channel) {
            event.manager.scheduleOutboundPacket(
                channel.generatePacketFrom(
                    new ForgeMessage.DimensionRegisterMessage(GameWorld.dimensionId(), OVERWORLD_PROVIDER_TYPE)));
        }
    }
}
