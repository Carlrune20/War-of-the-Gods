package com.warofthegods.web;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DeflaterOutputStream;

/**
 * The blocks of one game's map that browsers draw in 3D, grouped in square tiles of {@link #TILE_SIZE} blocks (8x8
 * chunks). The server thread stores each chunk's visible blocks as encoded by {@link MapRenderer}; web threads read a
 * tile out as one compressed blob. Each change gives the tile a new version number, which browsers use to know when
 * to fetch it again.
 *
 * <p>
 * A tile blob is zlib-compressed. Inside, all numbers big-endian: the bytes "WOTG", a format byte (1), then for each
 * of the 64 chunks, row by row (index chunkX + chunkZ * 8 within the tile), a u32 length and that many bytes of chunk
 * data, length 0 for a chunk not drawn yet. Chunk data is 256 columns (index x + z * 16), each a u8 base height and a
 * u8 block count, followed by every column's blocks in the same order, bottom to top, as u16 (block id << 4 |
 * metadata). A column holds the blocks from its base up to its highest block; everything below the base counts as
 * solid ground nobody can see.
 */
public final class MapTiles {

    public static final int TILE_SIZE = 128;
    public static final int CHUNKS_PER_TILE = TILE_SIZE / 16;
    private static final byte FORMAT = 1;

    private final int gameNumber;
    /**
     * Names this map for as long as the server runs. Tile versions count up from 1 again for every map, after a new
     * game and after a server restart alike, so browsers ask for tiles with the map id in the address too: a tile
     * they cached from an older map then never stands in for one of this map.
     */
    private final String id;
    private final Map<Long, Tile> tiles = new ConcurrentHashMap<>();
    private final Set<Long> changed = ConcurrentHashMap.newKeySet();
    private final AtomicInteger versions = new AtomicInteger();

    private static final class Tile {

        final byte[][] chunks = new byte[CHUNKS_PER_TILE * CHUNKS_PER_TILE][];
        /** The version each chunk got when it last changed, 0 for one not drawn yet. */
        final int[] chunkVersions = new int[CHUNKS_PER_TILE * CHUNKS_PER_TILE];
        volatile int version;
        byte[] blob;
        int blobVersion = -1;
    }

    public MapTiles(int gameNumber) {
        this.gameNumber = gameNumber;
        this.id = gameNumber + "-"
            + Long.toString(
                ThreadLocalRandom.current()
                    .nextLong() & Long.MAX_VALUE,
                36);
    }

    public String getId() {
        return id;
    }

    public int getGameNumber() {
        return gameNumber;
    }

    private static long key(int tx, int tz) {
        return ((long) tx << 32) | (tz & 0xFFFFFFFFL);
    }

    /** Stores one chunk's encoded blocks; the tile only gets a new version if they changed. */
    public void setChunk(int chunkX, int chunkZ, byte[] data) {
        int tx = Math.floorDiv(chunkX, CHUNKS_PER_TILE);
        int tz = Math.floorDiv(chunkZ, CHUNKS_PER_TILE);
        long key = key(tx, tz);
        Tile tile = tiles.computeIfAbsent(key, k -> new Tile());
        int index = Math.floorMod(chunkX, CHUNKS_PER_TILE) + Math.floorMod(chunkZ, CHUNKS_PER_TILE) * CHUNKS_PER_TILE;
        synchronized (tile) {
            if (Arrays.equals(tile.chunks[index], data)) {
                return;
            }
            tile.chunks[index] = data;
            tile.chunkVersions[index] = versions.incrementAndGet();
            tile.version = tile.chunkVersions[index];
        }
        changed.add(key);
    }

    /** Tiles that changed since the last call, as "x,z" to version. */
    public Map<String, Integer> takeChanged() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Long key : changed.toArray(new Long[0])) {
            changed.remove(key);
            Tile tile = tiles.get(key);
            if (tile != null) {
                result.put(name(key), tile.version);
            }
        }
        return result;
    }

    /** Every tile drawn so far, as "x,z" to version. */
    public Map<String, Integer> allVersions() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Tile> entry : tiles.entrySet()) {
            result.put(name(entry.getKey()), entry.getValue().version);
        }
        return result;
    }

    /** Every tile drawn so far, as its key ({@link #tileX}, {@link #tileZ}) to version. */
    public Map<Long, Integer> versionsByKey() {
        Map<Long, Integer> result = new HashMap<>();
        for (Map.Entry<Long, Tile> entry : tiles.entrySet()) {
            result.put(entry.getKey(), entry.getValue().version);
        }
        return result;
    }

    public static int tileX(long key) {
        return (int) (key >> 32);
    }

    public static int tileZ(long key) {
        return (int) key;
    }

    /**
     * The chunks of a tile that changed since the versions in sent, which it brings up to date: as many as were found,
     * their indexes within the tile (chunkX + chunkZ * 8) in indexes and their encoded blocks in data. sent, indexes
     * and
     * data hold {@link #CHUNKS_PER_TILE} squared entries.
     */
    public int newerChunks(long key, int[] sent, int[] indexes, byte[][] data) {
        Tile tile = tiles.get(key);
        if (tile == null) {
            return 0;
        }
        int count = 0;
        synchronized (tile) {
            for (int i = 0; i < tile.chunks.length; i++) {
                if (tile.chunks[i] != null && tile.chunkVersions[i] > sent[i]) {
                    sent[i] = tile.chunkVersions[i];
                    indexes[count] = i;
                    data[count] = tile.chunks[i];
                    count++;
                }
            }
        }
        return count;
    }

    private static String name(long key) {
        return (int) (key >> 32) + "," + (int) key;
    }

    /** The tile as a compressed blob, or null if nothing has been drawn there yet. */
    public byte[] blob(int tx, int tz) throws IOException {
        Tile tile = tiles.get(key(tx, tz));
        if (tile == null) {
            return null;
        }
        synchronized (tile) {
            int version = tile.version;
            if (tile.blob == null || tile.blobVersion != version) {
                tile.blob = encode(tile);
                tile.blobVersion = version;
            }
            return tile.blob;
        }
    }

    private static byte[] encode(Tile tile) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            out.writeBytes("WOTG");
            out.writeByte(FORMAT);
            for (byte[] chunk : tile.chunks) {
                if (chunk == null) {
                    out.writeInt(0);
                } else {
                    out.writeInt(chunk.length);
                    out.write(chunk);
                }
            }
        }
        return bytes.toByteArray();
    }
}
