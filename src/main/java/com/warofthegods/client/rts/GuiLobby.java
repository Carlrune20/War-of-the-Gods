package com.warofthegods.client.rts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The new game lobby, which comes up for everyone when an op opens it. Ops pick the map, its size and seed, how many
 * teams there are, each team's god and whether the computer plays on it, and can move anyone; every player picks
 * their own numbered team. Everyone clicks Ready, and the game starts once every player online has.
 */
public final class GuiLobby extends GuiScreen {

    private static final int PANEL = 0xC0101418;
    private static final int READY = 0x80FF80;
    private static final int NOT_READY = 0xE0E0E0;
    private static final int COMPUTER = 0x9AB8FF;

    private final RtsClient client;
    private final Map<Integer, Runnable> actions = new HashMap<>();
    /** Player names on screen: x, y, width, and the player's uuid and name. */
    private final List<Object[]> names = new ArrayList<>();
    private GuiTextField seedField;
    /** The seed as last sent, so it is only sent again when it changes. */
    private String sentSeed = "";
    /** The uuid of the player an op picked to move, or null. */
    private String picked;
    private String pickedName;

    public GuiLobby(RtsClient client) {
        this.client = client;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private JsonObject lobby() {
        return client.getLobby();
    }

    private boolean isOp() {
        JsonObject lobby = lobby();
        return lobby != null && lobby.get("op")
            .getAsBoolean();
    }

    private static String text(JsonObject json, String key) {
        return json.has(key) ? json.get(key)
            .getAsString() : "";
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        seedField = new GuiTextField(fontRendererObj, 0, 0, 80, 18);
        seedField.setMaxStringLength(32);
        JsonObject lobby = lobby();
        sentSeed = lobby == null ? "" : text(lobby, "seed");
        seedField.setText(sentSeed);
        layout();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    /** The server sent the lobby again. */
    void lobbyChanged() {
        JsonObject lobby = lobby();
        if (lobby != null && seedField != null && !seedField.isFocused()) {
            sentSeed = text(lobby, "seed");
            seedField.setText(sentSeed);
        }
        layout();
    }

    @Override
    public void updateScreen() {
        if (seedField != null) {
            seedField.updateCursorCounter();
        }
    }

    // ---------------------------------------------------------------- layout

    /** Left edge of the settings row and each of its parts: map, size, teams, seed. */
    private int[] settingsAt() {
        int total = 100 + 8 + 96 + 8 + 90 + 8 + 110;
        int x = Math.max(4, (width - total) / 2);
        return new int[] { x, x + 108, x + 212, x + 310 };
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelWidth() {
        return Math.min(width - 16, 470);
    }

    private int rowsTop() {
        return 52;
    }

    private int rowHeight(int teams) {
        int room = height - rowsTop() - 62;
        return Math.max(20, Math.min(26, room / (teams + 1)));
    }

    private void layout() {
        buttonList.clear();
        actions.clear();
        JsonObject lobby = lobby();
        if (lobby == null) {
            return;
        }
        boolean op = isOp();
        int[] at = settingsAt();
        int y = 26;
        if (op) {
            JsonArray maps = lobby.getAsJsonArray("maps");
            String map = text(lobby, "map");
            addButton(at[0], y, 100, "Map: " + text(lobby, "mapName"), () -> {
                int index = 0;
                for (int i = 0; i < maps.size(); i++) {
                    if (text(
                        maps.get(i)
                            .getAsJsonObject(),
                        "id").equals(map)) {
                        index = i;
                    }
                }
                JsonObject body = new JsonObject();
                body.addProperty(
                    "map",
                    text(
                        maps.get((index + 1) % maps.size())
                            .getAsJsonObject(),
                        "id"));
                send("lobby.settings", body);
            });
            JsonArray sizes = lobby.getAsJsonArray("sizes");
            int size = lobby.get("size")
                .getAsInt();
            addButton(at[1], y, 20, "<", () -> stepSize(sizes, size, -1));
            addButton(at[1] + 76, y, 20, ">", () -> stepSize(sizes, size, 1));
            int teams = lobby.getAsJsonArray("teams")
                .size();
            int most = lobby.get("maxTeams")
                .getAsInt();
            GuiButton fewer = addButton(at[2], y, 20, "-", () -> setTeams(teams - 1));
            fewer.enabled = teams > 1;
            GuiButton more = addButton(at[2] + 70, y, 20, "+", () -> setTeams(teams + 1));
            more.enabled = teams < most;
            seedField.xPosition = at[3] + 30;
            seedField.yPosition = y + 1;
        }

        JsonArray teams = lobby.getAsJsonArray("teams");
        int left = panelLeft();
        int right = left + panelWidth();
        int rowHeight = rowHeight(teams.size());
        String me = text(lobby, "me");
        int mine = myTeam(lobby);
        for (int i = 0; i < teams.size(); i++) {
            JsonObject team = teams.get(i)
                .getAsJsonObject();
            int ry = rowsTop() + i * rowHeight + (rowHeight - 20) / 2;
            int index = i;
            if (op) {
                boolean zamorak = "zamorak".equals(text(team, "faction"));
                addButton(left + 78, ry, 72, text(team, "factionName"), () -> {
                    JsonObject body = new JsonObject();
                    body.addProperty("team", index);
                    body.addProperty("faction", zamorak ? "saradomin" : "zamorak");
                    send("lobby.team", body);
                });
                boolean ai = team.get("ai")
                    .getAsBoolean();
                addButton(left + 154, ry, 56, ai ? "AI: on" : "AI: off", () -> {
                    JsonObject body = new JsonObject();
                    body.addProperty("team", index);
                    body.addProperty("ai", !ai);
                    send("lobby.team", body);
                });
            }
            if (op && picked != null) {
                String player = picked;
                addButton(right - 58, ry, 54, "Move here", () -> move(index, player));
            } else if (mine != i) {
                addButton(right - 58, ry, 54, "Join", () -> move(index, me));
            }
        }
        int noneY = rowsTop() + teams.size() * rowHeight + (rowHeight - 20) / 2;
        if (op && picked != null && myTeamOf(lobby, picked) >= 0) {
            String player = picked;
            addButton(right - 58, noneY, 54, "Take off", () -> move(-1, player));
        } else if (mine >= 0) {
            addButton(right - 58, noneY, 54, "Leave", () -> move(-1, me));
        }

        List<Object[]> footer = new ArrayList<>();
        boolean ready = isReady(lobby, me);
        footer.add(new Object[] { ready ? "Not ready" : "Ready", (Runnable) () -> {
            JsonObject body = new JsonObject();
            body.addProperty("ready", !ready);
            send("lobby.ready", body);
        } });
        if (op) {
            footer.add(new Object[] { "Cancel lobby", (Runnable) () -> send("lobby.close", new JsonObject()) });
        }
        footer.add(new Object[] { "Close", (Runnable) () -> mc.displayGuiScreen(null) });
        int w = 90;
        int x = (width - footer.size() * (w + 6) + 6) / 2;
        for (Object[] item : footer) {
            addButton(x, height - 26, w, (String) item[0], (Runnable) item[1]);
            x += w + 6;
        }
    }

    private void stepSize(JsonArray sizes, int size, int step) {
        int index = 0;
        for (int i = 0; i < sizes.size(); i++) {
            if (sizes.get(i)
                .getAsInt() == size) {
                index = i;
            }
        }
        int next = Math.max(0, Math.min(sizes.size() - 1, index + step));
        JsonObject body = new JsonObject();
        body.addProperty(
            "size",
            sizes.get(next)
                .getAsInt());
        send("lobby.settings", body);
    }

    private void setTeams(int teams) {
        JsonObject body = new JsonObject();
        body.addProperty("teams", teams);
        send("lobby.settings", body);
    }

    private void move(int team, String player) {
        JsonObject body = new JsonObject();
        body.addProperty("team", team);
        body.addProperty("player", player);
        picked = null;
        pickedName = null;
        send("lobby.move", body);
    }

    private void sendSeed() {
        String seed = seedField.getText()
            .trim();
        if (!seed.equals(sentSeed)) {
            sentSeed = seed;
            JsonObject body = new JsonObject();
            body.addProperty("seed", seed);
            send("lobby.settings", body);
        }
    }

    private void send(String type, JsonObject body) {
        client.command(type, body, null);
    }

    private int myTeam(JsonObject lobby) {
        return myTeamOf(lobby, text(lobby, "me"));
    }

    /** The index of the team the player is on, or -1. */
    private static int myTeamOf(JsonObject lobby, String uuid) {
        JsonArray teams = lobby.getAsJsonArray("teams");
        for (int i = 0; i < teams.size(); i++) {
            for (JsonElement player : teams.get(i)
                .getAsJsonObject()
                .getAsJsonArray("players")) {
                if (text(player.getAsJsonObject(), "uuid").equals(uuid)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isReady(JsonObject lobby, String uuid) {
        List<JsonElement> everyone = new ArrayList<>();
        for (JsonElement team : lobby.getAsJsonArray("teams")) {
            for (JsonElement player : team.getAsJsonObject()
                .getAsJsonArray("players")) {
                everyone.add(player);
            }
        }
        for (JsonElement player : lobby.getAsJsonArray("unassigned")) {
            everyone.add(player);
        }
        for (JsonElement player : everyone) {
            JsonObject json = player.getAsJsonObject();
            if (text(json, "uuid").equals(uuid)) {
                return json.get("ready")
                    .getAsBoolean();
            }
        }
        return false;
    }

    private GuiButton addButton(int x, int y, int w, String text, Runnable action) {
        int id = actions.size();
        GuiButton button = new GuiButton(id, x, y, w, 20, text);
        buttonList.add(button);
        actions.put(id, action);
        return button;
    }

    // ---------------------------------------------------------------- input

    @Override
    protected void actionPerformed(GuiButton button) {
        Runnable action = actions.get(button.id);
        if (action != null) {
            action.run();
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        if (isOp()) {
            boolean wasFocused = seedField.isFocused();
            seedField.mouseClicked(x, y, button);
            if (wasFocused && !seedField.isFocused()) {
                sendSeed();
            }
        }
        if (button != 0 || !isOp()) {
            return;
        }
        for (Object[] name : names) {
            int nx = (Integer) name[0];
            int ny = (Integer) name[1];
            int nw = (Integer) name[2];
            if (x >= nx - 2 && x < nx + nw + 2 && y >= ny - 2 && y < ny + 10) {
                String uuid = (String) name[3];
                if (uuid.equals(picked)) {
                    picked = null;
                    pickedName = null;
                } else {
                    picked = uuid;
                    pickedName = (String) name[4];
                }
                layout();
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int key) {
        if (isOp() && seedField.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                seedField.setFocused(false);
                sendSeed();
            } else if (key == Keyboard.KEY_ESCAPE) {
                seedField.setFocused(false);
                seedField.setText(sentSeed);
            } else {
                seedField.textboxKeyTyped(typedChar, key);
            }
            return;
        }
        if (key == Keyboard.KEY_ESCAPE || key == client.teamsKey.getKeyCode()) {
            mc.displayGuiScreen(null);
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "New game", width / 2, 8, 0xFFFFFF);
        names.clear();
        JsonObject lobby = lobby();
        if (lobby == null) {
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        boolean op = isOp();
        int[] at = settingsAt();
        int y = 32;
        String size = lobby.get("size")
            .getAsString();
        int teamCount = lobby.getAsJsonArray("teams")
            .size();
        String seed = text(lobby, "seed");
        if (op) {
            drawCenteredString(fontRendererObj, size + "x" + size, at[1] + 48, y, 0xFFFFFF);
            drawCenteredString(
                fontRendererObj,
                teamCount + (teamCount == 1 ? " team" : " teams"),
                at[2] + 45,
                y,
                0xFFFFFF);
            fontRendererObj.drawStringWithShadow("Seed", at[3], y, 0xA0A0A0);
        } else {
            fontRendererObj.drawStringWithShadow("Map: " + text(lobby, "mapName"), at[0], y, 0xFFFFFF);
            fontRendererObj.drawStringWithShadow("Size: " + size + "x" + size, at[1], y, 0xFFFFFF);
            fontRendererObj.drawStringWithShadow("Teams: " + teamCount, at[2], y, 0xFFFFFF);
            fontRendererObj.drawStringWithShadow("Seed: " + (seed.isEmpty() ? "random" : seed), at[3], y, 0xFFFFFF);
        }

        JsonArray teams = lobby.getAsJsonArray("teams");
        int left = panelLeft();
        int right = left + panelWidth();
        int rowHeight = rowHeight(teams.size());
        String me = text(lobby, "me");
        for (int i = 0; i <= teams.size(); i++) {
            int top = rowsTop() + i * rowHeight;
            int textY = top + (rowHeight - 8) / 2;
            drawRect(left, top + 1, right, top + rowHeight - 1, PANEL);
            List<JsonObject> players = new ArrayList<>();
            boolean ai = false;
            if (i < teams.size()) {
                JsonObject team = teams.get(i)
                    .getAsJsonObject();
                int color = team.get("color")
                    .getAsInt();
                drawRect(left, top + 1, left + 3, top + rowHeight - 1, 0xFF000000 | color);
                fontRendererObj.drawStringWithShadow((i + 1) + ". " + text(team, "name"), left + 7, textY, color);
                ai = team.get("ai")
                    .getAsBoolean();
                if (!op) {
                    fontRendererObj.drawStringWithShadow(text(team, "factionName"), left + 82, textY, 0xB0B0B0);
                }
                for (JsonElement player : team.getAsJsonArray("players")) {
                    players.add(player.getAsJsonObject());
                }
            } else {
                fontRendererObj.drawStringWithShadow("No team", left + 7, textY, 0xB0B0B0);
                for (JsonElement player : lobby.getAsJsonArray("unassigned")) {
                    players.add(player.getAsJsonObject());
                }
            }
            int x = left + 216;
            int end = right - 62;
            if (ai) {
                fontRendererObj.drawStringWithShadow("Computer", x, textY, COMPUTER);
                x += fontRendererObj.getStringWidth("Computer") + 8;
            }
            if (players.isEmpty() && !ai) {
                fontRendererObj.drawStringWithShadow("nobody", x, textY, 0x606060);
            }
            for (int p = 0; p < players.size(); p++) {
                JsonObject player = players.get(p);
                String uuid = text(player, "uuid");
                String name = text(player, "name") + (uuid.equals(me) ? " (you)" : "");
                int nw = fontRendererObj.getStringWidth(name);
                if (x + nw > end) {
                    fontRendererObj.drawStringWithShadow("+" + (players.size() - p), x, textY, 0xA0A0A0);
                    break;
                }
                if (uuid.equals(picked)) {
                    drawRect(x - 2, textY - 2, x + nw + 2, textY + 10, 0x80507090);
                }
                boolean ready = player.get("ready")
                    .getAsBoolean();
                fontRendererObj.drawStringWithShadow(name, x, textY, ready ? READY : NOT_READY);
                names.add(new Object[] { x, textY, nw, uuid, text(player, "name") });
                x += nw + 8;
            }
        }

        JsonArray waiting = lobby.getAsJsonArray("waiting");
        String status;
        if (waiting.size() == 0) {
            status = "Everyone is ready";
        } else {
            List<String> list = new ArrayList<>();
            for (JsonElement name : waiting) {
                list.add(name.getAsString());
            }
            status = "Waiting for " + String.join(", ", list) + " to click Ready";
        }
        drawCenteredString(fontRendererObj, status, width / 2, height - 52, 0xC0C0C0);
        String hint = op ? (picked == null ? "Green players are ready. Click a player to move them."
            : "Pick where " + pickedName + " goes") : "Green players are ready.";
        drawCenteredString(fontRendererObj, hint, width / 2, height - 40, 0x909090);
        String toast = client.getToast();
        if (toast != null) {
            drawCenteredString(fontRendererObj, toast, width / 2, 18, client.isToastBad() ? 0xFF8A80 : 0xA8FFB0);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (op) {
            seedField.drawTextBox();
            if (!seedField.isFocused() && seedField.getText()
                .isEmpty()) {
                fontRendererObj
                    .drawStringWithShadow("random", seedField.xPosition + 4, seedField.yPosition + 5, 0x707070);
            }
        }
    }
}
