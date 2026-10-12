package com.warofthegods.building;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.BlockEvent;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.game.Building;
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Team;
import com.warofthegods.game.UnitKind;
import com.warofthegods.unit.EntityUnit;
import com.warofthegods.unit.Units;
import com.warofthegods.world.GameWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Runs the buildings of the running game on the server thread: puts a town hall at every team's base, checks where
 * new buildings may go, raises them once workers have built the site, trains units, hands out harvests to the
 * workers at farms and quarries, and takes buildings down when enemies destroy them.
 *
 * <p>
 * Enemy players cannot break a building's blocks: the block stays and the building loses health instead.
 */
public final class Buildings {

    /** Ticks between looking over all buildings. */
    private static final int INTERVAL = 10;
    /** Blocks kept free between buildings. */
    public static final int GAP = 2;
    /** Most of a footprint that may be water. */
    private static final double MAX_WATER = 0.25;
    /** Most the ground may rise and fall under a footprint. */
    private static final int MAX_SLOPE = 12;
    /** Units one building may have waiting to train. */
    public static final int MAX_QUEUE = 10;
    /** How close a worker has to come to a site to build it, to a farm to work it, or a soldier to hit a building. */
    public static final double REACH = 2.5;

    private static Buildings instance;

    private final EnumMap<BuildingType, Schematic> schematics = new EnumMap<>(BuildingType.class);
    private final Map<Integer, Integer> harvestTicks = new HashMap<>();
    private int ticks;

    public Buildings() {
        instance = this;
        for (BuildingType type : BuildingType.values()) {
            schematics.put(type, Schematic.load(type.schematic));
        }
    }

    public static Buildings get() {
        return instance;
    }

    public Schematic schematic(BuildingType type) {
        return schematics.get(type);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % INTERVAL != 0) {
            return;
        }
        Game game = DimensionManager.getWorld(0) == null ? null : GameManager.getGame();
        WorldServer world = DimensionManager.getWorld(GameWorld.dimensionId());
        if (game == null || world == null) {
            harvestTicks.clear();
            return;
        }
        placeTownHalls(game, world);
        construct(game, world);
        train(game);
        harvest(game);
    }

    // ---------------------------------------------------------------- town halls

    /** Raises a town hall at the base of a team that has a base and no town hall; one per tick, as it is big. */
    private void placeTownHalls(Game game, WorldServer world) {
        for (Team team : game.getTeams()) {
            int[] base = team.getBase();
            if (base == null || !team.isTownHallDue()) {
                continue;
            }
            if (game.getTownHall(team) != null) {
                team.setTownHallDue(false);
                continue;
            }
            // Face the middle of the map.
            int dx = game.getCenterX() - base[0];
            int dz = game.getCenterZ() - base[1];
            int rotation = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0);
            Schematic schematic = schematic(BuildingType.TOWN_HALL);
            int sizeX = schematic.sizeX(rotation);
            int sizeZ = schematic.sizeZ(rotation);
            int minX = clamp(base[0] - sizeX / 2, game.getMinX(), game.getMaxX() - sizeX + 1);
            int minZ = clamp(base[1] - sizeZ / 2, game.getMinZ(), game.getMaxZ() - sizeZ + 1);
            Site site = survey(world, minX, minZ, sizeX, sizeZ);
            Building hall = game
                .addBuilding(BuildingType.TOWN_HALL, team, minX, minZ, sizeX, sizeZ, site.groundY, rotation, false);
            raise(world, hall);
            team.setTownHallDue(false);
            return;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---------------------------------------------------------------- placing

    /** How the ground under a footprint lies. */
    public static final class Site {

        /** The y the structure's ground layer goes at: the usual height of the ground's top block. */
        public final int groundY;
        public final double water;
        public final int slope;

        Site(int groundY, double water, int slope) {
            this.groundY = groundY;
            this.water = water;
            this.slope = slope;
        }
    }

    /** Looks at the ground under a footprint. */
    public static Site survey(WorldServer world, int minX, int minZ, int sizeX, int sizeZ) {
        int[] heights = new int[sizeX * sizeZ];
        int water = 0;
        int i = 0;
        for (int x = minX; x < minX + sizeX; x++) {
            for (int z = minZ; z < minZ + sizeZ; z++) {
                int top = world.getTopSolidOrLiquidBlock(x, z) - 1;
                heights[i++] = top;
                if (world.getBlock(x, top, z)
                    .getMaterial()
                    .isLiquid()) {
                    water++;
                }
            }
        }
        Arrays.sort(heights);
        // Ignore the highest and lowest few columns: a tree or a pit should not decide.
        int trim = heights.length / 10;
        int slope = heights[heights.length - 1 - trim] - heights[trim];
        return new Site(heights[heights.length / 2], water / (double) heights.length, slope);
    }

    /** A planned building: where its footprint goes, or why it cannot go there. */
    public static final class Plan {

        public final BuildingType type;
        public final int minX;
        public final int minZ;
        public final int sizeX;
        public final int sizeZ;
        public final int rotation;
        public final int groundY;
        /** Why it cannot be built there, or null if it can. */
        public final String problem;

        Plan(BuildingType type, int minX, int minZ, int sizeX, int sizeZ, int rotation, int groundY, String problem) {
            this.type = type;
            this.minX = minX;
            this.minZ = minZ;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.rotation = rotation;
            this.groundY = groundY;
            this.problem = problem;
        }
    }

    /**
     * Checks a building of the type centred on x, z: inside the map, clear of other buildings, on ground that is
     * dry and level enough. The web map centres its preview the same way: the footprint's corner is the centre less
     * half the size, rounded down.
     */
    public Plan plan(Game game, WorldServer world, BuildingType type, int centerX, int centerZ, int rotation) {
        rotation &= 3;
        Schematic schematic = schematic(type);
        int sizeX = schematic.sizeX(rotation);
        int sizeZ = schematic.sizeZ(rotation);
        int minX = centerX - sizeX / 2;
        int minZ = centerZ - sizeZ / 2;
        if (minX < game.getMinX() || minZ < game.getMinZ()
            || minX + sizeX - 1 > game.getMaxX()
            || minZ + sizeZ - 1 > game.getMaxZ()) {
            return new Plan(type, minX, minZ, sizeX, sizeZ, rotation, 0, "That is outside the map");
        }
        for (Building other : game.getBuildings()) {
            if (other.overlaps(minX, minZ, sizeX, sizeZ, GAP)) {
                return new Plan(type, minX, minZ, sizeX, sizeZ, rotation, 0, "That is too close to another building");
            }
        }
        Site site = survey(world, minX, minZ, sizeX, sizeZ);
        String problem = null;
        if (site.water > MAX_WATER) {
            problem = "That is too wet to build on";
        } else if (site.slope > MAX_SLOPE) {
            problem = "That ground is too steep";
        } else if (site.groundY - schematic.ground < 1 || site.groundY - schematic.ground + schematic.height > 255) {
            problem = "That is too high or too low";
        }
        return new Plan(type, minX, minZ, sizeX, sizeZ, rotation, site.groundY, problem);
    }

    /** Lays out a building site for a plan without a problem. */
    public Building startSite(Game game, Team team, Plan plan) {
        Building site = game.addBuilding(
            plan.type,
            team,
            plan.minX,
            plan.minZ,
            plan.sizeX,
            plan.sizeZ,
            plan.groundY,
            plan.rotation,
            false);
        WarOfTheGods.LOG.info("Started {}", site.describe(game));
        return site;
    }

    /** Looks around the team's town hall for a spot a building of the type fits, nearest first; null if none. */
    public Plan findSpot(Game game, WorldServer world, Team team, BuildingType type) {
        Building hall = game.getTownHall(team);
        int[] base = team.getBase();
        if (hall == null && base == null) {
            return null;
        }
        int cx = hall != null ? (int) hall.getCenterX() : base[0];
        int cz = hall != null ? (int) hall.getCenterZ() : base[1];
        // Start far enough out that it clears the town hall with room to walk around it.
        Schematic schematic = schematic(type);
        int start = (hall == null ? 0 : Math.max(hall.sizeX, hall.sizeZ) / 2)
            + Math.max(schematic.width, schematic.length) / 2
            + GAP
            + 4;
        for (int ring = start; ring <= start + 72; ring += 6) {
            for (int step = 0; step < 16; step++) {
                double angle = 2 * Math.PI * step / 16;
                int x = cx + (int) Math.round(Math.cos(angle) * ring);
                int z = cz + (int) Math.round(Math.sin(angle) * ring);
                Plan plan = plan(game, world, type, x, z, step / 4);
                if (plan.problem == null) {
                    return plan;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- building

    /** Workers at the building site, building it. */
    public static int countBuilders(Building site) {
        int count = 0;
        for (EntityUnit unit : Units.get()
            .getLoaded()) {
            if (unit.isBuildingAt(site.id)) {
                count++;
            }
        }
        return count;
    }

    /** Puts the work of the workers at each building site in, and raises the sites that are done. */
    private void construct(Game game, WorldServer world) {
        for (Building site : new ArrayList<>(game.getBuildings())) {
            if (site.isComplete()) {
                continue;
            }
            int builders = countBuilders(site);
            if (builders > 0 && site.build(INTERVAL * builders)) {
                raise(world, site);
            }
        }
    }

    // ---------------------------------------------------------------- raising

    /**
     * Puts the structure into the world and marks the building complete. Anything standing on the footprint is
     * moved off it first, so nobody ends up inside a wall.
     */
    public void raise(WorldServer world, Building building) {
        Schematic schematic = schematic(building.type);
        int baseY = building.groundY - schematic.ground;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
            building.minX,
            baseY,
            building.minZ,
            building.minX + building.sizeX,
            baseY + schematic.height + 32,
            building.minZ + building.sizeZ);
        List<?> inside = world.getEntitiesWithinAABB(EntityLivingBase.class, box);
        schematic.paste(world, building.minX, building.minZ, building.groundY, building.rotation);
        for (Object object : inside) {
            EntityLivingBase entity = (EntityLivingBase) object;
            double[] out = building.nearestOutside(entity.posX, entity.posZ);
            int y = world.getTopSolidOrLiquidBlock((int) Math.floor(out[0]), (int) Math.floor(out[1]));
            if (entity instanceof EntityPlayerMP) {
                ((EntityPlayerMP) entity).playerNetServerHandler
                    .setPlayerLocation(out[0], y, out[1], entity.rotationYaw, entity.rotationPitch);
            } else {
                entity.setPositionAndUpdate(out[0], y, out[1]);
            }
        }
        building.setComplete();
        Game game = GameManager.getGame();
        WarOfTheGods.LOG.info("Built {}", game == null ? building.type.displayName : building.describe(game));
    }

    // ---------------------------------------------------------------- training

    /** Units the team has trained or waiting to train, against its limit. */
    public static int room(Game game, Team team) {
        return Config.unitsPerTeam - game.countUnits(team) - game.countQueued(team);
    }

    private void train(Game game) {
        int ticksPerUnit = Math.max(1, Config.trainSeconds * 20);
        for (Building building : new ArrayList<>(game.getBuildings())) {
            if (!building.isComplete() || building.getQueue()
                .isEmpty()) {
                continue;
            }
            Team team = game.getTeam(building.team);
            UnitKind trained = building.train(INTERVAL, ticksPerUnit);
            if (team == null || trained == null) {
                continue;
            }
            double[] door = building.getDoorstep();
            List<EntityUnit> spawned = Units.get()
                .spawn(game, team, trained, 1, door[0], door[1]);
            if (spawned.isEmpty()) {
                // Over the limit after all, such as after an op spawned units; try again later.
                building.requeueFirst(trained);
            } else {
                WarOfTheGods.LOG.info(
                    "{} trained {}",
                    building.describe(game),
                    spawned.get(0)
                        .getCommandSenderName()
                        .replaceAll("\u00a7.", ""));
            }
        }
    }

    // ---------------------------------------------------------------- gathering

    /** Workers working at a farm or quarry, up to how many it has work for. */
    public static int countWorkers(Building workplace) {
        return Math.min(workersAt(workplace).size(), workplace.type.maxWorkers());
    }

    private static List<EntityUnit> workersAt(Building workplace) {
        List<EntityUnit> workers = new ArrayList<>();
        for (EntityUnit unit : Units.get()
            .getLoaded()) {
            if (unit.isWorkingAt(workplace.id)) {
                workers.add(unit);
            }
        }
        // The first to come keep their place when more come than there is work for.
        workers.sort((a, b) -> Integer.compare(a.getEntityId(), b.getEntityId()));
        return workers;
    }

    /** Hands each harvest to the workers at farms and quarries, who carry it to a town hall or storehouse. */
    private void harvest(Game game) {
        int ticksPerHarvest = Config.farmYieldSeconds * 20;
        for (Building workplace : game.getBuildings()) {
            if (!workplace.type.isWorkplace() || !workplace.isComplete()) {
                continue;
            }
            List<EntityUnit> workers = workersAt(workplace);
            if (workers.isEmpty()) {
                harvestTicks.remove(workplace.id);
                continue;
            }
            int elapsed = harvestTicks.getOrDefault(workplace.id, 0) + INTERVAL;
            if (elapsed < ticksPerHarvest) {
                harvestTicks.put(workplace.id, elapsed);
                continue;
            }
            harvestTicks.put(workplace.id, 0);
            long[] yield = workplace.type.yield();
            for (EntityUnit worker : workers.subList(0, Math.min(workers.size(), workplace.type.maxWorkers()))) {
                worker.gather(yield);
            }
        }
    }

    /** The nearest finished town hall or storehouse of the team, or null. */
    public static Building nearestStore(Game game, String team, double x, double z) {
        Building best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Building building : game.getBuildings()) {
            if (building.team.equals(team) && building.type.storesResources && building.isComplete()) {
                double distance = building.distanceTo(x, z);
                if (distance < bestDistance) {
                    best = building;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- damage

    /**
     * Takes health off a building; at 0 it is gone: its blocks above ground are cleared, the ground layer turns back
     * to grass, and units training there are lost. Foundations stay in the ground.
     */
    public void damage(Building building, int amount, String cause) {
        Game game = GameManager.getGame();
        if (game == null || game.getBuilding(building.id) != building || !building.damage(amount)) {
            return;
        }
        WorldServer world = DimensionManager.getWorld(GameWorld.dimensionId());
        if (world != null) {
            tearDown(world, building);
        }
        game.removeBuilding(building);
        harvestTicks.remove(building.id);
        WarOfTheGods.LOG.info("Destroyed {} ({})", building.describe(game), cause);
    }

    private void tearDown(WorldServer world, Building building) {
        Schematic schematic = schematic(building.type);
        int top = building.groundY - schematic.ground + schematic.height;
        for (int x = building.minX; x <= building.getMaxX(); x++) {
            for (int z = building.minZ; z <= building.getMaxZ(); z++) {
                // Top down, so nothing hanging on a wall drops as an item.
                for (int y = Math.min(255, top); y > building.groundY; y--) {
                    if (!world.isAirBlock(x, y, z)) {
                        world.setBlock(x, y, z, Blocks.air, 0, 2);
                    }
                }
                if (world.getBlock(x, building.groundY, z) != Blocks.grass) {
                    world.setBlock(x, building.groundY, z, Blocks.grass, 0, 2);
                }
            }
        }
    }

    /** The building whose blocks hold x, y, z, or null. */
    public Building buildingWithBlock(Game game, int x, int y, int z) {
        for (Building building : game.getBuildings()) {
            Schematic schematic = schematic(building.type);
            if (building.isComplete() && building.holds(x, y, z, schematic.height - schematic.ground)) {
                return building;
            }
        }
        return null;
    }

    /**
     * Players cannot break buildings' blocks in the game world. When an enemy tries, the block stays and the
     * building loses health; players in creative mode, such as ops shaping the map, break blocks as usual.
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.world.isRemote || event.world.provider.dimensionId != GameWorld.dimensionId()
            || event.getPlayer() == null
            || event.getPlayer().capabilities.isCreativeMode) {
            return;
        }
        Game game = GameManager.getGame();
        Building building = game == null ? null : buildingWithBlock(game, event.x, event.y, event.z);
        if (building == null) {
            return;
        }
        event.setCanceled(true);
        Team owner = game.getTeam(building.team);
        Team breaker = game.getTeamOf(
            event.getPlayer()
                .getUniqueID());
        if (owner != null && breaker != null && game.areEnemies(breaker, owner)) {
            damage(
                building,
                Config.blockBreakDamage,
                event.getPlayer()
                    .getCommandSenderName() + " broke a block");
        } else {
            event.getPlayer()
                .addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GRAY + "That is part of the "
                            + building.describe(game)
                            + "; only enemies can damage it."));
        }
    }
}
