package com.warofthegods.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

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
import com.warofthegods.game.WallLine;
import com.warofthegods.unit.EntityUnit;
import com.warofthegods.unit.Hunting;
import com.warofthegods.unit.Prayer;
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
 * houses. It puts storehouses by the forests and gold mines its workers gather far from home, walls its town between
 * its towers with a gate in each side, takes free settlements with more town centers and puts a barracks and a tower
 * by each, and marches its attacks in box or line formation as often as it lets them run.
 *
 * <p>
 * It plays by the fog of war: it attacks only enemy buildings its units or buildings have come near, and while it
 * knows of none it sends scouts, cavalry first, to where the enemy teams' bases are, which every player knows from the
 * team list, and on from one unexplored part of the map to the next until they find the enemy. Its
 * Sir Tradigee puts up the protection prayer against what most of the enemies around him fight with, and from then
 * on switches only to the one against what he is hit with. Playing
 * Zamorak, it raises a stone circle once it has dark wizards, keeps four of them standing at it and summons lesser
 * demons there when it has the favor; playing Saradomin, its monks earn favor and its wizards' tower trains blue
 * wizards. Once it has walls it researches stone and then citadel walls. It pays for everything like a player and gets
 * no bonuses.
 *
 * <p>
 * Handed a team only to run its economy, it commands all the team's workers, whoever trained them, and nothing else:
 * it trains workers, shares them over food, wood and gold, puts up houses, storehouses and farms, staffs building
 * sites nobody is at and repairs. The players lead the army, take the town into the next age and research, and may
 * still order any worker about; it puts a worker back to gathering once it stands idle.
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
    /** Blocks from a storehouse it chops in once the trees by the town center are gone. */
    private static final int STORE_TREE_RANGE = 32;
    /** Blocks from the town center it looks for trees when none stand nearer, and how often, in ticks. */
    private static final int FAR_TREE_RANGE = 128;
    private static final int FAR_TREE_TICKS = 20 * 30;
    /** Blocks round trees it found far off that it chops in until they run out. */
    private static final int FOREST_RANGE = 24;
    /** Workers gathering in one place before it puts a storehouse there, and how far from a store that is. */
    private static final int OUTPOST_WORKERS = 3;
    private static final double OUTPOST_DISTANCE = 28;
    private static final int MAX_STOREHOUSES = 4;
    /** Most town centers it keeps, its first among them. */
    private static final int MAX_TOWN_CENTERS = 3;
    /** Blocks from a town center that it looks for a barracks or tower of its own by, and puts one up in. */
    private static final double OUTPOST_RANGE = 32;
    /**
     * Pieces of wall it builds with at once, and how far out it bends a side of its wall round a building in the way
     * of it, whole pieces at a time so the bend meets the rest of the wall.
     */
    private static final int WALL_BUILDERS = 2;
    private static final int[] WALL_OFFSETS = { 0, 5, 10, 15, 20, 25 };
    /** Fewest towers its wall runs between, so that it goes all the way round the town. */
    private static final int WALL_TOWERS = 3;
    /** Ticks between looks at its walls for pieces knocked down, and before it tries again a side it found no room for. */
    private static final int BREACH_CHECK_TICKS = 200;
    private static final int WALL_RETRY_TICKS = 2400;
    /** Blocks a marching army walks toward its target between orders, and how near it gets before it charges. */
    private static final double MARCH_LEG = 12;
    private static final double CHARGE_RANGE = 28;
    /** Formations its attacks march in, in turn; null runs at the target as a crowd. */
    private static final Units.Formation[] WAVE_FORMATIONS = { Units.Formation.BOX, Units.Formation.LINE, null };
    /** Workers it lays a knights' fortress out with; other buildings get two. */
    private static final int FORTRESS_BUILDERS = 3;
    /** The upgrades it researches, in the order it wants them; the wall upgrades only once it has walls. */
    private static final Upgrade[] RESEARCH_ORDER = { Upgrade.ARMED_GOBLINS, Upgrade.BASKETS, Upgrade.SHARP_AXES,
        Upgrade.PLOUGHS, Upgrade.PICKAXES, Upgrade.FORGED_BLADES, Upgrade.PADDED_ARMOR, Upgrade.FLETCHING,
        Upgrade.WATCH_TOWER, Upgrade.MASONRY, Upgrade.PLATE_ARMOR, Upgrade.FIRE_ARROWS, Upgrade.FORTIFIED_WALLS,
        Upgrade.STONE_WALLS, Upgrade.DIAMOND_HOES, Upgrade.DIAMOND_AXES, Upgrade.DIAMOND_PICKAXES,
        Upgrade.TEMPERED_STEEL, Upgrade.GUARD_TOWER, Upgrade.ELITE_BLACK_KNIGHTS, Upgrade.JUSTICIARS,
        Upgrade.CITADEL_WALLS, Upgrade.GREATER_DEMONS, Upgrade.BLACK_DEMONS };
    /** Blocks across a cell of the map it keeps track of having explored. */
    private static final int FOG_CELL = 8;
    /** How far its units and buildings see, in blocks, as in the RTS view's fog of war. */
    private static final double UNIT_SIGHT = 36;
    private static final double BUILDING_SIGHT = 40;
    private static final double LOOKOUT_SIGHT = 60;
    /** Most scouts it has out at once: one for each enemy base it has not looked at yet, up to this many. */
    private static final int MAX_SCOUTS = 2;
    /** Times a scout that stopped short of a spot is sent again before the spot is given up on. */
    private static final int SCOUT_TRIES = 3;
    /** Blocks short of an unexplored cell's middle a scout counts as there, as it sees it from further off anyway. */
    private static final double SCOUTED = 16;
    /** Blocks round Sir Tradigee it looks at the enemies of to pick his prayer, and towers shoot about this far. */
    private static final double PRAYER_RANGE = 20;
    private static final double TOWER_REACH = 24;
    /** Centaurs it keeps at most. */
    private static final int MAX_CENTAURS = 6;
    /** Lesser demons it keeps at most. */
    private static final int MAX_DEMONS = 2;
    /** Of food and gold, how much more than a lesser demon costs it keeps in hand after summoning one. */
    private static final long DEMON_MARGIN = 100;

    /** What the computer remembers about a team between looks; lost on a restart, which does no harm. */
    private static final class State {

        int lastWave = -WAVE_TICKS;
        int waves;
        /** Whether no spot near the town center fits a knights' fortress, so it stops waiting to build one. */
        boolean noFortressSpot;
        /** The foot of a tree it found far from home, round which it chops; null when it knows of none. */
        int[] forest;
        int lastFarTreeSearch = -FAR_TREE_TICKS;
        /**
         * The corners of the ring of wall it puts round its town, the middles of its towers in order round the town
         * center when it started walling; null until then.
         */
        int[][] ring;
        /**
         * For each side of the ring, how its wall was bent out (x, z) once laid out, to lay out what was knocked down
         * of it again the same way; null while not yet laid out.
         */
        int[][] wallBends;
        /** For each side of the ring, when it last found no room to wall it. */
        int[] wallTried;
        int lastBreachCheck;
        /** Wall segments it makes gates of once they are built, by building id. */
        final List<Integer> gates = new ArrayList<>();
        /** Where its gates go, as the min x, min z of each, to make a gate again of wall rebuilt there. */
        final List<int[]> gateSpots = new ArrayList<>();
        /** The fighters marching on an attack in formation, and the formation. */
        final List<EntityUnit> march = new ArrayList<>();
        Units.Formation marchFormation;
        /** The map's cells its units and buildings have come near, FOG_CELL blocks across from minX, minZ. */
        boolean[] explored;
        int minX;
        int minZ;
        int columns;
        int rows;
        /** Enemy buildings it has seen, by id: the only ones it attacks. */
        final Set<Integer> knownBuildings = new HashSet<>();
        /** The units out finding the enemy while it knows of no enemy building, and the spot each is headed for. */
        final Map<EntityUnit, double[]> scouts = new HashMap<>();
        /** Times scouts stopped short of each spot, by its cell. */
        final Map<Integer, Integer> scoutTries = new HashMap<>();
        /** Dark wizards it keeps at its stone circle to summon lesser demons. */
        final List<EntityUnit> summoners = new ArrayList<>();
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
        if (team.isAiEconomy()) {
            runEconomy(game, world, team, state, hall);
            return;
        }
        List<EntityUnit> workers = new ArrayList<>();
        List<EntityUnit> fighters = new ArrayList<>();
        // Playing alongside people, it commands only the units it trained; on its own, the whole team's.
        boolean alongsidePlayers = !team.getMembers()
            .isEmpty();
        List<EntityUnit> all = Units.get()
            .getUnits(team);
        for (EntityUnit unit : all) {
            // Priests and monks only pray, and are left where they are; goblin priests too, though they can fight.
            if ((!alongsidePlayers || unit.isAiControlled()) && (unit.isWorker() || unit.getKind()
                .fights()
                && !unit.getKind()
                    .praysAtTemple())) {
                (unit.isWorker() ? workers : fighters).add(unit);
            }
        }
        explore(game, team, state, all);
        summon(game, world, team, state, hall, fighters);
        fighters.removeAll(state.summoners);
        pray(game, world, team, fighters);
        trainWorkers(game, team, hall, workers.size());
        boolean saving = advanceAge(game, team, hall, workers.size(), fighters.size());
        // In Age III, with a few fighters to hold the town, it saves up for its knights' fortress before more of them.
        boolean savingForFortress = !saving && !state.noFortressSpot
            && game.getAge(team) >= 3
            && count(game, team, fortressOf(team), false) == 0
            && fighters.size() >= FORTRESS_FIGHTERS;
        build(game, world, team, state, hall, workers, saving, fighters.size(), false);
        wall(game, world, team, state, hall, workers, !saving && !savingForFortress, ticks);
        makeGates(game, world, state);
        staffSites(game, team, workers);
        repair(game, world, team, workers);
        gather(game, world, team, hall, workers, state);
        if (!saving && !savingForFortress) {
            research(game, team);
            trainFighters(game, team, state, fighters.size());
            trainWorshippers(game, team);
        }
        fight(game, world, team, hall, fighters, state);
        scout(game, team, hall, fighters, state);
    }

    /** Runs only the team's economy, with every worker the team has, and leaves the rest to its players. */
    private void runEconomy(Game game, WorldServer world, Team team, State state, Building hall) {
        List<EntityUnit> workers = new ArrayList<>();
        for (EntityUnit unit : Units.get()
            .getUnits(team)) {
            if (unit.isWorker()) {
                workers.add(unit);
            }
        }
        trainWorkers(game, team, hall, workers.size());
        build(game, world, team, state, hall, workers, false, 0, true);
        staffSites(game, team, workers);
        repair(game, world, team, workers);
        gather(game, world, team, hall, workers, state);
    }

    // ---------------------------------------------------------------- fog of war

    /**
     * Marks the cells its units, its allies' units and their buildings are near as explored, and remembers the enemy
     * buildings in them.
     */
    private static void explore(Game game, Team team, State state, List<EntityUnit> units) {
        if (state.explored == null || state.minX != game.getMinX() || state.minZ != game.getMinZ()) {
            state.minX = game.getMinX();
            state.minZ = game.getMinZ();
            state.columns = (game.getMaxX() - game.getMinX()) / FOG_CELL + 1;
            state.rows = (game.getMaxZ() - game.getMinZ()) / FOG_CELL + 1;
            state.explored = new boolean[state.columns * state.rows];
            state.knownBuildings.clear();
        }
        List<EntityUnit> seeing = new ArrayList<>(units);
        for (Team ally : game.getAllies(team)) {
            seeing.addAll(
                Units.get()
                    .getUnits(ally));
        }
        for (EntityUnit unit : seeing) {
            see(state, unit.posX, unit.posZ, UNIT_SIGHT);
        }
        for (Building building : game.getBuildings()) {
            Team owner = game.getTeam(building.team);
            if (owner == team || owner != null && game.getAllies(team)
                .contains(owner)) {
                boolean lookout = building.isComplete()
                    && (building.type == BuildingType.TOWER || building.type == BuildingType.TOWN_HALL);
                see(
                    state,
                    building.getCenterX(),
                    building.getCenterZ(),
                    (lookout ? LOOKOUT_SIGHT : BUILDING_SIGHT) + Math.max(building.sizeX, building.sizeZ) / 2.0);
            }
        }
        for (Building building : game.getBuildings()) {
            if (!state.knownBuildings.contains(building.id)
                && explored(state, building.getCenterX(), building.getCenterZ())) {
                state.knownBuildings.add(building.id);
            }
        }
    }

    private static void see(State state, double x, double z, double range) {
        int c0 = Math.max(0, (int) Math.floor((x - range - state.minX) / FOG_CELL));
        int c1 = Math.min(state.columns - 1, (int) Math.floor((x + range - state.minX) / FOG_CELL));
        int r0 = Math.max(0, (int) Math.floor((z - range - state.minZ) / FOG_CELL));
        int r1 = Math.min(state.rows - 1, (int) Math.floor((z + range - state.minZ) / FOG_CELL));
        for (int r = r0; r <= r1; r++) {
            double dz = state.minZ + r * FOG_CELL + FOG_CELL / 2.0 - z;
            for (int c = c0; c <= c1; c++) {
                double dx = state.minX + c * FOG_CELL + FOG_CELL / 2.0 - x;
                if (dx * dx + dz * dz <= range * range) {
                    state.explored[r * state.columns + c] = true;
                }
            }
        }
    }

    private static boolean explored(State state, double x, double z) {
        int c = (int) Math.floor((x - state.minX) / FOG_CELL);
        int r = (int) Math.floor((z - state.minZ) / FOG_CELL);
        return c >= 0 && r >= 0 && c < state.columns && r < state.rows && state.explored[r * state.columns + c];
    }

    /** The middle of the unexplored cell nearest x, z, or null once all the map is explored. */
    private static double[] nearestUnexplored(State state, double x, double z) {
        double[] best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int r = 0; r < state.rows; r++) {
            for (int c = 0; c < state.columns; c++) {
                if (state.explored[r * state.columns + c]) {
                    continue;
                }
                double cx = state.minX + c * FOG_CELL + FOG_CELL / 2.0;
                double cz = state.minZ + r * FOG_CELL + FOG_CELL / 2.0;
                double distance = (cx - x) * (cx - x) + (cz - z) * (cz - z);
                if (distance < bestDistance) {
                    best = new double[] { cx, cz };
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /**
     * While it knows of no enemy building, sends scouts, cavalry first as they are fastest: one to each enemy base it
     * has not looked at yet, as that is where the enemy most likely is, then on from one unexplored part of the map to
     * the next. A scout that stops short of a spot, as at water it found no way across, is sent again a few times
     * before the spot is given up on. Once it has found an enemy building, the scouts are fighters again and its
     * attacks go there.
     */
    private static void scout(Game game, Team team, Building hall, List<EntityUnit> fighters, State state) {
        state.scouts.keySet()
            .removeIf(scout -> !scout.isEntityAlive() || !fighters.contains(scout));
        if (nearestEnemyBuilding(game, team, state, hall.getCenterX(), hall.getCenterZ()) != null) {
            state.scouts.clear();
            return;
        }
        List<double[]> bases = new ArrayList<>();
        for (Team other : game.getTeams()) {
            int[] base = other.getBase();
            if (base != null && !other.isDefeated()
                && game.areEnemies(team, other)
                && !explored(state, base[0] + 0.5, base[1] + 0.5)) {
                bases.add(new double[] { base[0] + 0.5, base[1] + 0.5 });
            }
        }
        int wanted = Math.min(MAX_SCOUTS, Math.max(1, bases.size()));
        while (state.scouts.size() < wanted) {
            EntityUnit pick = null;
            for (EntityUnit fighter : fighters) {
                if (fighter.getOrder() == EntityUnit.Order.IDLE && !state.march.contains(fighter)
                    && !state.scouts.containsKey(fighter)
                    && fighter.getKind() != UnitKind.LESSER_DEMON
                    && (pick == null || fighter.getKind() == UnitKind.CAVALRY)) {
                    pick = fighter;
                }
            }
            if (pick == null) {
                break;
            }
            state.scouts.put(pick, null);
            WarOfTheGods.LOG.info(
                "AI {} sent {} to scout",
                team.getName(),
                pick.getCommandSenderName()
                    .replaceAll("\u00a7.", ""));
        }
        for (Map.Entry<EntityUnit, double[]> entry : state.scouts.entrySet()) {
            EntityUnit scout = entry.getKey();
            double[] goal = entry.getValue();
            boolean there = goal == null || distance(scout, goal[0], goal[1]) <= SCOUTED
                || explored(state, goal[0], goal[1]);
            if (!there && scout.getOrder() != EntityUnit.Order.IDLE) {
                continue;
            }
            if (!there && state.scoutTries.merge(cellOf(state, goal[0], goal[1]), 1, Integer::sum) >= SCOUT_TRIES) {
                // Stopped short again and again: no way there, so that spot is given up on.
                markExplored(state, goal[0], goal[1]);
            }
            if (!there && !explored(state, goal[0], goal[1])) {
                go(scout, goal);
                continue;
            }
            // The nearest enemy base no other scout is headed for, else the nearest unexplored spot.
            double[] next = null;
            for (double[] base : bases) {
                boolean taken = false;
                for (double[] other : state.scouts.values()) {
                    taken |= other != null && other[0] == base[0] && other[1] == base[1];
                }
                if (!taken && !explored(state, base[0], base[1])
                    && (next == null || distance(scout, base[0], base[1]) < distance(scout, next[0], next[1]))) {
                    next = base;
                }
            }
            if (next == null) {
                next = nearestUnexplored(state, scout.posX, scout.posZ);
            }
            entry.setValue(next);
            if (next != null) {
                go(scout, next);
            }
        }
    }

    private static void go(EntityUnit unit, double[] spot) {
        List<EntityUnit> one = new ArrayList<>();
        one.add(unit);
        Units.get()
            .orderMove(one, spot[0], spot[1]);
    }

    private static int cellOf(State state, double x, double z) {
        int c = (int) Math.floor((x - state.minX) / FOG_CELL);
        int r = (int) Math.floor((z - state.minZ) / FOG_CELL);
        return r * state.columns + c;
    }

    private static void markExplored(State state, double x, double z) {
        int c = (int) Math.floor((x - state.minX) / FOG_CELL);
        int r = (int) Math.floor((z - state.minZ) / FOG_CELL);
        if (c >= 0 && r >= 0 && c < state.columns && r < state.rows) {
            state.explored[r * state.columns + c] = true;
        }
    }

    // ---------------------------------------------------------------- gods

    /**
     * Playing Zamorak, keeps back each dark wizard it trains until it has enough to summon with, raises a stone
     * circle by its town center once it has the first, keeps them standing at it, and summons a lesser demon there
     * whenever it has room, favor and food and gold to spare. Held back as they come, rather than waited for idle all
     * at once, so its attacks do not keep taking them off before there are enough.
     */
    private static void summon(Game game, WorldServer world, Team team, State state, Building hall,
        List<EntityUnit> fighters) {
        state.summoners.retainAll(fighters);
        if (team.getFaction() != Faction.ZAMORAK) {
            return;
        }
        Building circle = null;
        for (Building building : game.getBuildings(team)) {
            if (building.type == BuildingType.STONE_CIRCLE) {
                circle = building;
            }
        }
        List<EntityUnit> wizards = new ArrayList<>();
        for (EntityUnit fighter : fighters) {
            if (fighter.getKind() == UnitKind.DARK_WIZARD && !state.summoners.contains(fighter)
                && fighter.getOrder() == EntityUnit.Order.IDLE
                && !state.march.contains(fighter)
                && !state.scouts.containsKey(fighter)) {
                wizards.add(fighter);
            }
        }
        while (state.summoners.size() < Buildings.SUMMONERS && !wizards.isEmpty()) {
            state.summoners.add(wizards.remove(0));
        }
        if (state.summoners.isEmpty()) {
            return;
        }
        if (circle == null) {
            if (!affords(team, BuildingType.STONE_CIRCLE.cost(), RESEARCH_MARGIN)) {
                return;
            }
            Buildings.Plan plan = Buildings.get()
                .findSpot(game, world, team, BuildingType.STONE_CIRCLE);
            if (plan == null || !team.spend(BuildingType.STONE_CIRCLE.cost())) {
                return;
            }
            circle = Buildings.get()
                .startSite(game, team, plan);
            WarOfTheGods.LOG.info("AI {} raised {}", team.getName(), circle.describe(game));
        }
        // Back to the circle, those that wandered off it to fight.
        List<EntityUnit> astray = new ArrayList<>();
        for (EntityUnit summoner : state.summoners) {
            if (summoner.getOrder() == EntityUnit.Order.IDLE
                && circle.distanceTo(summoner.posX, summoner.posZ) > Buildings.CIRCLE_REACH) {
                astray.add(summoner);
            }
        }
        if (!astray.isEmpty() && circle.isComplete()) {
            Units.get()
                .orderBuild(astray, circle);
        }
        int demons = queued(game, team, UnitKind.LESSER_DEMON);
        for (EntityUnit fighter : fighters) {
            demons += fighter.getKind() == UnitKind.LESSER_DEMON ? 1 : 0;
        }
        if (circle.isComplete() && circle.getQueue()
            .isEmpty()
            && demons < MAX_DEMONS
            && state.summoners.size() >= Buildings.SUMMONERS
            && Buildings.room(game, team) > 0
            && affordsDemon(team)
            && team.spend(UnitKind.LESSER_DEMON.cost())) {
            circle.enqueue(UnitKind.LESSER_DEMON, true);
            WarOfTheGods.LOG.info("AI {} is summoning a lesser demon", team.getName());
        }
    }

    /** Whether it has the favor for a lesser demon, and the food and gold with some to spare. */
    private static boolean affordsDemon(Team team) {
        for (Map.Entry<Resource, Long> entry : UnitKind.LESSER_DEMON.cost()
            .entrySet()) {
            long margin = entry.getKey() == Resource.FAVOR ? 0 : DEMON_MARGIN;
            if (team.getResource(entry.getKey()) < entry.getValue() + margin) {
                return false;
            }
        }
        return true;
    }

    /**
     * Has its Sir Tradigee put up a protection prayer when he has none, against what most of the enemies near him
     * fight with: magic against wizards, missiles against archers and towers, melee against the rest. Once one is up
     * he keeps it until he is hit with another style (see EntityUnit's computerPrays), so a spell's burning or a
     * crowd of swordsmen nearby does not take him off magic.
     */
    private static void pray(Game game, WorldServer world, Team team, List<EntityUnit> fighters) {
        for (EntityUnit hero : fighters) {
            if (hero.getKind() != UnitKind.TRADIGEE || hero.getPrayer() != Prayer.NONE) {
                continue;
            }
            int melee = 0;
            int missiles = 0;
            int magic = 0;
            for (EntityLivingBase enemy : Units.get()
                .findEnemiesNear(world, team, hero.posX, hero.posY, hero.posZ, PRAYER_RANGE, 12)) {
                UnitKind kind = enemy instanceof EntityUnit ? ((EntityUnit) enemy).getKind() : null;
                if (kind != null && kind.isCaster()) {
                    magic++;
                } else if (kind != null && kind.ranged) {
                    missiles++;
                } else {
                    melee++;
                }
            }
            for (Building building : game.getBuildings()) {
                Team owner = game.getTeam(building.team);
                if (building.type == BuildingType.TOWER && building.isComplete()
                    && owner != null
                    && game.areEnemies(team, owner)
                    && owner.has(Upgrade.WATCH_TOWER)
                    && building.distanceTo(hero.posX, hero.posZ) <= TOWER_REACH) {
                    missiles += 2;
                }
            }
            Prayer wanted = hero.getPrayer();
            if (magic > missiles && magic > melee) {
                wanted = Prayer.MAGIC;
            } else if (missiles > melee) {
                wanted = Prayer.MISSILES;
            } else if (melee > 0) {
                wanted = Prayer.MELEE;
            }
            if (wanted != hero.getPrayer()) {
                List<EntityUnit> one = new ArrayList<>();
                one.add(hero);
                Units.get()
                    .orderPrayer(one, wanted);
            }
        }
    }

    // ---------------------------------------------------------------- economy

    private static void trainWorkers(Game game, Team team, Building hall, int workers) {
        int wanted = WORKERS_WANTED[game.getAge(team)];
        UnitKind worker = team.getFaction() == Faction.BANDOS ? UnitKind.SLAVE : UnitKind.WORKER;
        if (workers + queued(game, team, worker) < wanted && hall.getQueue()
            .size() < QUEUE && Buildings.room(game, team) > 0 && team.spend(worker.cost())) {
            hall.enqueue(worker, true);
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
            : workers >= 16 && fighters >= 4 && count(game, team, own(team, BuildingType.BARRACKS), true) > 0;
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
     * Lays out one building at a time: a house near the town center when it runs short of room, then what it needs
     * for its age, then a storehouse by a forest or gold mine its workers gather far from a store, then a barracks
     * and a tower by each town center it took, then, with resources to spare, more town centers, barracks, towers,
     * houses and fortresses. Running only the economy, it puts up just houses, storehouses and farms.
     */
    private static void build(Game game, WorldServer world, Team team, State state, Building hall,
        List<EntityUnit> workers, boolean saving, int fighters, boolean economyOnly) {
        for (Building building : game.getBuildings(team)) {
            if (!building.isComplete() && !building.type.isWall()) {
                return;
            }
        }
        BuildingType type = economyOnly ? neededEconomyBuilding(game, team, workers.size())
            : neededBuilding(game, team, state, workers.size(), saving);
        // Where to put it, when not round the town center.
        double[] near = null;
        if (type == null && !saving) {
            near = outpostStorehouse(game, team, workers);
            if (near != null) {
                type = BuildingType.STOREHOUSE;
            } else if (!economyOnly) {
                Object[] guard = outpostGuard(game, team, hall);
                if (guard != null) {
                    type = (BuildingType) guard[0];
                    near = (double[]) guard[1];
                }
            }
        }
        if (type != null) {
            if (!team.canAfford(type.cost())) {
                return;
            }
        } else if (!saving && !economyOnly) {
            type = spareBuilding(game, team, state, fighters);
        }
        if (type == null) {
            return;
        }
        Buildings.Plan plan = near == null ? Buildings.get()
            .findSpot(game, world, team, type) : spotNear(game, world, type, near[0], near[1]);
        if (plan == null && type.isFortress()) {
            state.noFortressSpot = true;
            WarOfTheGods.LOG.info("AI {} found no spot for {}", team.getName(), type.displayName);
        }
        if (plan == null || !team.spend(type.cost())) {
            return;
        }
        Building site = Buildings.get()
            .startSite(game, team, plan);
        List<EntityUnit> builders = spareWorkers(
            workers,
            type.isFortress() || type == BuildingType.TOWN_HALL ? FORTRESS_BUILDERS : 2);
        Units.get()
            .orderBuild(builders, site);
        WarOfTheGods.LOG
            .info("AI {} laid out {} with {} workers", team.getName(), site.describe(game), builders.size());
    }

    /** The building it needs next, which it waits to afford; or null when it needs none now. */
    private static BuildingType neededBuilding(Game game, Team team, State state, int workers, boolean saving) {
        int age = game.getAge(team);
        if (Buildings.room(game, team) <= 3 && game.getPopulationCap(team) < Config.unitsPerTeam) {
            return own(team, BuildingType.HOUSE);
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
        BuildingType barracks = own(team, BuildingType.BARRACKS);
        if (count(game, team, barracks, false) == 0) {
            return barracks;
        }
        if (count(game, team, BuildingType.FARM, false) * BuildingType.FARM.maxWorkers() < foodWorkers) {
            return BuildingType.FARM;
        }
        if (wants(team, BuildingType.ARCHERY_RANGE) && count(game, team, BuildingType.ARCHERY_RANGE, false) == 0) {
            return BuildingType.ARCHERY_RANGE;
        }
        if (count(game, team, BuildingType.ARMORY, false) == 0) {
            return BuildingType.ARMORY;
        }
        if (wants(team, BuildingType.STABLES) && count(game, team, BuildingType.STABLES, false) == 0) {
            return BuildingType.STABLES;
        }
        // Its god's houses: Zamorak's chaos temple, Bandos's temple, or Saradomin's monastery and wizards' tower.
        for (BuildingType godly : godsBuildings(team)) {
            if (count(game, team, godly, false) == 0) {
                return godly;
            }
        }
        if (age >= 3 && !state.noFortressSpot && count(game, team, fortressOf(team), false) == 0) {
            return fortressOf(team);
        }
        if (age >= 3 && count(game, team, barracks, false) < 2) {
            return barracks;
        }
        return null;
    }

    /** Running only the economy: a house when short of room, its first storehouse, or farms for its food workers. */
    private static BuildingType neededEconomyBuilding(Game game, Team team, int workers) {
        int age = game.getAge(team);
        if (Buildings.room(game, team) <= 3 && game.getPopulationCap(team) < Config.unitsPerTeam) {
            return own(team, BuildingType.HOUSE);
        }
        if (count(game, team, BuildingType.STOREHOUSE, false) == 0 && (workers >= STOREHOUSE_WORKERS || age >= 2)) {
            return BuildingType.STOREHOUSE;
        }
        int foodWorkers = (int) Math.round(WORKERS_WANTED[age] * SHARE_LATER[0]);
        if (age >= 2 && count(game, team, BuildingType.FARM, false) * BuildingType.FARM.maxWorkers() < foodWorkers) {
            return BuildingType.FARM;
        }
        return null;
    }

    /** A building it does not need but has the resources to spare for, or null. */
    private static BuildingType spareBuilding(Game game, Team team, State state, int fighters) {
        int age = game.getAge(team);
        List<BuildingType> wanted = new ArrayList<>();
        if (age >= 2 && count(game, team, BuildingType.TOWN_HALL, false) < MAX_TOWN_CENTERS) {
            wanted.add(BuildingType.TOWN_HALL);
        }
        if (age >= 3 && !state.noFortressSpot && count(game, team, fortressOf(team), false) < MAX_FORTRESSES) {
            wanted.add(fortressOf(team));
        }
        if (age >= 2 && count(game, team, own(team, BuildingType.BARRACKS), false) < MAX_EXTRA_BARRACKS) {
            wanted.add(own(team, BuildingType.BARRACKS));
        }
        if (age >= 2 && count(game, team, BuildingType.TOWER, false) < MAX_TOWERS) {
            wanted.add(BuildingType.TOWER);
        }
        // Room for the army it means to train.
        if (Buildings.room(game, team) <= 8 && fighters < MAX_FIGHTERS
            && game.getPopulationCap(team) < Config.unitsPerTeam) {
            wanted.add(own(team, BuildingType.HOUSE));
        }
        for (BuildingType type : wanted) {
            if (affords(team, type.cost(), SPARE_MARGIN)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Where to put a storehouse: by the gold mine or among the trees where most of its workers gather, when at least
     * a few do and no town center or storehouse of its own stands near; or null.
     */
    private static double[] outpostStorehouse(Game game, Team team, List<EntityUnit> workers) {
        if (count(game, team, BuildingType.STOREHOUSE, false) >= MAX_STOREHOUSES) {
            return null;
        }
        Map<Integer, Integer> miners = new HashMap<>();
        double treeX = 0;
        double treeZ = 0;
        int choppers = 0;
        for (EntityUnit worker : workers) {
            if (worker.getMineId() != 0) {
                miners.merge(worker.getMineId(), 1, Integer::sum);
            }
            int[] tree = worker.getTree();
            if (tree != null) {
                treeX += tree[0];
                treeZ += tree[2];
                choppers++;
            }
        }
        GoldMine busiest = null;
        int most = 0;
        for (Map.Entry<Integer, Integer> entry : miners.entrySet()) {
            GoldMine mine = game.getMine(entry.getKey());
            if (mine != null && !mine.isDepleted() && entry.getValue() > most) {
                busiest = mine;
                most = entry.getValue();
            }
        }
        List<double[]> spots = new ArrayList<>();
        if (most >= OUTPOST_WORKERS) {
            spots.add(new double[] { busiest.minX + busiest.size / 2.0, busiest.minZ + busiest.size / 2.0, most });
        }
        if (choppers >= OUTPOST_WORKERS) {
            spots.add(new double[] { treeX / choppers, treeZ / choppers, choppers });
        }
        spots.sort((a, b) -> Double.compare(b[2], a[2]));
        for (double[] spot : spots) {
            if (nearestOwn(game, team, spot[0], spot[1], BuildingType.TOWN_HALL, BuildingType.STOREHOUSE)
                > OUTPOST_DISTANCE) {
                return spot;
            }
        }
        return null;
    }

    /**
     * A barracks or tower a town center it took besides its first still wants by it, and where: the type and the
     * town center's middle; or null.
     */
    private static Object[] outpostGuard(Game game, Team team, Building hall) {
        if (game.getAge(team) < 2) {
            return null;
        }
        for (Building center : game.getBuildings(team)) {
            if (center.type != BuildingType.TOWN_HALL || !center.isComplete() || center == hall) {
                continue;
            }
            double[] middle = { center.getCenterX(), center.getCenterZ() };
            for (BuildingType type : new BuildingType[] { own(team, BuildingType.BARRACKS), BuildingType.TOWER }) {
                if (nearestOwn(game, team, middle[0], middle[1], type) > OUTPOST_RANGE) {
                    return new Object[] { type, middle };
                }
            }
        }
        return null;
    }

    /** Blocks from x, z to the nearest of the team's buildings of the types, sites too; very far if none. */
    private static double nearestOwn(Game game, Team team, double x, double z, BuildingType... types) {
        double best = Double.MAX_VALUE;
        for (Building building : game.getBuildings(team)) {
            for (BuildingType type : types) {
                if (building.type == type) {
                    best = Math.min(best, building.distanceTo(x, z));
                }
            }
        }
        return best;
    }

    /** The nearest spot round x, z a building of the type fits, a few blocks out and further; or null. */
    private static Buildings.Plan spotNear(Game game, WorldServer world, BuildingType type, double x, double z) {
        for (int ring = 8; ring <= OUTPOST_RANGE; ring += 3) {
            for (int step = 0; step < 16; step++) {
                double angle = 2 * Math.PI * step / 16;
                Buildings.Plan plan = Buildings.get()
                    .plan(
                        game,
                        world,
                        type,
                        (int) Math.round(x + Math.cos(angle) * ring),
                        (int) Math.round(z + Math.sin(angle) * ring),
                        step / 4);
                if (plan.problem == null) {
                    return plan;
                }
            }
        }
        return null;
    }

    /**
     * Walls its town in once it has an armory: a ring of wall from tower to tower round the town center, with no gap
     * but a gate in the middle of each side, laid out one side at a time when it has gold to spare. A side with a
     * building or water in the way is bent out round it; one it finds no room for it tries again now and then.
     * Whatever of its wall is knocked down it builds again first, spare or not, gates as gates.
     */
    private static void wall(Game game, WorldServer world, Team team, State state, Building hall,
        List<EntityUnit> workers, boolean spare, int now) {
        if (game.getAge(team) < 2 || count(game, team, BuildingType.ARMORY, true) == 0) {
            return;
        }
        for (Building building : game.getBuildings(team)) {
            if (building.type.isWall() && !building.isComplete()) {
                return;
            }
        }
        if (state.ring == null) {
            state.ring = spare ? ring(game, team, hall) : null;
            if (state.ring == null) {
                return;
            }
            state.wallBends = new int[state.ring.length][];
            state.wallTried = new int[state.ring.length];
            Arrays.fill(state.wallTried, now - WALL_RETRY_TICKS);
        }
        int sides = state.ring.length;
        if (now - state.lastBreachCheck >= BREACH_CHECK_TICKS) {
            state.lastBreachCheck = now;
            for (int side = 0; side < sides; side++) {
                int[] bend = state.wallBends[side];
                if (bend != null && layWall(game, world, team, state, workers, side, bend[0], bend[1], 0)) {
                    WarOfTheGods.LOG.info("AI {} is closing a breach in its wall", team.getName());
                    return;
                }
            }
        }
        if (!spare) {
            return;
        }
        for (int side = 0; side < sides; side++) {
            if (state.wallBends[side] != null || now - state.wallTried[side] < WALL_RETRY_TICKS) {
                continue;
            }
            int[] from = state.ring[side];
            int[] to = state.ring[(side + 1) % sides];
            // Out is away from the town center, square to the side.
            double ex = to[0] - from[0];
            double ez = to[1] - from[1];
            double length = Math.max(1, Math.sqrt(ex * ex + ez * ez));
            double nx = -ez / length;
            double nz = ex / length;
            if (((from[0] + to[0]) / 2.0 - hall.getCenterX()) * nx + ((from[1] + to[1]) / 2.0 - hall.getCenterZ()) * nz
                < 0) {
                nx = -nx;
                nz = -nz;
            }
            for (int offset : WALL_OFFSETS) {
                int ox = (int) Math.round(nx * offset);
                int oz = (int) Math.round(nz * offset);
                Buildings.WallPlan plan = Buildings.get()
                    .planWall(game, world, team, from[0], from[1], to[0], to[1], ox, oz, true);
                if (Buildings.WALL_THERE.equals(plan.problem)) {
                    state.wallBends[side] = new int[] { ox, oz };
                    return;
                }
                if (plan.problem != null) {
                    continue;
                }
                if (layWall(game, world, team, state, workers, side, ox, oz, SPARE_MARGIN)) {
                    state.wallBends[side] = new int[] { ox, oz };
                    WarOfTheGods.LOG.info("AI {} laid out side {} of its wall", team.getName(), side + 1);
                }
                // Laid out, or to be once it has the gold.
                return;
            }
            state.wallTried[side] = now;
            WarOfTheGods.LOG.info("AI {} found no room to wall side {} of its town", team.getName(), side + 1);
            return;
        }
    }

    /**
     * Lays out what is missing of one side of its wall, bent out by ox, oz, and sends workers to build it; the
     * segment in the middle of a new side, and any rebuilt where a gate stood, to be made a gate. False when there is
     * nothing missing, no room, or not the gold to keep margin of after.
     */
    private static boolean layWall(Game game, WorldServer world, Team team, State state, List<EntityUnit> workers,
        int side, int ox, int oz, long margin) {
        int[] from = state.ring[side];
        int[] to = state.ring[(side + 1) % state.ring.length];
        Buildings.WallPlan plan = Buildings.get()
            .planWall(game, world, team, from[0], from[1], to[0], to[1], ox, oz, true);
        if (plan.problem != null) {
            return false;
        }
        Map<Resource, Long> cost = Buildings.wallCost(plan.pieces.size());
        if (!affords(team, cost, margin)) {
            return false;
        }
        boolean fresh = state.wallBends[side] == null;
        team.spend(cost);
        List<Building> sites = Buildings.get()
            .startWall(game, team, plan);
        List<Building> segments = new ArrayList<>();
        for (Building site : sites) {
            // A gate takes a full segment, not a post or a short one.
            if (site.type == BuildingType.WALL && site.sizeX >= WallLine.SIZE && site.sizeZ >= WallLine.SIZE) {
                segments.add(site);
                for (int[] spot : state.gateSpots) {
                    if (spot[0] == site.minX && spot[1] == site.minZ) {
                        state.gates.add(site.id);
                    }
                }
            }
        }
        if (fresh && !segments.isEmpty()) {
            Building gate = segments.get(segments.size() / 2);
            state.gates.add(gate.id);
            state.gateSpots.add(new int[] { gate.minX, gate.minZ });
        }
        List<EntityUnit> builders = spareWorkers(workers, WALL_BUILDERS);
        for (int i = 0; i < sites.size(); i++) {
            Units.get()
                .orderBuild(builders, sites.get(i), i > 0);
        }
        WarOfTheGods.LOG.info(
            "AI {} laid out {} pieces of wall with {} workers",
            team.getName(),
            sites.size(),
            builders.size());
        return true;
    }

    /**
     * The ring to wall its town in along: the middles of its towers by the town center in order round it, or null
     * while there are too few, or all on one side of the town center so a ring through them would leave it out.
     */
    private static int[][] ring(Game game, Team team, Building hall) {
        double cx = hall.getCenterX();
        double cz = hall.getCenterZ();
        List<Building> towers = new ArrayList<>();
        for (Building tower : game.getBuildings(team)) {
            if (tower.type == BuildingType.TOWER && tower.isComplete() && tower.distanceTo(cx, cz) <= HOME_RANGE) {
                towers.add(tower);
            }
        }
        if (towers.size() < WALL_TOWERS) {
            return null;
        }
        towers.sort(
            Comparator.comparingDouble(tower -> Math.atan2(tower.getCenterZ() - cz, tower.getCenterX() - cx)));
        int[][] ring = new int[towers.size()][];
        for (int i = 0; i < towers.size(); i++) {
            Building tower = towers.get(i);
            Building next = towers.get((i + 1) % towers.size());
            double turn = Math.atan2(next.getCenterZ() - cz, next.getCenterX() - cx)
                - Math.atan2(tower.getCenterZ() - cz, tower.getCenterX() - cx);
            if (turn <= 0) {
                turn += 2 * Math.PI;
            }
            if (turn >= Math.PI) {
                return null;
            }
            ring[i] = new int[] { (int) Math.floor(tower.getCenterX()), (int) Math.floor(tower.getCenterZ()) };
        }
        return ring;
    }

    /** Makes the wall segments it meant for gates gates once they are built; forgets those that are gone. */
    private static void makeGates(Game game, WorldServer world, State state) {
        for (int i = state.gates.size() - 1; i >= 0; i--) {
            Building wall = game.getBuilding(state.gates.get(i));
            if (wall == null || wall.type != BuildingType.WALL) {
                state.gates.remove(i);
            } else if (Buildings.gateProblem(wall) == null) {
                Buildings.get()
                    .makeGate(game, world, wall);
                state.gates.remove(i);
            }
        }
    }

    /** The houses of the team's god. */
    private static BuildingType[] godsBuildings(Team team) {
        switch (team.getFaction()) {
            case ZAMORAK:
                return new BuildingType[] { BuildingType.CHAOS_TEMPLE };
            case BANDOS:
                return new BuildingType[] { BuildingType.BANDOS_TEMPLE };
            default:
                return new BuildingType[] { BuildingType.MONASTERY, BuildingType.WIZARDS_TOWER };
        }
    }

    /** The fortress of the team's god. */
    private static BuildingType fortressOf(Team team) {
        switch (team.getFaction()) {
            case ZAMORAK:
                return BuildingType.BLACK_KNIGHTS_FORTRESS;
            case BANDOS:
                return BuildingType.BANDOS_BASTION;
            default:
                return BuildingType.WHITE_KNIGHTS_FORTRESS;
        }
    }

    /** What the team builds for a house or a barracks: Bandos's goblin caves and war pits. */
    private static BuildingType own(Team team, BuildingType type) {
        if (team.getFaction() == Faction.BANDOS) {
            if (type == BuildingType.HOUSE) {
                return BuildingType.GOBLIN_CAVE;
            }
            if (type == BuildingType.BARRACKS) {
                return BuildingType.WAR_PIT;
            }
        }
        return type;
    }

    /** Whether the team's god lets it build the type at all. */
    private static boolean wants(Team team, BuildingType type) {
        return !type.shunnedBy(team.getFaction());
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
        boolean walls = count(game, team, BuildingType.WALL, false) > 0;
        for (Upgrade upgrade : RESEARCH_ORDER) {
            if (team.has(upgrade) || game.getAge(team) < upgrade.level
                || !walls && (upgrade == Upgrade.STONE_WALLS || upgrade == Upgrade.CITADEL_WALLS)) {
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

    /**
     * Sends a worker to every building site nobody is building, but to unbuilt wall only while fewer than a couple
     * are on it, as each goes on along the wall by itself.
     */
    private static void staffSites(Game game, Team team, List<EntityUnit> workers) {
        int onWalls = 0;
        for (EntityUnit worker : workers) {
            Building site = worker.getOrder() == EntityUnit.Order.BUILD ? game.getBuilding(worker.getBuildingId())
                : null;
            onWalls += site != null && site.type.isWall() && !site.isComplete() ? 1 : 0;
        }
        for (Building site : game.getBuildings(team)) {
            if (site.type.isWall() && !site.isComplete()) {
                if (onWalls < WALL_BUILDERS && assigned(workers, site) == 0) {
                    onWalls += Units.get()
                        .orderBuild(spareWorkers(workers, 1), site);
                }
                continue;
            }
            if (!site.isComplete() && Buildings.countBuilders(site) == 0 && assigned(workers, site) == 0) {
                Units.get()
                    .orderBuild(spareWorkers(workers, 1), site);
            }
        }
    }

    /**
     * Puts idle workers on whichever of food, wood and gold has the fewest workers for its share, and moves a worker
     * a look from what has the most workers over its share to what has the most too few, so the share comes right
     * again once what ran short, such as trees, is found.
     */
    private void gather(Game game, WorldServer world, Team team, Building hall, List<EntityUnit> workers, State state) {
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
            for (Resource resource : Resource.GATHERED) {
                if (best == null || on[resource.ordinal()] / share[resource.ordinal()]
                    < on[best.ordinal()] / share[best.ordinal()]) {
                    best = resource;
                }
            }
            // Where there is nothing of it to gather, the others in turn.
            Resource[] tries = { best, Resource.WOOD, Resource.GOLD, Resource.FOOD };
            for (Resource resource : tries) {
                if (sendTo(game, world, team, hall, worker, resource, state)) {
                    on[resource.ordinal()]++;
                    sent[resource.ordinal()]++;
                    break;
                }
            }
        }
        rebalance(game, world, team, hall, workers, state, on, share);
        if (sent[0] + sent[1] + sent[2] > 0) {
            WarOfTheGods.LOG.info(
                "AI {} put idle workers to work: {} on food, {} on wood, {} on gold",
                team.getName(),
                sent[Resource.FOOD.ordinal()],
                sent[Resource.WOOD.ordinal()],
                sent[Resource.GOLD.ordinal()]);
        }
    }

    private void rebalance(Game game, WorldServer world, Team team, Building hall, List<EntityUnit> workers,
        State state, int[] on, double[] share) {
        int total = on[0] + on[1] + on[2];
        Resource over = null;
        Resource under = null;
        for (Resource resource : Resource.GATHERED) {
            double off = on[resource.ordinal()] - share[resource.ordinal()] * total;
            if (off >= 2 && (over == null || off > on[over.ordinal()] - share[over.ordinal()] * total)) {
                over = resource;
            }
            if (off <= -2 && (under == null || off < on[under.ordinal()] - share[under.ordinal()] * total)) {
                under = resource;
            }
        }
        if (over == null || under == null) {
            return;
        }
        for (EntityUnit worker : workers) {
            // Not one carrying home what it has gathered.
            if (resourceOf(worker) == over && !worker.isDelivering()) {
                if (sendTo(game, world, team, hall, worker, under, state)) {
                    WarOfTheGods.LOG.info(
                        "AI {} moved a worker from {} to {}",
                        team.getName(),
                        over.displayName.toLowerCase(),
                        under.displayName.toLowerCase());
                }
                return;
            }
        }
    }

    private boolean sendTo(Game game, WorldServer world, Team team, Building hall, EntityUnit worker, Resource resource,
        State state) {
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
                int[] tree = nearestTree(game, world, team, x, z, state);
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

    /**
     * The tree it chops next: the nearest to the town center, else by one of its storehouses, else round trees it
     * found further off, looking for those now and then as far again; or null.
     */
    private int[] nearestTree(Game game, WorldServer world, Team team, double x, double z, State state) {
        int[] tree = Trees.nearest(world, game, x, z, TREE_RANGE);
        if (tree != null) {
            return tree;
        }
        for (Building store : game.getBuildings(team)) {
            if (store.type == BuildingType.STOREHOUSE && store.isComplete()) {
                tree = Trees.nearest(world, game, store.getCenterX(), store.getCenterZ(), STORE_TREE_RANGE);
                if (tree != null) {
                    return tree;
                }
            }
        }
        if (state.forest != null) {
            tree = Trees.nearest(world, game, state.forest[0] + 0.5, state.forest[2] + 0.5, FOREST_RANGE);
            state.forest = tree;
            if (tree != null) {
                return tree;
            }
        }
        if (ticks - state.lastFarTreeSearch < FAR_TREE_TICKS) {
            return null;
        }
        state.lastFarTreeSearch = ticks;
        state.forest = Trees.nearest(world, game, x, z, FAR_TREE_RANGE);
        if (state.forest != null) {
            WarOfTheGods.LOG.info(
                "AI {} found trees {} blocks from its town center",
                team.getName(),
                (int) Math.sqrt(
                    (state.forest[0] - x) * (state.forest[0] - x) + (state.forest[2] - z) * (state.forest[2] - z)));
        }
        return state.forest;
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

    /** Priests at each chaos temple, as many as pray there for favor, and monks at a monastery. */
    private static final int PRIESTS_WANTED = Buildings.PRIESTS_PER_TEMPLE;
    private static final int MONKS_WANTED = 4;

    /**
     * Trains chaos priests to pray at its chaos temples, goblin priests at its Bandos temples, or monks at its
     * monastery, for its god's favor.
     */
    private static void trainWorshippers(Game game, Team team) {
        for (Building building : game.getBuildings(team)) {
            UnitKind kind = building.type == BuildingType.CHAOS_TEMPLE ? UnitKind.CHAOS_PRIEST
                : building.type == BuildingType.BANDOS_TEMPLE ? UnitKind.GOBLIN_PRIEST
                    : building.type == BuildingType.MONASTERY ? UnitKind.MONK : null;
            if (kind == null || !building.isComplete()
                || building.getQueue()
                    .size() >= QUEUE
                || Buildings.room(game, team) <= 0) {
                continue;
            }
            int wanted = kind == UnitKind.MONK ? MONKS_WANTED : PRIESTS_WANTED * count(game, team, building.type, true);
            int have = queued(game, team, kind);
            for (EntityUnit unit : Units.get()
                .getUnits(team)) {
                have += unit.getKind() == kind ? 1 : 0;
            }
            if (have < wanted && team.spend(kind.cost())) {
                building.enqueue(kind, true);
            }
        }
    }

    // ---------------------------------------------------------------- army

    /**
     * Trains warriors at its barracks, archers at its archery range, cavalry at its stables, dark wizards and now and
     * then a Zamorak mage at a chaos temple, blue wizards at a wizards' tower, centaurs at a monastery whenever its
     * monks have earned the favor, goblins at a goblin cave, hobgoblins, axe-throwers and orks at a war pit, and at a
     * fortress its heroes when it lacks one, and its own fighters otherwise.
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
            UnitKind trains = building.type == BuildingType.BARRACKS ? UnitKind.SOLDIER
                : building.type == BuildingType.ARCHERY_RANGE ? UnitKind.ARCHER
                    : building.type == BuildingType.STABLES ? UnitKind.CAVALRY
                        : building.type == BuildingType.CHAOS_TEMPLE ? (ThreadLocalRandom.current()
                            .nextInt(3) == 0 ? UnitKind.ZAMORAK_MAGE : UnitKind.DARK_WIZARD)
                            : building.type == BuildingType.WIZARDS_TOWER ? UnitKind.BLUE_WIZARD
                                : building.type == BuildingType.GOBLIN_CAVE ? UnitKind.GOBLIN
                                    : building.type == BuildingType.WAR_PIT ? warPitFighter() : null;
            if (trains != null) {
                if (team.spend(trains.cost())) {
                    building.enqueue(trains, true);
                }
            } else if (building.type == BuildingType.MONASTERY) {
                int centaurs = queued(game, team, UnitKind.CENTAUR);
                for (EntityUnit unit : Units.get()
                    .getUnits(team)) {
                    centaurs += unit.getKind() == UnitKind.CENTAUR ? 1 : 0;
                }
                if (centaurs < MAX_CENTAURS && team.spend(UnitKind.CENTAUR.cost())) {
                    building.enqueue(UnitKind.CENTAUR, true);
                    WarOfTheGods.LOG.info("AI {} is training a centaur", team.getName());
                }
            } else if (building.type.isFortress()) {
                // The hero first, whenever it has none.
                List<UnitKind> kinds = new ArrayList<>(building.type.trains);
                kinds.sort((a, b) -> Boolean.compare(b.hero, a.hero));
                for (UnitKind kind : kinds) {
                    if (game.heroRoom(team, kind) > 0 && kind.unlockedFor(team) && team.spend(kind.cost())) {
                        building.enqueue(kind, true);
                        WarOfTheGods.LOG.info("AI {} is training {}", team.getName(), kind.displayName);
                        break;
                    }
                }
            }
        }
    }

    /** Mostly hobgoblins and goblin axe-throwers from a war pit, and now and then an ork. */
    private static UnitKind warPitFighter() {
        int roll = ThreadLocalRandom.current()
            .nextInt(5);
        return roll < 2 ? UnitKind.HOBGOBLIN : roll < 4 ? UnitKind.GOBLIN_THROWER : UnitKind.ORK;
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
            state.march.clear();
            List<EntityUnit> defenders = new ArrayList<>(Units.isMob(intruder) ? home : fighters);
            defenders.removeIf(unit -> unit.getOrder() == EntityUnit.Order.ATTACK || unit.isAttackMoving());
            if (!defenders.isEmpty()) {
                Units.get()
                    .orderAttackMove(defenders, intruder.posX, intruder.posZ, Units.Formation.BOX);
            }
            return;
        }
        state.march.retainAll(fighters);
        march(game, team, state);
        home.removeIf(unit -> unit.getOrder() != EntityUnit.Order.IDLE || state.march.contains(unit));
        if (home.size() >= FIRST_WAVE + WAVE_GROWTH * state.waves && ticks - state.lastWave >= WAVE_TICKS) {
            Building target = nearestEnemyBuilding(game, team, state, x, z);
            if (target != null) {
                Units.Formation formation = WAVE_FORMATIONS[state.waves % WAVE_FORMATIONS.length];
                if (formation == null) {
                    Units.get()
                        .orderAttackMove(home, target.getCenterX(), target.getCenterZ(), Units.Formation.BOX);
                } else {
                    state.march.clear();
                    state.march.addAll(home);
                    state.marchFormation = formation;
                    march(game, team, state);
                }
                state.waves++;
                state.lastWave = ticks;
                WarOfTheGods.LOG
                    .info("AI {} is attacking {} with {} units", team.getName(), target.describe(game), home.size());
            }
        }
        away.removeIf(unit -> unit.getOrder() != EntityUnit.Order.IDLE || state.march.contains(unit));
        if (away.isEmpty()) {
            return;
        }
        double awayX = 0;
        double awayZ = 0;
        for (EntityUnit fighter : away) {
            awayX += fighter.posX / away.size();
            awayZ += fighter.posZ / away.size();
        }
        Building target = nearestEnemyBuilding(game, team, state, awayX, awayZ);
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

    /**
     * Moves a marching army on in its formation: a short way toward the enemy building nearest it at each look, the
     * formation facing the way it goes and fighting what it meets, so it keeps together as it walks; near enough, it
     * charges the building and stops marching.
     */
    private static void march(Game game, Team team, State state) {
        if (state.march.isEmpty()) {
            return;
        }
        double cx = 0;
        double cz = 0;
        for (EntityUnit unit : state.march) {
            cx += unit.posX / state.march.size();
            cz += unit.posZ / state.march.size();
        }
        Building target = nearestEnemyBuilding(game, team, state, cx, cz);
        if (target == null) {
            state.march.clear();
            return;
        }
        double dx = target.getCenterX() - cx;
        double dz = target.getCenterZ() - cz;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (target.distanceTo(cx, cz) <= CHARGE_RANGE || distance <= MARCH_LEG) {
            Units.get()
                .orderAttackMove(
                    new ArrayList<>(state.march),
                    target.getCenterX(),
                    target.getCenterZ(),
                    state.marchFormation);
            state.march.clear();
            return;
        }
        // Those still fighting what they met catch up when they are done.
        List<EntityUnit> walking = new ArrayList<>(state.march);
        walking.removeIf(unit -> unit.getOrder() == EntityUnit.Order.ATTACK);
        Units.get()
            .orderAttackMove(
                walking,
                cx + dx / distance * MARCH_LEG,
                cz + dz / distance * MARCH_LEG,
                state.marchFormation);
    }

    /** The enemy building it has seen nearest x, z, a town center if it knows of any; or null. */
    private static Building nearestEnemyBuilding(Game game, Team team, State state, double x, double z) {
        Building best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Building building : game.getBuildings()) {
            Team owner = game.getTeam(building.team);
            if (owner == null || owner.isDefeated()
                || !game.areEnemies(team, owner)
                || !state.knownBuildings.contains(building.id)) {
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
