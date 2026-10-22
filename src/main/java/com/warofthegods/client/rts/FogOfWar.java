package com.warofthegods.client.rts;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;

/**
 * What the viewer's team has seen of the map, for the fog of war in the RTS view. The map is cut into cells of
 * {@link #CELL} blocks. A cell is visible while one of the team's or its allies' units, players or buildings is near
 * it; once seen it stays explored, so its ground and the enemy buildings found there show after they move on, while
 * what moves there is hidden. The RTS view draws the world lit by this: visible ground in full light, explored ground
 * at half and the rest pitch dark. Walking the map on foot, out of the RTS view, explores it too.
 * Without a team, as an observer, and once the game is over, there is no fog, nor while /rts seeall lifts it; /rts
 * reveal marks the whole map explored for a team.
 */
final class FogOfWar {

    /** Blocks across a cell. */
    static final int CELL = 4;
    /** How far units, players and buildings see, in blocks; watch towers and town centers see further. */
    private static final double UNIT_SIGHT = 36;
    private static final double PLAYER_SIGHT = 48;
    private static final double BUILDING_SIGHT = 40;
    private static final double LOOKOUT_SIGHT = 60;
    /** Armadyl's fliers see farther than units on the ground, and his observatory farther than a watch tower. */
    private static final double FLIER_SIGHT = UNIT_SIGHT + 12;
    private static final double OBSERVATORY_SIGHT = LOOKOUT_SIGHT * 2;
    /** Client ticks between working out what is visible. */
    private static final int INTERVAL = 5;
    /** Sky and block light, packed as the lightmap takes them, for ground in sight and ground only explored. */
    private static final int FULL_LIGHT = 15 << 20 | 15 << 4;
    private static final int HALF_LIGHT = 12 << 20;

    private int minX;
    private int minZ;
    private int columns;
    private int rows;
    private boolean[] explored = new boolean[0];
    private boolean[] visible = new boolean[0];
    private int game = -1;
    private boolean on;
    /** Whether the whole map has been marked explored, as /rts reveal asked. */
    private boolean revealed;
    private int ticks;
    /** Bumped whenever what is explored or visible changes, so the fog's look is only made again then. */
    private int version;
    /** Cells, counted from 0, 0, the viewer walked near on foot before the map was known here. */
    private final Set<Long> walked = new HashSet<>();
    /** Chunk columns, as x << 32 | z, whose light changed since they were last drawn again; or all of them. */
    private final Set<Long> changedChunks = new HashSet<>();
    private boolean changedAll;

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
        on = info != null && team != null && state.winners == null && !state.seeAll;
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
            revealed = false;
            version++;
            changedAll = true;
            ticks = 0;
        }
        if (!walked.isEmpty()) {
            for (long cell : walked) {
                see(explored, (int) (cell >> 32) * CELL + CELL / 2.0, (int) cell * CELL + CELL / 2.0, PLAYER_SIGHT);
            }
            walked.clear();
            version++;
            changedAll = true;
        }
        if (team != null && team.revealed && !revealed) {
            Arrays.fill(explored, true);
            revealed = true;
            version++;
            changedAll = true;
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
                see(now, at[0], at[2], unit.flies() ? FLIER_SIGHT : UNIT_SIGHT);
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
                double sight = building.complete && "observatory".equals(building.type) ? OBSERVATORY_SIGHT
                    : lookout ? LOOKOUT_SIGHT : BUILDING_SIGHT;
                see(
                    now,
                    building.x + building.sizeX / 2.0,
                    building.z + building.sizeZ / 2.0,
                    sight + Math.max(building.sizeX, building.sizeZ) / 2.0);
            }
        }
        if (team.eye != null) {
            see(now, team.eye[0], team.eye[1], team.eye[2]);
        }
        boolean changed = false;
        for (int i = 0; i < now.length; i++) {
            if (now[i] != visible[i]) {
                if (now[i]) {
                    explored[i] = true;
                }
                changed = true;
                int x = minX + i % columns * CELL;
                int z = minZ + i / columns * CELL;
                for (int cx = x >> 4; cx <= (x + CELL - 1) >> 4; cx++) {
                    for (int cz = z >> 4; cz <= (z + CELL - 1) >> 4; cz++) {
                        changedChunks.add((long) cx << 32 | cz & 0xFFFFFFFFL);
                    }
                }
            }
        }
        visible = now;
        if (changed) {
            version++;
        }
    }

    /**
     * The viewer on foot, out of the RTS view, explores around x, z; call every client tick while they are on the
     * game map but not commanding.
     */
    void walk(RtsClient client, double x, double z) {
        if (ticks++ % INTERVAL != 0) {
            return;
        }
        RtsState.GameInfo info = client.state.game;
        if (info != null && info.number == game && explored.length > 0) {
            if (see(explored, x, z, PLAYER_SIGHT)) {
                version++;
            }
        } else {
            long cx = (long) Math.floor(x / CELL);
            long cz = (long) Math.floor(z / CELL);
            walked.add(cx << 32 | cz & 0xFFFFFFFFL);
        }
    }

    /**
     * The light the RTS view draws the ground at x, z in, packed as the lightmap takes it: full where the team sees,
     * half where it has been, none elsewhere.
     */
    int light(int x, int z) {
        int i = index(x + 0.5, z + 0.5);
        if (i < 0 || !explored[i]) {
            return 0;
        }
        return visible[i] ? FULL_LIGHT : HALF_LIGHT;
    }

    int columns() {
        return columns;
    }

    int rows() {
        return rows;
    }

    int minX() {
        return minX;
    }

    int minZ() {
        return minZ;
    }

    /** The light the RTS view draws cell c, r in, packed as {@link #light} gives it; none off the map. */
    int cellLight(int c, int r) {
        int i = c < 0 || r < 0 || c >= columns || r >= rows ? -1 : r * columns + c;
        if (i < 0 || !explored[i]) {
            return 0;
        }
        return visible[i] ? FULL_LIGHT : HALF_LIGHT;
    }

    /** Has the ground whose light changed drawn again. */
    void redrawChanged(Minecraft mc) {
        if (changedAll) {
            mc.renderGlobal.loadRenderers();
        } else {
            for (long chunk : changedChunks) {
                int x = (int) (chunk >> 32) << 4;
                int z = (int) chunk << 4;
                mc.theWorld.markBlockRangeForRenderUpdate(x, 0, z, x + 15, 255, z + 15);
            }
        }
        changedChunks.clear();
        changedAll = false;
    }

    /** Forgets where the viewer walked, on leaving the server. */
    void forgetWalked() {
        walked.clear();
    }

    /** Marks the cells within range of x, z; whether any was not marked yet. */
    private boolean see(boolean[] now, double x, double z, double range) {
        int c0 = Math.max(0, (int) Math.floor((x - range - minX) / CELL));
        int c1 = Math.min(columns - 1, (int) Math.floor((x + range - minX) / CELL));
        int r0 = Math.max(0, (int) Math.floor((z - range - minZ) / CELL));
        int r1 = Math.min(rows - 1, (int) Math.floor((z + range - minZ) / CELL));
        double rangeSq = range * range;
        boolean changed = false;
        for (int r = r0; r <= r1; r++) {
            double dz = minZ + r * CELL + CELL / 2.0 - z;
            for (int c = c0; c <= c1; c++) {
                double dx = minX + c * CELL + CELL / 2.0 - x;
                if (dx * dx + dz * dz <= rangeSq && !now[r * columns + c]) {
                    now[r * columns + c] = true;
                    changed = true;
                }
            }
        }
        return changed;
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
