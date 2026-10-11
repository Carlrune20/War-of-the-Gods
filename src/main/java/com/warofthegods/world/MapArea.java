package com.warofthegods.world;

import com.warofthegods.game.Game;

/** The playable square of a game map plus the margin of generated terrain around it. Coordinates are inclusive. */
public final class MapArea {

    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final int margin;

    public MapArea(int minX, int minZ, int maxX, int maxZ, int margin) {
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.margin = margin;
    }

    public static MapArea of(Game game, int margin) {
        return new MapArea(game.getMinX(), game.getMinZ(), game.getMaxX(), game.getMaxZ(), margin);
    }

    public int getMinX() {
        return minX;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public int getMargin() {
        return margin;
    }

    public int getCenterX() {
        return (minX + maxX) / 2;
    }

    public int getCenterZ() {
        return (minZ + maxZ) / 2;
    }

    public boolean isInside(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** How far a block is outside the playable square, 0 if inside. */
    public double distanceOutside(int x, int z) {
        int dx = Math.max(Math.max(minX - x, x - maxX), 0);
        int dz = Math.max(Math.max(minZ - z, z - maxZ), 0);
        return Math.sqrt(dx * dx + dz * dz);
    }
}
