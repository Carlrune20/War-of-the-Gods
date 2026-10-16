package com.warofthegods.unit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.warofthegods.Config;
import com.warofthegods.game.Game;
import com.warofthegods.game.Tree;
import com.warofthegods.web.WebMap;

/**
 * Trees workers chop for wood. A tree is the logs joined to the foot of a trunk; it holds wood by its number of
 * logs, within limits, and once the wood is all chopped out it falls: its logs and leaves are gone.
 */
public final class Trees {

    /** How far from the foot of the trunk a tree's logs may reach, as with big oaks and acacias. */
    private static final int REACH = 6;
    private static final int MAX_LOGS = 160;

    private Trees() {}

    public static boolean isLog(Block block) {
        return block == Blocks.log || block == Blocks.log2;
    }

    private static boolean isLeaves(Block block) {
        return block.getMaterial() == Material.leaves;
    }

    /**
     * The foot of the trunk of the tree nearest a point where someone clicked, within a couple of blocks across and
     * among the leaves above it, as x, y, z; null if there is no tree there. Logs on a building are not trees.
     */
    public static int[] findTree(World world, Game game, double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        int[] best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 4; dy >= -10; dy--) {
                    int cx = bx + dx;
                    int cy = by + dy;
                    int cz = bz + dz;
                    if (cy < 1 || cy > 255
                        || !isLog(world.getBlock(cx, cy, cz))
                        || game.getBuildingAt(cx + 0.5, cz + 0.5) != null) {
                        continue;
                    }
                    int[] foot = footOf(world, cx, cy, cz);
                    if (foot == null) {
                        continue;
                    }
                    double distance = dx * dx + dz * dz + Math.abs(dy) * 0.1;
                    if (distance < bestDistance) {
                        best = foot;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    /** Follows a log down its trunk to the log standing on the ground; null if it does not stand on ground. */
    private static int[] footOf(World world, int x, int y, int z) {
        for (int i = 0; i < 40 && y > 1; i++) {
            if (isLog(world.getBlock(x, y - 1, z))) {
                y--;
                continue;
            }
            // A branch: step down and sideways onto the log it grows from.
            int[] below = null;
            for (int dx = -1; dx <= 1 && below == null; dx++) {
                for (int dz = -1; dz <= 1 && below == null; dz++) {
                    if ((dx != 0 || dz != 0) && isLog(world.getBlock(x + dx, y - 1, z + dz))) {
                        below = new int[] { x + dx, z + dz };
                    }
                }
            }
            if (below == null) {
                Block ground = world.getBlock(x, y - 1, z);
                return ground.getMaterial()
                    .isSolid() && !isLeaves(ground) ? new int[] { x, y, z } : null;
            }
            x = below[0];
            z = below[1];
            y--;
        }
        return null;
    }

    /** Every log of the tree with its trunk's foot at x, y, z. */
    public static List<int[]> logs(World world, int x, int y, int z) {
        List<int[]> logs = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] { x, y, z });
        seen.add(Tree.key(x, y, z));
        while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
            int[] log = queue.poll();
            if (!isLog(world.getBlock(log[0], log[1], log[2]))) {
                continue;
            }
            logs.add(log);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = log[0] + dx;
                        int ny = log[1] + dy;
                        int nz = log[2] + dz;
                        if (Math.abs(nx - x) > REACH || Math.abs(nz - z) > REACH || ny > 255) {
                            continue;
                        }
                        if (seen.add(Tree.key(nx, ny, nz)) && isLog(world.getBlock(nx, ny, nz))) {
                            queue.add(new int[] { nx, ny, nz });
                        }
                    }
                }
            }
        }
        return logs;
    }

    /** The tree with its trunk's foot at x, y, z, as the game tracks it, with its wood counted the first time. */
    public static Tree track(World world, Game game, int x, int y, int z) {
        Tree tree = game.getTree(x, y, z);
        if (tree != null) {
            return tree;
        }
        List<int[]> logs = logs(world, x, y, z);
        int top = y;
        for (int[] log : logs) {
            top = Math.max(top, log[1]);
        }
        int wood = Math.max(Config.treeWoodMin, Math.min(Config.treeWoodMax, logs.size() * Config.woodPerLog));
        return game.trackTree(x, y, z, top, wood);
    }

    /** Whether the tree still stands: its foot is a log. */
    public static boolean stands(World world, Tree tree) {
        return isLog(world.getBlock(tree.x, tree.y, tree.z));
    }

    /** Fells a tree that has no wood left: its logs go, and the leaves around them. */
    public static void fell(World world, Game game, Tree tree) {
        List<int[]> logs = logs(world, tree.x, tree.y, tree.z);
        int minX = tree.x;
        int maxX = tree.x;
        int minZ = tree.z;
        int maxZ = tree.z;
        Set<Long> leaves = new HashSet<>();
        for (int[] log : logs) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -1; dy <= 3; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        int lx = log[0] + dx;
                        int ly = log[1] + dy;
                        int lz = log[2] + dz;
                        // Leaves players placed are marked so; leave those.
                        if (ly <= 255 && isLeaves(world.getBlock(lx, ly, lz))
                            && (world.getBlockMetadata(lx, ly, lz) & 4) == 0
                            && leaves.add(Tree.key(lx, ly, lz))) {
                            world.setBlock(lx, ly, lz, Blocks.air, 0, 2);
                            minX = Math.min(minX, lx);
                            maxX = Math.max(maxX, lx);
                            minZ = Math.min(minZ, lz);
                            maxZ = Math.max(maxZ, lz);
                        }
                    }
                }
            }
        }
        // Top down, so nothing drops.
        logs.sort((a, b) -> Integer.compare(b[1], a[1]));
        for (int[] log : logs) {
            world.setBlock(log[0], log[1], log[2], Blocks.air, 0, 2);
        }
        world.playAuxSFX(2001, tree.x, tree.y, tree.z, Block.getIdFromBlock(Blocks.log));
        game.removeTree(tree);
        WebMap.blocksChanged(world, minX - 1, minZ - 1, maxX + 1, maxZ + 1);
    }

    /**
     * The foot of the nearest standing tree within range of x, z; null if none. Each column is looked down from its
     * top, so trees on hills and in hollows are found as well as those level with x, z.
     */
    public static int[] nearest(World world, Game game, double x, double z, int range) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int[] best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                double distance = dx * dx + dz * dz;
                if (distance >= bestDistance || distance > range * range) {
                    continue;
                }
                int cx = bx + dx;
                int cz = bz + dz;
                if (!game.isInside(cx, cz) || game.getBuildingAt(cx + 0.5, cz + 0.5) != null) {
                    continue;
                }
                int[] foot = trunkFoot(world, cx, cz);
                if (foot != null) {
                    best = foot;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /**
     * The foot of a trunk in the column, as x, y, z: down from the top through leaves, logs and air to a log standing
     * on the ground. Null if the column reaches the ground without one.
     */
    private static int[] trunkFoot(World world, int x, int z) {
        int top = world.getChunkFromBlockCoords(x, z)
            .getHeightValue(x & 15, z & 15) - 1;
        for (int y = Math.min(255, top); y > 1; y--) {
            Block block = world.getBlock(x, y, z);
            if (isLog(block)) {
                Block below = world.getBlock(x, y - 1, z);
                if (!isLog(below) && below.getMaterial()
                    .isSolid() && !isLeaves(below)) {
                    return new int[] { x, y, z };
                }
            } else if (!isLeaves(block) && block.getMaterial()
                .isSolid()) {
                    return null;
                }
        }
        return null;
    }
}
