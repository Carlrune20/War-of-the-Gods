package com.warofthegods.building;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.Explosion;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.block.BlockBanner;
import com.warofthegods.game.BridgeLine;
import com.warofthegods.game.Building;
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Faction;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.GoldMine;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Ruin;
import com.warofthegods.game.Settlement;
import com.warofthegods.game.Team;
import com.warofthegods.game.UnitKind;
import com.warofthegods.game.Upgrade;
import com.warofthegods.game.WallLine;
import com.warofthegods.rts.AttackAlerts;
import com.warofthegods.shop.Shop;
import com.warofthegods.unit.EntityTeamArrow;
import com.warofthegods.unit.EntityTrebuchetStone;
import com.warofthegods.unit.EntityUnit;
import com.warofthegods.unit.Units;
import com.warofthegods.web.WebMap;
import com.warofthegods.world.GameWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Runs the buildings of the running game on the server thread: puts a town hall at every team's base, checks where
 * new buildings may go, raises them once workers have built the site, trains units, hands out harvests to the
 * workers at farms, and takes buildings down when enemies destroy them.
 *
 * <p>
 * Enemy players cannot break a building's blocks: the block stays and the building loses health instead.
 */
public final class Buildings {

    /** Ticks between looking over all buildings. */
    private static final int INTERVAL = 10;
    /** Blocks from a gate's middle within which the team's own people and its allies open it. */
    private static final double GATE_REACH = 7;
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
    /** Blocks above the ground a knights' fortress shoots from: the top of its curtain walls. */
    private static final int FORTRESS_SHOOT_HEIGHT = 9;

    private final EnumMap<BuildingType, Schematic> schematics = new EnumMap<>(BuildingType.class);
    /** Every look of every building, by faction folder and name, such as zamorak/house_l2_d1. */
    private final Map<String, Schematic> variants = new HashMap<>();
    /** Looks cut short, by the look they were cut from and how long they are. */
    private final Map<Schematic, Schematic[]> shortCuts = new IdentityHashMap<>();
    private final Map<Integer, Integer> harvestTicks = new HashMap<>();
    private final Map<Integer, Integer> shotTicks = new HashMap<>();
    private final MapFeatures features = new MapFeatures();
    private int ticks;

    public Buildings() {
        instance = this;
        for (BuildingType type : BuildingType.values()) {
            // Every faction's look of a type has the same size; the first level stands for them all. A piece of
            // bridge is laid by Bridges, and its structure only gives its size.
            schematics.put(
                type,
                type == BuildingType.BRIDGE ? Schematic.load("neutral/bridge_piece")
                    : variant(Faction.SARADOMIN, type.id + "_l1"));
        }
    }

    public static Buildings get() {
        return instance;
    }

    /** The settlements and gold mines of the map. */
    public MapFeatures features() {
        return features;
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
     * A look cut to the building's footprint: a short segment of wall, closing a gap, is the first blocks of a full
     * one. Any other building keeps the look as it is.
     */
    private Schematic fitted(Schematic schematic, Building building) {
        if (building.type != BuildingType.WALL) {
            return schematic;
        }
        boolean alongX = (building.rotation & 1) == 0;
        int along = alongX ? building.sizeX : building.sizeZ;
        int across = alongX ? building.sizeZ : building.sizeX;
        if (across != schematic.length || along < 1 || along >= schematic.width) {
            return schematic;
        }
        Schematic[] cuts = shortCuts.computeIfAbsent(schematic, whole -> new Schematic[whole.width]);
        if (cuts[along] == null) {
            cuts[along] = schematic.cropped(along);
        }
        return cuts[along];
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
        features.placeBridgesIfDue(game, world);
        features.placeIfDue(game, world);
        construct(game, world);
        train(game);
        harvest(game);
        worship(game, world);
        research(game);
        shoot(game, world);
        gates(game, world);
        if (ticks % (INTERVAL * 2) == 0) {
            keepAnimalsOut(game, world);
        }
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
            if (game.getTownHall(team) != null || team.isDefeated()) {
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
            hall.setLevel(game.getAge(team));
            if (game.settlementOf(hall) == null) {
                game.addSettlement(minX, minZ, sizeX, sizeZ, site.groundY, rotation);
            }
            clearTrees(game, world, minX, minZ, sizeX, sizeZ, site.groundY);
            raise(world, hall);
            team.setTownHallDue(false);
            return;
        }
    }

    /** How far round a town center or settlement trees are cleared away, so none grows into it or blocks the way. */
    private static final int TREE_MARGIN = 5;

    /**
     * Fells the trees on a town center's or settlement's footprint and a few blocks round it: their logs and leaves,
     * where no building, settlement or gold mine stands. Leaves of trees further off that hang over decay by
     * themselves.
     */
    public static void clearTrees(Game game, WorldServer world, int minX, int minZ, int sizeX, int sizeZ, int groundY) {
        for (int x = minX - TREE_MARGIN; x < minX + sizeX + TREE_MARGIN; x++) {
            for (int z = minZ - TREE_MARGIN; z < minZ + sizeZ + TREE_MARGIN; z++) {
                if (x < game.getMinX() || x > game.getMaxX() || z < game.getMinZ() || z > game.getMaxZ()) {
                    continue;
                }
                boolean taken = false;
                for (Building building : game.getBuildings()) {
                    taken |= building.covers(x + 0.5, z + 0.5, 0);
                }
                for (Settlement settlement : game.getSettlements()) {
                    taken |= settlement.overlaps(x, z, 1, 1, 0);
                }
                for (GoldMine mine : game.getMines()) {
                    taken |= mine.overlaps(x, z, 1, 1, 0);
                }
                for (Ruin ruin : game.getRuins()) {
                    taken |= ruin.overlaps(x, z, 1, 1, 0);
                }
                if (taken) {
                    continue;
                }
                // Top down, so nothing drops.
                for (int y = Math.min(255, groundY + 40); y > Math.max(1, groundY - 4); y--) {
                    Block block = world.getBlock(x, y, z);
                    Material material = block.getMaterial();
                    if (block instanceof BlockLog || material == Material.leaves || material == Material.vine) {
                        world.setBlock(x, y, z, Blocks.air, 0, 2);
                    }
                }
            }
        }
        WebMap.blocksChanged(
            world,
            minX - TREE_MARGIN - 1,
            minZ - TREE_MARGIN - 1,
            minX + sizeX + TREE_MARGIN,
            minZ + sizeZ + TREE_MARGIN);
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
                // Despite its name, getTopSolidOrLiquidBlock passes through water to the bed under it, so look at
                // what lies on top of the ground.
                if (world.getBlock(x, top + 1, z)
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
        if (type == BuildingType.TOWN_HALL) {
            // Town centers only go on free settlements, which decide where and which way.
            Settlement settlement = game.freeSettlementNear(centerX, centerZ, 4);
            if (settlement == null) {
                int sizeX = schematic.sizeX(rotation);
                int sizeZ = schematic.sizeZ(rotation);
                return new Plan(
                    type,
                    centerX - sizeX / 2,
                    centerZ - sizeZ / 2,
                    sizeX,
                    sizeZ,
                    rotation,
                    0,
                    "A town center can only go on a settlement");
            }
            return new Plan(
                type,
                settlement.minX,
                settlement.minZ,
                settlement.sizeX,
                settlement.sizeZ,
                settlement.rotation,
                settlement.groundY,
                null);
        }
        if (type == BuildingType.QUARRY) {
            return planQuarry(game, schematic, centerX, centerZ, rotation);
        }
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
        for (Settlement settlement : game.getSettlements()) {
            if (settlement.overlaps(minX, minZ, sizeX, sizeZ, GAP)) {
                return new Plan(
                    type,
                    minX,
                    minZ,
                    sizeX,
                    sizeZ,
                    rotation,
                    0,
                    "That is too close to a settlement; only a town center can go there");
            }
        }
        for (GoldMine mine : game.getMines()) {
            if (mine.overlaps(minX, minZ, sizeX, sizeZ, GAP)) {
                return new Plan(type, minX, minZ, sizeX, sizeZ, rotation, 0, "That is too close to a gold mine");
            }
        }
        if (MapFeatures.bridgeNear(game, minX, minZ, sizeX, sizeZ, GAP + 4)) {
            return new Plan(type, minX, minZ, sizeX, sizeZ, rotation, 0, "That would block a bridge");
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

    /** A quarry only goes on a mined out mine with none on it yet, in the middle of the mine, on its ground. */
    private static Plan planQuarry(Game game, Schematic schematic, int centerX, int centerZ, int rotation) {
        int sizeX = schematic.sizeX(rotation);
        int sizeZ = schematic.sizeZ(rotation);
        GoldMine mine = game.freeMinedOutMineNear(centerX + 0.5, centerZ + 0.5, 4);
        if (mine == null) {
            return new Plan(
                BuildingType.QUARRY,
                centerX - sizeX / 2,
                centerZ - sizeZ / 2,
                sizeX,
                sizeZ,
                rotation,
                0,
                "A quarry can only go on a mined out gold mine");
        }
        return new Plan(
            BuildingType.QUARRY,
            mine.minX + (mine.size - sizeX) / 2,
            mine.minZ + (mine.size - sizeZ) / 2,
            sizeX,
            sizeZ,
            rotation,
            mine.groundY,
            null);
    }

    /** Lays out a building site for a plan without a problem; a stone circle stands at once. */
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
            if (site.type == BuildingType.TOWN_HALL) {
                clearTrees(game, world, site.minX, site.minZ, site.sizeX, site.sizeZ, site.groundY);
            }
            if (site.type.buildsInstantly()) {
                raise(world, site);
            } else {
                show(world, game, site);
            }
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
        if (type == BuildingType.TOWN_HALL) {
            // The nearest free settlement.
            Settlement best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Settlement settlement : game.getSettlements()) {
                double dx = settlement.getCenterX() - cx;
                double dz = settlement.getCenterZ() - cz;
                if (game.townCenterOn(settlement) == null && dx * dx + dz * dz < bestDistance) {
                    best = settlement;
                    bestDistance = dx * dx + dz * dz;
                }
            }
            return best == null ? null
                : plan(game, world, type, (int) best.getCenterX(), (int) best.getCenterZ(), best.rotation);
        }
        if (type == BuildingType.QUARRY) {
            // The nearest mined out mine with no quarry on it.
            GoldMine best = null;
            double bestDistance = Double.MAX_VALUE;
            for (GoldMine mine : game.getMines()) {
                double dx = mine.getCenterX() - cx;
                double dz = mine.getCenterZ() - cz;
                if (mine.isDepleted() && game.quarryOn(mine) == null && dx * dx + dz * dz < bestDistance) {
                    best = mine;
                    bestDistance = dx * dx + dz * dz;
                }
            }
            return best == null ? null : plan(game, world, type, (int) best.getCenterX(), (int) best.getCenterZ(), 0);
        }
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

    /**
     * Puts the work of the workers at each building site in, and raises the sites that are done. Workers at a
     * damaged building repair it, a third as fast as they would build it (repairTimeMultiplier).
     */
    private void construct(Game game, WorldServer world) {
        for (Building site : new ArrayList<>(game.getBuildings())) {
            if (site.isComplete()) {
                int repairers = site.isDamaged() ? countBuilders(site) : 0;
                if (repairers > 0) {
                    site.mend(INTERVAL * repairers);
                    if (!site.isDamaged()) {
                        WarOfTheGods.LOG.info("Repaired {}", site.describe(game));
                    }
                }
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
        if (building.type == BuildingType.BRIDGE) {
            building.setShown(look);
            Bridges.show(world, game, building);
            return true;
        }
        Schematic schematic = fitted(variant(factionOf(game, building), look), building);
        building.setShown(look);
        if (schematic.sizeX(building.rotation) != building.sizeX
            || schematic.sizeZ(building.rotation) != building.sizeZ) {
            return false;
        }
        paste(world, building, schematic);
        placeShopChest(world, game, building);
        return true;
    }

    /**
     * A finished town center or armory keeps its shop chest whatever its look: where the building has its first chest
     * when whole, one is put back if a damaged look left none there.
     */
    private void placeShopChest(WorldServer world, Game game, Building building) {
        if (!building.isComplete() || !Shop.keepsShop(building.type)) {
            return;
        }
        Schematic whole = variant(factionOf(game, building), building.type.id + "_l" + building.getLevel());
        int baseY = building.groundY - whole.ground;
        for (int y = 0; y < whole.height; y++) {
            for (int z = 0; z < whole.length; z++) {
                for (int x = 0; x < whole.width; x++) {
                    if (whole.blockId(x, y, z) != Block.getIdFromBlock(Blocks.chest)) {
                        continue;
                    }
                    int[] at = whole.turned(x, z, building.rotation);
                    int wx = building.minX + at[0];
                    int wz = building.minZ + at[1];
                    if (world.getBlock(wx, baseY + y, wz) != Blocks.chest) {
                        world
                            .setBlock(wx, baseY + y, wz, Blocks.chest, whole.turnedMeta(x, y, z, building.rotation), 2);
                    }
                    return;
                }
            }
        }
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

    /**
     * Buildings set to train a kind over and over queue one more of it whenever their queue runs empty, paying for it
     * as a click would; one whose team cannot pay or has no room waits until it can.
     */
    private static void autoTrain(Game game) {
        for (Building building : game.getBuildings()) {
            UnitKind kind = building.getAutoTrain();
            if (kind == null || !building.isComplete()
                || !building.getQueue()
                    .isEmpty()) {
                continue;
            }
            Team team = game.getTeam(building.team);
            if (team == null || team.isDefeated()
                || !kind.unlockedFor(team)
                || !kind.isAnimal() && room(game, team) <= 0
                || game.heroRoom(team, kind) <= 0) {
                continue;
            }
            if (team.spend(kind.cost())) {
                building.enqueue(kind);
            }
        }
    }

    private void train(Game game) {
        autoTrain(game);
        for (Building building : new ArrayList<>(game.getBuildings())) {
            if (!building.isComplete() || building.getQueue()
                .isEmpty()) {
                continue;
            }
            Team team = game.getTeam(building.team);
            // A lesser demon is summoned only while enough dark wizards stand at the stone circle.
            if (building.type == BuildingType.STONE_CIRCLE) {
                if (countSummoners(game, building) < SUMMONERS) {
                    continue;
                }
                GameWorld.getWorld()
                    .func_147487_a(
                        "portal",
                        building.getCenterX(),
                        building.groundY + 1.5,
                        building.getCenterZ(),
                        20,
                        1.5,
                        0.5,
                        1.5,
                        0.5);
            }
            UnitKind trained = building.train(
                INTERVAL,
                building.getQueue()
                    .get(0)
                    .trainTicks());
            if (team == null || trained == null) {
                continue;
            }
            double[] door = building.getDoorstep();
            if (trained.isAnimal()) {
                // A horse for players to ride, not a unit: out it comes, saddled, whatever the team's room.
                Units.get()
                    .spawnHorse(team, door[0], door[1]);
                WarOfTheGods.LOG.info("{} trained a horse", building.describe(game));
                continue;
            }
            List<EntityUnit> spawned = Units.get()
                .spawn(game, team, trained, 1, door[0], door[1]);
            if (spawned.isEmpty()) {
                // Over the limit after all, such as after an op spawned units; try again later.
                building.requeueFirst(trained, building.wasLastTrainedByAi());
            } else {
                for (EntityUnit unit : spawned) {
                    unit.setTrainedAt(building.id);
                    unit.setAiControlled(building.wasLastTrainedByAi());
                }
                double[] rally = building.getRally();
                if (rally != null) {
                    Units.get()
                        .orderMove(spawned, rally[0], rally[1]);
                }
                if (trained == UnitKind.LESSER_DEMON) {
                    // Summoned in a blast of fire and smoke.
                    WorldServer world = GameWorld.getWorld();
                    world.func_147487_a("lava", door[0], building.groundY + 1, door[1], 30, 1, 1, 1, 0.1);
                    world.func_147487_a("largesmoke", door[0], building.groundY + 1, door[1], 40, 1, 1.5, 1, 0.05);
                    world.playSoundEffect(door[0], building.groundY + 1, door[1], "mob.wither.spawn", 1.0F, 1.2F);
                }
                WarOfTheGods.LOG.info(
                    "{} trained {}",
                    building.describe(game),
                    spawned.get(0)
                        .getLabel());
            }
        }
    }

    // ---------------------------------------------------------------- worship

    /** Blocks from a chaos temple a chaos priest prays at, and most priests one temple has room for. */
    public static final double TEMPLE_RANGE = 8;
    public static final int PRIESTS_PER_TEMPLE = 5;
    /** How near a stone circle's stones dark wizards stand, or inside it, to summon, and how many it takes. */
    public static final double CIRCLE_REACH = 3;
    public static final int SUMMONERS = 4;

    /** Favor each team has earned towards its next whole one, by team id. */
    private final Map<String, Double> favorDue = new HashMap<>();

    /**
     * The gods grant favor: Zamorak for each chaos priest standing praying by one of its team's chaos temples, up to
     * five a temple, and Saradomin for each monk alive.
     */
    private void worship(Game game, WorldServer world) {
        Map<Integer, Integer> praying = new HashMap<>();
        for (EntityUnit unit : Units.get()
            .getLoaded()) {
            double seconds;
            if (unit.getKind() == UnitKind.MONK) {
                seconds = Config.monkFavorSeconds;
            } else if (unit.getKind()
                .praysAtTemple()) {
                    Building temple = templeOf(game, unit);
                    if (temple == null || praying.merge(temple.id, 1, Integer::sum) > PRIESTS_PER_TEMPLE) {
                        continue;
                    }
                    unit.pray(temple);
                    seconds = unit.getKind() == UnitKind.GOBLIN_PRIEST ? Config.goblinPriestFavorSeconds
                        : Config.priestFavorSeconds;
                } else {
                    continue;
                }
            favorDue.merge(unit.getTeamId(), INTERVAL / (seconds * 20), Double::sum);
        }
        for (Map.Entry<String, Double> due : favorDue.entrySet()) {
            int whole = (int) Math.floor(due.getValue());
            if (whole > 0) {
                Team team = game.getTeam(due.getKey());
                if (team != null) {
                    team.addResource(Resource.FAVOR, whole);
                }
                due.setValue(due.getValue() - whole);
            }
        }
    }

    /** The finished temple of its team and god a priest stands idle by, chaos or Bandos, or null. */
    private static Building templeOf(Game game, EntityUnit priest) {
        if (priest.getOrder() != EntityUnit.Order.IDLE) {
            return null;
        }
        BuildingType temple = priest.getKind() == UnitKind.GOBLIN_PRIEST ? BuildingType.BANDOS_TEMPLE
            : BuildingType.CHAOS_TEMPLE;
        for (Building building : game.getBuildings()) {
            if (building.type == temple && building.isComplete()
                && building.team.equals(priest.getTeamId())
                && building.distanceTo(priest.posX, priest.posZ) <= TEMPLE_RANGE) {
                return building;
            }
        }
        return null;
    }

    /** Dark wizards of the stone circle's team standing at it, to summon a lesser demon. */
    public static int countSummoners(Game game, Building circle) {
        int count = 0;
        for (EntityUnit unit : Units.get()
            .getLoaded()) {
            if (unit.getKind() == UnitKind.DARK_WIZARD && unit.getTeamId()
                .equals(circle.team) && circle.distanceTo(unit.posX, unit.posZ) <= CIRCLE_REACH) {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- gathering

    /** Workers working at a farm, up to how many it has work for. */
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
        // Iron tools yield half again as much, diamond ones twice as much.
        int halves = 2;
        if (team != null && workplace.type == BuildingType.FARM) {
            halves = team.has(Upgrade.DIAMOND_HOES) ? 4 : team.has(Upgrade.PLOUGHS) ? 3 : 2;
        } else if (team != null && workplace.type == BuildingType.QUARRY) {
            halves = team.has(Upgrade.DIAMOND_PICKAXES) ? 4 : team.has(Upgrade.PICKAXES) ? 3 : 2;
        }
        for (int i = 0; i < yield.length; i++) {
            yield[i] = yield[i] * halves / 2;
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
        Team team = game.getTeam(building.team);
        if (team == null || game.getAge(team) >= building.type.levels) {
            return "Your town is in its last age";
        }
        if (building.getResearch() != null) {
            return "It is busy";
        }
        for (Building other : game.getBuildings(team)) {
            if (Building.LEVEL_UP.equals(other.getResearch())) {
                return "Another town center is already advancing";
            }
        }
        return null;
    }

    /**
     * Takes the team of a town center into the next age: its town centers go up a level, and every other building of
     * the team takes on the new age's look with them.
     */
    public static void advanceAge(Game game, Building center) {
        Team team = game.getTeam(center.team);
        if (team != null) {
            team.setAge(game.getAge(team) + 1);
        }
        keepUpWithAge(game);
    }

    /**
     * Every finished building looks the age its team is in, as far as it has a look for it. Levels change only how
     * buildings look, so this takes nothing from them and gives them nothing. The age belongs to the team, so it stays
     * when the team loses its town centers.
     */
    private static void keepUpWithAge(Game game) {
        for (Building building : game.getBuildings()) {
            if (!building.isComplete()) {
                continue;
            }
            Team team = game.getTeam(building.team);
            int level = building.type.isWall() ? wallLevel(team)
                : Math.min(building.type.levels, team == null ? 1 : game.getAge(team));
            if (building.getLevel() != level) {
                if (Building.LEVEL_UP.equals(building.getResearch())) {
                    // Going up by itself from before ages did it for every building.
                    building.cancelResearch();
                }
                building.setLevel(level);
            }
        }
    }

    /** The look of a team's walls: 1 wood, 2 stone with stone walls, 3 stone brick with citadel walls. */
    public static int wallLevel(Team team) {
        if (team == null) {
            return 1;
        }
        return team.has(Upgrade.CITADEL_WALLS) ? 3 : team.has(Upgrade.STONE_WALLS) ? 2 : 1;
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
        if (team != null && upgrade.faction() != null && upgrade.faction() != team.getFaction()) {
            return "Only " + upgrade.faction().displayName + "'s teams research that";
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
        Upgrade first = upgrade.requires();
        if (first != null && !team.has(first)) {
            return "Research " + first.displayName.toLowerCase() + " first";
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

    // ---------------------------------------------------------------- walls

    /** Why a wall planned where the team has all of it already cannot go there. */
    public static final String WALL_THERE = "There is wall there already";

    /** A wall laid out from one point to another: a plan for each of its pieces, or why it cannot go there. */
    public static final class WallPlan {

        public final List<Plan> pieces;
        public final String problem;

        WallPlan(List<Plan> pieces, String problem) {
            this.pieces = pieces;
            this.problem = problem;
        }
    }

    /**
     * Lays a wall out from x1, z1 to x2, z2 (see {@link WallLine}). An end on one of the team's towers starts from
     * the tower's middle, and the pieces on the tower are left out, so the wall runs up to it; pieces where the team
     * already has wall are left out too.
     */
    public WallPlan planWall(Game game, WorldServer world, Team team, int x1, int z1, int x2, int z2) {
        return planWall(game, world, team, x1, z1, x2, z2, 0, 0, false);
    }

    /**
     * Lays a wall out as above, bent out by outX, outZ round what is in the way (see {@link WallLine#bent}). Closed,
     * a short segment that cannot go where it would close a gap fails the plan rather than being left out, so the
     * wall has no hole.
     */
    public WallPlan planWall(Game game, WorldServer world, Team team, int x1, int z1, int x2, int z2, int outX,
        int outZ, boolean closed) {
        Building from = towerAt(game, team, x1, z1);
        if (from != null) {
            x1 = (int) Math.floor(from.getCenterX());
            z1 = (int) Math.floor(from.getCenterZ());
        }
        Building to = towerAt(game, team, x2, z2);
        if (to != null) {
            x2 = (int) Math.floor(to.getCenterX());
            z2 = (int) Math.floor(to.getCenterZ());
        }
        List<WallLine.Piece> pieces = WallLine.bent(x1, z1, x2, z2, outX, outZ, (minX, minZ) -> {
            int[] box = null;
            for (Building building : game.getBuildings(team)) {
                if ((building.type == BuildingType.TOWER || building.type.isWall())
                    && building.overlaps(minX, minZ, WallLine.SIZE, WallLine.SIZE, 0)) {
                    box = union(box, building.minX, building.minZ, building.sizeX, building.sizeZ);
                }
            }
            return box;
        });
        List<Plan> plans = new ArrayList<>();
        for (WallLine.Piece piece : pieces) {
            String problem = wallPieceProblem(game, world, piece);
            if (problem != null) {
                if (piece.isShort() && !closed) {
                    // A short segment only closes a gap; the wall stands without it.
                    continue;
                }
                return new WallPlan(plans, problem);
            }
            Site site = survey(world, piece.minX, piece.minZ, piece.sizeX, piece.sizeZ);
            plans.add(
                new Plan(
                    piece.type(),
                    piece.minX,
                    piece.minZ,
                    piece.sizeX,
                    piece.sizeZ,
                    piece.rotation,
                    site.groundY,
                    null));
        }
        if (plans.isEmpty()) {
            return new WallPlan(plans, WALL_THERE);
        }
        return new WallPlan(plans, null);
    }

    /** The box holding box, if any, and the one given; each as min x, min z, size x, size z. */
    private static int[] union(int[] box, int minX, int minZ, int sizeX, int sizeZ) {
        if (box == null) {
            return new int[] { minX, minZ, sizeX, sizeZ };
        }
        int x = Math.min(box[0], minX);
        int z = Math.min(box[1], minZ);
        return new int[] { x, z, Math.max(box[0] + box[2], minX + sizeX) - x,
            Math.max(box[1] + box[3], minZ + sizeZ) - z };
    }

    /** Why a piece of wall cannot go where it is, or null. Walls follow the ground, however steep. */
    private static String wallPieceProblem(Game game, WorldServer world, WallLine.Piece piece) {
        int sizeX = piece.sizeX;
        int sizeZ = piece.sizeZ;
        if (piece.minX < game.getMinX() || piece.minZ < game.getMinZ()
            || piece.minX + sizeX - 1 > game.getMaxX()
            || piece.minZ + sizeZ - 1 > game.getMaxZ()) {
            return "The wall would run off the map";
        }
        for (Building other : game.getBuildings()) {
            if (other.overlaps(piece.minX, piece.minZ, sizeX, sizeZ, 0)) {
                return "The wall would run into a " + other.type.displayName.toLowerCase();
            }
        }
        for (Settlement settlement : game.getSettlements()) {
            if (settlement.overlaps(piece.minX, piece.minZ, sizeX, sizeZ, 1)) {
                return "The wall would run over a settlement";
            }
        }
        for (GoldMine mine : game.getMines()) {
            if (mine.overlaps(piece.minX, piece.minZ, sizeX, sizeZ, 1)) {
                return "The wall would run into a gold mine";
            }
        }
        if (MapFeatures.bridgeNear(game, piece.minX, piece.minZ, sizeX, sizeZ, 1)) {
            return "The wall would block a bridge";
        }
        if (survey(world, piece.minX, piece.minZ, sizeX, sizeZ).water > 2) {
            return "The wall would run through water";
        }
        return null;
    }

    /** The team's tower with its footprint at x, z, or within a block of it; null if none. */
    private static Building towerAt(Game game, Team team, int x, int z) {
        for (Building building : game.getBuildings(team)) {
            if (building.type == BuildingType.TOWER && building.covers(x + 0.5, z + 0.5, 1)) {
                return building;
            }
        }
        return null;
    }

    /** What a wall of so many pieces costs: each piece, post or segment, what a wall does. */
    public static Map<Resource, Long> wallCost(int pieces) {
        Map<Resource, Long> cost = new LinkedHashMap<>();
        for (Map.Entry<Resource, Long> entry : BuildingType.WALL.cost()
            .entrySet()) {
            cost.put(entry.getKey(), entry.getValue() * pieces);
        }
        return cost;
    }

    /** Lays out the building sites of a planned wall, in order along it; returns them. */
    public List<Building> startWall(Game game, Team team, WallPlan plan) {
        List<Building> sites = new ArrayList<>();
        for (Plan piece : plan.pieces) {
            sites.add(startSite(game, team, piece));
        }
        return sites;
    }

    // ---------------------------------------------------------------- bridges

    /** A bridge laid out from one point to another: a plan for each of its pieces, or why it cannot go there. */
    public static final class BridgePlan {

        public final List<Plan> pieces;
        public final String problem;

        BridgePlan(List<Plan> pieces, String problem) {
            this.pieces = pieces;
            this.problem = problem;
        }
    }

    /**
     * Lays a bridge out from x1, z1 to x2, z2 (see {@link BridgeLine}). Its deck lies a block over the water, level
     * with the banks, and in the ground on land. Pieces where the team already has bridge are left out, so a bridge can
     * be carried on from where one ends. It may cross water or not, so it can as well run as a boardwalk over a marsh
     * or a gully.
     */
    public BridgePlan planBridge(Game game, WorldServer world, Team team, int x1, int z1, int x2, int z2) {
        List<Plan> plans = new ArrayList<>();
        for (BridgeLine.Piece piece : BridgeLine.pieces(x1, z1, x2, z2)) {
            boolean built = false;
            for (Building building : game.getBuildings(team)) {
                built |= building.type == BuildingType.BRIDGE
                    && building.overlaps(piece.minX, piece.minZ, piece.sizeX, piece.sizeZ, 0);
            }
            if (built) {
                continue;
            }
            String problem = bridgePieceProblem(game, piece);
            if (problem != null) {
                return new BridgePlan(plans, problem);
            }
            // The middle of the piece decides: a block over water, or in the ground's top block.
            int x = (int) Math.floor(piece.centerX());
            int z = (int) Math.floor(piece.centerZ());
            int top = surfaceY(world, x, z);
            boolean wet = world.getBlock(x, top, z)
                .getMaterial()
                .isLiquid();
            int groundY = wet ? top + 1 : top;
            if (groundY < 2 || groundY > 250) {
                return new BridgePlan(plans, "The bridge would be too high or too low");
            }
            plans.add(
                new Plan(
                    BuildingType.BRIDGE,
                    piece.minX,
                    piece.minZ,
                    piece.sizeX,
                    piece.sizeZ,
                    piece.rotation,
                    groundY,
                    null));
        }
        if (plans.isEmpty()) {
            return new BridgePlan(plans, "There is bridge there already");
        }
        return new BridgePlan(plans, null);
    }

    /**
     * The y of the topmost block at x, z that is ground or water, past air, plants and leaves. Unlike
     * {@code getTopSolidOrLiquidBlock}, which in 1.7.10 looks through water to the bed under it, this stops at the
     * water's surface.
     */
    private static int surfaceY(WorldServer world, int x, int z) {
        int y = Math.min(255, world.getHeightValue(x, z));
        for (; y > 0; y--) {
            Material material = world.getBlock(x, y, z)
                .getMaterial();
            if (material != Material.air && material != Material.plants
                && material != Material.vine
                && material != Material.leaves
                && material != Material.snow) {
                break;
            }
        }
        return y;
    }

    /** Why a piece of bridge cannot go where it is, or null. */
    private static String bridgePieceProblem(Game game, BridgeLine.Piece piece) {
        if (piece.minX < game.getMinX() || piece.minZ < game.getMinZ()
            || piece.minX + piece.sizeX - 1 > game.getMaxX()
            || piece.minZ + piece.sizeZ - 1 > game.getMaxZ()) {
            return "The bridge would run off the map";
        }
        for (Building other : game.getBuildings()) {
            if (other.overlaps(piece.minX, piece.minZ, piece.sizeX, piece.sizeZ, 0)) {
                return "The bridge would run into a " + other.type.displayName.toLowerCase();
            }
        }
        for (Settlement settlement : game.getSettlements()) {
            if (settlement.overlaps(piece.minX, piece.minZ, piece.sizeX, piece.sizeZ, 0)) {
                return "The bridge would run over a settlement";
            }
        }
        for (GoldMine mine : game.getMines()) {
            if (mine.overlaps(piece.minX, piece.minZ, piece.sizeX, piece.sizeZ, 0)) {
                return "The bridge would run into a gold mine";
            }
        }
        if (MapFeatures.bridgeNear(game, piece.minX, piece.minZ, piece.sizeX, piece.sizeZ, 0)) {
            return "There is a bridge there already";
        }
        return null;
    }

    /** What a bridge so many blocks long costs: a piece's cost for each block. */
    public static Map<Resource, Long> bridgeCost(int pieces) {
        Map<Resource, Long> cost = new LinkedHashMap<>();
        for (Map.Entry<Resource, Long> entry : BuildingType.BRIDGE.cost()
            .entrySet()) {
            cost.put(entry.getKey(), entry.getValue() * pieces);
        }
        return cost;
    }

    /** Lays out the building sites of a planned bridge, in order along it; returns them. */
    public List<Building> startBridge(Game game, Team team, BridgePlan plan) {
        List<Building> sites = new ArrayList<>();
        for (Plan piece : plan.pieces) {
            sites.add(startSite(game, team, piece));
        }
        return sites;
    }

    /** Why a building cannot be made a gate, or null. */
    public static String gateProblem(Building building) {
        if (building.type != BuildingType.WALL) {
            return "Only a wall segment can be made a gate";
        }
        if (building.sizeX < WallLine.SIZE || building.sizeZ < WallLine.SIZE) {
            return "A short wall segment is too small for a gate";
        }
        if (!building.isComplete()) {
            return "It is not built yet";
        }
        return null;
    }

    /**
     * Makes a finished wall segment a gate at once, as damaged as the wall was; returns the gate, which has a new
     * id.
     */
    public Building makeGate(Game game, WorldServer world, Building wall) {
        Team team = game.getTeam(wall.team);
        double health = wall.getHealth() / (double) wall.getMaxHealth();
        game.removeBuilding(wall);
        Building gate = game.addBuilding(
            BuildingType.GATE,
            team,
            wall.minX,
            wall.minZ,
            wall.sizeX,
            wall.sizeZ,
            wall.groundY,
            wall.rotation,
            true);
        gate.setLevel(wallLevel(team));
        gate.damage(gate.getMaxHealth() - Math.max(1, (int) Math.round(gate.getMaxHealth() * health)));
        show(world, game, gate);
        WarOfTheGods.LOG.info("Made {} of wall #{}", gate.describe(game), wall.id);
        return gate;
    }

    /** Gates stand open while a unit or player of their team or an ally is near, and shut otherwise. */
    private void gates(Game game, WorldServer world) {
        for (Building gate : game.getBuildings()) {
            if (gate.type != BuildingType.GATE || !gate.isComplete()) {
                continue;
            }
            boolean open = friendNear(game, world, gate, GATE_REACH);
            for (int x = gate.minX; x <= gate.getMaxX(); x++) {
                for (int z = gate.minZ; z <= gate.getMaxZ(); z++) {
                    for (int y = gate.groundY + 1; y <= gate.groundY + 3; y++) {
                        if (world.getBlock(x, y, z) != Blocks.fence_gate) {
                            continue;
                        }
                        int meta = world.getBlockMetadata(x, y, z);
                        if (((meta & 4) != 0) != open) {
                            world.setBlockMetadataWithNotify(x, y, z, open ? meta | 4 : meta & ~4, 2);
                            world.playAuxSFXAtEntity(null, 1003, x, y, z, 0);
                        }
                    }
                }
            }
        }
    }

    /**
     * Animals that wander onto a town center, a building site or any other building are put back outside it, at the
     * nearest edge, so herds graze round towns rather than in them.
     */
    private static void keepAnimalsOut(Game game, WorldServer world) {
        for (Object object : world.loadedEntityList) {
            if (!(object instanceof EntityAnimal)) {
                continue;
            }
            EntityAnimal animal = (EntityAnimal) object;
            Building building = game.getBuildingAt(animal.posX, animal.posZ);
            if (building == null || building.type == BuildingType.BRIDGE) {
                continue;
            }
            // Out over the nearest side, a block clear of it.
            double toWest = animal.posX - building.minX;
            double toEast = building.minX + building.sizeX - animal.posX;
            double toNorth = animal.posZ - building.minZ;
            double toSouth = building.minZ + building.sizeZ - animal.posZ;
            double nearest = Math.min(Math.min(toWest, toEast), Math.min(toNorth, toSouth));
            double x = animal.posX;
            double z = animal.posZ;
            if (nearest == toWest) {
                x = building.minX - 1.5;
            } else if (nearest == toEast) {
                x = building.minX + building.sizeX + 1.5;
            } else if (nearest == toNorth) {
                z = building.minZ - 1.5;
            } else {
                z = building.minZ + building.sizeZ + 1.5;
            }
            int y = world.getTopSolidOrLiquidBlock((int) Math.floor(x), (int) Math.floor(z));
            animal.getNavigator()
                .clearPathEntity();
            animal.setPositionAndUpdate(x, y, z);
        }
    }

    /** Whether a unit or player of the building's team or an ally is within reach of its middle. */
    private static boolean friendNear(Game game, WorldServer world, Building building, double reach) {
        Team owner = game.getTeam(building.team);
        if (owner == null) {
            return false;
        }
        double cx = building.getCenterX();
        double cz = building.getCenterZ();
        for (EntityUnit unit : Units.get()
            .getLoaded()) {
            Team team = game.getTeam(unit.getTeamId());
            if (team != null && !game.areEnemies(owner, team)
                && (unit.posX - cx) * (unit.posX - cx) + (unit.posZ - cz) * (unit.posZ - cz) <= reach * reach) {
                return true;
            }
        }
        for (Object object : world.playerEntities) {
            EntityPlayer player = (EntityPlayer) object;
            Team team = game.getTeamOf(player.getUniqueID());
            if (team != null && !game.areEnemies(owner, team)
                && (player.posX - cx) * (player.posX - cx) + (player.posZ - cz) * (player.posZ - cz) <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    /** Only a gate's own team and its allies open it by hand. */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.world.isRemote
            || event.world.provider.dimensionId != GameWorld.dimensionId()
            || event.world.getBlock(event.x, event.y, event.z) != Blocks.fence_gate) {
            return;
        }
        Game game = GameManager.getGame();
        Building gate = game == null ? null : game.getBuildingAt(event.x + 0.5, event.z + 0.5);
        if (gate == null || gate.type != BuildingType.GATE) {
            return;
        }
        Team owner = game.getTeam(gate.team);
        Team team = game.getTeamOf(event.entityPlayer.getUniqueID());
        if (owner != null && (team == null || game.areEnemies(owner, team))) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- towers

    /**
     * Watch towers and town centers shoot arrows at the nearest enemy in range; knights' fortresses
     * shoot a volley at the nearest few.
     */
    private void shoot(Game game, WorldServer world) {
        for (Building building : game.getBuildings()) {
            Team team = game.getTeam(building.team);
            int range = building.isComplete() ? rangeOf(team, building) : 0;
            if (range <= 0) {
                shotTicks.remove(building.id);
                continue;
            }
            int every = Config.towerShotTicks;
            int elapsed = shotTicks.getOrDefault(building.id, 0) + INTERVAL;
            if (elapsed < every) {
                shotTicks.put(building.id, elapsed);
                continue;
            }
            double y = building.groundY + shootHeight(building);
            List<EntityLivingBase> targets = Units.get()
                .findEnemiesNear(
                    world,
                    team,
                    building.getCenterX(),
                    y,
                    building.getCenterZ(),
                    range,
                    building.type.arrowsPerVolley());
            if (targets.isEmpty()) {
                shotTicks.put(building.id, every);
                continue;
            }
            shotTicks.put(building.id, 0);
            // A fortress shoots its whole volley, sharing its arrows out over the enemies it sees.
            for (int i = 0; i < building.type.arrowsPerVolley(); i++) {
                EntityLivingBase target = targets.get(i % targets.size());
                double[] from = shotFrom(building, target.posX, target.posZ);
                EntityTeamArrow arrow = new EntityTeamArrow(world, from[0], y, from[1], team.getId());
                arrow.aim(target, Config.towerDamage + (team.has(Upgrade.FLETCHING) ? 1 : 0));
                world.spawnEntityInWorld(arrow);
            }
        }
    }

    /**
     * Where a building's arrow at x, z sets off, as x, z: just outside the footprint, where the line from its center
     * towards the target leaves it, so the arrow clears the building's own walls whichever side or corner the target
     * is at. On the way from there the arrow stays clear of the footprint, so even an enemy right up against the walls
     * is hit.
     */
    static double[] shotFrom(Building building, double x, double z) {
        double dx = x - building.getCenterX();
        double dz = z - building.getCenterZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.01) {
            dx = 1;
            dz = 0;
            length = 1;
        }
        double ux = dx / length;
        double uz = dz / length;
        double outX = Math.abs(ux) < 1.0E-6 ? Double.MAX_VALUE : (building.sizeX / 2.0 + 0.6) / Math.abs(ux);
        double outZ = Math.abs(uz) < 1.0E-6 ? Double.MAX_VALUE : (building.sizeZ / 2.0 + 0.6) / Math.abs(uz);
        double out = Math.min(outX, outZ);
        return new double[] { building.getCenterX() + ux * out, building.getCenterZ() + uz * out };
    }

    /**
     * How far a building shoots, with its team's upgrades; 0 for one that does not shoot, such as a tower before its
     * team has watch towers.
     */
    public static int rangeOf(Team team, Building building) {
        int range = building.type.range(building.getLevel());
        if (range <= 0 || team == null) {
            return 0;
        }
        if (building.type == BuildingType.TOWER) {
            if (!team.has(Upgrade.WATCH_TOWER)) {
                return 0;
            }
            if (team.has(Upgrade.GUARD_TOWER)) {
                range += Config.guardTowerRange;
            }
        }
        return team.has(Upgrade.FLETCHING) ? range + 4 : range;
    }

    /**
     * Four towers round a team's first town center, one off each corner, so between them they watch over the ground
     * all round it. They only shoot once the team has watch towers.
     */
    public void raiseStartingTowers(Game game, WorldServer world, Team team) {
        Building center = game.getTownHall(team);
        if (center == null) {
            return;
        }
        int raised = 0;
        for (int corner = 0; corner < 4; corner++) {
            int signX = corner % 2 == 0 ? -1 : 1;
            int signZ = corner < 2 ? -1 : 1;
            Plan plan = null;
            // Off the corner, a little further out if the ground there will not do.
            for (int step = 0; step < 6 && (plan == null || plan.problem != null); step++) {
                double out = 14 + step * 2;
                int x = (int) Math.round(center.getCenterX() + signX * (center.sizeX / 2.0 + out));
                int z = (int) Math.round(center.getCenterZ() + signZ * (center.sizeZ / 2.0 + out));
                plan = plan(game, world, BuildingType.TOWER, x, z, center.rotation);
            }
            if (plan.problem != null) {
                continue;
            }
            Building tower = game.addBuilding(
                BuildingType.TOWER,
                team,
                plan.minX,
                plan.minZ,
                plan.sizeX,
                plan.sizeZ,
                plan.groundY,
                plan.rotation,
                false);
            tower.setLevel(Math.min(BuildingType.TOWER.levels, game.getAge(team)));
            raise(world, tower);
            raised++;
        }
        WarOfTheGods.LOG.info("Raised {} towers round the town center of {}", raised, team.getName());
    }

    /** Blocks above the ground a building shoots from: a tower's deck, the town center's gate posts. */
    private static int shootHeight(Building building) {
        if (building.type == BuildingType.TOWER) {
            return building.getLevel() >= 2 ? 14 : 11;
        }
        if (building.type.isFortress()) {
            return FORTRESS_SHOOT_HEIGHT;
        }
        return 4;
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
        damage(building, amount, cause, null);
    }

    /**
     * Takes health off a building, as above, hit by the entity when it is known; its team hears of it unless the
     * blow takes it down at once, as when its team is defeated.
     */
    public void damage(Building building, int amount, String cause, Entity attacker) {
        Game game = GameManager.getGame();
        if (game == null || game.getBuilding(building.id) != building) {
            return;
        }
        if (amount > 0 && amount < building.getHealth()) {
            WorldServer world = DimensionManager.getWorld(GameWorld.dimensionId());
            AttackAlerts.get()
                .buildingAttacked(game, building, attacker, world == null ? 0 : world.getTotalWorldTime());
        }
        if (building.damage(amount)) {
            destroy(game, building, cause);
        }
    }

    /** Takes a building or building site down at once, whatever its health, as when its own team destroys it. */
    public void destroy(Game game, Building building, String cause) {
        if (game.getBuilding(building.id) != building) {
            return;
        }
        WorldServer world = DimensionManager.getWorld(GameWorld.dimensionId());
        if (world != null) {
            if (building.isComplete()) {
                collapse(world, building);
            }
            tearDown(world, game, building);
        }
        game.removeBuilding(building);
        harvestTicks.remove(building.id);
        shotTicks.remove(building.id);
        WarOfTheGods.LOG.info("Destroyed {} ({})", building.describe(game), cause);
    }

    /**
     * A building coming down goes with a roar: explosions sound across it and dust and flame burst out of it. Only for
     * show; nothing round it is hurt or broken.
     */
    private static void collapse(WorldServer world, Building building) {
        Schematic schematic = Buildings.get()
            .schematic(building.type);
        double height = schematic.height - schematic.ground;
        double x = building.minX + building.sizeX / 2.0;
        double z = building.minZ + building.sizeZ / 2.0;
        double y = building.groundY + 1 + height / 3;
        int blasts = Math.max(2, Math.min(6, (building.sizeX * building.sizeZ) / 40));
        for (int i = 0; i < blasts; i++) {
            double bx = building.minX + world.rand.nextDouble() * building.sizeX;
            double bz = building.minZ + world.rand.nextDouble() * building.sizeZ;
            double by = building.groundY + 1 + world.rand.nextDouble() * height / 2;
            world.playSoundEffect(bx, by, bz, "random.explode", 4.0F, 0.6F + world.rand.nextFloat() * 0.3F);
            world.func_147487_a("largeexplode", bx, by, bz, 2, 1.0, 1.0, 1.0, 0);
        }
        world.func_147487_a("hugeexplosion", x, y, z, 1, 0, 0, 0, 0);
        world.func_147487_a("largesmoke", x, y, z, 60, building.sizeX / 3.0, height / 3, building.sizeZ / 3.0, 0.05);
        world.func_147487_a("flame", x, y, z, 30, building.sizeX / 3.0, height / 4, building.sizeZ / 3.0, 0.05);
    }

    /**
     * A destroyed building leaves its ruin; a destroyed building site leaves bare ground. A town center or its site
     * leaves its settlement, free for anyone to build on again.
     */
    private void tearDown(WorldServer world, Game game, Building building) {
        if (building.type == BuildingType.BRIDGE) {
            Bridges.tearDown(world, building);
            return;
        }
        Settlement settlement = building.type == BuildingType.TOWN_HALL ? game.settlementOf(building) : null;
        if (settlement != null) {
            Schematic stones = features.schematic("settlement");
            if (stones.sizeX(settlement.rotation) == building.sizeX
                && stones.sizeZ(settlement.rotation) == building.sizeZ) {
                // Clear the town center away first: the settlement leaves air only where it has blocks of its own.
                Schematic schematic = schematic(building.type);
                int top = building.groundY - schematic.ground + schematic.height;
                for (int x = building.minX; x <= building.getMaxX(); x++) {
                    for (int z = building.minZ; z <= building.getMaxZ(); z++) {
                        for (int y = Math.min(255, top); y > building.groundY; y--) {
                            if (!world.isAirBlock(x, y, z)) {
                                world.setBlock(x, y, z, Blocks.air, 0, 2);
                            }
                        }
                    }
                }
                paste(world, building, stones);
                return;
            }
        }
        Schematic ruin = fitted(variant(factionOf(game, building), building.type.id + "_ruin"), building);
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
            String feature = game == null ? null : MapFeatures.featureWithBlock(game, event.x, event.y, event.z);
            if (feature != null) {
                // Gold comes out of mines by workers mining them, settlements stay for town centers and bridges
                // stay up.
                event.setCanceled(true);
                event.getPlayer()
                    .addChatMessage(
                        new ChatComponentText(
                            EnumChatFormatting.GRAY + "That is part of " + feature + " and cannot be broken."));
            }
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
            || block == Blocks.carpet
            || block instanceof BlockBanner;
    }

    /**
     * Explosions near buildings, such as creepers going off, take health off them instead of blowing holes in them:
     * nothing within {@link Config#explosionProtection} blocks of a building or building site breaks. A trebuchet's
     * stone hurts only enemies, units and buildings alike, and does that itself; its blast just breaks blocks.
     */
    @SubscribeEvent
    public void onDetonate(ExplosionEvent.Detonate event) {
        if (event.world.isRemote || !GameWorld.isGameWorld(event.world)) {
            return;
        }
        boolean stone = event.explosion.exploder instanceof EntityTrebuchetStone;
        if (harmless || stone) {
            event.getAffectedEntities()
                .clear();
        }
        Game game = GameManager.getGame();
        if (game == null) {
            return;
        }
        Explosion explosion = event.explosion;
        int pad = Config.explosionProtection;
        Iterator<ChunkPosition> features = event.getAffectedBlocks()
            .iterator();
        while (features.hasNext()) {
            ChunkPosition block = features.next();
            if (MapFeatures.featureWithBlock(game, block.chunkPosX, block.chunkPosY, block.chunkPosZ) != null) {
                features.remove();
            }
        }
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
            if (distance < reach && !stone) {
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
