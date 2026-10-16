package com.warofthegods.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The layout of a Misthalin map: grassland and forest with a river running right across it, crossed by three stone
 * bridges. The river runs straight along x or z, wherever it passes furthest from the teams' bases, and wanders a
 * little between the bridges; at each bridge it runs straight for a stretch, exactly as wide as the bridge spans.
 * Everything here follows from the map area, the seed and how many teams play, so the chunk generator and the server
 * agree on it without saving anything.
 */
public final class Misthalin {

    /** Water runs this far either side of the river's middle line, so the river is 7 blocks wide. */
    public static final int RIVER_HALF_WIDTH = 3;
    /** The bridge schematic, unrotated: across the river along z. */
    public static final int BRIDGE_WIDTH = 7;
    public static final int BRIDGE_LENGTH = 17;
    /** How far along the river either side of a bridge it runs straight. */
    private static final int STRAIGHT = 10;
    /** How far along the river after that it takes to wander off again. */
    private static final int EASE = 14;

    private final MapArea area;
    /** Whether the river runs along x, so bridges cross it along z. */
    private final boolean alongX;
    /** Where the river's middle line lies across the map: z if it runs along x, else x. */
    private final int line;
    private final double amplitude;
    private final double wavelength;
    private final TerrainNoise noise;
    private final List<Bridge> bridges = new ArrayList<>();
    private final double[][] bases;

    /** A bridge: its footprint and the quarter turn its schematic is pasted with. */
    public static final class Bridge {

        public final int minX;
        public final int minZ;
        public final int sizeX;
        public final int sizeZ;
        public final int rotation;

        Bridge(int minX, int minZ, int sizeX, int sizeZ, int rotation) {
            this.minX = minX;
            this.minZ = minZ;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.rotation = rotation;
        }

        public boolean covers(int x, int z) {
            return x >= minX && x < minX + sizeX && z >= minZ && z < minZ + sizeZ;
        }
    }

    public Misthalin(MapArea area, long seed, int teams) {
        this.area = area;
        this.noise = new TerrainNoise(seed * 13 + 5);
        int size = Math.max(area.getMaxX() - area.getMinX(), area.getMaxZ() - area.getMinZ()) + 1;
        // The line along x or z, near the middle, that passes furthest from every team's base.
        this.bases = basesOf(area, size, seed, teams);
        boolean bestAlongX = true;
        int bestLine = area.getCenterZ();
        double bestDistance = -1;
        int reach = (int) (size * 0.12);
        for (int axis = 0; axis < 2; axis++) {
            int center = axis == 0 ? area.getCenterZ() : area.getCenterX();
            for (int offset = 0; offset <= reach; offset++) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    int candidate = center + sign * offset;
                    double nearest = Double.MAX_VALUE;
                    for (double[] base : bases) {
                        nearest = Math.min(nearest, Math.abs((axis == 0 ? base[1] : base[0]) - candidate));
                    }
                    if (nearest > bestDistance + 0.5) {
                        bestDistance = nearest;
                        bestAlongX = axis == 0;
                        bestLine = candidate;
                    }
                }
            }
        }
        this.alongX = bestAlongX;
        this.line = bestLine;
        // It wanders no closer to a base than about 24 blocks, and at most a twentieth of the map.
        this.amplitude = Math.max(0, Math.min(size * 0.05, (bestDistance - 24) * 0.5));
        this.wavelength = Math.max(60, size * 0.3);
        int middle = alongX ? area.getCenterX() : area.getCenterZ();
        int spacing = (int) Math.round(size * 0.3);
        for (int k = -1; k <= 1; k++) {
            int u = middle + k * spacing;
            int v = line + (int) Math.round(wander(u));
            int half = BRIDGE_LENGTH / 2;
            if (alongX) {
                bridges.add(new Bridge(u - BRIDGE_WIDTH / 2, v - half, BRIDGE_WIDTH, BRIDGE_LENGTH, 0));
            } else {
                bridges.add(new Bridge(v - half, u - BRIDGE_WIDTH / 2, BRIDGE_LENGTH, BRIDGE_WIDTH, 1));
            }
        }
    }

    /** Where the teams' bases go, as x, z; the same spots {@code Units.assignBase} puts them on. */
    private static double[][] basesOf(MapArea area, int size, long seed, int teams) {
        int n = Math.max(2, teams);
        double[][] bases = new double[Math.max(1, teams)][];
        for (int i = 0; i < bases.length; i++) {
            bases[i] = baseSpot(area.getCenterX(), area.getCenterZ(), size, seed, i, n);
        }
        return bases;
    }

    /**
     * Where the base of team i of n goes, as x, z: round a circle about the middle of the map, the teams spread
     * evenly round it, but each pushed a little round and in or out by the seed, so no two maps start the same.
     */
    public static double[] baseSpot(int centerX, int centerZ, int size, long seed, int i, int n) {
        Random random = new Random(seed * 6364136223846793005L + i * 1442695040888963407L + 17);
        double angle = -Math.PI / 2 + 2 * Math.PI * i / n + (random.nextDouble() * 2 - 1) * 0.3 * Math.PI / n;
        double radius = size * (0.25 + random.nextDouble() * 0.1);
        return new double[] { centerX + Math.cos(angle) * radius, centerZ + Math.sin(angle) * radius };
    }

    /** Where the teams' bases go, as x, z. */
    public double[][] getBases() {
        return bases.clone();
    }

    public List<Bridge> getBridges() {
        return Collections.unmodifiableList(bridges);
    }

    /** The bridge with a block at x, z, or null. */
    public Bridge bridgeAt(int x, int z) {
        for (Bridge bridge : bridges) {
            if (bridge.covers(x, z)) {
                return bridge;
            }
        }
        return null;
    }

    /** How far the river's middle line strays from its straight line at u, along the river. */
    private double wander(double u) {
        return amplitude * noise.fbm(u / wavelength, 0.5, 2) * 1.6;
    }

    /** The river's middle line at u, held straight and on a whole block at each bridge. */
    private double middleAt(double u) {
        double v = line + wander(u);
        for (Bridge bridge : bridges) {
            double bridgeU = alongX ? bridge.minX + BRIDGE_WIDTH / 2 : bridge.minZ + BRIDGE_WIDTH / 2;
            double bridgeV = alongX ? bridge.minZ + BRIDGE_LENGTH / 2 : bridge.minX + BRIDGE_LENGTH / 2;
            double t = Math.max(0, Math.min(1, (Math.abs(u - bridgeU) - STRAIGHT) / EASE));
            double smooth = t * t * (3 - 2 * t);
            v = bridgeV + (v - bridgeV) * smooth;
        }
        return v;
    }

    /** How far x, z is from the river's middle line, across the river. */
    public double riverDistance(int x, int z) {
        double u = alongX ? x : z;
        double v = alongX ? z : x;
        return Math.abs(v - middleAt(u));
    }

    /** Whether x, z is under the river's water. */
    public boolean isRiver(int x, int z) {
        return riverDistance(x, z) <= RIVER_HALF_WIDTH + 0.5;
    }

    /**
     * 0 on the level ground a bridge's ends sit on, rising to 1 a few blocks off; terrain there is flattened to the
     * river banks so units walk straight onto the bridge.
     */
    public double bridgeApproach(int x, int z) {
        double nearest = 1;
        for (Bridge bridge : bridges) {
            double centerX = bridge.minX + bridge.sizeX / 2;
            double centerZ = bridge.minZ + bridge.sizeZ / 2;
            double along = alongX ? Math.abs(x - centerX) : Math.abs(z - centerZ);
            double across = alongX ? Math.abs(z - centerZ) : Math.abs(x - centerX);
            double t = Math.max((along - BRIDGE_WIDTH / 2 - 2) / 5, (across - BRIDGE_LENGTH / 2 - 3) / 6);
            nearest = Math.min(nearest, Math.max(0, Math.min(1, t)));
        }
        return nearest;
    }

    public MapArea getArea() {
        return area;
    }
}
