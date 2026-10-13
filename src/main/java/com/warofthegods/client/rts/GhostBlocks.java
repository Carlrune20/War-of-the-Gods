package com.warofthegods.client.rts;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

import com.warofthegods.building.Schematic;

/**
 * The blocks of a building being placed, where it would stand, for drawing it as it will look: Minecraft's block
 * renderer reads them from here, and finds air everywhere else, fully lit.
 */
final class GhostBlocks implements IBlockAccess {

    /** Block id and metadata by position, as id << 4 | meta. */
    private final Map<Long, Integer> blocks = new HashMap<>();
    private final int[][] positions;

    GhostBlocks(Schematic schematic, int rotation, int minX, int minZ, int groundY) {
        int baseY = groundY - schematic.ground;
        for (int y = schematic.ground; y < schematic.height; y++) {
            for (int z = 0; z < schematic.length; z++) {
                for (int x = 0; x < schematic.width; x++) {
                    int id = schematic.blockId(x, y, z);
                    if (id == 0) {
                        continue;
                    }
                    int[] at = schematic.turned(x, z, rotation);
                    blocks.put(
                        key(minX + at[0], baseY + y, minZ + at[1]),
                        id << 4 | schematic.turnedMeta(x, y, z, rotation));
                }
            }
        }
        positions = new int[blocks.size()][];
        int i = 0;
        for (Long position : blocks.keySet()) {
            positions[i++] = unkey(position);
        }
    }

    /** Every block's position, as x, y, z. */
    int[][] positions() {
        return positions;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 30000000) << 34) | ((long) (z + 30000000) << 8) | (y & 255);
    }

    private static int[] unkey(long key) {
        return new int[] { (int) (key >>> 34) - 30000000, (int) (key & 255),
            (int) ((key >>> 8) & 0x3FFFFFFL) - 30000000 };
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        Integer value = blocks.get(key(x, y, z));
        return value == null ? Blocks.air : Block.getBlockById(value >> 4);
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        Integer value = blocks.get(key(x, y, z));
        return value == null ? 0 : value & 15;
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        return null;
    }

    @Override
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int light) {
        return 15 << 20 | 15 << 4;
    }

    @Override
    public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
        return 0;
    }

    @Override
    public boolean isAirBlock(int x, int y, int z) {
        return !blocks.containsKey(key(x, y, z));
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld != null ? mc.theWorld.getBiomeGenForCoords(x, z) : BiomeGenBase.plains;
    }

    @Override
    public int getHeight() {
        return 256;
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return false;
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean fallback) {
        Block block = getBlock(x, y, z);
        return block != Blocks.air && block.isSideSolid(this, x, y, z, side);
    }
}
