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
import net.minecraft.world.gen.feature.WorldGenAbstractTree;
import net.minecraft.world.gen.feature.WorldGenBigTree;
import net.minecraft.world.gen.feature.WorldGenForest;
import net.minecraft.world.gen.feature.WorldGenTrees;

import com.warofthegods.building.Ruins;
import com.warofthegods.game.Building;
import com.warofthegods.game.Game;
import com.warofthegods.game.GoldMine;
import com.warofthegods.game.Ruin;
import com.warofthegods.game.Settlement;
import com.warofthegods.game.Team;

/**
 * The terrain of a Misthalin map: rolling grassland with low hills and forests, and a river right across it with
 * three stone bridges, laid out by {@link Misthalin}. There is no sand and there are no lakes. The land goes on across
 * the margin round the playable area and then stops, cut off sheer, with nothing beyond.
 */
public class ChunkProviderRts implements IChunkProvider {

    /** Highest y that is filled with water. */
    public static final int SEA_LEVEL = 62;
    /** The top of the river bed in the middle of the river, so the water is 2 deep. */
    private static final int RIVER_BED = SEA_LEVEL - 2;
    /** The banks of the river and the ground the bridges sit on: the top block is one above the water. */
    public static final int BANK = SEA_LEVEL + 1;

    private final World world;
    private final MapArea area;
    private final long seed;
    private final TerrainNoise heightNoise;
    private final TerrainNoise hillNoise;
    private final TerrainNoise forestNoise;
    private final Misthalin layout;
    private final Random random = new Random();

    public ChunkProviderRts(World world, MapArea area, long seed, int teams) {
        this.world = world;
        this.area = area;
        this.seed = seed;
        this.layout = new Misthalin(area, seed, teams);
        this.heightNoise = new TerrainNoise(seed);
        this.hillNoise = new TerrainNoise(seed * 31 + 7);
        this.forestNoise = new TerrainNoise(seed * 17 + 3);
    }

    /** The y of the top solid block of the column; under the river, of its bed. */
    public int heightAt(int x, int z) {
        double land = 68 + heightNoise.fbm(x / 300.0, z / 300.0, 4) * 6
            + heightNoise.fbm(x / 60.0 + 500, z / 60.0 + 500, 3) * 2
            + Math.max(0, hillNoise.fbm(x / 200.0, z / 200.0, 3) - 0.2) * 35;
        // Never below the banks, so there are no lakes.
        land = Math.max(BANK + 1, land);

        double river = layout.riverDistance(x, z);
        if (river <= Misthalin.RIVER_HALF_WIDTH + 0.5) {
            return river <= Misthalin.RIVER_HALF_WIDTH - 1.5 ? RIVER_BED : RIVER_BED + 1;
        }
        // Down to the banks beside the river, over a wider slope where the land is higher.
        double bank = 3 + (land - BANK) * 1.2;
        double t = Math.min(1, (river - Misthalin.RIVER_HALF_WIDTH - 0.5) / bank);
        double height = BANK + (land - BANK) * t * t * (3 - 2 * t);
        // Level ground at the ends of the bridges.
        double approach = layout.bridgeApproach(x, z);
        if (approach < 1) {
            height = BANK + (height - BANK) * approach * approach * (3 - 2 * approach);
        }
        return Math.max(1, Math.min(250, (int) Math.round(height)));
    }

    /** Whether the column lies past the margin, where the land is cut off. */
    private boolean isCutOff(int x, int z) {
        int dx = Math.max(Math.max(area.getMinX() - x, x - area.getMaxX()), 0);
        int dz = Math.max(Math.max(area.getMinZ() - z, z - area.getMaxZ()), 0);
        return Math.max(dx, dz) > area.getMargin();
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
                if (isCutOff(x, z)) {
                    biomes[localZ << 4 | localX] = (byte) BiomeGenBase.plains.biomeID;
                    continue;
                }
                int height = heightAt(x, z);
                int column = (localX * 16 + localZ) * 256;
                boolean river = layout.isRiver(x, z);

                Block top;
                Block filler = Blocks.dirt;
                if (river) {
                    // A bed of gravel and clay; no sand anywhere.
                    int roll = random.nextInt(6);
                    top = roll == 0 ? Blocks.clay : roll < 3 ? Blocks.gravel : Blocks.dirt;
                } else {
                    top = Blocks.grass;
                }

                for (int y = 0; y <= (river ? SEA_LEVEL : height); y++) {
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
                if (river) {
                    biome = BiomeGenBase.river;
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

    /**
     * Old ruins now and then, then trees, grass and flowers on land, none by the river or at the ends of the bridges.
     * Animals are not spawned here: each team gets the same herds near its town center once the game is under way.
     */
    @Override
    public void populate(IChunkProvider provider, int chunkX, int chunkZ) {
        int originX = chunkX * 16 + 8;
        int originZ = chunkZ * 16 + 8;
        // Ruins first, so the trees keep off them.
        if (world instanceof WorldServer) {
            Ruins.populate((WorldServer) world, chunkX, chunkZ, seed);
        }
        random.setSeed(chunkX * 341873128712L + chunkZ * 132897987541L + seed + 1);

        double forest = forestAt(originX + 8, originZ + 8);
        int trees = forest > 0.2 ? (int) ((forest - 0.2) * 25) + 2 : random.nextInt(10) == 0 ? 1 : 0;
        for (int i = 0; i < trees; i++) {
            int x = originX + random.nextInt(16);
            int z = originZ + random.nextInt(16);
            int y = world.getHeightValue(x, z);
            if (world.getBlock(x, y - 1, z) == Blocks.grass
                && layout.riverDistance(x, z) > Misthalin.RIVER_HALF_WIDTH + 4
                && layout.bridgeApproach(x, z) >= 1
                && !isCutOff(x + 3, z + 3)
                && !isCutOff(x - 3, z - 3)
                && !keptClear(x, z)) {
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

    }

    /** How far round where a team starts no tree grows, so its town center is not buried in a forest. */
    private static final int BASE_CLEARING = 24;
    /** How far round a town center, building, settlement, gold mine or ruin no tree grows. */
    private static final int FEATURE_CLEARING = 7;

    /**
     * Whether no tree may grow at x, z: near where a team starts, or near what already stands on the map. Chunks
     * further off are often filled in only after a town center goes up, and their trees would grow into it.
     */
    private boolean keptClear(int x, int z) {
        return keptClear(layout.getBases(), x, z);
    }

    /** Whether no tree may grow at x, z on any map: near where a team starts, or near what stands on the map. */
    static boolean keptClear(double[][] bases, int x, int z) {
        for (double[] base : bases) {
            double dx = base[0] - x;
            double dz = base[1] - z;
            if (dx * dx + dz * dz < BASE_CLEARING * BASE_CLEARING) {
                return true;
            }
        }
        Game game = GameWorld.currentGame();
        if (game == null) {
            return false;
        }
        for (Team team : game.getTeams()) {
            int[] base = team.getBase();
            if (base != null && Math.abs(base[0] - x) < BASE_CLEARING && Math.abs(base[1] - z) < BASE_CLEARING) {
                return true;
            }
        }
        for (Building building : game.getBuildings()) {
            if (building.covers(x + 0.5, z + 0.5, FEATURE_CLEARING)) {
                return true;
            }
        }
        for (Settlement settlement : game.getSettlements()) {
            if (settlement.overlaps(x, z, 1, 1, FEATURE_CLEARING)) {
                return true;
            }
        }
        for (GoldMine mine : game.getMines()) {
            if (mine.overlaps(x, z, 1, 1, FEATURE_CLEARING)) {
                return true;
            }
        }
        for (Ruin ruin : game.getRuins()) {
            if (ruin.overlaps(x, z, 1, 1, FEATURE_CLEARING)) {
                return true;
            }
        }
        return false;
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
