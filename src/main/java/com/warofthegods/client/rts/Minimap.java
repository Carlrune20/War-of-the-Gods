package com.warofthegods.client.rts;

import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import org.lwjgl.opengl.GL11;

/**
 * The little map in a corner of the RTS view: the whole game map as far as the client has seen it, with buildings,
 * units and hostile mobs on top and the part the camera looks at outlined. Terrain is read from the chunks the client
 * has, a slice of the map each frame, so it fills in as the camera goes about.
 */
final class Minimap {

    /** Pixels across the terrain image. */
    static final int PIXELS = 128;
    /** Map pixels sampled per frame. */
    private static final int SAMPLES_PER_FRAME = 1024;
    private static final int UNKNOWN = 0xFF1A1E22;

    private final Minecraft mc = Minecraft.getMinecraft();
    private DynamicTexture texture;
    private ResourceLocation location;
    private int[] heights;
    private int cursor;
    private boolean dirty;
    /** The map the image is of, to start afresh when a new game starts. */
    private int minX;
    private int minZ;
    private int maxX;
    private int maxZ;
    private World world;
    /** The fog of war over the map, and the fog's version it shows. */
    private DynamicTexture fogTexture;
    private ResourceLocation fogLocation;
    private int fogVersion = -1;

    /** Samples a slice of the map; call once a frame. */
    void update(RtsState.GameInfo game) {
        if (game == null || mc.theWorld == null) {
            return;
        }
        if (texture == null || game.minX != minX
            || game.minZ != minZ
            || game.maxX != maxX
            || game.maxZ != maxZ
            || world != mc.theWorld) {
            reset(game);
        }
        int[] pixels = texture.getTextureData();
        for (int n = 0; n < SAMPLES_PER_FRAME; n++) {
            int i = cursor % PIXELS;
            int j = cursor / PIXELS;
            cursor = (cursor + 1) % (PIXELS * PIXELS);
            int color = sample(i, j, game.seaLevel);
            if (color != 0 && pixels[j * PIXELS + i] != color) {
                pixels[j * PIXELS + i] = color;
                dirty = true;
            }
        }
    }

    private void reset(RtsState.GameInfo game) {
        if (texture == null) {
            texture = new DynamicTexture(PIXELS, PIXELS);
            location = mc.getTextureManager()
                .getDynamicTextureLocation("warofthegods_minimap", texture);
        }
        Arrays.fill(texture.getTextureData(), UNKNOWN);
        heights = new int[PIXELS * PIXELS];
        minX = game.minX;
        minZ = game.minZ;
        maxX = game.maxX;
        maxZ = game.maxZ;
        world = mc.theWorld;
        cursor = 0;
        dirty = true;
    }

    /** The color of map pixel i, j, shaded by slope as on a paper map; 0 if that part has not been seen. */
    private int sample(int i, int j, int seaLevel) {
        int x = minX + (int) ((i + 0.5) * (maxX - minX + 1) / PIXELS);
        int z = minZ + (int) ((j + 0.5) * (maxZ - minZ + 1) / PIXELS);
        Chunk chunk = world.getChunkFromBlockCoords(x, z);
        if (chunk == null || chunk.isEmpty()) {
            return 0;
        }
        int lx = x & 15;
        int lz = z & 15;
        int y = Math.min(255, chunk.getHeightValue(lx, lz));
        MapColor color = MapColor.airColor;
        while (y > 0 && color == MapColor.airColor) {
            y--;
            Block block = chunk.getBlock(lx, y, lz);
            color = block.getMapColor(chunk.getBlockMetadata(lx, y, lz));
        }
        if (color == MapColor.airColor) {
            return UNKNOWN;
        }
        heights[j * PIXELS + i] = y;
        int above = j > 0 ? heights[(j - 1) * PIXELS + i] : y;
        double shade = color == MapColor.waterColor ? 0.86 : y > above ? 1.08 : y < above ? 0.82 : 0.95;
        if (color != MapColor.waterColor) {
            shade *= 0.9 + Math.max(-0.1, Math.min(0.15, (y - seaLevel) / 120.0));
        }
        int rgb = color.colorValue;
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * shade));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * shade));
        int b = Math.min(255, (int) ((rgb & 0xFF) * shade));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The world x, z at a point on the minimap drawn at left, top, size across; null if off it. */
    double[] worldAt(int gx, int gy, int left, int top, int size) {
        if (gx < left || gy < top || gx >= left + size || gy >= top + size) {
            return null;
        }
        return new double[] { minX + (gx - left + 0.5) * (maxX - minX + 1) / size,
            minZ + (gy - top + 0.5) * (maxZ - minZ + 1) / size };
    }

    private double toX(double x, int left, int size) {
        return left + (x - minX) * size / (maxX - minX + 1);
    }

    private double toY(double z, int top, int size) {
        return top + (z - minZ) * size / (maxZ - minZ + 1);
    }

    /** Draws the map with what is on it; frame is the camera's view on the ground, as four x, z corners, or null. */
    void draw(RtsClient client, int left, int top, int size, double[][] frame) {
        if (texture == null) {
            return;
        }
        if (dirty) {
            texture.updateDynamicTexture();
            dirty = false;
        }
        Gui.drawRect(left - 2, top - 2, left + size + 2, top + size + 2, 0xE0101418);
        mc.getTextureManager()
            .bindTexture(location);
        GL11.glColor4f(1, 1, 1, 1);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(left, top + size, 0, 0, 1);
        tessellator.addVertexWithUV(left + size, top + size, 0, 1, 1);
        tessellator.addVertexWithUV(left + size, top, 0, 1, 0);
        tessellator.addVertexWithUV(left, top, 0, 0, 0);
        tessellator.draw();
        FogOfWar fog = client.fog;
        if (fog.isOn()) {
            drawFog(fog, left, top, size);
        }

        RtsState state = client.state;
        for (RtsState.BuildingInfo b : state.buildings.values()) {
            if (state.isEnemyTeam(b.team) && !fog.isExplored(b.x, b.z, b.sizeX, b.sizeZ)) {
                continue;
            }
            RtsState.TeamInfo team = state.teamsById.get(b.team);
            int color = team == null ? 0xFFFFFF : team.color;
            int x0 = (int) Math.floor(toX(b.x, left, size));
            int y0 = (int) Math.floor(toY(b.z, top, size));
            int x1 = Math.max(x0 + 2, (int) Math.ceil(toX(b.x + b.sizeX, left, size)));
            int y1 = Math.max(y0 + 2, (int) Math.ceil(toY(b.z + b.sizeZ, top, size)));
            Gui.drawRect(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF000000);
            Gui.drawRect(x0, y0, x1, y1, (b.complete ? 0xFF000000 : 0x90000000) | color);
        }
        for (RtsState.MobInfo mob : state.mobs.values()) {
            if (mob.hostile && fog.isVisible(mob.x, mob.z)) {
                dot(mob.x, mob.z, left, top, size, 0xFF8A1010);
            }
        }
        for (RtsState.UnitInfo unit : state.units.values()) {
            if (!fog.isVisible(unit.x, unit.z)) {
                continue;
            }
            RtsState.TeamInfo team = state.teamsById.get(unit.team);
            int color = team == null ? 0xFFFFFF : team.color;
            dot(unit.x, unit.z, left, top, size, client.selected.contains(unit.id) ? 0xFFFFFFFF : 0xFF000000 | color);
        }
        if (frame != null) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glColor4f(1, 1, 1, 0.9F);
            GL11.glLineWidth(1.5F);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            scissor(left, top, size);
            tessellator.startDrawing(GL11.GL_LINE_LOOP);
            for (double[] corner : frame) {
                tessellator.addVertex(toX(corner[0], left, size), toY(corner[1], top, size), 0);
            }
            tessellator.draw();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
    }

    /** Black over what the viewer's team has not explored. */
    private void drawFog(FogOfWar fog, int left, int top, int size) {
        if (fogTexture == null) {
            fogTexture = new DynamicTexture(PIXELS, PIXELS);
            fogLocation = mc.getTextureManager()
                .getDynamicTextureLocation("warofthegods_minimap_fog", fogTexture);
        }
        if (fogVersion != fog.version()) {
            fogVersion = fog.version();
            int[] pixels = fogTexture.getTextureData();
            for (int j = 0; j < PIXELS; j++) {
                double z = minZ + (j + 0.5) * (maxZ - minZ + 1) / PIXELS;
                for (int i = 0; i < PIXELS; i++) {
                    double x = minX + (i + 0.5) * (maxX - minX + 1) / PIXELS;
                    pixels[j * PIXELS + i] = fog.isExplored(x, z) ? 0 : 0xFF000000;
                }
            }
            fogTexture.updateDynamicTexture();
        }
        mc.getTextureManager()
            .bindTexture(fogLocation);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1, 1, 1, 1);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(left, top + size, 0, 0, 1);
        tessellator.addVertexWithUV(left + size, top + size, 0, 1, 1);
        tessellator.addVertexWithUV(left + size, top, 0, 1, 0);
        tessellator.addVertexWithUV(left, top, 0, 0, 0);
        tessellator.draw();
    }

    private void dot(double x, double z, int left, int top, int size, int color) {
        int px = (int) toX(x, left, size);
        int py = (int) toY(z, top, size);
        if (px >= left && py >= top && px < left + size && py < top + size) {
            Gui.drawRect(px - 1, py - 1, px + 1, py + 1, color);
        }
    }

    /** Keeps lines inside the map, in screen pixels rather than the scaled GUI. */
    private void scissor(int left, int top, int size) {
        double scale = mc.displayWidth
            / (double) new net.minecraft.client.gui.ScaledResolution(mc, mc.displayWidth, mc.displayHeight)
                .getScaledWidth();
        GL11.glScissor(
            (int) (left * scale),
            (int) (mc.displayHeight - (top + size) * scale),
            (int) (size * scale),
            (int) (size * scale));
    }
}
