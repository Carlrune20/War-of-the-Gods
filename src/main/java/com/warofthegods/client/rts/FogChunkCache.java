package com.warofthegods.client.rts;

import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;

/**
 * The world as a chunk being drawn sees it, with its light taken from the fog of war: the blocks are drawn as the team
 * has seen them, bright, dim or dark.
 */
final class FogChunkCache extends ChunkCache {

    private final FogOfWar fog;

    FogChunkCache(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, FogOfWar fog) {
        super(world, minX, minY, minZ, maxX, maxY, maxZ, 1);
        this.fog = fog;
    }

    @Override
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int minBlockLight) {
        return fog.light(x, z);
    }
}
