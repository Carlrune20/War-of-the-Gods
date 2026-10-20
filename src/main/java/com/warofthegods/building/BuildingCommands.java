package com.warofthegods.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.warofthegods.Config;
import com.warofthegods.game.Building;
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Team;
import com.warofthegods.game.UnitKind;
import com.warofthegods.game.Upgrade;
import com.warofthegods.unit.EntityUnit;
import com.warofthegods.unit.UnitCommands;
import com.warofthegods.unit.Units;
import com.warofthegods.web.WebCommand;
import com.warofthegods.web.WebJson;
import com.warofthegods.web.WebRequest;
import com.warofthegods.world.GameWorld;

/**
 * Building commands browsers send over the web map's WebSocket. Only players on a team can give them, for their own
 * team:
 *
 * <pre>
 *   {type: "buildings.place", building: type, x, z, rotation, units: [id...], queue?}  with queue, the workers
 *       build it after the sites they are on
 *       lay out a building site centred on x, z, paying for it, and send the workers among the units to build it
 *   {type: "buildings.train", building: id, unit: kind, count}  queue units at a town center or barracks,
 *       paying for each; unit may be left out where the building trains one kind
 *   {type: "buildings.upgrade", building: id}  take the building up a level, paying for it
 *   {type: "buildings.research", building: id, upgrade: id}  research an upgrade at an armory or storehouse
 *   {type: "buildings.rally", buildings: [id...], x?, z?}  send the units they train to x, z; with no x, z, keep
 *       them at the door
 *   {type: "buildings.auto", buildings: [id...], unit?}  train the unit over and over, one more whenever the
 *       queue runs empty while the team can pay; with no unit, stop
 *   {type: "buildings.wall", x1, z1, x2, z2, units: [id...], queue?}  lay out a wall from x1, z1 to x2, z2,
 *       paying for every piece, and send the workers to build it piece by piece; an end on one of the team's
 *       towers runs up to the tower
 *   {type: "buildings.bridge", x1, z1, x2, z2, units: [id...], queue?}  lay out a wooden bridge from x1, z1
 *       to x2, z2 across water, paying for every block of it, and send the workers to build it from the first end
 *   {type: "buildings.gate", building: id}  make a finished wall segment a gate at once, paying for it
 *   {type: "buildings.cancel", building: id, index}  take the unit at that place off the training queue, giving
 *       back what it cost; with research: true instead, stop what the building researches or its level-up
 *   {type: "buildings.destroy", buildings: [id...]}  take the team's own buildings or building sites down at once
 * </pre>
 */
public final class BuildingCommands {

    private static final int MAX_TRAIN = 10;

    private BuildingCommands() {}

    public static void register() {
        WebCommand.register("buildings.place", BuildingCommands::place);
        WebCommand.register("buildings.train", BuildingCommands::train);
        WebCommand.register("buildings.upgrade", BuildingCommands::upgrade);
        WebCommand.register("buildings.research", BuildingCommands::research);
        WebCommand.register("buildings.rally", BuildingCommands::rally);
        WebCommand.register("buildings.auto", BuildingCommands::auto);
        WebCommand.register("buildings.wall", BuildingCommands::wall);
        WebCommand.register("buildings.bridge", BuildingCommands::bridge);
        WebCommand.register("buildings.gate", BuildingCommands::gate);
        WebCommand.register("buildings.cancel", BuildingCommands::cancel);
        WebCommand.register("buildings.destroy", BuildingCommands::destroy);
    }

    /** The viewer's own building the request names, or null after replying why not. */
    private static Building ownBuilding(WebRequest request, Team team) {
        Game game = GameManager.getGame();
        Building building = game.getBuilding(UnitCommands.intField(request.body, "building", 0));
        if (building == null || !building.team.equals(team.getId())) {
            request.replyError("That is not your building");
            return null;
        }
        return building;
    }

    private static void upgrade(WebRequest request) {
        Team team = UnitCommands.commander(request);
        Building building = team == null ? null : ownBuilding(request, team);
        if (building == null) {
            return;
        }
        Game game = GameManager.getGame();
        String problem = Buildings.levelUpProblem(game, building);
        if (problem != null) {
            request.replyError(problem);
            return;
        }
        if (!team.spend(building.type.upgradeCost(building.getLevel() + 1))) {
            request.replyError("Not enough resources");
            return;
        }
        building.startResearch(Building.LEVEL_UP);
        JsonObject reply = WebJson.message("buildings.upgrading");
        reply.addProperty("level", building.getLevel() + 1);
        request.reply(reply);
    }

    private static void research(WebRequest request) {
        Team team = UnitCommands.commander(request);
        Building building = team == null ? null : ownBuilding(request, team);
        if (building == null) {
            return;
        }
        JsonElement field = request.body.get("upgrade");
        Upgrade upgrade = field != null && field.isJsonPrimitive() ? Upgrade.byId(field.getAsString()) : null;
        if (upgrade == null) {
            request.replyError("There is no such upgrade");
            return;
        }
        String problem = Buildings.researchProblem(GameManager.getGame(), building, upgrade);
        if (problem != null) {
            request.replyError(problem);
            return;
        }
        if (!team.spend(upgrade.cost())) {
            request.replyError("Not enough resources");
            return;
        }
        building.startResearch(upgrade.id);
        JsonObject reply = WebJson.message("buildings.researching");
        reply.addProperty("upgrade", upgrade.id);
        request.reply(reply);
    }

    private static void cancel(WebRequest request) {
        Team team = UnitCommands.commander(request);
        Building building = team == null ? null : ownBuilding(request, team);
        if (building == null) {
            return;
        }
        Map<Resource, Long> refund;
        JsonObject reply;
        if (UnitCommands.boolField(request.body, "research")) {
            String research = building.getResearch();
            if (research == null) {
                request.replyError("It is not researching anything");
                return;
            }
            Upgrade upgrade = Upgrade.byId(research);
            refund = Building.LEVEL_UP.equals(research) ? building.type.upgradeCost(building.getLevel() + 1)
                : upgrade != null ? upgrade.cost() : null;
            building.cancelResearch();
            reply = WebJson.message("buildings.cancelled");
            reply.addProperty("research", research);
        } else {
            UnitKind kind = building.cancelQueued(
                UnitCommands.intField(
                    request.body,
                    "index",
                    building.getQueue()
                        .size() - 1));
            if (kind == null) {
                request.replyError("Nothing is waiting to train there");
                return;
            }
            refund = kind.cost();
            reply = WebJson.message("buildings.cancelled");
            reply.addProperty("unit", kind.id);
        }
        if (refund != null) {
            for (Map.Entry<Resource, Long> entry : refund.entrySet()) {
                team.addResource(entry.getKey(), entry.getValue());
            }
        }
        request.reply(reply);
    }

    private static void destroy(WebRequest request) {
        Team team = UnitCommands.commander(request);
        if (team == null) {
            return;
        }
        Game game = GameManager.getGame();
        List<Building> buildings = new ArrayList<>();
        JsonElement ids = request.body.get("buildings");
        if (ids != null && ids.isJsonArray()) {
            for (JsonElement id : ids.getAsJsonArray()) {
                Building building = id.isJsonPrimitive() ? game.getBuilding(id.getAsInt()) : null;
                if (building != null && building.team.equals(team.getId())) {
                    buildings.add(building);
                }
            }
        }
        if (buildings.isEmpty()) {
            request.replyError("None of those buildings are yours");
            return;
        }
        for (Building building : buildings) {
            Buildings.get()
                .destroy(game, building, "destroyed by its own team");
        }
        JsonObject reply = WebJson.message("ok");
        reply.addProperty("buildings", buildings.size());
        request.reply(reply);
    }

    private static void place(WebRequest request) {
        Team team = UnitCommands.commander(request);
        if (team == null) {
            return;
        }
        JsonElement typeField = request.body.get("building");
        BuildingType type = typeField != null && typeField.isJsonPrimitive()
            ? BuildingType.byId(typeField.getAsString())
            : null;
        if (type == null || !type.buildable) {
            request.replyError("Workers cannot build that");
            return;
        }
        if (type == BuildingType.WALL) {
            request.replyError("Lay a wall out from one point to another");
            return;
        }
        if (type == BuildingType.BRIDGE) {
            request.replyError("Lay a bridge out from one bank to the other");
            return;
        }
        if (type.shunnedBy(team.getFaction())) {
            request.replyError(team.getFaction().displayName + "'s teams build no " + type.displayName.toLowerCase());
            return;
        }
        if (!type.buildableBy(team.getFaction())) {
            request.replyError("Only " + type.faction().displayName + "'s teams can build a " + type.displayName);
            return;
        }
        if (GameManager.getGame()
            .getAge(team) < type.age()) {
            request.replyError(
                "Your town has to reach " + BuildingType.ageName(type.age())
                    + " first: take the town center up a level");
            return;
        }
        Upgrade needed = type.requiredUpgrade();
        if (needed != null && !team.has(needed)) {
            request.replyError(
                "Research " + needed.displayName.toLowerCase()
                    + " at the "
                    + needed.at.displayName.toLowerCase()
                    + " first");
            return;
        }
        Double x = UnitCommands.doubleField(request.body, "x");
        Double z = UnitCommands.doubleField(request.body, "z");
        if (x == null || z == null) {
            request.replyError("Where to?");
            return;
        }
        List<EntityUnit> units = UnitCommands.ownUnits(request, team);
        if (units == null) {
            return;
        }
        units.removeIf(unit -> !unit.canRaise(type));
        if (units.isEmpty()) {
            request.replyError(
                type.raisedByWizards() ? "Select a dark wizard to raise it" : "Select a worker to build with");
            return;
        }
        Game game = GameManager.getGame();
        Buildings.Plan plan = Buildings.get()
            .plan(
                game,
                GameWorld.getWorld(),
                type,
                (int) Math.floor(x),
                (int) Math.floor(z),
                UnitCommands.intField(request.body, "rotation", 0));
        if (plan.problem != null) {
            request.replyError(plan.problem);
            return;
        }
        if (!team.spend(type.cost())) {
            request.replyError("Not enough resources for a " + type.displayName.toLowerCase());
            return;
        }
        Building site = Buildings.get()
            .startSite(game, team, plan);
        Units.get()
            .orderBuild(units, site, UnitCommands.boolField(request.body, "queue"));
        JsonObject reply = WebJson.message("buildings.placed");
        reply.addProperty("building", site.id);
        request.reply(reply);
    }

    private static void wall(WebRequest request) {
        Team team = UnitCommands.commander(request);
        if (team == null) {
            return;
        }
        Double x1 = UnitCommands.doubleField(request.body, "x1");
        Double z1 = UnitCommands.doubleField(request.body, "z1");
        Double x2 = UnitCommands.doubleField(request.body, "x2");
        Double z2 = UnitCommands.doubleField(request.body, "z2");
        if (x1 == null || z1 == null || x2 == null || z2 == null) {
            request.replyError("From where to where?");
            return;
        }
        List<EntityUnit> units = UnitCommands.ownUnits(request, team);
        if (units == null) {
            return;
        }
        units.removeIf(unit -> !unit.canBuild());
        if (units.isEmpty()) {
            request.replyError("Select a worker to build with");
            return;
        }
        Game game = GameManager.getGame();
        Buildings.WallPlan plan = Buildings.get()
            .planWall(
                game,
                GameWorld.getWorld(),
                team,
                (int) Math.floor(x1),
                (int) Math.floor(z1),
                (int) Math.floor(x2),
                (int) Math.floor(z2));
        if (plan.problem != null) {
            request.replyError(plan.problem);
            return;
        }
        if (!team.spend(Buildings.wallCost(plan.pieces.size()))) {
            request.replyError("Not enough gold for " + plan.pieces.size() + " pieces of wall");
            return;
        }
        List<Building> sites = Buildings.get()
            .startWall(game, team, plan);
        boolean queue = UnitCommands.boolField(request.body, "queue");
        for (int i = 0; i < sites.size(); i++) {
            Units.get()
                .orderBuild(units, sites.get(i), queue || i > 0);
        }
        JsonObject reply = WebJson.message("buildings.walled");
        reply.addProperty("pieces", sites.size());
        request.reply(reply);
    }

    private static void bridge(WebRequest request) {
        Team team = UnitCommands.commander(request);
        if (team == null) {
            return;
        }
        Double x1 = UnitCommands.doubleField(request.body, "x1");
        Double z1 = UnitCommands.doubleField(request.body, "z1");
        Double x2 = UnitCommands.doubleField(request.body, "x2");
        Double z2 = UnitCommands.doubleField(request.body, "z2");
        if (x1 == null || z1 == null || x2 == null || z2 == null) {
            request.replyError("From where to where?");
            return;
        }
        List<EntityUnit> units = UnitCommands.ownUnits(request, team);
        if (units == null) {
            return;
        }
        units.removeIf(unit -> !unit.canBuild());
        if (units.isEmpty()) {
            request.replyError("Select a worker to build with");
            return;
        }
        Game game = GameManager.getGame();
        Buildings.BridgePlan plan = Buildings.get()
            .planBridge(
                game,
                GameWorld.getWorld(),
                team,
                (int) Math.floor(x1),
                (int) Math.floor(z1),
                (int) Math.floor(x2),
                (int) Math.floor(z2));
        if (plan.problem != null) {
            request.replyError(plan.problem);
            return;
        }
        if (!team.spend(Buildings.bridgeCost(plan.pieces.size()))) {
            request.replyError("Not enough wood for a bridge " + plan.pieces.size() + " blocks long");
            return;
        }
        List<Building> sites = Buildings.get()
            .startBridge(game, team, plan);
        boolean queue = UnitCommands.boolField(request.body, "queue");
        for (int i = 0; i < sites.size(); i++) {
            Units.get()
                .orderBuild(units, sites.get(i), queue || i > 0);
        }
        JsonObject reply = WebJson.message("buildings.bridged");
        reply.addProperty("pieces", sites.size());
        request.reply(reply);
    }

    private static void gate(WebRequest request) {
        Team team = UnitCommands.commander(request);
        Building building = team == null ? null : ownBuilding(request, team);
        if (building == null) {
            return;
        }
        String problem = Buildings.gateProblem(building);
        if (problem != null) {
            request.replyError(problem);
            return;
        }
        if (!team.spend(BuildingType.GATE.cost())) {
            request.replyError("Not enough gold for a gate");
            return;
        }
        Building gate = Buildings.get()
            .makeGate(GameManager.getGame(), GameWorld.getWorld(), building);
        JsonObject reply = WebJson.message("buildings.gated");
        reply.addProperty("building", gate.id);
        request.reply(reply);
    }

    private static void rally(WebRequest request) {
        Team team = UnitCommands.commander(request);
        if (team == null) {
            return;
        }
        Game game = GameManager.getGame();
        Double x = UnitCommands.doubleField(request.body, "x");
        Double z = UnitCommands.doubleField(request.body, "z");
        double[] rally = null;
        if (x != null && z != null) {
            rally = new double[] { Math.max(game.getMinX() + 0.5, Math.min(game.getMaxX() + 0.5, x)),
                Math.max(game.getMinZ() + 0.5, Math.min(game.getMaxZ() + 0.5, z)) };
        }
        int count = 0;
        JsonElement ids = request.body.get("buildings");
        if (ids != null && ids.isJsonArray()) {
            for (JsonElement id : ids.getAsJsonArray()) {
                Building building = id.isJsonPrimitive() ? game.getBuilding(id.getAsInt()) : null;
                if (building != null && building.team.equals(team.getId()) && !building.type.trains.isEmpty()) {
                    building.setRally(rally);
                    count++;
                }
            }
        }
        if (count == 0) {
            request.replyError("Select a building that trains units");
            return;
        }
        JsonObject reply = WebJson.message("ok");
        reply.addProperty("buildings", count);
        request.reply(reply);
    }

    private static void auto(WebRequest request) {
        Team team = UnitCommands.commander(request);
        if (team == null) {
            return;
        }
        Game game = GameManager.getGame();
        JsonElement unitField = request.body.get("unit");
        UnitKind kind = unitField != null && unitField.isJsonPrimitive() ? UnitKind.byId(unitField.getAsString())
            : null;
        int count = 0;
        JsonElement ids = request.body.get("buildings");
        if (ids != null && ids.isJsonArray()) {
            for (JsonElement id : ids.getAsJsonArray()) {
                Building building = id.isJsonPrimitive() ? game.getBuilding(id.getAsInt()) : null;
                if (building != null && building.team.equals(team.getId())
                    && (kind == null ? !building.type.trains.isEmpty()
                        : building.type.trainsFor(team.getFaction())
                            .contains(kind))) {
                    building.setAutoTrain(kind);
                    count++;
                }
            }
        }
        if (count == 0) {
            request.replyError(
                kind == null ? "Select a building that trains units" : "Select a building that trains those");
            return;
        }
        JsonObject reply = WebJson.message("ok");
        reply.addProperty("buildings", count);
        request.reply(reply);
    }

    private static void train(WebRequest request) {
        Team team = UnitCommands.commander(request);
        if (team == null) {
            return;
        }
        Building building = ownBuilding(request, team);
        if (building == null) {
            return;
        }
        Game game = GameManager.getGame();
        if (building.type.trains.isEmpty()) {
            request.replyError("A " + building.type.displayName.toLowerCase() + " does not train units");
            return;
        }
        JsonElement unitField = request.body.get("unit");
        UnitKind kind = unitField != null && unitField.isJsonPrimitive() ? UnitKind.byId(unitField.getAsString())
            : building.type.trainsFor(team.getFaction())
                .get(0);
        if (kind == null || !building.type.trainsFor(team.getFaction())
            .contains(kind)) {
            request.replyError("A " + building.type.displayName.toLowerCase() + " does not train those");
            return;
        }
        if (!kind.unlockedFor(team)) {
            Upgrade needed = kind.requiredUpgrade();
            request.replyError(
                "Research " + needed.displayName.toLowerCase()
                    + " at the "
                    + needed.at.displayName.toLowerCase()
                    + " first");
            return;
        }
        if (!building.isComplete()) {
            request.replyError("It is not built yet");
            return;
        }
        int count = Math.max(1, Math.min(MAX_TRAIN, UnitCommands.intField(request.body, "count", 1)));
        count = Math.min(
            count,
            Buildings.MAX_QUEUE - building.getQueue()
                .size());
        if (count <= 0) {
            request.replyError("Its queue is full");
            return;
        }
        // Horses are not units, and need no room.
        int room = kind.isAnimal() ? count : Buildings.room(game, team);
        if (room <= 0) {
            request.replyError(
                game.getPopulationCap(team) < Config.unitsPerTeam ? "Build more houses to train more units"
                    : "Your team has the most units it can have");
            return;
        }
        count = Math.min(count, room);
        if (game.heroRoom(team, kind) <= 0) {
            request.replyError(kind.displayName + " is already with your team; train him again once he falls");
            return;
        }
        count = Math.min(count, game.heroRoom(team, kind));
        Map<Resource, Long> cost = kind.cost();
        int queued = 0;
        while (queued < count && team.spend(cost)) {
            building.enqueue(kind);
            queued++;
        }
        if (queued == 0) {
            request.replyError("Not enough resources");
            return;
        }
        JsonObject reply = WebJson.message("buildings.queued");
        reply.addProperty("count", queued);
        request.reply(reply);
    }
}
