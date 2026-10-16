package com.warofthegods.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumChatFormatting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.game.Faction;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Team;
import com.warofthegods.rts.RtsNetwork;
import com.warofthegods.unit.UnitCommands;
import com.warofthegods.web.Viewer;
import com.warofthegods.web.WebCommand;
import com.warofthegods.web.WebJson;
import com.warofthegods.web.WebRequest;
import com.warofthegods.world.GameWorld;

/**
 * The new game lobby. An op opens it, which ends the running game; everyone online then sees it. Ops pick the map, its
 * size and seed, how many teams there are, which god each team follows and whether the computer plays on it; ops put
 * players on teams, and players pick their own team when the server allows it. The game starts once every player
 * online has clicked ready. Players still on no team then go on the team with the fewest players, the computer
 * counting as one. The lobby lives only in memory: a restart closes it.
 *
 * <pre>
 *   {type: "lobby.get"}                                the lobby, as a "lobby" message
 *   {type: "lobby.open"}                               (ops) end the game and open the lobby
 *   {type: "lobby.close"}                              (ops) close the lobby without starting a game
 *   {type: "lobby.settings", map?, size?, seed?, teams?} (ops) change the map, its size, its seed or the team count
 *   {type: "lobby.team", team, faction?, ai?}          (ops) change a team's god, or whether the computer plays on it
 *   {type: "lobby.move", team, player?}                put yourself, or (ops) another player, on a team; -1 for none
 *   {type: "lobby.ready", ready}                       say you are ready, or not
 * </pre>
 *
 * Every change is sent to every player online as a "lobby" message; {"type": "lobby", "open": false} closes it.
 */
public final class Lobby {

    /** Maps a game can be played on, as id and name. Misthalin is the only one so far. */
    public static final String[][] MAPS = { { "misthalin", "Misthalin" } };
    /** Map sizes ops pick from, besides the configured default. */
    private static final int[] SIZES = { 256, 500, 750, 1000, 1500, 2000, 3000 };
    public static final int MAX_TEAMS = 8;

    private static final class Slot {

        final EnumChatFormatting color;
        Faction faction;
        boolean ai;
        /** Player UUID to name. */
        final Map<UUID, String> players = new LinkedHashMap<>();

        Slot(EnumChatFormatting color) {
            this.color = color;
            this.faction = Faction.forColor(color);
        }
    }

    private static Lobby current;
    /** A player logging out, who is still on the server's player list while they go. */
    private static UUID leaving;

    private String map = MAPS[0][0];
    private int size = Config.defaultMapSize;
    /** The seed as typed; blank for a random one. */
    private String seed = "";
    private final List<Slot> slots = new ArrayList<>();
    private final Set<UUID> ready = new HashSet<>();

    private Lobby() {}

    public static void register() {
        WebCommand.register("lobby.get", Lobby::get);
        WebCommand.register("lobby.open", Lobby::openCommand);
        WebCommand.register("lobby.close", Lobby::closeCommand);
        WebCommand.register("lobby.settings", Lobby::settings);
        WebCommand.register("lobby.team", Lobby::team);
        WebCommand.register("lobby.move", Lobby::move);
        WebCommand.register("lobby.ready", Lobby::readyCommand);
    }

    public static boolean isOpen() {
        return current != null;
    }

    // ---------------------------------------------------------------- opening and closing

    /**
     * Ends the running game and opens the lobby, with the teams of the game that ended (or the default number of
     * teams) and everyone online on the team they were on. Does nothing if it is open already.
     */
    public static void open(String by) {
        if (current != null) {
            return;
        }
        Lobby lobby = new Lobby();
        Game previous = GameManager.getGame();
        if (previous != null) {
            lobby.size = previous.getSize();
            for (Team team : previous.getTeams()) {
                if (lobby.slots.size() >= MAX_TEAMS) {
                    break;
                }
                Slot slot = lobby.addSlot();
                slot.faction = team.getFaction();
                slot.ai = team.isAi();
                for (EntityPlayerMP player : online()) {
                    if (team.getMembers()
                        .containsKey(player.getUniqueID())) {
                        slot.players.put(player.getUniqueID(), player.getCommandSenderName());
                    }
                }
            }
            GameManager.endGame();
            CommandRts.broadcast("Game #" + previous.getNumber() + " has ended.");
        }
        while (lobby.slots.size() < Math.max(1, Math.min(MAX_TEAMS, Config.defaultTeamCount))) {
            lobby.addSlot();
        }
        current = lobby;
        CommandRts.broadcast(by + " opened the lobby for a new game. Press B if it is not on your screen.");
        WarOfTheGods.LOG.info("{} opened the new game lobby", by);
        sendAll();
    }

    public static void close() {
        if (current == null) {
            return;
        }
        current = null;
        JsonObject closed = WebJson.message("lobby");
        closed.addProperty("open", false);
        for (EntityPlayerMP player : online()) {
            RtsNetwork.sendTo(player, closed.toString());
        }
    }

    private Slot addSlot() {
        EnumChatFormatting color = Team.COLORS[slots.size() % Team.COLORS.length];
        Slot slot = new Slot(color);
        slots.add(slot);
        return slot;
    }

    // ---------------------------------------------------------------- players coming and going

    /** Shows a player who logs in the lobby, if it is open. */
    public static void onLogin(EntityPlayerMP player) {
        if (current != null) {
            RtsNetwork.sendTo(
                player,
                current.toJson(player)
                    .toString());
            sendAll();
        }
    }

    /** Takes a player who logs out out of the lobby; the rest may all be ready now. */
    public static void onLogout(UUID player) {
        if (current == null) {
            return;
        }
        current.ready.remove(player);
        for (Slot slot : current.slots) {
            slot.players.remove(player);
        }
        leaving = player;
        try {
            current.sendAllOrStart();
        } finally {
            leaving = null;
        }
    }

    // ---------------------------------------------------------------- commands

    private static boolean player(WebRequest request) {
        if (request.viewer.isObserver()) {
            request.replyError("Observers have no place in the lobby");
            return false;
        }
        return true;
    }

    private static boolean openLobby(WebRequest request) {
        if (current == null) {
            request.replyError("The lobby is not open");
            return false;
        }
        return player(request);
    }

    private static boolean op(WebRequest request) {
        if (!TeamCommands.isOp(request)) {
            request.replyError("Only ops can change the game settings");
            return false;
        }
        return true;
    }

    private static void get(WebRequest request) {
        if (!player(request)) {
            return;
        }
        EntityPlayerMP player = TeamCommands.online(request.viewer.getPlayer());
        if (current == null || player == null) {
            JsonObject closed = WebJson.message("lobby");
            closed.addProperty("open", false);
            closed.addProperty("op", TeamCommands.isOp(request));
            request.reply(closed);
            return;
        }
        request.reply(current.toJson(player));
    }

    private static void openCommand(WebRequest request) {
        if (!player(request) || !op(request)) {
            return;
        }
        open(request.viewer.getName());
        request.reply(WebJson.message("ok"));
    }

    private static void closeCommand(WebRequest request) {
        if (!openLobby(request) || !op(request)) {
            return;
        }
        close();
        CommandRts.broadcast(request.viewer.getName() + " closed the lobby. No game is running.");
        request.reply(WebJson.message("ok"));
    }

    private static void settings(WebRequest request) {
        if (!openLobby(request) || !op(request)) {
            return;
        }
        Lobby lobby = current;
        JsonObject body = request.body;
        if (body.has("map")) {
            String id = body.get("map")
                .getAsString();
            boolean known = false;
            for (String[] map : MAPS) {
                known |= map[0].equals(id);
            }
            if (!known) {
                request.replyError("Unknown map " + id);
                return;
            }
            lobby.map = id;
        }
        if (body.has("size")) {
            int size = body.get("size")
                .getAsInt();
            if (size < 64 || size > 100000) {
                request.replyError("Maps are 64 to 100000 blocks across");
                return;
            }
            lobby.size = size;
        }
        if (body.has("seed")) {
            String seed = body.get("seed")
                .getAsString()
                .trim();
            lobby.seed = seed.length() > 32 ? seed.substring(0, 32) : seed;
        }
        if (body.has("teams")) {
            int teams = Math.max(
                1,
                Math.min(
                    MAX_TEAMS,
                    body.get("teams")
                        .getAsInt()));
            while (lobby.slots.size() < teams) {
                lobby.addSlot();
            }
            while (lobby.slots.size() > teams) {
                lobby.slots.remove(lobby.slots.size() - 1);
            }
        }
        lobby.changed();
        request.reply(WebJson.message("ok"));
    }

    private Slot slot(WebRequest request) {
        int index = request.body.has("team") ? request.body.get("team")
            .getAsInt() : -2;
        if (index < 0 || index >= slots.size()) {
            request.replyError("Unknown team");
            return null;
        }
        return slots.get(index);
    }

    private static void team(WebRequest request) {
        if (!openLobby(request) || !op(request)) {
            return;
        }
        Lobby lobby = current;
        Slot slot = lobby.slot(request);
        if (slot == null) {
            return;
        }
        if (request.body.has("faction")) {
            Faction faction = Faction.byId(
                request.body.get("faction")
                    .getAsString());
            if (faction == null) {
                request.replyError("Unknown god");
                return;
            }
            slot.faction = faction;
        }
        if (request.body.has("ai")) {
            slot.ai = request.body.get("ai")
                .getAsBoolean();
        }
        lobby.changed();
        request.reply(WebJson.message("ok"));
    }

    private static void move(WebRequest request) {
        if (!openLobby(request)) {
            return;
        }
        Lobby lobby = current;
        boolean op = TeamCommands.isOp(request);
        UUID id = request.viewer.getPlayer();
        if (request.body.has("player")) {
            UUID other = UnitCommands.uuid(request.body.get("player"));
            if (other == null) {
                request.replyError("Unknown player");
                return;
            }
            if (!other.equals(id) && !op) {
                request.replyError("Only ops can move other players");
                return;
            }
            id = other;
        }
        if (!op && !Config.allowSelfJoin) {
            request.replyError("Only ops can change teams on this server");
            return;
        }
        EntityPlayerMP player = TeamCommands.online(id);
        if (player == null) {
            request.replyError("That player is not online");
            return;
        }
        int index = request.body.has("team") ? request.body.get("team")
            .getAsInt() : -1;
        Slot slot = null;
        if (index >= 0) {
            slot = lobby.slot(request);
            if (slot == null) {
                return;
            }
        }
        for (Slot each : lobby.slots) {
            each.players.remove(id);
        }
        if (slot != null) {
            slot.players.put(id, player.getCommandSenderName());
        }
        lobby.ready.remove(id);
        lobby.sendAllOrStart();
        request.reply(WebJson.message("ok"));
    }

    private static void readyCommand(WebRequest request) {
        if (!openLobby(request)) {
            return;
        }
        Lobby lobby = current;
        UUID id = request.viewer.getPlayer();
        boolean ready = !request.body.has("ready") || request.body.get("ready")
            .getAsBoolean();
        if (ready) {
            lobby.ready.add(id);
        } else {
            lobby.ready.remove(id);
        }
        request.reply(WebJson.message("ok"));
        lobby.sendAllOrStart();
    }

    /** A setting changed: everyone has to look again and say they are ready. */
    private void changed() {
        ready.clear();
        sendAll();
    }

    private void sendAllOrStart() {
        if (!startIfReady()) {
            sendAll();
        }
    }

    // ---------------------------------------------------------------- starting

    /** Players online who have not said they are ready. */
    private List<String> waitingFor() {
        List<String> waiting = new ArrayList<>();
        for (EntityPlayerMP player : online()) {
            if (!ready.contains(player.getUniqueID())) {
                waiting.add(player.getCommandSenderName());
            }
        }
        return waiting;
    }

    /** Starts the game once everyone online is ready; returns whether it did. */
    private boolean startIfReady() {
        if (current != this || online().isEmpty() || !waitingFor().isEmpty()) {
            return false;
        }
        start();
        return true;
    }

    /** Starts the game now, whoever is ready; for /rts lobby start. */
    public static Game startNow() {
        return current == null ? null : current.start();
    }

    private Game start() {
        // Players still on no team go where there are fewest, the computer counting as one.
        for (EntityPlayerMP player : online()) {
            boolean placed = false;
            for (Slot slot : slots) {
                placed |= slot.players.containsKey(player.getUniqueID());
            }
            if (!placed) {
                Slot fewest = null;
                for (Slot slot : slots) {
                    if (fewest == null || headcount(slot) < headcount(fewest)) {
                        fewest = slot;
                    }
                }
                fewest.players.put(player.getUniqueID(), player.getCommandSenderName());
            }
        }
        List<GameManager.TeamSetup> teams = new ArrayList<>();
        for (Slot slot : slots) {
            GameManager.TeamSetup setup = new GameManager.TeamSetup(slot.color);
            setup.faction = slot.faction;
            setup.ai = slot.ai;
            setup.players.putAll(slot.players);
            teams.add(setup);
        }
        long gameSeed = seed.isEmpty() ? new Random().nextLong() : CommandRts.parseSeed(seed);
        close();
        Game game = GameManager.newGame(size, teams, gameSeed);
        CommandRts.broadcast(
            "Game #" + game.getNumber()
                + " has started on "
                + mapName(map)
                + ", "
                + size
                + "x"
                + size
                + ", with "
                + teams.size()
                + (teams.size() == 1 ? " team." : " teams."));
        CommandRts.announceTeams(game);
        for (Team team : game.getTeams()) {
            if (team.isAi()) {
                CommandRts
                    .broadcast("The computer plays on team " + team.getColoredName() + EnumChatFormatting.GOLD + ".");
            }
        }
        GameWorld.sendAllToGame(game);
        return game;
    }

    private static int headcount(Slot slot) {
        return slot.players.size() + (slot.ai ? 1 : 0);
    }

    private static String mapName(String id) {
        for (String[] map : MAPS) {
            if (map[0].equals(id)) {
                return map[1];
            }
        }
        return id;
    }

    // ---------------------------------------------------------------- telling players

    private static List<EntityPlayerMP> online() {
        List<EntityPlayerMP> players = new ArrayList<>();
        for (Object object : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) object;
            if (!player.getUniqueID()
                .equals(leaving)) {
                players.add(player);
            }
        }
        return players;
    }

    private static void sendAll() {
        if (current == null) {
            return;
        }
        for (EntityPlayerMP player : online()) {
            RtsNetwork.sendTo(
                player,
                current.toJson(player)
                    .toString());
        }
    }

    private JsonObject playerJson(UUID id, String name) {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", id.toString());
        json.addProperty("name", name);
        json.addProperty("ready", ready.contains(id));
        return json;
    }

    private JsonObject toJson(EntityPlayerMP viewer) {
        JsonObject message = WebJson.message("lobby");
        message.addProperty("open", true);
        WebRequest asViewer = new WebRequest(
            Viewer.player(viewer.getUniqueID(), viewer.getCommandSenderName()),
            reply -> {},
            "lobby",
            new JsonObject());
        message.addProperty("op", TeamCommands.isOp(asViewer));
        message.addProperty("selfJoin", Config.allowSelfJoin);
        message.addProperty(
            "me",
            viewer.getUniqueID()
                .toString());
        message.addProperty("map", map);
        message.addProperty("mapName", mapName(map));
        JsonArray maps = new JsonArray();
        for (String[] each : MAPS) {
            JsonObject json = new JsonObject();
            json.addProperty("id", each[0]);
            json.addProperty("name", each[1]);
            maps.add(json);
        }
        message.add("maps", maps);
        message.addProperty("size", size);
        JsonArray sizes = new JsonArray();
        for (int each : sizeChoices()) {
            sizes.add(new JsonPrimitive(each));
        }
        message.add("sizes", sizes);
        message.addProperty("seed", seed);
        message.addProperty("maxTeams", MAX_TEAMS);
        JsonArray teams = new JsonArray();
        Set<UUID> placed = new HashSet<>();
        for (Slot slot : slots) {
            JsonObject json = new JsonObject();
            json.addProperty("name", Game.colorDisplayName(slot.color));
            json.addProperty("color", Team.rgbOf(slot.color));
            json.addProperty("faction", slot.faction.id);
            json.addProperty("factionName", slot.faction.displayName);
            json.addProperty("ai", slot.ai);
            JsonArray players = new JsonArray();
            for (Map.Entry<UUID, String> player : slot.players.entrySet()) {
                players.add(playerJson(player.getKey(), player.getValue()));
                placed.add(player.getKey());
            }
            json.add("players", players);
            teams.add(json);
        }
        message.add("teams", teams);
        JsonArray unassigned = new JsonArray();
        for (EntityPlayerMP player : online()) {
            if (!placed.contains(player.getUniqueID())) {
                unassigned.add(playerJson(player.getUniqueID(), player.getCommandSenderName()));
            }
        }
        message.add("unassigned", unassigned);
        JsonArray waiting = new JsonArray();
        for (String name : waitingFor()) {
            waiting.add(new JsonPrimitive(name));
        }
        message.add("waiting", waiting);
        return message;
    }

    /** The sizes to pick from, in order, with the configured default and the current size among them. */
    private List<Integer> sizeChoices() {
        List<Integer> choices = new ArrayList<>();
        for (int each : SIZES) {
            choices.add(each);
        }
        for (int extra : new int[] { Config.defaultMapSize, size }) {
            if (!choices.contains(extra)) {
                choices.add(extra);
            }
        }
        choices.sort(Integer::compare);
        return choices;
    }
}
