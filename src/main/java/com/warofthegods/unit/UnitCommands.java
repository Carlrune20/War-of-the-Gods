package com.warofthegods.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.warofthegods.Config;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Team;
import com.warofthegods.web.WebCommand;
import com.warofthegods.web.WebJson;
import com.warofthegods.web.WebRequest;
import com.warofthegods.world.GameWorld;

/**
 * Unit orders browsers send over the web map's WebSocket. Only players on a team can give them, and only to their own
 * team's units:
 *
 * <pre>
 *   {type: "units.train", count}                train units at the team's base, paying for each
 *   {type: "units.move", units: [id...], x, z}  walk there
 *   {type: "units.attack", units: [id...], target: id}  attack an enemy unit or player
 *   {type: "units.stop", units: [id...]}        stop and guard where they stand
 * </pre>
 */
public final class UnitCommands {

    /** Units one order may name, so a request cannot be made arbitrarily large. */
    private static final int MAX_UNITS = 500;
    private static final int MAX_TRAIN = 10;

    private UnitCommands() {}

    public static void register() {
        WebCommand.register("units.train", UnitCommands::train);
        WebCommand.register("units.move", UnitCommands::move);
        WebCommand.register("units.attack", UnitCommands::attack);
        WebCommand.register("units.stop", UnitCommands::stop);
    }

    /** The viewer's team, or null after replying with why they cannot command units. */
    private static Team commander(WebRequest request) {
        Game game = GameManager.getGame();
        if (game == null) {
            request.replyError("No game is running");
            return null;
        }
        if (request.viewer.isObserver()) {
            request.replyError("Observers cannot command units");
            return null;
        }
        Team team = game.getTeamOf(request.viewer.getPlayer());
        if (team == null) {
            request.replyError("Join a team to command units");
        }
        return team;
    }

    private static void train(WebRequest request) {
        Team team = commander(request);
        if (team == null) {
            return;
        }
        Game game = GameManager.getGame();
        int count = Math.max(1, Math.min(MAX_TRAIN, intField(request.body, "count", 1)));
        int room = Config.unitsPerTeam - game.countUnits(team);
        if (room <= 0) {
            request.replyError("Your team has the most units it can have (" + Config.unitsPerTeam + ")");
            return;
        }
        count = Math.min(count, room);
        Map<Resource, Long> cost = Units.unitCost();
        int affordable = 0;
        while (affordable < count && team.spend(cost)) {
            affordable++;
        }
        if (affordable == 0) {
            request.replyError("Not enough resources");
            return;
        }
        List<EntityUnit> spawned = Units.get()
            .spawn(game, team, affordable);
        // Refund anything that could not be placed.
        for (int i = spawned.size(); i < affordable; i++) {
            for (Map.Entry<Resource, Long> entry : cost.entrySet()) {
                team.addResource(entry.getKey(), entry.getValue());
            }
        }
        JsonObject reply = WebJson.message("units.trained");
        reply.addProperty("count", spawned.size());
        request.reply(reply);
    }

    private static void move(WebRequest request) {
        Team team = commander(request);
        if (team == null) {
            return;
        }
        List<EntityUnit> units = ownUnits(request, team);
        Double x = doubleField(request.body, "x");
        Double z = doubleField(request.body, "z");
        if (units == null) {
            return;
        }
        if (x == null || z == null) {
            request.replyError("Where to?");
            return;
        }
        Units.get()
            .orderMove(units, x, z);
        ok(request, units.size());
    }

    private static void attack(WebRequest request) {
        Team team = commander(request);
        if (team == null) {
            return;
        }
        List<EntityUnit> units = ownUnits(request, team);
        if (units == null) {
            return;
        }
        UUID targetId = uuid(request.body.get("target"));
        EntityLivingBase target = targetId == null ? null
            : Units.get()
                .findLiving(GameWorld.getWorld(), targetId);
        if (target == null || !target.isEntityAlive()) {
            request.replyError("That target is gone");
            return;
        }
        if (!Units.get()
            .isEnemy(units.get(0), target)) {
            request.replyError("You can only attack enemies");
            return;
        }
        Units.get()
            .orderAttack(units, target);
        ok(request, units.size());
    }

    private static void stop(WebRequest request) {
        Team team = commander(request);
        if (team == null) {
            return;
        }
        List<EntityUnit> units = ownUnits(request, team);
        if (units == null) {
            return;
        }
        Units.get()
            .orderStop(units);
        ok(request, units.size());
    }

    private static void ok(WebRequest request, int count) {
        JsonObject reply = WebJson.message("ok");
        reply.addProperty("units", count);
        request.reply(reply);
    }

    /** The team's loaded units the request names, or null after replying that there are none. */
    private static List<EntityUnit> ownUnits(WebRequest request, Team team) {
        List<EntityUnit> units = new ArrayList<>();
        JsonElement ids = request.body.get("units");
        if (ids != null && ids.isJsonArray()) {
            JsonArray array = ids.getAsJsonArray();
            for (int i = 0; i < Math.min(array.size(), MAX_UNITS); i++) {
                UUID id = uuid(array.get(i));
                EntityUnit unit = id == null ? null
                    : Units.get()
                        .getUnit(id);
                if (unit != null && unit.getTeamId()
                    .equals(team.getId())) {
                    units.add(unit);
                }
            }
        }
        if (units.isEmpty()) {
            request.replyError("None of those units are yours");
            return null;
        }
        return units;
    }

    private static UUID uuid(JsonElement element) {
        try {
            return element == null ? null : UUID.fromString(element.getAsString());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Double doubleField(JsonObject body, String name) {
        try {
            double value = body.get(name)
                .getAsDouble();
            return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int intField(JsonObject body, String name, int fallback) {
        try {
            return body.has(name) ? body.get(name)
                .getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
