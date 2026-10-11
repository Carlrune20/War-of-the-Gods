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

/**
 * One RTS game: its map area, its teams and the alliances between them. Only one game runs at a time; it lasts until
 * an op starts a new one or ends it.
 */
public class Game {

    private final int number;
    private final long startedAt;
    private final int centerX;
    private final int centerZ;
    private final int size;
    private final Map<String, Team> teams = new LinkedHashMap<>();
    /** Allied team pairs, each stored as "a|b" with the two team ids sorted. */
    private final TreeSet<String> alliances = new TreeSet<>();
    private Runnable changeListener = () -> {};

    public Game(int number, long startedAt, int centerX, int centerZ, int size) {
        this.number = number;
        this.startedAt = startedAt;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.size = size;
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

    NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("number", number);
        tag.setLong("startedAt", startedAt);
        tag.setInteger("centerX", centerX);
        tag.setInteger("centerZ", centerZ);
        tag.setInteger("size", size);

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
        return tag;
    }

    static Game readFromNBT(NBTTagCompound tag) {
        Game game = new Game(
            tag.getInteger("number"),
            tag.getLong("startedAt"),
            tag.getInteger("centerX"),
            tag.getInteger("centerZ"),
            tag.getInteger("size"));

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
        return game;
    }
}
