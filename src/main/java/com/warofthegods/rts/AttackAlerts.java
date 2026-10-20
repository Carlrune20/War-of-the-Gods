package com.warofthegods.rts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.google.gson.JsonObject;
import com.warofthegods.game.Building;
import com.warofthegods.game.Game;
import com.warofthegods.game.Team;
import com.warofthegods.unit.EntityUnit;
import com.warofthegods.unit.Units;
import com.warofthegods.web.WebJson;

/**
 * Hears of a team's units and buildings coming under attack from another team, its units or its commanders, not from
 * mobs. It warns the team's players with a sound, a line and a mark on the minimap, no more than every few seconds,
 * and keeps the latest attacks for the computer to answer when it next looks over its team.
 */
public final class AttackAlerts {

    /** Fewest ticks between warnings to a team's players. */
    private static final long WARNING_TICKS = 20 * 10;
    /** Attacks kept for a team until the computer looks, and the squares, in blocks, one attack is kept for. */
    private static final int MAX_KEPT = 8;
    private static final int SPOT_CELL = 16;

    private static final AttackAlerts INSTANCE = new AttackAlerts();

    /** Where a team was attacked, what was hit and by whom. */
    public static final class Attack {

        public final double x;
        public final double z;
        /** The unit that was hit, or null for a building. */
        public final EntityUnit victim;
        /** Who hit it, when known. */
        public final EntityLivingBase attacker;

        Attack(double x, double z, EntityUnit victim, EntityLivingBase attacker) {
            this.x = x;
            this.z = z;
            this.victim = victim;
            this.attacker = attacker;
        }
    }

    private final Map<String, Long> lastWarning = new HashMap<>();
    private final Map<String, List<Attack>> kept = new HashMap<>();

    private AttackAlerts() {}

    public static AttackAlerts get() {
        return INSTANCE;
    }

    /** A unit was hurt by the entity: heard when it is another team's unit, player or commander. */
    public void unitAttacked(Game game, EntityUnit unit, Entity attacker, long now) {
        if (!(attacker instanceof EntityLivingBase) || Units.isMob(attacker)) {
            return;
        }
        Team team = Units.teamOf(game, unit);
        Team other = Units.teamOf(game, attacker);
        if (team == null || other == null || other == team || !game.areEnemies(team, other)) {
            return;
        }
        keep(team, new Attack(unit.posX, unit.posZ, unit, (EntityLivingBase) attacker));
        warn(
            team,
            unit.posX,
            unit.posZ,
            unit.isWorker() ? "Your workers are under attack!" : "Your army is under attack!",
            now);
    }

    /** A building was damaged, by the entity when known; heard unless the entity is a mob or of its own team. */
    public void buildingAttacked(Game game, Building building, Entity attacker, long now) {
        Team team = game.getTeam(building.team);
        if (team == null || attacker != null && Units.isMob(attacker)) {
            return;
        }
        Team other = attacker == null ? null : Units.teamOf(game, attacker);
        if (other == team) {
            return;
        }
        keep(
            team,
            new Attack(
                building.getCenterX(),
                building.getCenterZ(),
                null,
                attacker instanceof EntityLivingBase ? (EntityLivingBase) attacker : null));
        warn(
            team,
            building.getCenterX(),
            building.getCenterZ(),
            "Your " + building.type.displayName.toLowerCase() + " is under attack!",
            now);
    }

    /** The attacks on the team since it last asked, which it now forgets. */
    public List<Attack> take(Team team) {
        List<Attack> attacks = kept.remove(team.getId());
        return attacks == null ? new ArrayList<>() : attacks;
    }

    /** Forgets everything, as when a game ends. */
    public void clear() {
        lastWarning.clear();
        kept.clear();
    }

    private void keep(Team team, Attack attack) {
        List<Attack> attacks = kept.computeIfAbsent(team.getId(), id -> new ArrayList<>());
        for (int i = 0; i < attacks.size(); i++) {
            Attack other = attacks.get(i);
            if (Math.floor(other.x / SPOT_CELL) == Math.floor(attack.x / SPOT_CELL)
                && Math.floor(other.z / SPOT_CELL) == Math.floor(attack.z / SPOT_CELL)
                && (other.victim == null) == (attack.victim == null)) {
                // The latest hit there, as the one who hit moves.
                attacks.set(i, attack);
                return;
            }
        }
        if (attacks.size() >= MAX_KEPT) {
            attacks.remove(0);
        }
        attacks.add(attack);
    }

    /** Warns the team's players online, unless they were warned a moment ago. */
    private void warn(Team team, double x, double z, String text, long now) {
        Long last = lastWarning.get(team.getId());
        if (last != null && now - last < WARNING_TICKS && now >= last) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || team.getMembers()
            .isEmpty()) {
            return;
        }
        lastWarning.put(team.getId(), now);
        JsonObject message = WebJson.message("alert");
        message.addProperty("text", text);
        message.addProperty("x", x);
        message.addProperty("z", z);
        String json = message.toString();
        Map<UUID, String> members = team.getMembers();
        for (Object object : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) object;
            if (members.containsKey(player.getUniqueID())) {
                RtsNetwork.sendTo(player, json);
            }
        }
    }
}
