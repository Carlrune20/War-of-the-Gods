package com.warofthegods.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MapTilesTest {

    private static final int CHUNKS = MapTiles.CHUNKS_PER_TILE * MapTiles.CHUNKS_PER_TILE;

    private static byte[] chunk(int fill) {
        byte[] data = new byte[512];
        data[0] = (byte) fill;
        return data;
    }

    @Test
    void newerChunksGivesEachChangedChunkOnce() {
        MapTiles tiles = new MapTiles(1);
        tiles.setChunk(0, 0, chunk(1));
        tiles.setChunk(3, 2, chunk(2));
        long key = tiles.versionsByKey()
            .keySet()
            .iterator()
            .next();
        int[] sent = new int[CHUNKS];
        int[] indexes = new int[CHUNKS];
        byte[][] data = new byte[CHUNKS][];

        assertEquals(2, tiles.newerChunks(key, sent, indexes, data));
        assertEquals(0, indexes[0]);
        assertEquals(3 + 2 * MapTiles.CHUNKS_PER_TILE, indexes[1]);
        assertEquals(0, tiles.newerChunks(key, sent, indexes, data));

        tiles.setChunk(3, 2, chunk(5));
        assertEquals(1, tiles.newerChunks(key, sent, indexes, data));
        assertArrayEquals(chunk(5), data[0]);
    }

    @Test
    void anUnchangedChunkKeepsItsVersion() {
        MapTiles tiles = new MapTiles(1);
        tiles.setChunk(-1, -1, chunk(1));
        long key = tiles.versionsByKey()
            .keySet()
            .iterator()
            .next();
        int version = tiles.versionsByKey()
            .get(key);
        tiles.setChunk(-1, -1, chunk(1));

        assertEquals(
            version,
            (int) tiles.versionsByKey()
                .get(key));
        assertEquals(-1, MapTiles.tileX(key));
        assertEquals(-1, MapTiles.tileZ(key));
    }
}
