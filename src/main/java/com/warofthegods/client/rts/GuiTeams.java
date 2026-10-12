package com.warofthegods.client.rts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The team menu, opened with B in person or in the RTS view: every team with its faction and players, and who is on
 * no team. Players join and leave teams here when the server lets them; ops also pick a player and move them, fill
 * the teams with whoever is on none, or deal everyone out afresh.
 */
public final class GuiTeams extends GuiScreen {

    private static final int COLUMN = 118;
    private static final int GAP = 8;
    private static final int LINE = 11;
    private static final int PANEL = 0xC0101418;

    private final RtsClient client;
    private JsonObject data;
    private int refreshTicks;
    /** The uuid of the player an op picked to move, or null. */
    private String picked;
    private String pickedName;
    private final Map<Integer, Runnable> actions = new HashMap<>();
    /** Player names on screen: x, y, width, and the player's uuid and name. */
    private final List<Object[]> names = new ArrayList<>();

    private static final class Column {

        String id;
        String title;
        String subtitle;
        int color;
        List<JsonObject> players = new ArrayList<>();
    }

    public GuiTeams(RtsClient client) {
        this.client = client;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        refresh();
        layout();
    }

    private void refresh() {
        client.command("teams.list", new JsonObject(), reply -> {
            data = reply;
            layout();
        });
    }

    @Override
    public void updateScreen() {
        if (++refreshTicks >= 40) {
            refreshTicks = 0;
            refresh();
        }
    }

    private boolean isOp() {
        return data != null && data.has("op")
            && data.get("op")
                .getAsBoolean();
    }

    private String mine() {
        return data == null || !data.has("mine") ? ""
            : data.get("mine")
                .getAsString();
    }

    private List<Column> columns() {
        List<Column> columns = new ArrayList<>();
        if (data == null) {
            return columns;
        }
        for (JsonElement element : data.getAsJsonArray("teams")) {
            JsonObject team = element.getAsJsonObject();
            Column column = new Column();
            column.id = team.get("id")
                .getAsString();
            column.title = team.get("name")
                .getAsString();
            column.subtitle = team.get("faction")
                .getAsString();
            column.color = team.get("color")
                .getAsInt();
            for (JsonElement member : team.getAsJsonArray("members")) {
                column.players.add(member.getAsJsonObject());
            }
            columns.add(column);
        }
        Column none = new Column();
        none.title = "No team";
        none.subtitle = "online";
        none.color = 0xB0B0B0;
        for (JsonElement player : data.getAsJsonArray("unassigned")) {
            none.players.add(player.getAsJsonObject());
        }
        columns.add(none);
        return columns;
    }

    /** Where each column goes: as many side by side as fit, in rows. */
    private int perRow(int count) {
        return Math.max(1, Math.min(count, (width - 20 + GAP) / (COLUMN + GAP)));
    }

    private int columnHeight(List<Column> columns) {
        int most = 1;
        for (Column column : columns) {
            most = Math.max(most, column.players.size());
        }
        return 26 + most * LINE + 26;
    }

    private void layout() {
        buttonList.clear();
        actions.clear();
        List<Column> columns = columns();
        int perRow = perRow(columns.size());
        int height = columnHeight(columns);
        int rows = (columns.size() + perRow - 1) / perRow;
        int top = Math.max(30, (this.height - rows * (height + GAP) - 30) / 2);
        boolean op = isOp();
        boolean selfJoin = data != null && data.has("selfJoin")
            && data.get("selfJoin")
                .getAsBoolean();
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            int[] at = columnAt(i, columns.size(), perRow, top, height);
            int y = at[1] + height - 24;
            if (column.id == null) {
                if (op && picked != null && !isUnassigned(picked)) {
                    String player = picked;
                    addButton(at[0] + 4, y, COLUMN - 8, "Take " + shorten(pickedName, 9) + " off", () -> {
                        JsonObject body = new JsonObject();
                        body.addProperty("player", player);
                        send("teams.leave", body);
                    });
                }
                continue;
            }
            String team = column.id;
            if (op && picked != null) {
                String player = picked;
                addButton(at[0] + 4, y, COLUMN - 8, "Move " + shorten(pickedName, 9) + " here", () -> {
                    JsonObject body = new JsonObject();
                    body.addProperty("team", team);
                    body.addProperty("player", player);
                    send("teams.join", body);
                });
            } else if (team.equals(mine())) {
                if (selfJoin || op) {
                    addButton(at[0] + 4, y, COLUMN - 8, "Leave", () -> send("teams.leave", new JsonObject()));
                }
            } else if (selfJoin || op) {
                addButton(at[0] + 4, y, COLUMN - 8, "Join", () -> {
                    JsonObject body = new JsonObject();
                    body.addProperty("team", team);
                    send("teams.join", body);
                });
            }
        }
        int bottom = Math.min(this.height - 24, top + rows * (height + GAP) + 4);
        List<Object[]> footer = new ArrayList<>();
        if (op) {
            footer.add(new Object[] { "Fill teams", (Runnable) () -> send("teams.auto", new JsonObject()) });
            footer.add(new Object[] { "Shuffle everyone", (Runnable) () -> {
                JsonObject body = new JsonObject();
                body.addProperty("all", true);
                send("teams.auto", body);
            } });
        }
        footer.add(new Object[] { "Done", (Runnable) this::close });
        int w = 100;
        int x = (width - footer.size() * (w + 6) + 6) / 2;
        for (Object[] item : footer) {
            addButton(x, bottom, w, (String) item[0], (Runnable) item[1]);
            x += w + 6;
        }
    }

    private int[] columnAt(int index, int count, int perRow, int top, int height) {
        int row = index / perRow;
        int inRow = Math.min(perRow, count - row * perRow);
        int left = (width - inRow * (COLUMN + GAP) + GAP) / 2;
        return new int[] { left + (index % perRow) * (COLUMN + GAP), top + row * (height + GAP) };
    }

    private boolean isUnassigned(String uuid) {
        if (data == null) {
            return true;
        }
        for (JsonElement player : data.getAsJsonArray("unassigned")) {
            if (player.getAsJsonObject()
                .get("uuid")
                .getAsString()
                .equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    private static String shorten(String text, int most) {
        return text.length() <= most ? text : text.substring(0, most - 1) + "…";
    }

    private void addButton(int x, int y, int w, String text, Runnable action) {
        int id = actions.size();
        buttonList.add(new GuiButton(id, x, y, w, 20, text));
        actions.put(id, action);
    }

    private void send(String type, JsonObject body) {
        client.command(type, body, reply -> {
            picked = null;
            pickedName = null;
            refresh();
        });
    }

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
        if (button != 0 || !isOp()) {
            return;
        }
        for (Object[] name : names) {
            int nx = (Integer) name[0];
            int ny = (Integer) name[1];
            int nw = (Integer) name[2];
            if (x >= nx - 2 && x < nx + nw + 2 && y >= ny - 1 && y < ny + LINE - 1) {
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
        if (key == Keyboard.KEY_ESCAPE || key == client.teamsKey.getKeyCode()) {
            close();
        }
    }

    private void close() {
        // Back to the RTS view when it is open; its screen comes back by itself.
        mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Teams", width / 2, 12, 0xFFFFFF);
        names.clear();
        if (data == null) {
            drawCenteredString(fontRendererObj, "Asking the server…", width / 2, height / 2, 0xB0B0B0);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        List<Column> columns = columns();
        int perRow = perRow(columns.size());
        int height = columnHeight(columns);
        int rows = (columns.size() + perRow - 1) / perRow;
        int top = Math.max(30, (this.height - rows * (height + GAP) - 30) / 2);
        String mine = mine();
        String me = mc.thePlayer == null ? client.state.me
            : mc.thePlayer.getUniqueID()
                .toString();
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            int[] at = columnAt(i, columns.size(), perRow, top, height);
            int x = at[0];
            int y = at[1];
            drawRect(x, y, x + COLUMN, y + height, PANEL);
            drawRect(x, y, x + COLUMN, y + 2, 0xFF000000 | column.color);
            if (column.id != null && column.id.equals(mine)) {
                drawRect(x, y + height - 1, x + COLUMN, y + height, 0xFF000000 | column.color);
            }
            fontRendererObj.drawStringWithShadow(column.title, x + 5, y + 6, column.color);
            fontRendererObj.drawStringWithShadow(column.subtitle, x + 5, y + 15, 0x909090);
            int ny = y + 28;
            if (column.players.isEmpty()) {
                fontRendererObj.drawStringWithShadow("nobody", x + 8, ny, 0x606060);
            }
            for (JsonObject player : column.players) {
                String uuid = player.get("uuid")
                    .getAsString();
                String name = player.get("name")
                    .getAsString();
                boolean online = player.get("online")
                    .getAsBoolean();
                String text = shorten(name, 16) + (uuid.equals(me) ? " (you)" : "");
                int nw = fontRendererObj.getStringWidth(text);
                if (uuid.equals(picked)) {
                    drawRect(x + 6, ny - 1, x + 10 + nw, ny + LINE - 2, 0x80507090);
                }
                fontRendererObj.drawStringWithShadow(text, x + 8, ny, online ? 0xFFFFFF : 0x808080);
                names.add(new Object[] { x + 8, ny, nw, uuid, name });
                ny += LINE;
            }
        }
        String hint = isOp() ? (picked == null ? "Click a player to move them" : "Pick where " + pickedName + " goes")
            : null;
        if (hint != null) {
            drawCenteredString(fontRendererObj, hint, width / 2, 22, 0xA0A0A0);
        }
        String toast = client.getToast();
        if (toast != null) {
            drawCenteredString(
                fontRendererObj,
                toast,
                width / 2,
                this.height - 36,
                client.isToastBad() ? 0xFF8A80 : 0xA8FFB0);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
