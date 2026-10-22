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
 * The terrain of a Forinthry map, the Wilderness burnt black in the God Wars. The ground is black, charred earth broken
 * by patches of scorched stone and glowing ember rock, with craggy hills of it here and there, and patches of bare
 * brown earth where only dead bushes grow, rising in places into rolling hills. Lava runs through it all: winding
 * rivers that cross and split round islands, broken by fords, cutting canyons through the hills under ledges of rock
 * that arch out over them; lakes, and small potholes. {@link Forinthry} lays out the lakes, a few murky green ponds and
 * the old fortresses. Dead trees stand bare, alone or in groves, and mushrooms grow in clumps, and in patches round
 * where each team starts. Like Misthalin, the land goes on across the margin round the playable area
 * and then stops, cut off sheer.
 */
public class ChunkProviderForinthry implements IChunkProvider {

    /** The lowest the land goes: well above Misthalin's river banks, so nothing takes it for the river. */
    private static final int FLOOR = ChunkProviderRts.BANK + 3;
    /** The top of the lava in the rivers, a block under the lowest land, so they never spill. */
    private static final int RIVER = FLOOR - 1;
    /** The highest a lake or fortress is levelled at. */
    private static final int CEILING = 82;
    /** Columns computed round a chunk, for the lava rivers' banks. */
    private static final int BORDER = 3;
    private static final int GRID = 16 + 2 * BORDER;

    private final World world;
    private final MapArea area;
    private final long seed;
    private final TerrainNoise heightNoise;
    private final TerrainNoise hillNoise;
    private final TerrainNoise earthNoise;
    private final TerrainNoise rockNoise;
    private final TerrainNoise riverNoise;
    private final TerrainNoise warpNoise;
    private final TerrainNoise widthNoise;
    private final TerrainNoise maskNoise;
    private final TerrainNoise fordNoise;
    private final TerrainNoise groveNoise;
    private final Forinthry layout;
    private final double[][] bases;
    private final Random random = new Random();

    public ChunkProviderForinthry(World world, MapArea area, long seed, int teams) {
        this.world = world;
        this.area = area;
        this.seed = seed;
        this.layout = new Forinthry(area, seed, teams);
        this.bases = layout.getBases();
        this.heightNoise = new TerrainNoise(seed * 3 + 1);
        this.hillNoise = new TerrainNoise(seed * 29 + 5);
        this.earthNoise = new TerrainNoise(seed * 7 + 9);
        this.rockNoise = new TerrainNoise(seed * 31 + 3);
        this.riverNoise = new TerrainNoise(seed * 37 + 6);
        this.warpNoise = new TerrainNoise(seed * 43 + 8);
        this.widthNoise = new TerrainNoise(seed * 47 + 10);
        this.maskNoise = new TerrainNoise(seed * 53 + 12);
        this.fordNoise = new TerrainNoise(seed * 59 + 14);
        this.groveNoise = new TerrainNoise(seed * 19 + 2);
    }

    /** The height of the land before lakes and fortresses level it. */
    private double landAt(int x, int z) {
        double land = 68 + heightNoise.fbm(x / 280.0, z / 280.0, 4) * 5
            + heightNoise.fbm(x / 50.0 + 300, z / 50.0 + 300, 3) * 1.5
            + craggy(x, z) * 30
            + rolling(x, z) * 40;
        return Math.max(FLOOR, land);
    }

    /** 0 on the open plain, rising toward 1 on the craggy hills; the land round where teams start stays open. */
    private double craggy(int x, int z) {
        return Math.max(0, hillNoise.fbm(x / 170.0, z / 170.0, 4) - 0.22) * open(x, z);
    }

    /** 0 on the plain, rising on the rolling hills, which the rivers cut canyons through. */
    private double rolling(int x, int z) {
        return Math.max(0, hillNoise.fbm(x / 120.0 + 400, z / 120.0 + 400, 3) - 0.08) * open(x, z);
    }

    /** 0 near where a team starts, 1 well away from it, so the hills keep away from the bases. */
    private double open(int x, int z) {
        double nearest = Double.MAX_VALUE;
        for (double[] base : bases) {
            nearest = Math.min(nearest, Math.hypot(base[0] - x, base[1] - z));
        }
        return Math.max(0, Math.min(1, (nearest - Forinthry.BASE_ROOM - 16) / 50));
    }

    /** The level a lake or fortress lies at: the land at its middle. */
    private int levelAt(int x, int z) {
        return (int) Math.round(Math.max(FLOOR, Math.min(CEILING, landAt(x, z))));
    }

    /**
     * Ground eased from a level back to the land's over {@link Forinthry#EASE} blocks, and never below the level right
     * next to it, so whatever lies at that level does not spill out.
     */
    private static double ease(double land, int level, double outside) {
        double t = Math.max(0, Math.min(1, outside / Forinthry.EASE));
        double height = level + (land - level) * t * t * (3 - 2 * t);
        return outside <= 2 ? Math.max(height, level) : height;
    }

    /** What a column holds before the rivers: the top of its ground, and any lake or pond on it. */
    private static final class Column {

        int ground;
        Block liquid;
        int liquidTop;
        boolean pond;
        boolean feature;
    }

    private Column column(int x, int z) {
        Column column = new Column();
        double height = landAt(x, z);
        Forinthry.Keep keep = layout.keepAt(x, z, Forinthry.EASE);
        if (keep != null) {
            int level = levelAt(keep.getCenterX(), keep.getCenterZ());
            if (keep.covers(x, z, 0)) {
                column.feature = true;
                column.ground = level;
                return column;
            }
            height = ease(height, level, outsideSquare(x, z, keep.minX, keep.minZ, Forinthry.KEEP_SIZE));
        }
        Forinthry.Pool pool = layout.poolAt(x, z, Forinthry.EASE);
        if (pool != null) {
            int level = levelAt(pool.centerX, pool.centerZ);
            int depth = pool.depth(x, z);
            if (depth > 0) {
                column.feature = true;
                column.ground = level - depth;
                column.liquid = pool.lava ? Blocks.lava : Blocks.water;
                column.liquidTop = level;
                column.pond = !pool.lava;
                return column;
            }
            height = ease(height, level, Math.max(0, (pool.reach(x, z) - 1) * pool.radius));
            column.pond = !pool.lava && pool.reach(x, z) < 1 + 3 / pool.radius;
        }
        column.ground = Math.max(1, Math.min(250, (int) Math.round(height)));
        return column;
    }

    private static double outsideSquare(int x, int z, int minX, int minZ, int size) {
        int lx = x - minX;
        int lz = z - minZ;
        return Math.max(0, Math.max(Math.max(-lx, lx - size + 1), Math.max(-lz, lz - size + 1)));
    }

    /**
     * Whether a lava river runs through x, z: along the lines where two warped noises cross zero, so the rivers wind
     * and cross each other, each wider and narrower as it goes and only in some parts of the map. Where one runs wide
     * it now and then splits round an island; fords break them; and they keep away from where teams start, the old
     * fortresses and the ponds. They run through the hills as through the plain, in canyons.
     */
    private boolean isRiver(int x, int z) {
        if (layout.nearBase(x, z, 14) || layout.keepAt(x, z, 6) != null) {
            return false;
        }
        Forinthry.Pool pool = layout.poolAt(x, z, 8);
        if (pool != null && !pool.lava || fordNoise.fbm(x / 16.0, z / 16.0, 1) > 0.62) {
            return false;
        }
        double wx = x + warpNoise.fbm(x / 45.0, z / 45.0, 2) * 14;
        double wz = z + warpNoise.fbm(x / 45.0 + 70, z / 45.0 + 70, 2) * 14;
        double width = 0.018 + 0.016 * (widthNoise.fbm(x / 70.0, z / 70.0, 2) + 1) / 2;
        double first = maskNoise.fbm(x / 260.0, z / 260.0, 2) < -0.08 ? 9
            : Math.abs(riverNoise.fbm(wx / 170.0, wz / 170.0, 3)) / width;
        double second = maskNoise.fbm(x / 260.0 + 90, z / 260.0 + 90, 2) < 0.02 ? 9
            : Math.abs(riverNoise.fbm(wz / 140.0 + 300, wx / 140.0 + 300, 3)) / (width * 0.85);
        double across = Math.min(first, second);
        if (across >= 1) {
            return false;
        }
        // An island in the middle of a wide stretch.
        return !(across < 0.32 && width > 0.026 && widthNoise.fbm(x / 20.0 + 40, z / 20.0 + 40, 2) > 0.18);
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
        int biome = BiomeWilderness.get() != null ? BiomeWilderness.get().biomeID : BiomeGenBase.desert.biomeID;

        // The columns of the chunk and a border round it, and where the rivers run in them.
        Column[][] columns = new Column[GRID][GRID];
        boolean[][] river = new boolean[GRID][GRID];
        for (int gx = 0; gx < GRID; gx++) {
            for (int gz = 0; gz < GRID; gz++) {
                int x = chunkX * 16 + gx - BORDER;
                int z = chunkZ * 16 + gz - BORDER;
                columns[gx][gz] = column(x, z);
                river[gx][gz] = columns[gx][gz].liquid == null && !columns[gx][gz].feature && isRiver(x, z);
            }
        }

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = chunkX * 16 + localX;
                int z = chunkZ * 16 + localZ;
                biomes[localZ << 4 | localX] = (byte) biome;
                if (isCutOff(x, z)) {
                    continue;
                }
                int gx = localX + BORDER;
                int gz = localZ + BORDER;
                Column column = columns[gx][gz];
                int ground = column.ground;
                Block liquid = column.liquid;
                int liquidTop = column.liquidTop;
                int ledgeFrom = 0;
                int ledgeTo = -1;
                if (river[gx][gz]) {
                    liquid = Blocks.lava;
                    liquidTop = RIVER;
                    ground = liquidTop - 1 - (riverDepth(river, gx, gz) > 2 ? 1 : 0);
                    // Under a high bank, a ledge of rock reaching out over the lava, which meets the one from the
                    // other bank where the canyon is narrow.
                    int[] bank = bankBeside(columns, river, gx, gz);
                    if (bank != null && bank[0] - liquidTop >= 7 + 3 * (bank[1] - 1)
                        && rockNoise.fbm(x / 9.0 + 200, z / 9.0 + 200, 2) > -0.15) {
                        ledgeTo = bank[0];
                        ledgeFrom = bank[0] - (bank[1] == 1 ? 2 : 1);
                    }
                }
                boolean bank = liquid == null && lavaNear(columns, river, gx, gz);
                int index = (localX * 16 + localZ) * 256;
                Block top;
                int topMeta = 0;
                Block filler;
                int fillerMeta = 0;
                double crag = craggy(x, z);
                if (liquid == Blocks.lava) {
                    top = random.nextInt(3) == 0 ? ModBlocks.emberRock : ModBlocks.scorchedStone;
                    filler = ModBlocks.scorchedStone;
                } else if (liquid == Blocks.water || column.pond) {
                    // A pond's bed and shore: gravel and bare earth.
                    int roll = random.nextInt(5);
                    top = roll == 0 ? Blocks.gravel : Blocks.dirt;
                    topMeta = roll == 0 ? 0 : 1;
                    filler = Blocks.dirt;
                    fillerMeta = 1;
                } else if (bank) {
                    top = random.nextInt(5) < 2 ? ModBlocks.emberRock : ModBlocks.scorchedStone;
                    filler = ModBlocks.scorchedStone;
                } else if (crag > 0.3 + random.nextDouble() * 0.06
                    || rockNoise.fbm(x / 40.0, z / 40.0, 3) > 0.34 + random.nextDouble() * 0.04) {
                        int roll = random.nextInt(12);
                        top = roll == 0 ? ModBlocks.emberRock
                            : roll < 3 ? ModBlocks.charredEarth : ModBlocks.scorchedStone;
                        filler = ModBlocks.scorchedStone;
                    } else if (earthNoise.fbm(x / 75.0, z / 75.0, 3) > 0.24 + random.nextDouble() * 0.05) {
                        // Bare brown earth: coarse dirt, which grass never takes, some podzol and gravel.
                        int roll = random.nextInt(20);
                        if (roll < 2) {
                            top = Blocks.gravel;
                        } else if (roll < 5) {
                            top = ModBlocks.charredEarth;
                        } else {
                            top = Blocks.dirt;
                            topMeta = roll < 8 ? 2 : 1;
                        }
                        filler = Blocks.dirt;
                        fillerMeta = 1;
                    } else {
                        int roll = random.nextInt(150);
                        top = roll < 12 ? ModBlocks.scorchedStone
                            : roll < 16 ? ModBlocks.emberRock : roll == 16 ? ModBlocks.ash : ModBlocks.charredEarth;
                        filler = ModBlocks.charredEarth;
                    }

                for (int y = 0; y <= Math.max(ground, liquidTop); y++) {
                    Block block;
                    int meta = 0;
                    if (y == 0 || y <= 2 && random.nextInt(y + 1) == 0) {
                        block = Blocks.bedrock;
                    } else if (y > ground) {
                        block = liquid;
                    } else if (y < ground - 3) {
                        // Scorched stone in the canyon walls, plain stone deep down.
                        block = y > RIVER - 4 ? ModBlocks.scorchedStone : Blocks.stone;
                    } else if (y < ground) {
                        block = filler;
                        meta = fillerMeta;
                    } else {
                        block = top;
                        meta = topMeta;
                    }
                    blocks[index + y] = block;
                    metadata[index + y] = (byte) meta;
                }
                for (int y = ledgeFrom; y <= ledgeTo; y++) {
                    blocks[index + y] = y == ledgeTo && random.nextInt(3) == 0 ? ModBlocks.emberRock
                        : ModBlocks.scorchedStone;
                }
            }
        }

        Chunk chunk = new Chunk(world, blocks, metadata, chunkX, chunkZ);
        System.arraycopy(biomes, 0, chunk.getBiomeArray(), 0, biomes.length);
        chunk.generateSkylightMap();
        return chunk;
    }

    /** How many blocks a river column is from its nearer bank, up to 3. */
    private static int riverDepth(boolean[][] river, int gx, int gz) {
        for (int d = 1; d <= 2; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (!river[gx + dx][gz + dz]) {
                        return d;
                    }
                }
            }
        }
        return 3;
    }

    /**
     * The highest ground on the bank within two blocks of a river column and how far it is, 1 or 2; null in the
     * middle of a wide river.
     */
    private static int[] bankBeside(Column[][] columns, boolean[][] river, int gx, int gz) {
        for (int d = 1; d <= 2; d++) {
            int highest = -1;
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    Column column = columns[gx + dx][gz + dz];
                    if (!river[gx + dx][gz + dz] && column.liquid == null) {
                        highest = Math.max(highest, column.ground);
                    }
                }
            }
            if (highest >= 0) {
                return new int[] { highest, d };
            }
        }
        return null;
    }

    /** Whether lava lies within two blocks of a column, for the glowing rock along its banks. */
    private static boolean lavaNear(Column[][] columns, boolean[][] river, int gx, int gz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (river[gx + dx][gz + dz] || columns[gx + dx][gz + dz].liquid == Blocks.lava) {
                    return true;
                }
            }
        }
        return false;
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
     * Ruins and fortresses first, then small potholes of lava, dead trees in groves and here and there, dead bushes on
     * the bare earth, and mushrooms in clumps, none near where teams start or on what stands on the map; and the
     * mushroom patches round where they do.
     */
    @Override
    public void populate(IChunkProvider provider, int chunkX, int chunkZ) {
        int originX = chunkX * 16 + 8;
        int originZ = chunkZ * 16 + 8;
        if (world instanceof WorldServer) {
            for (Forinthry.Keep keep : layout.getKeeps()) {
                if (keep.covers(originX, originZ, 0) || keep.covers(originX + 15, originZ + 15, 0)
                    || keep.covers(originX + 15, originZ, 0)
                    || keep.covers(originX, originZ + 15, 0)) {
                    Ruins.oldFortress(
                        (WorldServer) world,
                        keep,
                        levelAt(keep.getCenterX(), keep.getCenterZ()),
                        chunkX,
                        chunkZ,
                        seed);
                }
            }
            Ruins.populate((WorldServer) world, chunkX, chunkZ, seed);
        }
        random.setSeed(chunkX * 341873128712L + chunkZ * 132897987541L + seed + 1);

        if (random.nextInt(5) < 2) {
            pothole(originX + 2 + random.nextInt(12), originZ + 2 + random.nextInt(12));
        }

        double grove = groveNoise.fbm((originX + 8) / 110.0, (originZ + 8) / 110.0, 3);
        int trees = grove > 0.15 ? (int) ((grove - 0.15) * 16) + 1 : random.nextInt(4) == 0 ? 1 : 0;
        for (int i = 0; i < trees; i++) {
            int x = originX + random.nextInt(16);
            int z = originZ + random.nextInt(16);
            if (canGrow(x, z, 2)) {
                deadTree(x, world.getHeightValue(x, z), z);
            }
        }

        for (int i = 0; i < 6; i++) {
            int x = originX + random.nextInt(16);
            int z = originZ + random.nextInt(16);
            int y = world.getHeightValue(x, z);
            if (world.getBlock(x, y - 1, z) == Blocks.dirt && world.isAirBlock(x, y, z) && canGrow(x, z, 0)) {
                world.setBlock(x, y, z, Blocks.deadbush, 0, 2);
            }
        }

        for (int[] patch : layout.getMushroomPatches()) {
            if (patch[0] >= originX && patch[0] < originX + 16 && patch[1] >= originZ && patch[1] < originZ + 16) {
                Block kind = random.nextBoolean() ? Blocks.red_mushroom : Blocks.brown_mushroom;
                int count = 4 + random.nextInt(3);
                for (int i = 0; i < count; i++) {
                    int x = patch[0] + random.nextInt(5) - 2;
                    int z = patch[1] + random.nextInt(5) - 2;
                    if (onOpenGround(x, z)) {
                        mushroom(x, world.getHeightValue(x, z), z, kind);
                    }
                }
            }
        }

        if (random.nextInt(100) < 30) {
            int cx = originX + 3 + random.nextInt(10);
            int cz = originZ + 3 + random.nextInt(10);
            Block kind = random.nextBoolean() ? Blocks.red_mushroom : Blocks.brown_mushroom;
            int count = 1 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                int x = cx + random.nextInt(5) - 2;
                int z = cz + random.nextInt(5) - 2;
                if (canGrow(x, z, 0)) {
                    mushroom(x, world.getHeightValue(x, z), z, kind);
                }
            }
        }
    }

    /** Whether something may grow at x, z: on open burnt ground, clear of features, bases and what stands. */
    private boolean canGrow(int x, int z, int reach) {
        int y = world.getHeightValue(x, z);
        Block ground = world.getBlock(x, y - 1, z);
        if (ground != ModBlocks.charredEarth && ground != Blocks.dirt
            && ground != ModBlocks.scorchedStone
            && ground != ModBlocks.ash) {
            return false;
        }
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (!world.isAirBlock(x + dx, y, z + dz) || isCutOff(x + dx, z + dz)) {
                    return false;
                }
            }
        }
        return !layout.nearFeature(x, z, 1, 1, reach + 2) && !ChunkProviderRts.keptClear(layout.getBases(), x, z);
    }

    /** Whether x, z is open, unbuilt, burnt ground, for a mushroom by where a team starts. */
    private boolean onOpenGround(int x, int z) {
        int y = world.getHeightValue(x, z);
        Block ground = world.getBlock(x, y - 1, z);
        return (ground == ModBlocks.charredEarth || ground == Blocks.dirt
            || ground == ModBlocks.scorchedStone
            || ground == ModBlocks.ash) && world.isAirBlock(x, y, z) && !isCutOff(x, z);
    }

    /**
     * A little pit of lava sunk in flat ground, one to three across, with glowing rock round its rim: only where the
     * ground round it is level, so it stays in.
     */
    private void pothole(int x, int z) {
        if (layout.nearBase(x, z, 0) || layout.nearFeature(x, z, 1, 1, 6)) {
            return;
        }
        int y = world.getHeightValue(x, z) - 1;
        int across = 1 + random.nextInt(3);
        for (int dx = -1; dx <= across; dx++) {
            for (int dz = -1; dz <= across; dz++) {
                Block block = world.getBlock(x + dx, y, z + dz);
                if (world.getHeightValue(x + dx, z + dz) - 1 != y || !block.isOpaqueCube()
                    || block == Blocks.lava
                    || isCutOff(x + dx, z + dz)) {
                    return;
                }
            }
        }
        for (int dx = -1; dx <= across; dx++) {
            for (int dz = -1; dz <= across; dz++) {
                boolean rim = dx < 0 || dz < 0 || dx == across || dz == across;
                boolean corner = (dx < 0 || dx == across) && (dz < 0 || dz == across);
                if (rim) {
                    if (!corner && random.nextInt(3) > 0) {
                        world.setBlock(x + dx, y, z + dz, ModBlocks.emberRock, 0, 2);
                    }
                } else if (across < 3 || random.nextInt(6) > 0) {
                    world.setBlock(x + dx, y, z + dz, Blocks.lava, 0, 2);
                    world.setBlock(x + dx, y - 1, z + dz, ModBlocks.scorchedStone, 0, 2);
                }
            }
        }
    }

    /**
     * A dead tree: a bare dark oak trunk, two to six high, burnt black and leafless, with a few crooked branches; now
     * and then just a broken stump. On the Wilderness's burnt ground, workers can chop it for its wood.
     */
    private void deadTree(int x, int y, int z) {
        int height = random.nextInt(6) == 0 ? 1 + random.nextInt(2) : 3 + random.nextInt(4);
        for (int i = 0; i < height; i++) {
            world.setBlock(x, y + i, z, Blocks.log2, 1, 2);
        }
        if (height < 3) {
            return;
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
                    world.setBlock(bx, up, bz, Blocks.log2, i == length ? 1 : 1 | side[2], 2);
                }
            }
        }
    }

    /**
     * A mushroom, the Wilderness's food, on a little patch of podzol, which keeps a mushroom whatever the light;
     * workers harvest it with hoes.
     */
    private void mushroom(int x, int y, int z, Block kind) {
        if (!world.isAirBlock(x, y, z)) {
            return;
        }
        world.setBlock(x, y - 1, z, Blocks.dirt, 2, 2);
        world.setBlock(x, y, z, kind, 0, 2);
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
