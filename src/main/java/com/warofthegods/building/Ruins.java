package com.warofthegods.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.game.Building;
import com.warofthegods.game.Game;
import com.warofthegods.game.GoldMine;
import com.warofthegods.game.Ruin;
import com.warofthegods.game.Settlement;
import com.warofthegods.game.Team;
import com.warofthegods.web.WebMap;
import com.warofthegods.world.ChunkProviderRts;
import com.warofthegods.world.GameWorld;
import com.warofthegods.world.Misthalin;

/**
 * Old ruins of Misthalin lying about the map: crumbled walls like those round Varrock, a broken tower like Draynor's,
 * the remains of a chapel like Paterdomus and an abandoned Lumbridge farmstead. They are built in stone gone mossy
 * and cracked, low and full of gaps, so units walk through and round them; they keep well away from town centers,
 * settlements, gold mines, the river and its bridges. Every team gets the same number and kinds of ruin, laid out the
 * same way round its side of the map, but each one crumbled its own way.
 */
final class Ruins {

    /** The kinds of ruin, with their footprints before turning. */
    enum Design {

        WALLS("crumbled walls", 11, 9),
        TOWER("broken tower", 9, 9),
        CHAPEL("ruined chapel", 9, 13),
        FARMSTEAD("abandoned farmstead", 9, 9);

        final String name;
        final int width;
        final int length;

        Design(String name, int width, int length) {
            this.name = name;
            this.width = width;
            this.length = length;
        }
    }

    /** Blocks kept clear between a ruin and a settlement, town center, tower or another ruin. */
    private static final int SPACING = 14;
    /** Blocks kept clear between a ruin and a gold mine. */
    private static final int MINE_SPACING = 8;
    /** Blocks kept clear between a ruin and a bridge. */
    private static final int BRIDGE_SPACING = 16;
    /** How far a ruin may be from the river's edge, at least. */
    private static final int RIVER_SPACING = 6;
    /** How much the ground under a ruin may rise and fall. */
    private static final int MAX_SLOPE = 2;
    /** How high above its ground a ruin reaches, at most; the air above is cleared up to here. */
    private static final int HEIGHT = 12;

    private Ruins() {}

    /** Ruins laid out for each team, by the size of the map. */
    static int perTeam(Game game) {
        if (Config.ruinEvery <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(game.getSize() / (double) Config.ruinEvery));
    }

    /**
     * Lays out the ruins over every team's side of the map, the same way round each: from between the team and the
     * middle of the map out toward its sides, well clear of where the teams start.
     */
    static void place(Game game, WorldServer world, List<Team> teams) {
        int count = perTeam(game);
        if (count == 0) {
            return;
        }
        int n = Math.max(2, teams.size());
        double sector = 2 * Math.PI / n;
        long seed = game.getSeed();
        Design[] designs = Design.values();
        int placed = 0;
        for (int i = 0; i < teams.size(); i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * i / n;
            for (int k = 0; k < count; k++) {
                double t = -1 + 2.0 * (k + 0.5) / count;
                // Nearer the middle in front of the team, further out off to its sides.
                double out = 0.13 + 0.2 * Math.abs(t) + 0.03 * ((k * 0.618034 + 0.4) % 1);
                int x = (int) Math.round(game.getCenterX() + Math.cos(angle + t * 0.4 * sector) * game.getSize() * out);
                int z = (int) Math.round(game.getCenterZ() + Math.sin(angle + t * 0.4 * sector) * game.getSize() * out);
                Design design = designs[(int) Math.floorMod(seed + k, (long) designs.length)];
                int rotation = MapFeatures.facingMiddle(game, x, z);
                int sizeX = rotation % 2 == 0 ? design.width : design.length;
                int sizeZ = rotation % 2 == 0 ? design.length : design.width;
                int[] spot = findSpot(game, world, x, z, sizeX, sizeZ, 40);
                if (spot == null) {
                    continue;
                }
                Buildings.clearTrees(game, world, spot[0], spot[1], sizeX, sizeZ, spot[2]);
                Ruin ruin = game.addRuin(spot[0], spot[1], sizeX, sizeZ, spot[2], design.name);
                Random random = new Random(seed * 31 + i * 7919L + k * 104729L);
                build(world, ruin, design, rotation, random);
                placed++;
            }
        }
        WarOfTheGods.LOG.info("Laid out {} ruins", placed);
    }

    /** A spot near x, z for a ruin's footprint, as minX, minZ, groundY; null if there is none. */
    private static int[] findSpot(Game game, WorldServer world, int x, int z, int sizeX, int sizeZ,
        int maxDistance) {
        for (int ring = 0; ring <= maxDistance; ring += 4) {
            int steps = ring == 0 ? 1 : 16;
            for (int step = 0; step < steps; step++) {
                double angle = 2 * Math.PI * step / steps;
                int minX = x + (int) Math.round(Math.cos(angle) * ring) - sizeX / 2;
                int minZ = z + (int) Math.round(Math.sin(angle) * ring) - sizeZ / 2;
                if (!clear(game, minX, minZ, sizeX, sizeZ)) {
                    continue;
                }
                Buildings.Site site = Buildings.survey(world, minX, minZ, sizeX, sizeZ);
                if (site.water == 0 && site.slope <= MAX_SLOPE
                    && site.groundY > ChunkProviderRts.BANK
                    && site.groundY + HEIGHT < 255) {
                    return new int[] { minX, minZ, site.groundY };
                }
            }
        }
        return null;
    }

    /**
     * Whether a footprint is inside the map and well clear of where the teams start, the town centers, towers,
     * settlements, gold mines, other ruins, the river and its bridges.
     */
    private static boolean clear(Game game, int minX, int minZ, int sizeX, int sizeZ) {
        int edge = 8;
        if (minX < game.getMinX() + edge || minZ < game.getMinZ() + edge
            || minX + sizeX - 1 > game.getMaxX() - edge
            || minZ + sizeZ - 1 > game.getMaxZ() - edge) {
            return false;
        }
        // Teams build out round where they start, so ruins keep further off from there.
        double baseSpacing = Math.max(48, game.getSize() * 0.12);
        double centerX = minX + sizeX / 2.0;
        double centerZ = minZ + sizeZ / 2.0;
        for (Team team : game.getTeams()) {
            int[] base = team.getBase();
            if (base != null) {
                double dx = base[0] - centerX;
                double dz = base[1] - centerZ;
                if (dx * dx + dz * dz < baseSpacing * baseSpacing) {
                    return false;
                }
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
            if (ruin.overlaps(minX, minZ, sizeX, sizeZ, SPACING)) {
                return false;
            }
        }
        if (MapFeatures.bridgeNear(game, minX, minZ, sizeX, sizeZ, BRIDGE_SPACING)) {
            return false;
        }
        Misthalin layout = GameWorld.layout(game);
        if (layout != null) {
            double reach = Misthalin.RIVER_HALF_WIDTH + 0.5 + RIVER_SPACING;
            for (int x = minX; x < minX + sizeX; x += Math.max(1, sizeX - 1)) {
                for (int z = minZ; z < minZ + sizeZ; z += Math.max(1, sizeZ - 1)) {
                    if (layout.riverDistance(x, z) < reach) {
                        return false;
                    }
                }
            }
            if (layout.riverDistance((int) centerX, (int) centerZ) < reach + Math.max(sizeX, sizeZ) / 2.0) {
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
            default:
                farmstead(plan, random);
                break;
        }
        overgrow(plan, random);
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
        for (int i = 0; i < ring.size(); i++) {
            int[] at = ring.get(i);
            boolean corner = (at[0] == x0 || at[0] == x1) && (at[1] == z0 || at[1] == z1);
            column(plan, at[0], at[1], corner ? 3 + random.nextInt(2) : heights[i], random);
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
        for (int x = 0; x < plan.width; x++) {
            for (int z = 0; z < plan.length; z++) {
                double dx = x - cx;
                double dz = z - cz;
                double distance = Math.sqrt(dx * dx + dz * dz);
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
