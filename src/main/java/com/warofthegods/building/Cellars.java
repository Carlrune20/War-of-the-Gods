package com.warofthegods.building;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;

import com.warofthegods.game.Ruin;

/**
 * What lies under some of the ruins: an old well that goes down into a small cave, or a cellar under a tower, chapel,
 * manor, farmstead, hut or old fort, or a crypt under a graveyard. Their walls are thick with coal and iron ore, and
 * now and then a diamond or two, for players to dig out and sell at their town center for gold, or smelt and make
 * armor of. A ladder leads down into each.
 *
 * <p>
 * Dug as the world is generated, along with the ruin, and kept inside the chunks the ruin was laid out in, which are
 * already there.
 */
final class Cellars {

    /** How deep a cellar's floor lies below the ground. */
    private static final int CELLAR_DEPTH = 6;
    /** How deep a cave's floor lies below the ground. */
    private static final int CAVE_DEPTH = 11;

    private Cellars() {}

    /**
     * Maybe digs something under a ruin just built, in the two chunks square from baseX, baseZ: wells mostly lead
     * down to a cave, bigger ruins and graveyards now and then have a cellar or crypt.
     */
    static void dig(WorldServer world, Ruin ruin, Ruins.Design design, Random random, int baseX, int baseZ) {
        int chance;
        switch (design) {
            case WELL:
                chance = 70;
                break;
            case TOWER:
            case CHAPEL:
            case MANOR:
            case FARMSTEAD:
            case HUT:
            case WALLS:
            case GRAVEYARD:
                chance = 30;
                break;
            default:
                return;
        }
        if (random.nextInt(100) >= chance) {
            return;
        }
        Random dig = new Random(random.nextLong());
        if (ruin.groundY - CAVE_DEPTH - 4 < 5) {
            return;
        }
        if (design == Ruins.Design.WELL) {
            cave(world, ruin, dig, baseX, baseZ);
        } else {
            cellar(world, ruin, dig);
        }
    }

    // ---------------------------------------------------------------- cellars

    /** A cellar of old stone under the whole ruin, with ore in its walls and heaped on its floor. */
    private static void cellar(WorldServer world, Ruin ruin, Random random) {
        int x0 = ruin.minX;
        int z0 = ruin.minZ;
        int x1 = ruin.minX + ruin.sizeX - 1;
        int z1 = ruin.minZ + ruin.sizeZ - 1;
        int ground = ruin.groundY;
        int floor = ground - CELLAR_DEPTH;
        int[] entry = cellarEntry(world, x0, z0, x1, z1, ground);
        if (entry == null) {
            return;
        }
        List<int[]> walls = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                for (int y = floor; y < ground; y++) {
                    if (edge || y == floor || y == ground - 1) {
                        oldStone(world, x, y, z, random);
                        if (edge && y > floor && y < ground - 1) {
                            walls.add(new int[] { x, y, z });
                        }
                    } else {
                        world.setBlock(x, y, z, Blocks.air, 0, 2);
                    }
                }
                if (!edge && random.nextInt(4) == 0) {
                    world.setBlock(x, floor, z, Blocks.gravel, 0, 2);
                }
            }
        }
        // Heaps of ore on the floor, as if left by the miners who stored it here.
        List<int[]> heaps = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int x = x0 + 1 + random.nextInt(Math.max(1, x1 - x0 - 1));
            int z = z0 + 1 + random.nextInt(Math.max(1, z1 - z0 - 1));
            if (Math.abs(x - entry[0]) + Math.abs(z - entry[1]) > 1) {
                heaps.add(new int[] { x, floor + 1, z });
            }
        }
        seedOres(world, walls, heaps, random);
        ladder(world, entry[0], entry[1], entry[2], floor + 1, ground);
        int tx = entry[0] == x0 + 1 ? x1 - 1 : x0 + 1;
        int tz = entry[1] == z0 + 1 ? z1 - 1 : z0 + 1;
        if (world.isAirBlock(tx, floor + 1, tz)) {
            world.setBlock(tx, floor + 1, tz, Blocks.torch, 5, 2);
        }
        for (int i = 0; i < 3; i++) {
            int x = x0 + 1 + random.nextInt(Math.max(1, x1 - x0 - 1));
            int z = z0 + 1 + random.nextInt(Math.max(1, z1 - z0 - 1));
            if (world.isAirBlock(x, ground - 2, z)) {
                world.setBlock(x, ground - 2, z, Blocks.web, 0, 2);
            }
        }
    }

    /**
     * Where a ladder can come down into a cellar: an open spot in the ruin just inside one of its walls, as x, z and
     * the ladder's metadata, which says which side it hangs on; null if there is none.
     */
    private static int[] cellarEntry(WorldServer world, int x0, int z0, int x1, int z1, int ground) {
        // Inside the north, south, west and east walls, hanging on that wall.
        int[][] sides = { { 0, -1, 3 }, { 0, 1, 2 }, { -1, 0, 5 }, { 1, 0, 4 } };
        for (int[] side : sides) {
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                for (int z = z0 + 1; z <= z1 - 1; z++) {
                    boolean along = side[1] == -1 ? z == z0 + 1
                        : side[1] == 1 ? z == z1 - 1 : side[0] == -1 ? x == x0 + 1 : x == x1 - 1;
                    if (along && open(world, x, ground + 1, z)
                        && open(world, x, ground + 2, z)
                        && world.getBlock(x + side[0], ground, z + side[1])
                            .isNormalCube()) {
                        return new int[] { x, z, side[2] };
                    }
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- caves

    /** A small cave of a few hollows run together, under an old well that has gone dry, reached down the well. */
    private static void cave(WorldServer world, Ruin ruin, Random random, int baseX, int baseZ) {
        int cx = ruin.minX + ruin.sizeX / 2;
        int cz = ruin.minZ + ruin.sizeZ / 2;
        int ground = ruin.groundY;
        int floor = ground - CAVE_DEPTH;
        Set<Long> carved = new HashSet<>();
        double x = cx;
        double z = cz;
        int hollows = 4 + random.nextInt(3);
        for (int i = 0; i < hollows; i++) {
            double across = 2.2 + random.nextDouble() * 1.4;
            double high = 1.6 + random.nextDouble() * 0.6;
            hollow(world, carved, x, floor + high, z, across, high, baseX, baseZ);
            x = clamp(x + random.nextInt(9) - 4, baseX + 5, baseX + 26);
            z = clamp(z + random.nextInt(9) - 4, baseZ + 5, baseZ + 26);
        }
        // The shaft down the well, with a ladder on a column of rock all the way down.
        for (int y = floor + 1; y <= ground; y++) {
            if (y < ground && !world.getBlock(cx, y, cz - 1)
                .isNormalCube()) {
                world.setBlock(cx, y, cz - 1, Blocks.stone, 0, 2);
            }
            carved.add(key(cx, y, cz));
        }
        ladder(world, cx, cz, 3, floor + 1, ground);
        // Walls of rock, not dirt, with ore in them.
        List<int[]> walls = new ArrayList<>();
        int[][] around = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        Set<Long> seen = new HashSet<>();
        for (long cell : carved) {
            int px = unpackX(cell);
            int py = unpackY(cell);
            int pz = unpackZ(cell);
            for (int[] side : around) {
                int wx = px + side[0];
                int wy = py + side[1];
                int wz = pz + side[2];
                long wall = key(wx, wy, wz);
                if (carved.contains(wall) || !seen.add(wall) || !inside(wx, wz, baseX, baseZ)) {
                    continue;
                }
                Block block = world.getBlock(wx, wy, wz);
                if (block == Blocks.dirt || block == Blocks.gravel || block == Blocks.clay) {
                    world.setBlock(wx, wy, wz, Blocks.stone, 0, 2);
                    block = Blocks.stone;
                }
                if (block == Blocks.stone && wy < ground - 1 && !(wx == cx && wz == cz - 1)) {
                    walls.add(new int[] { wx, wy, wz });
                }
            }
        }
        seedOres(world, walls, new ArrayList<int[]>(), random);
    }

    /** Hollows out a squashed ball of rock, where it is under ground and inside the chunks to hand. */
    private static void hollow(WorldServer world, Set<Long> carved, double cx, double cy, double cz, double across,
        double high, int baseX, int baseZ) {
        for (int x = (int) Math.floor(cx - across); x <= (int) Math.ceil(cx + across); x++) {
            for (int z = (int) Math.floor(cz - across); z <= (int) Math.ceil(cz + across); z++) {
                if (!inside(x, z, baseX, baseZ)) {
                    continue;
                }
                // Keep a few blocks of roof under the lowest ground of the column.
                int roof = world.getTopSolidOrLiquidBlock(x, z) - 5;
                for (int y = (int) Math.floor(cy - high); y <= (int) Math.ceil(cy + high); y++) {
                    double dx = (x + 0.5 - cx) / across;
                    double dy = (y + 0.5 - cy) / high;
                    double dz = (z + 0.5 - cz) / across;
                    if (dx * dx + dy * dy + dz * dz >= 1 || y > roof || y < 4) {
                        continue;
                    }
                    Block block = world.getBlock(x, y, z);
                    if (block == Blocks.bedrock || block.getMaterial()
                        .isLiquid()) {
                        continue;
                    }
                    world.setBlock(x, y, z, Blocks.air, 0, 2);
                    carved.add(key(x, y, z));
                }
            }
        }
    }

    // ---------------------------------------------------------------- shared

    /**
     * Sets ore into the walls and onto the heaps: plenty of coal and iron, and in about one in four a diamond or a
     * few, low down where they are found last.
     */
    private static void seedOres(WorldServer world, List<int[]> walls, List<int[]> heaps, Random random) {
        Collections.shuffle(walls, random);
        int coal = 22 + random.nextInt(12);
        int iron = 14 + random.nextInt(10);
        int diamonds = random.nextInt(4) == 0 ? 1 + random.nextInt(3) : 0;
        for (int[] heap : heaps) {
            Block ore = random.nextBoolean() ? Blocks.coal_ore : Blocks.iron_ore;
            world.setBlock(heap[0], heap[1], heap[2], ore, 0, 2);
            if (ore == Blocks.coal_ore) {
                coal--;
            } else {
                iron--;
            }
        }
        int i = 0;
        for (; i < walls.size() && (coal > 0 || iron > 0); i++) {
            int[] at = walls.get(i);
            Block ore = coal > 0 && (iron <= 0 || random.nextInt(5) < 3) ? Blocks.coal_ore : Blocks.iron_ore;
            world.setBlock(at[0], at[1], at[2], ore, 0, 2);
            if (ore == Blocks.coal_ore) {
                coal--;
            } else {
                iron--;
            }
        }
        // Diamonds go in the lowest of the walls left.
        List<int[]> rest = new ArrayList<>(walls.subList(i, walls.size()));
        Collections.sort(rest, (a, b) -> Integer.compare(a[1], b[1]));
        for (int k = 0; k < diamonds && k < rest.size(); k++) {
            int[] at = rest.get(k);
            world.setBlock(at[0], at[1], at[2], Blocks.diamond_ore, 0, 2);
        }
    }

    /** A ladder at x, z from bottom up to the ground, through a hole in it, hanging on the side meta says. */
    private static void ladder(WorldServer world, int x, int z, int meta, int bottom, int ground) {
        for (int y = bottom; y <= ground; y++) {
            world.setBlock(x, y, z, Blocks.ladder, meta, 2);
        }
        for (int y = ground + 1; y <= ground + 2; y++) {
            if (!world.isAirBlock(x, y, z)) {
                world.setBlock(x, y, z, Blocks.air, 0, 2);
            }
        }
    }

    /** A block of old stone, mostly stone bricks, some mossy, cracked or cobblestone. */
    private static void oldStone(WorldServer world, int x, int y, int z, Random random) {
        int roll = random.nextInt(10);
        if (roll < 5) {
            world.setBlock(x, y, z, Blocks.stonebrick, 0, 2);
        } else if (roll < 7) {
            world.setBlock(x, y, z, Blocks.stonebrick, roll == 5 ? 1 : 2, 2);
        } else if (roll < 9) {
            world.setBlock(x, y, z, Blocks.cobblestone, 0, 2);
        } else {
            world.setBlock(x, y, z, Blocks.mossy_cobblestone, 0, 2);
        }
    }

    /** Whether a block is air or something a ladder's way down can clear, such as tall grass. */
    private static boolean open(WorldServer world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        Material material = block.getMaterial();
        return block == Blocks.air || material == Material.plants || material == Material.vine;
    }

    private static boolean inside(int x, int z, int baseX, int baseZ) {
        return x > baseX && x < baseX + 31 && z > baseZ && z < baseZ + 31;
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + (1 << 25)) << 34) | ((long) y << 26) | (z + (1 << 25));
    }

    private static int unpackX(long key) {
        return (int) (key >>> 34) - (1 << 25);
    }

    private static int unpackY(long key) {
        return (int) (key >>> 26 & 0xFF);
    }

    private static int unpackZ(long key) {
        return (int) (key & ((1 << 26) - 1)) - (1 << 25);
    }
}
