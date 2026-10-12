package com.warofthegods.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;
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
        data.setGame(game);
        WarOfTheGods.LOG
            .info("Started game #{} with size {}, {} teams and seed {}", game.getNumber(), size, teamCount, seed);
        GameWorld.rebuild();
        return game;
    }

    public static Game endGame() {
        GameSavedData data = getData();
        Game game = data.getGame();
        data.setGame(null);
        return game;
    }
}
