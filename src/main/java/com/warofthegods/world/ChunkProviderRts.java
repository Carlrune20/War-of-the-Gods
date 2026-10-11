package com.warofthegods.world;

import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.feature.WorldGenAbstractTree;
import net.minecraft.world.gen.feature.WorldGenBigTree;
import net.minecraft.world.gen.feature.WorldGenForest;
import net.minecraft.world.gen.feature.WorldGenTrees;

/**
 * Simple terrain for a game map: rolling grassland with hills, lakes and forests inside the playable area. Across the
 * margin around it the land slopes down into sea, and beyond the margin there is only sea, so the map reads as an
 * island.
 */
public class ChunkProviderRts implements IChunkProvider {

    /** Highest y that is filled with water. */
    public static final int SEA_LEVEL = 62;
    private static final int OCEAN_FLOOR = 45;

    private final World world;
    private final MapArea area;
    private final long seed;
    private final TerrainNoise heightNoise;
    private final TerrainNoise hillNoise;
    private final TerrainNoise forestNoise;
    private final Random random = new Random();

    public ChunkProviderRts(World world, MapArea area, long seed) {
        this.world = world;
        this.area = area;
        this.seed = seed;
        this.heightNoise = new TerrainNoise(seed);
        this.hillNoise = new TerrainNoise(seed * 31 + 7);
        this.forestNoise = new TerrainNoise(seed * 17 + 3);
    }

    /** The y of the top solid block of the column. */
    public int heightAt(int x, int z) {
        double height = 66 + heightNoise.fbm(x / 300.0, z / 300.0, 4) * 9
            + heightNoise.fbm(x / 60.0 + 500, z / 60.0 + 500, 3) * 3
            + Math.max(0, hillNoise.fbm(x / 200.0, z / 200.0, 3) - 0.15) * 55;

        double outside = area.distanceOutside(x, z);
        if (outside > 0) {
            double t = area.getMargin() == 0 ? 1 : Math.min(1, outside / area.getMargin());
            double smooth = t * t * (3 - 2 * t);
            height = height * (1 - smooth) + OCEAN_FLOOR * smooth;
        }
        return Math.max(1, Math.min(250, (int) Math.round(height)));
    }

    /** 0 for open land, rising to about 1 in thick forest. */
    private double forestAt(int x, int z) {
        return forestNoise.fbm(x / 120.0, z / 120.0, 3);
    }

    @Override
    public Chunk provideChunk(int chunkX, int chunkZ) {
        random.setSeed(chunkX * 341873128712L + chunkZ * 132897987541L + seed);
        Block[] blocks = new Block[65536];
        byte[] metadata = new byte[65536];
        byte[] biomes = new byte[256];

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = chunkX * 16 + localX;
                int z = chunkZ * 16 + localZ;
                int height = heightAt(x, z);
                int column = (localX * 16 + localZ) * 256;

                Block top;
                Block filler;
                if (height <= SEA_LEVEL - 4) {
                    top = height < SEA_LEVEL - 8 ? Blocks.sand : Blocks.gravel;
                    filler = Blocks.sand;
                } else if (height <= SEA_LEVEL + 1) {
                    top = Blocks.sand;
                    filler = Blocks.sand;
                } else {
                    top = Blocks.grass;
                    filler = Blocks.dirt;
                }

                for (int y = 0; y <= Math.max(height, SEA_LEVEL); y++) {
                    Block block;
                    if (y == 0 || y <= 2 && random.nextInt(y + 1) == 0) {
                        block = Blocks.bedrock;
                    } else if (y < height - 3) {
                        block = Blocks.stone;
                    } else if (y < height) {
                        block = filler;
                    } else if (y == height) {
                        block = top;
                    } else {
                        block = Blocks.water;
                    }
                    blocks[column + y] = block;
                }

                BiomeGenBase biome;
                if (height <= SEA_LEVEL - 4) {
                    biome = BiomeGenBase.ocean;
                } else if (height <= SEA_LEVEL + 1) {
                    biome = BiomeGenBase.beach;
                } else if (forestAt(x, z) > 0.2) {
                    biome = BiomeGenBase.forest;
                } else {
                    biome = BiomeGenBase.plains;
                }
                biomes[localZ << 4 | localX] = (byte) biome.biomeID;
            }
        }

        Chunk chunk = new Chunk(world, blocks, metadata, chunkX, chunkZ);
        System.arraycopy(biomes, 0, chunk.getBiomeArray(), 0, biomes.length);
        chunk.generateSkylightMap();
        return chunk;
    }

    @Override
    public Chunk loadChunk(int chunkX, int chunkZ) {
        return provideChunk(chunkX, chunkZ);
    }

    @Override
    public boolean chunkExists(int chunkX, int chunkZ) {
        return true;
    }

    /** Trees, grass and flowers on land, and animals inside the playable area. */
    @Override
    public void populate(IChunkProvider provider, int chunkX, int chunkZ) {
        int originX = chunkX * 16 + 8;
        int originZ = chunkZ * 16 + 8;
        random.setSeed(chunkX * 341873128712L + chunkZ * 132897987541L + seed + 1);

        double forest = forestAt(originX + 8, originZ + 8);
        int trees = forest > 0.2 ? (int) ((forest - 0.2) * 25) + 2 : random.nextInt(10) == 0 ? 1 : 0;
        for (int i = 0; i < trees; i++) {
            int x = originX + random.nextInt(16);
            int z = originZ + random.nextInt(16);
            int y = world.getHeightValue(x, z);
            if (world.getBlock(x, y - 1, z) == Blocks.grass) {
                treeGenerator().generate(world, random, x, y, z);
            }
        }

        for (int i = 0; i < 12; i++) {
            int x = originX + random.nextInt(16);
            int z = originZ + random.nextInt(16);
            int y = world.getHeightValue(x, z);
            if (world.getBlock(x, y - 1, z) == Blocks.grass && world.isAirBlock(x, y, z)) {
                int roll = random.nextInt(20);
                if (roll == 0) {
                    world.setBlock(x, y, z, Blocks.yellow_flower, 0, 2);
                } else if (roll == 1) {
                    world.setBlock(x, y, z, Blocks.red_flower, 0, 2);
                } else {
                    world.setBlock(x, y, z, Blocks.tallgrass, 1, 2);
                }
            }
        }

        if (area.isInside(originX + 8, originZ + 8)) {
            BiomeGenBase biome = world.getBiomeGenForCoords(originX + 8, originZ + 8);
            SpawnerAnimals.performWorldGenSpawning(world, biome, originX, originZ, 16, 16, random);
        }
    }

    private WorldGenAbstractTree treeGenerator() {
        int roll = random.nextInt(10);
        if (roll == 0) {
            return new WorldGenBigTree(false);
        }
        if (roll < 3) {
            return new WorldGenForest(false, false);
        }
        return new WorldGenTrees(false);
    }

    @Override
    public boolean saveChunks(boolean saveAll, IProgressUpdate progress) {
        return true;
    }

    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    @Override
    public boolean canSave() {
        return true;
    }

    @Override
    public String makeString() {
        return "WarOfTheGodsLevelSource";
    }

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return world.getBiomeGenForCoords(x, z)
            .getSpawnableList(type);
    }

    @Override
    public ChunkPosition func_147416_a(World world, String structure, int x, int y, int z) {
        return null;
    }

    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    @Override
    public void recreateStructures(int chunkX, int chunkZ) {}

    @Override
    public void saveExtraData() {}
}
