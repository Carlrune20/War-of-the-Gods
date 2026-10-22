package com.warofthegods.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The layout of a Forinthry map, the Wilderness burnt black in the God Wars: where its lava lakes, its green ponds,
 * its old fortresses and the mushroom patches round each team's start lie. The lava rivers that wind between them
 * follow from noise as the
 * terrain is made (see {@link ChunkProviderForinthry}). Everything keeps away from where the teams start. Like
 * {@link Misthalin}, everything here follows from the map area, the seed and how many teams play.
 */
public final class Forinthry {

    /** How far round a fortress or pool the ground eases from its level back to the land's. */
    public static final int EASE = 8;
    /** Blocks across the old fortress, as tools/structures/wilderness.py draws it, and how many variants it draws. */
    public static final int KEEP_SIZE = 55;
    public static final int KEEP_VARIANTS = 3;
    /** Blocks square of map to each old fortress, and how far apart their corners keep at least. */
    private static final int KEEP_AREA = 500;
    private static final int KEEP_SPACING = 160;
    /** Blocks round where a team starts that nothing here comes nearer than. */
    public static final int BASE_ROOM = 44;

    private final MapArea area;
    private final List<Pool> pools = new ArrayList<>();
    private final List<Keep> keeps = new ArrayList<>();
    private final double[][] bases;
    private final List<int[]> mushrooms = new ArrayList<>();

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

    /** Where an old fortress stands: its square footprint, how it is turned, and which variant it is (from 1). */
    public static final class Keep {

        public final int minX;
        public final int minZ;
        public final int rotation;
        public final int variant;

        Keep(int minX, int minZ, int rotation, int variant) {
            this.minX = minX;
            this.minZ = minZ;
            this.rotation = rotation;
            this.variant = variant;
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
        // About one old fortress for every KEEP_AREA blocks square of map, one at least, spread apart; their
        // variants taken in turn from a random one, so neighbours differ.
        double share = size * (double) size / ((double) KEEP_AREA * KEEP_AREA) * (0.8 + random.nextDouble() * 0.4);
        int keepCount = Math.max(1, (int) Math.round(share));
        int variant = random.nextInt(KEEP_VARIANTS);
        for (int attempt = 0; attempt < 300 * keepCount && keeps.size() < keepCount; attempt++) {
            int[] spot = spot(random, size, KEEP_SIZE, EASE + 4);
            if (spot != null && roomFor(spot[0], spot[1], KEEP_SIZE, 24) && farFromKeeps(spot[0], spot[1])) {
                keeps.add(new Keep(spot[0], spot[1], random.nextInt(4), 1 + variant++ % KEEP_VARIANTS));
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
        // No animals graze on Forinthry, so mushrooms grow round where each team starts: a ring of patches for its
        // first food.
        for (double[] base : bases) {
            int patches = 7;
            double turn = random.nextDouble() * Math.PI * 2;
            for (int i = 0; i < patches; i++) {
                double angle = turn + i * Math.PI * 2 / patches + (random.nextDouble() - 0.5) * 0.5;
                double distance = 18 + random.nextDouble() * 14;
                mushrooms.add(
                    new int[] { (int) Math.round(base[0] + Math.cos(angle) * distance),
                        (int) Math.round(base[1] + Math.sin(angle) * distance) });
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

    /** Whether a square there keeps clear of the bases, and spacing blocks clear of the fortresses and pools. */
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
        for (Pool pool : pools) {
            int reach = (int) Math.ceil(pool.radius * 1.4);
            if (overlap(minX, minZ, span, pool.centerX - reach, pool.centerZ - reach, 2 * reach, spacing)) {
                return false;
            }
        }
        return true;
    }

    private boolean farFromKeeps(int minX, int minZ) {
        for (Keep keep : keeps) {
            if (Math.hypot(keep.minX - minX, keep.minZ - minZ) < KEEP_SPACING) {
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

    /** The middles of the mushroom patches round where the teams start. */
    public List<int[]> getMushroomPatches() {
        return Collections.unmodifiableList(mushrooms);
    }

    public List<Pool> getPools() {
        return Collections.unmodifiableList(pools);
    }

    public List<Keep> getKeeps() {
        return Collections.unmodifiableList(keeps);
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

    /** Whether a footprint comes within margin blocks of a fortress or a pool. */
    public boolean nearFeature(int minX, int minZ, int sizeX, int sizeZ, int margin) {
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
