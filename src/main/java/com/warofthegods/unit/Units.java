package com.warofthegods.unit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;

import com.warofthegods.Config;
import com.warofthegods.game.Building;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.GoldMine;
import com.warofthegods.game.Team;
import com.warofthegods.game.UnitKind;
import com.warofthegods.game.UnitRecord;
import com.warofthegods.rts.Commanders;
import com.warofthegods.rts.EntityCommanderBody;
import com.warofthegods.world.GameWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Keeps track of the units of the running game on the server thread: which are loaded, where they are, which chunks
 * must stay loaded for them, and where each team's base is. It also trains units and passes orders on to them.
 */
public final class Units {

    /** Ticks between looking over all units. */
    private static final int SCAN_INTERVAL = 10;
    /** Scans a unit's chunk may be loaded without the unit in it before the unit counts as gone. */
    private static final int MISSING_SCANS = 6;
    /** Blocks between units standing in formation. */
    private static final double SPACING = 1.6;
    /** Most archers in one row when they line up to shoot. */
    private static final int ARCHERS_PER_ROW = 12;

    private static Units instance;

    private final Map<UUID, EntityUnit> loaded = new LinkedHashMap<>();
    private final Map<UUID, Integer> missing = new HashMap<>();
    private final UnitChunkLoader chunks = new UnitChunkLoader();
    private int ticks;

    public Units() {
        instance = this;
    }

    public static Units get() {
        return instance;
    }

    /** For ForgeChunkManager.setForcedChunkLoadingCallback. */
    public ForgeChunkManager.LoadingCallback chunkLoadingCallback() {
        return chunks;
    }

    // ---------------------------------------------------------------- bookkeeping

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % SCAN_INTERVAL != 0) {
            return;
        }
        Game game = DimensionManager.getWorld(0) == null ? null : GameManager.getGame();
        WorldServer world = DimensionManager.getWorld(GameWorld.dimensionId());
        if (game == null || world == null) {
            loaded.clear();
            chunks.releaseAll();
            return;
        }
        scan(game, world);
        assignBases(game, world);
        chunks.update(world, wantedChunks(game));
    }

    private void scan(Game game, WorldServer world) {
        loaded.clear();
        for (Object object : world.loadedEntityList) {
            if (!(object instanceof EntityUnit)) {
                continue;
            }
            EntityUnit unit = (EntityUnit) object;
            if (unit.isDead) {
                continue;
            }
            Team team = game.getTeam(unit.getTeamId());
            if (team == null || team.isDefeated()) {
                // Its team was removed, or defeated while it was not loaded.
                unit.setDead();
                game.removeUnitRecord(unit.getUniqueID());
                continue;
            }
            unit.updateTeam(team);
            loaded.put(unit.getUniqueID(), unit);
            remember(game, unit);
        }
        for (UnitRecord record : new ArrayList<>(game.getUnitRecords())) {
            if (loaded.containsKey(record.id)) {
                missing.remove(record.id);
            } else if (world.getChunkProvider()
                .chunkExists(record.chunkX, record.chunkZ)) {
                    int scans = missing.containsKey(record.id) ? missing.get(record.id) + 1 : 1;
                    missing.put(record.id, scans);
                    if (scans >= MISSING_SCANS) {
                        game.removeUnitRecord(record.id);
                        missing.remove(record.id);
                    }
                }
        }
    }

    private static void remember(Game game, EntityUnit unit) {
        game.updateUnitRecord(
            unit.getUniqueID(),
            unit.getTeamId(),
            unit.getKind(),
            MathHelper.floor_double(unit.posX) >> 4,
            MathHelper.floor_double(unit.posZ) >> 4);
    }

    /** The chunks around every unit, so units keep moving and fighting with no player near. */
    private static Set<ChunkCoordIntPair> wantedChunks(Game game) {
        Set<ChunkCoordIntPair> wanted = new HashSet<>();
        for (UnitRecord record : game.getUnitRecords()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    wanted.add(new ChunkCoordIntPair(record.chunkX + dx, record.chunkZ + dz));
                }
            }
        }
        return wanted;
    }

    /** Called by a unit that died. */
    void forget(EntityUnit unit) {
        loaded.remove(unit.getUniqueID());
        Game game = GameManager.getGame();
        if (game != null) {
            game.removeUnitRecord(unit.getUniqueID());
        }
    }

    /** Loaded units of the running game, as of the last scan. */
    public Collection<EntityUnit> getLoaded() {
        List<EntityUnit> result = new ArrayList<>();
        for (EntityUnit unit : loaded.values()) {
            if (!unit.isDead) {
                result.add(unit);
            }
        }
        return result;
    }

    public List<EntityUnit> getUnits(Team team) {
        List<EntityUnit> result = new ArrayList<>();
        for (EntityUnit unit : getLoaded()) {
            if (unit.getTeamId()
                .equals(team.getId())) {
                result.add(unit);
            }
        }
        return result;
    }

    public EntityUnit getUnit(UUID id) {
        EntityUnit unit = loaded.get(id);
        return unit == null || unit.isDead ? null : unit;
    }

    /** A unit, a player or a mob in the world with the given UUID, or null. */
    EntityLivingBase findLiving(World world, UUID id) {
        EntityUnit unit = getUnit(id);
        if (unit != null) {
            return unit;
        }
        // A commanding player is where their body is.
        EntityCommanderBody body = Commanders.get()
            .bodyOf(id);
        if (body != null) {
            return body;
        }
        EntityPlayer player = world.func_152378_a(id);
        if (player != null) {
            return player;
        }
        for (Object object : world.loadedEntityList) {
            if (object instanceof EntityLiving && ((Entity) object).getUniqueID()
                .equals(id)) {
                return (EntityLiving) object;
            }
        }
        return null;
    }

    /** Whether the entity is a mob: a living thing that is neither a unit nor a player, nor a commander's body. */
    public static boolean isMob(Entity entity) {
        return entity instanceof EntityLiving && !(entity instanceof EntityUnit)
            && !(entity instanceof EntityCommanderBody);
    }

    /** The living mobs on the game's map, hostile or not. */
    public List<EntityLiving> getMobs(Game game, World world) {
        List<EntityLiving> mobs = new ArrayList<>();
        for (Object object : world.loadedEntityList) {
            if (!isMob((Entity) object)) {
                continue;
            }
            EntityLiving mob = (EntityLiving) object;
            if (mob.isEntityAlive()
                && game.isInside(MathHelper.floor_double(mob.posX), MathHelper.floor_double(mob.posZ))) {
                mobs.add(mob);
            }
        }
        return mobs;
    }

    // ---------------------------------------------------------------- bases

    /**
     * Gives teams without a base one, spread evenly on a circle around the middle of the map, on dry land. One team
     * per scan, since finding dry land may load chunks. The team's town hall goes up there.
     */
    private static void assignBases(Game game, WorldServer world) {
        for (Team team : game.getTeams()) {
            if (team.getBase() == null) {
                assignBase(game, world, team);
                return;
            }
        }
    }

    /** Gives the team its base, on its spot on the circle around the middle of the map, if it has none yet. */
    public static void assignBase(Game game, WorldServer world, Team team) {
        List<Team> teams = new ArrayList<>(game.getTeams());
        int i = teams.indexOf(team);
        if (i < 0 || team.getBase() != null) {
            return;
        }
        double angle = -Math.PI / 2 + 2 * Math.PI * i / Math.max(2, teams.size());
        double radius = game.getSize() * 0.3;
        int x = (int) Math.round(game.getCenterX() + Math.cos(angle) * radius);
        int z = (int) Math.round(game.getCenterZ() + Math.sin(angle) * radius);
        int[] ground = GameWorld.findDryGround(world, x, z, 64);
        team.setBase(ground[0], ground[2]);
    }

    // ---------------------------------------------------------------- training

    /**
     * Spawns units for the team in front of its town hall, or at its base if it has none yet, up to its unit
     * limit; returns them.
     */
    public List<EntityUnit> spawn(Game game, Team team, UnitKind kind, int count) {
        Building hall = game.getTownHall(team);
        if (hall != null) {
            double[] door = hall.getDoorstep();
            return spawn(game, team, kind, count, door[0], door[1]);
        }
        int[] base = team.getBase();
        if (base == null) {
            assignBases(game, GameWorld.getWorld());
            base = team.getBase();
        }
        if (base == null) {
            return new ArrayList<>();
        }
        return spawn(game, team, kind, count, base[0] + 0.5, base[1] + 0.5);
    }

    /** Spawns units for the team near x, z, up to its unit limit; returns them. */
    public List<EntityUnit> spawn(Game game, Team team, UnitKind kind, int count, double x, double z) {
        WorldServer world = GameWorld.getWorld();
        List<EntityUnit> spawned = new ArrayList<>();
        count = Math.min(count, Config.unitsPerTeam - game.countUnits(team));
        for (int i = 0; i < count; i++) {
            double[] spot = spawnSpot(world, game, MathHelper.floor_double(x), MathHelper.floor_double(z));
            EntityUnit unit = new EntityUnit(world);
            unit.setLocationAndAngles(spot[0], spot[1], spot[2], world.rand.nextFloat() * 360, 0);
            unit.setTeam(team, kind, team.nextUnitNumber());
            world.spawnEntityInWorld(unit);
            loaded.put(unit.getUniqueID(), unit);
            remember(game, unit);
            spawned.add(unit);
        }
        chunks.update(world, wantedChunks(game));
        return spawned;
    }

    /** A spot on dry ground within a few blocks of x, z, off any building, if there is one. */
    private static double[] spawnSpot(WorldServer world, Game game, int baseX, int baseZ) {
        double[] fallback = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = baseX + world.rand.nextInt(9) - 4;
            int z = baseZ + world.rand.nextInt(9) - 4;
            if (!game.isInside(x, z) || game.getBuildingAt(x + 0.5, z + 0.5) != null) {
                continue;
            }
            int y = world.getTopSolidOrLiquidBlock(x, z);
            double[] spot = { x + 0.5, y, z + 0.5 };
            if (!world.getBlock(x, y - 1, z)
                .getMaterial()
                .isLiquid()) {
                return spot;
            }
            if (fallback == null) {
                fallback = spot;
            }
        }
        return fallback != null ? fallback
            : new double[] { baseX + 0.5, world.getTopSolidOrLiquidBlock(baseX, baseZ), baseZ + 0.5 };
    }

    // ---------------------------------------------------------------- orders

    /** How units line up where they are sent, facing the way they went: warriors in front, archers behind them. */
    public enum Formation {

        /** A square block. */
        BOX("box", "Box"),
        /** A wide line, two or three deep. */
        LINE("line", "Line"),
        /** A wedge pointing the way they went. */
        WEDGE("wedge", "Wedge");

        public final String id;
        public final String displayName;

        Formation(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public static Formation byId(String id) {
            for (Formation formation : values()) {
                if (formation.id.equalsIgnoreCase(id)) {
                    return formation;
                }
            }
            return null;
        }
    }

    /** Sends the units to x, z in a square block around it. */
    public void orderMove(List<EntityUnit> units, double x, double z) {
        orderMove(units, x, z, Formation.BOX);
    }

    /**
     * Sends the units to x, z in formation, facing from where they are now toward it. Warriors take the front rows,
     * archers the ones behind and workers the back; within a row each goes to the slot on its own side, so the
     * group does not cross over itself on the way.
     */
    public void orderMove(List<EntityUnit> units, double x, double z, Formation formation) {
        if (units.isEmpty()) {
            return;
        }
        Game game = GameManager.getGame();
        double cx = 0;
        double cz = 0;
        for (EntityUnit unit : units) {
            cx += unit.posX;
            cz += unit.posZ;
        }
        cx /= units.size();
        cz /= units.size();
        double fx = x - cx;
        double fz = z - cz;
        double length = Math.sqrt(fx * fx + fz * fz);
        if (length < 1) {
            fx = 0;
            fz = 1;
        } else {
            fx /= length;
            fz /= length;
        }
        // To the right of facing forward.
        double rx = -fz;
        double rz = fx;
        List<List<double[]>> rows = rows(formation, units.size());
        List<EntityUnit> ranked = new ArrayList<>(units);
        ranked.sort(Comparator.comparingInt(Units::rank));
        int next = 0;
        for (int r = 0; r < rows.size() && next < ranked.size(); r++) {
            List<double[]> slots = rows.get(r);
            List<EntityUnit> row = new ArrayList<>(ranked.subList(next, Math.min(ranked.size(), next + slots.size())));
            next += row.size();
            final double sideX = rx;
            final double sideZ = rz;
            row.sort(Comparator.comparingDouble(unit -> unit.posX * sideX + unit.posZ * sideZ));
            // A short last row stands in the middle.
            int offset = (slots.size() - row.size()) / 2;
            for (int i = 0; i < row.size(); i++) {
                double[] slot = slots.get(offset + i);
                double slotX = x + rx * slot[0] * SPACING + fx * slot[1] * SPACING;
                double slotZ = z + rz * slot[0] * SPACING + fz * slot[1] * SPACING;
                if (game != null) {
                    slotX = Math.max(game.getMinX() + 0.5, Math.min(game.getMaxX() + 0.5, slotX));
                    slotZ = Math.max(game.getMinZ() + 0.5, Math.min(game.getMaxZ() + 0.5, slotZ));
                }
                row.get(i)
                    .orderMove(slotX, slotZ);
            }
        }
    }

    /**
     * Blocks from a player to the front row of the units following them, middle to middle: a block of space between
     * them, as both are 0.6 wide.
     */
    private static final double FOLLOW_GAP = 1.6;
    /** Which way each followed player is going, as x, z of a unit vector and where they were when it was taken. */
    private static final Map<UUID, double[]> followHeadings = new HashMap<>();

    /**
     * Has the units follow a player in formation, the front row a block of space behind them, the formation facing
     * the way the player goes.
     */
    public void orderFollow(List<EntityUnit> units, Entity leader, Formation formation) {
        if (units.isEmpty()) {
            return;
        }
        double cx = 0;
        double cz = 0;
        for (EntityUnit unit : units) {
            cx += unit.posX;
            cz += unit.posZ;
        }
        cx /= units.size();
        cz /= units.size();
        // Until the player moves on, they face away from the units, which line up behind them.
        double fx = leader.posX - cx;
        double fz = leader.posZ - cz;
        double length = Math.sqrt(fx * fx + fz * fz);
        if (length < 1) {
            float yaw = leader.rotationYaw * (float) Math.PI / 180;
            fx = -Math.sin(yaw);
            fz = Math.cos(yaw);
        } else {
            fx /= length;
            fz /= length;
        }
        followHeadings.put(leader.getUniqueID(), new double[] { fx, fz, leader.posX, leader.posZ });
        double rx = -fz;
        double rz = fx;
        List<List<double[]>> rows = rows(formation, units.size());
        double front = rows.isEmpty() || rows.get(0)
            .isEmpty() ? 0
                : rows.get(0)
                    .get(0)[1];
        List<EntityUnit> ranked = new ArrayList<>(units);
        ranked.sort(Comparator.comparingInt(Units::rank));
        int next = 0;
        for (int r = 0; r < rows.size() && next < ranked.size(); r++) {
            List<double[]> slots = rows.get(r);
            List<EntityUnit> row = new ArrayList<>(ranked.subList(next, Math.min(ranked.size(), next + slots.size())));
            next += row.size();
            row.sort(Comparator.comparingDouble(unit -> unit.posX * rx + unit.posZ * rz));
            int offset = (slots.size() - row.size()) / 2;
            for (int i = 0; i < row.size(); i++) {
                double[] slot = slots.get(offset + i);
                row.get(i)
                    .orderFollow(leader, slot[0] * SPACING, FOLLOW_GAP + (front - slot[1]) * SPACING);
            }
        }
    }

    /**
     * Which way a followed player is going, as x, z of a unit vector. It turns only once they have gone a couple
     * of blocks, so the units behind them do not swing about as they look around.
     */
    static double[] followHeading(Entity leader) {
        double[] heading = followHeadings.get(leader.getUniqueID());
        if (heading == null) {
            float yaw = leader.rotationYaw * (float) Math.PI / 180;
            heading = new double[] { -Math.sin(yaw), Math.cos(yaw), leader.posX, leader.posZ };
            followHeadings.put(leader.getUniqueID(), heading);
        }
        double dx = leader.posX - heading[2];
        double dz = leader.posZ - heading[3];
        double moved = Math.sqrt(dx * dx + dz * dz);
        if (moved >= 2) {
            heading[0] = dx / moved;
            heading[1] = dz / moved;
            heading[2] = leader.posX;
            heading[3] = leader.posZ;
        }
        return new double[] { heading[0], heading[1] };
    }

    /**
     * The player with this UUID as units following them see them: their body while they command from above, else
     * themselves; null when they are not in this world or are dead.
     */
    public static Entity leader(World world, UUID id) {
        if (id == null || world == null) {
            return null;
        }
        Entity body = Commanders.get()
            .bodyOf(id);
        if (body != null && body.worldObj == world) {
            return body;
        }
        EntityPlayer player = world.func_152378_a(id);
        return player == null || player.isDead
            || Commanders.get()
                .isCommanding(id) ? null : player;
    }

    /** Warriors first, then archers, then workers. */
    private static int rank(EntityUnit unit) {
        return unit.isWorker() ? 2 : unit.getKind() == UnitKind.ARCHER ? 1 : 0;
    }

    /**
     * Slots for count units, front row first, each row left to right, as {across, ahead} in spacings from the
     * point they were sent to; the formation is centred on it.
     */
    static List<List<double[]>> rows(Formation formation, int count) {
        List<Integer> widths = new ArrayList<>();
        int left = count;
        if (formation == Formation.WEDGE) {
            for (int width = 1; left > 0; width += 2) {
                widths.add(width);
                left -= width;
            }
        } else {
            int columns = formation == Formation.LINE ? Math.max(1, Math.min(count, Math.max(6, (count + 2) / 3)))
                : (int) Math.ceil(Math.sqrt(count));
            while (left > 0) {
                widths.add(columns);
                left -= columns;
            }
        }
        List<List<double[]>> rows = new ArrayList<>();
        int depth = widths.size();
        for (int r = 0; r < depth; r++) {
            int width = widths.get(r);
            double ahead = (depth - 1) / 2.0 - r;
            List<double[]> row = new ArrayList<>();
            for (int c = 0; c < width; c++) {
                row.add(new double[] { c - (width - 1) / 2.0, ahead });
            }
            rows.add(row);
        }
        return rows;
    }

    /** Sets how the units fight when not ordered to. */
    public void orderStance(List<EntityUnit> units, EntityUnit.Stance stance) {
        for (EntityUnit unit : units) {
            unit.setStance(stance);
        }
    }

    /** Sends workers to raise a building site or work a farm; returns how many went. */
    public int orderBuild(List<EntityUnit> units, Building building) {
        return orderBuild(units, building, false);
    }

    /**
     * Sends workers to raise a building site or work a farm, or with queue, to raise it once they have raised the
     * sites they are on and were given before; returns how many went.
     */
    public int orderBuild(List<EntityUnit> units, Building building, boolean queue) {
        int sent = 0;
        for (EntityUnit unit : units) {
            if (unit.isWorker()) {
                if (queue) {
                    unit.queueBuild(building);
                } else {
                    unit.orderBuild(building);
                }
                sent++;
            }
        }
        return sent;
    }

    /** Sends workers to carve food off a carcass and carry it home; returns how many went. */
    public int orderCarve(List<EntityUnit> units, Hunting.Carcass carcass) {
        int sent = 0;
        for (EntityUnit unit : units) {
            if (unit.isWorker()) {
                unit.orderCarve(carcass);
                sent++;
            }
        }
        return sent;
    }

    /** Sends workers to chop the tree with its trunk's foot at x, y, z; returns how many went. */
    public int orderChop(List<EntityUnit> units, int x, int y, int z) {
        int sent = 0;
        for (EntityUnit unit : units) {
            if (unit.isWorker()) {
                unit.orderChop(x, y, z);
                sent++;
            }
        }
        return sent;
    }

    /** Sends the workers to mine a gold mine; returns how many went. */
    public int orderMine(List<EntityUnit> units, GoldMine mine) {
        int sent = 0;
        for (EntityUnit unit : units) {
            if (unit.isWorker()) {
                unit.orderMine(mine);
                sent++;
            }
        }
        return sent;
    }

    /** The gold mine with gold left nearest x, z, no further than range, or null. */
    public static GoldMine nearestMine(Game game, double x, double z, double range) {
        GoldMine best = null;
        double bestDistance = range;
        for (GoldMine mine : game.getMines()) {
            double distance = mine.distanceTo(x, z);
            if (!mine.isDepleted() && distance <= bestDistance) {
                best = mine;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Shares the spots out among the workers, each taking a stretch of them in order, to put torches on; returns how
     * many went.
     */
    public int orderLight(List<EntityUnit> units, List<int[]> spots) {
        List<EntityUnit> workers = new ArrayList<>();
        for (EntityUnit unit : units) {
            if (unit.isWorker()) {
                workers.add(unit);
            }
        }
        int sent = Math.min(workers.size(), spots.size());
        for (int i = 0; i < sent; i++) {
            workers.get(i)
                .orderLight(spots.subList(i * spots.size() / sent, (i + 1) * spots.size() / sent));
        }
        return sent;
    }

    /** Sends soldiers to attack an enemy building; returns how many went. */
    public int orderAttackBuilding(List<EntityUnit> units, Building building) {
        int sent = 0;
        for (EntityUnit unit : units) {
            if (!unit.isWorker()) {
                unit.orderAttackBuilding(building);
                sent++;
            }
        }
        return sent;
    }

    /**
     * Sends the units at the target. Archers spread out in a line facing it, a bit inside their range, a second row
     * behind the first in the gaps, so every one of them has a clear shot.
     */
    public void orderAttack(List<EntityUnit> units, EntityLivingBase target) {
        List<EntityUnit> archers = new ArrayList<>();
        for (EntityUnit unit : units) {
            if (unit.getKind() == UnitKind.ARCHER) {
                archers.add(unit);
            } else {
                unit.orderAttack(target);
            }
        }
        if (archers.isEmpty()) {
            return;
        }
        double cx = 0;
        double cz = 0;
        for (EntityUnit archer : archers) {
            cx += archer.posX;
            cz += archer.posZ;
        }
        cx /= archers.size();
        cz /= archers.size();
        // From the target back toward the archers.
        double bx = cx - target.posX;
        double bz = cz - target.posZ;
        double length = Math.sqrt(bx * bx + bz * bz);
        if (length < 1) {
            bx = 0;
            bz = 1;
            length = 1;
        }
        bx /= length;
        bz /= length;
        double across = archers.get(0)
            .archerRange() - 3;
        double back = Math.max(6, Math.min(length, across));
        double rx = -bz;
        double rz = bx;
        final double sideX = rx;
        final double sideZ = rz;
        archers.sort(Comparator.comparingDouble(unit -> unit.posX * sideX + unit.posZ * sideZ));
        int perRow = Math.min(ARCHERS_PER_ROW, archers.size());
        Game game = GameManager.getGame();
        for (int i = 0; i < archers.size(); i++) {
            int row = i / perRow;
            int inRow = Math.min(perRow, archers.size() - row * perRow);
            // Rows behind stand in the gaps of the row ahead.
            double offset = (i % perRow - (inRow - 1) / 2.0 + (row % 2) * 0.5) * SPACING;
            double distance = back + row * SPACING;
            double x = target.posX + bx * distance + rx * offset;
            double z = target.posZ + bz * distance + rz * offset;
            if (game != null) {
                x = Math.max(game.getMinX() + 0.5, Math.min(game.getMaxX() + 0.5, x));
                z = Math.max(game.getMinZ() + 0.5, Math.min(game.getMaxZ() + 0.5, z));
            }
            archers.get(i)
                .orderAttack(target, x, z);
        }
    }

    /** Sends workers to hunt the animal; returns how many went. */
    public int orderHunt(List<EntityUnit> units, EntityLivingBase animal) {
        int sent = 0;
        for (EntityUnit unit : units) {
            if (unit.isWorker()) {
                unit.orderHunt(animal);
                sent++;
            }
        }
        return sent;
    }

    public void orderStop(List<EntityUnit> units) {
        for (EntityUnit unit : units) {
            unit.orderStop();
        }
    }

    public void kill(List<EntityUnit> units) {
        Game game = GameManager.getGame();
        for (EntityUnit unit : units) {
            unit.setDead();
            loaded.remove(unit.getUniqueID());
            if (game != null) {
                game.removeUnitRecord(unit.getUniqueID());
            }
        }
    }

    // ---------------------------------------------------------------- sides

    /** The team of a unit or a player, or null for anything else or a player on no team. */
    public static Team teamOf(Game game, Entity entity) {
        if (entity instanceof EntityUnit) {
            return game.getTeam(((EntityUnit) entity).getTeamId());
        }
        if (entity instanceof EntityPlayer || entity instanceof EntityCommanderBody) {
            return game.getTeamOf(entity.getUniqueID());
        }
        return null;
    }

    /** Whether the unit should never hurt the entity, or be hurt by it: units and players of allied teams. */
    public boolean isAlly(EntityUnit unit, Entity other) {
        Game game = GameManager.getGame();
        if (game == null) {
            return false;
        }
        Team team = game.getTeam(unit.getTeamId());
        Team otherTeam = teamOf(game, other);
        return team != null && otherTeam != null && game.areAllied(team, otherTeam);
    }

    /**
     * Whether a guarding unit attacks the entity: units and players of enemy teams, and hostile mobs. A commanding
     * player is fought through their body, not where they hover.
     */
    public boolean isEnemy(EntityUnit unit, Entity other) {
        if (other instanceof EntityPlayer && (((EntityPlayer) other).capabilities.isCreativeMode || Commanders.get()
            .isCommanding(other.getUniqueID()))) {
            return false;
        }
        if (other instanceof EntityCommanderBody) {
            EntityPlayer owner = ((EntityCommanderBody) other).getOwner();
            if (owner == null || owner.capabilities.isCreativeMode) {
                return false;
            }
        }
        Game game = GameManager.getGame();
        if (game == null) {
            return false;
        }
        Team team = game.getTeam(unit.getTeamId());
        if (other instanceof EntityUnit || other instanceof EntityPlayer || other instanceof EntityCommanderBody) {
            Team otherTeam = teamOf(game, other);
            return team != null && otherTeam != null && game.areEnemies(team, otherTeam);
        }
        return other instanceof IMob;
    }

    /**
     * Whether a tower or arrow of the team fights the entity: units, players and bodies of enemy teams, and hostile
     * mobs. Players in creative mode and commanding players, who hover above, are left alone.
     */
    public boolean isEnemyOf(Team team, Entity other) {
        if (other instanceof EntityPlayer && (((EntityPlayer) other).capabilities.isCreativeMode || Commanders.get()
            .isCommanding(other.getUniqueID()))) {
            return false;
        }
        if (other instanceof EntityCommanderBody) {
            EntityPlayer owner = ((EntityCommanderBody) other).getOwner();
            if (owner == null || owner.capabilities.isCreativeMode) {
                return false;
            }
        }
        Game game = GameManager.getGame();
        if (game == null || team == null) {
            return false;
        }
        if (other instanceof EntityUnit || other instanceof EntityPlayer || other instanceof EntityCommanderBody) {
            Team otherTeam = teamOf(game, other);
            return otherTeam != null && game.areEnemies(team, otherTeam);
        }
        return other instanceof IMob;
    }

    /** The nearest enemy of the team within range of a point, such as a tower's top, or null. */
    public EntityLivingBase findEnemyNear(World world, Team team, double x, double y, double z, double range) {
        EntityLivingBase best = null;
        double bestDistance = range * range;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x - range, y - range, z - range, x + range, y + 8, z + range);
        for (Object object : world.getEntitiesWithinAABB(EntityLivingBase.class, box)) {
            EntityLivingBase other = (EntityLivingBase) object;
            if (!other.isEntityAlive() || !isEnemyOf(team, other)) {
                continue;
            }
            double dx = other.posX - x;
            double dz = other.posZ - z;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                best = other;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * The nearest enemy the unit can see within range, or null. Creepers are left alone unless ordered: fighting one
     * up close only sets it off.
     */
    EntityLivingBase findEnemyNear(EntityUnit unit, double range) {
        EntityLivingBase best = null;
        double bestDistance = range * range;
        for (Object object : unit.worldObj
            .getEntitiesWithinAABB(EntityLivingBase.class, unit.boundingBox.expand(range, 4, range))) {
            EntityLivingBase other = (EntityLivingBase) object;
            if (other == unit || !other.isEntityAlive() || other instanceof EntityCreeper || !isEnemy(unit, other)) {
                continue;
            }
            double distance = unit.getDistanceSqToEntity(other);
            if (distance < bestDistance && unit.getEntitySenses()
                .canSee(other)) {
                best = other;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** The nearest building of an enemy team within range of the unit, or null. */
    Building findEnemyBuildingNear(EntityUnit unit, double range) {
        Game game = GameManager.getGame();
        Team team = game == null ? null : game.getTeam(unit.getTeamId());
        if (team == null) {
            return null;
        }
        Building best = null;
        double bestDistance = range;
        for (Building building : game.getBuildings()) {
            Team owner = game.getTeam(building.team);
            if (owner == null || !game.areEnemies(team, owner)) {
                continue;
            }
            double distance = building.distanceTo(unit.posX, unit.posZ);
            if (distance < bestDistance) {
                best = building;
                bestDistance = distance;
            }
        }
        return best;
    }
}
