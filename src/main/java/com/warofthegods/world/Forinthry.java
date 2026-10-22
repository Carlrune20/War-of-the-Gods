package com.warofthegods.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The layout of a Forinthry map, the Wilderness burnt black in the God Wars: where its lava lakes, its green ponds,
 * its small lava mazes and its old fortresses lie. The lava rivers that wind between them follow from noise as the
 * terrain is made (see {@link ChunkProviderForinthry}). Everything keeps away from where the teams start. Like
 * {@link Misthalin}, everything here follows from the map area, the seed and how many teams play.
 */
public final class Forinthry {

    /** Blocks of path across a maze's cell, and of lava between two cells. */
    public static final int PATH = 3;
    public static final int WALL = 2;
    public static final int PITCH = PATH + WALL;
    /** How far round a maze, fortress or pool the ground eases from its level back to the land's. */
    public static final int EASE = 8;
    /** Blocks across the old fortress, as tools/structures/wilderness.py draws it. */
    public static final int KEEP_SIZE = 55;
    /** Blocks round where a team starts that nothing here comes nearer than. */
    public static final int BASE_ROOM = 44;

    private final MapArea area;
    private final List<Maze> mazes = new ArrayList<>();
    private final List<Pool> pools = new ArrayList<>();
    private final List<Keep> keeps = new ArrayList<>();
    private final double[][] bases;

    /**
     * A small lava maze: a square of level ground cut into a grid of paths by channels of lava, carved by the seed
     * into a maze with a few loops and a way in on each side. Its channels wander a block or two either way, so it
     * looks worn rather than laid out. A chest lies in its middle (see {@code ForinthryRuins}).
     */
    public static final class Maze {

        public final int minX;
        public final int minZ;
        public final int cells;
        /** Open walls: east of cell i, j at [i][j], and south of it at [cells + i][j]. */
        private final boolean[][] open;
        private final TerrainNoise warp;

        Maze(int minX, int minZ, int cells, boolean[][] open, long seed) {
            this.minX = minX;
            this.minZ = minZ;
            this.cells = cells;
            this.open = open;
            this.warp = new TerrainNoise(seed);
        }

        /** Blocks across, lava border included. */
        public int size() {
            return cells * PITCH + WALL;
        }

        public int getCenterX() {
            return minX + size() / 2;
        }

        public int getCenterZ() {
            return minZ + size() / 2;
        }

        public boolean covers(int x, int z, int margin) {
            return x >= minX - margin && x < minX + size() + margin && z >= minZ - margin && z < minZ + size() + margin;
        }

        /** Whether x, z is lava: a channel between cells that is not carved open, or the border bar its gateways. */
        public boolean isLava(int x, int z) {
            int size = size();
            int lx = x - minX;
            int lz = z - minZ;
            if (lx < 0 || lz < 0 || lx >= size || lz >= size) {
                return false;
            }
            // Inside the border the channels wander; the border itself stays put, so the maze keeps its shape.
            if (lx >= WALL && lz >= WALL && lx < size - WALL && lz < size - WALL) {
                lx += (int) Math.round(warp.fbm(x / 7.0, z / 7.0, 2) * 1.4);
                lz += (int) Math.round(warp.fbm(x / 7.0 + 50, z / 7.0 + 50, 2) * 1.4);
                lx = Math.max(WALL, Math.min(size - WALL - 1, lx));
                lz = Math.max(WALL, Math.min(size - WALL - 1, lz));
            }
            boolean wallX = lx % PITCH < WALL;
            boolean wallZ = lz % PITCH < WALL;
            int i = lx / PITCH;
            int j = lz / PITCH;
            if (wallX && wallZ) {
                return true;
            }
            int middle = cells / 2;
            if (wallX) {
                if (i == 0 || i == cells) {
                    return j != middle;
                }
                return !open[i - 1][j];
            }
            if (wallZ) {
                if (j == 0 || j == cells) {
                    return i != middle;
                }
                return !open[cells + i][j - 1];
            }
            return false;
        }
    }

    /**
     * A pool: a lake of lava, or a small pond of the Wilderness's murky water, its shore ragged. It lies level, one to
     * three deep, and the ground round it eases to its level so nothing spills.
     */
    public static final class Pool {

        public final int centerX;
        public final int centerZ;
        public final double radius;
        public final boolean lava;
        private final TerrainNoise edge;

        Pool(int centerX, int centerZ, double radius, boolean lava, long seed) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.lava = lava;
            this.edge = new TerrainNoise(seed);
        }

        /** How far x, z is from the middle as a share of the shore's distance there: under 1 is in the pool. */
        public double reach(int x, int z) {
            double d = Math.hypot(x - centerX, z - centerZ) / radius;
            return d + edge.fbm(x / 6.0, z / 6.0, 2) * 0.3;
        }

        /** How deep the pool is at x, z, 0 outside it. */
        public int depth(int x, int z) {
            double d = reach(x, z);
            return d >= 1 ? 0 : 1 + (int) Math.min(2, (1 - d) * 3);
        }

        public boolean covers(int x, int z, int margin) {
            double far = radius * 1.4 + margin;
            return Math.abs(x - centerX) <= far && Math.abs(z - centerZ) <= far;
        }
    }

    /** Where an old fortress stands: its square footprint and how it is turned. */
    public static final class Keep {

        public final int minX;
        public final int minZ;
        public final int rotation;

        Keep(int minX, int minZ, int rotation) {
            this.minX = minX;
            this.minZ = minZ;
            this.rotation = rotation;
        }

        public int getCenterX() {
            return minX + KEEP_SIZE / 2;
        }

        public int getCenterZ() {
            return minZ + KEEP_SIZE / 2;
        }

        public boolean covers(int x, int z, int margin) {
            return x >= minX - margin && x < minX + KEEP_SIZE + margin
                && z >= minZ - margin
                && z < minZ + KEEP_SIZE + margin;
        }
    }

    public Forinthry(MapArea area, long seed, int teams) {
        this.area = area;
        this.bases = GameWorld.basesOf(area, seed, teams);
        int size = Math.max(area.getMaxX() - area.getMinX(), area.getMaxZ() - area.getMinZ()) + 1;
        Random random = new Random(seed * 41 + 11);
        // One old fortress always; now and then a second, more often on a big map, and on the biggest a third.
        int keepCount = 1;
        if (random.nextDouble() < (size >= 1000 ? 0.5 : 0.15)) {
            keepCount++;
            if (size >= 2000 && random.nextDouble() < 0.15) {
                keepCount++;
            }
        }
        for (int attempt = 0; attempt < 600 && keeps.size() < keepCount; attempt++) {
            int[] spot = spot(random, size, KEEP_SIZE, EASE + 4);
            if (spot != null && roomFor(spot[0], spot[1], KEEP_SIZE, 24)) {
                keeps.add(new Keep(spot[0], spot[1], random.nextInt(4)));
            }
        }
        // Small mazes, one for about every 500 blocks square.
        int mazeCount = Math.max(1, (int) Math.round(size * (double) size / (500.0 * 500.0)));
        int cells = size < 1200 ? 4 : 5;
        int span = cells * PITCH + WALL;
        for (int attempt = 0; attempt < 400 && mazes.size() < mazeCount; attempt++) {
            int[] spot = spot(random, size, span, EASE + 4);
            if (spot != null && roomFor(spot[0], spot[1], span, 30)) {
                mazes.add(new Maze(spot[0], spot[1], cells, carve(cells, random), random.nextLong()));
            }
        }
        // Lava lakes, many, and a few green ponds.
        int lakes = Math.max(2, (int) Math.round(size * (double) size / (220.0 * 220.0)));
        int ponds = Math.max(1, (int) Math.round(size * (double) size / (320.0 * 320.0)));
        for (int attempt = 0; attempt < 1500 && pools.size() < lakes + ponds; attempt++) {
            boolean lava = pools.size() < lakes;
            double radius = lava ? 4 + random.nextDouble() * 7 : 3 + random.nextDouble() * 3.5;
            int reach = (int) Math.ceil(radius * 1.4);
            int[] spot = spot(random, size, 2 * reach, EASE);
            if (spot == null) {
                continue;
            }
            int cx = spot[0] + reach;
            int cz = spot[1] + reach;
            if (roomFor(cx - reach, cz - reach, 2 * reach, 10) && !nearPool(cx, cz, reach + 12)) {
                pools.add(new Pool(cx, cz, radius, lava, random.nextLong()));
            }
        }
    }

    /** A random corner for a square of the size inside the map, kept edge blocks in from it; null if it cannot fit. */
    private int[] spot(Random random, int size, int span, int edge) {
        int room = size - 2 * (24 + edge) - span;
        if (room <= 0) {
            return null;
        }
        return new int[] { area.getMinX() + 24 + edge + random.nextInt(room),
            area.getMinZ() + 24 + edge + random.nextInt(room) };
    }

    /** Whether a square there keeps clear of the bases, and spacing blocks clear of the fortresses and mazes. */
    private boolean roomFor(int minX, int minZ, int span, int spacing) {
        double cx = minX + span / 2.0;
        double cz = minZ + span / 2.0;
        for (double[] base : bases) {
            if (Math.hypot(base[0] - cx, base[1] - cz) < BASE_ROOM + span / 2.0 + EASE) {
                return false;
            }
        }
        for (Keep keep : keeps) {
            if (overlap(minX, minZ, span, keep.minX, keep.minZ, KEEP_SIZE, spacing)) {
                return false;
            }
        }
        for (Maze maze : mazes) {
            if (overlap(minX, minZ, span, maze.minX, maze.minZ, maze.size(), spacing)) {
                return false;
            }
        }
        for (Pool pool : pools) {
            int reach = (int) Math.ceil(pool.radius * 1.4);
            if (overlap(minX, minZ, span, pool.centerX - reach, pool.centerZ - reach, 2 * reach, spacing)) {
                return false;
            }
        }
        return true;
    }

    private static boolean overlap(int ax, int az, int aSpan, int bx, int bz, int bSpan, int margin) {
        return ax < bx + bSpan + margin && bx < ax + aSpan + margin
            && az < bz + bSpan + margin
            && bz < az + aSpan + margin;
    }

    private boolean nearPool(int x, int z, double distance) {
        for (Pool pool : pools) {
            if (Math.hypot(pool.centerX - x, pool.centerZ - z) < distance + pool.radius) {
                return true;
            }
        }
        return false;
    }

    /**
     * Carves a maze of cells by cells: a walk that opens a wall into a cell not yet reached, backing up when it is
     * stuck, then a few more walls knocked through so there is more than one way round.
     */
    static boolean[][] carve(int cells, Random random) {
        boolean[][] open = new boolean[cells * 2][cells];
        boolean[][] seen = new boolean[cells][cells];
        List<int[]> stack = new ArrayList<>();
        stack.add(new int[] { cells / 2, cells / 2 });
        seen[cells / 2][cells / 2] = true;
        int[][] steps = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!stack.isEmpty()) {
            int[] at = stack.get(stack.size() - 1);
            List<int[]> ways = new ArrayList<>();
            for (int[] step : steps) {
                int ni = at[0] + step[0];
                int nj = at[1] + step[1];
                if (ni >= 0 && nj >= 0 && ni < cells && nj < cells && !seen[ni][nj]) {
                    ways.add(step);
                }
            }
            if (ways.isEmpty()) {
                stack.remove(stack.size() - 1);
                continue;
            }
            int[] step = ways.get(random.nextInt(ways.size()));
            int ni = at[0] + step[0];
            int nj = at[1] + step[1];
            openBetween(open, cells, at[0], at[1], ni, nj);
            seen[ni][nj] = true;
            stack.add(new int[] { ni, nj });
        }
        for (int extra = 0; extra < cells - 1; extra++) {
            int i = random.nextInt(cells - 1);
            int j = random.nextInt(cells);
            if (random.nextBoolean()) {
                open[i][j] = true;
            } else {
                open[cells + j][i] = true;
            }
        }
        return open;
    }

    private static void openBetween(boolean[][] open, int cells, int i, int j, int ni, int nj) {
        if (ni != i) {
            open[Math.min(i, ni)][j] = true;
        } else {
            open[cells + i][Math.min(j, nj)] = true;
        }
    }

    public List<Maze> getMazes() {
        return Collections.unmodifiableList(mazes);
    }

    public List<Pool> getPools() {
        return Collections.unmodifiableList(pools);
    }

    public List<Keep> getKeeps() {
        return Collections.unmodifiableList(keeps);
    }

    /** The maze covering x, z, or reaching within margin blocks of it; null if none. */
    public Maze mazeAt(int x, int z, int margin) {
        for (Maze maze : mazes) {
            if (maze.covers(x, z, margin)) {
                return maze;
            }
        }
        return null;
    }

    /** The fortress covering x, z, or reaching within margin blocks of it; null if none. */
    public Keep keepAt(int x, int z, int margin) {
        for (Keep keep : keeps) {
            if (keep.covers(x, z, margin)) {
                return keep;
            }
        }
        return null;
    }

    /** The pool reaching within margin blocks of x, z; null if none. */
    public Pool poolAt(int x, int z, int margin) {
        for (Pool pool : pools) {
            if (pool.covers(x, z, margin)) {
                return pool;
            }
        }
        return null;
    }

    /** Whether a footprint comes within margin blocks of a maze, a fortress or a pool. */
    public boolean nearFeature(int minX, int minZ, int sizeX, int sizeZ, int margin) {
        for (Maze maze : mazes) {
            int size = maze.size();
            if (minX < maze.minX + size + margin && maze.minX < minX + sizeX + margin
                && minZ < maze.minZ + size + margin
                && maze.minZ < minZ + sizeZ + margin) {
                return true;
            }
        }
        for (Keep keep : keeps) {
            if (minX < keep.minX + KEEP_SIZE + margin && keep.minX < minX + sizeX + margin
                && minZ < keep.minZ + KEEP_SIZE + margin
                && keep.minZ < minZ + sizeZ + margin) {
                return true;
            }
        }
        for (Pool pool : pools) {
            int reach = (int) Math.ceil(pool.radius * 1.4) + margin;
            if (minX < pool.centerX + reach && pool.centerX - reach < minX + sizeX
                && minZ < pool.centerZ + reach
                && pool.centerZ - reach < minZ + sizeZ) {
                return true;
            }
        }
        return false;
    }

    /** Whether x, z is within margin blocks of the room kept round where a team starts. */
    public boolean nearBase(int x, int z, int margin) {
        for (double[] base : bases) {
            if (Math.hypot(base[0] - x, base[1] - z) < BASE_ROOM + margin) {
                return true;
            }
        }
        return false;
    }

    /** Where the teams' bases go, as x, z. */
    public double[][] getBases() {
        return bases.clone();
    }

    public MapArea getArea() {
        return area;
    }
}
