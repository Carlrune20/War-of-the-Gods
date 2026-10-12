package com.warofthegods.unit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * An arrow shot by an archer or a tower, which remembers its team so it never hurts the team's allies. It cannot be
 * picked up and is gone a few seconds after it lands.
 */
public class EntityTeamArrow extends EntityArrow {

    /** The speed arrows fly at; vanilla multiplies an arrow's damage by its speed when it hits. */
    private static final float SPEED = 1.6F;

    private String teamId = "";

    public EntityTeamArrow(World world) {
        super(world);
        canBePickedUp = 0;
    }

    /** An arrow of a tower, which has no entity to shoot it. */
    public EntityTeamArrow(World world, double x, double y, double z, String teamId) {
        super(world, x, y, z);
        this.teamId = teamId;
        canBePickedUp = 0;
    }

    /** An arrow of an archer, starting at its eyes. */
    public EntityTeamArrow(World world, EntityUnit shooter) {
        super(world);
        shootingEntity = shooter;
        teamId = shooter.getTeamId();
        canBePickedUp = 0;
        setLocationAndAngles(shooter.posX, shooter.posY + shooter.getEyeHeight() - 0.1, shooter.posZ, 0, 0);
        yOffset = 0;
    }

    public String getTeamId() {
        return teamId;
    }

    /** Sends it at the target, a little high for the drop over the distance, to do about damage when it hits. */
    public void aim(Entity target, double damage) {
        double height = target instanceof EntityLivingBase ? target.height * 0.6 : 0.5;
        aimAt(target.posX, target.boundingBox.minY + height, target.posZ, damage);
    }

    public void aimAt(double x, double y, double z, double damage) {
        double dx = x - posX;
        double dy = y - posY;
        double dz = z - posZ;
        double flat = Math.sqrt(dx * dx + dz * dz);
        setThrowableHeading(dx, dy + flat * 0.12, dz, SPEED, 2);
        setDamage(damage / SPEED);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        // Arrows that stuck or flew off are not worth keeping around.
        if (!worldObj.isRemote && (ticksExisted > 100 || arrowShake > 0 && ticksExisted > 40)) {
            setDead();
        }
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setString("Team", teamId);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        teamId = tag.getString("Team");
    }
}
