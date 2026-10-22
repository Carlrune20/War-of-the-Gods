package com.warofthegods.command;

import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumChatFormatting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.warofthegods.Config;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Team;
import com.warofthegods.web.WebCommand;
import com.warofthegods.web.WebJson;
import com.warofthegods.web.WebRequest;
import com.warofthegods.world.GameWorld;

/**
 * The team menu players open with B. The same rules as /rts team apply: players join and leave teams themselves when
 * the server allows it, and only ops move other players or deal everyone out afresh.
 *
 * <pre>
 *   {type: "teams.list"}                     the teams and their players, and who is on no team; running is false,
 *                                            with no teams, while no game is running
 *   {type: "teams.join", team, player?}      join a team, or (ops) put another player on it
 *   {type: "teams.leave", player?}           leave your team, or (ops) take another player off theirs
 *   {type: "teams.auto", all?}               (ops) put players on no team on the smallest teams; all deals everyone
 *   {type: "teams.ai", team, mode}           hand your own team (ops: any team) to the computer: mode is off,
 *                                            economy (it runs only the workers) or full
 * </pre>
 */
public final class TeamCommands {

    private TeamCommands() {}

    public static void register() {
        WebCommand.register("teams.list", TeamCommands::list);
        WebCommand.register("teams.join", TeamCommands::join);
        WebCommand.register("teams.leave", TeamCommands::leave);
        WebCommand.register("teams.auto", TeamCommands::auto);
        WebCommand.register("teams.ai", TeamCommands::ai);
    }

    static EntityPlayerMP online(UUID id) {
        if (id == null) {
            return null;
        }
        for (Object object : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) object;
            if (player.getUniqueID()
                .equals(id)) {
                return player;
            }
        }
        return null;
    }

    static boolean isOp(WebRequest request) {
        EntityPlayerMP player = online(request.viewer.getPlayer());
        return player != null && player.canCommandSenderUseCommand(2, "rts");
    }

    private static Game game(WebRequest request) {
        Game game = GameManager.getGame();
        if (game == null) {
            request.replyError("No game is running");
        } else if (request.viewer.isObserver()) {
            request.replyError("Observers have no team");
            return null;
        }
        return game;
    }

    private static void list(WebRequest request) {
        if (GameManager.getGame() == null && !request.viewer.isObserver()) {
            // The menu still opens, so an op can start a game from it.
            JsonObject message = WebJson.message("teams");
            message.addProperty("running", false);
            message.add("teams", new JsonArray());
            message.add("unassigned", new JsonArray());
            message.addProperty("mine", "");
            message.addProperty("op", isOp(request));
            message.addProperty("selfJoin", Config.allowSelfJoin);
            request.reply(message);
            return;
        }
        Game game = game(request);
        if (game == null) {
            return;
        }
        JsonObject message = WebJson.message("teams");
        message.addProperty("running", true);
        JsonArray teams = new JsonArray();
        for (Team team : game.getTeams()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", team.getId());
            json.addProperty("name", team.getName());
            json.addProperty("color", team.getRgb());
            json.addProperty("faction", team.getFaction().displayName);
            json.addProperty("ai", team.getAiMode());
            JsonArray members = new JsonArray();
            for (Map.Entry<UUID, String> member : team.getMembers()
                .entrySet()) {
                members.add(player(member.getKey(), member.getValue()));
            }
            json.add("members", members);
            teams.add(json);
        }
        message.add("teams", teams);
        JsonArray unassigned = new JsonArray();
        for (Object object : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) object;
            if (game.getTeamOf(player.getUniqueID()) == null) {
                unassigned.add(player(player.getUniqueID(), player.getCommandSenderName()));
            }
        }
        message.add("unassigned", unassigned);
        Team mine = game.getTeamOf(request.viewer.getPlayer());
        message.addProperty("mine", mine == null ? "" : mine.getId());
        message.addProperty("op", isOp(request));
        message.addProperty("selfJoin", Config.allowSelfJoin);
        request.reply(message);
    }

    private static JsonObject player(UUID id, String name) {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", id.toString());
        json.addProperty("name", name);
        json.addProperty("online", online(id) != null);
        return json;
    }

    /**
     * The player a request is about: the viewer, or with a player field (ops only) someone else; null after replying
     * with why not.
     */
    private static UUID[] subject(WebRequest request) {
        boolean op = isOp(request);
        UUID id = request.viewer.getPlayer();
        if (request.body.has("player")) {
            UUID other;
            try {
                other = UUID.fromString(
                    request.body.get("player")
                        .getAsString());
            } catch (RuntimeException e) {
                request.replyError("Unknown player");
                return null;
            }
            if (!other.equals(id)) {
                if (!op) {
                    request.replyError("Only ops can move other players");
                    return null;
                }
                id = other;
            }
        }
        if (!op && !Config.allowSelfJoin) {
            request.replyError("Only ops can change teams on this server");
            return null;
        }
        return new UUID[] { id };
    }

    private static String nameOf(Game game, UUID id, WebRequest request) {
        EntityPlayerMP player = online(id);
        if (player != null) {
            return player.getCommandSenderName();
        }
        Team team = game.getTeamOf(id);
        return team == null ? request.viewer.getName()
            : team.getMembers()
                .get(id);
    }

    private static void join(WebRequest request) {
        Game game = game(request);
        if (game == null) {
            return;
        }
        UUID[] subject = subject(request);
        if (subject == null) {
            return;
        }
        Team team = request.body.has("team") ? game.getTeam(
            request.body.get("team")
                .getAsString())
            : null;
        if (team == null) {
            request.replyError("Unknown team");
            return;
        }
        String name = nameOf(game, subject[0], request);
        if (team == game.getTeamOf(subject[0])) {
            request.reply(WebJson.message("ok"));
            return;
        }
        game.setTeamOf(subject[0], name, team);
        moveSpawn(game, subject[0]);
        CommandRts.broadcast(name + " joined team " + team.getColoredName());
        request.reply(WebJson.message("ok"));
    }

    private static void leave(WebRequest request) {
        Game game = game(request);
        if (game == null) {
            return;
        }
        UUID[] subject = subject(request);
        if (subject == null) {
            return;
        }
        String name = nameOf(game, subject[0], request);
        Team team = game.removePlayer(subject[0]);
        moveSpawn(game, subject[0]);
        if (team != null) {
            CommandRts.broadcast(name + " left team " + team.getColoredName());
        }
        request.reply(WebJson.message("ok"));
    }

    /** Players who change team come back to life at their new team's town center. */
    private static void moveSpawn(Game game, UUID id) {
        EntityPlayerMP player = online(id);
        if (player != null) {
            GameWorld.setTeamSpawn(player, game);
        }
    }

    /**
     * Hands a team to the computer, in full or only to run its workers, or takes it back. Players may do this for their
     * own team; ops for any.
     */
    private static void ai(WebRequest request) {
        Game game = game(request);
        if (game == null) {
            return;
        }
        Team team = request.body.has("team") ? game.getTeam(
            request.body.get("team")
                .getAsString())
            : null;
        if (team == null) {
            request.replyError("Unknown team");
            return;
        }
        if (!isOp(request) && team != game.getTeamOf(request.viewer.getPlayer())) {
            request.replyError("Only ops can hand other teams to the computer");
            return;
        }
        String mode = request.body.has("mode") ? request.body.get("mode")
            .getAsString() : "";
        if (!"off".equals(mode) && !"economy".equals(mode) && !"full".equals(mode)) {
            request.replyError("Unknown AI mode");
            return;
        }
        if (!mode.equals(team.getAiMode())) {
            setAi(team, mode, request.viewer.getName());
        }
        request.reply(WebJson.message("ok"));
    }

    /** Sets how much of the team the computer plays, "off", "economy" or "full", and tells everyone who did it. */
    static void setAi(Team team, String mode, String by) {
        team.setAi(!"off".equals(mode), "economy".equals(mode));
        String what = "off".equals(mode) ? " is no longer played by the computer"
            : "economy".equals(mode) ? ": the computer now runs its workers" : " is now played by the computer";
        CommandRts.broadcast(
            "Team " + team.getColoredName() + EnumChatFormatting.GOLD + what + (by == null ? "" : " (" + by + ")"));
    }

    private static void auto(WebRequest request) {
        Game game = game(request);
        if (game == null) {
            return;
        }
        if (!isOp(request)) {
            request.replyError("Only ops can share out the teams");
            return;
        }
        if (request.body.has("all") && request.body.get("all")
            .getAsBoolean()) {
            for (Object object : MinecraftServer.getServer()
                .getConfigurationManager().playerEntityList) {
                game.removePlayer(((EntityPlayerMP) object).getUniqueID());
            }
        }
        int placed = GameManager.autoAssign(game);
        if (placed > 0) {
            for (Team team : game.getTeams()) {
                CommandRts.broadcast(
                    "Team " + team.getColoredName()
                        + EnumChatFormatting.GOLD
                        + ": "
                        + (team.getMembers()
                            .isEmpty() ? "nobody"
                                : String.join(
                                    ", ",
                                    team.getMembers()
                                        .values())));
            }
        }
        request.reply(WebJson.message("ok"));
    }
}
