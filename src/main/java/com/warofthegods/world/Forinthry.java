package com.warofthegods.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The layout of a Forinthry map, the Wilderness burnt black in the God Wars: where its lava mazes lie. A maze is a
 * square of level ground cut into a grid of paths three blocks wide by channels of lava two wide, carved by the map's
 * seed into a maze with a few loops in it and a way in on each side. A chest lies in its middle (see
 * {@code ForinthryRuins}). Mazes keep away from where the teams start and from each other. Like {@link Misthalin},
 * everything here follows from the map area, the seed and how many teams play.
 */
public final class Forinthry {

    /** Blocks of path across a cell, and of lava between two cells. */
    public static final int PATH = 3;
    public static final int WALL = 2;
    public static final int PITCH = PATH + WALL;
    /** How far round a maze the ground eases from the maze's level back to the land's. */
    public static final int EASE = 8;

    private final MapArea area;
    private final List<Maze> mazes = new ArrayList<>();
    private final double[][] bases;

    /** One lava maze: its corner, its cells across, and which walls between its cells are open. */
    public static final class Maze {

        public final int minX;
        public final int minZ;
        public final int cells;
        /** Open walls: east of cell i, j at [i][j], and south of it at [cells + i][j]. */
        private final boolean[][] open;

        Maze(int minX, int minZ, int cells, boolean[][] open) {
            this.minX = minX;
            this.minZ = minZ;
            this.cells = cells;
            this.open = open;
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
            int lx = x - minX;
            int lz = z - minZ;
            int size = size();
            if (lx < 0 || lz < 0 || lx >= size || lz >= size) {
                return false;
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
                // A channel between cell i - 1 and cell i in row j, or the west or east border.
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

    public Forinthry(MapArea area, long seed, int teams) {
        this.area = area;
        this.bases = GameWorld.basesOf(area, seed, teams);
        int size = Math.max(area.getMaxX() - area.getMinX(), area.getMaxZ() - area.getMinZ()) + 1;
        Random random = new Random(seed * 41 + 11);
        // About one maze for every 300 blocks square, at least two; bigger on bigger maps.
        int count = Math.max(2, (int) Math.round(size * (double) size / (300.0 * 300.0)));
        int cells = size < 400 ? 5 : size < 1200 ? 7 : 9;
        int span = cells * PITCH + WALL;
        int edge = 24 + EASE;
        double baseSpacing = Math.max(48, size * 0.12) + span / 2.0;
        for (int attempt = 0; attempt < 400 && mazes.size() < count; attempt++) {
            int room = size - 2 * edge - span;
            if (room <= 0) {
                break;
            }
            int minX = area.getMinX() + edge + random.nextInt(room);
            int minZ = area.getMinZ() + edge + random.nextInt(room);
            double cx = minX + span / 2.0;
            double cz = minZ + span / 2.0;
            boolean ok = true;
            for (double[] base : bases) {
                ok &= Math.hypot(base[0] - cx, base[1] - cz) > baseSpacing;
            }
            for (Maze other : mazes) {
                ok &= Math.hypot(other.getCenterX() - cx, other.getCenterZ() - cz) > span + 48;
            }
            // Not right in the middle either, where every team's path crosses.
            ok &= Math.hypot(area.getCenterX() - cx, area.getCenterZ() - cz) > span * 0.75;
            if (ok) {
                mazes.add(new Maze(minX, minZ, cells, carve(cells, random)));
            }
        }
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
        for (int extra = 0; extra < cells; extra++) {
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

    /** The maze covering x, z, or reaching within margin blocks of it; null if none. */
    public Maze mazeAt(int x, int z, int margin) {
        for (Maze maze : mazes) {
            if (maze.covers(x, z, margin)) {
                return maze;
            }
        }
        return null;
    }

    /** Whether a footprint comes within margin blocks of a maze. */
    public boolean nearMaze(int minX, int minZ, int sizeX, int sizeZ, int margin) {
        for (Maze maze : mazes) {
            int size = maze.size();
            if (minX < maze.minX + size + margin && maze.minX < minX + sizeX + margin
                && minZ < maze.minZ + size + margin
                && maze.minZ < minZ + sizeZ + margin) {
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
