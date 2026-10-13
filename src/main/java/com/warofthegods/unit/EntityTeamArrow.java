package com.warofthegods.unit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * An arrow shot by an archer or a tower, which remembers its team so it never hurts the team's allies. It flies as
 * fast as from a player's fully drawn bow, trailing the same crit sparks. It cannot be picked up and is gone a few
 * seconds after it lands.
 */
public class EntityTeamArrow extends EntityArrow {

    /**
     * The speed arrows fly at, that of a player's fully drawn bow; vanilla multiplies an arrow's damage by its speed
     * when it hits.
     */
    private static final float SPEED = 3.0F;

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

    /**
     * Sends it at where the target will be when it gets there, a little high for the drop over the distance, to do
     * about damage when it hits; a crit adds up to half again.
     */
    public void aim(Entity target, double damage) {
        double height = target instanceof EntityLivingBase ? target.height * 0.6 : 0.5;
        double dx = target.posX - posX;
        double dz = target.posZ - posZ;
        double ticks = Math.sqrt(dx * dx + dz * dz) / SPEED;
        aimAt(
            target.posX + target.motionX * ticks,
            target.boundingBox.minY + height,
            target.posZ + target.motionZ * ticks,
            damage);
    }

    public void aimAt(double x, double y, double z, double damage) {
        double dx = x - posX;
        double dy = y - posY;
        double dz = z - posZ;
        double flat = Math.sqrt(dx * dx + dz * dz);
        // Gravity pulls it down by about 0.0032 blocks times the distance squared at this speed.
        setThrowableHeading(dx, dy + flat * flat * 0.0032, dz, SPEED, 1);
        setDamage(damage / SPEED);
        setIsCritical(true);
    }

    /** A copy of this arrow flying on from x, y, z, as when it passes a friend by. */
    EntityTeamArrow carryOn(double x, double y, double z) {
        EntityTeamArrow arrow = new EntityTeamArrow(worldObj);
        arrow.teamId = teamId;
        arrow.shootingEntity = shootingEntity;
        arrow.setLocationAndAngles(x, y, z, rotationYaw, rotationPitch);
        arrow.yOffset = 0;
        arrow.motionX = motionX;
        arrow.motionY = motionY;
        arrow.motionZ = motionZ;
        arrow.setDamage(getDamage());
        arrow.setIsCritical(getIsCritical());
        return arrow;
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
