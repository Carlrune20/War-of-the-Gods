package com.warofthegods.building;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.Explosion;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.game.Building;
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Faction;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Team;
import com.warofthegods.game.UnitKind;
import com.warofthegods.game.Upgrade;
import com.warofthegods.unit.EntityTeamArrow;
import com.warofthegods.unit.EntityUnit;
import com.warofthegods.unit.Units;
import com.warofthegods.web.WebMap;
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

    /** Structures put into the world per interval, as big ones take a while. */
    private static final int PASTES_PER_INTERVAL = 2;

    private final EnumMap<BuildingType, Schematic> schematics = new EnumMap<>(BuildingType.class);
    /** Every look of every building, by faction folder and name, such as zamorak/house_l2_d1. */
    private final Map<String, Schematic> variants = new HashMap<>();
    private final Map<Integer, Integer> harvestTicks = new HashMap<>();
    private final Map<Integer, Integer> shotTicks = new HashMap<>();
    private int ticks;

    public Buildings() {
        instance = this;
        for (BuildingType type : BuildingType.values()) {
            // Every faction's look of a type has the same size; the first level stands for them all.
            schematics.put(type, variant(Faction.SARADOMIN, type.id + "_l1"));
        }
    }

    public static Buildings get() {
        return instance;
    }

    /** The structure of the type at its first level, which has the size and shape every look of it shares. */
    public Schematic schematic(BuildingType type) {
        return schematics.get(type);
    }

    /** One look of a building, such as house_l2_d1 for a damaged level 2 house; loaded the first time. */
    public Schematic variant(Faction faction, String name) {
        String key = faction.id + "/" + name;
        Schematic schematic = variants.get(key);
        if (schematic == null) {
            schematic = Schematic.load(key);
            variants.put(key, schematic);
        }
        return schematic;
    }

    /**
     * The look a building should have now: a building site of three stages as work on it goes on, then the
     * finished building at its level, looking worse at three quarters, half and a quarter of its health.
     */
    public static String lookOf(Building building) {
        String type = building.type.id;
        if (!building.isComplete()) {
            return type + "_stage" + Math.min(3, 1 + (int) (building.getBuildProgress() * 3));
        }
        double health = building.getHealth() / (double) building.getMaxHealth();
        int damage = health < 0.25 ? 3 : health < 0.5 ? 2 : health < 0.75 ? 1 : 0;
        return type + "_l" + building.getLevel() + (damage > 0 ? "_d" + damage : "");
    }

    private static Faction factionOf(Game game, Building building) {
        Team team = game.getTeam(building.team);
        return team == null ? Faction.SARADOMIN : team.getFaction();
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
        research(game);
        shoot(game, world);
        keepUpWithAge(game);
        updateLooks(game, world);
        smoulder(game, world);
    }

    /**
     * Badly damaged buildings show it: below half health smoke rises from them, and below a quarter they burn. The
     * fire is only for show and spreads nowhere.
     */
    private void smoulder(Game game, WorldServer world) {
        for (Building building : game.getBuildings()) {
            if (!building.isComplete() || building.getHealth() * 2 >= building.getMaxHealth()) {
                continue;
            }
            Schematic schematic = schematic(building.type);
            double top = building.groundY + schematic.height - schematic.ground;
            boolean burning = building.getHealth() * 4 < building.getMaxHealth();
            for (int i = 0; i < (burning ? 3 : 1); i++) {
                double x = building.minX + world.rand.nextDouble() * building.sizeX;
                double z = building.minZ + world.rand.nextDouble() * building.sizeZ;
                double y = Math
                    .max(building.groundY + 1, world.getHeightValue((int) Math.floor(x), (int) Math.floor(z)));
                y = Math.min(y, top + 1);
                world.func_147487_a("largesmoke", x, y, z, 4, 0.3, 0.2, 0.3, 0.02);
                if (burning) {
                    world.func_147487_a("flame", x, y, z, 6, 0.4, 0.3, 0.4, 0.01);
                    world.func_147487_a("lava", x, y, z, 1, 0.3, 0.1, 0.3, 0);
                }
            }
        }
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
        WorldServer world = GameWorld.getWorld();
        if (world != null) {
            show(world, game, site);
        }
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

    /** Marks the building complete and puts the finished structure into the world. */
    public void raise(WorldServer world, Building building) {
        building.setComplete();
        Game game = GameManager.getGame();
        if (game != null) {
            show(world, game, building);
        }
        WarOfTheGods.LOG.info("Built {}", game == null ? building.type.displayName : building.describe(game));
    }

    /** Puts the building's look into the world where it changed, a few buildings at a time. */
    private void updateLooks(Game game, WorldServer world) {
        int pasted = 0;
        for (Building building : new ArrayList<>(game.getBuildings())) {
            if (pasted >= PASTES_PER_INTERVAL) {
                return;
            }
            if (!lookOf(building).equals(building.getShown())) {
                if (show(world, game, building)) {
                    pasted++;
                }
            }
        }
    }

    /**
     * Puts the building's current look into the world. Anything standing on the footprint is moved off it first,
     * so nobody ends up inside a wall. Returns whether anything was pasted: a building from a save made before its
     * type took its present size keeps the blocks it has.
     */
    public boolean show(WorldServer world, Game game, Building building) {
        String look = lookOf(building);
        Schematic schematic = variant(factionOf(game, building), look);
        building.setShown(look);
        if (schematic.sizeX(building.rotation) != building.sizeX
            || schematic.sizeZ(building.rotation) != building.sizeZ) {
            return false;
        }
        paste(world, building, schematic);
        return true;
    }

    private static void paste(WorldServer world, Building building, Schematic schematic) {
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
        WebMap
            .blocksChanged(world, building.minX - 1, building.minZ - 1, building.getMaxX() + 1, building.getMaxZ() + 1);
    }

    // ---------------------------------------------------------------- training

    /** Units the team has room for: what its buildings house, less units it has and has waiting to train. */
    public static int room(Game game, Team team) {
        return game.getPopulationCap(team) - game.countUnits(team) - game.countQueued(team);
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
            long[] yield = harvestOf(game, workplace);
            for (EntityUnit worker : workers.subList(0, Math.min(workers.size(), workplace.type.maxWorkers()))) {
                worker.gather(yield);
            }
        }
    }

    /** What one worker gathers at a workplace per harvest, with its level and the team's upgrades. */
    public static long[] harvestOf(Game game, Building workplace) {
        long[] yield = workplace.type.yield(workplace.getLevel());
        Team team = game.getTeam(workplace.team);
        boolean better = team != null && (workplace.type == BuildingType.FARM ? team.has(Upgrade.PLOUGHS)
            : workplace.type == BuildingType.QUARRY && team.has(Upgrade.PICKAXES));
        if (better) {
            for (int i = 0; i < yield.length; i++) {
                yield[i] = yield[i] * 3 / 2;
            }
        }
        return yield;
    }

    // ---------------------------------------------------------------- levels and research

    /** Ticks a building takes over what it researches. */
    public static int researchTicks(Building building) {
        if (Building.LEVEL_UP.equals(building.getResearch())) {
            return building.type.upgradeTicks(building.getLevel() + 1);
        }
        Upgrade upgrade = Upgrade.byId(building.getResearch());
        return upgrade == null ? 1 : upgrade.ticks();
    }

    /**
     * Why the building cannot go up a level now, or null if it can, leaving out whether the team can pay. Only the
     * town center goes up, taking its team into the next age; the other buildings follow by themselves.
     */
    public static String levelUpProblem(Game game, Building building) {
        if (building.type != BuildingType.TOWN_HALL) {
            return "Buildings take on the look of each new age by themselves; advance the town center instead";
        }
        if (!building.isComplete()) {
            return "It is not built yet";
        }
        if (building.getLevel() >= building.type.levels) {
            return "It is at its highest level";
        }
        if (building.getResearch() != null) {
            return "It is busy";
        }
        return null;
    }

    /**
     * Takes the team of a town center into the next age: the town center goes up a level, and every other building
     * of the team takes on the new age's look with it.
     */
    public static void advanceAge(Game game, Building center) {
        center.levelUp();
        keepUpWithAge(game);
    }

    /**
     * Every finished building looks the age its team is in, as far as it has a look for it. Levels change only how
     * buildings look, so this takes nothing from them and gives them nothing.
     */
    private static void keepUpWithAge(Game game) {
        for (Building building : game.getBuildings()) {
            if (building.type == BuildingType.TOWN_HALL || !building.isComplete()) {
                continue;
            }
            Team team = game.getTeam(building.team);
            int level = Math.min(building.type.levels, team == null ? 1 : game.getAge(team));
            if (building.getLevel() != level) {
                if (Building.LEVEL_UP.equals(building.getResearch())) {
                    // Going up by itself from before ages did it for every building.
                    building.cancelResearch();
                }
                building.setLevel(level);
            }
        }
    }

    /** Why the upgrade cannot be researched at the building now, or null; leaves out whether the team can pay. */
    public static String researchProblem(Game game, Building building, Upgrade upgrade) {
        Team team = game.getTeam(building.team);
        if (building.type != upgrade.at) {
            return "That is researched at the " + upgrade.at.displayName.toLowerCase();
        }
        if (!building.isComplete()) {
            return "It is not built yet";
        }
        if (team != null && team.has(upgrade)) {
            return "Already researched";
        }
        for (Building other : game.getBuildings()) {
            if (other.team.equals(building.team) && upgrade.id.equals(other.getResearch())) {
                return "Already being researched";
            }
        }
        if (building.getResearch() != null) {
            return "It is busy";
        }
        if (team == null || game.getAge(team) < upgrade.level) {
            return "Your town has to reach " + BuildingType.ageName(upgrade.level) + " first";
        }
        return null;
    }

    /** Moves research and level-ups along, and finishes what is done. */
    private void research(Game game) {
        for (Building building : new ArrayList<>(game.getBuildings())) {
            if (!building.isComplete() || building.getResearch() == null) {
                continue;
            }
            String done = building.research(INTERVAL, researchTicks(building));
            if (done == null) {
                continue;
            }
            Team team = game.getTeam(building.team);
            if (Building.LEVEL_UP.equals(done)) {
                if (building.type == BuildingType.TOWN_HALL) {
                    advanceAge(game, building);
                }
                WarOfTheGods.LOG.info("Upgraded {}", building.describe(game));
                continue;
            }
            Upgrade upgrade = Upgrade.byId(done);
            if (team == null || upgrade == null) {
                continue;
            }
            Map<Building, Integer> before = new HashMap<>();
            for (Building own : game.getBuildings(team)) {
                before.put(own, own.getMaxHealth());
            }
            team.addUpgrade(upgrade);
            for (Map.Entry<Building, Integer> entry : before.entrySet()) {
                entry.getKey()
                    .addMaxHealth(entry.getValue());
            }
            for (EntityUnit unit : Units.get()
                .getUnits(team)) {
                unit.applyUpgrades(team);
            }
            WarOfTheGods.LOG.info("{} researched {}", team.getName(), upgrade.displayName.toLowerCase());
        }
    }

    // ---------------------------------------------------------------- towers

    /** Towers, and town centers from level 2, shoot arrows at the nearest enemy in range. */
    private void shoot(Game game, WorldServer world) {
        for (Building building : game.getBuildings()) {
            if (!building.isComplete() || !building.type.shoots(building.getLevel())) {
                shotTicks.remove(building.id);
                continue;
            }
            int every = Config.towerShotTicks;
            int elapsed = shotTicks.getOrDefault(building.id, 0) + INTERVAL;
            if (elapsed < every) {
                shotTicks.put(building.id, elapsed);
                continue;
            }
            Team team = game.getTeam(building.team);
            if (team == null) {
                continue;
            }
            int range = building.type.range(building.getLevel()) + (team.has(Upgrade.FLETCHING) ? 4 : 0);
            double y = building.groundY + shootHeight(building);
            EntityLivingBase target = Units.get()
                .findEnemyNear(world, team, building.getCenterX(), y, building.getCenterZ(), range);
            if (target == null) {
                shotTicks.put(building.id, every);
                continue;
            }
            shotTicks.put(building.id, 0);
            // From the side facing the target, so the arrow clears the building's own walls.
            double dx = target.posX - building.getCenterX();
            double dz = target.posZ - building.getCenterZ();
            double length = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
            double out = Math.min(building.sizeX, building.sizeZ) / 2.0 + 0.6;
            EntityTeamArrow arrow = new EntityTeamArrow(
                world,
                building.getCenterX() + dx / length * out,
                y,
                building.getCenterZ() + dz / length * out,
                team.getId());
            arrow.aim(target, Config.towerDamage + (team.has(Upgrade.FLETCHING) ? 1 : 0));
            world.spawnEntityInWorld(arrow);
        }
    }

    /** Blocks above the ground a building shoots from: a tower's deck, the town center's upper windows. */
    private static int shootHeight(Building building) {
        if (building.type == BuildingType.TOWER) {
            return building.getLevel() >= 2 ? 14 : 11;
        }
        return 9;
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
     * Takes health off a building; at 0 it is gone, leaving a ruin, and units training there are lost. A destroyed
     * building site is cleared away.
     */
    public void damage(Building building, int amount, String cause) {
        Game game = GameManager.getGame();
        if (game == null || game.getBuilding(building.id) != building || !building.damage(amount)) {
            return;
        }
        WorldServer world = DimensionManager.getWorld(GameWorld.dimensionId());
        if (world != null) {
            tearDown(world, game, building);
        }
        game.removeBuilding(building);
        harvestTicks.remove(building.id);
        shotTicks.remove(building.id);
        WarOfTheGods.LOG.info("Destroyed {} ({})", building.describe(game), cause);
    }

    /** A destroyed building leaves its ruin; a destroyed building site leaves bare ground. */
    private void tearDown(WorldServer world, Game game, Building building) {
        Schematic ruin = variant(factionOf(game, building), building.type.id + "_ruin");
        if (building.isComplete() && ruin.sizeX(building.rotation) == building.sizeX
            && ruin.sizeZ(building.rotation) == building.sizeZ) {
            paste(world, building, ruin);
            return;
        }
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
        WebMap
            .blocksChanged(world, building.minX - 1, building.minZ - 1, building.getMaxX() + 1, building.getMaxZ() + 1);
    }

    /** The building or building site whose blocks hold x, y, z, or null. */
    public Building buildingWithBlock(Game game, int x, int y, int z) {
        for (Building building : game.getBuildings()) {
            Schematic schematic = schematic(building.type);
            if (building.getShown() != null && building.holds(x, y, z, schematic.height - schematic.ground)) {
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
        if (isFlimsy(event.block.getBlockHardness(event.world, event.x, event.y, event.z), event.block)) {
            // Torches, glass, beds and the like break in a moment; picking at them would knock a building down for
            // free.
            event.getPlayer()
                .addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GRAY + "That is part of the "
                            + building.describe(game)
                            + "; breaking it does the building no harm."));
        } else if (owner != null && breaker != null && game.areEnemies(breaker, owner)) {
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

    /** Set while an explosion that should hurt no one goes off. */
    private boolean harmless;

    /** An explosion that breaks blocks and damages buildings as a creeper's would, but hurts no one: for testing. */
    public void explodeHarmlessly(WorldServer world, double x, double y, double z, float size) {
        harmless = true;
        try {
            world.createExplosion(null, x, y, z, size, true);
        } finally {
            harmless = false;
        }
    }

    /** Blocks so quick to break, such as torches, glass and beds, that breaking one does a building no harm. */
    static boolean isFlimsy(float hardness, Block block) {
        return hardness < 0.5F || block.getMaterial() == Material.glass
            || block == Blocks.bed
            || block == Blocks.ladder
            || block == Blocks.wool
            || block == Blocks.carpet;
    }

    /**
     * Explosions near buildings, such as creepers going off, take health off them instead of blowing holes in them:
     * nothing within {@link Config#explosionProtection} blocks of a building or building site breaks.
     */
    @SubscribeEvent
    public void onDetonate(ExplosionEvent.Detonate event) {
        if (event.world.isRemote || !GameWorld.isGameWorld(event.world)) {
            return;
        }
        if (harmless) {
            event.getAffectedEntities()
                .clear();
        }
        Game game = GameManager.getGame();
        if (game == null) {
            return;
        }
        Explosion explosion = event.explosion;
        int pad = Config.explosionProtection;
        // By now the explosion has doubled its size for working out what it hits; a creeper's is 3 to begin with.
        double size = explosion.explosionSize / 2;
        double reach = size * 2 + 1;
        for (Building building : new ArrayList<>(game.getBuildings())) {
            if (building.getShown() == null) {
                continue;
            }
            Schematic schematic = schematic(building.type);
            int top = building.groundY + schematic.height - schematic.ground;
            Iterator<ChunkPosition> blocks = event.getAffectedBlocks()
                .iterator();
            while (blocks.hasNext()) {
                ChunkPosition block = blocks.next();
                if (building.covers(block.chunkPosX + 0.5, block.chunkPosZ + 0.5, pad)
                    && block.chunkPosY >= building.groundY - pad
                    && block.chunkPosY <= top + pad) {
                    blocks.remove();
                }
            }
            double dx = Math.max(
                0,
                Math.max(building.minX - explosion.explosionX, explosion.explosionX - (building.getMaxX() + 1)));
            double dz = Math.max(
                0,
                Math.max(building.minZ - explosion.explosionZ, explosion.explosionZ - (building.getMaxZ() + 1)));
            double dy = Math.max(0, Math.max(building.groundY - explosion.explosionY, explosion.explosionY - top));
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < reach) {
                int amount = (int) Math
                    .round(Config.explosionBuildingDamage * (1 - distance / reach) * Math.min(2, size / 3));
                if (amount > 0) {
                    String cause = explosion.exploder instanceof EntityCreeper ? "a creeper exploded" : "an explosion";
                    damage(building, amount, cause);
                }
            }
        }
    }
}
