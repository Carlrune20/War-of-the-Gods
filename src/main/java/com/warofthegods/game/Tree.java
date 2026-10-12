package com.warofthegods.game;

import net.minecraft.nbt.NBTTagCompound;

/**
 * A tree workers have started chopping: the log at the foot of its trunk, the top of its trunk, and how much wood is
 * left in it. Trees nobody has touched are not tracked; they hold their full amount.
 */
public final class Tree {

    public final int x;
    public final int y;
    public final int z;
    /** The y of its highest log, for drawing its bar above it. */
    public final int top;
    public final int maxWood;
    private int wood;

    public Tree(int x, int y, int z, int top, int maxWood) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.top = top;
        this.maxWood = maxWood;
        this.wood = maxWood;
    }

    public int getWood() {
        return wood;
    }

    /** Takes up to amount of wood out of it; returns how much it gave. */
    int take(int amount) {
        int taken = Math.max(0, Math.min(wood, amount));
        wood -= taken;
        return taken;
    }

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public long key() {
        return key(x, y, z);
    }

    NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setIntArray("pos", new int[] { x, y, z, top, maxWood, wood });
        return tag;
    }

    static Tree readFromNBT(NBTTagCompound tag) {
        int[] pos = tag.getIntArray("pos");
        if (pos.length != 6) {
            return null;
        }
        Tree tree = new Tree(pos[0], pos[1], pos[2], pos[3], pos[4]);
        tree.wood = pos[5];
        return tree;
    }
}
