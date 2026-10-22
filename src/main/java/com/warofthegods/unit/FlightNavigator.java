package com.warofthegods.unit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.world.World;

/**
 * How aviansie and Armadyl's heroes get about: no path over the ground, just a heading straight at where they are
 * going, over walls, rivers and cliffs alike. The unit's own movement does the flying; this only keeps where to.
 */
public class FlightNavigator extends PathNavigate {

    private boolean going;
    private double goalX;
    private double goalZ;
    private Entity following;

    public FlightNavigator(EntityLiving flier, World world) {
        super(flier, world);
    }

    /** Where it is flying to now, as x, z; null when it has nowhere to go. */
    public double[] goal() {
        if (!going) {
            return null;
        }
        if (following != null) {
            if (!following.isEntityAlive()) {
                clearPathEntity();
                return null;
            }
            goalX = following.posX;
            goalZ = following.posZ;
        }
        return new double[] { goalX, goalZ };
    }

    /** Called once it is there, so it stops and hovers. */
    public void arrived() {
        if (following == null) {
            going = false;
        }
    }

    @Override
    public boolean tryMoveToXYZ(double x, double y, double z, double speed) {
        goalX = x;
        goalZ = z;
        following = null;
        going = true;
        return true;
    }

    @Override
    public boolean tryMoveToEntityLiving(Entity entity, double speed) {
        if (entity == null) {
            return false;
        }
        following = entity;
        going = true;
        return true;
    }

    @Override
    public boolean setPath(PathEntity path, double speed) {
        return false;
    }

    @Override
    public PathEntity getPath() {
        return null;
    }

    /** The flying is done in the unit's own movement, a tick at a time. */
    @Override
    public void onUpdateNavigation() {}

    @Override
    public boolean noPath() {
        return !going;
    }

    @Override
    public void clearPathEntity() {
        going = false;
        following = null;
    }
}
