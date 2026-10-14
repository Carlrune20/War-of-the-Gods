package com.warofthegods.game;

import net.minecraft.nbt.NBTTagCompound;

/**
 * A spot on the map where a town center can stand. Every team's first town center stands on one, and more lie about
 * the map for anyone to take. A settlement nobody holds shows a ring of old standing stones; workers can raise a town
 * center on it, and when a town center is destroyed its settlement is free again.
 */
public final class Settlement {

    public final int id;
    /** The north-west corner of the footprint, which is a town center's. */
    public final int minX;
    public final int minZ;
    public final int sizeX;
    public final int sizeZ;
    public final int groundY;
    /** Which way a town center on it faces. */
    public final int rotation;

    public Settlement(int id, int minX, int minZ, int sizeX, int sizeZ, int groundY, int rotation) {
        this.id = id;
        this.minX = minX;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.groundY = groundY;
        this.rotation = rotation & 3;
    }

    public double getCenterX() {
        return minX + sizeX / 2.0;
    }

    public double getCenterZ() {
        return minZ + sizeZ / 2.0;
    }

    public int getMaxX() {
        return minX + sizeX - 1;
    }

    public int getMaxZ() {
        return minZ + sizeZ - 1;
    }

    /** Whether x, z is on the footprint, or within margin blocks of it. */
    public boolean covers(double x, double z, double margin) {
        return x >= minX - margin && x < minX + sizeX + margin && z >= minZ - margin && z < minZ + sizeZ + margin;
    }

    /** Whether a footprint overlaps this one, or comes within margin blocks of it. */
    public boolean overlaps(int otherMinX, int otherMinZ, int otherSizeX, int otherSizeZ, int margin) {
        return otherMinX < minX + sizeX + margin && minX < otherMinX + otherSizeX + margin
            && otherMinZ < minZ + sizeZ + margin
            && minZ < otherMinZ + otherSizeZ + margin;
    }

    /** Whether the building stands on this settlement. */
    public boolean holds(Building building) {
        return building.minX == minX && building.minZ == minZ && building.sizeX == sizeX && building.sizeZ == sizeZ;
    }

    NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("id", id);
        tag.setIntArray("box", new int[] { minX, minZ, sizeX, sizeZ, groundY, rotation });
        return tag;
    }

    static Settlement readFromNBT(NBTTagCompound tag) {
        int[] box = tag.getIntArray("box");
        if (box.length != 6) {
            return null;
        }
        return new Settlement(tag.getInteger("id"), box[0], box[1], box[2], box[3], box[4], box[5]);
    }
}
