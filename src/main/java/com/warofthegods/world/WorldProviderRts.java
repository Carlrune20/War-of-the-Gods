package com.warofthegods.world;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManagerHell;
import net.minecraft.world.chunk.IChunkProvider;

import com.warofthegods.game.Game;

/**
 * The game dimension. Each game gets its own save folder, so a new game always starts from fresh terrain. Clients
 * are told this dimension is a plain overworld-type dimension, so players need no client mod.
 */
public class WorldProviderRts extends WorldProvider {

    private Game game;

    @Override
    protected void registerWorldChunkManager() {
        worldChunkMgr = new WorldChunkManagerHell(BiomeGenBase.plains, 0.5F);
        if (!worldObj.isRemote) {
            game = GameWorld.currentGame();
        }
    }

    @Override
    public IChunkProvider createChunkGenerator() {
        return new ChunkProviderRts(worldObj, GameWorld.mapArea(game), GameWorld.seedOf(game, worldObj.getSeed()));
    }

    @Override
    public String getSaveFolder() {
        return GameWorld.saveFolder(game);
    }

    @Override
    public String getDimensionName() {
        return "War of the Gods";
    }

    @Override
    public boolean canRespawnHere() {
        return true;
    }

    /** The middle of the map; respawning players are moved onto dry ground from here. */
    @Override
    public ChunkCoordinates getSpawnPoint() {
        MapArea area = GameWorld.mapArea(game);
        return new ChunkCoordinates(area.getCenterX(), ChunkProviderRts.SEA_LEVEL + 2, area.getCenterZ());
    }
}
