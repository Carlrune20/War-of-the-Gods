package com.warofthegods.client.rts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.warofthegods.unit.Trees;

/**
 * The screen of the RTS view: it frees the mouse over the world the camera shows and draws the panels on top, like
 * the web map. Click one of your units to select it (shift-click adds, double-click selects all of them in view),
 * drag a box around several, and right-click the ground to send them there, an enemy or a mob to attack it, or one of
 * your building sites or farms, a gold mine or a tree to send workers to it. Click a building to see what it trains.
 * WASD or the
 * arrow keys (or the mouse at the window's edge) move the camera, Q and E turn it a quarter round, the wheel zooms,
 * the middle button dragged tilts and turns it, or with shift drags it along. The right button only ever gives orders,
 * however the mouse moves while it is down. V or Esc leaves.
 *
 * <p>
 * The world and the mouse are in window pixels; the panels in the scaled units Minecraft draws its screens in.
 */
public final class GuiRts extends GuiScreen {

    private static final int PANEL = 0xC0101418;
    /** The size of a square icon button. */
    private static final int SQUARE = 20;
    private static final int GREEN = 0xFF5FDC6A;
    private static final int AMBER = 0xFFF0B43C;
    private static final int RED = 0xFFE8493F;
    /** Pixels around a unit, player or mob as drawn that still count as pointing at it. */
    private static final double PICK_SLACK = 3;
    private static final double PICK_MIN = 4;
    /** The building, unit and resource icons, one 16 pixel square each, in the order of {@link #ICON_NAMES}. */
    private static final ResourceLocation ICONS = new ResourceLocation("warofthegods", "textures/gui/icons.png");
    private static final List<String> ICON_NAMES = Arrays.asList(
        "town_hall",
        "house",
        "barracks",
        "storehouse",
        // No longer a building, but still a picture in icons.png.
        "quarry",
        "farm",
        "armory",
        "tower",
        "worker",
        "soldier",
        "archer",
        "tree",
        "upgrade",
        "level",
        "food",
        "wood",
        "gold",
        "sword",
        "axe",
        "pickaxe",
        "hoe",
        "wall",
        "gate",
        "white_knights_fortress",
        "black_knights_fortress",
        "white_knight",
        "black_knight",
        "tradigee",
        "sagittare",
        "archery_range",
        "stables",
        "cavalry",
        "horse",
        "favor",
        "chaos_temple",
        "monastery",
        "wizards_tower",
        "stone_circle",
        "chaos_priest",
        "monk",
        "dark_wizard",
        "zamorak_mage",
        "blue_wizard",
        "lesser_demon",
        "centaur");

    /** Icons drawn as Minecraft items in the view; icons.png has the same pictures for the web map. */
    private static final Map<String, ItemStack> VANILLA_ICONS = new HashMap<>();

    static {
        VANILLA_ICONS.put("food", new ItemStack(Items.beef));
        VANILLA_ICONS.put("wood", new ItemStack(Blocks.log));
        VANILLA_ICONS.put("gold", new ItemStack(Items.gold_ingot));
        VANILLA_ICONS.put("favor", new ItemStack(Items.nether_star));
        VANILLA_ICONS.put("sword", new ItemStack(Items.iron_sword));
        VANILLA_ICONS.put("axe", new ItemStack(Items.iron_axe));
        VANILLA_ICONS.put("pickaxe", new ItemStack(Items.iron_pickaxe));
        VANILLA_ICONS.put("hoe", new ItemStack(Items.iron_hoe));
        VANILLA_ICONS.put("bridge", new ItemStack(Blocks.planks));
    }

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
    /** Whether a right drag draws a box of enemies to attack, as it does with warriors or archers selected. */
    private boolean attackBox;
    private Widget pressedWidget;
    private long lastClickTime;
    private String lastClickId;
    /** Number of the idle worker the idle button last went to. */
    private int lastIdle;
    private int mouseX = -1;
    private int mouseY = -1;
    /** The y of the bar's top row, for what goes just above it. */
    private int barTop;
    /** Whether the left button went down on the minimap, which then pans the camera while held. */
    private boolean minimapDrag;
    /** How far between ticks the frame being drawn is, so picking matches what is on screen. */
    private float partialTicks = 1;

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
        /** An icon drawn before the text, or null. */
        String icon;
        /** For a progress bar, how full it is; NaN otherwise. */
        double fill = Double.NaN;
        int fillColor = GREEN;
        /** For a square button, the icon from {@link ActionIcons} it shows instead of text, else null. */
        String square;
        /** For a square button, a bar along its bottom showing how far along it is, or NaN. */
        double bar = Double.NaN;
        /** For a square button, a number in its corner, such as how many are waiting to train, or null. */
        String badge;
        /** For a square button, whether what it turns on is on, such as a prayer that is up. */
        boolean lit;
        /** The top and bottom of the panel row it is on, for telling whether the mouse is over the panel. */
        int rowTop;
        int rowBottom;

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
        double[] onMap = minimapAt(gx, gy);
        if (onMap != null && downButton < 0) {
            if (button == 0) {
                minimapDrag = true;
                client.focusOn(onMap[0], onMap[1]);
            } else if (button == 1 && !client.selected.isEmpty() && state().myTeam() != null) {
                double y = mc.theWorld.getHeightValue((int) Math.floor(onMap[0]), (int) Math.floor(onMap[1]));
                client.mark(onMap[0], y, onMap[1], false);
                JsonObject body = unitsBody(client.selected);
                body.addProperty("x", onMap[0]);
                body.addProperty("z", onMap[1]);
                body.addProperty("formation", client.formation);
                body.addProperty("queue", isShiftKeyDown());
                client.command("units.move", body, null);
            }
            return;
        }
        if (downButton >= 0 || overPanel(gx, gy)) {
            return;
        }
        downButton = button;
        downX = lastX = px;
        downY = lastY = py;
        moved = false;
        box = button == 0 && client.placing == null && !client.lighting && state().myTeam() != null;
        attackBox = button == 1 && client.placing == null
            && !client.lighting
            && !client.selectedFighters()
                .isEmpty();
    }

    private void drag(int px, int py) {
        if (client.placing != null) {
            movePlacing(px, py);
        }
        if (minimapDrag) {
            double[] onMap = minimapAt(guiX(px), guiY(py));
            if (onMap != null) {
                client.focusOn(onMap[0], onMap[1]);
            }
            return;
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
        if (downButton == 2 && isShiftKeyDown()) {
            double blocksPerPixel = 2 * camera.distance * Math.tan(Math.toRadians(camera.fov) / 2) / mc.displayHeight;
            camera
                .pan(-dx * blocksPerPixel, dy * blocksPerPixel / Math.max(0.3, Math.sin(Math.toRadians(camera.pitch))));
            client.clampFocus();
        } else if (downButton == 2 && moved) {
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
        if (minimapDrag && button == 0) {
            minimapDrag = false;
            return;
        }
        if (button != downButton) {
            return;
        }
        downButton = -1;
        if (box && moved) {
            selectIn(downX, downY, px, py, isShiftKeyDown());
        } else if (attackBox && moved && attackIn(downX, downY, px, py, isShiftKeyDown())) {
            // Done: the fighters go at the enemies in the box.
        } else if (!moved || button == 1) {
            // A right click orders the units even if the mouse slipped while it was down.
            click(px, py, button, isShiftKeyDown());
        }
        box = false;
        attackBox = false;
    }

    /**
     * Sends the selected warriors and archers at the enemies drawn inside a screen rectangle, shared out among them,
     * after the enemies they were already ordered at with add. With no enemy units or players in it, at the hostile
     * mobs in it. Returns whether there was anything to attack.
     */
    private boolean attackIn(int x0, int y0, int x1, int y1, boolean add) {
        int left = Math.min(x0, x1);
        int right = Math.max(x0, x1);
        int top = Math.min(y0, y1);
        int bottom = Math.max(y0, y1);
        List<RtsState.Thing> enemies = new ArrayList<>();
        List<RtsState.Thing> mobs = new ArrayList<>();
        List<RtsState.Thing> things = new ArrayList<>(state().units.values());
        things.addAll(state().players.values());
        things.addAll(state().mobs.values());
        for (RtsState.Thing thing : things) {
            boolean enemy = thing instanceof RtsState.UnitInfo ? state().isEnemyTeam(((RtsState.UnitInfo) thing).team)
                : thing instanceof RtsState.PlayerInfo && state().isEnemyTeam(((RtsState.PlayerInfo) thing).team);
            boolean mob = thing instanceof RtsState.MobInfo && ((RtsState.MobInfo) thing).hostile
                && !((RtsState.MobInfo) thing).carcass;
            if (!enemy && !mob) {
                continue;
            }
            double[] at = client.positionOf(thing, 1);
            if (!client.fog.isVisible(at[0], at[2])) {
                continue;
            }
            double[] p = project(at[0], at[1] + at[3] / 2, at[2]);
            if (p != null && p[0] >= left && p[0] <= right && p[1] >= top && p[1] <= bottom) {
                (enemy ? enemies : mobs).add(thing);
            }
        }
        List<RtsState.Thing> targets = enemies.isEmpty() ? mobs : enemies;
        if (targets.isEmpty()) {
            return false;
        }
        JsonObject body = unitsBody(client.selectedFighters());
        JsonArray ids = new JsonArray();
        double x = 0;
        double y = 0;
        double z = 0;
        for (RtsState.Thing target : targets) {
            ids.add(new JsonPrimitive(target.id));
            double[] at = client.positionOf(target, 1);
            x += at[0] / targets.size();
            y += at[1] / targets.size();
            z += at[2] / targets.size();
        }
        body.add("targets", ids);
        body.addProperty("queue", add);
        client.mark(x, y, z, true);
        int count = targets.size();
        client.command(
            "units.attack",
            body,
            reply -> client.showToast("Attacking " + count + (count == 1 ? " enemy" : " enemies"), false));
        return true;
    }

    /** Starts an attack move: the next click sends the selected warriors and archers there. */
    private void startAttackMove() {
        client.stopPlacing();
        client.stopLighting();
        client.attackMoving = true;
        client.showToast("Attack move: click where to go", false);
    }

    @Override
    protected void keyTyped(char typedChar, int key) {
        RtsCamera camera = camera();
        if (camera == null) {
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            if (client.attackMoving) {
                client.attackMoving = false;
            } else if (client.placing != null) {
                client.stopPlacing();
            } else if (client.lighting) {
                client.stopLighting();
            } else if (!client.selected.isEmpty() || client.selectedBuilding != 0) {
                client.selected.clear();
                client.selectBuildings(new ArrayList<>(), 0);
                client.changedSelection();
            } else {
                client.leave();
            }
        } else if (key >= Keyboard.KEY_1 && key <= Keyboard.KEY_0 && state().myTeam() != null) {
            // Ctrl and a number makes the selection that control group, shift and the number adds to it, and the
            // number alone selects it, or goes to it when it is already selected.
            int number = key == Keyboard.KEY_0 ? 0 : key - Keyboard.KEY_1 + 1;
            if (isCtrlKeyDown()) {
                setGroup(number, false);
            } else if (isShiftKeyDown()) {
                setGroup(number, true);
            } else {
                recallGroup(number);
            }
        } else if (key == client.toggleKey.getKeyCode()) {
            client.leave();
        } else if (key == client.teamsKey.getKeyCode()) {
            mc.displayGuiScreen(client.getLobby() != null ? new GuiLobby(client) : new GuiTeams(client));
        } else if (key == Keyboard.KEY_SPACE && !client.selectedFighters()
            .isEmpty()) {
                startAttackMove();
            } else if (key == Keyboard.KEY_Q) {
                camera.turn(-1);
            } else if (key == Keyboard.KEY_E) {
                camera.turn(1);
            } else if (key == Keyboard.KEY_R && client.placing != null) {
                client.placing.rotation = (client.placing.rotation + 1) % 4;
            } else if (key == Keyboard.KEY_H) {
                goToTownHall();
            } else if (key == Keyboard.KEY_DELETE) {
                deleteSelection();
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

    /** Kills the selected units of your own and destroys your selected buildings, as Delete does. */
    private void deleteSelection() {
        RtsState state = state();
        List<String> units = new ArrayList<>();
        for (String id : client.selected) {
            RtsState.UnitInfo unit = state.units.get(id);
            if (unit != null && state.isMine(unit)) {
                units.add(id);
            }
        }
        RtsState.TeamInfo team = state.myTeam();
        List<RtsState.BuildingInfo> buildings = new ArrayList<>();
        for (RtsState.BuildingInfo building : client.selectedBuildingList()) {
            if (team != null && building.team.equals(team.id)) {
                buildings.add(building);
            }
        }
        if (!units.isEmpty()) {
            int count = units.size();
            client.command(
                "units.kill",
                unitsBody(units),
                reply -> client.showToast(count == 1 ? "Unit killed" : count + " units killed", false));
            client.selected.removeAll(units);
            client.changedSelection();
        }
        if (!buildings.isEmpty()) {
            int count = buildings.size();
            client.command(
                "buildings.destroy",
                buildingsBody(buildings),
                reply -> client.showToast(count == 1 ? "Building destroyed" : count + " buildings destroyed", false));
            client.selectBuildings(new ArrayList<>(), 0);
            client.changedSelection();
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
            // A commanding player, the viewer too, is where their body stands, so right-clicking your own body
            // has the selected units follow you.
            candidates.add(new Object[] { "player", player });
        }
        for (RtsState.MobInfo mob : state().mobs.values()) {
            candidates.add(new Object[] { "mob", mob });
        }
        double[] right = camera().right();
        for (Object[] candidate : candidates) {
            RtsState.Thing thing = (RtsState.Thing) candidate[1];
            double[] at = client.positionOf(thing, partialTicks);
            // What moves in the fog is not drawn, so cannot be picked.
            if (!client.fog.isVisible(at[0], at[2])) {
                continue;
            }
            double[] feet = project(at[0], at[1], at[2]);
            double[] head = project(at[0], at[1] + at[3], at[2]);
            // Half the width it is drawn at, measured across the screen.
            double halfWidth = thing instanceof RtsState.MobInfo ? Math.max(0.3, ((RtsState.MobInfo) thing).width / 2)
                : 0.3;
            double[] side = project(at[0] + right[0] * halfWidth, at[1], at[2] + right[2] * halfWidth);
            if (feet == null || head == null || side == null) {
                continue;
            }
            // Only on the figure as drawn, give or take a few pixels: zoomed out a few pixels span blocks.
            double reach = Math.max(PICK_MIN, Math.hypot(side[0] - feet[0], side[1] - feet[1]) + PICK_SLACK);
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
     * walking the blocks the client has, and past them the map's surface the server sent. Past that it meets sea
     * level. As {x, y, z}.
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
        double limit = Math.max(600, camera.distance * 4);
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
        if (!client.isLoaded(x, z)) {
            return y < client.surfaceTop(x, z);
        }
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
        if (building == null) {
            building = state().buildingAt(ground[0] + ray[3] * 0.3, ground[2] + ray[5] * 0.3);
        }
        return building == null || seen(building) ? building : null;
    }

    /** Whether the viewer's team knows of the building: it stands where they have been, or it is their own. */
    private boolean seen(RtsState.BuildingInfo b) {
        return !state().isEnemyTeam(b.team) || client.fog.isExplored(b.x, b.z, b.sizeX, b.sizeZ);
    }

    /** Whether the unit, player or mob is out of the fog. */
    private boolean seen(RtsState.Thing thing, float partialTicks) {
        double[] at = client.positionOf(thing, partialTicks);
        return client.fog.isVisible(at[0], at[2]);
    }

    private double[] project(double x, double y, double z) {
        return camera().project(x, y, z, mc.displayWidth, mc.displayHeight);
    }

    // ---------------------------------------------------------------- selecting and ordering

    private boolean isEnemy(Hit hit) {
        if ("mob".equals(hit.kind)) {
            return !((RtsState.MobInfo) hit.thing).carcass;
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
        if (client.lighting) {
            if (button == 1) {
                client.stopLighting();
            } else if (button == 0) {
                pickLightCorner(px, py);
            }
            return;
        }
        Hit hit = entityAt(px, py);
        if (client.attackMoving && (button == 0 || button == 1)) {
            client.attackMoving = false;
            attackMove(px, py, hit);
            return;
        }
        if (button == 1) {
            if (!client.selected.isEmpty()) {
                order(px, py, hit);
            } else if (!client.selectedBuildings.isEmpty()) {
                setRally(px, py, hit);
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
            String key = "building " + building.id;
            long now = System.currentTimeMillis();
            if (key.equals(lastClickId) && now - lastClickTime < 400) {
                // A double click picks every building of that kind in view.
                selectBuildingsIn(0, 0, mc.displayWidth, mc.displayHeight, building);
            } else if (shift) {
                List<Integer> ids = new ArrayList<>(client.selectedBuildings);
                if (!ids.remove((Integer) building.id)) {
                    ids.add(building.id);
                }
                client.selectBuildings(ids, client.selectedBuilding);
                client.selected.clear();
                client.changedSelection();
            } else {
                client.selectBuilding(building.id);
            }
            lastClickId = key;
            lastClickTime = now;
        } else if (!shift && (!client.selected.isEmpty() || client.selectedBuilding != 0)) {
            client.selected.clear();
            client.selectBuildings(new ArrayList<>(), 0);
            client.changedSelection();
        }
    }

    /** The first click picks one corner of the area to light; the second the other, and the workers go. */
    private void pickLightCorner(int px, int py) {
        double[] ground = groundUnder(px, py);
        if (ground == null) {
            return;
        }
        if (client.lightCorner == null) {
            client.lightCorner = ground;
            return;
        }
        double[] first = client.lightCorner;
        client.stopLighting();
        JsonObject body = unitsBody(client.selectedWorkers());
        body.addProperty("x1", first[0]);
        body.addProperty("z1", first[2]);
        body.addProperty("x2", ground[0]);
        body.addProperty("z2", ground[2]);
        client.mark((first[0] + ground[0]) / 2, ground[1], (first[2] + ground[2]) / 2, false);
        client.command("units.light", body, reply -> {
            int torches = reply.has("torches") ? reply.get("torches")
                .getAsInt() : 0;
            client.showToast("Putting up " + torches + (torches == 1 ? " torch" : " torches"), false);
        });
    }

    private void order(int px, int py, Hit hit) {
        RtsState.TeamInfo team = state().myTeam();
        if (hit != null && hit.thing instanceof RtsState.PlayerInfo
            && team != null
            && team.id.equals(((RtsState.PlayerInfo) hit.thing).team)) {
            // Right-clicking a player of the team, such as yourself, has the units follow them in formation.
            String name = ((RtsState.PlayerInfo) hit.thing).name;
            client.mark(hit.position[0], hit.position[1], hit.position[2], false);
            JsonObject body = unitsBody(client.selected);
            body.addProperty("target", hit.thing.id);
            body.addProperty("formation", client.formation);
            client.command("units.follow", body, reply -> client.showToast("Following " + name, false));
            return;
        }
        if (hit != null && hit.thing instanceof RtsState.MobInfo
            && ((RtsState.MobInfo) hit.thing).carcass
            && !client.selectedWorkers()
                .isEmpty()) {
            // Workers carve a carcass; anyone else just goes there.
            client.mark(hit.position[0], hit.position[1], hit.position[2], false);
            JsonObject body = unitsBody(client.selectedWorkers());
            body.addProperty("target", hit.thing.id);
            client.command("units.attack", body, null);
            return;
        }
        if (hit != null && isEnemy(hit)) {
            client.mark(hit.position[0], hit.position[1], hit.position[2], true);
            JsonObject body = unitsBody(client.selected);
            body.addProperty("target", hit.thing.id);
            // Shift-clicking enemies one after another lines them up to attack in turn.
            body.addProperty("queue", isShiftKeyDown());
            client.command("units.attack", body, null);
            return;
        }
        if (hit == null
            && (orderBuild(px, py) || orderAttackBuilding(px, py) || orderMine(px, py) || orderChop(px, py))) {
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
        body.addProperty("formation", client.formation);
        // Shift-clicking ground one spot after another lays out waypoints to walk in turn, as for scouting.
        body.addProperty("queue", isShiftKeyDown());
        client.command("units.move", body, null);
    }

    /**
     * Sends the selected warriors and archers on an attack move to the ground clicked, fighting whatever enemies they
     * meet; an enemy clicked they attack straight away.
     */
    private void attackMove(int px, int py, Hit hit) {
        Set<String> fighters = client.selectedFighters();
        if (fighters.isEmpty()) {
            return;
        }
        if (hit != null && isEnemy(hit)) {
            client.mark(hit.position[0], hit.position[1], hit.position[2], true);
            JsonObject body = unitsBody(fighters);
            body.addProperty("target", hit.thing.id);
            body.addProperty("queue", isShiftKeyDown());
            client.command("units.attack", body, null);
            return;
        }
        double[] ground = groundUnder(px, py);
        if (ground == null) {
            return;
        }
        client.mark(ground[0], ground[1], ground[2], true);
        JsonObject body = unitsBody(fighters);
        body.addProperty("x", ground[0]);
        body.addProperty("z", ground[2]);
        body.addProperty("formation", client.formation);
        body.addProperty("attack", true);
        body.addProperty("queue", isShiftKeyDown());
        client.command("units.move", body, reply -> client.showToast("Attack move", false));
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

    /**
     * Right-clicking one of the team's building sites, damaged buildings or farms sends the selected units that can
     * build it there: dark wizards to a stone circle, workers to the rest. Any others selected walk there.
     */
    private boolean orderBuild(int px, int py) {
        RtsState.BuildingInfo building = buildingAt(px, py);
        RtsState.TeamInfo team = state().myTeam();
        RtsState.BuildingTypeInfo type = building == null ? null : state().buildingType(building.type);
        if (building == null || team == null || !building.team.equals(team.id)) {
            return false;
        }
        boolean whole = building.complete && building.health >= building.maxHealth;
        boolean wizards = type != null && type.wizards;
        // A whole building needs no building or repair; only workers go to work a farm.
        Set<String> builders = new LinkedHashSet<>();
        if (!whole || (!wizards && type != null && type.workers > 0)) {
            for (String id : client.selected) {
                RtsState.UnitInfo unit = state().units.get(id);
                if (unit != null && (wizards ? unit.isDarkWizard() : whole ? unit.isWorker() : unit.canBuild())) {
                    builders.add(id);
                }
            }
        }
        if (builders.isEmpty()) {
            return false;
        }
        double x = building.x + building.sizeX / 2.0;
        double z = building.z + building.sizeZ / 2.0;
        client.mark(x, building.y + 1, z, false);
        JsonObject body = unitsBody(builders);
        body.addProperty("building", building.id);
        body.addProperty("queue", isShiftKeyDown());
        client.command("units.build", body, null);
        Set<String> others = new LinkedHashSet<>(client.selected);
        others.removeAll(builders);
        if (!others.isEmpty()) {
            JsonObject move = unitsBody(others);
            move.addProperty("x", x);
            move.addProperty("z", z);
            move.addProperty("formation", client.formation);
            move.addProperty("queue", isShiftKeyDown());
            client.command("units.move", move, null);
        }
        return true;
    }

    /** Right-clicking a gold mine sends the selected workers to mine it. */
    private boolean orderMine(int px, int py) {
        RtsState.FeatureInfo mine = mineUnder(px, py);
        Set<String> workers = client.selectedWorkers();
        if (mine == null || workers.isEmpty()) {
            return false;
        }
        double x = mine.x + mine.sizeX / 2.0;
        double z = mine.z + mine.sizeZ / 2.0;
        client.mark(x, mine.y + 1, z, false);
        JsonObject body = unitsBody(workers);
        body.addProperty("x", x);
        body.addProperty("y", mine.y);
        body.addProperty("z", z);
        client.command("units.gather", body, null);
        return true;
    }

    /** The gold mine under a screen point, or null. */
    private RtsState.FeatureInfo mineUnder(int px, int py) {
        double[] ground = groundUnder(px, py);
        return ground == null ? null : state().mineAt(ground[0], ground[2]);
    }

    /** Right-clicking a tree sends the selected workers to chop it. */
    private boolean orderChop(int px, int py) {
        int[] tree = treeUnder(px, py);
        Set<String> workers = client.selectedWorkers();
        if (tree == null || workers.isEmpty()) {
            return false;
        }
        client.mark(tree[0] + 0.5, tree[1] + 0.5, tree[2] + 0.5, false);
        JsonObject body = unitsBody(workers);
        body.addProperty("x", tree[0] + 0.5);
        body.addProperty("y", tree[1]);
        body.addProperty("z", tree[2] + 0.5);
        client.command("units.gather", body, null);
        return true;
    }

    /** The log or leaves under a screen point, off any building, as x, y, z; null if it is no tree. */
    private int[] treeUnder(int px, int py) {
        double[] ground = groundUnder(px, py);
        if (ground == null) {
            return null;
        }
        double[] ray = camera().ray(px, py, mc.displayWidth, mc.displayHeight);
        int x = MathHelper.floor_double(ground[0] + ray[3] * 0.05);
        int y = MathHelper.floor_double(ground[1] + ray[4] * 0.05);
        int z = MathHelper.floor_double(ground[2] + ray[5] * 0.05);
        if (state().buildingAt(x + 0.5, z + 0.5) != null) {
            return null;
        }
        long key = ((long) x << 36) ^ ((long) (y & 0xFF) << 28) ^ (z & 0xFFFFFFFL);
        if (key != treeKey || mc.theWorld.getTotalWorldTime() - treeTime > 20) {
            // Only logs and leaves that grew on a tree; those of buildings, walls and ruins are no tree's.
            treeKey = key;
            treeTime = mc.theWorld.getTotalWorldTime();
            treeFound = Trees.treeOf(mc.theWorld, x, y, z) != null;
        }
        return treeFound ? new int[] { x, y, z } : null;
    }

    /**
     * The block treeUnder looked at last, when, and whether it was part of a tree, as working that out takes a while.
     */
    private long treeKey = Long.MIN_VALUE;
    private long treeTime;
    private boolean treeFound;

    /** The tracked tree nearest a block of a tree, within a few blocks across; null if none is tracked. */
    private RtsState.TreeInfo trackedTreeNear(int[] block) {
        RtsState.TreeInfo best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (RtsState.TreeInfo tree : state().trees) {
            int dx = tree.x - block[0];
            int dz = tree.z - block[2];
            int distance = dx * dx + dz * dz;
            if (distance <= 9 && block[1] >= tree.y - 1 && block[1] <= tree.top + 4 && distance < bestDistance) {
                best = tree;
                bestDistance = distance;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- control groups and rally points

    private void setGroup(int number, boolean add) {
        if (client.selected.isEmpty() && client.selectedBuildings.isEmpty()) {
            return;
        }
        RtsClient.Group group = client.groups.computeIfAbsent(number, n -> new RtsClient.Group());
        if (!add) {
            group.units.clear();
            group.buildings.clear();
        }
        group.units.addAll(client.selected);
        group.buildings.addAll(client.selectedBuildings);
        client.showToast((add ? "Added to group " : "Made group ") + number, false);
    }

    private void recallGroup(int number) {
        RtsClient.Group group = client.groups.get(number);
        if (group == null || group.isEmpty()) {
            return;
        }
        if (client.selected.equals(group.units) && client.selectedBuildings.equals(group.buildings)) {
            focusGroup(group);
            return;
        }
        client.selected.clear();
        client.selected.addAll(group.units);
        client.selectBuildings(new ArrayList<>(group.buildings), 0);
        if (!group.units.isEmpty()) {
            client.changedSelection();
        }
    }

    private void focusGroup(RtsClient.Group group) {
        double x = 0;
        double z = 0;
        int count = 0;
        for (String id : group.units) {
            RtsState.UnitInfo unit = state().units.get(id);
            if (unit != null) {
                x += unit.x;
                z += unit.z;
                count++;
            }
        }
        for (int id : group.buildings) {
            RtsState.BuildingInfo building = state().buildings.get(id);
            if (building != null) {
                x += building.x + building.sizeX / 2.0;
                z += building.z + building.sizeZ / 2.0;
                count++;
            }
        }
        if (count > 0) {
            client.focusOn(x / count, z / count);
        }
    }

    /** The selected buildings that train units. */
    private List<RtsState.BuildingInfo> selectedTrainers() {
        List<RtsState.BuildingInfo> trainers = new ArrayList<>();
        for (RtsState.BuildingInfo building : client.selectedBuildingList()) {
            RtsState.BuildingTypeInfo type = state().buildingType(building.type);
            if (type != null && !type.trains.isEmpty()) {
                trainers.add(building);
            }
        }
        return trainers;
    }

    private static JsonObject buildingsBody(List<RtsState.BuildingInfo> buildings) {
        JsonArray ids = new JsonArray();
        for (RtsState.BuildingInfo building : buildings) {
            ids.add(new JsonPrimitive(building.id));
        }
        JsonObject body = new JsonObject();
        body.add("buildings", ids);
        return body;
    }

    /** Right-click with buildings selected: their new units go there, or join the control group of the unit. */
    private void setRally(int px, int py, Hit hit) {
        List<RtsState.BuildingInfo> trainers = selectedTrainers();
        if (trainers.isEmpty()) {
            return;
        }
        Integer number = hit != null && hit.thing instanceof RtsState.UnitInfo
            && state().isMine((RtsState.UnitInfo) hit.thing) ? client.groupOf(hit.thing.id) : null;
        if (number != null) {
            for (RtsState.BuildingInfo building : trainers) {
                client.rallyGroups.put(building.id, number);
            }
            client.command("buildings.rally", buildingsBody(trainers), null);
            client.showToast("New units join group " + number, false);
            return;
        }
        double[] ground = hit != null ? hit.position : groundUnder(px, py);
        if (ground == null) {
            return;
        }
        for (RtsState.BuildingInfo building : trainers) {
            client.rallyGroups.remove(building.id);
        }
        client.mark(ground[0], ground[1], ground[2], false);
        JsonObject body = buildingsBody(trainers);
        body.addProperty("x", ground[0]);
        body.addProperty("z", ground[2]);
        client.command("buildings.rally", body, reply -> client.showToast("Rally point set", false));
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
        // With no units in it, the box picks the viewer's buildings in it instead.
        if (client.selected.isEmpty() && selectBuildingsIn(left, top, right, bottom, null)) {
            return;
        }
        client.changedSelection();
    }

    /**
     * Selects the viewer's buildings drawn inside a screen rectangle, only those of like's kind if like is given,
     * like itself among them. Returns whether it found any.
     */
    private boolean selectBuildingsIn(int left, int top, int right, int bottom, RtsState.BuildingInfo like) {
        RtsState.TeamInfo team = state().myTeam();
        if (team == null) {
            return false;
        }
        List<Integer> ids = new ArrayList<>();
        if (like != null) {
            ids.add(like.id);
        }
        for (RtsState.BuildingInfo b : state().buildings.values()) {
            if (!b.team.equals(team.id) || ids.contains(b.id)
                || like != null && (!like.type.equals(b.type) || !b.complete)) {
                continue;
            }
            double[] p = project(b.x + b.sizeX / 2.0, b.y + 1, b.z + b.sizeZ / 2.0);
            if (p != null && p[0] >= left && p[0] <= right && p[1] >= top && p[1] <= bottom) {
                ids.add(b.id);
            }
        }
        if (ids.isEmpty()) {
            return false;
        }
        client.selectBuildings(ids, like != null ? like.id : client.selectedBuilding);
        client.selected.clear();
        return true;
    }

    /** The viewer's workers with nothing to do, by number. */
    private List<RtsState.UnitInfo> idleWorkers() {
        List<RtsState.UnitInfo> idle = new ArrayList<>();
        for (RtsState.UnitInfo unit : state().units.values()) {
            if (state().isMine(unit) && unit.isWorker() && "idle".equals(doingText(unit))) {
                idle.add(unit);
            }
        }
        idle.sort((a, b) -> Integer.compare(a.number, b.number));
        return idle;
    }

    /** Selects and goes to the next idle worker, or with all every idle worker. */
    private void selectIdleWorkers(boolean all) {
        List<RtsState.UnitInfo> idle = idleWorkers();
        if (idle.isEmpty()) {
            return;
        }
        RtsState.UnitInfo next = idle.get(0);
        for (RtsState.UnitInfo unit : idle) {
            if (unit.number > lastIdle) {
                next = unit;
                break;
            }
        }
        client.selected.clear();
        if (all) {
            for (RtsState.UnitInfo unit : idle) {
                client.selected.add(unit.id);
            }
        } else {
            client.selected.add(next.id);
        }
        lastIdle = next.number;
        client.focusOn(next.x, next.z);
        client.changedSelection();
    }

    /** Narrows the selection by kind: keeps only that kind, or with drop set, every other kind. */
    private void filterSelection(String kind, boolean drop) {
        client.selected.removeIf(id -> {
            RtsState.UnitInfo unit = state().units.get(id);
            return unit == null || kind.equals(unit.kind) == drop;
        });
        client.changedSelection();
    }

    /**
     * Queues count units of a kind over the buildings, each going to whichever has the shortest queue then, and
     * says how many it queued.
     */
    private void trainSpread(List<RtsState.BuildingInfo> buildings, String kindId, String kindName, int count) {
        Map<Integer, Integer> queued = new LinkedHashMap<>();
        for (RtsState.BuildingInfo b : buildings) {
            queued.put(b.id, b.queue.size());
        }
        Map<Integer, Integer> plan = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            Integer best = null;
            for (Map.Entry<Integer, Integer> entry : queued.entrySet()) {
                if (entry.getValue() < 10 && (best == null || entry.getValue() < queued.get(best))) {
                    best = entry.getKey();
                }
            }
            if (best == null) {
                break;
            }
            queued.put(best, queued.get(best) + 1);
            plan.merge(best, 1, Integer::sum);
        }
        int[] total = { 0 };
        for (Map.Entry<Integer, Integer> entry : plan.entrySet()) {
            JsonObject body = new JsonObject();
            body.addProperty("building", entry.getKey());
            body.addProperty("unit", kindId);
            body.addProperty("count", entry.getValue());
            int places = plan.size();
            client.command("buildings.train", body, reply -> {
                total[0] += reply.has("count") ? reply.get("count")
                    .getAsInt() : 1;
                client.showToast(
                    (total[0] == 1 ? "Training a " + kindName : "Training " + total[0] + " " + kindName + "s")
                        + (places > 1 ? " at " + places + " buildings" : ""),
                    false);
            });
        }
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
        if (RtsClient.isLine(client.placing.type)) {
            placeWall(keep);
            return;
        }
        RtsClient.Footprint spot = client.footprint();
        if (spot == null) {
            return;
        }
        if (!spot.ok) {
            client.showToast(
                "town_hall".equals(client.placing.type) ? "A town center can only go on a settlement"
                    : "quarry".equals(client.placing.type) ? "A quarry can only go on a mined out gold mine"
                        : "It does not fit there",
                true);
            return;
        }
        Set<String> workers = client.selectedBuilders();
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
        // Shift lays out more sites, which the workers build one after another.
        body.addProperty("queue", keep);
        RtsState.BuildingTypeInfo type = state().buildingType(placing.type);
        client.command("buildings.place", body, reply -> client.showToast(type.name + " site laid out", false));
    }

    /**
     * A click while laying out a wall or bridge: the first sets where it starts, the second where it ends, and sends
     * it. With shift the next one starts where this one ended.
     */
    private void placeWall(boolean keep) {
        RtsClient.Placing placing = client.placing;
        if (placing.start == null) {
            placing.start = new int[] { placing.cx, placing.cz };
            return;
        }
        RtsClient.WallSpot spot = client.wallSpot();
        if (spot == null) {
            return;
        }
        boolean bridge = RtsClient.BRIDGE.equals(placing.type);
        if (!spot.ok) {
            client.showToast(!bridge ? "The wall does not fit there" : "The bridge does not fit there", true);
            return;
        }
        Set<String> workers = client.selectedConstructors();
        if (workers.isEmpty()) {
            client.showToast("Select a worker to build with", true);
            client.stopPlacing();
            return;
        }
        JsonObject body = unitsBody(workers);
        body.addProperty("x1", placing.start[0]);
        body.addProperty("z1", placing.start[1]);
        body.addProperty("x2", placing.cx);
        body.addProperty("z2", placing.cz);
        body.addProperty("queue", keep);
        if (keep) {
            placing.start = new int[] { placing.cx, placing.cz };
        } else {
            client.stopPlacing();
        }
        client.command(
            bridge ? "buildings.bridge" : "buildings.wall",
            body,
            reply -> client.showToast(
                (bridge ? "Bridge of " : "Wall of ") + (reply.has("pieces") ? reply.get("pieces")
                    .getAsInt() : spot.pieces.size()) + (bridge ? " blocks laid out" : " pieces laid out"),
                false));
    }

    /** What a wall of so many pieces costs: a segment's cost for each, as the server charges. */
    private static Map<String, Long> wallCost(RtsState.BuildingTypeInfo type, int pieces) {
        Map<String, Long> cost = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : type.cost.entrySet()) {
            cost.put(entry.getKey(), entry.getValue() * pieces);
        }
        return cost;
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
                return (headed ? "on the way to build " + where : "building " + where)
                    + (unit.queued > 0 ? ", " + unit.queued + " more queued" : "");
            case "work":
                if (unit.delivering) {
                    return "taking " + RtsState.amountsText(unit.carrying) + " to store";
                }
                return headed ? "on the way to " + where : "working at " + where;
            case "chop":
                if (unit.delivering) {
                    return "taking " + RtsState.amountsText(unit.carrying) + " to store";
                }
                return unit.chopping ? "chopping a tree" : "on the way to a tree";
            case "mine":
                if (unit.delivering) {
                    return "taking " + RtsState.amountsText(unit.carrying) + " to store";
                }
                return unit.mining ? "mining gold" : "on the way to a gold mine";
            case "light":
                return "putting up torches, " + unit.torches + " to go";
            case "follow": {
                RtsState.PlayerInfo leader = unit.target == null ? null : state().players.get(unit.target);
                return "following " + (leader != null ? leader.name : "a player");
            }
            case "hunt":
                if (unit.delivering) {
                    return "taking " + RtsState.amountsText(unit.carrying) + " to store";
                }
                return unit.carving ? "carving a carcass" : unit.target != null ? "hunting" : "on the way to a carcass";
            default:
                return unit.isWorker() ? "idle" : "on guard";
        }
    }

    private String unitName(RtsState.UnitInfo unit) {
        RtsState.TeamInfo team = state().teamsById.get(unit.team);
        RtsState.UnitKindInfo kind = state().unitKind(unit.kind);
        // A hero goes by his own name.
        if (kind != null && kind.hero) {
            return kind.name + (team != null ? " (" + team.name + ")" : "");
        }
        return (team != null ? team.name + " " : "") + (kind != null ? kind.name.toLowerCase() : unit.kind)
            + " "
            + unit.number;
    }

    private String buildingName(RtsState.BuildingInfo building) {
        RtsState.TeamInfo team = state().teamsById.get(building.team);
        RtsState.BuildingTypeInfo type = state().buildingType(building.type);
        return (team != null ? team.name + " " : "") + (type != null ? type.name.toLowerCase() : building.type)
            + (building.level > 1 ? " (level " + building.level + ")" : "");
    }

    private static String healthText(double health, double max) {
        return (int) Math.ceil(health) + "/" + (int) Math.ceil(max);
    }

    /** What the mouse is over, as {icon item, text, "attack" or "act" or null}, or null for nothing. */
    private Object[] hint(int px, int py) {
        if (state().game == null || client.placing != null) {
            return null;
        }
        if (client.lighting) {
            return new Object[] { new ItemStack(Blocks.torch),
                client.lightCorner == null ? "Click one corner of the area to light" : "Click the opposite corner",
                "act" };
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
        if (hit != null && workers + fighters > 0
            && hit.thing instanceof RtsState.PlayerInfo
            && team != null
            && team.id.equals(((RtsState.PlayerInfo) hit.thing).team)) {
            return new Object[] { "soldier", "Follow " + ((RtsState.PlayerInfo) hit.thing).name + " in formation",
                "act" };
        }
        if (workers + fighters == 0 && !selectedTrainers().isEmpty()) {
            Integer number = hit != null && hit.thing instanceof RtsState.UnitInfo
                && state().isMine((RtsState.UnitInfo) hit.thing) ? client.groupOf(hit.thing.id) : null;
            return new Object[] { "level", number != null ? "New units join group " + number : "Set rally point",
                "act" };
        }
        if (hit != null) {
            if (hit.thing instanceof RtsState.MobInfo && ((RtsState.MobInfo) hit.thing).carcass) {
                RtsState.MobInfo carcass = (RtsState.MobInfo) hit.thing;
                return new Object[] { "food",
                    (workers > 0 ? "Carve " + carcass.name.toLowerCase() : carcass.name) + " · "
                        + carcass.food
                        + " food",
                    workers > 0 ? "act" : null };
            }
            if (workers > 0 && fighters == 0
                && hit.thing instanceof RtsState.MobInfo
                && ((RtsState.MobInfo) hit.thing).food > 0) {
                RtsState.MobInfo mob = (RtsState.MobInfo) hit.thing;
                return new Object[] { "sword", "Hunt " + mob.name + " · " + mob.food + " food", "attack" };
            }
            if (fighters > 0 && isEnemy(hit)) {
                String name = hit.thing instanceof RtsState.UnitInfo ? unitName((RtsState.UnitInfo) hit.thing)
                    : hit.thing instanceof RtsState.PlayerInfo ? ((RtsState.PlayerInfo) hit.thing).name
                        : ((RtsState.MobInfo) hit.thing).name + " · "
                            + healthText(hit.thing.health, hit.thing.maxHealth);
                return new Object[] { "sword", "Attack " + name, "attack" };
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
            return new Object[] { unit.kind, text + " · " + healthText(unit.health, unit.maxHealth), null };
        }
        RtsState.BuildingInfo b = buildingAt(px, py);
        if (b == null) {
            RtsState.FeatureInfo goldMine = mineUnder(px, py);
            if (goldMine != null) {
                String gold = goldMine.gold + "/" + goldMine.maxGold + " gold";
                if (goldMine.gold <= 0) {
                    return new Object[] { "gold", "Mined out " + goldMine.name.toLowerCase()
                        + (team != null && goldMine.free ? " · workers can dig a quarry here" : ""), null };
                }
                if (workers > 0) {
                    return new Object[] { "pickaxe", "Mine gold · " + goldMine.name + " · " + gold, "act" };
                }
                return new Object[] { "gold",
                    goldMine.name + " · " + gold + (team != null ? " · select workers to mine it" : ""), null };
            }
            double[] ground = groundUnder(px, py);
            RtsState.FeatureInfo settlement = ground == null ? null
                : state().freeSettlementNear(ground[0], ground[2], 0);
            if (settlement != null) {
                return new Object[] { "town_hall", "Settlement · workers can raise a town center here", null };
            }
            int[] block = treeUnder(px, py);
            if (block == null) {
                return null;
            }
            RtsState.TreeInfo tree = trackedTreeNear(block);
            String wood = tree == null ? "" : " · " + tree.wood + "/" + tree.maxWood + " wood";
            if (workers > 0) {
                return new Object[] { "axe", "Chop tree" + wood, "act" };
            }
            return new Object[] { "tree", "Tree" + wood + (team != null ? " · select workers to chop it" : ""), null };
        }
        RtsState.BuildingTypeInfo type = state().buildingType(b.type);
        if (type == null) {
            return null;
        }
        boolean mine = team != null && b.team.equals(team.id);
        if (fighters > 0 && state().isEnemyTeam(b.team)) {
            return new Object[] { "sword", "Attack " + buildingName(b) + " · " + b.health + "/" + b.maxHealth,
                "attack" };
        }
        if (mine && workers > 0 && !b.complete) {
            return new Object[] { "axe",
                "Build " + type.name.toLowerCase() + " · " + (int) Math.floor(100 * b.built) + "% built", "act" };
        }
        if (mine && workers > 0 && type.workers > 0) {
            return new Object[] { toolFor(b.type),
                "Work at " + type.name.toLowerCase() + " (" + b.workers + " of " + type.workers + " workers)", "act" };
        }
        String text = buildingName(b);
        if (!b.complete) {
            text += " · building site, " + (int) Math.floor(100 * b.built) + "% built";
        } else if (!b.queue.isEmpty()) {
            text += " · training " + b.queue.size();
        } else if (b.research != null) {
            text += " · " + researchName(b.research).toLowerCase() + " " + (int) Math.floor(100 * b.researched) + "%";
        } else if (type.workers > 0) {
            text += " · " + b.workers + " working";
        }
        text += " · " + b.health + "/" + b.maxHealth;
        return new Object[] { b.complete ? b.type : new ItemStack(Blocks.fence), text, null };
    }

    /** What a building researches, by name: an upgrade, or going up a level. */
    private String researchName(String research) {
        if ("level".equals(research)) {
            return "Going up a level";
        }
        RtsState.UpgradeInfo upgrade = state().upgrade(research);
        return upgrade == null ? research : upgrade.name;
    }

    /** The tool workers use at a building: a hoe at a farm, a pickaxe at a quarry, else an axe. */
    private static String toolFor(String buildingType) {
        return "farm".equals(buildingType) ? "hoe" : "quarry".equals(buildingType) ? "pickaxe" : "axe";
    }

    /** Draws one of the icons, 16 pixels square, scaled to size. */
    private void drawIcon(String name, int x, int y, int size) {
        ItemStack item = VANILLA_ICONS.get(name);
        if (item != null) {
            // The resources and tools are Minecraft's own items, drawn as the inventory draws them.
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, 0);
            GL11.glScalef(size / 16F, size / 16F, 1);
            drawItem(item, 0, 0);
            GL11.glPopMatrix();
            return;
        }
        int index = ICON_NAMES.indexOf(name);
        if (index < 0) {
            return;
        }
        mc.getTextureManager()
            .bindTexture(ICONS);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_BLEND);
        func_152125_a(x, y, index * 16, 0, 16, 16, size, size, ICON_NAMES.size() * 16, 16);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void drawScreen(int unusedX, int unusedY, float partialTicks) {
        if (camera() == null) {
            return;
        }
        this.partialTicks = partialTicks;
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
        if (attackBox && moved && downButton == 1) {
            int x0 = guiX(Math.min(downX, mouseX));
            int y0 = guiY(Math.min(downY, mouseY));
            int x1 = guiX(Math.max(downX, mouseX));
            int y1 = guiY(Math.max(downY, mouseY));
            drawRect(x0, y0, x1, y1, 0x20FF4040);
            drawOutline(x0, y0, x1, y1, 0xE0FF4040);
        }
        if (mc.gameSettings.hideGUI) {
            return;
        }
        drawLightArea();
        drawBanner();
        drawTeamPanel();
        drawBar();
        drawHelp();
        drawMinimap();
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

    private int minimapSize() {
        return Math.max(64, Math.min(120, height / 3));
    }

    /** The world x, z under a point on the minimap, or null if the point is not on it. */
    private double[] minimapAt(int gx, int gy) {
        if (state().game == null || mc.gameSettings.hideGUI) {
            return null;
        }
        int size = minimapSize();
        return client.minimap.worldAt(gx, gy, width - size - 6, height - size - 6, size);
    }

    private void drawMinimap() {
        if (state().game == null) {
            return;
        }
        int size = minimapSize();
        client.minimap.update(state().game, client.terrain);
        client.minimap.draw(client, width - size - 6, height - size - 6, size, viewFrame());
        GL11.glColor4f(1, 1, 1, 1);
    }

    /** Where the corners of the screen meet the ground around what the camera looks at, as x, z. */
    private double[][] viewFrame() {
        RtsCamera camera = camera();
        int[][] corners = { { 0, 0 }, { mc.displayWidth, 0 }, { mc.displayWidth, mc.displayHeight },
            { 0, mc.displayHeight } };
        double[][] frame = new double[4][];
        for (int i = 0; i < 4; i++) {
            double[] ray = camera.ray(corners[i][0], corners[i][1], mc.displayWidth, mc.displayHeight);
            // Rays that miss the ground, toward the horizon, are cut off a few camera distances out.
            double t = ray[4] < -0.05 ? (camera.focusY - ray[1]) / ray[4] : camera.distance * 4;
            t = Math.min(t, camera.distance * 4);
            frame[i] = new double[] { ray[0] + ray[3] * t, ray[2] + ray[5] * t };
        }
        return frame;
    }

    /** The area workers are about to light, from its first corner to the ground under the mouse. */
    private void drawLightArea() {
        if (!client.lighting || client.lightCorner == null) {
            return;
        }
        double[] other = groundUnder(mouseX, mouseY);
        if (other == null) {
            return;
        }
        double[] a = client.lightCorner;
        double x0 = Math.floor(Math.min(a[0], other[0]));
        double x1 = Math.floor(Math.max(a[0], other[0])) + 1;
        double z0 = Math.floor(Math.min(a[2], other[2]));
        double z1 = Math.floor(Math.max(a[2], other[2])) + 1;
        double y = Math.max(a[1], other[1]) + 0.05;
        double[][] corners = { { x0, z0 }, { x1, z0 }, { x1, z1 }, { x0, z1 } };
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1F, 0.85F, 0.3F, 0.9F);
        GL11.glLineWidth(2F);
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.instance;
        tessellator.startDrawing(GL11.GL_LINE_LOOP);
        for (double[] corner : corners) {
            double[] p = project(corner[0], y, corner[1]);
            if (p == null) {
                tessellator.draw();
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(1, 1, 1, 1);
                return;
            }
            tessellator.addVertex(guiX(p[0]), guiY(p[1]), 0);
        }
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
        String size = (int) (x1 - x0) + " × " + (int) (z1 - z0);
        fontRendererObj.drawStringWithShadow(size, guiX(mouseX) + 12, guiY(mouseY) - 12, 0xFFE9A8);
    }

    private void drawTips() {
        int gx = guiX(mouseX);
        int gy = guiY(mouseY);
        client.hoverBuilding = 0;
        for (Widget widget : widgets) {
            if (widget.tip != null && widget.contains(gx, gy)) {
                drawTip(null, widget.tip, gx, gy, 0xFFFFFF);
                return;
            }
        }
        if (overPanel(gx, gy) || downButton >= 0) {
            return;
        }
        // A tower under the mouse shows how far it shoots.
        RtsState.BuildingInfo over = client.placing == null ? buildingAt(mouseX, mouseY) : null;
        if (over != null && over.range > 0) {
            client.hoverBuilding = over.id;
        }
        Object[] hint = hint(mouseX, mouseY);
        if (hint != null) {
            int color = "attack".equals(hint[2]) ? 0xFF8A80 : "act".equals(hint[2]) ? 0xA8FFB0 : 0xFFFFFF;
            drawTip(hint[0], (String) hint[1], gx, gy, color);
        }
    }

    /** A tooltip by the mouse; a line break in the text starts a new line, the lines after the first in grey. */
    private void drawTip(Object icon, String text, int gx, int gy, int color) {
        String[] lines = text.split("\n");
        int iconWidth = icon == null ? 0 : 18;
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, fontRendererObj.getStringWidth(line));
        }
        int w = textWidth + 8 + iconWidth;
        int h = Math.max(icon == null ? 14 : 20, lines.length * 10 + 4);
        int x = Math.min(gx + 10, width - w - 2);
        int y = gy + 14 + h > height ? gy - h - 4 : gy + 14;
        drawRect(x, y, x + w, y + h, 0xE0000000);
        if (icon instanceof ItemStack) {
            drawItem((ItemStack) icon, x + 3, y + 2);
        } else if (icon instanceof String) {
            drawIcon((String) icon, x + 3, y + 2, 16);
        }
        int textY = y + (h - lines.length * 10 + 2) / 2;
        for (int i = 0; i < lines.length; i++) {
            fontRendererObj
                .drawStringWithShadow(lines[i], x + 4 + iconWidth, textY + i * 10, i == 0 ? color : 0xB4B4B4);
        }
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
                || !seen(b)
                || (!"town_hall".equals(b.type) && !client.selectedBuildings.contains(b.id)
                    && b.complete
                    && b.queue.isEmpty()
                    && b.research == null
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
            String text = team.name + " "
                + type.name.toLowerCase()
                + (b.complete ? b.level > 1 ? " " + roman(b.level) : "" : " (site)");
            fontRendererObj
                .drawStringWithShadow(text, x - fontRendererObj.getStringWidth(text) / 2, y - 12, team.color);
            int row = 0;
            if (!b.complete) {
                bar(x, y + row++ * 5, 40, b.built, AMBER);
            } else if (!b.queue.isEmpty()) {
                bar(x, y + row++ * 5, 40, b.progress, GREEN);
            }
            if (b.research != null) {
                bar(x, y + row++ * 5, 40, b.researched, 0xFF6FA8FF);
            }
            if (hurt) {
                bar(x, y + row * 5, 40, b.health / (double) b.maxHealth, 0xFF000000 | team.color);
            }
        }
        for (RtsState.TreeInfo tree : state.trees) {
            if (tree.wood >= tree.maxWood) {
                continue;
            }
            double[] p = project(tree.x + 0.5, tree.top + 2, tree.z + 0.5);
            if (p != null) {
                bar(guiX(p[0]), guiY(p[1]), 20, tree.wood / (double) Math.max(1, tree.maxWood), 0xFFC8963C);
            }
        }
        // Units too far off for Minecraft to draw show as dots in their team's colour.
        for (RtsState.UnitInfo unit : state.units.values()) {
            if (client.isDrawn(unit) || !seen(unit, partialTicks)) {
                continue;
            }
            double[] p = project(unit.x, unit.y + 0.5, unit.z);
            if (p != null) {
                int x = guiX(p[0]);
                int y = guiY(p[1]);
                drawRect(x - 2, y - 2, x + 2, y + 2, 0xC0000000);
                drawRect(
                    x - 1,
                    y - 1,
                    x + 1,
                    y + 1,
                    client.selected.contains(unit.id) ? 0xFF73FF80 : teamColor(unit.team));
            }
        }
        for (RtsState.UnitInfo unit : state.units.values()) {
            boolean selected = client.selected.contains(unit.id);
            if (!selected && unit.health >= unit.maxHealth || !seen(unit, partialTicks)) {
                continue;
            }
            double[] at = client.positionOf(unit, partialTicks);
            double[] p = project(at[0], at[1] + at[3] + 0.4, at[2]);
            if (p != null) {
                bar(guiX(p[0]), guiY(p[1]), 16, unit.health / Math.max(1, unit.maxHealth), teamColor(unit.team));
            }
        }
        for (RtsState.MobInfo mob : state.mobs.values()) {
            if (mob.health >= mob.maxHealth || !seen(mob, partialTicks)) {
                continue;
            }
            double[] at = client.positionOf(mob, partialTicks);
            double[] p = project(at[0], at[1] + at[3] + 0.4, at[2]);
            if (p != null) {
                bar(guiX(p[0]), guiY(p[1]), 14, mob.health / Math.max(1, mob.maxHealth), RED);
            }
        }
        for (RtsState.PlayerInfo player : state.players.values()) {
            if (!seen(player, partialTicks)) {
                continue;
            }
            double[] at = client.positionOf(player, partialTicks);
            double[] p = project(at[0], at[1] + 2.2, at[2]);
            // Anyone hurt shows it, the viewer's own body too; commanders' bodies carry their own names.
            if (p != null && player.health < player.maxHealth) {
                bar(
                    guiX(p[0]),
                    guiY(p[1]) + 2,
                    16,
                    player.health / Math.max(1, player.maxHealth),
                    teamColor(player.team));
            }
            if (player.commanding || player.id.equals(state.me)) {
                continue;
            }
            if (p != null) {
                fontRendererObj.drawStringWithShadow(
                    player.name,
                    guiX(p[0]) - fontRendererObj.getStringWidth(player.name) / 2,
                    guiY(p[1]) - 8,
                    0xFFFFFF);
            }
        }
    }

    static String ageName(int age) {
        return "Age " + roman(age);
    }

    private static String roman(int level) {
        return level == 1 ? "I" : level == 2 ? "II" : level == 3 ? "III" : String.valueOf(level);
    }

    /** A team's colour for its health bars, whatever health is left; white for no team. */
    private int teamColor(String id) {
        RtsState.TeamInfo team = id == null ? null : state().teamsById.get(id);
        return 0xFF000000 | (team == null ? 0xFFFFFF : team.color);
    }

    private void bar(int cx, int y, int w, double fraction, int color) {
        int x = cx - w / 2;
        drawRect(x - 1, y - 1, x + w + 1, y + 3, 0xC0000000);
        drawRect(x, y, x + (int) Math.round(w * Math.max(0, Math.min(1, fraction))), y + 2, color);
    }

    /**
     * Across the top: who won once the game is over, otherwise how long each team that lost its last town center has
     * left to raise a new one.
     */
    private void drawBanner() {
        RtsState state = state();
        List<String> lines = new ArrayList<>();
        if (state.winners != null) {
            List<String> names = new ArrayList<>();
            for (String id : state.winners) {
                RtsState.TeamInfo team = state.teamsById.get(id);
                names.add(team == null ? id : team.name);
            }
            lines.add(
                names.isEmpty() ? "Game over: nobody won"
                    : "Game over: " + String.join(" and ", names) + (names.size() == 1 ? " wins!" : " win!"));
        } else {
            for (RtsState.TeamInfo team : state.teams) {
                if (team.countdown >= 0) {
                    lines.add(
                        team.name + " lost its last town center: "
                            + team.countdown / 60
                            + ":"
                            + String.format("%02d", team.countdown % 60)
                            + " to raise a new one");
                }
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int w = fontRendererObj.getStringWidth(line) + 16;
            int x = (width - w) / 2;
            int y = 6 + i * 16;
            drawRect(x, y, x + w, y + 14, 0xD0401008);
            fontRendererObj.drawStringWithShadow(line, x + 8, y + 3, 0xFFD27A);
        }
    }

    private void drawTeamPanel() {
        RtsState state = state();
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        // Where on the resources line each resource's icon goes, left blank in the text.
        int resourceLine = -1;
        List<Integer> iconAt = new ArrayList<>();
        RtsState.TeamInfo team = state.myTeam();
        if (state.game == null) {
            lines.add("No game is running");
            colors.add(0xFFFFFF);
        } else if (team == null) {
            lines.add("Watching: join a team to command" + (state.elapsed >= 0 ? " · " + clock(state.elapsed) : ""));
            colors.add(0xFFFFFF);
        } else {
            lines.add(
                "Team " + team.name
                    + " · "
                    + ageName(team.age)
                    + (state.elapsed >= 0 ? " · " + clock(state.elapsed) : ""));
            colors.add(team.color);
            if (team.ai != null) {
                lines.add(
                    "economy".equals(team.ai) ? "The computer runs your workers (B to change)"
                        : "The computer plays alongside you (B to change)");
                colors.add(0x9ACBFF);
            }
            if (team.defeated) {
                lines.add("Your team has been defeated");
                colors.add(0xFF6A5A);
            }
            StringBuilder resources = new StringBuilder();
            for (String resource : RtsState.RESOURCES) {
                Long amount = team.resources.get(resource);
                if (resources.length() > 0) {
                    resources.append("    ");
                }
                iconAt.add(fontRendererObj.getStringWidth(resources.toString()));
                resources.append("   ")
                    .append(amount == null ? 0 : amount);
            }
            resourceLine = lines.size();
            lines.add(resources.toString());
            colors.add(0xFFE9A8);
            lines.add(
                "Units " + team.units
                    + " of "
                    + team.population
                    + (team.population < state.game.unitsPerTeam ? " · build houses for more" : ""));
            colors.add(0xC8C8C8);
        }
        // The body left standing can be attacked while commanding.
        if (mc.thePlayer != null && state.game != null) {
            float health = mc.thePlayer.getHealth();
            float max = mc.thePlayer.getMaxHealth();
            lines.add(String.format("Your health %.0f of %.0f", Math.ceil(health), max));
            colors.add(health >= max ? 0xC8C8C8 : health > max / 2 ? 0xFFE9A8 : 0xFF6A5A);
        }
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, fontRendererObj.getStringWidth(line));
        }
        drawRect(4, 4, 4 + w + 8, 4 + lines.size() * 11 + 5, PANEL);
        for (int i = 0; i < lines.size(); i++) {
            fontRendererObj.drawStringWithShadow(lines.get(i), 8, 8 + i * 11, colors.get(i));
        }
        for (int i = 0; i < iconAt.size(); i++) {
            drawIcon(RtsState.RESOURCES[i], 8 + iconAt.get(i), 7 + resourceLine * 11, 9);
        }
    }

    /** Seconds as m:ss, or h:mm:ss from an hour on. */
    private static String clock(long seconds) {
        long h = seconds / 3600;
        long m = seconds / 60 % 60;
        long sec = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }

    private void drawHelp() {
        String help = "V leave · WASD move · Q/E turn · wheel zoom · H town center · B teams";
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
            if (RtsClient.BRIDGE.equals(client.placing.type)) {
                RtsClient.WallSpot bridge = client.wallSpot();
                int pieces = bridge == null ? 0 : bridge.pieces.size();
                actions.add(
                    label(
                        client.placing.start == null ? "Bridge: click the bank it starts from"
                            : "Bridge: click the far bank; " + pieces
                                + (pieces == 1 ? " block, " : " blocks, ")
                                + RtsState.costText(wallCost(type, pieces))
                                + "; shift to go on",
                        0xC8C8C8));
                actions.add(button("Cancel", "Esc or right-click", shift -> client.stopPlacing(), true));
            } else if (RtsClient.isWall(client.placing.type)) {
                RtsClient.WallSpot wall = client.wallSpot();
                int pieces = wall == null ? 0 : wall.pieces.size();
                actions.add(
                    label(
                        client.placing.start == null ? "Wall: click where it starts, or on one of your towers"
                            : "Wall: click where it ends; " + pieces
                                + (pieces == 1 ? " piece, " : " pieces, ")
                                + RtsState.costText(wallCost(type, pieces))
                                + "; shift to go on",
                        0xC8C8C8));
                actions.add(button("Cancel", "Esc or right-click", shift -> client.stopPlacing(), true));
            } else {
                actions.add(label("Place " + type.name.toLowerCase() + ": click to place, R to rotate", 0xC8C8C8));
                actions.add(
                    button(
                        "Rotate",
                        "Turn it a quarter round (R)",
                        shift -> { client.placing.rotation = (client.placing.rotation + 1) % 4; },
                        true));
                actions.add(button("Cancel", "Esc or right-click", shift -> client.stopPlacing(), true));
            }
        } else if (client.lighting) {
            actions.add(
                label(
                    client.lightCorner == null ? "Torches: click one corner of the area to light"
                        : "Torches: now click the opposite corner",
                    0xC8C8C8));
            actions.add(button("Cancel", "Esc or right-click", shift -> client.stopLighting(), true));
        } else if (building != null) {
            buildingActions(actions, team, building);
        } else if (!client.selectedBuilders()
            .isEmpty()) {
                // Workers build all but the stone circle, which only dark wizards raise.
                boolean wizards = client.selectedConstructors()
                    .isEmpty();
                for (Map.Entry<String, RtsState.BuildingTypeInfo> entry : state.game.buildingTypes.entrySet()) {
                    RtsState.BuildingTypeInfo type = entry.getValue();
                    // Each god's buildings belong to its own teams.
                    if (!type.buildable || type.wizards != wizards
                        || (type.faction != null && !type.faction.equals(team.faction))
                        || type.shunnedBy.contains(team.faction)) {
                        continue;
                    }
                    String id = entry.getKey();
                    boolean aged = team.age >= type.age;
                    RtsState.UpgradeInfo needed = type.requires == null || team.upgrades.contains(type.requires) ? null
                        : state.game.upgrades.get(type.requires);
                    actions.add(
                        square(
                            ActionIcons.building(team.faction, id),
                            id,
                            type.name + " ("
                                + RtsState.costText(type.cost)
                                + (RtsClient.BRIDGE.equals(id) ? " a block)" : RtsClient.isWall(id) ? " a piece)" : ")")
                                + "\n"
                                + (!aged ? "Needs " + ageName(type.age) + ": take your town center up a level"
                                    : needed != null ? "Needs " + needed.name + ": research it first"
                                        : RtsClient.BRIDGE.equals(id)
                                            ? "Build a wooden bridge: click one bank, then the other"
                                            : RtsClient.isWall(id)
                                                ? "Build a wall: click where it starts, or a tower, then where it ends"
                                                : "Build a " + type.name.toLowerCase() + ", then click where it goes"),
                            shift -> client.startPlacing(id),
                            aged && needed == null && RtsState.canAfford(team, type.cost)));
                }
                if (!wizards) {
                    actions.add(
                        square(
                            "torches",
                            "wood",
                            "Torches\nLight up an area so no mobs spawn there: click two corners. Each torch costs wood",
                            shift -> client.startLighting(),
                            true));
                }
            } else {
                RtsState.BuildingInfo hall = state.townHallOf(team.id);
                if (hall != null) {
                    actions.add(
                        square(
                            ActionIcons.building(team.faction, "town_hall"),
                            "town_hall",
                            "Town center\nSelect your town center to train workers (H)",
                            shift -> goToTownHall(),
                            true));
                }
            }

        if (client.placing == null && !client.lighting && building == null) {
            fighterActions(actions);
        }
        int count = client.selected.size();
        List<Widget> controls = new ArrayList<>();
        for (int number = 0; number <= 9; number++) {
            RtsClient.Group group = client.groups.get(number);
            if (group != null && !group.isEmpty()) {
                int recalled = number;
                controls.add(
                    button(
                        number + "·" + (group.units.size() + group.buildings.size()),
                        "Control group " + number + ": press " + number + " to select it, again to go to it",
                        shift -> recallGroup(recalled),
                        true));
            }
        }
        int idle = idleWorkers().size();
        if (idle > 0) {
            Widget idleButton = button(
                idle + " idle",
                "Go to the next idle worker; shift-click selects every idle worker",
                this::selectIdleWorkers,
                true);
            idleButton.color = 0xFFD76A;
            controls.add(withIcon(idleButton, "worker"));
        }
        if (count > 0) {
            // How many of each kind are selected; clicking one narrows the selection, as in Age of Mythology.
            Map<String, Integer> kinds = new LinkedHashMap<>();
            for (String id : client.selected) {
                RtsState.UnitInfo unit = state.units.get(id);
                if (unit != null) {
                    kinds.merge(unit.kind, 1, Integer::sum);
                }
            }
            for (Map.Entry<String, Integer> entry : kinds.entrySet()) {
                String kind = entry.getKey();
                RtsState.UnitKindInfo info = state.unitKind(kind);
                String name = info != null ? info.name.toLowerCase() + "s" : kind;
                boolean mixed = kinds.size() > 1;
                controls.add(
                    withIcon(
                        button(
                            String.valueOf(entry.getValue()),
                            mixed ? "Click or ctrl-click: keep only the " + name + "; shift-click: drop the " + name
                                : entry.getValue() + " " + name,
                            shift -> {
                                if (mixed) {
                                    filterSelection(kind, shift);
                                }
                            },
                            true),
                        kind));
            }
        } else if (building != null && client.selectedBuildings.size() > 1) {
            controls.add(label(client.selectedBuildings.size() + " buildings selected", 0xFFFFFF));
        } else {
            controls.add(label(team.units > 0 ? "None selected" : "No units yet", 0xFFFFFF));
        }
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
            client.selectBuildings(new ArrayList<>(), 0);
            client.changedSelection();
        }, count > 0 || building != null));
        controls.add(button("Leave (V)", "Back to playing in person", shift -> client.leave(), true));

        barTop = layoutRows(controls, height - 22);
        if (!actions.isEmpty()) {
            barTop = layoutRows(actions, barTop - 18);
        }
    }

    private static final String[][] FORMATIONS = { { "box", "Box", "A square block" },
        { "line", "Line", "A wide line, two or three deep" },
        { "wedge", "Wedge", "A wedge pointing the way they go" } };
    private static final String[][] STANCES = {
        { "aggressive", "Aggressive", "Look further for enemies and chase them far" },
        { "defensive", "Defensive", "Fight what comes near, then go back" },
        { "hold", "Hold", "Never leave the spot: fight only what is in reach" },
        { "passive", "Passive", "Never fight unless ordered, not even back" } };

    /** With warriors or archers selected: how they line up when sent somewhere, and how they fight. */
    private void fighterActions(List<Widget> actions) {
        List<String> fighters = new ArrayList<>();
        String common = null;
        boolean mixed = false;
        for (String id : client.selected) {
            RtsState.UnitInfo unit = state().units.get(id);
            if (unit != null && !unit.isWorker()) {
                fighters.add(id);
                if (common == null) {
                    common = unit.stance;
                } else if (!common.equals(unit.stance)) {
                    mixed = true;
                }
            }
        }
        if (fighters.isEmpty()) {
            return;
        }
        actions.add(label("Formation", 0xC8C8C8));
        for (String[] formation : FORMATIONS) {
            boolean on = formation[0].equals(client.formation);
            actions.add(
                button(
                    (on ? "» " : "") + formation[1],
                    formation[2] + ", where you send them",
                    shift -> client.formation = formation[0],
                    true));
        }
        actions.add(
            button(
                (client.attackMoving ? "» " : "") + "Attack move (Space)",
                "Then click where to go: they fight any enemy they meet on the way",
                shift -> startAttackMove(),
                true));
        actions.add(label("Stance", 0xC8C8C8));
        for (String[] stance : STANCES) {
            boolean on = !mixed && stance[0].equals(common);
            actions.add(button((on ? "» " : "") + stance[1], stance[2], shift -> {
                JsonObject body = unitsBody(fighters);
                body.addProperty("stance", stance[0]);
                client.command("units.stance", body, reply -> client.showToast(stance[1] + " stance", false));
            }, true));
        }
        prayerActions(actions);
    }

    private static final String[][] PRAYERS = { { "melee", "Protect from Melee", "melee attacks" },
        { "missiles", "Protect from Missiles", "arrows and other missiles" },
        { "magic", "Protect from Magic", "wizards' spells" } };

    /**
     * With Sir Tradigee or Sagittare of one's own selected: their protection prayers, which take three quarters of the
     * damage and all the knockback of the attack style they protect from. Sir Tradigee switches between them at a
     * click, and clicking the one that is up puts it down; Sagittare always protects from missiles.
     */
    private void prayerActions(List<Widget> actions) {
        RtsState state = state();
        List<String> praying = new ArrayList<>();
        boolean sagittare = false;
        String common = null;
        boolean mixed = false;
        for (String id : client.selected) {
            RtsState.UnitInfo unit = state.units.get(id);
            if (unit == null || !state.isMine(unit)) {
                continue;
            }
            if ("tradigee".equals(unit.kind)) {
                praying.add(id);
                if (common == null) {
                    common = unit.prayer;
                } else if (!common.equals(unit.prayer)) {
                    mixed = true;
                }
            } else if ("sagittare".equals(unit.kind)) {
                sagittare = true;
            }
        }
        if (praying.isEmpty() && !sagittare) {
            return;
        }
        actions.add(label("Prayer", 0xC8C8C8));
        for (String[] prayer : PRAYERS) {
            boolean missiles = "missiles".equals(prayer[0]);
            boolean on = praying.isEmpty() ? missiles : !mixed && prayer[0].equals(common);
            String tip;
            if (praying.isEmpty()) {
                tip = prayer[1] + "\n"
                    + (missiles ? "Sagittare always protects from missiles"
                        : "Sagittare cannot protect from " + ("melee".equals(prayer[0]) ? "melee" : "magic"));
            } else {
                tip = prayer[1] + "\nTakes 75% less damage and no knockback from "
                    + prayer[2]
                    + (on ? "; click to stop" : "");
            }
            Widget button = square(ActionIcons.prayer(prayer[0]), "hero", tip, shift -> {
                JsonObject body = unitsBody(praying);
                body.addProperty("prayer", on ? "none" : prayer[0]);
                client.command(
                    "units.prayer",
                    body,
                    reply -> client.showToast(on ? prayer[1] + " off" : prayer[1], false));
            }, !praying.isEmpty());
            button.lit = on;
            actions.add(button);
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
        int alike = 0;
        for (RtsState.BuildingInfo other : client.selectedBuildingList()) {
            if (other.type.equals(building.type)) {
                alike++;
            }
        }
        actions.add(
            withIcon(
                label(
                    type.name + (building.level > 1 ? " " + roman(building.level) : "")
                        + (alike > 1 ? " \u00D7" + alike : ""),
                    0xFFFFFF),
                building.type));
        actions.add(label("Health " + building.health + "/" + building.maxHealth, 0xC8C8C8));
        if (type.workers > 0) {
            actions.add(label(building.workers + " of " + type.workers + " workers", 0xC8C8C8));
        }
        RtsState.BuildingTypeInfo gate = state.buildingType("gate");
        if ("wall".equals(building.type) && building.team.equals(team.id) && gate != null) {
            int wallId = building.id;
            actions.add(
                square(
                    "gate",
                    "gate",
                    "Make gate (" + RtsState.costText(gate.cost)
                        + ")\nTurn this wall segment into a gate your units can walk through",
                    shift -> {
                        JsonObject body = new JsonObject();
                        body.addProperty("building", wallId);
                        client.command("buildings.gate", body, reply -> client.showToast("Gate made", false));
                    },
                    RtsState.canAfford(team, gate.cost)));
        }
        boolean teamFull = state.teamUnitCount(team) >= team.population;
        // Each click queues one more (shift-click five), up to ten waiting; training goes on one at a time. With
        // several buildings selected it is shared out among those that train that kind.
        for (String kindId : type.trains) {
            RtsState.UnitKindInfo kind = state.unitKind(kindId);
            if (kind == null || !kind.trainableBy(team.faction)) {
                continue;
            }
            List<RtsState.BuildingInfo> trainers = new ArrayList<>();
            boolean room = false;
            for (RtsState.BuildingInfo other : client.selectedBuildingList()) {
                RtsState.BuildingTypeInfo otherType = state.buildingType(other.type);
                if (other.complete && otherType != null && otherType.trains.contains(kindId)) {
                    trainers.add(other);
                    room |= other.queue.size() < 10;
                }
            }
            // A team has one of each hero at a time, and may train him again once he falls.
            boolean heroTaken = kind.hero && state.hasUnitOf(team, kindId);
            // A horse is no unit, and needs no room.
            boolean full = !kind.animal && teamFull;
            int waiting = 0;
            for (RtsState.BuildingInfo trainer : trainers) {
                for (String queued : trainer.queue) {
                    if (queued.equals(kindId)) {
                        waiting++;
                    }
                }
            }
            Widget train = square(
                ActionIcons.face(team.faction, kindId),
                kindId,
                kind.name + " ("
                    + RtsState.costText(kind.cost)
                    + ")\n"
                    + (heroTaken ? kind.name + " is already with your team; train him again once he falls"
                        : full
                            ? (team.population < state.game.unitsPerTeam ? "Build houses to house more units"
                                : "Your team has the most units it can have")
                            : kind.hero ? "Train " + kind.name + ", a hero; your team has one at a time"
                                : "Train a " + kind.name.toLowerCase()
                                    + "; shift-click for 5"
                                    + (trainers.size() > 1 ? ", shared out over the " + trainers.size() + " selected"
                                        : "")),
                shift -> trainSpread(
                    trainers,
                    kindId,
                    kind.hero ? kind.name : kind.name.toLowerCase(),
                    kind.hero ? 1 : shift ? 5 : 1),
                !heroTaken && !full && RtsState.canAfford(team, kind.cost) && room);
            train.badge = waiting > 0 ? String.valueOf(waiting) : null;
            actions.add(train);
        }
        if (!type.trains.isEmpty()) {
            Integer number = client.rallyGroups.get(building.id);
            boolean rallied = number != null || !Double.isNaN(building.rallyX);
            actions.add(
                label(
                    number != null ? "New units join group " + number
                        : rallied ? "Rally point set" : "Right-click to set a rally point",
                    0xC8C8C8));
            // Training over and over: off, then each kind it trains in turn, then off again.
            List<String> repeatable = new ArrayList<>();
            for (String kindId : type.trains) {
                RtsState.UnitKindInfo kind = state.unitKind(kindId);
                if (kind != null && !kind.hero && kind.trainableBy(team.faction)) {
                    repeatable.add(kindId);
                }
            }
            if (!repeatable.isEmpty()) {
                int at = building.autoTrain == null ? -1 : repeatable.indexOf(building.autoTrain);
                String next = at + 1 < repeatable.size() ? repeatable.get(at + 1) : null;
                RtsState.UnitKindInfo current = building.autoTrain == null ? null : state.unitKind(building.autoTrain);
                RtsState.UnitKindInfo following = next == null ? null : state.unitKind(next);
                actions.add(
                    button(
                        "Auto: " + (current == null ? "off" : current.name.toLowerCase()),
                        "Keeps training one more whenever the queue runs empty, while your team can pay\nClick for "
                            + (following == null ? "off" : following.name.toLowerCase()),
                        shift -> {
                            List<RtsState.BuildingInfo> trainers = new ArrayList<>();
                            for (RtsState.BuildingInfo trainer : selectedTrainers()) {
                                RtsState.BuildingTypeInfo trainerType = state.buildingType(trainer.type);
                                if (next == null || trainerType != null && trainerType.trains.contains(next)) {
                                    trainers.add(trainer);
                                }
                            }
                            JsonObject body = buildingsBody(trainers);
                            if (next != null) {
                                body.addProperty("unit", next);
                            }
                            client.command(
                                "buildings.auto",
                                body,
                                reply -> client.showToast(
                                    following == null ? "Stopped training over and over"
                                        : "Training " + following.name.toLowerCase() + "s over and over",
                                    false));
                        },
                        true));
            }
            if (rallied) {
                actions.add(button("Clear rally", "New units stay at the door", shift -> {
                    List<RtsState.BuildingInfo> trainers = selectedTrainers();
                    for (RtsState.BuildingInfo trainer : trainers) {
                        client.rallyGroups.remove(trainer.id);
                    }
                    client.command("buildings.rally", buildingsBody(trainers), null);
                }, true));
            }
        }
        if (!building.queue.isEmpty()) {
            // The queue, the one in training first; clicking one takes it off and gives back what it cost.
            actions.add(label("Training", 0xC8C8C8));
            for (int i = 0; i < building.queue.size(); i++) {
                String kindId = building.queue.get(i);
                RtsState.UnitKindInfo kind = state.unitKind(kindId);
                String name = kind == null ? kindId : kind.hero ? kind.name : kind.name.toLowerCase();
                int index = i;
                Widget queued = square(
                    ActionIcons.face(team.faction, kindId),
                    kindId,
                    (i == 0 ? "Training " + name + ", " + (int) Math.floor(100 * building.progress) + "%"
                        : "Waiting to train " + name) + "\nClick to cancel and get back what it cost",
                    shift -> cancel(building, index),
                    true);
                queued.w = queued.h = 16;
                if (i == 0) {
                    queued.bar = building.progress;
                }
                actions.add(queued);
            }
        }
        if (building.research != null) {
            String research = building.research;
            Widget researching = square(
                researchIcon(research),
                "level".equals(research) ? "level" : "upgrade",
                researchName(research) + ", "
                    + (int) Math.floor(100 * building.researched)
                    + "%\nClick to cancel and get back what it cost",
                shift -> cancel(building, -1),
                true);
            researching.bar = building.researched;
            actions.add(label("Researching", 0xC8C8C8));
            actions.add(researching);
            return;
        }
        RtsState.LevelInfo next = type.levelUps.get(building.level + 1);
        if (next != null) {
            boolean center = "town_hall".equals(building.type);
            boolean centerReady = center || team.age > building.level;
            actions.add(
                square(
                    "level",
                    "level",
                    (center ? "Advance to " + ageName(building.level + 1) : "Level " + (building.level + 1)) + " ("
                        + RtsState.costText(next.cost)
                        + ")\n"
                        + (centerReady ? "Takes " + next.seconds + "s; every building takes on the new age's look"
                            : "Your town has to reach " + ageName(building.level + 1) + " first"),
                    shift -> {
                        JsonObject body = new JsonObject();
                        body.addProperty("building", building.id);
                        client.command("buildings.upgrade", body, reply -> client.showToast("Going up a level", false));
                    },
                    centerReady && RtsState.canAfford(team, next.cost)));
        }
        for (String upgradeId : type.researches) {
            RtsState.UpgradeInfo upgrade = state.upgrade(upgradeId);
            // Ones that need another upgrade first only show up once the team has it.
            if (upgrade == null || team.upgrades.contains(upgradeId)
                || !upgrade.requires.isEmpty() && !team.upgrades.contains(upgrade.requires)) {
                continue;
            }
            boolean busy = state.isResearching(team, upgradeId);
            boolean levelOk = team.age >= upgrade.level;
            actions.add(
                square(
                    ActionIcons.upgrade(upgradeId),
                    "upgrade",
                    upgrade.name + " ("
                        + RtsState.costText(upgrade.cost)
                        + ")\n"
                        + (!levelOk ? "Needs " + ageName(upgrade.level)
                            : busy ? "Being researched" : upgrade.description + "; takes " + upgrade.seconds + "s"),
                    shift -> {
                        JsonObject body = new JsonObject();
                        body.addProperty("building", building.id);
                        body.addProperty("upgrade", upgradeId);
                        client.command(
                            "buildings.research",
                            body,
                            reply -> client.showToast("Researching " + upgrade.name.toLowerCase(), false));
                    },
                    levelOk && !busy && RtsState.canAfford(team, upgrade.cost)));
        }
    }

    /** Takes a unit off a building's training queue, or with index -1 stops its research, getting the cost back. */
    private void cancel(RtsState.BuildingInfo building, int index) {
        JsonObject body = new JsonObject();
        body.addProperty("building", building.id);
        if (index < 0) {
            body.addProperty("research", true);
        } else {
            body.addProperty("index", index);
        }
        client.command("buildings.cancel", body, reply -> client.showToast("Cancelled", false));
    }

    private static String researchIcon(String research) {
        return "level".equals(research) ? "level" : ActionIcons.upgrade(research);
    }

    /**
     * A square button showing an icon, with what it does in its tooltip. The fallback is one of the small icons, for
     * when actions.png has no icon of that name.
     */
    private Widget square(String icon, String fallback, String tip, Consumer<Boolean> action, boolean enabled) {
        Widget widget = new Widget();
        widget.square = ActionIcons.has(icon) ? icon : null;
        widget.icon = fallback;
        widget.tip = tip;
        widget.action = action;
        widget.enabled = enabled;
        widget.w = SQUARE;
        widget.h = SQUARE;
        return widget;
    }

    private Widget withIcon(Widget widget, String icon) {
        widget.icon = icon;
        widget.w += 13;
        return widget;
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
            if (!row.isEmpty() && x + widget.w > width - left - minimapSize() - 10) {
                rows.add(row);
                row = new ArrayList<>();
                x = left;
            }
            row.add(widget);
            x += widget.w + gap;
        }
        rows.add(row);
        // Each row is as tall as its tallest widget, the rest centred on it; the last row's bottom is where a row of
        // ordinary buttons at y would end.
        int[] heights = new int[rows.size()];
        int total = 0;
        for (int i = 0; i < rows.size(); i++) {
            heights[i] = 14;
            for (Widget widget : rows.get(i)) {
                heights[i] = Math.max(heights[i], widget.h);
            }
            total += heights[i] + (i > 0 ? 4 : 0);
        }
        int top = y + 14 - total;
        int rowY = top;
        for (int i = 0; i < rows.size(); i++) {
            x = left;
            for (Widget widget : rows.get(i)) {
                widget.x = x;
                widget.y = rowY + (heights[i] - widget.h) / 2;
                widget.rowTop = rowY - 3;
                widget.rowBottom = rowY + heights[i] + 3;
                x += widget.w + gap;
                widgets.add(widget);
            }
            drawRect(left - 4, rowY - 3, x - gap + 4, rowY + heights[i] + 3, PANEL);
            rowY += heights[i] + 4;
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
        if (widget.action != null && (widget.square != null || widget.text == null)) {
            drawSquare(widget, gx, gy);
            return;
        }
        int textX = widget.x + (widget.action == null ? 2 : 5);
        if (widget.icon != null) {
            drawIcon(widget.icon, widget.x + (widget.action == null ? 1 : 3), widget.y + 1, 12);
            textX += 13;
        }
        if (widget.action == null) {
            fontRendererObj.drawStringWithShadow(widget.text, textX, widget.y + 3, widget.color);
            return;
        }
        boolean hover = widget.enabled && widget.contains(gx, gy);
        int background = !widget.enabled ? 0xC0303030 : hover ? 0xE0506070 : 0xE0384450;
        drawRect(widget.x, widget.y, widget.x + widget.w, widget.y + widget.h, background);
        drawOutline(widget.x, widget.y, widget.x + widget.w, widget.y + widget.h, hover ? 0xFFA0B4C8 : 0xFF5A6A78);
        if (widget.icon != null) {
            drawIcon(widget.icon, widget.x + 3, widget.y + 1, 12);
        }
        fontRendererObj.drawStringWithShadow(
            widget.text,
            textX,
            widget.y + 3,
            widget.enabled ? widget.color & 0xFFFFFF : 0x808080);
    }

    private void drawSquare(Widget widget, int gx, int gy) {
        boolean hover = widget.contains(gx, gy);
        int x0 = widget.x;
        int y0 = widget.y;
        int x1 = widget.x + widget.w;
        int y1 = widget.y + widget.h;
        drawRect(x0, y0, x1, y1, 0xFF000000);
        if (widget.square == null || !ActionIcons.draw(widget.square, x0 + 1, y0 + 1, widget.w - 2)) {
            drawIcon(widget.icon, x0 + 2, y0 + 2, widget.w - 4);
        }
        if (!widget.enabled) {
            drawRect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xA0000000);
        } else if (hover) {
            drawRect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0x30FFFFFF);
        }
        if (!Double.isNaN(widget.bar)) {
            drawRect(x0 + 1, y1 - 3, x1 - 1, y1 - 1, 0xC0000000);
            drawRect(
                x0 + 1,
                y1 - 3,
                x0 + 1 + (int) Math.round((widget.w - 2) * Math.max(0, Math.min(1, widget.bar))),
                y1 - 1,
                GREEN);
        }
        if (widget.lit) {
            drawRect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0x40FFE080);
            drawOutline(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFFFFD040);
        }
        drawOutline(
            x0,
            y0,
            x1,
            y1,
            widget.lit ? 0xFFFFD040 : hover && widget.enabled ? 0xFFE0E8F0 : widget.enabled ? 0xFF7A8A98 : 0xFF404850);
        if (widget.badge != null) {
            fontRendererObj.drawStringWithShadow(
                widget.badge,
                x1 - 1 - fontRendererObj.getStringWidth(widget.badge),
                y1 - 9,
                0xFFFFFF);
        }
    }

    private void drawOutline(int x0, int y0, int x1, int y1, int color) {
        drawRect(x0, y0, x1, y0 + 1, color);
        drawRect(x0, y1 - 1, x1, y1, color);
        drawRect(x0, y0, x0 + 1, y1, color);
        drawRect(x1 - 1, y0, x1, y1, color);
    }

    /** Whether a point is on a panel rather than over the world. */
    private boolean overPanel(int gx, int gy) {
        int size = minimapSize();
        if (state().game != null && gx >= width - size - 8 && gy >= height - size - 8) {
            return true;
        }
        if (gy >= barTop - 3 && !widgets.isEmpty()) {
            for (Widget widget : widgets) {
                if (gy >= widget.rowTop && gy < widget.rowBottom && gx >= 0 && gx < width) {
                    int left = Integer.MAX_VALUE;
                    int right = Integer.MIN_VALUE;
                    for (Widget other : widgets) {
                        if (other.rowTop == widget.rowTop) {
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
