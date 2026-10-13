package com.warofthegods.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * A building of a team: what it is, the ground it stands on, which way it faces, its health, and whether it is still
 * a building site and how far along. Buildings that train units keep their training queue here.
 *
 * <p>
 * A building site starts with a tenth of the building's health and gains the rest as it is built.
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
    /** Ticks of work put into a building site. */
    private int buildTicks;
    private int health;
    private final List<UnitKind> queue = new ArrayList<>();
    /** Ticks spent training the first unit in the queue. */
    private int trainingTicks;
    private int level = 1;
    /** What it is researching: an upgrade, or {@link #LEVEL_UP} when the building itself goes up a level. */
    private String research;
    private int researchTicks;
    /** The structure last put into the world for it, such as house_l1_d2; null before the first. */
    private String shown;

    /** What {@link #getResearch()} is while the building goes up a level. */
    public static final String LEVEL_UP = "level";

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
        this.health = complete ? getMaxHealth() : siteHealth(0);
    }

    private int siteHealth(int ticks) {
        int max = getMaxHealth();
        return Math.max(1, max / 10 + (int) ((long) (max - max / 10) * ticks / type.buildTicks()));
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

    /** Finishes the building, at full health less what it lost while a site. */
    public void setComplete() {
        if (!complete) {
            health = Math.max(1, getMaxHealth() - (siteHealth(buildTicks) - health));
        }
        complete = true;
        buildTicks = 0;
        game.changed();
    }

    /** How far a building site is along, from 0 to 1; 1 once built. */
    public double getBuildProgress() {
        return complete ? 1 : Math.min(1, buildTicks / (double) type.buildTicks());
    }

    /** Adds ticks of work to a building site; returns whether it is now fully built. */
    public boolean build(int ticks) {
        if (complete) {
            return true;
        }
        int before = siteHealth(buildTicks);
        buildTicks = Math.min(type.buildTicks(), buildTicks + ticks);
        health = Math.min(getMaxHealth(), health + siteHealth(buildTicks) - before);
        game.changed();
        return buildTicks >= type.buildTicks();
    }

    public int getHealth() {
        return health;
    }

    /** Its type's health, a fifth more with masonry and half again more with fortified walls. */
    public int getMaxHealth() {
        int base = type.maxHealth(level);
        int max = base;
        Team owner = game.getTeam(team);
        if (owner != null && owner.has(Upgrade.MASONRY)) {
            max += base / 5;
        }
        if (owner != null && owner.has(Upgrade.FORTIFIED_WALLS)) {
            max += base / 2;
        }
        return max;
    }

    /** Raises health by as much as the most it can have went up, as when it gains a level or masonry. */
    public void addMaxHealth(int before) {
        health = Math.max(1, Math.min(getMaxHealth(), health + getMaxHealth() - before));
        game.changed();
    }

    /** Health back to full, as when an op repairs it. */
    public void repair() {
        health = getMaxHealth();
        game.changed();
    }

    public int getLevel() {
        return level;
    }

    /** Goes up a level, keeping the health it lost. */
    public void levelUp() {
        setLevel(level + 1);
    }

    /** Takes on the look of a level, as far as it has one; levels change only how a building looks. */
    public void setLevel(int newLevel) {
        newLevel = Math.max(1, Math.min(type.levels, newLevel));
        if (newLevel != level) {
            int before = getMaxHealth();
            level = newLevel;
            addMaxHealth(before);
        }
    }

    /** What it is researching, an upgrade id or {@link #LEVEL_UP}, or null. */
    public String getResearch() {
        return research;
    }

    public int getResearchTicks() {
        return researchTicks;
    }

    public void startResearch(String what) {
        research = what;
        researchTicks = 0;
        game.changed();
    }

    /** Adds ticks to what it researches; returns it once done, and stops. */
    public String research(int ticks, int needed) {
        if (research == null) {
            return null;
        }
        researchTicks += ticks;
        game.changed();
        if (researchTicks < needed) {
            return null;
        }
        String done = research;
        research = null;
        researchTicks = 0;
        return done;
    }

    /** Takes what it researches off, as when it was destroyed or the research no longer applies. */
    public void cancelResearch() {
        research = null;
        researchTicks = 0;
        game.changed();
    }

    public String getShown() {
        return shown;
    }

    public void setShown(String shown) {
        this.shown = shown;
        game.changed();
    }

    /** Takes health off; returns whether that brought it to 0. */
    public boolean damage(int amount) {
        if (amount <= 0 || health <= 0) {
            return false;
        }
        health = Math.max(0, health - amount);
        game.changed();
        return health == 0;
    }

    /** Whether x, y, z is in the building's own blocks: on the footprint, from the ground layer up to its top. */
    public boolean holds(int x, int y, int z, int height) {
        return covers(x + 0.5, z + 0.5, 0) && y >= groundY && y < groundY + height;
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
            + (level > 1 ? " (level " + level + ")" : "")
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
        tag.setInteger("buildTicks", buildTicks);
        tag.setInteger("health", health);
        NBTTagList queueList = new NBTTagList();
        for (UnitKind kind : queue) {
            queueList.appendTag(new NBTTagString(kind.id));
        }
        tag.setTag("queue", queueList);
        tag.setInteger("trainingTicks", trainingTicks);
        tag.setInteger("level", level);
        if (research != null) {
            tag.setString("research", research);
            tag.setInteger("researchTicks", researchTicks);
        }
        if (shown != null) {
            tag.setString("shown", shown);
        }
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
        building.buildTicks = tag.getInteger("buildTicks");
        building.level = Math.max(1, Math.min(type.levels, tag.getInteger("level")));
        if (tag.hasKey("research")) {
            building.research = tag.getString("research");
            building.researchTicks = tag.getInteger("researchTicks");
        }
        building.shown = tag.hasKey("shown") ? tag.getString("shown") : null;
        if (tag.hasKey("health")) {
            // Teams are read first, so masonry counts.
            building.health = Math.max(1, Math.min(building.getMaxHealth(), tag.getInteger("health")));
        }
        return building;
    }
}
