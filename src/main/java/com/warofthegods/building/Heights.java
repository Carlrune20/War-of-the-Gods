package com.warofthegods.building;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.World;

import com.warofthegods.Config;
import com.warofthegods.game.Building;

/**
 * How high a building stands above the ground round it, for Armadyl's high ground: the floor's height less the average
 * height of the ground on a ring {@link #RING} blocks out from its middle, at {@link #POINTS} points round it, taking
 * the top solid block at each or the water on it. A tower on a hilltop or a cliff edge stands high; one on a plain,
 * however high the plain, about 0. A building's is worked out the first time it is asked for and kept, since only
 * foundations change the ground after it is placed; it is not saved, and is worked out again after a restart.
 */
public final class Heights {

    /** How far out the ring of ground a building is measured against lies, and how many points on it are taken. */
    public static final int RING = 16;
    public static final int POINTS = 16;

    /** Each building's height, forgotten with the building. */
    private static final Map<Building, Double> heights = new WeakHashMap<>();

    private Heights() {}

    /** How high the building stands above the ground round it, in blocks; below 0 in a hollow. */
    public static double of(World world, Building building) {
        Double known = heights.get(building);
        if (known == null) {
            known = at(world, building.getCenterX(), building.getCenterZ(), building.groundY);
            heights.put(building, known);
        }
        return known;
    }

    /** Whether one of Armadyl's buildings stands high enough for its towers to shoot farther and its temple to pray. */
    public static boolean isHigh(World world, Building building) {
        return world != null && of(world, building) >= Config.armadylHighGround;
    }

    /** How high a floor at groundY over x, z would stand above the ground round it. */
    public static double at(World world, double x, double z, int groundY) {
        double total = 0;
        for (int i = 0; i < POINTS; i++) {
            double angle = 2 * Math.PI * i / POINTS;
            total += surface(
                world,
                (int) Math.floor(x + Math.cos(angle) * RING),
                (int) Math.floor(z + Math.sin(angle) * RING));
        }
        return groundY - total / POINTS;
    }

    /** The y of the top of the ground at x, z: its top solid block, or the water lying on it. */
    static int surface(World world, int x, int z) {
        // getTopSolidOrLiquidBlock passes through water to the bed under it, so climb up through what lies on top.
        int top = world.getTopSolidOrLiquidBlock(x, z) - 1;
        while (top < 255 && world.getBlock(x, top + 1, z)
            .getMaterial()
            .isLiquid()) {
            top++;
        }
        return top;
    }
}
