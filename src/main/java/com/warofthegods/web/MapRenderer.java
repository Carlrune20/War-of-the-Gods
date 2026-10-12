package com.warofthegods.web;

import java.util.ArrayDeque;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import com.warofthegods.Config;
import com.warofthegods.game.Game;
import com.warofthegods.world.MapArea;

/**
 * Reads a game's map into {@link MapTiles} on the server thread, within a time budget per tick.
 *
 * <p>
 * First it walks every chunk of the map and its margin once, row by row, loading (and so generating) each. A chunk is
 * read one row and one column behind the loading front: by then its neighbours are loaded, so it has been populated
 * with trees and lakes and nothing will be added to it. Chunks the walk loaded are let go again two rows behind it.
 * After that it keeps rereading loaded chunks in turn, and every chunk just before it unloads, so changes players
 * and later units make show up; chunks that are not loaded cannot change.
 */
final class MapRenderer {

    /** Chunks per tick reread for changes once the first walk is done. */
    private static final int REFRESH_CHUNKS_PER_TICK = 4;

    final WorldServer world;
    final int gameNumber;
    final MapTiles tiles;

    private final int minChunkX;
    private final int minChunkZ;
    private final int maxChunkX;
    private final int maxChunkZ;

    /** The next chunk the first walk loads; the walk covers one extra row and column so the last ones get read. */
    private int cursorX;
    private int cursorZ;
    private boolean walkDone;
    /** Chunks the walk loaded that were not loaded before, oldest first. */
    private final ArrayDeque<ChunkCoordIntPair> loadedByWalk = new ArrayDeque<>();
    private int refreshIndex;

    private static final int UNKNOWN = Integer.MIN_VALUE;
    private static final int[][] NEIGHBOURS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    private final int[] ground = new int[18 * 18];
    private final int[] bases = new int[256];
    private final int[] counts = new int[256];

    MapRenderer(WorldServer world, Game game) {
        this.world = world;
        this.gameNumber = game.getNumber();
        this.tiles = new MapTiles(gameNumber);
        MapArea area = MapArea.of(game, Config.mapMargin);
        minChunkX = (area.getMinX() - area.getMargin()) >> 4;
        minChunkZ = (area.getMinZ() - area.getMargin()) >> 4;
        maxChunkX = (area.getMaxX() + area.getMargin()) >> 4;
        maxChunkZ = (area.getMaxZ() + area.getMargin()) >> 4;
        cursorX = minChunkX;
        cursorZ = minChunkZ;
    }

    boolean isWalkDone() {
        return walkDone;
    }

    private boolean inRange(int chunkX, int chunkZ) {
        return chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
    }

    void tick(long budgetNanos) {
        long deadline = System.nanoTime() + budgetNanos;
        ChunkProviderServer provider = world.theChunkProviderServer;
        while (!walkDone && System.nanoTime() < deadline) {
            walkStep(provider);
        }
        refresh(provider);
    }

    private void walkStep(ChunkProviderServer provider) {
        if (!provider.chunkExists(cursorX, cursorZ)) {
            loadedByWalk.add(new ChunkCoordIntPair(cursorX, cursorZ));
        }
        provider.loadChunk(cursorX, cursorZ);

        int readX = cursorX - 1;
        int readZ = cursorZ - 1;
        if (inRange(readX, readZ)) {
            scan(provider.provideChunk(readX, readZ));
        }
        while (!loadedByWalk.isEmpty() && loadedByWalk.peek().chunkZPos <= cursorZ - 2) {
            ChunkCoordIntPair done = loadedByWalk.poll();
            if (!nearPlayer(done.chunkXPos, done.chunkZPos)) {
                provider.unloadChunksIfNotNearSpawn(done.chunkXPos, done.chunkZPos);
            }
        }

        cursorX++;
        if (cursorX > maxChunkX + 1) {
            cursorX = minChunkX;
            cursorZ++;
            if (cursorZ > maxChunkZ + 1) {
                walkDone = true;
                for (ChunkCoordIntPair done : loadedByWalk) {
                    if (!nearPlayer(done.chunkXPos, done.chunkZPos)) {
                        provider.unloadChunksIfNotNearSpawn(done.chunkXPos, done.chunkZPos);
                    }
                }
                loadedByWalk.clear();
            }
        }
    }

    /** Rereads a few loaded chunks, taking turns, to catch changes. */
    private void refresh(ChunkProviderServer provider) {
        List<?> loaded = provider.loadedChunks;
        int count = Math.min(REFRESH_CHUNKS_PER_TICK, loaded.size());
        for (int i = 0; i < count; i++) {
            if (refreshIndex >= loaded.size()) {
                refreshIndex = 0;
            }
            scan((Chunk) loaded.get(refreshIndex++));
        }
    }

    /** Whether a player might be looking at the chunk, so it should stay loaded. */
    private boolean nearPlayer(int chunkX, int chunkZ) {
        int range = MinecraftServer.getServer()
            .getConfigurationManager()
            .getViewDistance() + 1;
        for (Object entity : world.playerEntities) {
            EntityPlayer player = (EntityPlayer) entity;
            int playerChunkX = (int) Math.floor(player.posX) >> 4;
            int playerChunkZ = (int) Math.floor(player.posZ) >> 4;
            if (Math.abs(playerChunkX - chunkX) <= range && Math.abs(playerChunkZ - chunkZ) <= range) {
                return true;
            }
        }
        return false;
    }

    /** Reads the visible blocks of each column of a loaded chunk into the tiles. */
    void scan(Chunk chunk) {
        if (chunk == null || !inRange(chunk.xPosition, chunk.zPosition) || !chunk.isTerrainPopulated) {
            return;
        }
        ChunkProviderServer provider = world.theChunkProviderServer;
        for (int z = -1; z <= 16; z++) {
            for (int x = -1; x <= 16; x++) {
                ground[x + 1 + (z + 1) * 18] = groundAt(chunk, provider, x, z);
            }
        }
        int top = chunk.getTopFilledSegment() + 15;
        int total = 0;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int index = x + z * 16;
                int highest = top;
                while (highest >= 0 && chunk.getBlock(x, highest, z)
                    .getMaterial() == Material.air) {
                    highest--;
                }
                if (highest < 0) {
                    bases[index] = 0;
                    counts[index] = 0;
                    continue;
                }
                // Down to the lowest solid ground of this column and its four neighbours, so cliff sides show.
                int own = ground[x + 1 + (z + 1) * 18];
                int base = own;
                for (int[] offset : NEIGHBOURS) {
                    int neighbour = ground[x + 1 + offset[0] + (z + 1 + offset[1]) * 18];
                    base = Math.min(base, neighbour == UNKNOWN ? own - 4 : neighbour);
                }
                base = Math.max(0, Math.min(base, highest));
                base = Math.max(base, highest - 254);
                bases[index] = base;
                counts[index] = highest - base + 1;
                total += counts[index];
            }
        }
        byte[] data = new byte[512 + total * 2];
        int pos = 512;
        for (int index = 0; index < 256; index++) {
            data[index * 2] = (byte) bases[index];
            data[index * 2 + 1] = (byte) counts[index];
            int x = index & 15;
            int z = index >> 4;
            for (int y = bases[index]; y < bases[index] + counts[index]; y++) {
                int id = Block.getIdFromBlock(chunk.getBlock(x, y, z)) & 0xFFF;
                int value = id << 4 | (chunk.getBlockMetadata(x, y, z) & 15);
                data[pos++] = (byte) (value >> 8);
                data[pos++] = (byte) value;
            }
        }
        tiles.setChunk(chunk.xPosition, chunk.zPosition, data);
    }

    /** Solid ground height of a column given relative to the chunk, which may lie in a neighbouring chunk. */
    private static int groundAt(Chunk chunk, ChunkProviderServer provider, int x, int z) {
        if (x >= 0 && x < 16 && z >= 0 && z < 16) {
            return groundOf(chunk, x, z);
        }
        int chunkX = chunk.xPosition + (x >> 4);
        int chunkZ = chunk.zPosition + (z >> 4);
        if (!provider.chunkExists(chunkX, chunkZ)) {
            return UNKNOWN;
        }
        return groundOf(provider.provideChunk(chunkX, chunkZ), x & 15, z & 15);
    }

    /** The highest opaque full block of a column, or -1 if there is none. */
    private static int groundOf(Chunk chunk, int x, int z) {
        for (int y = chunk.getTopFilledSegment() + 15; y >= 0; y--) {
            Block block = chunk.getBlock(x, y, z);
            if (block.isOpaqueCube() && block.getMaterial()
                .isOpaque()) {
                return y;
            }
        }
        return -1;
    }
}
