package com.warofthegods.unit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Team;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Hits from units, and arrows from archers and towers. They land however recently the victim was last hurt, so
 * several archers or warriors on one enemy all count. Arrows fly on past their own team and its allies, so archers
 * behind one another can all shoot, and never hurt them or set them alight.
 */
public final class Combat {

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.entity.worldObj.isRemote) {
            return;
        }
        Entity arrow = event.source.getSourceOfDamage();
        if (passesFriend(event.source, event.entityLiving)) {
            event.setCanceled(true);
            return;
        }
        if (arrow instanceof EntityTeamArrow || event.source.getEntity() instanceof EntityUnit) {
            event.entityLiving.hurtResistantTime = 0;
        }
        // A fire arrow sets alight only enemies, and anything else that is no friend, such as mobs.
        if (arrow instanceof EntityTeamArrow && ((EntityTeamArrow) arrow).isFlaming()) {
            event.entityLiving.setFire(5);
        }
    }

    /**
     * Whether the hit is an arrow of the entity's own team or an ally, which then flies on past it rather than
     * bouncing off. Units and commanders' bodies ask this before anything else, since they turn friendly hits away
     * themselves, before any attack event.
     */
    public static boolean passesFriend(DamageSource source, Entity hit) {
        Entity arrow = source.getSourceOfDamage();
        if (!(arrow instanceof EntityTeamArrow) || hit.worldObj.isRemote) {
            return false;
        }
        Game game = GameManager.getGame();
        Team shooter = game == null ? null : game.getTeam(((EntityTeamArrow) arrow).getTeamId());
        Team victim = game == null ? null : Units.teamOf(game, hit);
        if (shooter == null || victim == null || !game.areAllied(shooter, victim)) {
            return false;
        }
        passThrough((EntityTeamArrow) arrow, hit);
        return true;
    }

    /** An arrow that missed what it hit, such as a rider on the move, flies on past it rather than bouncing off. */
    public static void flyPast(DamageSource source, Entity hit) {
        Entity arrow = source.getSourceOfDamage();
        if (arrow instanceof EntityArrow && !hit.worldObj.isRemote) {
            passThrough((EntityArrow) arrow, hit);
        }
    }

    /**
     * An arrow that hit a friend, or one already dying, would bounce off; instead it goes on from just beyond them, as
     * it was flying. The arrow that hit is removed.
     */
    public static void passThrough(EntityArrow arrow, Entity friend) {
        if (arrow.isDead) {
            return;
        }
        arrow.setDead();
        double speed = Math
            .sqrt(arrow.motionX * arrow.motionX + arrow.motionY * arrow.motionY + arrow.motionZ * arrow.motionZ);
        if (speed < 0.1) {
            return;
        }
        double dx = arrow.motionX / speed;
        double dy = arrow.motionY / speed;
        double dz = arrow.motionZ / speed;
        AxisAlignedBB box = friend.boundingBox.expand(0.3, 0.3, 0.3);
        double x = arrow.posX;
        double y = arrow.posY;
        double z = arrow.posZ;
        // On along its flight into the friend and out the other side.
        boolean entered = false;
        for (int step = 0; step < 32; step++) {
            boolean inside = box.isVecInside(Vec3.createVectorHelper(x, y, z));
            if (inside) {
                entered = true;
            } else if (entered) {
                break;
            }
            x += dx * 0.25;
            y += dy * 0.25;
            z += dz * 0.25;
        }
        arrow.worldObj.spawnEntityInWorld(
            arrow instanceof EntityTeamArrow ? ((EntityTeamArrow) arrow).carryOn(x, y, z) : carryOn(arrow, x, y, z));
    }

    /** A copy of a player's arrow flying on from x, y, z. */
    private static EntityArrow carryOn(EntityArrow from, double x, double y, double z) {
        EntityArrow arrow = new EntityArrow(from.worldObj);
        arrow.shootingEntity = from.shootingEntity;
        arrow.canBePickedUp = from.canBePickedUp;
        arrow.setLocationAndAngles(x, y, z, from.rotationYaw, from.rotationPitch);
        arrow.yOffset = 0;
        arrow.motionX = from.motionX;
        arrow.motionY = from.motionY;
        arrow.motionZ = from.motionZ;
        arrow.setDamage(from.getDamage());
        arrow.setIsCritical(from.getIsCritical());
        if (from.isBurning()) {
            arrow.setFire(100);
        }
        // Marks such as the dark bow's go with it.
        NBTTagCompound tags = from.getEntityData();
        for (Object key : tags.func_150296_c()) {
            arrow.getEntityData()
                .setTag(
                    (String) key,
                    tags.getTag((String) key)
                        .copy());
        }
        return arrow;
    }
}
