package com.warofthegods.world;

import net.minecraft.world.biome.BiomeGenBase;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.block.ModBlocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Forinthry's biome, the Wilderness: dry and hot, never rained on, with nothing that spawns of itself, its few ponds
 * a murky green-blue and what little grows there a burnt brown. Its id is {@link Config#wildernessBiomeId}.
 */
public class BiomeWilderness extends BiomeGenBase {

    /** The pools' tint, a stagnant green-blue rather than the overworld's clear blue. */
    private static final int WATER = 0x4A9E86;
    private static final int GROWTH = 0x5A4A2E;

    private static BiomeWilderness instance;

    private BiomeWilderness(int id) {
        super(id);
        setBiomeName("Wilderness");
        setDisableRain();
        setTemperatureRainfall(2.0F, 0.0F);
        waterColorMultiplier = WATER;
        topBlock = ModBlocks.charredEarth;
        fillerBlock = ModBlocks.charredEarth;
        spawnableMonsterList.clear();
        spawnableCreatureList.clear();
        spawnableWaterCreatureList.clear();
        spawnableCaveCreatureList.clear();
    }

    /** Makes the biome once the mod's blocks are there, warning when another mod already has its id. */
    public static void register() {
        int id = Config.wildernessBiomeId;
        BiomeGenBase taken = BiomeGenBase.getBiomeGenArray()[id];
        if (taken != null) {
            WarOfTheGods.LOG.warn(
                "Biome id {} is already {}; Forinthry takes it over. Change wildernessBiomeId in the config.",
                id,
                taken.biomeName);
        }
        instance = new BiomeWilderness(id);
    }

    public static BiomeWilderness get() {
        return instance;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getBiomeGrassColor(int x, int y, int z) {
        return GROWTH;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getBiomeFoliageColor(int x, int y, int z) {
        return GROWTH;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getSkyColorByTemp(float temperature) {
        return 0x2A2024;
    }
}
