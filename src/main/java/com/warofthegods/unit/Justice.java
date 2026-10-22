package com.warofthegods.unit;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.warofthegods.Config;
import com.warofthegods.game.Building;
import com.warofthegods.game.Faction;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Team;
import com.warofthegods.game.Upgrade;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Armadyl is the god of justice, and grants favor for punishing those who attack his teams first: a favor when one of
 * an Armadyl team's units, or its arrow or spell, kills an enemy that attacked the team in the last
 * {@link Config#justiceSeconds} seconds, or {@link Config#justiceSecondsDivineLaw} with divine law. Hitting one of the
 * team's buildings always marks the attacker, as buildings start no fights; hitting one of its units marks the attacker
 * only if the team's units had not hit it in the {@link #FIRST_STRIKE_TICKS} before. Towers and fortress volleys only
 * shoot at enemies who came to the town, so they never count as striking first. Wild mobs never count. Nothing here is
 * saved; a restart forgets it.
 */
public final class Justice {

    /** How long a team's hit at an enemy makes the enemy's hit back self-defense rather than an attack. */
    private static final long FIRST_STRIKE_TICKS = 200;
    /** How many marks a team keeps before it forgets the stale ones. */
    private static final int PRUNE_AT = 256;

    /** By Armadyl team id, the enemies that attacked it, with the world time each last did. */
    private static final Map<String, Map<UUID, Long>> aggressors = new HashMap<>();
    /** By team id, the enemies its units last hit, with the world time. */
    private static final Map<String, Map<UUID, Long>> struck = new HashMap<>();

    /** One of a team's buildings has been hit: an Armadyl team marks an enemy attacker. */
    public static void buildingHit(Game game, Building building, Entity attacker) {
        Team team = game.getTeam(building.team);
        Team theirs = teamOf(game, attacker);
        if (team == null || theirs == null || team.getFaction() != Faction.ARMADYL || !game.areEnemies(team, theirs)) {
            return;
        }
        remember(aggressors, team, attacker.getUniqueID(), attacker.worldObj.getTotalWorldTime());
    }

    /** One team's unit or player hurts another's: noted who struck whom, and an Armadyl team marks an aggressor. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        EntityLivingBase victim = event.entityLiving;
        Entity attacker = event.source.getEntity();
        if (victim.worldObj.isRemote || attacker == null || attacker == victim) {
            return;
        }
        Game game = GameManager.getGame();
        Team ours = game == null ? null : teamOf(game, victim);
        Team theirs = game == null ? null : teamOf(game, attacker);
        if (ours == null || theirs == null || !game.areEnemies(ours, theirs)) {
            return;
        }
        long now = victim.worldObj.getTotalWorldTime();
        remember(struck, theirs, victim.getUniqueID(), now);
        if (ours.getFaction() != Faction.ARMADYL) {
            return;
        }
        Map<UUID, Long> ourHits = struck.get(ours.getId());
        Long hit = ourHits == null ? null : ourHits.get(attacker.getUniqueID());
        if (hit == null || now - hit > FIRST_STRIKE_TICKS) {
            remember(aggressors, ours, attacker.getUniqueID(), now);
        }
    }

    /** An Armadyl team that kills an aggressor of its own while still marked earns favor for it, once. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        EntityLivingBase victim = event.entityLiving;
        if (victim.worldObj.isRemote) {
            return;
        }
        UUID id = victim.getUniqueID();
        Entity killer = event.source.getEntity();
        Game game = GameManager.getGame();
        Team team = game == null || killer == null ? null : teamOf(game, killer);
        Map<UUID, Long> marked = team == null ? null : aggressors.get(team.getId());
        Long when = marked == null ? null : marked.remove(id);
        if (when != null && team.getFaction() == Faction.ARMADYL) {
            int seconds = team.has(Upgrade.DIVINE_LAW) ? Config.justiceSecondsDivineLaw : Config.justiceSeconds;
            if (victim.worldObj.getTotalWorldTime() - when <= seconds * 20L) {
                team.addResource(Resource.FAVOR, Config.justiceFavor);
            }
        }
        // The dead attack no one any more; a player who respawns starts with a clean slate.
        for (Map<UUID, Long> marks : aggressors.values()) {
            marks.remove(id);
        }
        for (Map<UUID, Long> hits : struck.values()) {
            hits.remove(id);
        }
    }

    /** The team of a unit or a player in the game; null for wild mobs and onlookers. */
    private static Team teamOf(Game game, Entity entity) {
        if (entity instanceof EntityUnit) {
            return game.getTeam(((EntityUnit) entity).getTeamId());
        }
        if (entity instanceof EntityPlayer) {
            return game.getTeamOf(entity.getUniqueID());
        }
        return null;
    }

    private static void remember(Map<String, Map<UUID, Long>> lists, Team team, UUID id, long now) {
        Map<UUID, Long> list = lists.computeIfAbsent(team.getId(), key -> new HashMap<>());
        list.put(id, now);
        if (list.size() > PRUNE_AT) {
            long stale = Math.max(Config.justiceSeconds, Config.justiceSecondsDivineLaw) * 20L;
            for (Iterator<Long> it = list.values()
                .iterator(); it.hasNext();) {
                if (now - it.next() > stale) {
                    it.remove();
                }
            }
        }
    }
}
