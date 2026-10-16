package com.warofthegods.game;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Old ruins that lie about the map and belong to no team: crumbled walls, broken towers, chapels and manors fallen
 * in, abandoned huts and farmsteads, graveyards and the like. Units walk through and around them, but nothing can be
 * built on them. Some are haunted: spiders lurk among cobwebs in them, or the dead walk round them.
 */
public final class Ruin {

    public final int id;
    public final int minX;
    public final int minZ;
    public final int sizeX;
    public final int sizeZ;
    public final int groundY;
    /** What the ruin was, as the name of its design. */
    public final String design;
    /** What lurks there: {@link #QUIET}, {@link #SPIDERS} or {@link #UNDEAD}. */
    public final int haunt;

    public static final int QUIET = 0;
    public static final int SPIDERS = 1;
    public static final int UNDEAD = 2;

    public Ruin(int id, int minX, int minZ, int sizeX, int sizeZ, int groundY, String design, int haunt) {
        this.id = id;
        this.minX = minX;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.groundY = groundY;
        this.design = design;
        this.haunt = haunt;
    }

    public double getCenterX() {
        return minX + sizeX / 2.0;
    }

    public double getCenterZ() {
        return minZ + sizeZ / 2.0;
    }

    /** Whether a footprint overlaps this one, or comes within margin blocks of it. */
    public boolean overlaps(int otherMinX, int otherMinZ, int otherSizeX, int otherSizeZ, int margin) {
        return otherMinX < minX + sizeX + margin && minX < otherMinX + otherSizeX + margin
            && otherMinZ < minZ + sizeZ + margin
            && minZ < otherMinZ + otherSizeZ + margin;
    }

    NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("id", id);
        tag.setIntArray("box", new int[] { minX, minZ, sizeX, sizeZ, groundY, haunt });
        tag.setString("design", design);
        return tag;
    }

    static Ruin readFromNBT(NBTTagCompound tag) {
        int[] box = tag.getIntArray("box");
        if (box.length < 5) {
            return null;
        }
        return new Ruin(
            tag.getInteger("id"),
            box[0],
            box[1],
            box[2],
            box[3],
            box[4],
            tag.getString("design"),
            box.length > 5 ? box[5] : QUIET);
    }
}
