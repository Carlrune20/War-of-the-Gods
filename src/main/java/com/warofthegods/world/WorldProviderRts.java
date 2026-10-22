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
     * Forinthry is always at midnight, whatever the time in the overworld; players are sent this time, so their sky
     * stays dark there too. Elsewhere the game map keeps the overworld's time.
     */
    @Override
    public long getWorldTime() {
        if (GameWorld.mapOf(game) == GameMap.FORINTHRY) {
            long time = super.getWorldTime();
            return time - Math.floorMod(time, 24000L) + MIDNIGHT;
        }
        return super.getWorldTime();
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
