package com.warofthegods.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;

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

    /** The running game, or null if none has been started. */
    public static Game getGame() {
        return getData().getGame();
    }

    /** Ends any running game and starts a new one with the given number of default teams. */
    public static Game newGame(int centerX, int centerZ, int size, int teamCount) {
        GameSavedData data = getData();
        Game game = new Game(data.getLastGameNumber() + 1, System.currentTimeMillis(), centerX, centerZ, size);
        for (int i = 0; i < teamCount; i++) {
            game.addDefaultTeam(Config.startingResources);
        }
        data.setGame(game);
        WarOfTheGods.LOG.info(
            "Started game #{} centred on {},{} with size {} and {} teams",
            game.getNumber(),
            centerX,
            centerZ,
            size,
            teamCount);
        return game;
    }

    public static Game endGame() {
        GameSavedData data = getData();
        Game game = data.getGame();
        data.setGame(null);
        return game;
    }
}
