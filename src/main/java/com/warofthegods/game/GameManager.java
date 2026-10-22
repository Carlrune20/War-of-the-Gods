package com.warofthegods.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.unit.Hunting;
import com.warofthegods.world.GameWorld;

/** Entry point for finding, starting and ending the current game. */
public final class GameManager {

    private GameManager() {}

    public static GameSavedData getData() {
        WorldServer overworld = MinecraftServer.getServer()
            .worldServerForDimension(0);
        GameSavedData data = (GameSavedData) overworld.mapStorage.loadData(GameSavedData.class, GameSavedData.KEY);
        if (data == null) {
            data = new GameSavedData(GameSavedData.KEY);
            overworld.mapStorage.setData(GameSavedData.KEY, data);
        }
        return data;
    }

    /** The running game, or null if none has been started or there is no server here (a client on a server). */
    public static Game getGame() {
        if (MinecraftServer.getServer() == null) {
            return null;
        }
        return getData().getGame();
    }

    /**
     * Ends any running game and starts a new one with the given number of teams, the gods taking turns. The map is
     * centred on 0,0 of the game world, which is rebuilt from the seed.
     */
    public static Game newGame(int size, int teamCount, long seed) {
        return newGame(size, teamCount, seed, GameMap.MISTHALIN);
    }

    /** As {@link #newGame(int, int, long)}, on the given map. */
    public static Game newGame(int size, int teamCount, long seed, GameMap map) {
        List<TeamSetup> teams = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) {
            TeamSetup setup = new TeamSetup(Team.COLORS[i % Team.COLORS.length]);
            // The gods take turns, so each follows a third of the teams: Red Zamorak, Blue Saradomin, Green Bandos...
            setup.faction = new Faction[] { Faction.ZAMORAK, Faction.SARADOMIN, Faction.BANDOS }[i % 3];
            teams.add(setup);
        }
        return newGame(size, teams, seed, 0, map);
    }

    /**
     * How one team of a new game is set up: its color, its god, whether the computer plays it, its alliance and its
     * players.
     */
    public static final class TeamSetup {

        public final EnumChatFormatting color;
        public Faction faction;
        public boolean ai;
        /** Teams with the same alliance, other than 0, start the game allied. */
        public int alliance;
        /** Player UUID to name. */
        public final Map<UUID, String> players = new LinkedHashMap<>();

        public TeamSetup(EnumChatFormatting color) {
            this.color = color;
            this.faction = Faction.forColor(color);
        }
    }

    /**
     * Ends any running game and starts a new one with the given teams, each named after its color. The map is centred
     * on 0,0 of the game world, which is rebuilt from the seed.
     */
    public static Game newGame(int size, List<TeamSetup> teams, long seed) {
        return newGame(size, teams, seed, 0);
    }

    /**
     * Ends any running game and starts a new one with the given teams, each of up to the given number of units (0 for
     * the configured number).
     */
    public static Game newGame(int size, List<TeamSetup> teams, long seed, int unitsPerTeam) {
        return newGame(size, teams, seed, unitsPerTeam, GameMap.MISTHALIN);
    }

    /** Ends any running game and starts a new one on the given map. */
    public static Game newGame(int size, List<TeamSetup> teams, long seed, int unitsPerTeam, GameMap map) {
        GameSavedData data = getData();
        Game game = new Game(data.getLastGameNumber() + 1, System.currentTimeMillis(), 0, 0, size, seed);
        game.setUnitsPerTeam(unitsPerTeam);
        game.setMap(map);
        int teamCount = teams.size();
        List<Team> made = new ArrayList<>();
        for (TeamSetup setup : teams) {
            String name = Game.colorDisplayName(setup.color);
            for (int i = 2; game.getTeam(name) != null; i++) {
                name = Game.colorDisplayName(setup.color) + i;
            }
            Team team = game.addTeam(name, setup.color, Config.startingResources);
            team.setFaction(setup.faction);
            team.setAi(setup.ai);
            made.add(team);
            for (Map.Entry<UUID, String> player : setup.players.entrySet()) {
                game.setTeamOf(player.getKey(), player.getValue(), team);
            }
        }
        for (int i = 0; i < made.size(); i++) {
            for (int j = i + 1; j < made.size(); j++) {
                int alliance = teams.get(i).alliance;
                if (alliance != 0 && alliance == teams.get(j).alliance) {
                    game.setAllied(made.get(i), made.get(j), true);
                }
            }
        }
        game.setMapTeams(teamCount);
        data.setGame(game);
        WarOfTheGods.LOG.info(
            "Started game #{} on {} with size {}, {} teams and seed {}",
            game.getNumber(),
            map.displayName,
            size,
            teamCount,
            seed);
        GameWorld.rebuild();
        Hunting.clear();
        resetDay();
        return game;
    }

    /**
     * Every game starts at the same time of day under a clear sky. The game map takes its time and weather from the
     * overworld.
     */
    private static void resetDay() {
        MinecraftServer server = MinecraftServer.getServer();
        for (WorldServer world : server.worldServers) {
            world.setWorldTime(Config.startTime);
        }
        WorldInfo info = server.worldServerForDimension(0)
            .getWorldInfo();
        info.setRaining(false);
        info.setThundering(false);
        info.setRainTime(0);
        info.setThunderTime(0);
    }

    /**
     * Puts each player who is online and on no team on the team with the fewest players, so teams stay even. Returns
     * how many were placed.
     */
    public static int autoAssign(Game game) {
        List<EntityPlayerMP> players = new ArrayList<>();
        for (Object object : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) object;
            if (game.getTeamOf(player.getUniqueID()) == null) {
                players.add(player);
            }
        }
        Collections.shuffle(players);
        int placed = 0;
        for (EntityPlayerMP player : players) {
            if (autoAssign(game, player) != null) {
                placed++;
            }
        }
        return placed;
    }

    /** Puts the player on the team with the fewest players; returns the team, or null if there are no teams. */
    public static Team autoAssign(Game game, EntityPlayer player) {
        Team smallest = game.getSmallestTeam();
        if (smallest != null) {
            game.setTeamOf(player.getUniqueID(), player.getCommandSenderName(), smallest);
            if (player instanceof EntityPlayerMP) {
                GameWorld.setTeamSpawn((EntityPlayerMP) player, game);
            }
        }
        return smallest;
    }

    public static Game endGame() {
        GameSavedData data = getData();
        Game game = data.getGame();
        data.setGame(null);
        return game;
    }
}
