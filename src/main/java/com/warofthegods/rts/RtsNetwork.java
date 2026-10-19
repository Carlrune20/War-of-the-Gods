package com.warofthegods.rts;

import net.minecraft.entity.player.EntityPlayerMP;

import com.warofthegods.WarOfTheGods;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * The mod's network channel, carrying {@link RtsMessage}s both ways. Both handlers run on Netty threads and only queue
 * what arrives for the server or client thread.
 */
public final class RtsNetwork {

    private static SimpleNetworkWrapper channel;

    private RtsNetwork() {}

    public static void register() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(WarOfTheGods.MODID);
        channel.registerMessage(ToServer.class, RtsMessage.class, 0, Side.SERVER);
        channel.registerMessage(ToClient.class, RtsMessage.class, 0, Side.CLIENT);
    }

    public static void sendTo(EntityPlayerMP player, String json) {
        sendTo(player, new RtsMessage(json));
    }

    /** Sends a message already made, which may go to several players and is compressed only the first time. */
    public static void sendTo(EntityPlayerMP player, RtsMessage message) {
        channel.sendTo(message, player);
    }

    public static void sendToServer(String json) {
        channel.sendToServer(new RtsMessage(json));
    }

    public static final class ToServer implements IMessageHandler<RtsMessage, IMessage> {

        @Override
        public IMessage onMessage(RtsMessage message, MessageContext ctx) {
            if (message.json != null) {
                Commanders.receive(ctx.getServerHandler().playerEntity, message.json);
            }
            return null;
        }
    }

    public static final class ToClient implements IMessageHandler<RtsMessage, IMessage> {

        @Override
        public IMessage onMessage(RtsMessage message, MessageContext ctx) {
            if (message.json != null) {
                WarOfTheGods.proxy.receiveRts(message.json);
            }
            return null;
        }
    }
}
