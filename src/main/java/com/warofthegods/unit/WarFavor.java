package com.warofthegods.unit;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.warofthegods.Config;
import com.warofthegods.game.Faction;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Team;
import com.warofthegods.game.UnitKind;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Bandos is the god of war, and grants most of his favor for it: a favor for every few enemy units, or more wild mobs,
 * his teams kill within reach of one of their goblin priests, and favor for every enemy building they raze. Here too
 * are the goblin heroes' gifts to those round them: goblins near Sergeant Strongstack hit harder, goblins near
 * Sergeant Steelwill take less from spells, and General Graardor hits harder while one of his sergeants fights near
 * him.
 */
public final class WarFavor {

    /** What goblins near Sergeant Steelwill take of a spell's damage. */
    static final float STEELWILL_SPELL_DAMAGE = 0.75F;
    /** How far a sergeant's gift reaches, and how near Graardor one of his sergeants has to be. */
    private static final double AURA_RANGE = 8;
    private static final double BODYGUARD_RANGE = 16;
    /** How much harder goblins near Strongstack hit, and Graardor with a sergeant near. */
    private static final int STRONGSTACK_BONUS = 1;
    private static final int GRAARDOR_BONUS = 4;

    /** Favor each team has earned toward its next whole one, by team id; not saved. */
    private static final Map<String, Double> due = new HashMap<>();

    /** A kill by a Bandos team's unit, or its arrow, axe or spell, near one of its goblin priests earns favor. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        EntityLivingBase victim = event.entityLiving;
        Entity killer = event.source.getEntity();
        if (victim.worldObj.isRemote || !(killer instanceof EntityUnit)) {
            return;
        }
        EntityUnit unit = (EntityUnit) killer;
        Game game = GameManager.getGame();
        Team team = game == null ? null : game.getTeam(unit.getTeamId());
        if (team == null || team.getFaction() != Faction.BANDOS) {
            return;
        }
        boolean mob = victim instanceof IMob && !(victim instanceof EntityUnit);
        if (!mob && !Units.get()
            .isEnemy(unit, victim)) {
            return;
        }
        if (!priestNear(team, victim)) {
            return;
        }
        grant(team, 1.0 / (mob ? Config.mobKillsPerFavor : Config.killsPerFavor));
    }

    /** A Bandos team's unit has just brought an enemy building down. */
    static void razed(EntityUnit unit) {
        Game game = GameManager.getGame();
        Team team = game == null ? null : game.getTeam(unit.getTeamId());
        if (team != null && team.getFaction() == Faction.BANDOS) {
            grant(team, Config.razeFavor);
        }
    }

    private static void grant(Team team, double favor) {
        double total = due.getOrDefault(team.getId(), 0.0) + favor;
        // A little over, so three thirds make a whole one.
        int whole = (int) Math.floor(total + 1e-9);
        if (whole > 0) {
            team.addResource(Resource.FAVOR, whole);
            total -= whole;
        }
        due.put(team.getId(), Math.max(0, total));
    }

    /** Whether one of the team's goblin priests stands near enough where something fell. */
    private static boolean priestNear(Team team, Entity where) {
        double range = Config.warFavorRange;
        for (EntityUnit unit : Units.get()
            .getUnits(team)) {
            if (unit.getKind() == UnitKind.GOBLIN_PRIEST && unit.getDistanceSqToEntity(where) <= range * range) {
                return true;
            }
        }
        return false;
    }

    /** How much harder the unit hits for the goblin heroes round it. */
    static int heroBonus(EntityUnit unit) {
        UnitKind kind = unit.getKind();
        if (kind == UnitKind.GRAARDOR) {
            return near(unit, BODYGUARD_RANGE, UnitKind.STRONGSTACK, UnitKind.STEELWILL, UnitKind.GRIMSPIKE)
                ? GRAARDOR_BONUS
                : 0;
        }
        return kind.isGoblin() && kind != UnitKind.STRONGSTACK && nearHero(unit, UnitKind.STRONGSTACK)
            ? STRONGSTACK_BONUS
            : 0;
    }

    /** Whether the hero of its team stands near the unit. */
    static boolean nearHero(EntityUnit unit, UnitKind hero) {
        return unit.getKind() != hero && near(unit, AURA_RANGE, hero);
    }

    private static boolean near(EntityUnit unit, double range, UnitKind... kinds) {
        Game game = GameManager.getGame();
        Team team = game == null ? null : game.getTeam(unit.getTeamId());
        if (team == null) {
            return false;
        }
        for (EntityUnit other : Units.get()
            .getUnits(team)) {
            if (other == unit || other.getDistanceSqToEntity(unit) > range * range) {
                continue;
            }
            for (UnitKind kind : kinds) {
                if (other.getKind() == kind) {
                    return true;
                }
            }
        }
        return false;
    }
}
