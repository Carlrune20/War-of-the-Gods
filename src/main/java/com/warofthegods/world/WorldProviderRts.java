package com.warofthegods.world;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManagerHell;
import net.minecraft.world.chunk.IChunkProvider;

import com.warofthegods.Config;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameMap;

/**
 * The game dimension. Each game gets its own save folder, so a new game always starts from fresh terrain. Clients
 * are told this dimension is a plain overworld-type dimension, so players need no client mod.
 */
public class WorldProviderRts extends WorldProvider {

    /** The time of day at midnight, in ticks. */
    private static final long MIDNIGHT = 18000;
    /**
     * The first day of the game map's time, which the shader pack reads the map from. Far past any overworld's day
     * count, and low enough that the time still fits an int.
     */
    static final long SHADER_DAY = 65536;

    private Game game;

    @Override
    protected void registerWorldChunkManager() {
        if (!worldObj.isRemote) {
            game = GameWorld.currentGame();
        }
        boolean wild = GameWorld.mapOf(game) == GameMap.FORINTHRY && BiomeWilderness.get() != null;
        worldChunkMgr = new WorldChunkManagerHell(wild ? BiomeWilderness.get() : BiomeGenBase.plains, wild ? 0 : 0.5F);
    }

    @Override
    public IChunkProvider createChunkGenerator() {
        MapArea area = GameWorld.mapArea(game);
        long seed = GameWorld.seedOf(game, worldObj.getSeed());
        if (GameWorld.mapOf(game) == GameMap.FORINTHRY) {
            return new ChunkProviderForinthry(worldObj, area, seed, GameWorld.mapTeams(game));
        }
        return new ChunkProviderRts(worldObj, area, seed, GameWorld.mapTeams(game));
    }

    /**
     * The time players are sent. Forinthry is always at midnight, whatever the time in the overworld, so their sky
     * stays dark there too; elsewhere the game map keeps the overworld's time of day. The day count tells the shader
     * pack which map this is (shaderpack/shaders/lib/map.glsl). A client with the mod keeps the time as sent.
     */
    @Override
    public long getWorldTime() {
        if (worldObj.isRemote) {
            return super.getWorldTime();
        }
        return gameTime(super.getWorldTime(), GameWorld.mapOf(game));
    }

    /**
     * The game map's time from the overworld's: its time of day, or midnight on Forinthry, on day
     * {@link #SHADER_DAY} + 16 times the map's place in {@link GameMap} + the overworld's day in the moon's 8-day
     * cycle. The moon keeps its phases, and the day count never reaches the next map's even when the client counts on
     * past midnight before the server's next time arrives. Without a map, the overworld's time.
     */
    static long gameTime(long time, GameMap map) {
        if (map == null) {
            return time;
        }
        long timeOfDay = map == GameMap.FORINTHRY ? MIDNIGHT : Math.floorMod(time, 24000L);
        long moonDay = Math.floorMod(Math.floorDiv(time, 24000L), 8L);
        return (SHADER_DAY + 16L * map.ordinal() + moonDay) * 24000L + timeOfDay;
    }

    /** The sun's place in the sky by the time; on Forinthry the server too keeps it at midnight, for its light. */
    @Override
    public float calculateCelestialAngle(long time, float partialTicks) {
        if (GameWorld.mapOf(game) == GameMap.FORINTHRY) {
            return super.calculateCelestialAngle(MIDNIGHT, 0);
        }
        return super.calculateCelestialAngle(time, partialTicks);
    }

    @Override
    public String getSaveFolder() {
        return GameWorld.saveFolder(game);
    }

    @Override
    public String getDimensionName() {
        return "War of the Gods";
    }

    /** Unless rain is turned on, the game map stays dry whatever the weather does elsewhere. */
    @Override
    public void updateWeather() {
        if (Config.rain) {
            super.updateWeather();
            return;
        }
        worldObj.prevRainingStrength = 0;
        worldObj.rainingStrength = 0;
        worldObj.prevThunderingStrength = 0;
        worldObj.thunderingStrength = 0;
    }

    @Override
    public boolean canRespawnHere() {
        return true;
    }

    /** The middle of the map; respawning players are moved onto dry ground from here. */
    @Override
    public ChunkCoordinates getSpawnPoint() {
        MapArea area = GameWorld.mapArea(game);
        return new ChunkCoordinates(area.getCenterX(), ChunkProviderRts.BANK + 1, area.getCenterZ());
    }
}
