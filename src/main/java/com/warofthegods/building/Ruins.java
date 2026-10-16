package com.warofthegods.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;

import com.warofthegods.Config;
import com.warofthegods.game.Building;
import com.warofthegods.game.Game;
import com.warofthegods.game.GoldMine;
import com.warofthegods.game.Ruin;
import com.warofthegods.game.Settlement;
import com.warofthegods.web.WebMap;
import com.warofthegods.world.ChunkProviderRts;
import com.warofthegods.world.GameWorld;
import com.warofthegods.world.Misthalin;

/**
 * Old ruins of Misthalin lying about the map, laid out as the world is generated, at random by the map's seed, so they
 * are come upon rather than known: crumbled walls like those round Varrock, broken towers like Draynor's, chapels like
 * Paterdomus, a fallen manor, abandoned Lumbridge farmsteads and wooden huts, graveyards, wells, arches, colonnades,
 * stretches of old wall and toppled statues. Sometimes a few small ones lie together. They are built in stone gone
 * mossy and cracked or in rotten wood, low and full of gaps, so units walk through and round them, and keep away from
 * where teams start, town centers, settlements, gold mines, the river and its bridges. Some are haunted: cobwebs hang
 * in them and spiders lurk there, or zombies and skeletons walk round them (see {@code Mobs}) until someone builds
 * nearby. Players can break them down and build over them.
 */
public final class Ruins {

    /** The kinds of ruin, with their footprints before turning, whether they are small and how common they are. */
    enum Design {

        WALLS("crumbled walls", 11, 9, false, 3),
        TOWER("broken tower", 9, 9, false, 3),
        CHAPEL("ruined chapel", 9, 13, false, 2),
        FARMSTEAD("abandoned farmstead", 9, 9, false, 2),
        MANOR("fallen manor", 13, 11, false, 1),
        HUT("abandoned hut", 5, 6, true, 4),
        GRAVEYARD("old graveyard", 9, 7, true, 2),
        ARCH("broken arch", 7, 3, true, 2),
        WELL("old well", 5, 5, true, 2),
        PILLARS("fallen colonnade", 9, 7, true, 2),
        WALL_STRETCH("stretch of old wall", 13, 3, true, 2),
        STATUE("toppled statue", 5, 5, true, 1);

        final String name;
        final int width;
        final int length;
        final boolean small;
        final int weight;

        Design(String name, int width, int length, boolean small, int weight) {
            this.name = name;
            this.width = width;
            this.length = length;
            this.small = small;
            this.weight = weight;
        }
    }

    /** Blocks kept clear between a ruin and a settlement, town center or other building. */
    private static final int SPACING = 12;
    /** Blocks kept clear between two ruins. */
    private static final int RUIN_SPACING = 3;
    /** Blocks kept clear between a ruin and a gold mine. */
    private static final int MINE_SPACING = 6;
    /** Blocks kept clear between a ruin and a bridge. */
    private static final int BRIDGE_SPACING = 14;
    /** How far a ruin may be from the river's edge, at least. */
    private static final int RIVER_SPACING = 5;
    /** How much the ground under a ruin may rise and fall. */
    private static final int MAX_SLOPE = 2;
    /** How high above its ground a ruin reaches, at most; the air above is cleared up to here. */
    private static final int HEIGHT = 12;
    /** How many spots in a chunk a ruin is tried at before giving up on that chunk. */
    private static final int TRIES = 6;

    private Ruins() {}

    /**
     * Lays out ruins as the world is generated, chunk by chunk, while the chunk is being filled in: one site in
     * about every {@link Config#ruinEvery} blocks square, picked by the map's seed. A site is one ruin, or now and
     * then a few small ones together, all haunted the same way if at all. Everything stays inside the chunk and the
     * ones east and south of it, which are already there, so no further chunks are generated for it.
     */
    public static void populate(WorldServer world, int chunkX, int chunkZ, long seed) {
        Game game = GameWorld.currentGame();
        if (game == null || Config.ruinEvery <= 0 || GameWorld.layout(game) == null) {
            return;
        }
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        if (!game.isInside(baseX + 16, baseZ + 16)) {
            return;
        }
        Random random = new Random(chunkX * 341873128712L + chunkZ * 132897987541L + seed + 2);
        if (random.nextDouble() >= 256.0 / ((double) Config.ruinEvery * Config.ruinEvery)) {
            return;
        }
        int roll = random.nextInt(100);
        int haunt = roll < 30 ? Ruin.SPIDERS : roll < 50 ? Ruin.UNDEAD : Ruin.QUIET;
        if (random.nextInt(4) == 0) {
            int count = 2 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                placeRuin(game, world, baseX, baseZ, pick(random, true), haunt, random);
            }
        } else {
            Design design = pick(random, false);
            if (design == Design.GRAVEYARD && haunt == Ruin.QUIET && random.nextBoolean()) {
                haunt = Ruin.UNDEAD;
            }
            placeRuin(game, world, baseX, baseZ, design, haunt, random);
        }
    }

    /** A design picked by how common it is; only small ones if asked. */
    private static Design pick(Random random, boolean small) {
        int total = 0;
        for (Design design : Design.values()) {
            total += !small || design.small ? design.weight : 0;
        }
        int roll = random.nextInt(total);
        for (Design design : Design.values()) {
            if (small && !design.small) {
                continue;
            }
            roll -= design.weight;
            if (roll < 0) {
                return design;
            }
        }
        return Design.HUT;
    }

    /** Puts a ruin somewhere in the two chunks square from baseX, baseZ that are already generated, if it fits. */
    private static void placeRuin(Game game, WorldServer world, int baseX, int baseZ, Design design, int haunt,
        Random random) {
        int rotation = random.nextInt(4);
        int sizeX = rotation % 2 == 0 ? design.width : design.length;
        int sizeZ = rotation % 2 == 0 ? design.length : design.width;
        for (int tries = 0; tries < TRIES; tries++) {
            // From the middle of this chunk to the middle of the next, a block in from either side.
            int minX = baseX + 8 + random.nextInt(Math.max(1, 23 - sizeX));
            int minZ = baseZ + 8 + random.nextInt(Math.max(1, 23 - sizeZ));
            if (!clear(game, minX, minZ, sizeX, sizeZ)) {
                continue;
            }
            Buildings.Site site = Buildings.survey(world, minX, minZ, sizeX, sizeZ);
            if (site.water == 0 && site.slope <= MAX_SLOPE
                && site.groundY > ChunkProviderRts.BANK
                && site.groundY + HEIGHT < 255) {
                Ruin ruin = game.addRuin(minX, minZ, sizeX, sizeZ, site.groundY, design.name, haunt);
                build(world, ruin, design, rotation, random);
                return;
            }
        }
    }

    /**
     * Whether a footprint is inside the map and clear of where the teams start, the town centers and other
     * buildings, settlements, gold mines, other ruins, the river and its bridges.
     */
    private static boolean clear(Game game, int minX, int minZ, int sizeX, int sizeZ) {
        int edge = 8;
        if (minX < game.getMinX() + edge || minZ < game.getMinZ() + edge
            || minX + sizeX - 1 > game.getMaxX() - edge
            || minZ + sizeZ - 1 > game.getMaxZ() - edge) {
            return false;
        }
        double centerX = minX + sizeX / 2.0;
        double centerZ = minZ + sizeZ / 2.0;
        // Teams build out round where they start, so ruins keep further off from there, including where a team
        // will start but has not yet.
        double baseSpacing = Math.max(40, game.getSize() * 0.08);
        if (MapFeatures.nearBase(game, centerX, centerZ, baseSpacing)) {
            return false;
        }
        Misthalin layout = GameWorld.layout(game);
        for (double[] base : layout == null ? new double[0][] : layout.getBases()) {
            double dx = base[0] - centerX;
            double dz = base[1] - centerZ;
            if (dx * dx + dz * dz < baseSpacing * baseSpacing) {
                return false;
            }
        }
        for (Building building : game.getBuildings()) {
            if (building.overlaps(minX, minZ, sizeX, sizeZ, SPACING)) {
                return false;
            }
        }
        for (Settlement settlement : game.getSettlements()) {
            if (settlement.overlaps(minX, minZ, sizeX, sizeZ, SPACING)) {
                return false;
            }
        }
        for (GoldMine mine : game.getMines()) {
            if (mine.overlaps(minX, minZ, sizeX, sizeZ, MINE_SPACING)) {
                return false;
            }
        }
        for (Ruin ruin : game.getRuins()) {
            if (ruin.overlaps(minX, minZ, sizeX, sizeZ, RUIN_SPACING)) {
                return false;
            }
        }
        if (MapFeatures.bridgeNear(game, minX, minZ, sizeX, sizeZ, BRIDGE_SPACING)) {
            return false;
        }
        if (layout != null) {
            double reach = Misthalin.RIVER_HALF_WIDTH + 0.5 + RIVER_SPACING + Math.max(sizeX, sizeZ) / 2.0;
            if (layout.riverDistance((int) centerX, (int) centerZ) < reach) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- building

    /**
     * The blocks of a ruin before it is turned, by x across its width, y up from its ground and z along its length;
     * null for leaving the block as it is: air above the ground, the ground at y 0.
     */
    private static final class Plan {

        final int width;
        final int length;
        final Block[] blocks;
        final byte[] metadata;

        Plan(int width, int length) {
            this.width = width;
            this.length = length;
            this.blocks = new Block[width * length * (HEIGHT + 1)];
            this.metadata = new byte[blocks.length];
        }

        private int index(int x, int y, int z) {
            return (y * length + z) * width + x;
        }

        boolean inside(int x, int y, int z) {
            return x >= 0 && x < width && z >= 0 && z < length && y >= 0 && y <= HEIGHT;
        }

        void set(int x, int y, int z, Block block, int meta) {
            if (inside(x, y, z)) {
                blocks[index(x, y, z)] = block;
                metadata[index(x, y, z)] = (byte) meta;
            }
        }

        Block get(int x, int y, int z) {
            return inside(x, y, z) ? blocks[index(x, y, z)] : null;
        }

        /** How many blocks stand on the ground at x, z. */
        int heightAt(int x, int z) {
            int height = 0;
            for (int y = 1; y <= HEIGHT; y++) {
                if (get(x, y, z) != null) {
                    height = y;
                }
            }
            return height;
        }
    }

    private static void build(WorldServer world, Ruin ruin, Design design, int rotation, Random random) {
        Plan plan = new Plan(design.width, design.length);
        switch (design) {
            case WALLS:
                walls(plan, random);
                break;
            case TOWER:
                tower(plan, random);
                break;
            case CHAPEL:
                chapel(plan, random);
                break;
            case FARMSTEAD:
                farmstead(plan, random);
                break;
            case MANOR:
                manor(plan, random);
                break;
            case HUT:
                hut(plan, random);
                break;
            case GRAVEYARD:
                graveyard(plan, random);
                break;
            case ARCH:
                arch(plan, random);
                break;
            case WELL:
                well(plan, random);
                break;
            case PILLARS:
                pillars(plan, random);
                break;
            case WALL_STRETCH:
                wallStretch(plan, random);
                break;
            default:
                statue(plan, random);
                break;
        }
        overgrow(plan, random);
        if (ruin.haunt == Ruin.SPIDERS) {
            webs(plan, random);
        }
        for (int x = 0; x < plan.width; x++) {
            for (int z = 0; z < plan.length; z++) {
                int[] at = turn(x, z, plan.width, plan.length, rotation + 2);
                int wx = ruin.minX + at[0];
                int wz = ruin.minZ + at[1];
                level(world, wx, wz, ruin.groundY);
                for (int y = 0; y <= HEIGHT; y++) {
                    Block block = plan.get(x, y, z);
                    if (block != null) {
                        int meta = plan.metadata[plan.index(x, y, z)];
                        if (block == Blocks.vine) {
                            meta = turnVine(meta, rotation + 2);
                        }
                        world.setBlock(wx, ruin.groundY + y, wz, block, meta, 2);
                    }
                }
            }
        }
        WebMap.blocksChanged(world, ruin.minX - 1, ruin.minZ - 1, ruin.minX + ruin.sizeX, ruin.minZ + ruin.sizeZ);
    }

    /** Where x, z of a footprint width by length ends up once turned a number of quarter turns. */
    private static int[] turn(int x, int z, int width, int length, int rotation) {
        switch (rotation & 3) {
            case 1:
                return new int[] { length - 1 - z, x };
            case 2:
                return new int[] { width - 1 - x, length - 1 - z };
            case 3:
                return new int[] { z, width - 1 - x };
            default:
                return new int[] { x, z };
        }
    }

    /**
     * Vine metadata says which sides of the block it clings to: 1 south, 2 west, 4 north, 8 east. Turned the same
     * way as {@link #turn}: a quarter turn takes south to west.
     */
    private static int turnVine(int meta, int rotation) {
        for (int i = 0; i < (rotation & 3); i++) {
            meta = (meta << 1 | meta >> 3) & 15;
        }
        return meta;
    }

    /** Brings the ground of a column to y: grass on dirt, with the air above it clear. */
    private static void level(WorldServer world, int x, int z, int groundY) {
        for (int y = groundY - 1; y > 0 && !world.getBlock(x, y, z)
            .isOpaqueCube(); y--) {
            world.setBlock(x, y, z, Blocks.dirt, 0, 2);
        }
        int top = world.getHeightValue(x, z) - 1;
        for (int y = Math.max(top, groundY + HEIGHT); y > groundY; y--) {
            world.setBlock(x, y, z, Blocks.air, 0, 2);
        }
        world.setBlock(x, groundY, z, Blocks.grass, 0, 2);
    }

    // ---------------------------------------------------------------- stone

    /** A stone block gone old: mostly stone bricks, some mossy or cracked, some cobblestone. */
    private static void stone(Plan plan, int x, int y, int z, Random random) {
        int roll = random.nextInt(10);
        if (roll < 4) {
            plan.set(x, y, z, Blocks.stonebrick, 0);
        } else if (roll < 6) {
            plan.set(x, y, z, Blocks.stonebrick, 1);
        } else if (roll < 7) {
            plan.set(x, y, z, Blocks.stonebrick, 2);
        } else if (roll < 9) {
            plan.set(x, y, z, Blocks.mossy_cobblestone, 0);
        } else {
            plan.set(x, y, z, Blocks.cobblestone, 0);
        }
    }

    /** A column of old stone from the ground up, height blocks high, its top block half fallen away now and then. */
    private static void column(Plan plan, int x, int z, int height, Random random) {
        for (int y = 1; y <= height; y++) {
            if (y == height && height > 1 && random.nextInt(4) == 0) {
                plan.set(x, y, z, Blocks.stone_slab, random.nextBoolean() ? 5 : 3);
            } else {
                stone(plan, x, y, z, random);
            }
        }
    }

    /** Fallen stones lying on the ground, one block high so units step over them. */
    private static void rubble(Plan plan, int x, int z, Random random) {
        if (!plan.inside(x, 1, z) || plan.get(x, 1, z) != null) {
            return;
        }
        int roll = random.nextInt(5);
        if (roll < 2) {
            plan.set(x, 1, z, Blocks.stone_slab, roll == 0 ? 3 : 5);
        } else if (roll < 4) {
            plan.set(x, 1, z, Blocks.mossy_cobblestone, 0);
        } else {
            plan.set(x, 1, z, Blocks.cobblestone, 0);
        }
    }

    /** Old paving underfoot: cobblestone, gravel and stone bricks, with grass coming through. */
    private static void paving(Plan plan, int x, int z, Random random) {
        int roll = random.nextInt(8);
        if (roll < 2) {
            plan.set(x, 0, z, Blocks.cobblestone, 0);
        } else if (roll < 3) {
            plan.set(x, 0, z, Blocks.mossy_cobblestone, 0);
        } else if (roll < 4) {
            plan.set(x, 0, z, Blocks.gravel, 0);
        } else if (roll < 6) {
            plan.set(x, 0, z, Blocks.stonebrick, random.nextInt(3));
        }
    }

    /** A wall's height along its length: a walk up and down between low and high, crumbling away here and there. */
    private static int[] crumbling(int length, int low, int high, Random random) {
        int[] heights = new int[length];
        int height = low + random.nextInt(high - low + 1);
        for (int i = 0; i < length; i++) {
            height = Math.max(low, Math.min(high, height + random.nextInt(3) - 1));
            heights[i] = height;
        }
        return heights;
    }

    /** The places round the edge of a rectangle from x0, z0 to x1, z1, clockwise from its north-west corner. */
    private static List<int[]> ring(int x0, int z0, int x1, int z1) {
        List<int[]> ring = new ArrayList<>();
        for (int x = x0; x < x1; x++) {
            ring.add(new int[] { x, z0 });
        }
        for (int z = z0; z < z1; z++) {
            ring.add(new int[] { x1, z });
        }
        for (int x = x1; x > x0; x--) {
            ring.add(new int[] { x, z1 });
        }
        for (int z = z1; z > z0; z--) {
            ring.add(new int[] { x0, z });
        }
        return ring;
    }

    // ---------------------------------------------------------------- designs

    /**
     * Crumbled walls, as of an old fort on Varrock's outskirts: a square of walls one to three blocks high, standing
     * taller at the corners, broken through on every side.
     */
    private static void walls(Plan plan, Random random) {
        int x0 = 1;
        int z0 = 1;
        int x1 = plan.width - 2;
        int z1 = plan.length - 2;
        List<int[]> ring = ring(x0, z0, x1, z1);
        int[] heights = crumbling(ring.size(), 0, 3, random);
        // Some have only two sides left standing, some had towers at the corners.
        boolean twoSides = random.nextInt(3) == 0;
        boolean towers = random.nextInt(3) == 0;
        for (int i = 0; i < ring.size(); i++) {
            int[] at = ring.get(i);
            boolean corner = (at[0] == x0 || at[0] == x1) && (at[1] == z0 || at[1] == z1);
            if (twoSides && (at[0] == x1 && at[1] < z1 || at[1] == z0 && at[0] > x0)) {
                if (random.nextInt(3) == 0) {
                    rubble(plan, at[0], at[1], random);
                }
                continue;
            }
            int tower = towers ? 5 + random.nextInt(2) : 3 + random.nextInt(2);
            column(plan, at[0], at[1], corner ? tower : heights[i], random);
        }
        // A gateway two wide in the middle of the front and back, and a breach in each side.
        int midX = plan.width / 2;
        int midZ = plan.length / 2;
        int back = midX + random.nextInt(2);
        int west = midZ - random.nextInt(2);
        int east = midZ - random.nextInt(2);
        for (int y = 1; y <= HEIGHT; y++) {
            plan.set(midX, y, z1, null, 0);
            plan.set(midX - 1, y, z1, null, 0);
            plan.set(back, y, z0, null, 0);
            plan.set(back - 1, y, z0, null, 0);
            plan.set(x0, y, west, null, 0);
            plan.set(x0, y, west + 1, null, 0);
            plan.set(x1, y, east, null, 0);
            plan.set(x1, y, east + 1, null, 0);
        }
        for (int x = x0 + 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                if (random.nextInt(3) == 0) {
                    paving(plan, x, z, random);
                }
            }
        }
        scatter(plan, 7, random);
    }

    /**
     * A broken tower, as Draynor's old watchtower might stand once the wizards left: a round stone wall standing
     * tall on one side and fallen almost to the ground on the other, with a doorway at the front and its stones
     * strewn where it fell.
     */
    private static void tower(Plan plan, Random random) {
        double cx = (plan.width - 1) / 2.0;
        double cz = (plan.length - 1) / 2.0;
        double standing = random.nextDouble() * 2 * Math.PI;
        // Round, or now and then square.
        boolean square = random.nextInt(3) == 0;
        for (int x = 0; x < plan.width; x++) {
            for (int z = 0; z < plan.length; z++) {
                double dx = x - cx;
                double dz = z - cz;
                double distance = square ? Math.max(Math.abs(dx), Math.abs(dz)) + 0.1 : Math.sqrt(dx * dx + dz * dz);
                if (distance < 2.6) {
                    paving(plan, x, z, random);
                    if (plan.get(x, 0, z) == null) {
                        plan.set(x, 0, z, Blocks.cobblestone, 0);
                    }
                } else if (distance < 3.65) {
                    double side = Math.cos(Math.atan2(dz, dx) - standing);
                    int height = (int) Math.round(3.5 + 3.5 * side) + random.nextInt(2) - 1;
                    column(plan, x, z, Math.max(0, Math.min(7, height)), random);
                    // Arrow slits, where enough of the wall stands.
                    if (height >= 5 && random.nextInt(3) == 0) {
                        plan.set(x, 4, z, null, 0);
                    }
                }
            }
        }
        // A doorway two wide at the front, and a hole broken through the fallen side.
        int midX = plan.width / 2;
        for (int y = 1; y <= HEIGHT; y++) {
            for (int z = plan.length - 3; z < plan.length; z++) {
                plan.set(midX, y, z, null, 0);
                plan.set(midX - 1, y, z, null, 0);
            }
        }
        int holeX = (int) Math.round(cx - Math.cos(standing) * 3);
        int holeZ = (int) Math.round(cz - Math.sin(standing) * 3);
        for (int y = 1; y <= HEIGHT; y++) {
            for (int d = -1; d <= 1; d++) {
                if (Math.abs(Math.cos(standing)) > Math.abs(Math.sin(standing))) {
                    plan.set(holeX, y, holeZ + d, null, 0);
                } else {
                    plan.set(holeX + d, y, holeZ, null, 0);
                }
            }
        }
        // Stones strewn outside the fallen side, where the wall came down.
        for (int i = 0; i < 9; i++) {
            double reach = 3.6 + random.nextDouble() * 1.4;
            double angle = standing + Math.PI + (random.nextDouble() - 0.5) * 1.6;
            rubble(
                plan,
                (int) Math.round(cx + Math.cos(angle) * reach),
                (int) Math.round(cz + Math.sin(angle) * reach),
                random);
        }
    }

    /**
     * The remains of a chapel, like the temple of Paterdomus or Lumbridge's church after a long war: the walls of
     * the nave fallen to stubs and gaps, the gable at the back still standing round its window, an altar stone
     * before it and the doorway at the front open.
     */
    private static void chapel(Plan plan, Random random) {
        int x0 = 1;
        int z0 = 1;
        int x1 = plan.width - 2;
        int z1 = plan.length - 2;
        int midX = plan.width / 2;
        // The side walls, with buttresses taller every few blocks.
        for (int x : new int[] { x0, x1 }) {
            int[] heights = crumbling(z1 - z0, 0, 3, random);
            for (int z = z0 + 1; z < z1; z++) {
                int height = heights[z - z0 - 1];
                boolean buttress = (z - z0) % 3 == 0;
                column(plan, x, z, buttress ? Math.max(height, 3) + random.nextInt(2) : height, random);
            }
            // A stretch fallen right down, so the nave can be walked into from the side.
            int gap = z0 + 2 + random.nextInt(Math.max(1, z1 - z0 - 5));
            for (int y = 1; y <= HEIGHT; y++) {
                plan.set(x, y, gap, null, 0);
                plan.set(x, y, gap + 1, null, 0);
            }
        }
        // The gable at the back, rising to a point round a window.
        for (int x = x0; x <= x1; x++) {
            int height = 6 - Math.abs(x - midX) - random.nextInt(2);
            column(plan, x, z0, Math.max(2, height), random);
        }
        plan.set(midX, 3, z0, null, 0);
        plan.set(midX, 4, z0, null, 0);
        // Now and then the stump of a bell tower still stands at a front corner.
        if (random.nextInt(3) == 0) {
            int height = 5 + random.nextInt(3);
            for (int x = x0; x <= x0 + 1; x++) {
                for (int z = z1 - 1; z <= z1; z++) {
                    column(plan, x, z, height - random.nextInt(2), random);
                }
            }
        }
        // The front, standing either side of a doorway three wide.
        for (int x = x0; x <= x1; x++) {
            if (Math.abs(x - midX) > 1) {
                column(plan, x, z1, 1 + random.nextInt(3), random);
            }
        }
        // The floor, the altar before the gable and a pew or two still about.
        for (int x = x0 + 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                if (random.nextInt(4) != 0) {
                    plan.set(x, 0, z, Blocks.stonebrick, random.nextInt(3));
                }
            }
        }
        plan.set(midX, 1, z0 + 2, Blocks.double_stone_slab, 0);
        plan.set(midX - 1, 1, z0 + 2, Blocks.stone_slab, 0);
        plan.set(midX + 1, 1, z0 + 2, Blocks.stone_slab, 0);
        for (int z = z0 + 4; z < z1 - 1; z += 2) {
            if (random.nextInt(3) == 0) {
                plan.set(midX - 2, 1, z, Blocks.wooden_slab, 1);
            }
            if (random.nextInt(3) == 0) {
                plan.set(midX + 2, 1, z, Blocks.wooden_slab, 1);
            }
        }
        scatter(plan, 6, random);
    }

    /**
     * An abandoned farmstead, of the kind that once stood in the fields round Lumbridge: the low cobblestone
     * footings of a house with its chimney still up, the roof fallen in as planks on the ground, and a well.
     */
    private static void farmstead(Plan plan, Random random) {
        int x0 = 1;
        int z0 = 1;
        int x1 = plan.width - 4;
        int z1 = plan.length - 3;
        List<int[]> ring = ring(x0, z0, x1, z1);
        int[] heights = crumbling(ring.size(), 0, 2, random);
        for (int i = 0; i < ring.size(); i++) {
            int[] at = ring.get(i);
            for (int y = 1; y <= heights[i]; y++) {
                plan.set(at[0], y, at[1], random.nextInt(3) == 0 ? Blocks.mossy_cobblestone : Blocks.cobblestone, 0);
            }
        }
        // The doorway at the front and the chimney in the back corner.
        int doorX = (x0 + x1) / 2;
        for (int y = 1; y <= HEIGHT; y++) {
            plan.set(doorX, y, z1, null, 0);
            plan.set(doorX + 1, y, z1, null, 0);
        }
        int chimney = 4 + random.nextInt(2);
        for (int y = 1; y <= chimney; y++) {
            plan.set(x0, y, z0, y == chimney ? Blocks.stone_slab : Blocks.brick_block, y == chimney ? 4 : 0);
        }
        // The roof, fallen in.
        for (int x = x0 + 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                if (random.nextInt(3) == 0) {
                    plan.set(x, 1, z, Blocks.wooden_slab, 1);
                } else if (random.nextInt(4) == 0) {
                    plan.set(x, 0, z, Blocks.planks, 1);
                }
            }
        }
        // A well round the side: a ring of stone a block high round a hole of water.
        int wellX = plan.width - 2;
        int wellZ = plan.length - 2;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    plan.set(wellX, 0, wellZ, Blocks.water, 0);
                } else if (dx == 0 || dz == 0) {
                    plan.set(wellX + dx, 1, wellZ + dz, Blocks.cobblestone, 0);
                } else {
                    plan.set(wellX + dx, 1, wellZ + dz, Blocks.stone_slab, 3);
                }
            }
        }
        scatter(plan, 4, random);
    }

    /**
     * A fallen manor, like Draynor Manor left to rot: two rooms behind crumbling walls up to a storey high, with
     * window holes, a chimney stack at each end, a doorway at the front and the floor half planks, half stone.
     */
    private static void manor(Plan plan, Random random) {
        int x0 = 1;
        int z0 = 1;
        int x1 = plan.width - 2;
        int z1 = plan.length - 2;
        int midX = plan.width / 2;
        List<int[]> ring = ring(x0, z0, x1, z1);
        int[] heights = crumbling(ring.size(), 1, 5, random);
        for (int i = 0; i < ring.size(); i++) {
            int[] at = ring.get(i);
            column(plan, at[0], at[1], heights[i], random);
            if (heights[i] >= 4 && random.nextInt(3) == 0) {
                plan.set(at[0], 3, at[1], null, 0);
            }
        }
        // The wall between the rooms, with a doorway through it.
        int[] inner = crumbling(z1 - z0 - 1, 0, 4, random);
        for (int z = z0 + 1; z < z1; z++) {
            column(plan, midX, z, inner[z - z0 - 1], random);
        }
        int through = z0 + 2 + random.nextInt(Math.max(1, z1 - z0 - 4));
        for (int y = 1; y <= HEIGHT; y++) {
            plan.set(midX, y, through, null, 0);
            plan.set(midX, y, through + 1, null, 0);
            // The front door, and a breach in the back of each room.
            plan.set(midX - 2, y, z1, null, 0);
            plan.set(midX - 3, y, z1, null, 0);
            plan.set(midX + 3, y, z1, null, 0);
            plan.set(x0 + 2, y, z0, null, 0);
            plan.set(x1 - 2, y, z0, null, 0);
            plan.set(x1 - 3, y, z0, null, 0);
        }
        // Chimney stacks at either end.
        for (int x : new int[] { x0, x1 }) {
            int height = 5 + random.nextInt(3);
            for (int y = 1; y <= height; y++) {
                plan.set(x, y, (z0 + z1) / 2, Blocks.brick_block, 0);
            }
        }
        // The floor: rotten planks in one room, flagstones in the other, the roof fallen in on both.
        int planks = random.nextInt(3) == 0 ? 5 : 1;
        for (int x = x0 + 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                if (x == midX) {
                    continue;
                }
                if (random.nextInt(4) != 0) {
                    if (x < midX) {
                        plan.set(x, 0, z, Blocks.planks, planks);
                    } else {
                        plan.set(x, 0, z, Blocks.stonebrick, random.nextInt(3));
                    }
                }
                if (plan.get(x, 1, z) == null && random.nextInt(6) == 0) {
                    plan.set(x, 1, z, Blocks.wooden_slab, planks);
                }
            }
        }
        scatter(plan, 8, random);
    }

    /**
     * An abandoned wooden hut, of the kind woodcutters and fishermen left behind: plank walls with a doorway and
     * holes rotted through them, a sagging roof with gaps in it and nothing inside to give light. Some have half
     * their roof fallen in.
     */
    private static void hut(Plan plan, Random random) {
        int wood = new int[] { 0, 1, 1, 5 }[random.nextInt(4)];
        int posts = wood == 5 ? 1 : 5;
        int x1 = plan.width - 1;
        int z1 = plan.length - 1;
        int midX = plan.width / 2;
        boolean fallen = random.nextInt(3) == 0;
        for (int x = 0; x <= x1; x++) {
            for (int z = 0; z <= z1; z++) {
                boolean wall = x == 0 || x == x1 || z == 0 || z == z1;
                boolean corner = (x == 0 || x == x1) && (z == 0 || z == z1);
                if (!wall) {
                    if (random.nextInt(3) != 0) {
                        plan.set(x, 0, z, Blocks.planks, wood);
                    }
                    continue;
                }
                int height = fallen && x == x1 ? 1 + random.nextInt(2) : 3;
                for (int y = 1; y <= height; y++) {
                    // Small holes rotted through, never so many that the walls are gone.
                    if (!corner && y >= 2 && random.nextInt(6) == 0) {
                        continue;
                    }
                    plan.set(x, y, z, Blocks.planks, corner ? posts : wood);
                }
            }
        }
        // The doorway at the front.
        plan.set(midX, 1, z1, null, 0);
        plan.set(midX, 2, z1, null, 0);
        // The roof: sloping down to either side, with gaps, missing over the fallen side.
        for (int z = 0; z <= z1; z++) {
            for (int x = 0; x <= x1; x++) {
                if (fallen && x >= midX + 1 || random.nextInt(7) == 0) {
                    continue;
                }
                int fromMiddle = Math.abs(x - midX);
                if (fromMiddle == 2) {
                    plan.set(x, 4, z, Blocks.wooden_slab, wood);
                } else if (fromMiddle == 1) {
                    plan.set(x, 4, z, Blocks.planks, wood);
                    plan.set(x, 5, z, Blocks.wooden_slab, wood);
                } else {
                    plan.set(x, 4, z, Blocks.planks, wood);
                    plan.set(x, 5, z, Blocks.planks, wood);
                }
            }
        }
        // What fell in lies on the floor.
        if (fallen) {
            for (int z = 1; z < z1; z++) {
                if (random.nextBoolean()) {
                    plan.set(x1 - 1, 1, z, Blocks.wooden_slab, wood);
                }
            }
        }
    }

    /**
     * An old graveyard, like the one by Draynor Manor: a low mossy wall with gaps round rows of headstones, some
     * fallen, the graves before them bare earth.
     */
    private static void graveyard(Plan plan, Random random) {
        int x1 = plan.width - 1;
        int z1 = plan.length - 1;
        int midX = plan.width / 2;
        for (int[] at : ring(0, 0, x1, z1)) {
            boolean gate = Math.abs(at[0] - midX) <= 1 && (at[1] == 0 || at[1] == z1);
            if (!gate && random.nextInt(5) != 0) {
                plan.set(at[0], 1, at[1], random.nextBoolean() ? Blocks.mossy_cobblestone : Blocks.cobblestone, 0);
            }
        }
        for (int z = 2; z < z1; z += 2) {
            for (int x = 2; x < x1; x += 2) {
                if (x == midX || random.nextInt(5) == 0) {
                    continue;
                }
                int roll = random.nextInt(4);
                if (roll == 0) {
                    plan.set(x, 1, z, Blocks.stone_slab, 5);
                } else {
                    plan.set(x, 1, z, Blocks.stonebrick, roll == 1 ? 1 : roll == 2 ? 2 : 0);
                }
                plan.set(x, 0, z + 1, Blocks.dirt, random.nextBoolean() ? 1 : 2);
            }
        }
    }

    /** A broken arch, all that is left of a gatehouse: two pillars, the lintel half fallen, its stones about. */
    private static void arch(Plan plan, Random random) {
        int left = 1;
        int right = plan.width - 2;
        int height = 4 + random.nextInt(2);
        column(plan, left, 1, height, random);
        column(plan, right, 1, height - random.nextInt(3), random);
        // The lintel runs from the taller pillar and breaks off partway.
        int reach = 1 + random.nextInt(3);
        for (int x = left; x <= left + reach; x++) {
            stone(plan, x, height + 1, 1, random);
        }
        scatter(plan, 4, random);
    }

    /** An old well: a ring of stone round the water, the posts of its winch and a bit of its roof. */
    private static void well(Plan plan, Random random) {
        int c = 2;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    plan.set(c, 0, c, Blocks.water, 0);
                } else {
                    Block block = random.nextInt(3) == 0 ? Blocks.mossy_cobblestone : Blocks.cobblestone;
                    plan.set(c + dx, 1, c + dz, block, 0);
                }
            }
        }
        boolean roof = random.nextBoolean();
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int y = 2; y <= 3; y++) {
                plan.set(c + dx, y, c, Blocks.cobblestone, 0);
            }
        }
        if (roof) {
            for (int dx = -1; dx <= 1; dx++) {
                if (random.nextInt(3) != 0) {
                    plan.set(c + dx, 4, c, Blocks.wooden_slab, 1);
                }
            }
        }
        for (int[] at : ring(0, 0, plan.width - 1, plan.length - 1)) {
            if (random.nextInt(4) == 0) {
                plan.set(at[0], 0, at[1], Blocks.gravel, 0);
            }
        }
        scatter(plan, 2, random);
    }

    /**
     * A fallen colonnade, as of some old temple: two rows of pillars standing to different heights, the fallen ones
     * lying where they broke, on what is left of the paving.
     */
    private static void pillars(Plan plan, Random random) {
        int z0 = 1;
        int z1 = plan.length - 2;
        for (int x = 0; x < plan.width; x++) {
            for (int z = z0; z <= z1; z++) {
                if (random.nextInt(3) != 0) {
                    paving(plan, x, z, random);
                }
            }
        }
        for (int x = 1; x < plan.width; x += 3) {
            for (int z : new int[] { z0, z1 }) {
                int height = random.nextInt(6);
                if (height > 1) {
                    column(plan, x, z, height, random);
                } else {
                    // Fallen: its drums lie in a line beside the stump.
                    stone(plan, x, 1, z, random);
                    int dz = z == z0 ? 1 : -1;
                    for (int i = 1; i <= 2; i++) {
                        plan.set(x, 1, z + dz * i, Blocks.stone_slab, 5);
                    }
                }
            }
        }
    }

    /**
     * A stretch of an old city wall, like Varrock's where it has gone to ruin: a wall up to four blocks high, broken
     * through, buttressed here and there, its stones fallen on both sides.
     */
    private static void wallStretch(Plan plan, Random random) {
        int z = 1;
        int[] heights = crumbling(plan.width, 0, 4, random);
        for (int x = 0; x < plan.width; x++) {
            column(plan, x, z, heights[x], random);
            if (heights[x] >= 3 && x % 4 == 2) {
                column(plan, x, z - 1, 2, random);
            }
        }
        int gap = 3 + random.nextInt(Math.max(1, plan.width - 7));
        for (int y = 1; y <= HEIGHT; y++) {
            for (int x = gap; x < gap + 2; x++) {
                plan.set(x, y, z, null, 0);
                plan.set(x, y, z - 1, null, 0);
            }
        }
        scatter(plan, 6, random);
    }

    /** A toppled statue of Saradomin, or someone: its plinth, its legs to the knee and its head in the grass. */
    private static void statue(Plan plan, Random random) {
        int c = 2;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                plan.set(c + dx, 1, c + dz, Blocks.stonebrick, random.nextInt(3));
            }
        }
        plan.set(c, 2, c, Blocks.stone, 0);
        if (random.nextBoolean()) {
            plan.set(c, 3, c, Blocks.stone, 0);
        }
        int[][] spots = { { 0, 4 }, { 4, 0 }, { 4, 4 }, { 0, 0 } };
        int[] head = spots[random.nextInt(spots.length)];
        plan.set(head[0], 1, head[1], Blocks.stone, 0);
        scatter(plan, 2, random);
    }

    /**
     * Cobwebs where spiders lurk: in the corners up under the walls and the roof, out of the way of units walking
     * through, which they would slow.
     */
    private static void webs(Plan plan, Random random) {
        int count = 4 + random.nextInt(6);
        for (int tries = 0; tries < 200 && count > 0; tries++) {
            int x = random.nextInt(plan.width);
            int z = random.nextInt(plan.length);
            int y = 2 + random.nextInt(4);
            if (plan.get(x, y, z) != null) {
                continue;
            }
            int around = 0;
            for (int[] side : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                around += solid(plan.get(x + side[0], y, z + side[1])) ? 1 : 0;
            }
            if (around >= 2 || around >= 1 && solid(plan.get(x, y + 1, z))) {
                plan.set(x, y, z, Blocks.web, 0);
                count--;
            }
        }
    }

    /** Whether a block of a plan is something a cobweb can hang from. */
    private static boolean solid(Block block) {
        return block != null && block != Blocks.vine
            && block != Blocks.tallgrass
            && block != Blocks.web
            && block != Blocks.leaves
            && block != Blocks.water;
    }

    /** Stones fallen off the walls onto the ground, inside and out. */
    private static void scatter(Plan plan, int count, Random random) {
        for (int i = 0; i < count; i++) {
            rubble(plan, random.nextInt(plan.width), random.nextInt(plan.length), random);
        }
    }

    /**
     * Grows the ruin over: vines on the walls that stand, tall grass and ferns where nothing stands on the ground,
     * and moss in the tops of the tallest walls. Leaves go only on walls three or more high, where no unit walks.
     */
    private static void overgrow(Plan plan, Random random) {
        int[][] heights = new int[plan.width][plan.length];
        for (int x = 0; x < plan.width; x++) {
            for (int z = 0; z < plan.length; z++) {
                heights[x][z] = plan.heightAt(x, z);
            }
        }
        int[][] sides = { { 0, 1, 4 }, { -1, 0, 8 }, { 0, -1, 1 }, { 1, 0, 2 } };
        for (int x = 0; x < plan.width; x++) {
            for (int z = 0; z < plan.length; z++) {
                int height = heights[x][z];
                if (height == 0) {
                    Block floor = plan.get(x, 0, z);
                    if (floor == null && plan.get(x, 1, z) == null && random.nextInt(4) == 0) {
                        plan.set(x, 1, z, Blocks.tallgrass, random.nextInt(3) == 0 ? 2 : 1);
                    }
                    continue;
                }
                if (height >= 3 && plan.get(x, height, z) != Blocks.stone_slab && random.nextInt(4) == 0) {
                    plan.set(x, height + 1, z, Blocks.leaves, 4);
                }
                if (height < 2) {
                    continue;
                }
                // Vines hang in the open block beside a wall, clinging back to it: meta is the side the wall is on.
                for (int[] side : sides) {
                    int vx = x + side[0];
                    int vz = z + side[1];
                    if (random.nextInt(5) != 0 || !plan.inside(vx, 1, vz) || heights[vx][vz] > 0) {
                        continue;
                    }
                    for (int y = height; y >= Math.max(2, height - 2); y--) {
                        if (plan.get(vx, y, vz) == null) {
                            plan.set(vx, y, vz, Blocks.vine, side[2]);
                        }
                    }
                }
            }
        }
    }
}
