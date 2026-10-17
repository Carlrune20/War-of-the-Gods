package com.warofthegods.client.rts;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * What the viewer's team has seen of the map, for the fog of war in the RTS view. The map is cut into cells of
 * {@link #CELL} blocks. A cell is visible while one of the team's or its allies' units, players or buildings is near
 * it; once seen it stays explored, so its ground shows, dimmed, after they move on, while what moves there is hidden.
 * Cells nobody has been near are black. Without a team, as an observer, and once the game is over, there is no fog.
 */
final class FogOfWar {

    /** Blocks across a cell. */
    static final int CELL = 4;
    /** How far units, players and buildings see, in blocks; watch towers and town centers see further. */
    private static final double UNIT_SIGHT = 36;
    private static final double PLAYER_SIGHT = 48;
    private static final double BUILDING_SIGHT = 40;
    private static final double LOOKOUT_SIGHT = 60;
    /** Client ticks between working out what is visible. */
    private static final int INTERVAL = 5;

    private int minX;
    private int minZ;
    private int columns;
    private int rows;
    private boolean[] explored = new boolean[0];
    private boolean[] visible = new boolean[0];
    private int game = -1;
    private boolean on;
    private int ticks;
    /** Bumped whenever what is explored or visible changes, so the fog's look is only made again then. */
    private int version;

    /** Whether there is fog at all: the viewer is on a team in a game that goes on. */
    boolean isOn() {
        return on;
    }

    int version() {
        return version;
    }

    /** Works out what the viewer's team sees now; call every client tick. */
    void update(RtsClient client) {
        RtsState state = client.state;
        RtsState.GameInfo info = state.game;
        RtsState.TeamInfo team = state.myTeam();
        on = info != null && team != null && state.winners == null;
        if (info == null) {
            return;
        }
        if (info.number != game || info.minX != minX || info.minZ != minZ) {
            game = info.number;
            minX = info.minX;
            minZ = info.minZ;
            columns = (info.maxX - info.minX) / CELL + 1;
            rows = (info.maxZ - info.minZ) / CELL + 1;
            explored = new boolean[columns * rows];
            visible = new boolean[columns * rows];
            version++;
            ticks = 0;
        }
        if (!on || ticks++ % INTERVAL != 0) {
            return;
        }
        Set<String> friends = new HashSet<>(team.allies);
        friends.add(team.id);
        boolean[] now = new boolean[visible.length];
        for (RtsState.UnitInfo unit : state.units.values()) {
            if (friends.contains(unit.team)) {
                double[] at = client.positionOf(unit, 1);
                see(now, at[0], at[2], UNIT_SIGHT);
            }
        }
        for (RtsState.PlayerInfo player : state.players.values()) {
            if (friends.contains(player.team)) {
                see(now, player.x, player.z, PLAYER_SIGHT);
            }
        }
        for (RtsState.BuildingInfo building : state.buildings.values()) {
            if (friends.contains(building.team)) {
                boolean lookout = building.complete
                    && ("tower".equals(building.type) || "town_hall".equals(building.type) || building.range > 0);
                see(
                    now,
                    building.x + building.sizeX / 2.0,
                    building.z + building.sizeZ / 2.0,
                    (lookout ? LOOKOUT_SIGHT : BUILDING_SIGHT) + Math.max(building.sizeX, building.sizeZ) / 2.0);
            }
        }
        boolean changed = !Arrays.equals(now, visible);
        for (int i = 0; i < now.length; i++) {
            if (now[i] && !explored[i]) {
                explored[i] = true;
                changed = true;
            }
        }
        visible = now;
        if (changed) {
            version++;
        }
    }

    /** Marks the cells within range of x, z visible. */
    private void see(boolean[] now, double x, double z, double range) {
        int c0 = Math.max(0, (int) Math.floor((x - range - minX) / CELL));
        int c1 = Math.min(columns - 1, (int) Math.floor((x + range - minX) / CELL));
        int r0 = Math.max(0, (int) Math.floor((z - range - minZ) / CELL));
        int r1 = Math.min(rows - 1, (int) Math.floor((z + range - minZ) / CELL));
        double rangeSq = range * range;
        for (int r = r0; r <= r1; r++) {
            double dz = minZ + r * CELL + CELL / 2.0 - z;
            for (int c = c0; c <= c1; c++) {
                double dx = minX + c * CELL + CELL / 2.0 - x;
                if (dx * dx + dz * dz <= rangeSq) {
                    now[r * columns + c] = true;
                }
            }
        }
    }

    private int index(double x, double z) {
        int c = (int) Math.floor((x - minX) / CELL);
        int r = (int) Math.floor((z - minZ) / CELL);
        return c < 0 || r < 0 || c >= columns || r >= rows ? -1 : r * columns + c;
    }

    /** Whether the team sees x, z now; everywhere when there is no fog, nowhere off the map. */
    boolean isVisible(double x, double z) {
        if (!on) {
            return true;
        }
        int i = index(x, z);
        return i >= 0 && visible[i];
    }

    /** Whether the team has seen x, z; everywhere when there is no fog. */
    boolean isExplored(double x, double z) {
        if (!on) {
            return true;
        }
        int i = index(x, z);
        return i >= 0 && explored[i];
    }

    /** Whether any of a footprint has been seen. */
    boolean isExplored(int x, int z, int sizeX, int sizeZ) {
        if (!on) {
            return true;
        }
        for (int bx = x; bx < x + sizeX; bx += CELL) {
            for (int bz = z; bz < z + sizeZ; bz += CELL) {
                if (isExplored(bx, bz)) {
                    return true;
                }
            }
        }
        return isExplored(x + sizeX - 1, z + sizeZ - 1);
    }

    /** The corner of the cell holding x, z, in blocks. */
    int cellX(double x) {
        return minX + (int) Math.floor((x - minX) / CELL) * CELL;
    }

    int cellZ(double z) {
        return minZ + (int) Math.floor((z - minZ) / CELL) * CELL;
    }
}
