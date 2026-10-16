package com.warofthegods.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumChatFormatting;

import com.warofthegods.Config;

/**
 * One RTS game: its map area and terrain seed, its teams and the alliances between them. Only one game runs at a time;
 * it lasts until an op starts a new one or ends it.
 */
public class Game {

    private final int number;
    private final long startedAt;
    private final int centerX;
    private final int centerZ;
    private final int size;
    /** Seed for the terrain of this game's world. */
    private final long seed;
    private final Map<String, Team> teams = new LinkedHashMap<>();
    /** Allied team pairs, each stored as "a|b" with the two team ids sorted. */
    private final TreeSet<String> alliances = new TreeSet<>();
    /** Every unit of the game by its entity UUID, with the chunk it was last seen in. */
    private final Map<UUID, UnitRecord> units = new LinkedHashMap<>();
    /** Every building and building site by id. */
    private final Map<Integer, Building> buildings = new LinkedHashMap<>();
    private int lastBuildingId;
    /** Trees workers have started chopping, by {@link Tree#key()}. */
    private final Map<Long, Tree> trees = new LinkedHashMap<>();
    /** Spots town centers stand on, by id. */
    private final Map<Integer, Settlement> settlements = new LinkedHashMap<>();
    /** Gold mines on the map, by id. */
    private final Map<Integer, GoldMine> mines = new LinkedHashMap<>();
    /** Old ruins on the map, by id. */
    private final Map<Integer, Ruin> ruins = new LinkedHashMap<>();
    private int lastFeatureId;
    /** Whether the neutral settlements and the gold mines have been laid out on the map. */
    private boolean featuresPlaced;
    /** How many teams the map was laid out for, which places its river and bridges; 0 for maps from before. */
    private int mapTeams;
    /** Whether the bridges are in the world. */
    private boolean bridgesPlaced;
    /** Whether a winner has been decided; nobody is defeated after that. */
    private boolean over;
    /** Ids of the teams that won, once the game is over; empty if nobody did. */
    private final List<String> winners = new ArrayList<>();
    private Runnable changeListener = () -> {};

    public Game(int number, long startedAt, int centerX, int centerZ, int size, long seed) {
        this.number = number;
        this.startedAt = startedAt;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.size = size;
        this.seed = seed;
    }

    void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    void changed() {
        changeListener.run();
    }

    public int getNumber() {
        return number;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public int getSize() {
        return size;
    }

    public long getSeed() {
        return seed;
    }

    public int getMinX() {
        return centerX - size / 2;
    }

    public int getMinZ() {
        return centerZ - size / 2;
    }

    public int getMaxX() {
        return getMinX() + size - 1;
    }

    public int getMaxZ() {
        return getMinZ() + size - 1;
    }

    public boolean isInside(int x, int z) {
        return x >= getMinX() && x <= getMaxX() && z >= getMinZ() && z <= getMaxZ();
    }

    public Collection<Team> getTeams() {
        return Collections.unmodifiableCollection(teams.values());
    }

    public Team getTeam(String name) {
        return teams.get(Team.idOf(name));
    }

    /** Returns the new team, or null if a team with that name already exists. */
    public Team addTeam(String name, EnumChatFormatting color, long[] startingResources) {
        if (getTeam(name) != null) {
            return null;
        }
        Team team = new Team(this, name, color);
        for (Resource resource : Resource.values()) {
            team.setResource(resource, startingResources[resource.ordinal()]);
        }
        teams.put(team.getId(), team);
        changed();
        return team;
    }

    /** Adds a team named after the next free color, such as "Red" or "Blue". */
    public Team addDefaultTeam(long[] startingResources) {
        EnumChatFormatting color = nextFreeColor();
        String name = colorDisplayName(color);
        for (int i = 2; getTeam(name) != null; i++) {
            name = colorDisplayName(color) + i;
        }
        return addTeam(name, color, startingResources);
    }

    /** LIGHT_PURPLE becomes LightPurple. */
    public static String colorDisplayName(EnumChatFormatting color) {
        StringBuilder name = new StringBuilder();
        for (String word : color.name()
            .split("_")) {
            name.append(word.charAt(0))
                .append(
                    word.substring(1)
                        .toLowerCase());
        }
        return name.toString();
    }

    public boolean removeTeam(String name) {
        Team team = teams.remove(Team.idOf(name));
        if (team == null) {
            return false;
        }
        alliances.removeIf(pair -> pair.startsWith(team.getId() + "|") || pair.endsWith("|" + team.getId()));
        buildings.values()
            .removeIf(building -> building.team.equals(team.getId()));
        changed();
        return true;
    }

    /** The first color no team uses yet, or the first color if all are taken. */
    public EnumChatFormatting nextFreeColor() {
        for (EnumChatFormatting color : Team.COLORS) {
            boolean used = false;
            for (Team team : teams.values()) {
                used |= team.getColor() == color;
            }
            if (!used) {
                return color;
            }
        }
        return Team.COLORS[0];
    }

    /**
     * The team with the fewest players, the first such in order on a tie, leaving out defeated teams while there are
     * others, and teams the computer plays while there are teams it does not; null when there are no teams.
     */
    public Team getSmallestTeam() {
        boolean anyStanding = false;
        boolean anyHuman = false;
        for (Team team : teams.values()) {
            anyStanding |= !team.isDefeated();
            anyHuman |= !team.isAi();
        }
        Team smallest = null;
        for (Team team : teams.values()) {
            if ((anyStanding && team.isDefeated()) || (anyHuman && team.isAi())) {
                continue;
            }
            if (smallest == null || team.getMembers()
                .size()
                < smallest.getMembers()
                    .size()) {
                smallest = team;
            }
        }
        return smallest;
    }

    public Team getTeamOf(UUID player) {
        for (Team team : teams.values()) {
            if (team.getMembers()
                .containsKey(player)) {
                return team;
            }
        }
        return null;
    }

    /** Puts the player on the team, taking them off any team they were on before. */
    public void setTeamOf(UUID player, String playerName, Team team) {
        removePlayer(player);
        team.addMember(player, playerName);
        changed();
    }

    public Team removePlayer(UUID player) {
        Team team = getTeamOf(player);
        if (team != null) {
            team.removeMember(player);
            changed();
        }
        return team;
    }

    /** Keeps stored player names current when a player changes their name. */
    public void updatePlayerName(UUID player, String playerName) {
        Team team = getTeamOf(player);
        if (team != null && !playerName.equals(
            team.getMembers()
                .get(player))) {
            team.addMember(player, playerName);
            changed();
        }
    }

    private static String allianceKey(Team a, Team b) {
        String idA = a.getId();
        String idB = b.getId();
        return idA.compareTo(idB) < 0 ? idA + "|" + idB : idB + "|" + idA;
    }

    /** Returns false if the two teams were already allied or are the same team. */
    public boolean setAllied(Team a, Team b, boolean allied) {
        if (a == b) {
            return false;
        }
        boolean result = allied ? alliances.add(allianceKey(a, b)) : alliances.remove(allianceKey(a, b));
        if (result) {
            changed();
        }
        return result;
    }

    /** A team counts as allied with itself. */
    public boolean areAllied(Team a, Team b) {
        return a == b || alliances.contains(allianceKey(a, b));
    }

    public boolean areEnemies(Team a, Team b) {
        return !areAllied(a, b);
    }

    public List<Team> getAllies(Team team) {
        List<Team> allies = new ArrayList<>();
        for (Team other : teams.values()) {
            if (other != team && areAllied(team, other)) {
                allies.add(other);
            }
        }
        return allies;
    }

    /** All alliances as pairs of teams, in a stable order. */
    public List<Team[]> getAlliances() {
        List<Team[]> result = new ArrayList<>();
        for (String pair : alliances) {
            String[] ids = pair.split("\\|");
            result.add(new Team[] { teams.get(ids[0]), teams.get(ids[1]) });
        }
        return result;
    }

    public Collection<UnitRecord> getUnitRecords() {
        return Collections.unmodifiableCollection(units.values());
    }

    /** Remembers where a unit is; only marks the game changed when the unit is new or moved to another chunk. */
    public void updateUnitRecord(UUID id, String team, UnitKind kind, int chunkX, int chunkZ) {
        UnitRecord old = units.get(id);
        if (old == null || old.chunkX != chunkX || old.chunkZ != chunkZ || !old.team.equals(team) || old.kind != kind) {
            units.put(id, new UnitRecord(id, team, kind, chunkX, chunkZ));
            changed();
        }
    }

    public void removeUnitRecord(UUID id) {
        if (units.remove(id) != null) {
            changed();
        }
    }

    public int countUnits(Team team) {
        int count = 0;
        for (UnitRecord unit : units.values()) {
            count += unit.team.equals(team.getId()) ? 1 : 0;
        }
        return count;
    }

    /** The team's units of a kind, loaded or not. */
    public int countUnits(Team team, UnitKind kind) {
        int count = 0;
        for (UnitRecord unit : units.values()) {
            count += unit.team.equals(team.getId()) && unit.kind == kind ? 1 : 0;
        }
        return count;
    }

    /**
     * How many more of a kind the team may train now, by its rule rather than its room: a hero only when it has none
     * alive or in training, anything else as many as it likes.
     */
    public int heroRoom(Team team, UnitKind kind) {
        if (!kind.hero) {
            return Integer.MAX_VALUE;
        }
        return countUnits(team, kind) + countQueued(team, kind) > 0 ? 0 : 1;
    }

    /**
     * The team's workers, loaded or not. A unit saved before kinds were remembered counts until it is next seen, so a
     * team is never thought to have none while its units are still loading.
     */
    public int countWorkers(Team team) {
        int count = 0;
        for (UnitRecord unit : units.values()) {
            if (unit.team.equals(team.getId()) && (unit.kind == null || unit.kind == UnitKind.WORKER)) {
                count++;
            }
        }
        return count;
    }

    public Collection<Building> getBuildings() {
        return Collections.unmodifiableCollection(buildings.values());
    }

    public Building getBuilding(int id) {
        return buildings.get(id);
    }

    public List<Building> getBuildings(Team team) {
        List<Building> result = new ArrayList<>();
        for (Building building : buildings.values()) {
            if (building.team.equals(team.getId())) {
                result.add(building);
            }
        }
        return result;
    }

    /** A team's age, 1 to 3. It stays when the team loses its town centers. */
    public int getAge(Team team) {
        return team.getAge();
    }

    /** The team's first town center, or null if it has none. */
    public Building getTownHall(Team team) {
        for (Building building : buildings.values()) {
            if (building.team.equals(team.getId()) && building.type == BuildingType.TOWN_HALL) {
                return building;
            }
        }
        return null;
    }

    /** How many finished town centers the team has. */
    public int countTownCenters(Team team) {
        int count = 0;
        for (Building building : buildings.values()) {
            if (building.team.equals(team.getId()) && building.type == BuildingType.TOWN_HALL
                && building.isComplete()) {
                count++;
            }
        }
        return count;
    }

    public boolean isOver() {
        return over;
    }

    /** The teams that won, once the game is over; empty if nobody did or it is not over. */
    public List<Team> getWinners() {
        List<Team> result = new ArrayList<>();
        for (String id : winners) {
            Team team = teams.get(id);
            if (team != null) {
                result.add(team);
            }
        }
        return result;
    }

    /** Ends the game with the given teams as winners. */
    public void setOver(List<Team> winningTeams) {
        over = true;
        winners.clear();
        for (Team team : winningTeams) {
            winners.add(team.getId());
        }
        changed();
    }

    /** The building whose footprint holds x, z, or null. */
    public Building getBuildingAt(double x, double z) {
        for (Building building : buildings.values()) {
            if (building.covers(x, z, 0)) {
                return building;
            }
        }
        return null;
    }

    /** Adds a building or building site with the next free id. */
    public Building addBuilding(BuildingType type, Team team, int minX, int minZ, int sizeX, int sizeZ, int groundY,
        int rotation, boolean complete) {
        lastBuildingId++;
        Building building = new Building(
            this,
            lastBuildingId,
            type,
            team.getId(),
            minX,
            minZ,
            sizeX,
            sizeZ,
            groundY,
            rotation,
            complete);
        buildings.put(building.id, building);
        changed();
        return building;
    }

    public void removeBuilding(Building building) {
        if (buildings.remove(building.id) != null) {
            changed();
        }
    }

    public Collection<Settlement> getSettlements() {
        return Collections.unmodifiableCollection(settlements.values());
    }

    public Settlement addSettlement(int minX, int minZ, int sizeX, int sizeZ, int groundY, int rotation) {
        lastFeatureId++;
        Settlement settlement = new Settlement(lastFeatureId, minX, minZ, sizeX, sizeZ, groundY, rotation);
        settlements.put(settlement.id, settlement);
        changed();
        return settlement;
    }

    public Collection<Ruin> getRuins() {
        return Collections.unmodifiableCollection(ruins.values());
    }

    public Ruin addRuin(int minX, int minZ, int sizeX, int sizeZ, int groundY, String design, int haunt) {
        lastFeatureId++;
        Ruin ruin = new Ruin(lastFeatureId, minX, minZ, sizeX, sizeZ, groundY, design, haunt);
        ruins.put(ruin.id, ruin);
        changed();
        return ruin;
    }

    /** The settlement the building stands on, or null. */
    public Settlement settlementOf(Building building) {
        for (Settlement settlement : settlements.values()) {
            if (settlement.holds(building)) {
                return settlement;
            }
        }
        return null;
    }

    /** The town center or town center site on a settlement, or null when it is free. */
    public Building townCenterOn(Settlement settlement) {
        for (Building building : buildings.values()) {
            if (building.type == BuildingType.TOWN_HALL && settlement.holds(building)) {
                return building;
            }
        }
        return null;
    }

    /** The free settlement whose footprint holds x, z or comes within margin blocks of it, nearest first; or null. */
    public Settlement freeSettlementNear(double x, double z, double margin) {
        Settlement best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Settlement settlement : settlements.values()) {
            if (!settlement.covers(x, z, margin) || townCenterOn(settlement) != null) {
                continue;
            }
            double dx = settlement.getCenterX() - x;
            double dz = settlement.getCenterZ() - z;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                best = settlement;
                bestDistance = distance;
            }
        }
        return best;
    }

    public Collection<GoldMine> getMines() {
        return Collections.unmodifiableCollection(mines.values());
    }

    public GoldMine getMine(int id) {
        return mines.get(id);
    }

    public GoldMine addMine(int minX, int minZ, int size, int groundY, int gold) {
        lastFeatureId++;
        GoldMine mine = new GoldMine(lastFeatureId, minX, minZ, size, groundY, gold);
        mines.put(mine.id, mine);
        changed();
        return mine;
    }

    /** The gold mine whose footprint holds x, z or comes within margin blocks of it, or null. */
    public GoldMine mineAt(double x, double z, double margin) {
        for (GoldMine mine : mines.values()) {
            if (mine.covers(x, z, margin)) {
                return mine;
            }
        }
        return null;
    }

    /** Takes gold out of a mine; returns how much it gave. */
    public int mine(GoldMine mine, int amount) {
        int taken = mine.take(amount);
        changed();
        return taken;
    }

    public int getMapTeams() {
        return mapTeams;
    }

    public void setMapTeams(int mapTeams) {
        this.mapTeams = mapTeams;
        changed();
    }

    public boolean areBridgesPlaced() {
        return bridgesPlaced;
    }

    public void setBridgesPlaced() {
        bridgesPlaced = true;
        changed();
    }

    public boolean areFeaturesPlaced() {
        return featuresPlaced;
    }

    public void setFeaturesPlaced() {
        featuresPlaced = true;
        changed();
    }

    /** Units the team's finished buildings house, up to the most any team may have. */
    public int getPopulationCap(Team team) {
        int cap = 0;
        for (Building building : buildings.values()) {
            if (building.team.equals(team.getId()) && building.isComplete()) {
                cap += building.type.population(building.getLevel());
            }
        }
        return Math.min(cap, Config.unitsPerTeam);
    }

    public Collection<Tree> getTrees() {
        return Collections.unmodifiableCollection(trees.values());
    }

    public Tree getTree(int x, int y, int z) {
        return trees.get(Tree.key(x, y, z));
    }

    /** The tree with its trunk's foot at x, y, z, tracked from now on if it was not yet. */
    public Tree trackTree(int x, int y, int z, int top, int wood) {
        Tree tree = trees.get(Tree.key(x, y, z));
        if (tree == null) {
            tree = new Tree(x, y, z, top, wood);
            trees.put(tree.key(), tree);
            changed();
        }
        return tree;
    }

    /** Takes wood out of a tree; returns how much it gave. */
    public int chopTree(Tree tree, int amount) {
        int taken = tree.take(amount);
        changed();
        return taken;
    }

    public void removeTree(Tree tree) {
        if (trees.remove(tree.key()) != null) {
            changed();
        }
    }

    /** Units the team has waiting in training queues. */
    /** Units of a kind the team has waiting to train. */
    public int countQueued(Team team, UnitKind kind) {
        int count = 0;
        for (Building building : buildings.values()) {
            if (building.team.equals(team.getId())) {
                for (UnitKind queued : building.getQueue()) {
                    count += queued == kind ? 1 : 0;
                }
            }
        }
        return count;
    }

    public int countQueued(Team team) {
        int count = 0;
        for (Building building : buildings.values()) {
            if (building.team.equals(team.getId())) {
                count += building.getQueue()
                    .size();
            }
        }
        return count;
    }

    NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("number", number);
        tag.setLong("startedAt", startedAt);
        tag.setInteger("centerX", centerX);
        tag.setInteger("centerZ", centerZ);
        tag.setInteger("size", size);
        tag.setLong("seed", seed);

        NBTTagList teamList = new NBTTagList();
        for (Team team : teams.values()) {
            teamList.appendTag(team.writeToNBT());
        }
        tag.setTag("teams", teamList);

        NBTTagList allianceList = new NBTTagList();
        for (String pair : alliances) {
            allianceList.appendTag(new NBTTagString(pair));
        }
        tag.setTag("alliances", allianceList);

        NBTTagList unitList = new NBTTagList();
        for (UnitRecord unit : units.values()) {
            unitList.appendTag(unit.writeToNBT());
        }
        tag.setTag("units", unitList);

        NBTTagList buildingList = new NBTTagList();
        for (Building building : buildings.values()) {
            buildingList.appendTag(building.writeToNBT());
        }
        tag.setTag("buildings", buildingList);
        tag.setInteger("lastBuildingId", lastBuildingId);
        NBTTagList treeList = new NBTTagList();
        for (Tree tree : trees.values()) {
            treeList.appendTag(tree.writeToNBT());
        }
        tag.setTag("trees", treeList);
        NBTTagList settlementList = new NBTTagList();
        for (Settlement settlement : settlements.values()) {
            settlementList.appendTag(settlement.writeToNBT());
        }
        tag.setTag("settlements", settlementList);
        NBTTagList mineList = new NBTTagList();
        for (GoldMine mine : mines.values()) {
            mineList.appendTag(mine.writeToNBT());
        }
        tag.setTag("mines", mineList);
        NBTTagList ruinList = new NBTTagList();
        for (Ruin ruin : ruins.values()) {
            ruinList.appendTag(ruin.writeToNBT());
        }
        tag.setTag("ruins", ruinList);
        tag.setInteger("lastFeatureId", lastFeatureId);
        tag.setBoolean("featuresPlaced", featuresPlaced);
        tag.setInteger("mapTeams", mapTeams);
        tag.setBoolean("bridgesPlaced", bridgesPlaced);
        tag.setBoolean("over", over);
        NBTTagList winnerList = new NBTTagList();
        for (String id : winners) {
            winnerList.appendTag(new NBTTagString(id));
        }
        tag.setTag("winners", winnerList);
        return tag;
    }

    static Game readFromNBT(NBTTagCompound tag) {
        Game game = new Game(
            tag.getInteger("number"),
            tag.getLong("startedAt"),
            tag.getInteger("centerX"),
            tag.getInteger("centerZ"),
            tag.getInteger("size"),
            tag.getLong("seed"));

        NBTTagList teamList = tag.getTagList("teams", 10);
        for (int i = 0; i < teamList.tagCount(); i++) {
            Team team = Team.readFromNBT(game, teamList.getCompoundTagAt(i));
            game.teams.put(team.getId(), team);
        }

        NBTTagList allianceList = tag.getTagList("alliances", 8);
        for (int i = 0; i < allianceList.tagCount(); i++) {
            String pair = allianceList.getStringTagAt(i);
            String[] ids = pair.split("\\|");
            if (ids.length == 2 && game.teams.containsKey(ids[0]) && game.teams.containsKey(ids[1])) {
                game.alliances.add(pair);
            }
        }

        NBTTagList unitList = tag.getTagList("units", 10);
        for (int i = 0; i < unitList.tagCount(); i++) {
            UnitRecord unit = UnitRecord.readFromNBT(unitList.getCompoundTagAt(i));
            game.units.put(unit.id, unit);
        }

        NBTTagList buildingList = tag.getTagList("buildings", 10);
        for (int i = 0; i < buildingList.tagCount(); i++) {
            Building building = Building.readFromNBT(game, buildingList.getCompoundTagAt(i));
            if (building != null && game.teams.containsKey(building.team)) {
                game.buildings.put(building.id, building);
            }
        }
        game.lastBuildingId = tag.getInteger("lastBuildingId");
        NBTTagList treeList = tag.getTagList("trees", 10);
        for (int i = 0; i < treeList.tagCount(); i++) {
            Tree tree = Tree.readFromNBT(treeList.getCompoundTagAt(i));
            if (tree != null) {
                game.trees.put(tree.key(), tree);
            }
        }
        NBTTagList settlementList = tag.getTagList("settlements", 10);
        for (int i = 0; i < settlementList.tagCount(); i++) {
            Settlement settlement = Settlement.readFromNBT(settlementList.getCompoundTagAt(i));
            if (settlement != null) {
                game.settlements.put(settlement.id, settlement);
            }
        }
        NBTTagList mineList = tag.getTagList("mines", 10);
        for (int i = 0; i < mineList.tagCount(); i++) {
            GoldMine mine = GoldMine.readFromNBT(mineList.getCompoundTagAt(i));
            if (mine != null) {
                game.mines.put(mine.id, mine);
            }
        }
        NBTTagList ruinList = tag.getTagList("ruins", 10);
        for (int i = 0; i < ruinList.tagCount(); i++) {
            Ruin ruin = Ruin.readFromNBT(ruinList.getCompoundTagAt(i));
            if (ruin != null) {
                game.ruins.put(ruin.id, ruin);
            }
        }
        game.lastFeatureId = tag.getInteger("lastFeatureId");
        game.featuresPlaced = tag.getBoolean("featuresPlaced");
        game.mapTeams = tag.getInteger("mapTeams");
        game.bridgesPlaced = tag.getBoolean("bridgesPlaced");
        game.over = tag.getBoolean("over");
        NBTTagList winnerList = tag.getTagList("winners", 8);
        for (int i = 0; i < winnerList.tagCount(); i++) {
            game.winners.add(winnerList.getStringTagAt(i));
        }
        for (Team team : game.teams.values()) {
            if (team.getAge() == 0) {
                // Saved before ages belonged to teams: the age was the level of the town center.
                Building center = game.getTownHall(team);
                team.setAgeQuietly(center == null || !center.isComplete() ? 1 : center.getLevel());
            }
            if (game.countTownCenters(team) > 0) {
                team.setHadTownCenter();
            }
        }
        return game;
    }
}
