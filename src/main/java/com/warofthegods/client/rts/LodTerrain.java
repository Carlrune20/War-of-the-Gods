package com.warofthegods.client.rts;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.block.ModBlocks;
import com.warofthegods.rts.TerrainMessage;
import com.warofthegods.web.MapTiles;

import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * The map's surface beyond the chunks the client has, drawn in the RTS view so the whole map can be seen at once.
 *
 * <p>
 * The server sends every chunk's columns as the web map has them ({@link TerrainMessage}); of each column only the top
 * is kept, its height and block. The surface is drawn the way Distant Horizons draws its levels of detail (LGPL-3.0,
 * https://gitlab.com/distant-horizons-team/distant-horizons): in square cells of 1, 2, 4, 8 or 16 blocks, coarser the
 * farther a chunk is from the camera, each cell a box as high as its highest column in the average colour of its
 * columns, with only the sides that a lower neighbour leaves open, shaded by the way they face. A chunk's outer sides
 * reach down to the lowest column across its edge, so chunks drawn at different detail meet without gaps.
 *
 * <p>
 * It is drawn before the world, right after the sky, with a far plane of its own, and the depth buffer is cleared
 * after: the chunks Minecraft draws then always cover it. Chunk columns Minecraft has built and draws are left out;
 * until then the surface stands in for them. A chunk's mesh is only replaced once its new one is made, so no chunk
 * ever shows the sky through it. Fog of war darkens it as it darkens the world, through a texture with a texel per fog
 * cell laid over it.
 */
final class LodTerrain {

    /** Detail levels: cells of 1 << level blocks. */
    private static final int LEVELS = 5;
    /**
     * Time a frame may spend making chunk meshes at the detail they want, so a new view fills in over a few frames
     * rather than stalling one. A chunk with no mesh at all always gets its coarsest, which is quick.
     */
    private static final long BUILD_NANOS = 3_000_000;
    /** How far past a level's bounds a chunk's distance must go before it changes level, so it does not flip. */
    private static final double LEVEL_SLACK = 1.25;
    private static final float NEAR = 0.5F;
    private static final float FAR = 8192;
    /** How sides are shaded by the way they face, as Minecraft shades blocks: north and south, then east and west. */
    private static final double SHADE_NS = 0.8;
    private static final double SHADE_EW = 0.6;
    /** How much darker water gets for each block of depth, down to at most {@link #MAX_DEPTH} blocks. */
    private static final double DEPTH_DARKENING = 0.04;
    private static final int MAX_DEPTH = 8;

    /** The top of one chunk's columns. */
    static final class LodChunk {

        final int x;
        final int z;
        /** Per column (x + z * 16), the y just above its top block, or 0 for a column with nothing in it. */
        final int[] tops = new int[256];
        /** Per column, its top block, as id << 4 | metadata in the map's stored ids. */
        final int[] blocks = new int[256];
        /** Per column, how much water lies over the ground. */
        final byte[] depths = new byte[256];
        int minTop = 255;
        int maxTop;
        /** Display list for each level, 0 where none has been made. */
        final int[] lists = new int[LEVELS];
        /** Lists made before the chunk or a neighbour changed: still drawn, and made again when there is time. */
        final boolean[] stale = new boolean[LEVELS];
        /** The level drawn last, or -1. */
        int level = -1;
        /** The lowest y any of its lists reaches, cliff sides included. */
        int bottom;

        LodChunk(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    private static final class Received {

        final String mapId;
        final List<LodChunk> chunks;

        Received(String mapId, List<LodChunk> chunks) {
            this.mapId = mapId;
            this.chunks = chunks;
        }
    }

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Queue<Received> inbox = new ConcurrentLinkedQueue<>();
    private final Map<Long, LodChunk> chunks = new HashMap<>();
    final BlockColors colors = new BlockColors();
    /** The map the chunks are of, or null before any came. */
    private String mapId;
    private World world;

    /** Chunk columns Minecraft draws this frame, so their surface is left out. */
    private final Set<Long> covered = new HashSet<>();
    private boolean lookedUp;
    private Field renderersField;
    private Field isInitialized;

    private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer plane = BufferUtils.createFloatBuffer(4);
    private DynamicTexture fogTexture;
    private int fogWidth;
    private int fogHeight;
    private int fogVersion = -1;
    private float fogDaylight = -1;

    private static long key(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
    }

    /** Some of the map's surface from the server; called on a Netty thread. */
    void receive(TerrainMessage message) {
        List<LodChunk> decoded = new ArrayList<>();
        for (int i = 0; i < message.chunks.length; i++) {
            int index = message.indexes[i];
            int chunkX = message.tileX * MapTiles.CHUNKS_PER_TILE + index % MapTiles.CHUNKS_PER_TILE;
            int chunkZ = message.tileZ * MapTiles.CHUNKS_PER_TILE + index / MapTiles.CHUNKS_PER_TILE;
            LodChunk chunk = decode(chunkX, chunkZ, message.chunks[i]);
            if (chunk != null) {
                decoded.add(chunk);
            }
        }
        inbox.add(new Received(message.mapId, decoded));
    }

    /**
     * Reads a chunk as {@link MapTiles} encodes it: per column a base and a count, then each column's blocks from the
     * base up. A column's top is its highest block that is not air nor something thin, such as grass or a torch, that
     * would make it look higher than it is; null if the data does not add up.
     */
    static LodChunk decode(int chunkX, int chunkZ, byte[] data) {
        if (data.length < 512) {
            return null;
        }
        LodChunk chunk = new LodChunk(chunkX, chunkZ);
        int pos = 512;
        for (int index = 0; index < 256; index++) {
            int base = data[index * 2] & 0xFF;
            int count = data[index * 2 + 1] & 0xFF;
            if (pos + count * 2 > data.length) {
                return null;
            }
            int top = -1;
            int highest = -1;
            for (int k = count - 1; k >= 0; k--) {
                int value = (data[pos + k * 2] & 0xFF) << 8 | data[pos + k * 2 + 1] & 0xFF;
                Material material = ModBlocks.fromStored(value >> 4)
                    .getMaterial();
                if (material == Material.air) {
                    continue;
                }
                if (highest < 0) {
                    highest = k;
                }
                if (material.blocksMovement() || material.isLiquid() || material == Material.leaves) {
                    top = k;
                    break;
                }
            }
            if (top < 0) {
                top = highest;
            }
            if (top >= 0) {
                int value = (data[pos + top * 2] & 0xFF) << 8 | data[pos + top * 2 + 1] & 0xFF;
                chunk.tops[index] = base + top + 1;
                chunk.blocks[index] = value;
                Block block = ModBlocks.fromStored(value >> 4);
                if (block.getMaterial() == Material.water) {
                    int depth = 0;
                    for (int k = top - 1; k >= 0 && depth < MAX_DEPTH; k--) {
                        int below = (data[pos + k * 2] & 0xFF) << 8 | data[pos + k * 2 + 1] & 0xFF;
                        if (ModBlocks.fromStored(below >> 4)
                            .getMaterial() != Material.water) {
                            break;
                        }
                        depth++;
                    }
                    chunk.depths[index] = (byte) depth;
                }
                chunk.minTop = Math.min(chunk.minTop, chunk.tops[index]);
                chunk.maxTop = Math.max(chunk.maxTop, chunk.tops[index]);
            }
            pos += count * 2;
        }
        if (chunk.maxTop == 0) {
            chunk.minTop = 0;
        }
        chunk.bottom = Math.max(0, chunk.minTop - 1);
        return chunk;
    }

    /** The map the client holds the surface of, to tell the server on entering the view; null if none. */
    String mapId() {
        return mapId;
    }

    boolean isEmpty() {
        return chunks.isEmpty();
    }

    /** Takes in what arrived; call every client tick. Everything is let go when the client leaves the world. */
    void update() {
        if (mc.theWorld != world) {
            clear();
            world = mc.theWorld;
        }
        if (world == null) {
            inbox.clear();
            return;
        }
        Received received;
        while ((received = inbox.poll()) != null) {
            if (!received.mapId.equals(mapId)) {
                clear();
                mapId = received.mapId;
            }
            for (LodChunk chunk : received.chunks) {
                LodChunk old = chunks.put(key(chunk.x, chunk.z), chunk);
                if (old != null) {
                    // The old meshes stay on show until new ones are made, so nothing blinks out meanwhile.
                    System.arraycopy(old.lists, 0, chunk.lists, 0, LEVELS);
                    chunk.level = old.level;
                    chunk.bottom = Math.min(chunk.bottom, old.bottom);
                    markStale(chunk);
                }
                // The neighbours' sides reach down to this chunk's edge.
                markStale(chunks.get(key(chunk.x + 1, chunk.z)));
                markStale(chunks.get(key(chunk.x - 1, chunk.z)));
                markStale(chunks.get(key(chunk.x, chunk.z + 1)));
                markStale(chunks.get(key(chunk.x, chunk.z - 1)));
            }
        }
    }

    private void clear() {
        for (LodChunk chunk : chunks.values()) {
            deleteLists(chunk);
        }
        chunks.clear();
        mapId = null;
    }

    private static void markStale(LodChunk chunk) {
        if (chunk != null) {
            for (int i = 0; i < LEVELS; i++) {
                chunk.stale[i] = chunk.lists[i] != 0;
            }
        }
    }

    private static void deleteLists(LodChunk chunk) {
        if (chunk == null) {
            return;
        }
        for (int i = 0; i < LEVELS; i++) {
            if (chunk.lists[i] != 0) {
                GLAllocation.deleteDisplayLists(chunk.lists[i]);
                chunk.lists[i] = 0;
            }
        }
    }

    /** The y just above the top block at x, z, or -1 where the surface is not known. */
    int top(int x, int z) {
        LodChunk chunk = chunks.get(key(x >> 4, z >> 4));
        return chunk == null ? -1 : chunk.tops[(x & 15) + (z & 15) * 16];
    }

    /** The colour of the top block at x, z as 0xRRGGBB, or -1 where the surface is not known or empty. */
    int color(int x, int z) {
        LodChunk chunk = chunks.get(key(x >> 4, z >> 4));
        int index = (x & 15) + (z & 15) * 16;
        return chunk == null || chunk.tops[index] == 0 ? -1 : columnColor(chunk, index);
    }

    /** Whether the top at x, z is water. */
    boolean isWater(int x, int z) {
        LodChunk chunk = chunks.get(key(x >> 4, z >> 4));
        int index = (x & 15) + (z & 15) * 16;
        return chunk != null && chunk.tops[index] > 0
            && ModBlocks.fromStored(chunk.blocks[index] >> 4)
                .getMaterial() == Material.water;
    }

    private int columnColor(LodChunk chunk, int index) {
        int color = colors.of(chunk.blocks[index]);
        int depth = chunk.depths[index];
        return depth > 0 ? BlockColors.shade(color, 1 - depth * DEPTH_DARKENING) : color;
    }

    // ---------------------------------------------------------------- drawing

    /** Draws the surface; call right after the sky is drawn, while the view is open. */
    void render(RtsClient client, float partialTicks) {
        Entity view = mc.renderViewEntity;
        RtsCamera camera = client.getCamera();
        if (chunks.isEmpty() || view == null || camera == null || world == null) {
            return;
        }
        double ex = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double ey = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double ez = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;

        // Minecraft's projection, with this surface's own near and far planes.
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        matrix.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, matrix);
        matrix.put(10, -(FAR + NEAR) / (FAR - NEAR));
        matrix.put(14, -2 * FAR * NEAR / (FAR - NEAR));
        matrix.rewind();
        GL11.glLoadMatrix(matrix);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        // Made from the matrices as they are now, with the far plane moved out.
        Frustrum frustum = new Frustrum();
        frustum.setPosition(ex, ey, ez);

        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_TRANSFORM_BIT);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glColor4f(1, 1, 1, 1);
        FogOfWar fog = client.fog;
        boolean fogged = fog.isOn() && fog.columns() > 0;
        if (fogged) {
            bindFog(fog, partialTicks);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glTexGeni(GL11.GL_S, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR);
            GL11.glTexGeni(GL11.GL_T, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR);
            GL11.glEnable(GL11.GL_TEXTURE_GEN_S);
            GL11.glEnable(GL11.GL_TEXTURE_GEN_T);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        } else {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        int renderChunks = mc.gameSettings.renderDistanceChunks;
        double vanillaFar = renderChunks * 16 * 2;
        double blocksPerPixel = 2 * Math.tan(Math.toRadians(camera.fov) / 2) / Math.max(1, mc.displayHeight);
        double cellPixels = Config.rtsFarTerrainCellPixels;
        long buildUntil = System.nanoTime() + BUILD_NANOS;
        coveredColumns(ex, ey, ez, vanillaFar);
        for (LodChunk chunk : chunks.values()) {
            if (chunk.maxTop == 0) {
                continue;
            }
            double x0 = chunk.x * 16;
            double z0 = chunk.z * 16;
            if (!frustum.isBoxInFrustum(x0, chunk.bottom, z0, x0 + 16, chunk.maxTop, z0 + 16)
                || covered.contains(key(chunk.x, chunk.z))) {
                continue;
            }
            double dx = x0 + 8 - ex;
            double dy = (chunk.minTop + chunk.maxTop) / 2.0 - ey;
            double dz = z0 + 8 - ez;
            double pixelBlocks = Math.sqrt(dx * dx + dy * dy + dz * dz) * blocksPerPixel * cellPixels;
            int level = levelFor(chunk.level, pixelBlocks);
            int list = chunk.lists[level];
            boolean time = System.nanoTime() < buildUntil;
            if ((list == 0 || chunk.stale[level]) && time) {
                list = build(chunk, level);
            }
            if (list == 0) {
                // Not made yet: the nearest level there is will do for now, else the coarsest, made now.
                for (int step = 1; step < LEVELS && list == 0; step++) {
                    if (level + step < LEVELS) {
                        list = chunk.lists[level + step];
                    }
                    if (list == 0 && level - step >= 0) {
                        list = chunk.lists[level - step];
                    }
                }
                if (list == 0) {
                    list = build(chunk, LEVELS - 1);
                }
            } else {
                chunk.level = level;
            }
            if (fogged) {
                fogPlanes(fog, chunk);
            }
            GL11.glPushMatrix();
            GL11.glTranslated(x0 - ex, -ey, z0 - ez);
            GL11.glCallList(list);
            GL11.glPopMatrix();
        }

        if (fogged) {
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
        GL11.glPopAttrib();
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        // Minecraft culls entities with the frustum it worked out before the sky; work it out again from its matrices.
        ClippingHelperImpl.getInstance();
        // The world drawn next always covers the surface.
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glColor4f(1, 1, 1, 1);
    }

    /**
     * The level a chunk is drawn at, from how many blocks its cells may span and the level it had: each level is for
     * cells spanning up to twice its size, and a chunk only leaves its level once well past that.
     */
    static int levelFor(int current, double pixelBlocks) {
        int level = 0;
        while (level < LEVELS - 1 && (2 << level) <= pixelBlocks) {
            level++;
        }
        if (current >= 0 && current < LEVELS && level != current) {
            double low = current == 0 ? 0 : (1 << current) / LEVEL_SLACK;
            double high = current == LEVELS - 1 ? Double.MAX_VALUE : (2 << current) * LEVEL_SLACK;
            if (pixelBlocks >= low && pixelBlocks < high) {
                return current;
            }
        }
        return level;
    }

    /**
     * Works out {@link #covered}: the chunk columns Minecraft really draws this frame. Each section of the column
     * has been built since it last changed, and each that holds anything is in Minecraft's view and before its far
     * plane. A column whose blocks are still on their way, or are being built again after the camera moved, keeps its
     * surface until then. If Minecraft's renderers cannot be read, a column counts as drawn once the client has it and
     * it is in range, as near as can be told without them.
     */
    private void coveredColumns(double ex, double ey, double ez, double far) {
        covered.clear();
        WorldRenderer[] renderers = renderers();
        Map<Long, Boolean> columns = new HashMap<>();
        if (renderers != null) {
            try {
                for (WorldRenderer renderer : renderers) {
                    if (renderer == null) {
                        continue;
                    }
                    long key = key(renderer.posX >> 4, renderer.posZ >> 4);
                    boolean drawn = isInitialized.getBoolean(renderer) && !renderer.needsUpdate
                        && (renderer.skipAllRenderPasses() || renderer.isInFrustum);
                    Boolean before = columns.get(key);
                    columns.put(key, (before == null || before) && drawn);
                }
            } catch (IllegalAccessException e) {
                renderersField = null;
                columns.clear();
            }
        }
        int cameraX = (int) Math.floor(ex) >> 4;
        int cameraZ = (int) Math.floor(ez) >> 4;
        int range = mc.gameSettings.renderDistanceChunks;
        for (int x = cameraX - range; x <= cameraX + range; x++) {
            for (int z = cameraZ - range; z <= cameraZ + range; z++) {
                long key = key(x, z);
                LodChunk chunk = chunks.get(key);
                if (chunk == null || !loaded(x, z)) {
                    continue;
                }
                if (renderersField != null) {
                    if (!Boolean.TRUE.equals(columns.get(key))) {
                        continue;
                    }
                } else if (Math.abs(x - cameraX) > range - 1 || Math.abs(z - cameraZ) > range - 1
                    || !loaded(x + 1, z)
                    || !loaded(x - 1, z)
                    || !loaded(x, z + 1)
                    || !loaded(x, z - 1)) {
                        continue;
                    }
                // Minecraft's view of the section is from the frame before; past its far plane it draws nothing.
                double dx = Math.max(Math.abs(x * 16 - ex), Math.abs(x * 16 + 16 - ex));
                double dz = Math.max(Math.abs(z * 16 - ez), Math.abs(z * 16 + 16 - ez));
                double dy = Math.max(Math.abs(chunk.bottom - ey), Math.abs(chunk.maxTop - ey));
                if (dx * dx + dy * dy + dz * dz < far * far * 0.81) {
                    covered.add(key);
                }
            }
        }
    }

    /** Minecraft's chunk section renderers, or null if they cannot be read. */
    private WorldRenderer[] renderers() {
        if (!lookedUp) {
            lookedUp = true;
            try {
                renderersField = ReflectionHelper.findField(RenderGlobal.class, "worldRenderers", "field_72765_l");
                isInitialized = ReflectionHelper.findField(WorldRenderer.class, "isInitialized", "field_78915_A");
            } catch (RuntimeException e) {
                WarOfTheGods.LOG.warn("Cannot read Minecraft's chunk renderers; the far terrain may blink", e);
                renderersField = null;
            }
        }
        if (renderersField == null) {
            return null;
        }
        try {
            return (WorldRenderer[]) renderersField.get(mc.renderGlobal);
        } catch (IllegalAccessException | ClassCastException e) {
            renderersField = null;
            return null;
        }
    }

    private boolean loaded(int chunkX, int chunkZ) {
        return !world.getChunkFromChunkCoords(chunkX, chunkZ)
            .isEmpty();
    }

    /** Makes the chunk's display list for a level; 0 if it could not be made. */
    private int build(LodChunk chunk, int level) {
        int size = 1 << level;
        int cells = 16 / size;
        int[] heights = new int[cells * cells];
        int[] colors = new int[cells * cells];
        for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                int height = 0;
                long r = 0;
                long g = 0;
                long b = 0;
                int count = 0;
                for (int z = j * size; z < (j + 1) * size; z++) {
                    for (int x = i * size; x < (i + 1) * size; x++) {
                        int index = x + z * 16;
                        if (chunk.tops[index] == 0) {
                            continue;
                        }
                        height = Math.max(height, chunk.tops[index]);
                        int color = columnColor(chunk, index);
                        r += (color >> 16) & 0xFF;
                        g += (color >> 8) & 0xFF;
                        b += color & 0xFF;
                        count++;
                    }
                }
                heights[i + j * cells] = height;
                if (count > 0) {
                    colors[i + j * cells] = (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
                }
            }
        }
        LodChunk east = chunks.get(key(chunk.x + 1, chunk.z));
        LodChunk west = chunks.get(key(chunk.x - 1, chunk.z));
        LodChunk south = chunks.get(key(chunk.x, chunk.z + 1));
        LodChunk north = chunks.get(key(chunk.x, chunk.z - 1));

        int list = chunk.lists[level] != 0 ? chunk.lists[level] : GLAllocation.generateDisplayLists(1);
        GL11.glNewList(list, GL11.GL_COMPILE);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        int bottom = chunk.minTop - 1;
        for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                int h = heights[i + j * cells];
                if (h == 0) {
                    continue;
                }
                int color = colors[i + j * cells];
                int x0 = i * size;
                int x1 = x0 + size;
                int z0 = j * size;
                int z1 = z0 + size;
                t.setColorOpaque_I(color);
                t.addVertex(x0, h, z0);
                t.addVertex(x0, h, z1);
                t.addVertex(x1, h, z1);
                t.addVertex(x1, h, z0);
                // East and west.
                int hEast = i + 1 < cells ? heights[i + 1 + j * cells] : edgeLow(east, 0, z0, 0, size);
                int hWest = i > 0 ? heights[i - 1 + j * cells] : edgeLow(west, 15, z0, 0, size);
                // South and north.
                int hSouth = j + 1 < cells ? heights[i + (j + 1) * cells] : edgeLow(south, x0, 0, size, 0);
                int hNorth = j > 0 ? heights[i + (j - 1) * cells] : edgeLow(north, x0, 15, size, 0);
                bottom = Math.min(bottom, Math.min(Math.min(hEast, hWest), Math.min(hSouth, hNorth)));
                if (hEast < h || hWest < h) {
                    t.setColorOpaque_I(BlockColors.shade(color, SHADE_EW));
                    if (hEast < h) {
                        t.addVertex(x1, h, z0);
                        t.addVertex(x1, h, z1);
                        t.addVertex(x1, hEast, z1);
                        t.addVertex(x1, hEast, z0);
                    }
                    if (hWest < h) {
                        t.addVertex(x0, h, z0);
                        t.addVertex(x0, hWest, z0);
                        t.addVertex(x0, hWest, z1);
                        t.addVertex(x0, h, z1);
                    }
                }
                if (hSouth < h || hNorth < h) {
                    t.setColorOpaque_I(BlockColors.shade(color, SHADE_NS));
                    if (hSouth < h) {
                        t.addVertex(x0, h, z1);
                        t.addVertex(x0, hSouth, z1);
                        t.addVertex(x1, hSouth, z1);
                        t.addVertex(x1, h, z1);
                    }
                    if (hNorth < h) {
                        t.addVertex(x0, h, z0);
                        t.addVertex(x1, h, z0);
                        t.addVertex(x1, hNorth, z0);
                        t.addVertex(x0, hNorth, z0);
                    }
                }
            }
        }
        t.draw();
        GL11.glEndList();
        chunk.lists[level] = list;
        chunk.stale[level] = false;
        chunk.bottom = Math.max(0, Math.min(chunk.bottom, bottom));
        return list;
    }

    /**
     * The lowest column top of a neighbouring chunk along a stretch of its edge, starting at x, z and going dx or dz
     * blocks; 0, down to the bottom, where the neighbour is not known.
     */
    private static int edgeLow(LodChunk neighbour, int x, int z, int dx, int dz) {
        if (neighbour == null) {
            return 0;
        }
        int low = 255;
        for (int n = 0; n < Math.max(dx, dz); n++) {
            low = Math.min(low, neighbour.tops[x + n * Math.min(dx, 1) + (z + n * Math.min(dz, 1)) * 16]);
        }
        return low;
    }

    /**
     * Binds the fog of war as a texture, a texel per fog cell with a dark border round the map, made again when the
     * fog or the daylight changes.
     */
    private void bindFog(FogOfWar fog, float partialTicks) {
        int width = fog.columns() + 2;
        int height = fog.rows() + 2;
        // Lit by the fog, the world darkens at night; so does the surface, in steps so it is not made every frame.
        float daylight = Math.round((world.getSunBrightness(partialTicks) * 0.95F + 0.05F) * 32) / 32F;
        if (fogTexture == null || fogWidth != width || fogHeight != height) {
            if (fogTexture != null) {
                fogTexture.deleteGlTexture();
            }
            fogTexture = new DynamicTexture(width, height);
            fogWidth = width;
            fogHeight = height;
            fogVersion = -1;
        }
        if (fogVersion != fog.version() || fogDaylight != daylight) {
            fogVersion = fog.version();
            fogDaylight = daylight;
            int[] pixels = fogTexture.getTextureData();
            for (int r = 0; r < height; r++) {
                for (int c = 0; c < width; c++) {
                    int v = (int) (fog.brightness(c - 1, r - 1) * daylight * 255);
                    pixels[r * width + c] = 0xFF000000 | v << 16 | v << 8 | v;
                }
            }
            fogTexture.updateDynamicTexture();
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fogTexture.getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    /** Lays the fog texture over a chunk, whose display list has its corner at 0, 0. */
    private void fogPlanes(FogOfWar fog, LodChunk chunk) {
        float across = FogOfWar.CELL * fogWidth;
        float down = FogOfWar.CELL * fogHeight;
        plane.clear();
        plane.put(1 / across)
            .put(0)
            .put(0)
            .put((chunk.x * 16 - fog.minX() + FogOfWar.CELL) / across);
        plane.flip();
        GL11.glTexGen(GL11.GL_S, GL11.GL_OBJECT_PLANE, plane);
        plane.clear();
        plane.put(0)
            .put(0)
            .put(1 / down)
            .put((chunk.z * 16 - fog.minZ() + FogOfWar.CELL) / down);
        plane.flip();
        GL11.glTexGen(GL11.GL_T, GL11.GL_OBJECT_PLANE, plane);
    }
}
