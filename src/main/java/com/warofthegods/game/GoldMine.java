package com.warofthegods.game;

import net.minecraft.nbt.NBTTagCompound;

/**
 * An outcrop of rock with gold in it, the only source of gold on the map. Workers stand round it and mine it, carrying
 * the gold to a town center or storehouse, until it is mined out.
 */
public final class GoldMine {

    public final int id;
    public final int minX;
    public final int minZ;
    public final int size;
    public final int groundY;
    public final int maxGold;
    private int gold;

    public GoldMine(int id, int minX, int minZ, int size, int groundY, int gold) {
        this.id = id;
        this.minX = minX;
        this.minZ = minZ;
        this.size = size;
        this.groundY = groundY;
        this.maxGold = gold;
        this.gold = gold;
    }

    public int getGold() {
        return gold;
    }

    public boolean isDepleted() {
        return gold <= 0;
    }

    /** Takes up to amount of gold out; returns how much it gave. */
    int take(int amount) {
        int taken = Math.max(0, Math.min(gold, amount));
        gold -= taken;
        return taken;
    }

    public double getCenterX() {
        return minX + size / 2.0;
    }

    public double getCenterZ() {
        return minZ + size / 2.0;
    }

    public int getMaxX() {
        return minX + size - 1;
    }

    public int getMaxZ() {
        return minZ + size - 1;
    }

    /** Whether x, z is on the footprint, or within margin blocks of it. */
    public boolean covers(double x, double z, double margin) {
        return x >= minX - margin && x < minX + size + margin && z >= minZ - margin && z < minZ + size + margin;
    }

    /** Whether a footprint overlaps this one, or comes within margin blocks of it. */
    public boolean overlaps(int otherMinX, int otherMinZ, int otherSizeX, int otherSizeZ, int margin) {
        return otherMinX < minX + size + margin && minX < otherMinX + otherSizeX + margin
            && otherMinZ < minZ + size + margin
            && minZ < otherMinZ + otherSizeZ + margin;
    }

    /** Horizontal distance from x, z to the nearest point of the footprint; 0 on it. */
    public double distanceTo(double x, double z) {
        double dx = Math.max(Math.max(minX - x, 0), x - (minX + size));
        double dz = Math.max(Math.max(minZ - z, 0), z - (minZ + size));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The point a step outside the footprint nearest to x, z, as x, z. */
    public double[] nearestOutside(double x, double z) {
        double cx = Math.max(minX - 1.0, Math.min(minX + size + 1.0, x));
        double cz = Math.max(minZ - 1.0, Math.min(minZ + size + 1.0, z));
        if (covers(cx, cz, 0.5)) {
            double west = cx - minX;
            double east = minX + size - cx;
            double north = cz - minZ;
            double south = minZ + size - cz;
            double least = Math.min(Math.min(west, east), Math.min(north, south));
            if (least == west) {
                cx = minX - 1.0;
            } else if (least == east) {
                cx = minX + size + 1.0;
            } else if (least == north) {
                cz = minZ - 1.0;
            } else {
                cz = minZ + size + 1.0;
            }
        }
        return new double[] { cx, cz };
    }

    NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("id", id);
        tag.setIntArray("box", new int[] { minX, minZ, size, groundY, maxGold, gold });
        return tag;
    }

    static GoldMine readFromNBT(NBTTagCompound tag) {
        int[] box = tag.getIntArray("box");
        if (box.length != 6) {
            return null;
        }
        GoldMine mine = new GoldMine(tag.getInteger("id"), box[0], box[1], box[2], box[3], box[4]);
        mine.gold = box[5];
        return mine;
    }
}
