package com.warofthegods.web;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

/**
 * The drawn map of one game, as square tiles of {@link #TILE_SIZE} blocks with one pixel per block. The server thread
 * writes block colors in whole chunks; web threads read tiles out as PNG images. Each change gives the tile a new
 * version number, which browsers use to know when to fetch it again.
 */
public final class MapTiles {

    public static final int TILE_SIZE = 128;
    private static final int CHUNKS_PER_TILE = TILE_SIZE / 16;

    /** Alpha of a column color that still needs shading by height; other colors are final. */
    public static final int SHADE_BY_HEIGHT = 0xFF000000;
    /** Alpha of a column color that is already shaded, such as water shaded by depth. */
    public static final int FINAL_COLOR = 0xFE000000;

    private final int gameNumber;
    private final Map<Long, Tile> tiles = new ConcurrentHashMap<>();
    private final Set<Long> changed = ConcurrentHashMap.newKeySet();
    private final AtomicInteger versions = new AtomicInteger();

    private static final class Tile {

        final int[] colors = new int[TILE_SIZE * TILE_SIZE];
        final short[] heights = new short[TILE_SIZE * TILE_SIZE];
        volatile int version;
        byte[] png;
        int pngVersion = -1;
    }

    public MapTiles(int gameNumber) {
        this.gameNumber = gameNumber;
    }

    public int getGameNumber() {
        return gameNumber;
    }

    private static long key(int tx, int tz) {
        return ((long) tx << 32) | (tz & 0xFFFFFFFFL);
    }

    /**
     * Stores the colors and heights of one chunk's 16x16 columns, indexed x + z * 16. Colors carry {@link
     * #SHADE_BY_HEIGHT} or {@link #FINAL_COLOR} in their alpha byte; 0 means nothing to draw.
     */
    public void setChunk(int chunkX, int chunkZ, int[] colors, short[] heights) {
        int tx = Math.floorDiv(chunkX, CHUNKS_PER_TILE);
        int tz = Math.floorDiv(chunkZ, CHUNKS_PER_TILE);
        long key = key(tx, tz);
        Tile tile = tiles.computeIfAbsent(key, k -> new Tile());
        int offsetX = Math.floorMod(chunkX, CHUNKS_PER_TILE) * 16;
        int offsetZ = Math.floorMod(chunkZ, CHUNKS_PER_TILE) * 16;
        boolean different = false;
        synchronized (tile) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = offsetX + x + (offsetZ + z) * TILE_SIZE;
                    int color = colors[x + z * 16];
                    short height = heights[x + z * 16];
                    if (tile.colors[index] != color || tile.heights[index] != height) {
                        tile.colors[index] = color;
                        tile.heights[index] = height;
                        different = true;
                    }
                }
            }
        }
        if (different) {
            tile.version = versions.incrementAndGet();
            changed.add(key);
            // The top row of the tile below is shaded against this tile's bottom row.
            Tile below = offsetZ == TILE_SIZE - 16 ? tiles.get(key(tx, tz + 1)) : null;
            if (below != null) {
                below.version = versions.incrementAndGet();
                changed.add(key(tx, tz + 1));
            }
        }
    }

    /** Tiles that changed since the last call, as "x,z" to version. */
    public Map<String, Integer> takeChanged() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Long key : changed.toArray(new Long[0])) {
            changed.remove(key);
            Tile tile = tiles.get(key);
            if (tile != null) {
                result.put(name(key), tile.version);
            }
        }
        return result;
    }

    /** Every tile drawn so far, as "x,z" to version. */
    public Map<String, Integer> allVersions() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Tile> entry : tiles.entrySet()) {
            result.put(name(entry.getKey()), entry.getValue().version);
        }
        return result;
    }

    private static String name(long key) {
        return (int) (key >> 32) + "," + (int) key;
    }

    /** The tile as a PNG image, or null if nothing has been drawn there yet. */
    public byte[] png(int tx, int tz) throws IOException {
        Tile tile = tiles.get(key(tx, tz));
        if (tile == null) {
            return null;
        }
        Tile above = tiles.get(key(tx, tz - 1));
        synchronized (tile) {
            int version = tile.version;
            if (tile.png == null || tile.pngVersion != version) {
                tile.png = encode(tile, above);
                tile.pngVersion = version;
            }
            return tile.png;
        }
    }

    private static byte[] encode(Tile tile, Tile above) throws IOException {
        BufferedImage image = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[TILE_SIZE * TILE_SIZE];
        for (int z = 0; z < TILE_SIZE; z++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                int index = x + z * TILE_SIZE;
                int color = tile.colors[index];
                if (color == 0) {
                    continue;
                }
                if ((color & 0xFF000000) == SHADE_BY_HEIGHT) {
                    int north;
                    if (z > 0) {
                        north = tile.heights[index - TILE_SIZE];
                    } else if (above != null) {
                        north = above.heights[x + (TILE_SIZE - 1) * TILE_SIZE];
                    } else {
                        north = tile.heights[index];
                    }
                    int height = tile.heights[index];
                    // Like vanilla maps: slopes facing north are lit, slopes facing south are shaded.
                    int shade = height > north ? 255 : height < north ? 180 : 220;
                    color = scale(color, shade);
                }
                pixels[index] = 0xFF000000 | (color & 0xFFFFFF);
            }
        }
        image.setRGB(0, 0, TILE_SIZE, TILE_SIZE, pixels, 0, TILE_SIZE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** Multiplies each channel of an RGB color by shade / 255. */
    public static int scale(int color, int shade) {
        int r = ((color >> 16) & 0xFF) * shade / 255;
        int g = ((color >> 8) & 0xFF) * shade / 255;
        int b = (color & 0xFF) * shade / 255;
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
