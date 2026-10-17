package com.warofthegods.unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityHorse;
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
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Faction;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.GoldMine;
import com.warofthegods.game.Team;
import com.warofthegods.game.UnitKind;
import com.warofthegods.game.UnitRecord;
import com.warofthegods.rts.Commanders;
import com.warofthegods.rts.EntityCommanderBody;
import com.warofthegods.world.GameWorld;
import com.warofthegods.world.Misthalin;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Keeps track of the units of the running game on the server thread: which are loaded, where they are, which chunks
 * must stay loaded for them, and where each team's base is. It also trains units and passes orders on to them.
 */
public final class Units {

    /** Horse coats: the low byte of a horse's variant. */
    private static final int HORSE_WHITE = 0;
    private static final int HORSE_BLACK = 4;
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
     * Gives teams without a base one, roughly opposite each other round the middle of the map, on dry land. One team
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
        int n = Math.max(2, teams.size());
        double[] spot = Misthalin.baseSpot(game.getCenterX(), game.getCenterZ(), game.getSize(), game.getSeed(), i, n);
        int x = (int) Math.round(spot[0]);
        int z = (int) Math.round(spot[1]);
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

    /**
     * Brings out a tame, saddled horse near x, z for anyone to ride: white for Saradomin's teams, black for
     * Zamorak's, named for its team. It is no unit, so it takes no room and nobody commands it.
     */
    public EntityHorse spawnHorse(Team team, double x, double z) {
        WorldServer world = GameWorld.getWorld();
        Game game = GameManager.getGame();
        double[] spot = spawnSpot(world, game, MathHelper.floor_double(x), MathHelper.floor_double(z));
        EntityHorse horse = new EntityHorse(world);
        horse.setLocationAndAngles(spot[0], spot[1], spot[2], world.rand.nextFloat() * 360, 0);
        horse.setHorseType(0);
        horse.setHorseVariant(team.getFaction() == Faction.ZAMORAK ? HORSE_BLACK : HORSE_WHITE);
        horse.setHorseTamed(true);
        horse.setHorseSaddled(true);
        horse.setCustomNameTag(team.getColor() + team.getName() + " horse");
        world.spawnEntityInWorld(horse);
        return horse;
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
        inFormation(units, x, z, formation, false, (unit, slot) -> unit.orderMove(slot[0], slot[1]));
    }

    /**
     * Has the units walk to x, z in formation once done with the moves they are on and those queued before, as a
     * shift-click does, fighting on the way with attack; units doing anything else go now. The formation faces from
     * where they will be toward it.
     */
    public void queueMove(List<EntityUnit> units, double x, double z, Formation formation, boolean attack) {
        inFormation(units, x, z, formation, true, (unit, slot) -> unit.queueMove(slot[0], slot[1], attack));
    }

    /**
     * Shares out the slots of a formation at x, z among the units (see {@link #orderMove}), facing from where they
     * are, or from where their queued moves leave them with queued, and hands each its slot.
     */
    private static void inFormation(List<EntityUnit> units, double x, double z, Formation formation, boolean queued,
        BiConsumer<EntityUnit, double[]> go) {
        if (units.isEmpty()) {
            return;
        }
        Game game = GameManager.getGame();
        Map<EntityUnit, double[]> from = new HashMap<>();
        double cx = 0;
        double cz = 0;
        for (EntityUnit unit : units) {
            double[] at = queued ? unit.lastGoal() : new double[] { unit.posX, unit.posZ };
            from.put(unit, at);
            cx += at[0];
            cz += at[1];
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
            row.sort(Comparator.comparingDouble(unit -> from.get(unit)[0] * sideX + from.get(unit)[1] * sideZ));
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
                go.accept(row.get(i), new double[] { slotX, slotZ });
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

    /** Warriors and knights first, then archers, then workers. */
    private static int rank(EntityUnit unit) {
        return unit.isWorker() ? 2 : unit.getKind().ranged ? 1 : 0;
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

    /** Puts up a protection prayer, or none, on the units that can have it up; returns how many did. */
    public int orderPrayer(List<EntityUnit> units, Prayer prayer) {
        int praying = 0;
        for (EntityUnit unit : units) {
            if (unit.setPrayer(prayer)) {
                praying++;
            }
        }
        return praying;
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
        Game game = GameManager.getGame();
        if (game != null && takesWorkers(building)) {
            return orderWork(game, units, building);
        }
        if (building.type.buildsInstantly() && building.isComplete() && !building.isDamaged()) {
            // Up already, as a stone circle is the moment it is laid out: the dark wizards go and stand in it, ready
            // to summon.
            List<EntityUnit> going = new ArrayList<>();
            for (EntityUnit unit : units) {
                if (unit.canRaise(building.type)) {
                    going.add(unit);
                }
            }
            orderMove(going, building.getCenterX(), building.getCenterZ());
            return going.size();
        }
        // Sent to a piece of wall or bridge, they build the rest of it after it.
        List<Building> rest = game == null ? new ArrayList<>() : wallAfter(game, building);
        int sent = 0;
        for (EntityUnit unit : units) {
            if (unit.canRaise(building.type)) {
                if (queue) {
                    unit.queueBuild(building);
                } else {
                    unit.orderBuild(building);
                }
                for (Building next : rest) {
                    unit.queueBuild(next);
                }
                sent++;
            }
        }
        return sent;
    }

    /** How far from a full farm workers sent to it look for another farm with room. */
    public static final double NEARBY_FARM = 64;

    /** What a player is told when workers they sent to a farm found no room there or at any farm nearby. */
    public static String farmFull() {
        return Config.farmWorkers == 1 ? "Only one worker can work on a farm"
            : "Only " + Config.farmWorkers + " workers can work on a farm";
    }

    /** Whether workers sent to the building would work it, so that only so many can go, rather than build it. */
    public static boolean takesWorkers(Building building) {
        return building.type.maxWorkers() > 0 && building.isComplete() && !building.isDamaged();
    }

    /** Whether the farm has room for one more worker than those sent to work it. */
    public static boolean hasRoom(Building farm) {
        return hasRoom(farm, new HashMap<>(), new HashSet<>());
    }

    /**
     * The farm if it has room for the worker, or else the nearest of its team's farms nearby that does; null if none
     * has.
     */
    public static Building workplaceFor(Game game, EntityUnit worker, Building farm) {
        Set<EntityUnit> moving = new HashSet<>();
        moving.add(worker);
        return workplaceFor(game, farm, new HashMap<>(), moving);
    }

    /**
     * Sends workers to work a farm, as many as it has room for, those already at it keeping their places. The rest go
     * to the nearest of the team's farms nearby with room, and any left over carry on as they were. Returns how many
     * went.
     */
    private int orderWork(Game game, List<EntityUnit> units, Building farm) {
        List<EntityUnit> workers = new ArrayList<>();
        for (EntityUnit unit : units) {
            if (unit.isWorker()) {
                workers.add(unit);
            }
        }
        workers.sort(Comparator.comparingInt(unit -> unit.isSentToWork(farm.id) ? 0 : 1));
        Set<EntityUnit> moving = new HashSet<>(workers);
        Map<Integer, Integer> sentTo = new HashMap<>();
        int sent = 0;
        for (EntityUnit worker : workers) {
            Building place = workplaceFor(game, farm, sentTo, moving);
            if (place == null) {
                continue;
            }
            sentTo.merge(place.id, 1, Integer::sum);
            worker.orderBuild(place);
            sent++;
        }
        return sent;
    }

    /**
     * The farm, or the nearest of its team's farms nearby, with room for another worker besides those sent to it now
     * and those not being moved; null if none has room.
     */
    private static Building workplaceFor(Game game, Building farm, Map<Integer, Integer> sentTo,
        Set<EntityUnit> moving) {
        if (hasRoom(farm, sentTo, moving)) {
            return farm;
        }
        Building nearest = null;
        double nearestDistance = NEARBY_FARM;
        for (Building other : game.getBuildings()) {
            if (other == farm || other.type != farm.type || !other.team.equals(farm.team) || !takesWorkers(other)) {
                continue;
            }
            double distance = other.distanceTo(farm.getCenterX(), farm.getCenterZ());
            if (distance <= nearestDistance && hasRoom(other, sentTo, moving)) {
                nearest = other;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static boolean hasRoom(Building farm, Map<Integer, Integer> sentTo, Set<EntityUnit> moving) {
        int workers = sentTo.getOrDefault(farm.id, 0);
        for (EntityUnit unit : get().getLoaded()) {
            if (!moving.contains(unit) && unit.isSentToWork(farm.id)) {
                workers++;
            }
        }
        return workers < farm.type.maxWorkers();
    }

    /** Most pieces of wall followed from one piece. */
    private static final int MAX_WALL = 256;

    /**
     * The unbuilt pieces of the wall or bridge the site is a piece of, in the order to build them after it: along it
     * one way to its end, then the other way. Pieces join where they touch side to side; built pieces carry it on.
     * Empty for anything but an unbuilt piece of wall or bridge.
     */
    static List<Building> wallAfter(Game game, Building site) {
        List<Building> rest = new ArrayList<>();
        if (!site.type.isLine() || site.isComplete()) {
            return rest;
        }
        boolean bridge = site.type == BuildingType.BRIDGE;
        List<Building> pieces = new ArrayList<>();
        for (Building building : game.getBuildings()) {
            if (building.type.isLine() && (building.type == BuildingType.BRIDGE) == bridge
                && building.team.equals(site.team)) {
                pieces.add(building);
            }
        }
        Set<Integer> seen = new HashSet<>();
        seen.add(site.id);
        for (Building start : neighbours(site, pieces)) {
            if (!seen.add(start.id)) {
                continue;
            }
            // Out along this way from the site, nearest first.
            List<Building> frontier = new ArrayList<>();
            frontier.add(start);
            while (!frontier.isEmpty() && seen.size() < MAX_WALL) {
                List<Building> next = new ArrayList<>();
                for (Building piece : frontier) {
                    if (!piece.isComplete()) {
                        rest.add(piece);
                    }
                    for (Building on : neighbours(piece, pieces)) {
                        if (seen.add(on.id)) {
                            next.add(on);
                        }
                    }
                }
                frontier = next;
            }
        }
        return rest;
    }

    /** The pieces touching piece side to side, in id order. */
    private static List<Building> neighbours(Building piece, List<Building> pieces) {
        List<Building> touching = new ArrayList<>();
        for (Building other : pieces) {
            if (other == piece) {
                continue;
            }
            boolean overlapX = other.minX <= piece.getMaxX() && piece.minX <= other.getMaxX();
            boolean overlapZ = other.minZ <= piece.getMaxZ() && piece.minZ <= other.getMaxZ();
            boolean nextX = other.minX == piece.getMaxX() + 1 || piece.minX == other.getMaxX() + 1;
            boolean nextZ = other.minZ == piece.getMaxZ() + 1 || piece.minZ == other.getMaxZ() + 1;
            if (overlapZ && nextX || overlapX && nextZ) {
                touching.add(other);
            }
        }
        touching.sort(Comparator.comparingInt(b -> b.id));
        return touching;
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
            if (unit.getKind().ranged) {
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

    /** Sends the units to x, z in formation, the warriors and archers fighting any enemy they meet on the way. */
    public void orderAttackMove(List<EntityUnit> units, double x, double z, Formation formation) {
        orderMove(units, x, z, formation);
        for (EntityUnit unit : units) {
            unit.makeAttackMove();
        }
    }

    /** Has the units attack the target once done with the enemies they were already ordered at. */
    public void queueAttack(List<EntityUnit> units, EntityLivingBase target) {
        for (EntityUnit unit : units) {
            unit.queueAttack(target);
        }
    }

    /**
     * Shares the targets out among the units by their health, so a strong enemy draws more of them than a weak one
     * and every enemy draws at least one while there are units enough; each goes for the nearest target still
     * wanting attackers. After that each attacks the rest of the targets, nearest first, from where its own was.
     * With queue, the targets come after the enemies the units were already ordered at.
     */
    public void orderAttackAll(List<EntityUnit> units, List<EntityLivingBase> targets, boolean queue) {
        if (units.isEmpty() || targets.isEmpty()) {
            return;
        }
        int[] wanted = shareByHealth(units.size(), targets);
        // Every unit and target pair, nearest first: each unit takes the nearest target still wanting one.
        List<int[]> pairs = new ArrayList<>();
        for (int u = 0; u < units.size(); u++) {
            for (int t = 0; t < targets.size(); t++) {
                pairs.add(new int[] { u, t });
            }
        }
        pairs.sort(
            Comparator.comparingDouble(
                pair -> units.get(pair[0])
                    .getDistanceSqToEntity(targets.get(pair[1]))));
        int[] first = new int[units.size()];
        Arrays.fill(first, -1);
        for (int[] pair : pairs) {
            if (first[pair[0]] < 0 && wanted[pair[1]] > 0) {
                first[pair[0]] = pair[1];
                wanted[pair[1]]--;
            }
        }
        for (int u = 0; u < units.size(); u++) {
            EntityUnit unit = units.get(u);
            int own = first[u] < 0 ? 0 : first[u];
            EntityLivingBase from = targets.get(own);
            List<EntityLivingBase> order = new ArrayList<>(targets);
            order.remove(from);
            order.sort(Comparator.comparingDouble(from::getDistanceSqToEntity));
            order.add(0, from);
            for (int i = 0; i < order.size(); i++) {
                if (i == 0 && !queue) {
                    unit.orderAttack(order.get(i));
                } else {
                    unit.queueAttack(order.get(i));
                }
            }
        }
    }

    /**
     * How many of so many units each target should draw: in proportion to its health, but at least one each while
     * there are units for that.
     */
    static int[] shareByHealth(int units, List<EntityLivingBase> targets) {
        int n = targets.size();
        int[] wanted = new int[n];
        if (units < n) {
            // Too few to go round: the healthiest take one each.
            Integer[] byHealth = new Integer[n];
            for (int t = 0; t < n; t++) {
                byHealth[t] = t;
            }
            Arrays.sort(
                byHealth,
                (a, b) -> Float.compare(
                    targets.get(b)
                        .getHealth(),
                    targets.get(a)
                        .getHealth()));
            for (int i = 0; i < units; i++) {
                wanted[byHealth[i]] = 1;
            }
            return wanted;
        }
        double total = 0;
        for (EntityLivingBase target : targets) {
            total += Math.max(1, target.getHealth());
        }
        double[] share = new double[n];
        int given = 0;
        for (int t = 0; t < n; t++) {
            share[t] = units * Math.max(
                1,
                targets.get(t)
                    .getHealth())
                / total;
            wanted[t] = Math.max(1, (int) Math.floor(share[t]));
            given += wanted[t];
        }
        // Round to the units there are: one more to whichever is furthest short of its share, or one less from
        // whichever is furthest over it.
        while (given != units) {
            int best = -1;
            for (int t = 0; t < n; t++) {
                if (given > units && wanted[t] <= 1) {
                    continue;
                }
                double gap = share[t] - wanted[t];
                if (best < 0 || (given < units ? gap > share[best] - wanted[best] : gap < share[best] - wanted[best])) {
                    best = t;
                }
            }
            wanted[best] += given < units ? 1 : -1;
            given += given < units ? 1 : -1;
        }
        return wanted;
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
        List<EntityLivingBase> enemies = findEnemiesNear(world, team, x, y, z, range, 1);
        return enemies.isEmpty() ? null : enemies.get(0);
    }

    /** Up to so many of the nearest enemies of the team within range of a point, nearest first. */
    public List<EntityLivingBase> findEnemiesNear(World world, Team team, double x, double y, double z, double range,
        int most) {
        List<EntityLivingBase> found = new ArrayList<>();
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x - range, y - range, z - range, x + range, y + 8, z + range);
        for (Object object : world.getEntitiesWithinAABB(EntityLivingBase.class, box)) {
            EntityLivingBase other = (EntityLivingBase) object;
            if (!other.isEntityAlive() || !isEnemyOf(team, other)) {
                continue;
            }
            double dx = other.posX - x;
            double dz = other.posZ - z;
            if (dx * dx + dz * dz < range * range) {
                found.add(other);
            }
        }
        found.sort(Comparator.comparingDouble(other -> {
            double dx = other.posX - x;
            double dz = other.posZ - z;
            return dx * dx + dz * dz;
        }));
        return found.size() > most ? new ArrayList<>(found.subList(0, most)) : found;
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
