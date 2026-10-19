package com.warofthegods.rts;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

class TerrainMessageTest {

    @Test
    void chunksSurviveTheWire() {
        byte[] first = new byte[512];
        byte[] second = new byte[516];
        first[3] = 7;
        second[515] = -2;
        TerrainMessage sent = new TerrainMessage(
            "map-1",
            -2,
            5,
            new int[] { 4, 63, 0 },
            new byte[][] { first, second, null },
            2);
        ByteBuf buf = Unpooled.buffer();
        sent.toBytes(buf);

        TerrainMessage received = new TerrainMessage();
        received.fromBytes(buf);

        assertEquals("map-1", received.mapId);
        assertEquals(-2, received.tileX);
        assertEquals(5, received.tileZ);
        assertArrayEquals(new int[] { 4, 63 }, received.indexes);
        assertArrayEquals(first, received.chunks[0]);
        assertArrayEquals(second, received.chunks[1]);
    }

    @Test
    void aBrokenMessageArrivesEmpty() {
        ByteBuf buf = Unpooled.buffer();
        new TerrainMessage("map-1", 0, 0, new int[] { 1 }, new byte[][] { new byte[512] }, 1).toBytes(buf);
        buf.setByte(buf.writerIndex() - 1, buf.getByte(buf.writerIndex() - 1) ^ 0x55);

        TerrainMessage received = new TerrainMessage();
        received.fromBytes(buf);

        assertEquals(0, received.chunks.length);
    }
}
