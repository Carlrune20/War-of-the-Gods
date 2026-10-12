package com.warofthegods.building;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.warofthegods.game.Building;
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Team;
import com.warofthegods.game.UnitKind;
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
 *   {type: "buildings.place", building: type, x, z, rotation, units: [id...]}
 *       lay out a building site centred on x, z, paying for it, and send the workers among the units to build it
 *   {type: "buildings.train", building: id, count}  queue units at a town hall or barracks, paying for each
 * </pre>
 */
public final class BuildingCommands {

    private static final int MAX_TRAIN = 10;

    private BuildingCommands() {}

    public static void register() {
        WebCommand.register("buildings.place", BuildingCommands::place);
        WebCommand.register("buildings.train", BuildingCommands::train);
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
            .orderBuild(units, site);
        JsonObject reply = WebJson.message("buildings.placed");
        reply.addProperty("building", site.id);
        request.reply(reply);
    }

    private static void train(WebRequest request) {
        Team team = UnitCommands.commander(request);
        if (team == null) {
            return;
        }
        Game game = GameManager.getGame();
        Building building = game.getBuilding(UnitCommands.intField(request.body, "building", 0));
        if (building == null || !building.team.equals(team.getId())) {
            request.replyError("That is not your building");
            return;
        }
        UnitKind kind = building.type.trains;
        if (kind == null) {
            request.replyError("A " + building.type.displayName.toLowerCase() + " does not train units");
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
            request.replyError("Your team has the most units it can have");
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
