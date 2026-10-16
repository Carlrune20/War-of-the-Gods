package com.warofthegods.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.building.Buildings;
import com.warofthegods.game.Building;
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.GoldMine;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Team;
import com.warofthegods.game.UnitKind;
import com.warofthegods.unit.EntityUnit;
import com.warofthegods.unit.Hunting;
import com.warofthegods.unit.Trees;
import com.warofthegods.unit.Units;
import com.warofthegods.world.GameWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * A simple computer player. Every two seconds it looks over each team an op handed to it with /rts ai or the new
 * game lobby and gives the orders a player could. On a team with people on it, it plays alongside them as one more
 * player, commanding only the units it trained itself and sharing the team's resources. It trains workers and spreads them over food, wood and gold,
 * puts up houses, farms and barracks near its town center, takes its town into the next age, trains warriors and archers, sends them
 * on an attack move at the nearest enemy every few minutes and pulls them back when its town center is attacked. It
 * pays for everything like a player and gets no bonuses.
 */
public final class AiPlayers {

    /** Ticks between looking over the computer's teams. */
    private static final int THINK_TICKS = 40;
    /** Workers it keeps in each age, by age. */
    private static final int[] WORKERS_WANTED = { 0, 12, 20, 25 };
    /** Share of the workers on food, wood and gold in Age I, and from Age II on, by resource ordinal. */
    private static final double[] SHARE_AGE_1 = { 0.45, 0.4, 0.15 };
    private static final double[] SHARE_LATER = { 0.4, 0.35, 0.25 };
    /** Blocks from the town center it hunts and chops in. */
    private static final int HUNT_RANGE = 48;
    private static final int TREE_RANGE = 64;
    /** Fighters this close to the town center are at home; farther off they are out attacking. */
    private static final double HOME_RANGE = 40;
    /** Enemies this close to the town center are attacking it. */
    private static final double DANGER_RANGE = 32;
    /** Fighters away from home attack an enemy building this close, rather than walk up to it. */
    private static final double SIEGE_RANGE = 24;
    /** Fighters the first attack takes; each attack after takes this many more. */
    private static final int FIRST_WAVE = 6;
    private static final int WAVE_GROWTH = 2;
    /** Fewest ticks between attacks. */
    private static final int WAVE_TICKS = 20 * 150;
    /** Most fighters it trains. */
    private static final int MAX_FIGHTERS = 40;
    /** Most units it keeps waiting in a building's training queue. */
    private static final int QUEUE = 2;

    /** What the computer remembers about a team between looks; lost on a restart, which does no harm. */
    private static final class State {

        int lastWave = -WAVE_TICKS;
        int waves;
        boolean archerNext;
    }

    private final Map<String, State> states = new HashMap<>();
    private int gameNumber;
    private int ticks;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % THINK_TICKS != 0) {
            return;
        }
        Game game = DimensionManager.getWorld(0) == null ? null : GameManager.getGame();
        WorldServer world = DimensionManager.getWorld(GameWorld.dimensionId());
        if (game == null || world == null || game.isOver()) {
            states.clear();
            return;
        }
        if (game.getNumber() != gameNumber) {
            states.clear();
            gameNumber = game.getNumber();
        }
        for (Team team : game.getTeams()) {
            if (team.isAi() && !team.isDefeated()) {
                think(game, world, team, states.computeIfAbsent(team.getId(), id -> new State()));
            }
        }
    }

    private void think(Game game, WorldServer world, Team team, State state) {
        Building hall = finishedTownCenter(game, team);
        if (hall == null) {
            return;
        }
        List<EntityUnit> workers = new ArrayList<>();
        List<EntityUnit> fighters = new ArrayList<>();
        // Playing alongside people, it commands only the units it trained; on its own, the whole team's.
        boolean alongsidePlayers = !team.getMembers()
            .isEmpty();
        for (EntityUnit unit : Units.get()
            .getUnits(team)) {
            if (!alongsidePlayers || unit.isAiControlled()) {
                (unit.isWorker() ? workers : fighters).add(unit);
            }
        }
        trainWorkers(game, team, hall, workers.size());
        boolean saving = advanceAge(game, team, hall, workers.size(), fighters.size());
        build(game, world, team, workers, saving);
        staffSites(game, team, workers);
        gather(game, world, team, hall, workers);
        if (!saving) {
            trainFighters(game, team, state, fighters.size());
        }
        fight(game, world, team, hall, fighters, state);
    }

    // ---------------------------------------------------------------- economy

    private static void trainWorkers(Game game, Team team, Building hall, int workers) {
        int wanted = WORKERS_WANTED[game.getAge(team)];
        if (workers + queued(game, team, UnitKind.WORKER) < wanted && hall.getQueue()
            .size() < QUEUE && Buildings.room(game, team) > 0 && team.spend(UnitKind.WORKER.cost())) {
            hall.enqueue(UnitKind.WORKER, true);
        }
    }

    /**
     * Takes the town into the next age once it has the workers, and in Age II a barracks and some fighters, for it.
     * Returns true while it is saving up for it, so nothing else spends what it needs.
     */
    private static boolean advanceAge(Game game, Team team, Building hall, int workers, int fighters) {
        int age = game.getAge(team);
        if (age >= 3) {
            return false;
        }
        boolean ready = age == 1 ? workers >= 10
            : workers >= 16 && fighters >= 4 && count(game, team, BuildingType.BARRACKS, true) > 0;
        if (!ready || Buildings.levelUpProblem(game, hall) != null) {
            return false;
        }
        if (!team.spend(hall.type.upgradeCost(hall.getLevel() + 1))) {
            return true;
        }
        hall.startResearch(Building.LEVEL_UP);
        WarOfTheGods.LOG.info("AI {} is advancing to {}", team.getName(), BuildingType.ageName(age + 1));
        return false;
    }

    /** Lays out one building at a time near the town center: a house when it runs short of room, then the rest. */
    private static void build(Game game, WorldServer world, Team team, List<EntityUnit> workers, boolean saving) {
        for (Building building : game.getBuildings(team)) {
            if (!building.isComplete() && !building.type.isWall()) {
                return;
            }
        }
        BuildingType type = null;
        if (Buildings.room(game, team) <= 3 && game.getPopulationCap(team) < Config.unitsPerTeam) {
            type = BuildingType.HOUSE;
        } else if (!saving && game.getAge(team) >= 2) {
            int foodWorkers = (int) Math.round(WORKERS_WANTED[game.getAge(team)] * SHARE_LATER[0]);
            if (count(game, team, BuildingType.BARRACKS, false) == 0) {
                type = BuildingType.BARRACKS;
            } else if (count(game, team, BuildingType.FARM, false) * BuildingType.FARM.maxWorkers() < foodWorkers) {
                type = BuildingType.FARM;
            } else if (game.getAge(team) >= 3 && count(game, team, BuildingType.BARRACKS, false) < 2) {
                type = BuildingType.BARRACKS;
            }
        }
        if (type == null || !team.canAfford(type.cost())) {
            return;
        }
        Buildings.Plan plan = Buildings.get()
            .findSpot(game, world, team, type);
        if (plan == null || !team.spend(type.cost())) {
            return;
        }
        Building site = Buildings.get()
            .startSite(game, team, plan);
        List<EntityUnit> builders = spareWorkers(workers, 2);
        Units.get()
            .orderBuild(builders, site);
        WarOfTheGods.LOG
            .info("AI {} laid out {} with {} workers", team.getName(), site.describe(game), builders.size());
    }

    /** Sends a worker to every building site nobody is building. */
    private static void staffSites(Game game, Team team, List<EntityUnit> workers) {
        for (Building site : game.getBuildings(team)) {
            if (!site.isComplete() && Buildings.countBuilders(site) == 0) {
                Units.get()
                    .orderBuild(spareWorkers(workers, 1), site);
            }
        }
    }

    /** Puts idle workers on whichever of food, wood and gold has the fewest workers for its share. */
    private static void gather(Game game, WorldServer world, Team team, Building hall, List<EntityUnit> workers) {
        int[] on = new int[Resource.values().length];
        List<EntityUnit> idle = new ArrayList<>();
        for (EntityUnit worker : workers) {
            Resource resource = resourceOf(worker);
            if (resource != null) {
                on[resource.ordinal()]++;
            } else if (worker.getOrder() == EntityUnit.Order.IDLE) {
                idle.add(worker);
            }
        }
        double[] share = game.getAge(team) >= 2 ? SHARE_LATER : SHARE_AGE_1;
        int[] sent = new int[Resource.values().length];
        for (EntityUnit worker : idle) {
            Resource best = null;
            for (Resource resource : Resource.values()) {
                if (best == null || on[resource.ordinal()] / share[resource.ordinal()]
                    < on[best.ordinal()] / share[best.ordinal()]) {
                    best = resource;
                }
            }
            // Where there is nothing of it to gather, the others in turn.
            Resource[] tries = { best, Resource.WOOD, Resource.GOLD, Resource.FOOD };
            for (Resource resource : tries) {
                if (sendTo(game, world, team, hall, worker, resource)) {
                    on[resource.ordinal()]++;
                    sent[resource.ordinal()]++;
                    break;
                }
            }
        }
        if (sent[0] + sent[1] + sent[2] > 0) {
            WarOfTheGods.LOG.info(
                "AI {} put idle workers to work: {} on food, {} on wood, {} on gold",
                team.getName(),
                sent[Resource.FOOD.ordinal()],
                sent[Resource.WOOD.ordinal()],
                sent[Resource.GOLD.ordinal()]);
        }
    }

    private static boolean sendTo(Game game, WorldServer world, Team team, Building hall, EntityUnit worker,
        Resource resource) {
        List<EntityUnit> one = new ArrayList<>();
        one.add(worker);
        double x = hall.getCenterX();
        double z = hall.getCenterZ();
        switch (resource) {
            case FOOD: {
                Building farm = farmWithRoom(game, team);
                if (farm != null) {
                    return Units.get()
                        .orderBuild(one, farm) > 0;
                }
                Hunting.Carcass carcass = Hunting.nearest(x, z, HUNT_RANGE);
                if (carcass != null && carcass.getFood() > 0) {
                    return Units.get()
                        .orderCarve(one, carcass) > 0;
                }
                EntityLiving animal = nearestAnimal(game, world, x, z);
                return animal != null && Units.get()
                    .orderHunt(one, animal) > 0;
            }
            case WOOD: {
                int[] tree = Trees.nearest(world, game, x, hall.groundY, z, TREE_RANGE);
                if (tree == null || Units.get()
                    .orderChop(one, tree[0], tree[1], tree[2]) == 0) {
                    return false;
                }
                Trees.track(world, game, tree[0], tree[1], tree[2]);
                return true;
            }
            default: {
                GoldMine mine = Units.nearestMine(game, x, z, Double.MAX_VALUE);
                return mine != null && Units.get()
                    .orderMine(one, mine) > 0;
            }
        }
    }

    /** What the worker is gathering, or null if it is gathering nothing. */
    private static Resource resourceOf(EntityUnit worker) {
        switch (worker.getOrder()) {
            case HUNT:
            case WORK:
                return Resource.FOOD;
            case CHOP:
                return Resource.WOOD;
            case MINE:
                return Resource.GOLD;
            default:
                return null;
        }
    }

    private static Building farmWithRoom(Game game, Team team) {
        for (Building farm : game.getBuildings(team)) {
            if (farm.type != BuildingType.FARM || !farm.isComplete()) {
                continue;
            }
            int working = 0;
            for (EntityUnit unit : Units.get()
                .getUnits(team)) {
                working += unit.isWorkingAt(farm.id) ? 1 : 0;
            }
            if (working < farm.type.maxWorkers()) {
                return farm;
            }
        }
        return null;
    }

    private static EntityLiving nearestAnimal(Game game, WorldServer world, double x, double z) {
        EntityLiving best = null;
        double bestDistance = HUNT_RANGE * HUNT_RANGE;
        for (EntityLiving mob : Units.get()
            .getMobs(game, world)) {
            if (Hunting.foodOf(mob) <= 0) {
                continue;
            }
            double dx = mob.posX - x;
            double dz = mob.posZ - z;
            if (dx * dx + dz * dz < bestDistance) {
                best = mob;
                bestDistance = dx * dx + dz * dz;
            }
        }
        return best;
    }

    /** Up to count workers to take off what they are doing: idle ones first, then woodcutters. */
    private static List<EntityUnit> spareWorkers(List<EntityUnit> workers, int count) {
        List<EntityUnit> spare = new ArrayList<>();
        for (EntityUnit.Order order : new EntityUnit.Order[] { EntityUnit.Order.IDLE, EntityUnit.Order.CHOP }) {
            for (EntityUnit worker : workers) {
                if (spare.size() < count && worker.getOrder() == order) {
                    spare.add(worker);
                }
            }
        }
        return spare;
    }

    // ---------------------------------------------------------------- army

    private static void trainFighters(Game game, Team team, State state, int fighters) {
        if (fighters + queued(game, team, UnitKind.SOLDIER) + queued(game, team, UnitKind.ARCHER) >= MAX_FIGHTERS) {
            return;
        }
        for (Building barracks : game.getBuildings(team)) {
            if (barracks.type != BuildingType.BARRACKS || !barracks.isComplete()
                || barracks.getQueue()
                    .size() >= QUEUE
                || Buildings.room(game, team) <= 0) {
                continue;
            }
            UnitKind kind = state.archerNext ? UnitKind.ARCHER : UnitKind.SOLDIER;
            if (team.spend(kind.cost())) {
                barracks.enqueue(kind, true);
                state.archerNext = !state.archerNext;
            }
        }
    }

    /**
     * Defends the town center first: fighters at home go at any enemy near it, and all of them when a team rather
     * than a mob comes. Otherwise, once enough fighters are at home, they go on an attack move at the nearest enemy
     * building; fighters out attacking go on to the next enemy building when they are done.
     */
    private void fight(Game game, WorldServer world, Team team, Building hall, List<EntityUnit> fighters, State state) {
        double x = hall.getCenterX();
        double z = hall.getCenterZ();
        List<EntityUnit> home = new ArrayList<>();
        List<EntityUnit> away = new ArrayList<>();
        for (EntityUnit fighter : fighters) {
            (distance(fighter, x, z) <= HOME_RANGE ? home : away).add(fighter);
        }
        EntityLivingBase intruder = Units.get()
            .findEnemyNear(world, team, x, hall.groundY + 1, z, DANGER_RANGE);
        if (intruder != null) {
            List<EntityUnit> defenders = new ArrayList<>(Units.isMob(intruder) ? home : fighters);
            defenders.removeIf(unit -> unit.getOrder() == EntityUnit.Order.ATTACK || unit.isAttackMoving());
            if (!defenders.isEmpty()) {
                Units.get()
                    .orderAttackMove(defenders, intruder.posX, intruder.posZ, Units.Formation.BOX);
            }
            return;
        }
        home.removeIf(unit -> unit.getOrder() != EntityUnit.Order.IDLE);
        if (home.size() >= FIRST_WAVE + WAVE_GROWTH * state.waves && ticks - state.lastWave >= WAVE_TICKS) {
            Building target = nearestEnemyBuilding(game, team, x, z);
            if (target != null) {
                Units.get()
                    .orderAttackMove(home, target.getCenterX(), target.getCenterZ(), Units.Formation.BOX);
                state.waves++;
                state.lastWave = ticks;
                WarOfTheGods.LOG
                    .info("AI {} is attacking {} with {} units", team.getName(), target.describe(game), home.size());
            }
        }
        away.removeIf(unit -> unit.getOrder() != EntityUnit.Order.IDLE);
        if (away.isEmpty()) {
            return;
        }
        double awayX = 0;
        double awayZ = 0;
        for (EntityUnit fighter : away) {
            awayX += fighter.posX / away.size();
            awayZ += fighter.posZ / away.size();
        }
        Building target = nearestEnemyBuilding(game, team, awayX, awayZ);
        if (target == null) {
            return;
        }
        if (target.distanceTo(awayX, awayZ) <= SIEGE_RANGE) {
            Units.get()
                .orderAttackBuilding(away, target);
        } else {
            Units.get()
                .orderAttackMove(away, target.getCenterX(), target.getCenterZ(), Units.Formation.BOX);
        }
    }

    /** The enemy building nearest x, z, a town center if any is left; or null. */
    private static Building nearestEnemyBuilding(Game game, Team team, double x, double z) {
        Building best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Building building : game.getBuildings()) {
            Team owner = game.getTeam(building.team);
            if (owner == null || owner.isDefeated() || !game.areEnemies(team, owner)) {
                continue;
            }
            double distance = building.distanceTo(x, z) + (building.type == BuildingType.TOWN_HALL ? 0 : 10000);
            if (distance < bestDistance) {
                best = building;
                bestDistance = distance;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- counting

    /** The team's first finished town center, passing over town center sites; or null. */
    private static Building finishedTownCenter(Game game, Team team) {
        for (Building building : game.getBuildings(team)) {
            if (building.type == BuildingType.TOWN_HALL && building.isComplete()) {
                return building;
            }
        }
        return null;
    }

    private static double distance(EntityUnit unit, double x, double z) {
        double dx = unit.posX - x;
        double dz = unit.posZ - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The team's buildings of the type, finished ones only or building sites too. */
    private static int count(Game game, Team team, BuildingType type, boolean finishedOnly) {
        int count = 0;
        for (Building building : game.getBuildings(team)) {
            if (building.type == type && (building.isComplete() || !finishedOnly)) {
                count++;
            }
        }
        return count;
    }

    /** Units of the kind waiting in the team's training queues. */
    private static int queued(Game game, Team team, UnitKind kind) {
        int count = 0;
        for (Building building : game.getBuildings(team)) {
            for (UnitKind queued : building.getQueue()) {
                count += queued == kind ? 1 : 0;
            }
        }
        return count;
    }
}
