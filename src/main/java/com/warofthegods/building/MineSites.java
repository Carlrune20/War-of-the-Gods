package com.warofthegods.building;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;

import com.warofthegods.game.Game;
import com.warofthegods.game.GoldMine;
import com.warofthegods.game.MineKind;
import com.warofthegods.web.WebMap;

/**
 * Mining sites, laid out the way Old School RuneScape's surface mines look: bare brown ground, gravel and loose stone
 * with low rocks standing about it, each showing the ore in it. Some sites are an open patch of rocks, like
 * Rimmington's; some lie under a rocky ridge with ore in its face, like the mine north of Al Kharid; and some are
 * dug into a rocky mound with rocks round its foot, like the mine south-west of Varrock. Fence posts with torches,
 * a stretch of cart track and a crate or two stand among them.
 *
 * <p>
 * A site is built from its id and place, so it comes out the same every time: when it is mined out it is built again
 * with every ore turned to bare stone, as rocks go grey in RuneScape once mined.
 */
public final class MineSites {

    /** An open patch of rocks, like Rimmington's mine. */
    public static final int OPEN = 0;
    /** Rocks under a ridge with ore in its face, like the mine north of Al Kharid. */
    public static final int RIDGE = 1;
    /** A rocky mound with rocks round its foot, like the mine south-west of Varrock. */
    public static final int MOUND = 2;
    public static final int DESIGNS = 3;

    /** How high a site reaches above its ground, at most; blocks up to here are part of the site. */
    public static final int HEIGHT = 6;

    private MineSites() {}

    /** How many blocks square a site of a design is. */
    public static int sizeOf(int design) {
        return design == RIDGE ? 13 : 11;
    }

    /** The plan of a site before it is turned: y 0 is the ground, null is air. */
    private static final class Plan {

        final int size;
        final Block[] blocks;
        final byte[] metadata;
        final boolean depleted;

        Plan(int size, boolean depleted) {
            this.size = size;
            this.depleted = depleted;
            blocks = new Block[size * size * (HEIGHT + 1)];
            metadata = new byte[blocks.length];
        }

        private int index(int x, int y, int z) {
            return (y * size + z) * size + x;
        }

        boolean inside(int x, int y, int z) {
            return x >= 0 && x < size && z >= 0 && z < size && y >= 0 && y <= HEIGHT;
        }

        void set(int x, int y, int z, Block block, int meta) {
            if (inside(x, y, z)) {
                blocks[index(x, y, z)] = block;
                metadata[index(x, y, z)] = (byte) meta;
            }
        }

        /** An ore block, or bare stone once the site is mined out. */
        void ore(int x, int y, int z, Block ore) {
            set(x, y, z, depleted ? Blocks.stone : ore, 0);
        }

        Block get(int x, int y, int z) {
            return inside(x, y, z) ? blocks[index(x, y, z)] : null;
        }

        /** Whether nothing stands on the ground at x, z or round it. */
        boolean open(int x, int z, int margin) {
            for (int dx = -margin; dx <= margin; dx++) {
                for (int dz = -margin; dz <= margin; dz++) {
                    int px = x + dx;
                    int pz = z + dz;
                    if (px >= 0 && px < size && pz >= 0 && pz < size && get(px, 1, pz) != null) {
                        return false;
                    }
                }
            }
            return x >= 0 && x < size && z >= 0 && z < size;
        }
    }

    /** Puts a mining site into the world, or its mined out look. */
    public static void build(WorldServer world, Game game, GoldMine mine, boolean depleted) {
        int design = mine.design < 0 ? MOUND : mine.design % DESIGNS;
        Random random = new Random(mine.id * 341873128712L + mine.minX * 132897987541L + mine.minZ + 7);
        Plan plan = new Plan(mine.size, depleted);
        ground(plan, random);
        switch (design) {
            case RIDGE:
                ridge(plan, mine.kind, random);
                break;
            case MOUND:
                mound(plan, mine.kind, random);
                break;
            default:
                open(plan, mine.kind, random);
                break;
        }
        // The ridge stands on the side away from the middle of the map, so workers come at it from the front.
        int rotation = (MapFeatures.facingMiddle(game, (int) mine.getCenterX(), (int) mine.getCenterZ()) + 2) & 3;
        for (int x = 0; x < plan.size; x++) {
            for (int z = 0; z < plan.size; z++) {
                int[] at = turn(x, z, plan.size, rotation);
                int wx = mine.minX + at[0];
                int wz = mine.minZ + at[1];
                level(world, wx, wz, mine.groundY);
                for (int y = 0; y <= HEIGHT; y++) {
                    Block block = plan.get(x, y, z);
                    int meta = plan.metadata[plan.index(x, y, z)];
                    if (block == Blocks.rail && (rotation & 1) == 1) {
                        meta ^= 1;
                    }
                    if (block == Blocks.grass) {
                        block = Schematic.groundBlock(world);
                    }
                    if (block != null) {
                        world.setBlock(wx, mine.groundY + y, wz, block, meta, 2);
                    } else if (y > 0) {
                        world.setBlock(wx, mine.groundY + y, wz, Blocks.air, 0, 2);
                    }
                }
            }
        }
        WebMap.blocksChanged(world, mine.minX - 1, mine.minZ - 1, mine.getMaxX() + 1, mine.getMaxZ() + 1);
    }

    /** Where x, z of a square footprint ends up turned a number of quarter turns clockwise. */
    private static int[] turn(int x, int z, int size, int rotation) {
        for (int i = 0; i < (rotation & 3); i++) {
            int nx = size - 1 - z;
            z = x;
            x = nx;
        }
        return new int[] { x, z };
    }

    /** Fills the ground under a column down to solid ground, and clears what stands above the site. */
    private static void level(WorldServer world, int x, int z, int groundY) {
        for (int y = groundY - 1, depth = 0; y > 0 && depth < 16; y--, depth++) {
            if (Schematic.isGround(world.getBlock(x, y, z))) {
                break;
            }
            world.setBlock(x, y, z, Blocks.dirt, 0, 2);
        }
        for (int y = Math.min(255, groundY + HEIGHT + 32); y > groundY + HEIGHT; y--) {
            if (!world.isAirBlock(x, y, z)) {
                world.setBlock(x, y, z, Blocks.air, 0, 2);
            }
        }
    }

    // ---------------------------------------------------------------- pieces

    /** Bare brown ground with gravel and loose stone, going over to grass round the edge. */
    private static void ground(Plan plan, Random random) {
        int last = plan.size - 1;
        for (int x = 0; x < plan.size; x++) {
            for (int z = 0; z < plan.size; z++) {
                boolean edge = x == 0 || z == 0 || x == last || z == last;
                int roll = random.nextInt(20);
                if (edge && roll < 9) {
                    plan.set(x, 0, z, Blocks.grass, 0);
                } else if (roll < 10) {
                    // Coarse dirt, the brown of RuneScape's mines.
                    plan.set(x, 0, z, Blocks.dirt, 1);
                } else if (roll < 14) {
                    plan.set(x, 0, z, Blocks.gravel, 0);
                } else if (roll < 16) {
                    plan.set(x, 0, z, Blocks.dirt, 0);
                } else if (roll < 18) {
                    plan.set(x, 0, z, Blocks.cobblestone, 0);
                } else {
                    plan.set(x, 0, z, Blocks.stone, 0);
                }
            }
        }
    }

    /** A rock's ore: the site's best now and then, otherwise one of its lesser ores or plain rock. */
    private static Block rockOre(MineKind kind, Random random, boolean best) {
        if (best) {
            return kind.ore;
        }
        Block[] lesser = kind.lesserOres();
        return random.nextInt(4) == 0 ? Blocks.stone : lesser[random.nextInt(lesser.length)];
    }

    /**
     * A rock standing on the ground at x, z: a single block of ore, or a bigger one of stone with ore showing in it
     * and now and then a second layer.
     */
    private static void rock(Plan plan, int x, int z, Block ore, Random random) {
        if (random.nextInt(3) == 0) {
            plan.ore(x, 1, z, ore);
            return;
        }
        int sizeX = 1 + random.nextInt(2);
        int sizeZ = 1 + random.nextInt(2);
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                if (!plan.inside(x + dx, 1, z + dz)) {
                    continue;
                }
                if (random.nextInt(3) == 0) {
                    plan.set(x + dx, 1, z + dz, random.nextBoolean() ? Blocks.stone : Blocks.cobblestone, 0);
                } else {
                    plan.ore(x + dx, 1, z + dz, ore);
                }
            }
        }
        if (random.nextBoolean()) {
            plan.ore(x, 2, z, random.nextBoolean() ? ore : Blocks.stone);
        }
    }

    /** Rocks scattered over the open ground, a step apart so workers walk between them. */
    private static void scatterRocks(Plan plan, MineKind kind, int count, int best, Random random) {
        for (int tries = 0; tries < count * 12 && count > 0; tries++) {
            int x = 1 + random.nextInt(Math.max(1, plan.size - 2));
            int z = 1 + random.nextInt(Math.max(1, plan.size - 2));
            if (!plan.open(x, z, 1) || !plan.open(x + 1, z + 1, 1)) {
                continue;
            }
            rock(plan, x, z, rockOre(kind, random, best > 0), random);
            best--;
            count--;
        }
    }

    /** A fence post with a torch on it, if the ground there is free. */
    private static void torchPost(Plan plan, int x, int z) {
        if (plan.open(x, z, 0)) {
            plan.set(x, 1, z, Blocks.fence, 0);
            plan.set(x, 2, z, Blocks.torch, 5);
        }
    }

    /** A crate or two: a block of planks with a slab on it, or a lone log. */
    private static void crates(Plan plan, Random random) {
        for (int i = 0; i < 2; i++) {
            int x = 1 + random.nextInt(Math.max(1, plan.size - 2));
            int z = 1 + random.nextInt(Math.max(1, plan.size - 2));
            if (plan.open(x, z, 1)) {
                if (random.nextBoolean()) {
                    plan.set(x, 1, z, Blocks.planks, random.nextInt(2));
                } else {
                    plan.set(x, 1, z, Blocks.log, 0);
                }
            }
        }
    }

    /** A short stretch of cart track over open ground, ending where the rocks begin. */
    private static void track(Plan plan, int x, int z0, int z1) {
        for (int z = z0; z <= z1; z++) {
            if (!plan.open(x, z, 0)) {
                return;
            }
            plan.set(x, 0, z, Blocks.gravel, 0);
            plan.set(x, 1, z, Blocks.rail, 0);
        }
    }

    // ---------------------------------------------------------------- designs

    /** An open patch of rocks, like Rimmington's mine: rocks all over, a torch post at two corners. */
    private static void open(Plan plan, MineKind kind, Random random) {
        int last = plan.size - 1;
        torchPost(plan, 1, 1);
        torchPost(plan, last - 1, last - 1);
        int rocks = plan.size * plan.size / 10;
        scatterRocks(plan, kind, rocks, Math.max(2, rocks / 3), random);
        crates(plan, random);
    }

    /**
     * Rocks under a ridge, like the mine north of Al Kharid: a bank of rock along the back, highest in the middle,
     * with ore showing in its face, and rocks and a cart track on the open ground before it.
     */
    private static void ridge(Plan plan, MineKind kind, Random random) {
        int depth = Math.max(2, plan.size / 4);
        for (int x = 0; x < plan.size; x++) {
            // Higher in the middle, crumbling toward either end.
            double middle = 1 - Math.abs(x - (plan.size - 1) / 2.0) / (plan.size / 2.0);
            int top = Math.max(1, Math.min(HEIGHT - 1, (int) Math.round(1 + middle * 3.5 + random.nextInt(2))));
            for (int z = 0; z < depth; z++) {
                int height = Math.max(1, top - z);
                for (int y = 1; y <= height; y++) {
                    boolean face = z == depth - 1 || y == height;
                    int roll = random.nextInt(10);
                    if (face && roll < 3) {
                        plan.ore(x, y, z, rockOre(kind, random, roll == 0));
                    } else if (roll < 6) {
                        plan.set(x, y, z, Blocks.stone, 0);
                    } else if (roll < 8) {
                        plan.set(x, y, z, Blocks.cobblestone, 0);
                    } else if (roll < 9) {
                        plan.set(x, y, z, Blocks.mossy_cobblestone, 0);
                    } else {
                        plan.set(x, y, z, Blocks.gravel, 0);
                    }
                }
                if (z == 0 && random.nextInt(3) == 0) {
                    plan.set(x, height + 1, z, Blocks.tallgrass, 1);
                }
            }
        }
        // The richest seam shows low in the face, where workers reach it.
        int seam = 2 + random.nextInt(Math.max(1, plan.size - 4));
        for (int x = seam - 1; x <= seam + 1; x++) {
            plan.ore(x, 1, depth - 1, kind.ore);
        }
        track(plan, plan.size - 3, depth, plan.size - 2);
        torchPost(plan, 1, plan.size - 2);
        torchPost(plan, plan.size - 2, depth + 1);
        int rocks = plan.size * plan.size / 14;
        scatterRocks(plan, kind, rocks, Math.max(2, rocks / 3), random);
        crates(plan, random);
    }

    /**
     * A rocky mound, like the mine south-west of Varrock: rock heaped up in the middle with ore showing on it, and a
     * ring of rocks round its foot.
     */
    private static void mound(Plan plan, MineKind kind, Random random) {
        double center = (plan.size - 1) / 2.0;
        double radius = plan.size / 2.0 - 2.5;
        for (int x = 0; x < plan.size; x++) {
            for (int z = 0; z < plan.size; z++) {
                double distance = Math.sqrt((x - center) * (x - center) + (z - center) * (z - center));
                int height = (int) Math.round((radius - distance) * 1.4 + random.nextDouble() * 0.8);
                height = Math.min(HEIGHT - 1, height);
                for (int y = 1; y <= height; y++) {
                    int roll = random.nextInt(10);
                    if (y == height && roll < 4) {
                        plan.ore(x, y, z, rockOre(kind, random, roll == 0));
                    } else if (roll < 6) {
                        plan.set(x, y, z, Blocks.stone, 0);
                    } else if (roll < 9) {
                        plan.set(x, y, z, Blocks.cobblestone, 0);
                    } else {
                        plan.set(x, y, z, Blocks.mossy_cobblestone, 0);
                    }
                }
            }
        }
        int c = (int) Math.round(center);
        plan.ore(c, Math.max(1, (int) Math.round(radius * 1.4)), c, kind.ore);
        int last = plan.size - 1;
        torchPost(plan, 0, 0);
        torchPost(plan, last, last);
        // Rocks round the foot of the mound, every few steps round the edge.
        for (int i = 0; i < 4 * last; i += 3 + random.nextInt(2)) {
            int side = i / last;
            int along = i % last;
            int x = side == 0 ? along : side == 1 ? last - 1 : side == 2 ? last - along : 1;
            int z = side == 0 ? 1 : side == 1 ? along : side == 2 ? last - 1 : last - along;
            if (plan.open(x, z, 0) && random.nextInt(3) != 0) {
                plan.ore(x, 1, z, rockOre(kind, random, random.nextInt(3) == 0));
            }
        }
    }
}
