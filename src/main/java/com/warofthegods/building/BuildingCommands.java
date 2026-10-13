package com.warofthegods.building;

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
        if (GameManager.getGame()
            .getAge(team) < type.age()) {
            request.replyError(
                "Your town has to reach " + BuildingType.ageName(type.age())
                    + " first: take the town center up a level");
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
        units.removeIf(unit -> !unit.isWorker());
        if (units.isEmpty()) {
            request.replyError("Select a worker to build with");
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
            request.replyError("Select a town center or barracks");
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
            : building.type.trains.get(0);
        if (kind == null || !building.type.trains.contains(kind)) {
            request.replyError("A " + building.type.displayName.toLowerCase() + " does not train those");
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
        int room = Buildings.room(game, team);
        if (room <= 0) {
            request.replyError(
                game.getPopulationCap(team) < Config.unitsPerTeam ? "Build more houses to train more units"
                    : "Your team has the most units it can have");
            return;
        }
        count = Math.min(count, room);
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
