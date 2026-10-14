package com.warofthegods.unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.warofthegods.game.Building;
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Game;
import com.warofthegods.game.Team;

/**
 * Long-range paths across the map. Minecraft's own pathfinder only looks a few dozen blocks ahead, so a unit sent
 * across the map plans its whole way here first, over the ground surface block by block: around lakes, hills too
 * steep to climb, tree trunks and buildings. It then walks the route as a few waypoints, with the vanilla
 * pathfinder doing each short stretch. Chunks nobody has loaded are taken as open ground, and the route is planned
 * again once the unit gets there.
 */
public final class Routes {

    /** Most blocks one search may look at. */
    private static final int MAX_NODES = 40000;
    /** Most searches per server tick, across all units. */
    private static final int SEARCHES_PER_TICK = 3;
    /** How far apart waypoints may be. */
    private static final int WAYPOINT_SPACING = 16;
    /** Blocks around the straight line from start to goal a search may stray into. */
    private static final int MARGIN = 48;

    private static final int BLOCKED = Integer.MIN_VALUE;
    private static final int UNKNOWN = Integer.MIN_VALUE + 1;
    private static final int[][] STEPS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 },
        { -1, -1 } };

    private static long searchTick = -1;
    private static int searches;

    private Routes() {}

    /**
     * Waypoints from x0, z0 to x1, z1 (the goal last), as {x, z} block centres; null when there is no time for a
     * search this tick or no way was found, in which case the unit heads straight for the goal as before.
     */
    public static List<double[]> find(World world, Game game, double fromX, double fromZ, double toX, double toZ) {
        return find(world, game, null, fromX, fromZ, toX, toZ);
    }

    /**
     * Waypoints for a unit of a team, which may go through the gates of its own team and its allies; null for no
     * team, which goes through none.
     */
    public static List<double[]> find(World world, Game game, String team, double fromX, double fromZ, double toX,
        double toZ) {
        long tick = world.getTotalWorldTime();
        if (tick != searchTick) {
            searchTick = tick;
            searches = 0;
        }
        if (++searches > SEARCHES_PER_TICK) {
            return null;
        }
        return new Search(world, game, team, floor(fromX), floor(fromZ), floor(toX), floor(toZ)).run();
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static final class Node implements Comparable<Node> {

        final int x;
        final int z;
        final double cost;
        final double estimate;

        Node(int x, int z, double cost, double estimate) {
            this.x = x;
            this.z = z;
            this.cost = cost;
            this.estimate = estimate;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(cost + estimate, other.cost + other.estimate);
        }
    }

    private static final class Search {

        final World world;
        final Game game;
        final int startX;
        final int startZ;
        final int goalX;
        final int goalZ;
        final int minX;
        final int minZ;
        final int maxX;
        final int maxZ;
        final Map<Long, Integer> heights = new HashMap<>();
        final Map<Long, Long> cameFrom = new HashMap<>();
        final Map<Long, Double> best = new HashMap<>();
        final List<Building> buildings = new ArrayList<>();

        Search(World world, Game game, String team, int startX, int startZ, int goalX, int goalZ) {
            this.world = world;
            this.game = game;
            this.startX = startX;
            this.startZ = startZ;
            this.goalX = goalX;
            this.goalZ = goalZ;
            minX = Math.max(game.getMinX(), Math.min(startX, goalX) - MARGIN);
            maxX = Math.min(game.getMaxX(), Math.max(startX, goalX) + MARGIN);
            minZ = Math.max(game.getMinZ(), Math.min(startZ, goalZ) - MARGIN);
            maxZ = Math.min(game.getMaxZ(), Math.max(startZ, goalZ) + MARGIN);
            Team own = team == null ? null : game.getTeam(team);
            for (Building building : game.getBuildings()) {
                // A unit sent onto a building's footprint, such as into a farm, may go in. A gate of its own team or
                // an ally lets it through its opening.
                Team owner = game.getTeam(building.team);
                if (building.type == BuildingType.GATE && own != null
                    && owner != null
                    && !game.areEnemies(own, owner)) {
                    continue;
                }
                if (building.getShown() != null && !building.covers(goalX + 0.5, goalZ + 0.5, 0)
                    && !building.covers(startX + 0.5, startZ + 0.5, 0)) {
                    buildings.add(building);
                }
            }
        }

        static long key(int x, int z) {
            return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        }

        List<double[]> run() {
            PriorityQueue<Node> open = new PriorityQueue<>();
            open.add(new Node(startX, startZ, 0, estimate(startX, startZ)));
            best.put(key(startX, startZ), 0.0);
            int visited = 0;
            while (!open.isEmpty() && visited < MAX_NODES) {
                Node node = open.poll();
                long nodeKey = key(node.x, node.z);
                if (node.cost > best.get(nodeKey)) {
                    continue;
                }
                visited++;
                if (node.x == goalX && node.z == goalZ) {
                    return waypoints(nodeKey);
                }
                int height = height(node.x, node.z);
                for (int[] step : STEPS) {
                    int x = node.x + step[0];
                    int z = node.z + step[1];
                    if (x < minX || x > maxX || z < minZ || z > maxZ) {
                        continue;
                    }
                    double cost = stepCost(height, node.x, node.z, x, z);
                    if (cost < 0) {
                        continue;
                    }
                    if (step[0] != 0 && step[1] != 0) {
                        // No cutting corners past something in the way.
                        if (stepCost(height, node.x, node.z, node.x + step[0], node.z) < 0
                            || stepCost(height, node.x, node.z, node.x, node.z + step[1]) < 0) {
                            continue;
                        }
                        cost *= 1.4142;
                    }
                    long nextKey = key(x, z);
                    double total = node.cost + cost;
                    Double known = best.get(nextKey);
                    if (known == null || total < known) {
                        best.put(nextKey, total);
                        cameFrom.put(nextKey, nodeKey);
                        open.add(new Node(x, z, total, estimate(x, z)));
                    }
                }
            }
            return null;
        }

        double estimate(int x, int z) {
            int dx = Math.abs(goalX - x);
            int dz = Math.abs(goalZ - z);
            return Math.max(dx, dz) + 0.4142 * Math.min(dx, dz);
        }

        /** What one step from a block at the given height to x, z costs; negative when it cannot be taken. */
        double stepCost(int fromHeight, int fromX, int fromZ, int x, int z) {
            int height = height(x, z);
            if (height == BLOCKED) {
                return -1;
            }
            if (height == UNKNOWN || fromHeight == UNKNOWN) {
                return 1.2;
            }
            int ground = height & 0xFFFF;
            int climb = ground - (fromHeight & 0xFFFF);
            if (climb > 1) {
                return -1;
            }
            // Dropping more than three blocks hurts; going round is better.
            if (climb < -3) {
                return -1;
            }
            boolean water = (height & 0x10000) != 0;
            return water ? 4 : climb != 0 ? 1.5 : 1;
        }

        /**
         * The block a unit stands on at x, z, with bit 16 set for water; BLOCKED for a tree trunk, lava or a
         * building, UNKNOWN where the chunk is not loaded.
         */
        int height(int x, int z) {
            long k = key(x, z);
            Integer known = heights.get(k);
            if (known != null) {
                return known;
            }
            int value = look(x, z);
            heights.put(k, value);
            return value;
        }

        int look(int x, int z) {
            if (world instanceof WorldServer
                && !((WorldServer) world).theChunkProviderServer.chunkExists(x >> 4, z >> 4)) {
                return UNKNOWN;
            }
            for (Building building : buildings) {
                if (building.covers(x + 0.5, z + 0.5, 0)) {
                    return BLOCKED;
                }
            }
            int y = world.getHeightValue(x, z) - 1;
            while (y > 0) {
                Block block = world.getBlock(x, y, z);
                Material material = block.getMaterial();
                if (material == Material.leaves || material == Material.air
                    || material == Material.plants
                    || material == Material.vine
                    || material == Material.snow
                    || !material.blocksMovement() && !material.isLiquid()) {
                    y--;
                    continue;
                }
                if (block == Blocks.fence_gate) {
                    // The opening of a gate, which opens as the unit comes up to it.
                    y--;
                    continue;
                }
                if (Trees.isLog(block) || material == Material.lava || material == Material.cactus) {
                    return BLOCKED;
                }
                return material.isLiquid() ? (y | 0x10000) : y;
            }
            return BLOCKED;
        }

        /** The route back from the goal, thinned out to waypoints the unit can walk between in a straight line. */
        List<double[]> waypoints(long goalKey) {
            List<long[]> path = new ArrayList<>();
            Long at = goalKey;
            while (at != null) {
                path.add(0, new long[] { at >> 32, (int) (long) at });
                at = cameFrom.get(at);
            }
            List<double[]> points = new ArrayList<>();
            int anchor = 0;
            while (anchor < path.size() - 1) {
                int next = anchor + 1;
                for (int i = anchor + 2; i < path.size() && i - anchor <= WAYPOINT_SPACING * 2; i++) {
                    if (distance(path.get(anchor), path.get(i)) > WAYPOINT_SPACING
                        || !straight(path.get(anchor), path.get(i))) {
                        break;
                    }
                    next = i;
                }
                points.add(new double[] { path.get(next)[0] + 0.5, path.get(next)[1] + 0.5 });
                anchor = next;
            }
            return points;
        }

        static double distance(long[] a, long[] b) {
            return Math.hypot(a[0] - b[0], a[1] - b[1]);
        }

        /** Whether a unit can walk straight from a to b: every block on the line between can be stepped onto. */
        boolean straight(long[] a, long[] b) {
            double dx = b[0] - a[0];
            double dz = b[1] - a[1];
            int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dz)) * 2);
            int lastX = (int) a[0];
            int lastZ = (int) a[1];
            for (int i = 1; i <= steps; i++) {
                int x = (int) Math.floor(a[0] + 0.5 + dx * i / steps);
                int z = (int) Math.floor(a[1] + 0.5 + dz * i / steps);
                if (x == lastX && z == lastZ) {
                    continue;
                }
                if (stepCost(height(lastX, lastZ), lastX, lastZ, x, z) < 0) {
                    return false;
                }
                lastX = x;
                lastZ = z;
            }
            return true;
        }
    }
}
