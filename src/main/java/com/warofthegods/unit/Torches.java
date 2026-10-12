package com.warofthegods.unit;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.warofthegods.game.Building;
import com.warofthegods.game.Game;

/**
 * Where workers put torches to light up an area, so that no mob can spawn there at night. Mobs need a block light
 * below 8; a torch gives 14, one less per block walked away from it, so a torch lights everything within 6 steps.
 */
public final class Torches {

    /** Most blocks across an area to light may be, each way. */
    public static final int MAX_SIDE = 64;
    /** Below this block light, mobs can spawn. */
    private static final int DARK = 8;
    /** Steps from a torch that are still lit above DARK. */
    private static final int REACH = 14 - DARK;

    private Torches() {}

    /**
     * The spots in the area from x1, z1 to x2, z2 to put torches on, in an order a worker can walk: rows back and
     * forth, a torch wherever the ones before leave it dark. Buildings and the block around them are left alone.
     */
    public static List<int[]> plan(World world, Game game, int x1, int z1, int x2, int z2) {
        int minX = Math.max(Math.min(x1, x2), game.getMinX());
        int maxX = Math.min(Math.max(x1, x2), game.getMaxX());
        int minZ = Math.max(Math.min(z1, z2), game.getMinZ());
        int maxZ = Math.min(Math.max(z1, z2), game.getMaxZ());
        maxX = Math.min(maxX, minX + MAX_SIDE - 1);
        maxZ = Math.min(maxZ, minZ + MAX_SIDE - 1);
        List<int[]> spots = new ArrayList<>();
        for (int z = minZ; z <= maxZ; z++) {
            boolean back = (z - minZ) % 2 == 1;
            for (int i = 0; i <= maxX - minX; i++) {
                int x = back ? maxX - i : minX + i;
                int y = world.getTopSolidOrLiquidBlock(x, z);
                if (needsLight(world, game, x, y, z) && !isLit(spots, x, y, z)) {
                    spots.add(new int[] { x, y, z });
                }
            }
        }
        return spots;
    }

    /** Whether a mob could spawn standing at x, y, z for want of light, outside any building. */
    private static boolean needsLight(World world, Game game, int x, int y, int z) {
        if (y <= 0 || y >= 255 || !canHold(world, x, y, z)) {
            return false;
        }
        if (world.getSavedLightValue(EnumSkyBlock.Block, x, y, z) >= DARK) {
            return false;
        }
        for (Building building : game.getBuildings()) {
            if (building.covers(x + 0.5, z + 0.5, 1)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a torch can stand at x, y, z: in the air, on top of solid ground, out of water. */
    static boolean canHold(World world, int x, int y, int z) {
        return world.isAirBlock(x, y, z) && world.getBlock(x, y - 1, z)
            .isSideSolid(world, x, y - 1, z, ForgeDirection.UP)
            && world.getBlock(x, y - 1, z)
                .getMaterial() != Material.leaves
            && Blocks.torch.canPlaceBlockAt(world, x, y, z);
    }

    private static boolean isLit(List<int[]> torches, int x, int y, int z) {
        for (int[] torch : torches) {
            if (Math.abs(torch[0] - x) + Math.abs(torch[1] - y) + Math.abs(torch[2] - z) <= REACH) {
                return true;
            }
        }
        return false;
    }

    /** Whether the spot still wants a torch when the worker gets there. */
    static boolean stillDark(World world, int x, int y, int z) {
        return canHold(world, x, y, z) && world.getSavedLightValue(EnumSkyBlock.Block, x, y, z) < DARK;
    }
}
