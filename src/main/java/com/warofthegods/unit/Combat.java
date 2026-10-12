package com.warofthegods.unit;

import net.minecraftforge.event.entity.living.LivingAttackEvent;

import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Team;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Keeps arrows of archers and towers off their own team and its allies. */
public final class Combat {

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.entity.worldObj.isRemote || !(event.source.getSourceOfDamage() instanceof EntityTeamArrow)) {
            return;
        }
        Game game = GameManager.getGame();
        if (game == null) {
            return;
        }
        Team shooter = game.getTeam(((EntityTeamArrow) event.source.getSourceOfDamage()).getTeamId());
        Team victim = Units.teamOf(game, event.entityLiving);
        if (shooter != null && victim != null && game.areAllied(shooter, victim)) {
            event.setCanceled(true);
        }
    }
}
