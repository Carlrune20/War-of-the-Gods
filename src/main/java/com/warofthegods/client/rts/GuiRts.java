package com.warofthegods.client.rts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * The screen of the RTS view: it frees the mouse over the world the camera shows and draws the panels on top, like
 * the web map. Click one of your units to select it (shift-click adds, double-click selects all of them in view),
 * drag a box around several, and right-click the ground to send them there, an enemy or a mob to attack it, or one of
 * your building sites, farms or quarries to send workers to it. Click a building to see what it trains. WASD or the
 * arrow keys (or the mouse at the window's edge) move the camera, Q and E turn it a quarter round, the wheel zooms,
 * the middle button drags it along, the right button dragged tilts and turns it. V or Esc leaves.
 *
 * <p>
 * The world and the mouse are in window pixels; the panels in the scaled units Minecraft draws its screens in.
 */
public final class GuiRts extends GuiScreen {

    private static final int PANEL = 0xC0101418;
    private static final int GREEN = 0xFF5FDC6A;
    private static final int AMBER = 0xFFF0B43C;
    private static final int RED = 0xFFE8493F;
    private static final double PICK_PIXELS = 10;

    private final RtsClient client;
    private final List<Widget> widgets = new ArrayList<>();

    // The mouse button held over the world, where it went down, and whether it has been dragged.
    private int downButton = -1;
    private int downX;
    private int downY;
    private int lastX;
    private int lastY;
    private boolean moved;
    private boolean box;
    private Widget pressedWidget;
    private long lastClickTime;
    private String lastClickId;
    private int mouseX = -1;
    private int mouseY = -1;
    /** The y of the bar's top row, for what goes just above it. */
    private int barTop;

    /** Something on a panel: a button when it has an action, else a label or a progress bar. */
    private static final class Widget {

        int x;
        int y;
        int w;
        int h;
        String text;
        String tip;
        int color = 0xFFFFFFFF;
        boolean enabled = true;
        Consumer<Boolean> action;
        /** For a progress bar, how full it is; NaN otherwise. */
        double fill = Double.NaN;
        int fillColor = GREEN;

        boolean contains(int gx, int gy) {
            return gx >= x && gx < x + w && gy >= y && gy < y + h;
        }
    }

    /** What is under the mouse: a unit, a player or a mob. */
    private static final class Hit {

        final String kind;
        final RtsState.Thing thing;
        final double[] position;

        Hit(String kind, RtsState.Thing thing, double[] position) {
            this.kind = kind;
            this.thing = thing;
            this.position = position;
        }
    }

    GuiRts(RtsClient client) {
        this.client = client;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private RtsCamera camera() {
        return client.getCamera();
    }

    private RtsState state() {
        return client.state;
    }

    // ---------------------------------------------------------------- input

    @Override
    public void handleMouseInput() {
        if (camera() == null) {
            return;
        }
        int px = Mouse.getEventX();
        int py = mc.displayHeight - Mouse.getEventY() - 1;
        int button = Mouse.getEventButton();
        int wheel = Mouse.getEventDWheel();
        mouseX = px;
        mouseY = py;
        if (wheel != 0) {
            scroll(wheel);
        }
        if (button >= 0 && Mouse.getEventButtonState()) {
            press(px, py, button);
        } else if (button >= 0) {
            release(px, py, button);
        } else {
            drag(px, py);
        }
    }

    private void scroll(int wheel) {
        RtsCamera camera = camera();
        if (isCtrlKeyDown()) {
            camera.pitch = clamp(camera.pitch + (wheel > 0 ? 4 : -4), 25, 88);
        } else {
            camera.distance = Math
                .max(10, Math.min(client.maxDistance(), camera.distance * Math.pow(0.88, wheel / 120.0)));
        }
    }

    private void press(int px, int py, int button) {
        int gx = guiX(px);
        int gy = guiY(py);
        if (button == 0) {
            for (Widget widget : widgets) {
                if (widget.action != null && widget.contains(gx, gy)) {
                    pressedWidget = widget;
                    if (widget.enabled) {
                        mc.getSoundHandler()
                            .playSound(
                                net.minecraft.client.audio.PositionedSoundRecord
                                    .func_147674_a(new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
                        widget.action.accept(isShiftKeyDown());
                    }
                    return;
                }
            }
        }
        if (downButton >= 0 || overPanel(gx, gy)) {
            return;
        }
        downButton = button;
        downX = lastX = px;
        downY = lastY = py;
        moved = false;
        box = button == 0 && client.placing == null && state().myTeam() != null;
    }

    private void drag(int px, int py) {
        if (client.placing != null) {
            movePlacing(px, py);
        }
        if (downButton < 0) {
            return;
        }
        int dx = px - lastX;
        int dy = py - lastY;
        lastX = px;
        lastY = py;
        moved = moved || Math.hypot(px - downX, py - downY) > 6;
        RtsCamera camera = camera();
        if (downButton == 2) {
            double blocksPerPixel = 2 * camera.distance * Math.tan(Math.toRadians(camera.fov) / 2) / mc.displayHeight;
            camera
                .pan(-dx * blocksPerPixel, dy * blocksPerPixel / Math.max(0.3, Math.sin(Math.toRadians(camera.pitch))));
            client.clampFocus();
        } else if (downButton == 1 && moved) {
            camera.yaw += dx * 0.3F;
            camera.yawGoal = camera.yaw;
            camera.pitch = clamp(camera.pitch + dy * 0.2F, 25, 88);
        }
    }

    private void release(int px, int py, int button) {
        if (pressedWidget != null && button == 0) {
            pressedWidget = null;
            return;
        }
        if (button != downButton) {
            return;
        }
        downButton = -1;
        if (box && moved) {
            selectIn(downX, downY, px, py, isShiftKeyDown());
        } else if (!moved) {
            click(px, py, button, isShiftKeyDown());
        }
        box = false;
    }

    @Override
    protected void keyTyped(char typedChar, int key) {
        RtsCamera camera = camera();
        if (camera == null) {
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            if (client.placing != null) {
                client.stopPlacing();
            } else if (!client.selected.isEmpty() || client.selectedBuilding != 0) {
                client.selected.clear();
                client.selectedBuilding = 0;
                client.changedSelection();
            } else {
                client.leave();
            }
        } else if (key == client.toggleKey.getKeyCode()) {
            client.leave();
        } else if (key == Keyboard.KEY_Q) {
            camera.turn(-1);
        } else if (key == Keyboard.KEY_E) {
            camera.turn(1);
        } else if (key == Keyboard.KEY_R && client.placing != null) {
            client.placing.rotation = (client.placing.rotation + 1) % 4;
        } else if (key == Keyboard.KEY_H) {
            goToTownHall();
        } else if (key == Keyboard.KEY_SPACE) {
            focusSelection();
        } else if (key == mc.gameSettings.keyBindChat.getKeyCode()) {
            client.openChat("");
        } else if (key == mc.gameSettings.keyBindCommand.getKeyCode()) {
            client.openChat("/");
        } else if (key == Keyboard.KEY_F1) {
            mc.gameSettings.hideGUI = !mc.gameSettings.hideGUI;
        }
    }

    private void goToTownHall() {
        RtsState.TeamInfo team = state().myTeam();
        RtsState.BuildingInfo hall = team == null ? null : state().townHallOf(team.id);
        if (hall != null) {
            client.selectBuilding(hall.id);
            client.focusOn(hall.x + hall.sizeX / 2.0, hall.z + hall.sizeZ / 2.0);
        }
    }

    private void focusSelection() {
        double x = 0;
        double z = 0;
        int count = 0;
        for (String id : client.selected) {
            RtsState.UnitInfo unit = state().units.get(id);
            if (unit != null) {
                double[] at = client.positionOf(unit, 1);
                x += at[0];
                z += at[2];
                count++;
            }
        }
        if (count > 0) {
            client.focusOn(x / count, z / count);
        }
    }

    // ---------------------------------------------------------------- picking

    /** The unit, player or mob drawn under a screen point, or null. */
    private Hit entityAt(int px, int py) {
        Hit best = null;
        double bestDistance = Double.MAX_VALUE;
        List<Object[]> candidates = new ArrayList<>();
        for (RtsState.UnitInfo unit : state().units.values()) {
            candidates.add(new Object[] { "unit", unit });
        }
        for (RtsState.PlayerInfo player : state().players.values()) {
            if (!player.commanding && !player.id.equals(state().me)) {
                candidates.add(new Object[] { "player", player });
            }
        }
        for (RtsState.MobInfo mob : state().mobs.values()) {
            candidates.add(new Object[] { "mob", mob });
        }
        for (Object[] candidate : candidates) {
            RtsState.Thing thing = (RtsState.Thing) candidate[1];
            double[] at = client.positionOf(thing, 1);
            double[] feet = project(at[0], at[1], at[2]);
            double[] head = project(at[0], at[1] + at[3], at[2]);
            if (feet == null || head == null) {
                continue;
            }
            double reach = Math.max(PICK_PIXELS, Math.hypot(head[0] - feet[0], head[1] - feet[1]) * 0.35);
            double distance = distanceToSegment(px, py, feet, head);
            if (distance <= reach && distance < bestDistance) {
                bestDistance = distance;
                best = new Hit((String) candidate[0], thing, at);
            }
        }
        return best;
    }

    private static double distanceToSegment(double px, double py, double[] a, double[] b) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double length = dx * dx + dy * dy;
        double t = length == 0 ? 0 : Math.max(0, Math.min(1, ((px - a[0]) * dx + (py - a[1]) * dy) / length));
        return Math.hypot(px - (a[0] + t * dx), py - (a[1] + t * dy));
    }

    /**
     * The ground under a screen point: where the ray from the camera through it first meets a solid block or water,
     * walking the blocks the client has. Past them it meets sea level. As {x, y, z}.
     */
    private double[] groundUnder(int px, int py) {
        RtsCamera camera = camera();
        double[] ray = camera.ray(px, py, mc.displayWidth, mc.displayHeight);
        double ox = ray[0];
        double oy = ray[1];
        double oz = ray[2];
        double dx = ray[3];
        double dy = ray[4];
        double dz = ray[5];
        int x = MathHelper.floor_double(ox);
        int y = MathHelper.floor_double(oy);
        int z = MathHelper.floor_double(oz);
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double tDeltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
        double tDeltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
        double tDeltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
        double tMaxX = dx == 0 ? Double.MAX_VALUE : ((dx > 0 ? x + 1 - ox : ox - x) * tDeltaX);
        double tMaxY = dy == 0 ? Double.MAX_VALUE : ((dy > 0 ? y + 1 - oy : oy - y) * tDeltaY);
        double tMaxZ = dz == 0 ? Double.MAX_VALUE : ((dz > 0 ? z + 1 - oz : oz - z) * tDeltaZ);
        double t = 0;
        double limit = 600;
        while (t < limit) {
            if (y >= 0 && y < 256 && isGround(x, y, z)) {
                return new double[] { ox + dx * t, oy + dy * t, oz + dz * t };
            }
            if (y < 0 && dy < 0) {
                break;
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                t = tMaxX;
                tMaxX += tDeltaX;
                x += stepX;
            } else if (tMaxY < tMaxZ) {
                t = tMaxY;
                tMaxY += tDeltaY;
                y += stepY;
            } else {
                t = tMaxZ;
                tMaxZ += tDeltaZ;
                z += stepZ;
            }
        }
        double sea = state().game == null ? 64 : state().game.seaLevel + 1;
        if (dy >= 0) {
            return null;
        }
        double tSea = (sea - oy) / dy;
        return new double[] { ox + dx * tSea, sea, oz + dz * tSea };
    }

    private boolean isGround(int x, int y, int z) {
        Block block = mc.theWorld.getBlock(x, y, z);
        if (block.getMaterial() == Material.air) {
            return false;
        }
        Material material = block.getMaterial();
        return material.isSolid() || material.isLiquid();
    }

    private RtsState.BuildingInfo buildingAt(int px, int py) {
        double[] ground = groundUnder(px, py);
        if (ground == null) {
            return null;
        }
        // A ray that hits a wall stops just outside the footprint; step a little further in.
        double[] ray = camera().ray(px, py, mc.displayWidth, mc.displayHeight);
        RtsState.BuildingInfo building = state().buildingAt(ground[0], ground[2]);
        return building != null ? building : state().buildingAt(ground[0] + ray[3] * 0.3, ground[2] + ray[5] * 0.3);
    }

    private double[] project(double x, double y, double z) {
        return camera().project(x, y, z, mc.displayWidth, mc.displayHeight);
    }

    // ---------------------------------------------------------------- selecting and ordering

    private boolean isEnemy(Hit hit) {
        if ("mob".equals(hit.kind)) {
            return true;
        }
        String team = hit.thing instanceof RtsState.UnitInfo ? ((RtsState.UnitInfo) hit.thing).team
            : ((RtsState.PlayerInfo) hit.thing).team;
        return state().isEnemyTeam(team);
    }

    private void click(int px, int py, int button, boolean shift) {
        RtsState.TeamInfo team = state().myTeam();
        if (team == null) {
            return;
        }
        if (client.placing != null) {
            if (button == 1) {
                client.stopPlacing();
            } else if (button == 0) {
                place(px, py, shift);
            }
            return;
        }
        Hit hit = entityAt(px, py);
        if (button == 1) {
            if (!client.selected.isEmpty()) {
                order(px, py, hit);
            }
            return;
        }
        if (button != 0) {
            return;
        }
        if (hit != null && hit.thing instanceof RtsState.UnitInfo && state().isMine((RtsState.UnitInfo) hit.thing)) {
            String id = hit.thing.id;
            long now = System.currentTimeMillis();
            if (id.equals(lastClickId) && now - lastClickTime < 400) {
                selectIn(0, 0, mc.displayWidth, mc.displayHeight, false);
            } else if (shift) {
                if (!client.selected.remove(id)) {
                    client.selected.add(id);
                }
            } else {
                client.selected.clear();
                client.selected.add(id);
            }
            lastClickId = id;
            lastClickTime = now;
            client.changedSelection();
            return;
        }
        RtsState.BuildingInfo building = hit == null ? buildingAt(px, py) : null;
        if (building != null && building.team.equals(team.id)) {
            client.selectBuilding(building.id);
        } else if (!shift && (!client.selected.isEmpty() || client.selectedBuilding != 0)) {
            client.selected.clear();
            client.selectedBuilding = 0;
            client.changedSelection();
        }
    }

    private void order(int px, int py, Hit hit) {
        if (hit != null && isEnemy(hit)) {
            client.mark(hit.position[0], hit.position[1], hit.position[2], true);
            JsonObject body = unitsBody(client.selected);
            body.addProperty("target", hit.thing.id);
            client.command("units.attack", body, null);
            return;
        }
        if (hit == null && (orderBuild(px, py) || orderAttackBuilding(px, py))) {
            return;
        }
        double[] ground = groundUnder(px, py);
        if (ground == null) {
            return;
        }
        client.mark(ground[0], ground[1], ground[2], false);
        JsonObject body = unitsBody(client.selected);
        body.addProperty("x", ground[0]);
        body.addProperty("z", ground[2]);
        client.command("units.move", body, null);
    }

    /** Right-clicking an enemy building sends the selected soldiers to attack it. */
    private boolean orderAttackBuilding(int px, int py) {
        RtsState.BuildingInfo building = buildingAt(px, py);
        List<String> fighters = new ArrayList<>();
        for (String id : client.selected) {
            RtsState.UnitInfo unit = state().units.get(id);
            if (unit != null && !unit.isWorker()) {
                fighters.add(id);
            }
        }
        if (building == null || fighters.isEmpty() || !state().isEnemyTeam(building.team)) {
            return false;
        }
        client.mark(building.x + building.sizeX / 2.0, building.y + 1, building.z + building.sizeZ / 2.0, true);
        JsonObject body = unitsBody(fighters);
        body.addProperty("building", building.id);
        client.command("units.attack", body, null);
        return true;
    }

    /** Right-clicking one of the team's building sites, or a farm or quarry, sends the selected workers to it. */
    private boolean orderBuild(int px, int py) {
        RtsState.BuildingInfo building = buildingAt(px, py);
        Set<String> workers = client.selectedWorkers();
        RtsState.TeamInfo team = state().myTeam();
        RtsState.BuildingTypeInfo type = building == null ? null : state().buildingType(building.type);
        if (building == null || team == null
            || !building.team.equals(team.id)
            || workers.isEmpty()
            || (building.complete && (type == null || type.workers == 0))) {
            return false;
        }
        client.mark(building.x + building.sizeX / 2.0, building.y + 1, building.z + building.sizeZ / 2.0, false);
        JsonObject body = unitsBody(workers);
        body.addProperty("building", building.id);
        client.command("units.build", body, null);
        return true;
    }

    private static JsonObject unitsBody(Iterable<String> ids) {
        JsonArray units = new JsonArray();
        for (String id : ids) {
            units.add(new JsonPrimitive(id));
        }
        JsonObject body = new JsonObject();
        body.add("units", units);
        return body;
    }

    /** Selects the viewer's units drawn inside a screen rectangle; adds to the selection if asked. */
    private void selectIn(int x0, int y0, int x1, int y1, boolean add) {
        int left = Math.min(x0, x1);
        int right = Math.max(x0, x1);
        int top = Math.min(y0, y1);
        int bottom = Math.max(y0, y1);
        if (!add) {
            client.selected.clear();
        }
        for (RtsState.UnitInfo unit : state().units.values()) {
            if (!state().isMine(unit)) {
                continue;
            }
            double[] at = client.positionOf(unit, 1);
            double[] p = project(at[0], at[1] + at[3] / 2, at[2]);
            if (p != null && p[0] >= left && p[0] <= right && p[1] >= top && p[1] <= bottom) {
                client.selected.add(unit.id);
            }
        }
        client.changedSelection();
    }

    private void movePlacing(int px, int py) {
        if (overPanel(guiX(px), guiY(py))) {
            return;
        }
        double[] ground = groundUnder(px, py);
        if (ground != null) {
            client.placing.cx = MathHelper.floor_double(ground[0]);
            client.placing.cz = MathHelper.floor_double(ground[2]);
        }
    }

    private void place(int px, int py, boolean keep) {
        movePlacing(px, py);
        RtsClient.Footprint spot = client.footprint();
        if (spot == null) {
            return;
        }
        if (!spot.ok) {
            client.showToast("It does not fit there", true);
            return;
        }
        Set<String> workers = client.selectedWorkers();
        if (workers.isEmpty()) {
            client.showToast("Select a worker to build with", true);
            client.stopPlacing();
            return;
        }
        RtsClient.Placing placing = client.placing;
        if (!keep) {
            client.stopPlacing();
        }
        JsonObject body = unitsBody(workers);
        body.addProperty("building", placing.type);
        body.addProperty("x", placing.cx);
        body.addProperty("z", placing.cz);
        body.addProperty("rotation", placing.rotation);
        RtsState.BuildingTypeInfo type = state().buildingType(placing.type);
        client.command("buildings.place", body, reply -> client.showToast(type.name + " site laid out", false));
    }

    // ---------------------------------------------------------------- tooltips

    /** What a unit is doing, in a few words. */
    private String doingText(RtsState.UnitInfo unit) {
        RtsState.BuildingInfo building = state().buildings.get(unit.building);
        RtsState.BuildingTypeInfo type = building == null ? null : state().buildingType(building.type);
        String where = type == null ? "" : type.name.toLowerCase();
        boolean headed = !Double.isNaN(unit.toX);
        switch (unit.order) {
            case "move":
                return "walking";
            case "attack":
                return "fighting";
            case "attack_building":
                return "attacking " + (building != null ? buildingName(building) : "a building");
            case "build":
                return headed ? "on the way to build " + where : "building " + where;
            case "work":
                if (unit.delivering) {
                    return "taking " + RtsState.amountsText(unit.carrying) + " to store";
                }
                return headed ? "on the way to " + where : "working at " + where;
            default:
                return unit.isWorker() ? "idle" : "on guard";
        }
    }

    private String unitName(RtsState.UnitInfo unit) {
        RtsState.TeamInfo team = state().teamsById.get(unit.team);
        return (team != null ? team.name + " " : "") + (unit.isWorker() ? "worker " : "soldier ") + unit.number;
    }

    private String buildingName(RtsState.BuildingInfo building) {
        RtsState.TeamInfo team = state().teamsById.get(building.team);
        RtsState.BuildingTypeInfo type = state().buildingType(building.type);
        return (team != null ? team.name + " " : "") + (type != null ? type.name.toLowerCase() : building.type);
    }

    private static String healthText(double health, double max) {
        return (int) Math.ceil(health) + "/" + (int) Math.ceil(max);
    }

    /** What the mouse is over, as {icon item, text, "attack" or "act" or null}, or null for nothing. */
    private Object[] hint(int px, int py) {
        if (state().game == null || client.placing != null) {
            return null;
        }
        RtsState.TeamInfo team = state().myTeam();
        int fighters = 0;
        int workers = 0;
        for (String id : client.selected) {
            RtsState.UnitInfo unit = state().units.get(id);
            if (unit != null) {
                if (unit.isWorker()) {
                    workers++;
                } else {
                    fighters++;
                }
            }
        }
        Hit hit = entityAt(px, py);
        if (hit != null) {
            if (fighters > 0 && isEnemy(hit)) {
                String name = hit.thing instanceof RtsState.UnitInfo ? unitName((RtsState.UnitInfo) hit.thing)
                    : hit.thing instanceof RtsState.PlayerInfo ? ((RtsState.PlayerInfo) hit.thing).name
                        : ((RtsState.MobInfo) hit.thing).name + " · "
                            + healthText(hit.thing.health, hit.thing.maxHealth);
                return new Object[] { new ItemStack(Items.iron_sword), "Attack " + name, "attack" };
            }
            if (hit.thing instanceof RtsState.MobInfo) {
                RtsState.MobInfo mob = (RtsState.MobInfo) hit.thing;
                return new Object[] { new ItemStack(mob.hostile ? Items.rotten_flesh : Items.leather),
                    mob.name + (mob.hostile ? "" : " (passive)") + " · " + healthText(mob.health, mob.maxHealth),
                    null };
            }
            if (hit.thing instanceof RtsState.PlayerInfo) {
                RtsState.PlayerInfo player = (RtsState.PlayerInfo) hit.thing;
                RtsState.TeamInfo playerTeam = state().teamsById.get(player.team);
                return new Object[] { new ItemStack(Items.skull, 1, 3),
                    player.name + (playerTeam != null ? " (" + playerTeam.name + ")" : ""), null };
            }
            RtsState.UnitInfo unit = (RtsState.UnitInfo) hit.thing;
            String text = unitName(unit) + " · " + doingText(unit);
            if (!unit.carrying.isEmpty() && !unit.delivering) {
                text += " · carrying " + RtsState.amountsText(unit.carrying);
            }
            return new Object[] { new ItemStack(unit.isWorker() ? Items.iron_axe : Items.iron_sword),
                text + " · " + healthText(unit.health, unit.maxHealth), null };
        }
        RtsState.BuildingInfo b = buildingAt(px, py);
        if (b == null) {
            return null;
        }
        RtsState.BuildingTypeInfo type = state().buildingType(b.type);
        if (type == null) {
            return null;
        }
        boolean mine = team != null && b.team.equals(team.id);
        if (fighters > 0 && state().isEnemyTeam(b.team)) {
            return new Object[] { new ItemStack(Items.iron_sword),
                "Attack " + buildingName(b) + " · " + b.health + "/" + b.maxHealth, "attack" };
        }
        if (mine && workers > 0 && !b.complete) {
            return new Object[] { new ItemStack(Items.iron_axe),
                "Build " + type.name.toLowerCase() + " · " + (int) Math.floor(100 * b.built) + "% built", "act" };
        }
        if (mine && workers > 0 && type.workers > 0) {
            return new Object[] { buildingIcon(b.type),
                "Work at " + type.name.toLowerCase() + " (" + b.workers + " of " + type.workers + " workers)", "act" };
        }
        String text = buildingName(b);
        if (!b.complete) {
            text += " · building site, " + (int) Math.floor(100 * b.built) + "% built";
        } else if (!b.queue.isEmpty()) {
            text += " · training " + b.queue.size();
        } else if (type.workers > 0) {
            text += " · " + b.workers + " working";
        }
        text += " · " + b.health + "/" + b.maxHealth;
        return new Object[] { b.complete ? buildingIcon(b.type) : new ItemStack(Blocks.fence), text, null };
    }

    private static ItemStack buildingIcon(String type) {
        switch (type) {
            case "town_hall":
                return new ItemStack(Blocks.bookshelf);
            case "farm":
                return new ItemStack(Items.wheat);
            case "barracks":
                return new ItemStack(Items.iron_sword);
            case "storehouse":
                return new ItemStack(Blocks.chest);
            case "quarry":
                return new ItemStack(Items.iron_pickaxe);
            default:
                return new ItemStack(Item.getItemFromBlock(Blocks.planks));
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void drawScreen(int unusedX, int unusedY, float partialTicks) {
        if (camera() == null) {
            return;
        }
        if (mouseX < 0) {
            mouseX = Mouse.getX();
            mouseY = mc.displayHeight - Mouse.getY() - 1;
        }
        widgets.clear();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        drawLabels(partialTicks);
        if (box && moved && downButton == 0) {
            int x0 = guiX(Math.min(downX, mouseX));
            int y0 = guiY(Math.min(downY, mouseY));
            int x1 = guiX(Math.max(downX, mouseX));
            int y1 = guiY(Math.max(downY, mouseY));
            drawRect(x0, y0, x1, y1, 0x2073FF80);
            drawOutline(x0, y0, x1, y1, 0xE073FF80);
        }
        if (mc.gameSettings.hideGUI) {
            return;
        }
        drawTeamPanel();
        drawBar();
        drawHelp();
        for (Widget widget : widgets) {
            drawWidget(widget);
        }
        String toast = client.getToast();
        if (toast != null) {
            int w = fontRendererObj.getStringWidth(toast) + 12;
            int x = (width - w) / 2;
            int y = barTop - 24;
            drawRect(x, y, x + w, y + 14, client.isToastBad() ? 0xD0801818 : 0xD0185028);
            fontRendererObj.drawStringWithShadow(toast, x + 6, y + 3, 0xFFFFFF);
        }
        drawTips();
    }

    private void drawTips() {
        int gx = guiX(mouseX);
        int gy = guiY(mouseY);
        for (Widget widget : widgets) {
            if (widget.tip != null && widget.contains(gx, gy)) {
                drawTip(null, widget.tip, gx, gy, 0xFFFFFF);
                return;
            }
        }
        if (overPanel(gx, gy) || downButton >= 0) {
            return;
        }
        Object[] hint = hint(mouseX, mouseY);
        if (hint != null) {
            int color = "attack".equals(hint[2]) ? 0xFF8A80 : "act".equals(hint[2]) ? 0xA8FFB0 : 0xFFFFFF;
            drawTip((ItemStack) hint[0], (String) hint[1], gx, gy, color);
        }
    }

    private void drawTip(ItemStack icon, String text, int gx, int gy, int color) {
        int iconWidth = icon == null ? 0 : 18;
        int w = fontRendererObj.getStringWidth(text) + 8 + iconWidth;
        int h = icon == null ? 14 : 20;
        int x = Math.min(gx + 10, width - w - 2);
        int y = gy + 14 + h > height ? gy - h - 4 : gy + 14;
        drawRect(x, y, x + w, y + h, 0xE0000000);
        if (icon != null) {
            drawItem(icon, x + 3, y + 2);
        }
        fontRendererObj.drawStringWithShadow(text, x + 4 + iconWidth, y + (h - 8) / 2, color);
    }

    private void drawItem(ItemStack stack, int x, int y) {
        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    /** Names and bars over buildings, and health bars over hurt or selected units and hurt mobs. */
    private void drawLabels(float partialTicks) {
        RtsState state = state();
        for (RtsState.BuildingInfo b : state.buildings.values()) {
            RtsState.BuildingTypeInfo type = state.buildingType(b.type);
            RtsState.TeamInfo team = state.teamsById.get(b.team);
            boolean hurt = b.complete && b.health < b.maxHealth;
            if (type == null || team == null
                || (!"town_hall".equals(b.type) && b.id != client.selectedBuilding
                    && b.complete
                    && b.queue.isEmpty()
                    && !hurt)) {
                continue;
            }
            int top = b.y + RtsState.tallest(RtsState.turnedShape(type, b.rotation)) + 1;
            double[] p = project(b.x + b.sizeX / 2.0, top, b.z + b.sizeZ / 2.0);
            if (p == null) {
                continue;
            }
            int x = guiX(p[0]);
            int y = guiY(p[1]);
            String text = team.name + " " + type.name.toLowerCase() + (b.complete ? "" : " (site)");
            fontRendererObj
                .drawStringWithShadow(text, x - fontRendererObj.getStringWidth(text) / 2, y - 12, team.color);
            int row = 0;
            if (!b.complete) {
                bar(x, y + row++ * 5, 40, b.built, AMBER);
            } else if (!b.queue.isEmpty()) {
                bar(x, y + row++ * 5, 40, b.progress, GREEN);
            }
            if (hurt) {
                double health = b.health / (double) b.maxHealth;
                bar(x, y + row * 5, 40, health, health > 0.5 ? GREEN : health > 0.25 ? AMBER : RED);
            }
        }
        for (RtsState.UnitInfo unit : state.units.values()) {
            boolean selected = client.selected.contains(unit.id);
            if (!selected && unit.health >= unit.maxHealth) {
                continue;
            }
            double[] at = client.positionOf(unit, partialTicks);
            double[] p = project(at[0], at[1] + at[3] + 0.4, at[2]);
            if (p != null) {
                double health = unit.health / Math.max(1, unit.maxHealth);
                bar(guiX(p[0]), guiY(p[1]), 16, health, health > 0.5 ? GREEN : health > 0.25 ? AMBER : RED);
            }
        }
        for (RtsState.MobInfo mob : state.mobs.values()) {
            if (mob.health >= mob.maxHealth) {
                continue;
            }
            double[] at = client.positionOf(mob, partialTicks);
            double[] p = project(at[0], at[1] + at[3] + 0.4, at[2]);
            if (p != null) {
                bar(guiX(p[0]), guiY(p[1]), 14, mob.health / Math.max(1, mob.maxHealth), RED);
            }
        }
        for (RtsState.PlayerInfo player : state.players.values()) {
            if (player.commanding || player.id.equals(state.me)) {
                continue;
            }
            double[] at = client.positionOf(player, partialTicks);
            double[] p = project(at[0], at[1] + 2.2, at[2]);
            if (p != null) {
                fontRendererObj.drawStringWithShadow(
                    player.name,
                    guiX(p[0]) - fontRendererObj.getStringWidth(player.name) / 2,
                    guiY(p[1]) - 8,
                    0xFFFFFF);
            }
        }
    }

    private void bar(int cx, int y, int w, double fraction, int color) {
        int x = cx - w / 2;
        drawRect(x - 1, y - 1, x + w + 1, y + 3, 0xC0000000);
        drawRect(x, y, x + (int) Math.round(w * Math.max(0, Math.min(1, fraction))), y + 2, color);
    }

    private void drawTeamPanel() {
        RtsState state = state();
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        RtsState.TeamInfo team = state.myTeam();
        if (state.game == null) {
            lines.add("No game is running");
            colors.add(0xFFFFFF);
        } else if (team == null) {
            lines.add("Watching: join a team to command");
            colors.add(0xFFFFFF);
        } else {
            lines.add("Team " + team.name);
            colors.add(team.color);
            StringBuilder resources = new StringBuilder();
            for (String resource : RtsState.RESOURCES) {
                Long amount = team.resources.get(resource);
                if (resources.length() > 0) {
                    resources.append("   ");
                }
                resources.append(Character.toUpperCase(resource.charAt(0)))
                    .append(resource.substring(1))
                    .append(' ')
                    .append(amount == null ? 0 : amount);
            }
            lines.add(resources.toString());
            colors.add(0xFFE9A8);
            lines.add("Units " + team.units + " of " + state.game.unitsPerTeam);
            colors.add(0xC8C8C8);
        }
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, fontRendererObj.getStringWidth(line));
        }
        drawRect(4, 4, 4 + w + 8, 4 + lines.size() * 11 + 5, PANEL);
        for (int i = 0; i < lines.size(); i++) {
            fontRendererObj.drawStringWithShadow(lines.get(i), 8, 8 + i * 11, colors.get(i));
        }
    }

    private void drawHelp() {
        String help = "V leave · WASD move · Q/E turn · wheel zoom · H town hall";
        int w = fontRendererObj.getStringWidth(help);
        drawRect(width - w - 12, 4, width - 4, 17, PANEL);
        fontRendererObj.drawStringWithShadow(help, width - w - 8, 7, 0xB0B0B0);
    }

    /**
     * The bar along the bottom, as on the web map. Its upper row changes with what is going on: placing a building,
     * a building selected, workers selected, or nothing in particular; the lower row tells what is selected.
     */
    private void drawBar() {
        RtsState state = state();
        RtsState.TeamInfo team = state.myTeam();
        if (team == null || state.game == null) {
            List<Widget> row = new ArrayList<>();
            row.add(button("Leave (V)", "Back to playing in person", shift -> client.leave(), true));
            barTop = layoutRows(row, height - 22);
            return;
        }
        List<Widget> actions = new ArrayList<>();
        RtsState.BuildingInfo building = state.buildings.get(client.selectedBuilding);
        if (client.placing != null) {
            RtsState.BuildingTypeInfo type = state.buildingType(client.placing.type);
            actions.add(label("Place " + type.name.toLowerCase() + ": click to place, R to rotate", 0xC8C8C8));
            actions.add(
                button(
                    "Rotate",
                    "Turn it a quarter round (R)",
                    shift -> { client.placing.rotation = (client.placing.rotation + 1) % 4; },
                    true));
            actions.add(button("Cancel", "Esc or right-click", shift -> client.stopPlacing(), true));
        } else if (building != null) {
            buildingActions(actions, team, building);
        } else if (!client.selectedWorkers()
            .isEmpty()) {
                for (Map.Entry<String, RtsState.BuildingTypeInfo> entry : state.game.buildingTypes.entrySet()) {
                    RtsState.BuildingTypeInfo type = entry.getValue();
                    if (!type.buildable) {
                        continue;
                    }
                    String id = entry.getKey();
                    actions.add(
                        button(
                            "Build " + type.name.toLowerCase() + " (" + RtsState.costText(type.cost) + ")",
                            "Then click where it goes",
                            shift -> client.startPlacing(id),
                            RtsState.canAfford(team, type.cost)));
                }
            } else {
                RtsState.BuildingInfo hall = state.townHallOf(team.id);
                if (hall != null) {
                    actions.add(
                        button(
                            "Town hall",
                            "Select your town hall to train workers (H)",
                            shift -> goToTownHall(),
                            true));
                }
            }

        int count = client.selected.size();
        int workers = client.selectedWorkers()
            .size();
        String info = team.units > 0 ? "None selected" : "No units yet";
        if (count > 0) {
            info = count + " selected"
                + (workers == count ? (count == 1 ? ", a worker" : ", all workers")
                    : workers > 0 ? ", " + workers + " workers" : "");
        }
        List<Widget> controls = new ArrayList<>();
        controls.add(label(info, 0xFFFFFF));
        controls.add(button("All", "Select all your units", shift -> {
            client.selected.clear();
            for (RtsState.UnitInfo unit : state.units.values()) {
                if (state.isMine(unit)) {
                    client.selected.add(unit.id);
                }
            }
            client.changedSelection();
        }, true));
        controls.add(button("Stop", "Stop and guard where they stand", shift -> {
            if (!client.selected.isEmpty()) {
                client.command("units.stop", unitsBody(client.selected), null);
            }
        }, count > 0));
        controls.add(button("Clear", "Deselect (Esc)", shift -> {
            client.selected.clear();
            client.selectedBuilding = 0;
            client.changedSelection();
        }, count > 0 || building != null));
        controls.add(button("Leave (V)", "Back to playing in person", shift -> client.leave(), true));

        barTop = layoutRows(controls, height - 22);
        if (!actions.isEmpty()) {
            barTop = layoutRows(actions, barTop - 18);
        }
    }

    private void buildingActions(List<Widget> actions, RtsState.TeamInfo team, RtsState.BuildingInfo building) {
        RtsState state = state();
        RtsState.BuildingTypeInfo type = state.buildingType(building.type);
        if (type == null) {
            return;
        }
        if (!building.complete) {
            int coming = -building.builders;
            for (RtsState.UnitInfo unit : state.units.values()) {
                if (unit.building == building.id && "build".equals(unit.order)) {
                    coming++;
                }
            }
            actions.add(label(type.name + " site", 0xFFFFFF));
            actions.add(progress(building.built, AMBER));
            String note = (int) Math.floor(100 * building.built) + "% built";
            if (building.builders > 0) {
                note += ", " + building.builders + (building.builders == 1 ? " worker building" : " workers building");
            }
            if (coming > 0) {
                note += ", " + coming + " on the way";
            }
            if (building.builders == 0 && coming <= 0) {
                note += ": select a worker and right-click it to build";
            }
            actions.add(label(note, 0xC8C8C8));
            return;
        }
        actions.add(label(type.name, 0xFFFFFF));
        actions.add(label("Health " + building.health + "/" + building.maxHealth, 0xC8C8C8));
        if (type.workers > 0) {
            actions.add(label(building.workers + " of " + type.workers + " workers", 0xC8C8C8));
        }
        if (type.trains == null) {
            return;
        }
        RtsState.UnitKindInfo kind = state.unitKind(type.trains);
        if (kind == null) {
            return;
        }
        boolean full = state.teamUnitCount(team) >= state.game.unitsPerTeam;
        // Each click queues one more (shift-click five), up to ten waiting; training goes on one at a time.
        actions.add(
            button(
                "Train " + kind.name.toLowerCase() + " (" + RtsState.costText(kind.cost) + ")",
                full ? "Your team has the most units it can have" : "Shift-click to train 5",
                shift -> {
                    JsonObject body = new JsonObject();
                    body.addProperty("building", building.id);
                    body.addProperty("count", shift ? 5 : 1);
                    client.command("buildings.train", body, reply -> {
                        int queued = reply.has("count") ? reply.get("count")
                            .getAsInt() : 1;
                        client.showToast(
                            queued == 1 ? "Training a " + kind.name.toLowerCase()
                                : "Training " + queued + " " + kind.name.toLowerCase() + "s",
                            false);
                    });
                },
                !full && RtsState.canAfford(team, kind.cost) && building.queue.size() < 10));
        if (!building.queue.isEmpty()) {
            actions.add(progress(building.progress, GREEN));
            actions.add(label(building.queue.size() + " in training", 0xC8C8C8));
        }
    }

    private Widget button(String text, String tip, Consumer<Boolean> action, boolean enabled) {
        Widget widget = new Widget();
        widget.text = text;
        widget.tip = tip;
        widget.action = action;
        widget.enabled = enabled;
        widget.w = fontRendererObj.getStringWidth(text) + 10;
        widget.h = 14;
        return widget;
    }

    private Widget label(String text, int color) {
        Widget widget = new Widget();
        widget.text = text;
        widget.color = color;
        widget.w = fontRendererObj.getStringWidth(text) + 4;
        widget.h = 14;
        return widget;
    }

    private Widget progress(double fill, int color) {
        Widget widget = new Widget();
        widget.fill = fill;
        widget.fillColor = color;
        widget.w = 50;
        widget.h = 14;
        return widget;
    }

    /**
     * Lays out widgets from the left in rows on panels, wrapping where the window is too narrow, with the last row at
     * y; adds them to the widgets and returns the y of the first row. Rows grow to the right, so a button does not
     * move when a note after it changes.
     */
    private int layoutRows(List<Widget> items, int y) {
        int gap = 4;
        int left = 8;
        List<List<Widget>> rows = new ArrayList<>();
        List<Widget> row = new ArrayList<>();
        int x = left;
        for (Widget widget : items) {
            if (!row.isEmpty() && x + widget.w > width - left) {
                rows.add(row);
                row = new ArrayList<>();
                x = left;
            }
            row.add(widget);
            x += widget.w + gap;
        }
        rows.add(row);
        int top = y - (rows.size() - 1) * 18;
        for (int i = 0; i < rows.size(); i++) {
            int rowY = top + i * 18;
            x = left;
            for (Widget widget : rows.get(i)) {
                widget.x = x;
                widget.y = rowY;
                x += widget.w + gap;
                widgets.add(widget);
            }
            drawRect(left - 4, rowY - 3, x - gap + 4, rowY + 17, PANEL);
        }
        return top;
    }

    private void drawWidget(Widget widget) {
        int gx = guiX(mouseX);
        int gy = guiY(mouseY);
        if (!Double.isNaN(widget.fill)) {
            int y = widget.y + 5;
            drawRect(widget.x, y, widget.x + widget.w, y + 4, 0xFF000000);
            drawRect(
                widget.x,
                y,
                widget.x + (int) Math.round(widget.w * Math.max(0, Math.min(1, widget.fill))),
                y + 4,
                widget.fillColor);
            return;
        }
        if (widget.action == null) {
            fontRendererObj.drawStringWithShadow(widget.text, widget.x + 2, widget.y + 3, widget.color);
            return;
        }
        boolean hover = widget.enabled && widget.contains(gx, gy);
        int background = !widget.enabled ? 0xC0303030 : hover ? 0xE0506070 : 0xE0384450;
        drawRect(widget.x, widget.y, widget.x + widget.w, widget.y + widget.h, background);
        drawOutline(widget.x, widget.y, widget.x + widget.w, widget.y + widget.h, hover ? 0xFFA0B4C8 : 0xFF5A6A78);
        fontRendererObj
            .drawStringWithShadow(widget.text, widget.x + 5, widget.y + 3, widget.enabled ? 0xFFFFFF : 0x808080);
    }

    private void drawOutline(int x0, int y0, int x1, int y1, int color) {
        drawRect(x0, y0, x1, y0 + 1, color);
        drawRect(x0, y1 - 1, x1, y1, color);
        drawRect(x0, y0, x0 + 1, y1, color);
        drawRect(x1 - 1, y0, x1, y1, color);
    }

    /** Whether a point is on a panel rather than over the world. */
    private boolean overPanel(int gx, int gy) {
        if (gy >= barTop - 3 && !widgets.isEmpty()) {
            for (Widget widget : widgets) {
                if (gy >= widget.y - 3 && gy < widget.y + 17 && gx >= 0 && gx < width) {
                    int left = Integer.MAX_VALUE;
                    int right = Integer.MIN_VALUE;
                    for (Widget other : widgets) {
                        if (other.y == widget.y) {
                            left = Math.min(left, other.x);
                            right = Math.max(right, other.x + other.w);
                        }
                    }
                    if (gx >= left - 4 && gx < right + 4) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int guiX(double px) {
        return (int) (px * width / mc.displayWidth);
    }

    private int guiY(double py) {
        return (int) (py * height / mc.displayHeight);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void onGuiClosed() {
        downButton = -1;
    }
}
