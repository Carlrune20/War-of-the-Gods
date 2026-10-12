package com.warofthegods.unit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;

import com.warofthegods.Config;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Team;
import com.warofthegods.game.UnitRecord;
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
            if (team == null) {
                // Its team was removed.
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

    /** A unit or a player in the world with the given UUID, or null. */
    EntityLivingBase findLiving(World world, UUID id) {
        EntityUnit unit = getUnit(id);
        if (unit != null) {
            return unit;
        }
        return world.func_152378_a(id);
    }

    // ---------------------------------------------------------------- bases

    /**
     * Gives teams without a base one, spread evenly on a circle around the middle of the map, on dry land. One team
     * per scan, since finding dry land may load chunks.
     */
    private static void assignBases(Game game, WorldServer world) {
        List<Team> teams = new ArrayList<>(game.getTeams());
        for (int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);
            if (team.getBase() != null) {
                continue;
            }
            double angle = -Math.PI / 2 + 2 * Math.PI * i / Math.max(2, teams.size());
            double radius = game.getSize() * 0.3;
            int x = (int) Math.round(game.getCenterX() + Math.cos(angle) * radius);
            int z = (int) Math.round(game.getCenterZ() + Math.sin(angle) * radius);
            int[] ground = GameWorld.findDryGround(world, x, z, 64);
            team.setBase(ground[0], ground[2]);
            return;
        }
    }

    // ---------------------------------------------------------------- training

    /** What training one unit costs. */
    public static Map<Resource, Long> unitCost() {
        Map<Resource, Long> cost = new LinkedHashMap<>();
        for (Resource resource : Resource.values()) {
            long amount = Config.unitCost[resource.ordinal()];
            if (amount > 0) {
                cost.put(resource, amount);
            }
        }
        return cost;
    }

    /** Spawns units for the team next to its base, up to its unit limit; returns them. */
    public List<EntityUnit> spawn(Game game, Team team, int count) {
        WorldServer world = GameWorld.getWorld();
        List<EntityUnit> spawned = new ArrayList<>();
        int[] base = team.getBase();
        if (base == null) {
            assignBases(game, world);
            base = team.getBase();
        }
        if (base == null) {
            return spawned;
        }
        count = Math.min(count, Config.unitsPerTeam - game.countUnits(team));
        for (int i = 0; i < count; i++) {
            double[] spot = spawnSpot(world, game, base[0], base[1]);
            EntityUnit unit = new EntityUnit(world);
            unit.setLocationAndAngles(spot[0], spot[1], spot[2], world.rand.nextFloat() * 360, 0);
            unit.setTeam(team, team.nextUnitNumber());
            world.spawnEntityInWorld(unit);
            loaded.put(unit.getUniqueID(), unit);
            remember(game, unit);
            spawned.add(unit);
        }
        chunks.update(world, wantedChunks(game));
        return spawned;
    }

    /** A spot on dry ground within a few blocks of the base, if there is one. */
    private static double[] spawnSpot(WorldServer world, Game game, int baseX, int baseZ) {
        double[] fallback = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = baseX + world.rand.nextInt(9) - 4;
            int z = baseZ + world.rand.nextInt(9) - 4;
            if (!game.isInside(x, z)) {
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

    /** Sends the units to x, z, standing in a square block around it. */
    public void orderMove(List<EntityUnit> units, double x, double z) {
        Game game = GameManager.getGame();
        int columns = (int) Math.ceil(Math.sqrt(units.size()));
        int rows = (int) Math.ceil(units.size() / (double) Math.max(1, columns));
        for (int i = 0; i < units.size(); i++) {
            double slotX = x + (i % columns - (columns - 1) / 2.0) * SPACING;
            double slotZ = z + (i / columns - (rows - 1) / 2.0) * SPACING;
            if (game != null) {
                slotX = Math.max(game.getMinX() + 0.5, Math.min(game.getMaxX() + 0.5, slotX));
                slotZ = Math.max(game.getMinZ() + 0.5, Math.min(game.getMaxZ() + 0.5, slotZ));
            }
            units.get(i)
                .orderMove(slotX, slotZ);
        }
    }

    public void orderAttack(List<EntityUnit> units, EntityLivingBase target) {
        for (EntityUnit unit : units) {
            unit.orderAttack(target);
        }
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
        if (entity instanceof EntityPlayer) {
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

    /** Whether a guarding unit attacks the entity: units and players of enemy teams, and hostile mobs. */
    public boolean isEnemy(EntityUnit unit, Entity other) {
        if (other instanceof EntityPlayer && ((EntityPlayer) other).capabilities.isCreativeMode) {
            return false;
        }
        Game game = GameManager.getGame();
        if (game == null) {
            return false;
        }
        Team team = game.getTeam(unit.getTeamId());
        if (other instanceof EntityUnit || other instanceof EntityPlayer) {
            Team otherTeam = teamOf(game, other);
            return team != null && otherTeam != null && game.areEnemies(team, otherTeam);
        }
        return other instanceof IMob;
    }

    /** The nearest enemy the unit can see within range, or null. */
    EntityLivingBase findEnemyNear(EntityUnit unit, double range) {
        EntityLivingBase best = null;
        double bestDistance = range * range;
        for (Object object : unit.worldObj
            .getEntitiesWithinAABB(EntityLivingBase.class, unit.boundingBox.expand(range, 4, range))) {
            EntityLivingBase other = (EntityLivingBase) object;
            if (other == unit || !other.isEntityAlive() || !isEnemy(unit, other)) {
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
}
