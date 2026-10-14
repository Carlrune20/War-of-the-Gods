package com.warofthegods.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
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
     * Ends any running game and starts a new one with the given number of default teams. The map is centred on 0,0 of
     * the game world, which is rebuilt from the seed.
     */
    public static Game newGame(int size, int teamCount, long seed) {
        GameSavedData data = getData();
        Game game = new Game(data.getLastGameNumber() + 1, System.currentTimeMillis(), 0, 0, size, seed);
        for (int i = 0; i < teamCount; i++) {
            game.addDefaultTeam(Config.startingResources);
        }
        game.setMapTeams(teamCount);
        data.setGame(game);
        WarOfTheGods.LOG
            .info("Started game #{} with size {}, {} teams and seed {}", game.getNumber(), size, teamCount, seed);
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
