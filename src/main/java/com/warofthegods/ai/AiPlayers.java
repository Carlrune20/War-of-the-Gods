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
import com.warofthegods.game.Faction;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.GoldMine;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Team;
import com.warofthegods.game.UnitKind;
import com.warofthegods.game.Upgrade;
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
 * player, commanding only the units it trained itself and sharing the team's resources. It trains workers and spreads
 * them over food, wood and gold, puts up houses, farms and barracks near its town center, takes its town into the next
 * age, trains warriors and archers, sends them on an attack move at the nearest enemy every few minutes and pulls
 * them back when its town center is attacked. It also puts up a storehouse and an armory and researches their
 * upgrades, builds its god's knights' fortress in Age III and trains knights and the hero there, sends workers to
 * repair damaged buildings once no enemy is at them, and spends what it has to spare on more barracks, towers and
 * houses. It pays for everything like a player and gets no bonuses.
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
    /** Workers it puts up a storehouse with in Age I. */
    private static final int STOREHOUSE_WORKERS = 8;
    /** Fighters it has before it saves up for a knights' fortress rather than train more. */
    private static final int FORTRESS_FIGHTERS = 8;
    /** Of each resource a building or upgrade costs, how much more it keeps in hand after paying for it. */
    private static final long RESEARCH_MARGIN = 50;
    /** How much it has to have left over after paying for a building it does not need, to count it as to spare. */
    private static final long SPARE_MARGIN = 250;
    /** Most of each building it builds in all once it has resources to spare. */
    private static final int MAX_EXTRA_BARRACKS = 3;
    private static final int MAX_TOWERS = 6;
    private static final int MAX_FORTRESSES = 2;
    /** Workers it sends to repair a building, and most repairing at once. */
    private static final int REPAIRERS = 2;
    private static final int MAX_REPAIRERS = 6;
    /** Enemies this close to a damaged building keep its workers away until they are gone. */
    private static final double REPAIR_DANGER_RANGE = 16;
    /** Workers it lays a knights' fortress out with; other buildings get two. */
    private static final int FORTRESS_BUILDERS = 3;
    /** The upgrades it researches, in the order it wants them; wall upgrades are left out, as it builds no walls. */
    private static final Upgrade[] RESEARCH_ORDER = { Upgrade.BASKETS, Upgrade.SHARP_AXES, Upgrade.PLOUGHS,
        Upgrade.PICKAXES, Upgrade.FORGED_BLADES, Upgrade.PADDED_ARMOR, Upgrade.FLETCHING, Upgrade.WATCH_TOWER,
        Upgrade.MASONRY, Upgrade.PLATE_ARMOR, Upgrade.FIRE_ARROWS, Upgrade.FORTIFIED_WALLS, Upgrade.DIAMOND_HOES,
        Upgrade.DIAMOND_AXES, Upgrade.DIAMOND_PICKAXES, Upgrade.TEMPERED_STEEL, Upgrade.GUARD_TOWER };

    /** What the computer remembers about a team between looks; lost on a restart, which does no harm. */
    private static final class State {

        int lastWave = -WAVE_TICKS;
        int waves;
        boolean archerNext;
        /** Whether no spot near the town center fits a knights' fortress, so it stops waiting to build one. */
        boolean noFortressSpot;
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
        // In Age III, with a few fighters to hold the town, it saves up for its knights' fortress before more of them.
        boolean savingForFortress = !saving && !state.noFortressSpot
            && game.getAge(team) >= 3
            && count(game, team, fortressOf(team), false) == 0
            && fighters.size() >= FORTRESS_FIGHTERS;
        build(game, world, team, state, workers, saving, fighters.size());
        staffSites(game, team, workers);
        repair(game, world, team, workers);
        gather(game, world, team, hall, workers);
        if (!saving && !savingForFortress) {
            research(game, team);
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

    /**
     * Lays out one building at a time near the town center: a house when it runs short of room, then what it needs
     * for its age, then, with resources to spare, more barracks, towers, houses and fortresses.
     */
    private static void build(Game game, WorldServer world, Team team, State state, List<EntityUnit> workers,
        boolean saving, int fighters) {
        for (Building building : game.getBuildings(team)) {
            if (!building.isComplete() && !building.type.isWall()) {
                return;
            }
        }
        BuildingType type = neededBuilding(game, team, state, workers.size(), saving);
        if (type != null) {
            if (!team.canAfford(type.cost())) {
                return;
            }
        } else if (!saving) {
            type = spareBuilding(game, team, state, fighters);
        }
        if (type == null) {
            return;
        }
        Buildings.Plan plan = Buildings.get()
            .findSpot(game, world, team, type);
        if (plan == null && type.isFortress()) {
            state.noFortressSpot = true;
            WarOfTheGods.LOG.info("AI {} found no spot for {}", team.getName(), type.displayName);
        }
        if (plan == null || !team.spend(type.cost())) {
            return;
        }
        Building site = Buildings.get()
            .startSite(game, team, plan);
        List<EntityUnit> builders = spareWorkers(workers, type.isFortress() ? FORTRESS_BUILDERS : 2);
        Units.get()
            .orderBuild(builders, site);
        WarOfTheGods.LOG
            .info("AI {} laid out {} with {} workers", team.getName(), site.describe(game), builders.size());
    }

    /** The building it needs next, which it waits to afford; or null when it needs none now. */
    private static BuildingType neededBuilding(Game game, Team team, State state, int workers, boolean saving) {
        int age = game.getAge(team);
        if (Buildings.room(game, team) <= 3 && game.getPopulationCap(team) < Config.unitsPerTeam) {
            return BuildingType.HOUSE;
        }
        if (saving) {
            return null;
        }
        if (count(game, team, BuildingType.STOREHOUSE, false) == 0 && (workers >= STOREHOUSE_WORKERS || age >= 2)) {
            return BuildingType.STOREHOUSE;
        }
        if (age < 2) {
            return null;
        }
        int foodWorkers = (int) Math.round(WORKERS_WANTED[age] * SHARE_LATER[0]);
        if (count(game, team, BuildingType.BARRACKS, false) == 0) {
            return BuildingType.BARRACKS;
        }
        if (count(game, team, BuildingType.FARM, false) * BuildingType.FARM.maxWorkers() < foodWorkers) {
            return BuildingType.FARM;
        }
        if (count(game, team, BuildingType.ARMORY, false) == 0) {
            return BuildingType.ARMORY;
        }
        if (age >= 3 && !state.noFortressSpot && count(game, team, fortressOf(team), false) == 0) {
            return fortressOf(team);
        }
        if (age >= 3 && count(game, team, BuildingType.BARRACKS, false) < 2) {
            return BuildingType.BARRACKS;
        }
        return null;
    }

    /** A building it does not need but has the resources to spare for, or null. */
    private static BuildingType spareBuilding(Game game, Team team, State state, int fighters) {
        int age = game.getAge(team);
        List<BuildingType> wanted = new ArrayList<>();
        if (age >= 3 && !state.noFortressSpot && count(game, team, fortressOf(team), false) < MAX_FORTRESSES) {
            wanted.add(fortressOf(team));
        }
        if (age >= 2 && count(game, team, BuildingType.BARRACKS, false) < MAX_EXTRA_BARRACKS) {
            wanted.add(BuildingType.BARRACKS);
        }
        if (age >= 2 && count(game, team, BuildingType.TOWER, false) < MAX_TOWERS) {
            wanted.add(BuildingType.TOWER);
        }
        // Room for the army it means to train.
        if (Buildings.room(game, team) <= 8 && fighters < MAX_FIGHTERS
            && game.getPopulationCap(team) < Config.unitsPerTeam) {
            wanted.add(BuildingType.HOUSE);
        }
        for (BuildingType type : wanted) {
            if (affords(team, type.cost(), SPARE_MARGIN)) {
                return type;
            }
        }
        return null;
    }

    /** The knights' fortress of the team's god. */
    private static BuildingType fortressOf(Team team) {
        return team.getFaction() == Faction.ZAMORAK ? BuildingType.BLACK_KNIGHTS_FORTRESS
            : BuildingType.WHITE_KNIGHTS_FORTRESS;
    }

    /** Whether the team can pay the cost and keep the margin of each resource it costs. */
    private static boolean affords(Team team, Map<Resource, Long> cost, long margin) {
        for (Map.Entry<Resource, Long> entry : cost.entrySet()) {
            if (team.getResource(entry.getKey()) < entry.getValue() + margin) {
                return false;
            }
        }
        return true;
    }

    /** Starts researching the first upgrade it wants that one of its buildings can research now and it can pay for. */
    private static void research(Game game, Team team) {
        for (Upgrade upgrade : RESEARCH_ORDER) {
            if (team.has(upgrade) || game.getAge(team) < upgrade.level) {
                continue;
            }
            for (Building building : game.getBuildings(team)) {
                if (building.type != upgrade.at || Buildings.researchProblem(game, building, upgrade) != null) {
                    continue;
                }
                if (!affords(team, upgrade.cost(), RESEARCH_MARGIN) || !team.spend(upgrade.cost())) {
                    // It waits for this one rather than spend on those after it.
                    return;
                }
                building.startResearch(upgrade.id);
                WarOfTheGods.LOG.info("AI {} is researching {}", team.getName(), upgrade.displayName.toLowerCase());
                return;
            }
        }
    }

    /**
     * Sends workers to repair its damaged buildings that no enemy is at, most damaged first, a few workers at a time.
     */
    private static void repair(Game game, WorldServer world, Team team, List<EntityUnit> workers) {
        int repairing = 0;
        List<Building> damaged = new ArrayList<>();
        for (Building building : game.getBuildings(team)) {
            if (building.isDamaged()) {
                damaged.add(building);
                repairing += assigned(workers, building);
            }
        }
        damaged.sort(
            (a, b) -> Double
                .compare(a.getHealth() / (double) a.getMaxHealth(), b.getHealth() / (double) b.getMaxHealth()));
        for (Building building : damaged) {
            int wanted = REPAIRERS - assigned(workers, building);
            if (wanted <= 0 || repairing >= MAX_REPAIRERS
                || Units.get()
                    .findEnemyNear(
                        world,
                        team,
                        building.getCenterX(),
                        building.groundY + 1,
                        building.getCenterZ(),
                        REPAIR_DANGER_RANGE)
                    != null) {
                continue;
            }
            List<EntityUnit> repairers = spareWorkers(workers, Math.min(wanted, MAX_REPAIRERS - repairing));
            int sent = Units.get()
                .orderBuild(repairers, building);
            repairing += sent;
            if (sent > 0) {
                WarOfTheGods.LOG
                    .info("AI {} sent {} workers to repair {}", team.getName(), sent, building.describe(game));
            }
        }
    }

    /** Its workers sent to build or repair the building, whether there yet or on the way. */
    private static int assigned(List<EntityUnit> workers, Building building) {
        int count = 0;
        for (EntityUnit worker : workers) {
            count += worker.getOrder() == EntityUnit.Order.BUILD && worker.getBuildingId() == building.id ? 1 : 0;
        }
        return count;
    }

    /** Sends a worker to every building site nobody is building. */
    private static void staffSites(Game game, Team team, List<EntityUnit> workers) {
        for (Building site : game.getBuildings(team)) {
            if (!site.isComplete() && Buildings.countBuilders(site) == 0 && assigned(workers, site) == 0) {
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
            // Counting workers on their way there and carrying from it, not only those at it, so it sends no more
            // than a farm takes.
            if (farm.type == BuildingType.FARM && Units.takesWorkers(farm) && Units.hasRoom(farm)) {
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

    /**
     * Trains warriors and archers in turn at its barracks, and at a knights' fortress its hero when it has none, and
     * knights otherwise.
     */
    private static void trainFighters(Game game, Team team, State state, int fighters) {
        int planned = fighters;
        for (UnitKind kind : UnitKind.values()) {
            planned += kind.fights() ? queued(game, team, kind) : 0;
        }
        if (planned >= MAX_FIGHTERS) {
            return;
        }
        for (Building building : game.getBuildings(team)) {
            if (!building.isComplete() || building.getQueue()
                .size() >= QUEUE || Buildings.room(game, team) <= 0) {
                continue;
            }
            if (building.type == BuildingType.BARRACKS) {
                UnitKind kind = state.archerNext ? UnitKind.ARCHER : UnitKind.SOLDIER;
                if (team.spend(kind.cost())) {
                    building.enqueue(kind, true);
                    state.archerNext = !state.archerNext;
                }
            } else if (building.type.isFortress()) {
                // The hero first, whenever it has none.
                List<UnitKind> kinds = new ArrayList<>(building.type.trains);
                kinds.sort((a, b) -> Boolean.compare(b.hero, a.hero));
                for (UnitKind kind : kinds) {
                    if (game.heroRoom(team, kind) > 0 && team.spend(kind.cost())) {
                        building.enqueue(kind, true);
                        WarOfTheGods.LOG.info("AI {} is training {}", team.getName(), kind.displayName);
                        break;
                    }
                }
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
