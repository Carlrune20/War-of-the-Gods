package com.warofthegods.building;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.game.Building;
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Team;
import com.warofthegods.game.UnitKind;
import com.warofthegods.unit.EntityUnit;
import com.warofthegods.unit.Units;
import com.warofthegods.world.GameWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Runs the buildings of the running game on the server thread: puts a town hall at every team's base, checks where
 * new buildings may go, raises them when a worker reaches the site, trains units and pays out farm harvests.
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
    /** How close a worker has to come to a site to build it, or to a farm to work it. */
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
        train(game);
        harvest(game);
    }

    // ---------------------------------------------------------------- town halls

    /** Raises a town hall at the base of a team that has a base and no town hall; one per tick, as it is big. */
    private void placeTownHalls(Game game, WorldServer world) {
        for (Team team : game.getTeams()) {
            int[] base = team.getBase();
            if (base == null || game.getTownHall(team) != null) {
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

    // ---------------------------------------------------------------- farming

    /** Workers working at the farm, up to how many it has work for. */
    public static int countWorkers(Building farm) {
        int count = 0;
        for (EntityUnit unit : Units.get()
            .getLoaded()) {
            if (unit.isWorkingAt(farm.id)) {
                count++;
            }
        }
        return Math.min(count, Config.farmWorkers);
    }

    private void harvest(Game game) {
        int ticksPerHarvest = Config.farmYieldSeconds * 20;
        for (Building farm : game.getBuildings()) {
            if (farm.type != BuildingType.FARM || !farm.isComplete()) {
                continue;
            }
            int workers = countWorkers(farm);
            if (workers == 0) {
                harvestTicks.remove(farm.id);
                continue;
            }
            int elapsed = harvestTicks.getOrDefault(farm.id, 0) + INTERVAL;
            if (elapsed < ticksPerHarvest) {
                harvestTicks.put(farm.id, elapsed);
                continue;
            }
            harvestTicks.put(farm.id, 0);
            Team team = game.getTeam(farm.team);
            if (team != null) {
                team.addResource(Resource.FOOD, (long) Config.farmFood * workers);
                team.addResource(Resource.WOOD, (long) Config.farmWood * workers);
            }
        }
    }
}
