package com.warofthegods.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.WorldServer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.warofthegods.game.Building;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
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
 *   {type: "units.move", units: [id...], x, z, formation?}  walk there, in a box, line or wedge
 *   {type: "units.stance", units: [id...], stance}  aggressive, defensive, hold or passive
 *   {type: "units.attack", units: [id...], target: id}  attack an enemy unit or player, or a mob; workers do not
 *                                                       fight, but hunt cows, pigs, sheep and chickens
 *   {type: "units.attack", units: [id...], building: id}  attack an enemy building
 *   {type: "units.build", units: [id...], building: id}  workers build a building site or work a farm or quarry
 *   {type: "units.gather", units: [id...], x, y, z}  workers chop the tree at or near that block
 *   {type: "units.light", units: [id...], x1, z1, x2, z2}  workers put torches in the dark parts of that area
 *   {type: "units.stop", units: [id...]}        stop and guard where they stand
 * </pre>
 *
 * Training and placing buildings are in {@link com.warofthegods.building.BuildingCommands}.
 */
public final class UnitCommands {

    /** Units one order may name, so a request cannot be made arbitrarily large. */
    private static final int MAX_UNITS = 500;

    private UnitCommands() {}

    public static void register() {
        WebCommand.register("units.move", UnitCommands::move);
        WebCommand.register("units.attack", UnitCommands::attack);
        WebCommand.register("units.build", UnitCommands::build);
        WebCommand.register("units.stop", UnitCommands::stop);
        WebCommand.register("units.gather", UnitCommands::gather);
        WebCommand.register("units.light", UnitCommands::light);
        WebCommand.register("units.stance", UnitCommands::stance);
    }

    /** The viewer's team, or null after replying with why they cannot command units. */
    public static Team commander(WebRequest request) {
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
        Units.Formation formation = request.body.has("formation") ? Units.Formation.byId(
            request.body.get("formation")
                .getAsString())
            : null;
        Units.get()
            .orderMove(units, x, z, formation == null ? Units.Formation.BOX : formation);
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
        if (request.body.has("building")) {
            units.removeIf(EntityUnit::isWorker);
            if (units.isEmpty()) {
                request.replyError("Workers do not fight");
                return;
            }
            Game game = GameManager.getGame();
            Building building = game.getBuilding(intField(request.body, "building", 0));
            Team owner = building == null ? null : game.getTeam(building.team);
            if (owner == null) {
                request.replyError("That building is gone");
                return;
            }
            if (!game.areEnemies(team, owner)) {
                request.replyError("You can only attack enemies");
                return;
            }
            ok(
                request,
                Units.get()
                    .orderAttackBuilding(units, building));
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
        // Workers hunt animals that leave food; they fight nothing else.
        List<EntityUnit> workers = new ArrayList<>();
        for (EntityUnit unit : units) {
            if (unit.isWorker()) {
                workers.add(unit);
            }
        }
        units.removeAll(workers);
        int hunters = Hunting.foodOf(target) > 0 ? Units.get()
            .orderHunt(workers, target) : 0;
        if (units.isEmpty()) {
            if (hunters == 0) {
                request.replyError("Workers do not fight");
            } else {
                ok(request, hunters);
            }
            return;
        }
        // Any mob can be hunted on command; units and players only if their team is an enemy.
        if (!Units.isMob(target) && !Units.get()
            .isEnemy(units.get(0), target)) {
            request.replyError("You can only attack enemies");
            return;
        }
        Units.get()
            .orderAttack(units, target);
        ok(request, units.size() + hunters);
    }

    private static void build(WebRequest request) {
        Team team = commander(request);
        if (team == null) {
            return;
        }
        List<EntityUnit> units = ownUnits(request, team);
        if (units == null) {
            return;
        }
        Game game = GameManager.getGame();
        Building building = game.getBuilding(intField(request.body, "building", 0));
        if (building == null || !building.team.equals(team.getId())) {
            request.replyError("That is not your building");
            return;
        }
        if (building.isComplete() && !building.type.isWorkplace()) {
            request.replyError("That is already built");
            return;
        }
        int sent = Units.get()
            .orderBuild(units, building);
        if (sent == 0) {
            request.replyError("Only workers can build and gather");
            return;
        }
        ok(request, sent);
    }

    private static void gather(WebRequest request) {
        Team team = commander(request);
        if (team == null) {
            return;
        }
        List<EntityUnit> units = ownUnits(request, team);
        if (units == null) {
            return;
        }
        Double x = doubleField(request.body, "x");
        Double y = doubleField(request.body, "y");
        Double z = doubleField(request.body, "z");
        if (x == null || z == null) {
            request.replyError("Where to?");
            return;
        }
        Game game = GameManager.getGame();
        WorldServer world = GameWorld.getWorld();
        double fromY = y != null ? y : world.getHeightValue((int) Math.floor(x), (int) Math.floor(z));
        int[] tree = Trees.findTree(world, game, x, fromY, z);
        if (tree == null || !game.isInside(tree[0], tree[2])) {
            request.replyError("There is no tree there");
            return;
        }
        int sent = Units.get()
            .orderChop(units, tree[0], tree[1], tree[2]);
        if (sent == 0) {
            request.replyError("Only workers chop trees");
            return;
        }
        Trees.track(world, game, tree[0], tree[1], tree[2]);
        JsonObject reply = WebJson.message("ok");
        reply.addProperty("units", sent);
        reply.addProperty("x", tree[0]);
        reply.addProperty("y", tree[1]);
        reply.addProperty("z", tree[2]);
        request.reply(reply);
    }

    private static void light(WebRequest request) {
        Team team = commander(request);
        if (team == null) {
            return;
        }
        List<EntityUnit> units = ownUnits(request, team);
        if (units == null) {
            return;
        }
        Double x1 = doubleField(request.body, "x1");
        Double z1 = doubleField(request.body, "z1");
        Double x2 = doubleField(request.body, "x2");
        Double z2 = doubleField(request.body, "z2");
        if (x1 == null || z1 == null || x2 == null || z2 == null) {
            request.replyError("Which area?");
            return;
        }
        List<int[]> spots = Torches.plan(
            GameWorld.getWorld(),
            GameManager.getGame(),
            (int) Math.floor(x1),
            (int) Math.floor(z1),
            (int) Math.floor(x2),
            (int) Math.floor(z2));
        if (spots.isEmpty()) {
            request.replyError("That area is lit already");
            return;
        }
        int sent = Units.get()
            .orderLight(units, spots);
        if (sent == 0) {
            request.replyError("Only workers put up torches");
            return;
        }
        JsonObject reply = WebJson.message("ok");
        reply.addProperty("units", sent);
        reply.addProperty("torches", spots.size());
        request.reply(reply);
    }

    private static void stance(WebRequest request) {
        Team team = commander(request);
        if (team == null) {
            return;
        }
        List<EntityUnit> units = ownUnits(request, team);
        if (units == null) {
            return;
        }
        EntityUnit.Stance stance = request.body.has("stance") ? EntityUnit.Stance.byId(
            request.body.get("stance")
                .getAsString())
            : null;
        if (stance == null) {
            request.replyError("Unknown stance");
            return;
        }
        Units.get()
            .orderStance(units, stance);
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
    public static List<EntityUnit> ownUnits(WebRequest request, Team team) {
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

    public static UUID uuid(JsonElement element) {
        try {
            return element == null ? null : UUID.fromString(element.getAsString());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Double doubleField(JsonObject body, String name) {
        try {
            double value = body.get(name)
                .getAsDouble();
            return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static int intField(JsonObject body, String name, int fallback) {
        try {
            return body.has(name) ? body.get(name)
                .getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
