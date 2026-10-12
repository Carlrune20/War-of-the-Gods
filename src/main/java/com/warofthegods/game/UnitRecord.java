package com.warofthegods.game;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

/**
 * What the game remembers about a unit while its entity may not be loaded: its team and the chunk it was last seen
 * in, so that chunk can be loaded again after a restart.
 */
public final class UnitRecord {

    public final UUID id;
    public final String team;
    public final int chunkX;
    public final int chunkZ;

    public UnitRecord(UUID id, String team, int chunkX, int chunkZ) {
        this.id = id;
        this.team = team;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("uuidMost", id.getMostSignificantBits());
        tag.setLong("uuidLeast", id.getLeastSignificantBits());
        tag.setString("team", team);
        tag.setInteger("chunkX", chunkX);
        tag.setInteger("chunkZ", chunkZ);
        return tag;
    }

    static UnitRecord readFromNBT(NBTTagCompound tag) {
        return new UnitRecord(
            new UUID(tag.getLong("uuidMost"), tag.getLong("uuidLeast")),
            tag.getString("team"),
            tag.getInteger("chunkX"),
            tag.getInteger("chunkZ"));
    }
}
