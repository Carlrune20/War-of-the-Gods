package com.warofthegods.world;

import net.minecraft.entity.Entity;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

/** Moves an entity to a fixed spot in another dimension without looking for or building a portal. */
public class DirectTeleporter extends Teleporter {

    private final double x;
    private final double y;
    private final double z;

    public DirectTeleporter(WorldServer world, double x, double y, double z) {
        super(world);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void placeInPortal(Entity entity, double oldX, double oldY, double oldZ, float yaw) {
        entity.setLocationAndAngles(x, y, z, entity.rotationYaw, entity.rotationPitch);
        entity.motionX = 0;
        entity.motionY = 0;
        entity.motionZ = 0;
    }

    @Override
    public boolean placeInExistingPortal(Entity entity, double oldX, double oldY, double oldZ, float yaw) {
        placeInPortal(entity, oldX, oldY, oldZ, yaw);
        return true;
    }

    @Override
    public boolean makePortal(Entity entity) {
        return true;
    }

    @Override
    public void removeStalePortalLocations(long worldTime) {}
}
