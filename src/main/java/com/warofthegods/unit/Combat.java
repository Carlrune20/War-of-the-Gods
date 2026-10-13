package com.warofthegods.unit;

import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Team;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Hits from units, and arrows from archers and towers. They land however recently the victim was last hurt, so
 * several archers or warriors on one enemy all count. Arrows fly on past their own team and its allies, so archers
 * behind one another can all shoot.
 */
public final class Combat {

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.entity.worldObj.isRemote) {
            return;
        }
        Entity arrow = event.source.getSourceOfDamage();
        if (arrow instanceof EntityTeamArrow) {
            Game game = GameManager.getGame();
            Team shooter = game == null ? null : game.getTeam(((EntityTeamArrow) arrow).getTeamId());
            Team victim = game == null ? null : Units.teamOf(game, event.entityLiving);
            if (shooter != null && victim != null && game.areAllied(shooter, victim)) {
                event.setCanceled(true);
                passThrough((EntityTeamArrow) arrow, event.entityLiving);
                return;
            }
        }
        if (arrow instanceof EntityTeamArrow || event.source.getEntity() instanceof EntityUnit) {
            event.entityLiving.hurtResistantTime = 0;
        }
    }

    /**
     * An arrow that hit a friend would bounce off; instead it goes on from just beyond them, as it was flying. The
     * arrow that hit is removed.
     */
    private static void passThrough(EntityTeamArrow arrow, Entity friend) {
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
        arrow.worldObj.spawnEntityInWorld(arrow.carryOn(x, y, z));
    }
}
