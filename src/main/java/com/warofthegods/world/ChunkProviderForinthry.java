package com.warofthegods.world;

import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

import com.warofthegods.block.ModBlocks;
import com.warofthegods.building.Ruins;

/**
 * The terrain of a Forinthry map, the Wilderness burnt black in the God Wars: rolling charred earth with drifts of ash,
 * craggy hills of scorched stone where embers still glow in the cracks, groves of dead, charred trees, giant mushrooms,
 * and the lava mazes laid out by {@link Forinthry}. Like Misthalin, the land goes on across the margin round the
 * playable area and then stops, cut off sheer.
 */
public class ChunkProviderForinthry implements IChunkProvider {

    /** The lowest the land goes: well above Misthalin's river banks, so nothing takes it for the river. */
    private static final int FLOOR = ChunkProviderRts.BANK + 3;

    private final World world;
    private final MapArea area;
    private final long seed;
    private final TerrainNoise heightNoise;
    private final TerrainNoise hillNoise;
    private final TerrainNoise ashNoise;
    private final TerrainNoise groveNoise;
    private final TerrainNoise mushroomNoise;
    private final Forinthry layout;
    private final Random random = new Random();

    public ChunkProviderForinthry(World world, MapArea area, long seed, int teams) {
        this.world = world;
        this.area = area;
        this.seed = seed;
        this.layout = new Forinthry(area, seed, teams);
        this.heightNoise = new TerrainNoise(seed * 3 + 1);
        this.hillNoise = new TerrainNoise(seed * 29 + 5);
        this.ashNoise = new TerrainNoise(seed * 7 + 9);
        this.groveNoise = new TerrainNoise(seed * 19 + 2);
        this.mushroomNoise = new TerrainNoise(seed * 23 + 4);
    }

    /** The height of the land before the mazes level it. */
    private double landAt(int x, int z) {
        double land = 68 + heightNoise.fbm(x / 280.0, z / 280.0, 4) * 5
            + heightNoise.fbm(x / 50.0 + 300, z / 50.0 + 300, 3) * 1.5
            + craggy(x, z) * 30;
        return Math.max(FLOOR, land);
    }

    /** 0 on the open plain, rising toward 1 on the craggy hills. */
    private double craggy(int x, int z) {
        return Math.max(0, hillNoise.fbm(x / 170.0, z / 170.0, 4) - 0.22);
    }

    /** The y of the top solid block of the column; under a maze's lava, of the lava itself. */
    public int heightAt(int x, int z) {
        double height = landAt(x, z);
        Forinthry.Maze maze = layout.mazeAt(x, z, Forinthry.EASE);
        if (maze != null) {
            int level = mazeLevel(maze);
            int lx = x - maze.minX;
            int lz = z - maze.minZ;
            int size = maze.size();
            double outside = Math.max(Math.max(-lx, lx - size + 1), Math.max(-lz, lz - size + 1));
            double t = Math.max(0, Math.min(1, outside / Forinthry.EASE));
            height = level + (height - level) * t * t * (3 - 2 * t);
        }
        return Math.max(1, Math.min(250, (int) Math.round(height)));
    }

    /** The level of a maze's ground: the land at its middle. */
    private int mazeLevel(Forinthry.Maze maze) {
        return (int) Math.round(Math.max(FLOOR, Math.min(80, landAt(maze.getCenterX(), maze.getCenterZ()))));
    }

    private boolean isCutOff(int x, int z) {
        int dx = Math.max(Math.max(area.getMinX() - x, x - area.getMaxX()), 0);
        int dz = Math.max(Math.max(area.getMinZ() - z, z - area.getMaxZ()), 0);
        return Math.max(dx, dz) > area.getMargin();
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
                biomes[localZ << 4 | localX] = (byte) BiomeGenBase.hell.biomeID;
                if (isCutOff(x, z)) {
                    continue;
                }
                int height = heightAt(x, z);
                int column = (localX * 16 + localZ) * 256;
                Forinthry.Maze maze = layout.mazeAt(x, z, 0);

                Block top;
                Block filler;
                double crag = craggy(x, z);
                if (maze != null) {
                    // Paths of ash between the channels, the channels lava over scorched stone.
                    top = maze.isLava(x, z) ? Blocks.lava : ModBlocks.ash;
                    filler = ModBlocks.scorchedStone;
                } else if (crag > 0.3 + random.nextDouble() * 0.06) {
                    top = random.nextInt(9) == 0 ? ModBlocks.emberRock : ModBlocks.scorchedStone;
                    filler = ModBlocks.scorchedStone;
                } else if (ashNoise.fbm(x / 60.0, z / 60.0, 3) > 0.25 + random.nextDouble() * 0.05) {
                    top = ModBlocks.ash;
                    filler = ModBlocks.ash;
                } else {
                    top = random.nextInt(60) == 0 ? ModBlocks.ash : ModBlocks.charredEarth;
                    filler = ModBlocks.charredEarth;
                }

                for (int y = 0; y <= height; y++) {
                    Block block;
                    if (y == 0 || y <= 2 && random.nextInt(y + 1) == 0) {
                        block = Blocks.bedrock;
                    } else if (y < height - 3) {
                        block = Blocks.stone;
                    } else if (y < height) {
                        block = filler;
                    } else {
                        block = top;
                    }
                    blocks[column + y] = block;
                }
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

    /**
     * Ruins and the mazes' chests first, then dead trees, in groves and here and there, and giant mushrooms, in
     * patches and now and then alone, none near where teams start, in a maze or on what stands on the map.
     */
    @Override
    public void populate(IChunkProvider provider, int chunkX, int chunkZ) {
        int originX = chunkX * 16 + 8;
        int originZ = chunkZ * 16 + 8;
        if (world instanceof WorldServer) {
            Ruins.populate((WorldServer) world, chunkX, chunkZ, seed);
        }
        random.setSeed(chunkX * 341873128712L + chunkZ * 132897987541L + seed + 1);

        double grove = groveNoise.fbm((originX + 8) / 110.0, (originZ + 8) / 110.0, 3);
        int trees = grove > 0.2 ? (int) ((grove - 0.2) * 14) + 1 : random.nextInt(6) == 0 ? 1 : 0;
        for (int i = 0; i < trees; i++) {
            int x = originX + random.nextInt(16);
            int z = originZ + random.nextInt(16);
            if (canGrow(x, z, 3)) {
                deadTree(x, world.getHeightValue(x, z), z);
            }
        }

        double patch = mushroomNoise.fbm((originX + 8) / 90.0, (originZ + 8) / 90.0, 2);
        int mushrooms = patch > 0.3 ? 1 + random.nextInt(3) : random.nextInt(4) == 0 ? 1 : 0;
        for (int i = 0; i < mushrooms; i++) {
            int x = originX + 2 + random.nextInt(12);
            int z = originZ + 2 + random.nextInt(12);
            if (canGrow(x, z, 3)) {
                giantMushroom(x, world.getHeightValue(x, z), z);
            }
        }
    }

    /** Whether a dead tree or mushroom may grow at x, z: on open ground, clear of mazes and of what stands. */
    private boolean canGrow(int x, int z, int reach) {
        int y = world.getHeightValue(x, z);
        Block ground = world.getBlock(x, y - 1, z);
        if (ground != ModBlocks.charredEarth && ground != ModBlocks.ash) {
            return false;
        }
        for (int dx = -reach; dx <= reach; dx += reach) {
            for (int dz = -reach; dz <= reach; dz += reach) {
                if (!world.isAirBlock(x + dx, y, z + dz) || isCutOff(x + dx, z + dz)) {
                    return false;
                }
            }
        }
        return layout.mazeAt(x, z, reach + 2) == null && !ChunkProviderRts.keptClear(layout.getBases(), x, z);
    }

    /**
     * A dead tree: a black trunk four to seven high, bare of leaves, with a few crooked branches. Its logs are
     * charred, which marks it a tree for workers to chop although it has no leaves.
     */
    private void deadTree(int x, int y, int z) {
        int height = 4 + random.nextInt(4);
        for (int i = 0; i < height; i++) {
            world.setBlock(x, y + i, z, ModBlocks.charredLog, 0, 2);
        }
        int branches = 1 + random.nextInt(3);
        int[][] sides = { { 1, 0, 4 }, { -1, 0, 4 }, { 0, 1, 8 }, { 0, -1, 8 } };
        for (int b = 0; b < branches; b++) {
            int[] side = sides[random.nextInt(sides.length)];
            int by = y + height - 1 - random.nextInt(Math.max(1, height / 2));
            int length = 1 + random.nextInt(2);
            for (int i = 1; i <= length; i++) {
                int bx = x + side[0] * i;
                int bz = z + side[1] * i;
                int up = by + (i == length ? 1 : 0);
                if (world.isAirBlock(bx, up, bz)) {
                    world.setBlock(bx, up, bz, ModBlocks.charredLog, i == length ? 0 : side[2], 2);
                }
            }
        }
    }

    /**
     * A giant mushroom, the Wilderness's food: a pale stem three to five high under a violet cap, three across on the
     * small ones and five across, its rim hanging down, on the big ones.
     */
    private void giantMushroom(int x, int y, int z) {
        int height = 3 + random.nextInt(3);
        boolean big = height >= 4 && random.nextBoolean();
        for (int i = 0; i < height; i++) {
            world.setBlock(x, y + i, z, ModBlocks.mushroomStem, 0, 2);
        }
        int capY = y + height;
        int radius = big ? 2 : 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                boolean corner = Math.abs(dx) == radius && Math.abs(dz) == radius;
                if (big && corner) {
                    continue;
                }
                world.setBlock(x + dx, capY, z + dz, ModBlocks.mushroomCap, 0, 2);
                boolean rim = Math.max(Math.abs(dx), Math.abs(dz)) == radius;
                if (big && rim && world.isAirBlock(x + dx, capY - 1, z + dz)) {
                    world.setBlock(x + dx, capY - 1, z + dz, ModBlocks.mushroomCap, 0, 2);
                }
            }
        }
        if (big) {
            world.setBlock(x, capY + 1, z, ModBlocks.mushroomCap, 0, 2);
        }
    }

    public Forinthry getLayout() {
        return layout;
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
        return "WarOfTheGodsForinthrySource";
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
