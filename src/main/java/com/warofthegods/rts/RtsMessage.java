package com.warofthegods.rts;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.warofthegods.WarOfTheGods;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * A JSON message between the server and a player's client for the in-game RTS view, deflated. The server sends the
 * same game and live state messages browsers get; the client sends the same commands. A message that cannot be read
 * arrives with a null json and is dropped.
 */
public final class RtsMessage implements IMessage {

    /** Most bytes a message may take on the wire. */
    private static final int MAX_COMPRESSED = 4 * 1024 * 1024;
    /** Most bytes a message may hold once inflated. */
    private static final int MAX_JSON = 32 * 1024 * 1024;

    public String json;
    /** The json deflated, kept from the first send for the rest when one message goes to several players. */
    private byte[] compressed;

    public RtsMessage() {}

    public RtsMessage(String json) {
        this.json = json;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        json = null;
        int length = buf.readInt();
        if (length < 0 || length > MAX_COMPRESSED || length > buf.readableBytes()) {
            return;
        }
        byte[] data = new byte[length];
        buf.readBytes(data);
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream(length * 4);
            byte[] chunk = new byte[8192];
            while (!inflater.finished()) {
                int read = inflater.inflate(chunk);
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    return;
                }
                out.write(chunk, 0, read);
                if (out.size() > MAX_JSON) {
                    return;
                }
            }
            json = new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (DataFormatException e) {
            WarOfTheGods.LOG.debug("Dropped an RTS message that would not inflate", e);
        } finally {
            inflater.end();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (compressed == null) {
            compressed = deflate(json);
        }
        buf.writeInt(compressed.length);
        buf.writeBytes(compressed);
    }

    private static byte[] deflate(String json) {
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
            byte[] chunk = new byte[8192];
            while (!deflater.finished()) {
                out.write(chunk, 0, deflater.deflate(chunk));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }
}
