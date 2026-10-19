package com.warofthegods.rts;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import com.warofthegods.WarOfTheGods;
import com.warofthegods.web.MapTiles;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * Some chunks of one tile of the map's surface, from the server to a player in the RTS view, who draws the map with
 * them beyond the chunks their client has. Each chunk is encoded as {@link MapTiles} holds it. The chunks are deflated
 * together. A message that cannot be read arrives with no chunks and is dropped.
 */
public final class TerrainMessage implements IMessage {

    /** Most bytes a message may take on the wire. */
    private static final int MAX_COMPRESSED = 4 * 1024 * 1024;
    /** Most bytes one chunk may hold: 256 columns of up to 255 blocks. */
    private static final int MAX_CHUNK = 512 + 256 * 255 * 2;

    /** The map these chunks are of, as {@link MapTiles#getId()}. */
    public String mapId;
    public int tileX;
    public int tileZ;
    /** Each chunk's index within the tile (chunkX + chunkZ * 8), and its encoded blocks. */
    public int[] indexes = new int[0];
    public byte[][] chunks = new byte[0][];
    private byte[] compressed;

    public TerrainMessage() {}

    public TerrainMessage(String mapId, int tileX, int tileZ, int[] indexes, byte[][] chunks, int count) {
        this.mapId = mapId;
        this.tileX = tileX;
        this.tileZ = tileZ;
        this.indexes = Arrays.copyOf(indexes, count);
        this.chunks = Arrays.copyOf(chunks, count);
        this.compressed = deflate();
    }

    /** Bytes the chunks take on the wire. */
    public int size() {
        return compressed.length;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        indexes = new int[0];
        chunks = new byte[0][];
        mapId = ByteBufUtils.readUTF8String(buf);
        tileX = buf.readInt();
        tileZ = buf.readInt();
        int length = buf.readInt();
        if (length < 0 || length > MAX_COMPRESSED || length > buf.readableBytes()) {
            return;
        }
        byte[] data = new byte[length];
        buf.readBytes(data);
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
            int count = in.readUnsignedByte();
            int max = MapTiles.CHUNKS_PER_TILE * MapTiles.CHUNKS_PER_TILE;
            if (count > max) {
                return;
            }
            int[] readIndexes = new int[count];
            byte[][] readChunks = new byte[count][];
            for (int i = 0; i < count; i++) {
                readIndexes[i] = in.readUnsignedByte();
                int size = in.readInt();
                if (readIndexes[i] >= max || size < 512 || size > MAX_CHUNK) {
                    return;
                }
                readChunks[i] = new byte[size];
                in.readFully(readChunks[i]);
            }
            indexes = readIndexes;
            chunks = readChunks;
        } catch (IOException e) {
            WarOfTheGods.LOG.debug("Dropped map surface that would not inflate", e);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (compressed == null) {
            compressed = deflate();
        }
        ByteBufUtils.writeUTF8String(buf, mapId);
        buf.writeInt(tileX);
        buf.writeInt(tileZ);
        buf.writeInt(compressed.length);
        buf.writeBytes(compressed);
    }

    private byte[] deflate() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            out.writeByte(indexes.length);
            for (int i = 0; i < indexes.length; i++) {
                out.writeByte(indexes[i]);
                out.writeInt(chunks[i].length);
                out.write(chunks[i]);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }
}
