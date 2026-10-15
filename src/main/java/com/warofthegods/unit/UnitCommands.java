package com.warofthegods.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.warofthegods.game.Building;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.GoldMine;
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
 *   {type: "units.move", units: [id...], x, z, formation?}  walk there, in a box, line or wedge; with attack: true
 *                                                   warriors and archers fight any enemy they meet on the way
 *   {type: "units.stance", units: [id...], stance}  aggressive, defensive, hold or passive
 *   {type: "units.attack", units: [id...], target: id}  attack an enemy unit or player, or a mob; workers do not
 *                                                       fight, but hunt cows, pigs, sheep and chickens
 *   {type: "units.attack", units: [id...], building: id}  attack an enemy building
 *   {type: "units.attack", units: [id...], targets: [id...]}  attack all of them, shared out among the units by
 *                                                             health, each going on to the rest after its own
 *   units.attack takes queue: true to attack after the enemies the units were already ordered at
 *   {type: "units.build", units: [id...], building: id}  workers build a building site, repair a damaged
 *                                                        building or work a farm
 *   {type: "units.gather", units: [id...], x, y, z}  workers chop the tree at or near that block, or mine the gold
 *                                                    mine there
 *   {type: "units.light", units: [id...], x1, z1, x2, z2}  workers put torches in the dark parts of that area
 *   {type: "units.stop", units: [id...]}        stop and guard where they stand
 *   {type: "units.follow", units: [id...], target: uuid, formation?}  follow a player of their team in formation
 *   {type: "units.kill", units: [id...]}        kill the team's own units
 *   target may also be "carcass-" and a carcass's number: workers carve it
 *   units.build takes queue: true to build it after the sites the workers are on
 * </pre>
 *
 * Training and placing buildings are in {@link com.warofthegods.building.BuildingCommands}.
 */
public final class UnitCommands {

    /** Carcasses go by this and their number, where living things go by their UUID. */
    private static final String CARCASS = "carcass-";

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
        WebCommand.register("units.follow", UnitCommands::follow);
        WebCommand.register("units.kill", UnitCommands::kill);
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
        } else if (team.isDefeated()) {
            request.replyError("Your team has been defeated");
            return null;
        }
        return team;
    }

    private static void kill(WebRequest request) {
        Team team = commander(request);
        List<EntityUnit> units = team == null ? null : ownUnits(request, team);
        if (units == null) {
            return;
        }
        for (EntityUnit unit : units) {
            // They die as units do, falling over, and are forgotten like any fallen unit.
            unit.attackEntityFrom(DamageSource.outOfWorld, 1.0e9F);
        }
        ok(request, units.size());
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
        if (boolField(request.body, "attack")) {
            Units.get()
                .orderAttackMove(units, x, z, formation == null ? Units.Formation.BOX : formation);
        } else {
            Units.get()
                .orderMove(units, x, z, formation == null ? Units.Formation.BOX : formation);
        }
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
        if (request.body.has("targets")) {
            attackAll(request, team, units);
            return;
        }
        JsonElement targetField = request.body.get("target");
        if (targetField != null && targetField.isJsonPrimitive()
            && targetField.getAsString()
                .startsWith(CARCASS)) {
            carve(request, units, targetField.getAsString());
            return;
        }
        UUID targetId = uuid(targetField);
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
        if (boolField(request.body, "queue")) {
            Units.get()
                .queueAttack(units, target);
        } else {
            Units.get()
                .orderAttack(units, target);
        }
        ok(request, units.size() + hunters);
    }

    /** Most targets one attack box may name. */
    private static final int MAX_TARGETS = 200;

    /** The fighters attack every enemy named, shared out among them; workers stay out of it. */
    private static void attackAll(WebRequest request, Team team, List<EntityUnit> units) {
        units.removeIf(EntityUnit::isWorker);
        if (units.isEmpty()) {
            request.replyError("Workers do not fight");
            return;
        }
        JsonElement field = request.body.get("targets");
        if (!field.isJsonArray()) {
            request.replyError("Attack what?");
            return;
        }
        WorldServer world = GameWorld.getWorld();
        List<EntityLivingBase> targets = new ArrayList<>();
        for (JsonElement element : field.getAsJsonArray()) {
            if (targets.size() >= MAX_TARGETS) {
                break;
            }
            UUID id = uuid(element);
            EntityLivingBase target = id == null ? null
                : Units.get()
                    .findLiving(world, id);
            // Enemies, and mobs; anything else in the box is left alone.
            if (target != null && target.isEntityAlive()
                && !targets.contains(target)
                && (Units.isMob(target) || Units.get()
                    .isEnemy(units.get(0), target))) {
                targets.add(target);
            }
        }
        if (targets.isEmpty()) {
            request.replyError("There are no enemies there");
            return;
        }
        Units.get()
            .orderAttackAll(units, targets, boolField(request.body, "queue"));
        JsonObject reply = WebJson.message("ok");
        reply.addProperty("units", units.size());
        reply.addProperty("targets", targets.size());
        request.reply(reply);
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
        if (building.isComplete() && !building.type.isWorkplace() && !building.isDamaged()) {
            request.replyError("That is already built and needs no repair");
            return;
        }
        int sent = Units.get()
            .orderBuild(units, building, boolField(request.body, "queue"));
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
        GoldMine mine = game.mineAt(x, z, 1);
        if (mine != null) {
            if (mine.isDepleted()) {
                request.replyError("That gold mine is mined out");
                return;
            }
            int sent = Units.get()
                .orderMine(units, mine);
            if (sent == 0) {
                request.replyError("Only workers mine gold");
                return;
            }
            JsonObject reply = WebJson.message("ok");
            reply.addProperty("units", sent);
            reply.addProperty("mine", mine.id);
            request.reply(reply);
            return;
        }
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

    private static void follow(WebRequest request) {
        Team team = commander(request);
        if (team == null) {
            return;
        }
        List<EntityUnit> units = ownUnits(request, team);
        if (units == null) {
            return;
        }
        UUID leaderId = uuid(request.body.get("target"));
        Game game = GameManager.getGame();
        Entity leader = Units.leader(GameWorld.getWorld(), leaderId);
        if (leader == null || game.getTeamOf(leaderId) != team) {
            request.replyError("Units follow only players of their own team who are on the map");
            return;
        }
        Units.Formation formation = request.body.has("formation") ? Units.Formation.byId(
            request.body.get("formation")
                .getAsString())
            : null;
        Units.get()
            .orderFollow(units, leader, formation == null ? Units.Formation.BOX : formation);
        ok(request, units.size());
    }

    /** Workers sent to a carcass carve it; nobody else has anything to do there. */
    private static void carve(WebRequest request, List<EntityUnit> units, String target) {
        Hunting.Carcass carcass;
        try {
            carcass = Hunting.get(Integer.parseInt(target.substring(CARCASS.length())));
        } catch (NumberFormatException e) {
            carcass = null;
        }
        if (carcass == null || carcass.getFood() <= 0) {
            request.replyError("Nothing is left of it");
            return;
        }
        int sent = Units.get()
            .orderCarve(units, carcass);
        if (sent == 0) {
            request.replyError("Only workers carve food");
            return;
        }
        ok(request, sent);
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

    /** A true or false field, false when missing. */
    public static boolean boolField(JsonObject body, String name) {
        JsonElement field = body.get(name);
        return field != null && field.isJsonPrimitive() && field.getAsBoolean();
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
