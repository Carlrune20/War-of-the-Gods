package com.warofthegods.client.rts;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.material.MapColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.biome.BiomeGenBase;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.warofthegods.block.ModBlocks;

/**
 * The colour a block shows from above, for the map's surface drawn far off: the average of its top texture as the
 * block atlas holds it, tinted like the map's plains for grass and leaves. Read from the atlas the first time it is
 * needed, which must be on the client thread; a block whose texture cannot be had gets its map colour.
 */
final class BlockColors {

    /** Most pixels of block atlas read back; a bigger atlas, of a big mod pack, falls back on map colours. */
    private static final int MAX_ATLAS_PIXELS = 4096 * 4096;

    private final Map<Integer, Integer> colors = new HashMap<>();
    private int[] atlas;
    private int atlasWidth;
    private int atlasHeight;
    private boolean read;

    /** The colour of a block, as id << 4 | metadata in the map's stored ids, as 0xRRGGBB. */
    int of(int stored) {
        Integer color = colors.get(stored);
        if (color == null) {
            color = compute(stored);
            colors.put(stored, color);
        }
        return color;
    }

    /** Forgets what was read, for when the textures are loaded again. */
    void clear() {
        colors.clear();
        atlas = null;
        read = false;
    }

    private int compute(int stored) {
        Block block = ModBlocks.fromStored(stored >> 4);
        int meta = stored & 15;
        int color = -1;
        try {
            color = averageTop(block, meta);
            if (color >= 0) {
                color = multiply(color, tint(block, meta));
            }
        } catch (RuntimeException e) {
            color = -1;
        }
        if (color < 0) {
            MapColor map = block.getMapColor(meta);
            color = map == null ? 0x7F7F7F : map.colorValue;
        }
        return color & 0xFFFFFF;
    }

    private static int tint(Block block, int meta) {
        if (block == Blocks.grass || block == Blocks.tallgrass || block == Blocks.double_plant) {
            return BiomeGenBase.plains.getBiomeGrassColor(0, 64, 0);
        }
        if (block instanceof BlockLeaves && ((meta & 3) == 0 || (meta & 3) == 3) || block == Blocks.vine) {
            return BiomeGenBase.plains.getBiomeFoliageColor(0, 64, 0);
        }
        return block.getRenderColor(meta);
    }

    /** The average colour of the block's top texture, ignoring see-through pixels; -1 if it cannot be had. */
    private int averageTop(Block block, int meta) {
        if (!read) {
            readAtlas();
        }
        IIcon icon = block.getIcon(1, meta);
        if (icon == null || atlas == null) {
            return -1;
        }
        int x0 = Math.max(0, (int) Math.floor(icon.getMinU() * atlasWidth));
        int x1 = Math.min(atlasWidth, (int) Math.ceil(icon.getMaxU() * atlasWidth));
        int y0 = Math.max(0, (int) Math.floor(icon.getMinV() * atlasHeight));
        int y1 = Math.min(atlasHeight, (int) Math.ceil(icon.getMaxV() * atlasHeight));
        long r = 0;
        long g = 0;
        long b = 0;
        int count = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int argb = atlas[y * atlasWidth + x];
                if ((argb >>> 24) < 0x20) {
                    continue;
                }
                r += (argb >> 16) & 0xFF;
                g += (argb >> 8) & 0xFF;
                b += argb & 0xFF;
                count++;
            }
        }
        if (count == 0) {
            return -1;
        }
        return (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
    }

    private void readAtlas() {
        read = true;
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            readBoundAtlas();
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        }
    }

    private void readBoundAtlas() {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (width <= 0 || height <= 0 || (long) width * height > MAX_ATLAS_PIXELS) {
            return;
        }
        IntBuffer buffer = BufferUtils.createIntBuffer(width * height);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
        atlas = new int[width * height];
        buffer.get(atlas);
        atlasWidth = width;
        atlasHeight = height;
    }

    static int multiply(int a, int b) {
        int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
        int g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
        int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return r << 16 | g << 8 | bl;
    }

    /** A colour with each channel times f, at most 255. */
    static int shade(int color, double f) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((color & 0xFF) * f));
        return r << 16 | g << 8 | b;
    }
}
