package com.warofthegods.web;

import java.util.ArrayDeque;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
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
 * Draws a game's map into {@link MapTiles} on the server thread, within a time budget per tick.
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
    private static final int REFRESH_CHUNKS_PER_TICK = 8;

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

    private final int[] colors = new int[256];
    private final short[] heights = new short[256];

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

    /** Reads the top block of each column of a loaded chunk into the tiles. */
    void scan(Chunk chunk) {
        if (chunk == null || !inRange(chunk.xPosition, chunk.zPosition) || !chunk.isTerrainPopulated) {
            return;
        }
        int top = chunk.getTopFilledSegment() + 15;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                scanColumn(chunk, x, z, top);
            }
        }
        tiles.setChunk(chunk.xPosition, chunk.zPosition, colors, heights);
    }

    private void scanColumn(Chunk chunk, int x, int z, int top) {
        int index = x + z * 16;
        for (int y = top; y >= 0; y--) {
            Block block = chunk.getBlock(x, y, z);
            if (block.getMaterial() == Material.air) {
                continue;
            }
            MapColor mapColor = block.getMapColor(chunk.getBlockMetadata(x, y, z));
            if (mapColor == null || mapColor == MapColor.airColor) {
                continue;
            }
            if (block.getMaterial() == Material.water) {
                int depth = 1;
                while (y - depth > 0 && chunk.getBlock(x, y - depth, z)
                    .getMaterial() == Material.water) {
                    depth++;
                }
                // Deeper water is darker, as on vanilla maps.
                int shade = Math.max(140, 245 - depth * 7);
                colors[index] = MapTiles.FINAL_COLOR | MapTiles.scale(MapColor.waterColor.colorValue, shade);
            } else {
                colors[index] = MapTiles.SHADE_BY_HEIGHT | (mapColor.colorValue & 0xFFFFFF);
            }
            heights[index] = (short) y;
            return;
        }
        colors[index] = 0;
        heights[index] = 0;
    }
}
