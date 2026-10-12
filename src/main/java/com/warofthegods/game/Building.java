package com.warofthegods.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * A building of a team: what it is, the ground it stands on, which way it faces, and whether it is still a building
 * site waiting for a worker. Buildings that train units keep their training queue here.
 */
public final class Building {

    public final int id;
    public final BuildingType type;
    public final String team;
    /** The north-west corner of the footprint. */
    public final int minX;
    public final int minZ;
    /** Footprint size along x and z, after rotation. */
    public final int sizeX;
    public final int sizeZ;
    /** The y of the structure's ground floor, the layer that replaces the ground's top block. */
    public final int groundY;
    /** Quarter turns clockwise seen from above; at 0 the front faces north. */
    public final int rotation;

    private final Game game;
    private boolean complete;
    private final List<UnitKind> queue = new ArrayList<>();
    /** Ticks spent training the first unit in the queue. */
    private int trainingTicks;

    Building(Game game, int id, BuildingType type, String team, int minX, int minZ, int sizeX, int sizeZ, int groundY,
        int rotation, boolean complete) {
        this.game = game;
        this.id = id;
        this.type = type;
        this.team = team;
        this.minX = minX;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.groundY = groundY;
        this.rotation = rotation & 3;
        this.complete = complete;
    }

    public int getMaxX() {
        return minX + sizeX - 1;
    }

    public int getMaxZ() {
        return minZ + sizeZ - 1;
    }

    public double getCenterX() {
        return minX + sizeX / 2.0;
    }

    public double getCenterZ() {
        return minZ + sizeZ / 2.0;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete() {
        complete = true;
        game.changed();
    }

    /** Whether x, z is on the footprint, or within margin blocks of it. */
    public boolean covers(double x, double z, double margin) {
        return x >= minX - margin && x < minX + sizeX + margin && z >= minZ - margin && z < minZ + sizeZ + margin;
    }

    /** Whether the footprints of the two overlap, or come within margin blocks of each other. */
    public boolean overlaps(int otherMinX, int otherMinZ, int otherSizeX, int otherSizeZ, int margin) {
        return otherMinX < minX + sizeX + margin && minX < otherMinX + otherSizeX + margin
            && otherMinZ < minZ + sizeZ + margin
            && minZ < otherMinZ + otherSizeZ + margin;
    }

    /** Horizontal distance from x, z to the nearest point of the footprint; 0 on it. */
    public double distanceTo(double x, double z) {
        double dx = Math.max(Math.max(minX - x, 0), x - (minX + sizeX));
        double dz = Math.max(Math.max(minZ - z, 0), z - (minZ + sizeZ));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The point a step outside the footprint nearest to x, z, as x, z. */
    public double[] nearestOutside(double x, double z) {
        double cx = Math.max(minX - 1.0, Math.min(minX + sizeX + 1.0, x));
        double cz = Math.max(minZ - 1.0, Math.min(minZ + sizeZ + 1.0, z));
        if (covers(cx, cz, 0.5)) {
            // On the footprint: out by the nearest side.
            double west = cx - minX;
            double east = minX + sizeX - cx;
            double north = cz - minZ;
            double south = minZ + sizeZ - cz;
            double least = Math.min(Math.min(west, east), Math.min(north, south));
            if (least == west) {
                cx = minX - 1.0;
            } else if (least == east) {
                cx = minX + sizeX + 1.0;
            } else if (least == north) {
                cz = minZ - 1.0;
            } else {
                cz = minZ + sizeZ + 1.0;
            }
        }
        return new double[] { cx, cz };
    }

    /** Where units it trains appear: a few blocks out from the middle of its front, as x, z. */
    public double[] getDoorstep() {
        double out = 2.5;
        switch (rotation) {
            case 1:
                return new double[] { minX + sizeX + out, getCenterZ() };
            case 2:
                return new double[] { getCenterX(), minZ + sizeZ + out };
            case 3:
                return new double[] { minX - out, getCenterZ() };
            default:
                return new double[] { getCenterX(), minZ - out };
        }
    }

    public List<UnitKind> getQueue() {
        return Collections.unmodifiableList(queue);
    }

    public void enqueue(UnitKind kind) {
        queue.add(kind);
        game.changed();
    }

    public int getTrainingTicks() {
        return trainingTicks;
    }

    /** Adds ticks to the unit in training; returns it once trained, and starts on the next. */
    public UnitKind train(int ticks, int ticksPerUnit) {
        if (queue.isEmpty()) {
            trainingTicks = 0;
            return null;
        }
        trainingTicks += ticks;
        game.changed();
        if (trainingTicks < ticksPerUnit) {
            return null;
        }
        trainingTicks = 0;
        return queue.remove(0);
    }

    /** Takes the unit in training back, as when it could not be placed; it trains again from the start. */
    public void requeueFirst(UnitKind kind) {
        queue.add(0, kind);
        game.changed();
    }

    public String describe(Game game) {
        Team owner = game.getTeam(team);
        return (owner == null ? team : owner.getName()) + " "
            + type.displayName.toLowerCase()
            + " #"
            + id
            + " at "
            + (int) Math.floor(getCenterX())
            + ","
            + (int) Math.floor(getCenterZ())
            + (complete ? "" : " (building site)");
    }

    NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("id", id);
        tag.setString("type", type.id);
        tag.setString("team", team);
        tag.setIntArray("box", new int[] { minX, minZ, sizeX, sizeZ, groundY, rotation });
        tag.setBoolean("complete", complete);
        NBTTagList queueList = new NBTTagList();
        for (UnitKind kind : queue) {
            queueList.appendTag(new NBTTagString(kind.id));
        }
        tag.setTag("queue", queueList);
        tag.setInteger("trainingTicks", trainingTicks);
        return tag;
    }

    /** Null for a building of a type this version does not know. */
    static Building readFromNBT(Game game, NBTTagCompound tag) {
        BuildingType type = BuildingType.byId(tag.getString("type"));
        int[] box = tag.getIntArray("box");
        if (type == null || box.length != 6) {
            return null;
        }
        Building building = new Building(
            game,
            tag.getInteger("id"),
            type,
            tag.getString("team"),
            box[0],
            box[1],
            box[2],
            box[3],
            box[4],
            box[5],
            tag.getBoolean("complete"));
        NBTTagList queueList = tag.getTagList("queue", 8);
        for (int i = 0; i < queueList.tagCount(); i++) {
            UnitKind kind = UnitKind.byId(queueList.getStringTagAt(i));
            if (kind != null) {
                building.queue.add(kind);
            }
        }
        building.trainingTicks = tag.getInteger("trainingTicks");
        return building;
    }
}
