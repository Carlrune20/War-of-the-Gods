package com.warofthegods.rts;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** A player's prayer points and protection prayer, for the bar above their hunger. */
public final class PrayerMessage implements IMessage {

    public int points;
    /** The chosen prayer's ordinal. */
    public int prayer;
    public boolean up;

    public PrayerMessage() {}

    public PrayerMessage(int points, int prayer, boolean up) {
        this.points = points;
        this.prayer = prayer;
        this.up = up;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        points = buf.readByte();
        prayer = buf.readByte();
        up = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(points);
        buf.writeByte(prayer);
        buf.writeBoolean(up);
    }
}
