package com.warofthegods.client.rts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.warofthegods.Config;
import com.warofthegods.game.WallLine;
import com.warofthegods.rts.RtsNetwork;
import com.warofthegods.world.GameWorld;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The in-game RTS view on the client. A key (V unless rebound) asks the server to let the player command from
 * above; once it agrees, Minecraft renders from an {@link RtsCamera} instead of the player and {@link GuiRts} takes
 * the mouse and keyboard. Everything shown comes from the same game and live state messages the web map gets, and
 * every order goes out as the same commands.
 */
public final class RtsClient {

    private static RtsClient instance;

    public final KeyBinding toggleKey = new KeyBinding(
        "key.warofthegods.rts",
        Keyboard.KEY_V,
        "key.categories.warofthegods");
    public final KeyBinding teamsKey = new KeyBinding(
        "key.warofthegods.teams",
        Keyboard.KEY_B,
        "key.categories.warofthegods");
    public final RtsState state = new RtsState();
    public final RtsWorldRenderer worldRenderer = new RtsWorldRenderer(this);

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Queue<String> inbox = new ConcurrentLinkedQueue<>();
    private final Map<Integer, Consumer<JsonObject>> pending = new HashMap<>();
    private final Map<Entity, Double> renderWeights = new WeakHashMap<>();
    /** Players commanding from the RTS view, who hover unseen; told to every client, in the view or not. */
    private final Set<String> commanders = new HashSet<>();
    private int nextCommandId = 1;
    private int enterId = -1;

    /** Long enough that the night vision never starts to flicker, as it does when about to run out. */
    private static final int NIGHT_VISION_TICKS = 1000000;

    private boolean active;
    private boolean nightVision;
    private boolean entering;
    private RtsCamera camera;
    private float savedFov;
    private int savedThirdPerson;
    /** Draws no clouds; the world's cloud renderer while the view is open. */
    private static final IRenderHandler NO_CLOUDS = new IRenderHandler() {

        @Override
        public void render(float partialTicks, WorldClient world, Minecraft mc) {}
    };
    private long lastFrame;
    private double sentX = Double.NaN;
    private double sentZ = Double.NaN;
    private int ticks;
    private int lastSentTick;

    /** Ids of the viewer's units that orders go to. */
    public final Set<String> selected = new LinkedHashSet<>();
    /** Id of the viewer's building whose controls the bar shows, or 0. */
    public int selectedBuilding;
    /** Ids of every selected building, selectedBuilding among them. */
    public final Set<Integer> selectedBuildings = new LinkedHashSet<>();
    /** Id of the shooting building under the mouse, whose range shows, or 0. */
    public int hoverBuilding;
    /** Control groups by number: the units in each, and the buildings. */
    public final Map<Integer, Group> groups = new HashMap<>();
    /** The control group each building's new units join, by building id. */
    public final Map<Integer, Integer> rallyGroups = new HashMap<>();
    /** Ids of the viewer's units seen so far, to spot new ones. */
    private final Set<String> knownUnits = new HashSet<>();
    private boolean knownReady;

    /** A control group: units and buildings picked together with a number key. */
    public static final class Group {

        public final Set<String> units = new LinkedHashSet<>();
        public final Set<Integer> buildings = new LinkedHashSet<>();

        public boolean isEmpty() {
            return units.isEmpty() && buildings.isEmpty();
        }
    }

    /** Whether workers wait for two corners of an area to light up with torches. */
    public boolean lighting;
    /** The first corner of that area, x, y, z, once clicked; null before. */
    public double[] lightCorner;
    final Minimap minimap = new Minimap();
    /** How units line up where they are sent: box, line or wedge. */
    public String formation = "box";
    /** The building being placed, or null. */
    public Placing placing;
    /** The last order's marker: x, y, z, red, green, blue, and when it was given. */
    public double[] orderMark;
    public long orderMarkTime;

    private String toast;
    private boolean toastBad;
    private long toastTime;

    /** A building being placed: which type, turned how far, centred on which block (cx is NaN until known). */
    public static final class Placing {

        public final String type;
        public int rotation;
        public int cx = Integer.MIN_VALUE;
        public int cz;
        /** For a wall, where it starts once the first click has said; null before. */
        public int[] start;

        Placing(String type, int rotation) {
            this.type = type;
            this.rotation = rotation;
        }
    }

    public static RtsClient get() {
        return instance;
    }

    public static void init() {
        instance = new RtsClient();
        ClientRegistry.registerKeyBinding(instance.toggleKey);
        ClientRegistry.registerKeyBinding(instance.teamsKey);
        FMLCommonHandler.instance()
            .bus()
            .register(instance);
        MinecraftForge.EVENT_BUS.register(instance);
    }

    public boolean isActive() {
        return active;
    }

    public RtsCamera getCamera() {
        return camera;
    }

    /** A message from the server; called on a Netty thread. */
    public void receive(String json) {
        inbox.add(json);
    }

    // ---------------------------------------------------------------- talking to the server

    /** Sends a command; the reply goes to onReply, or an error to the toast. */
    public void command(String type, JsonObject body, Consumer<JsonObject> onReply) {
        int id = nextCommandId++;
        body.addProperty("type", type);
        body.addProperty("id", id);
        if (onReply != null) {
            pending.put(id, onReply);
        } else {
            pending.put(id, reply -> {});
        }
        RtsNetwork.sendToServer(body.toString());
    }

    private void handle(String json) {
        JsonObject message;
        try {
            message = new JsonParser().parse(json)
                .getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        String type = message.has("type") ? message.get("type")
            .getAsString() : "";
        JsonElement replyTo = message.get("replyTo");
        if (replyTo != null && replyTo.isJsonPrimitive()) {
            Consumer<JsonObject> callback = pending.remove(replyTo.getAsInt());
            if ("error".equals(type)) {
                showToast(
                    message.get("error")
                        .getAsString(),
                    true);
                if (replyTo.getAsInt() == enterId) {
                    entering = false;
                    if (mc.thePlayer != null) {
                        mc.thePlayer.addChatMessage(
                            new ChatComponentText(
                                EnumChatFormatting.RED + message.get("error")
                                    .getAsString()));
                    }
                }
                return;
            }
            if (callback != null) {
                callback.accept(message);
            }
            return;
        }
        switch (type) {
            case "game":
                state.setGame(message.get("game"));
                selected.clear();
                selectBuildings(new ArrayList<>(), 0);
                placing = null;
                break;
            case "state":
                state.setState(message);
                joinRallyGroups();
                tidySelection();
                break;
            case "rts.left":
                stop();
                break;
            case "rts.commanders":
                commanders.clear();
                for (JsonElement id : message.getAsJsonArray("ids")) {
                    commanders.add(id.getAsString());
                }
                break;
            default:
        }
    }

    /** New units from a building that rallies to a control group join it and walk over to it. */
    private void joinRallyGroups() {
        RtsState.TeamInfo team = state.myTeam();
        List<RtsState.UnitInfo> fresh = new ArrayList<>();
        for (RtsState.UnitInfo unit : state.units.values()) {
            if (team != null && unit.team.equals(team.id) && knownUnits.add(unit.id)) {
                fresh.add(unit);
            }
        }
        if (!knownReady) {
            knownReady = true;
            return;
        }
        for (RtsState.UnitInfo unit : fresh) {
            Integer number = rallyGroups.get(unit.from);
            if (number == null) {
                continue;
            }
            Group group = groups.computeIfAbsent(number, n -> new Group());
            double x = 0;
            double z = 0;
            int others = 0;
            for (String id : group.units) {
                RtsState.UnitInfo other = state.units.get(id);
                if (other != null) {
                    x += other.x;
                    z += other.z;
                    others++;
                }
            }
            group.units.add(unit.id);
            if (others > 0) {
                JsonObject body = new JsonObject();
                JsonArray ids = new JsonArray();
                ids.add(new JsonPrimitive(unit.id));
                body.add("units", ids);
                body.addProperty("x", x / others);
                body.addProperty("z", z / others);
                body.addProperty("formation", formation);
                command("units.move", body, null);
            }
        }
    }

    /** The control group a unit is in, or null. */
    public Integer groupOf(String unitId) {
        for (Map.Entry<Integer, Group> entry : groups.entrySet()) {
            Group group = entry.getValue();
            if (group.units.contains(unitId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Dead units, and units of a team the viewer has left, drop out of the selection, as do lost buildings. */
    private void tidySelection() {
        for (Group group : groups.values()) {
            group.units.removeIf(id -> !state.units.containsKey(id));
            group.buildings.removeIf(id -> !state.buildings.containsKey(id));
        }
        RtsState.TeamInfo team = state.myTeam();
        selected.removeIf(id -> {
            RtsState.UnitInfo unit = state.units.get(id);
            return unit == null || team == null || !unit.team.equals(team.id);
        });
        List<Integer> kept = new ArrayList<>();
        for (int id : selectedBuildings) {
            RtsState.BuildingInfo building = state.buildings.get(id);
            if (building != null && team != null && building.team.equals(team.id)) {
                kept.add(id);
            }
        }
        if (kept.size() != selectedBuildings.size()) {
            selectBuildings(kept, selectedBuilding);
        }
        if (placing != null && selectedWorkers().isEmpty()) {
            placing = null;
        }
        if (lighting && selectedWorkers().isEmpty()) {
            stopLighting();
        }
    }

    public Set<String> selectedWorkers() {
        Set<String> workers = new LinkedHashSet<>();
        for (String id : selected) {
            RtsState.UnitInfo unit = state.units.get(id);
            if (unit != null && unit.isWorker()) {
                workers.add(id);
            }
        }
        return workers;
    }

    /** Workers wait for the corners of an area to put torches in. */
    public void startLighting() {
        placing = null;
        lighting = true;
        lightCorner = null;
    }

    public void stopLighting() {
        lighting = false;
        lightCorner = null;
    }

    public void showToast(String text, boolean bad) {
        toast = text;
        toastBad = bad;
        toastTime = System.currentTimeMillis();
    }

    /** The toast to show now, or null. */
    public String getToast() {
        return toast != null && System.currentTimeMillis() - toastTime < 3000 ? toast : null;
    }

    public boolean isToastBad() {
        return toastBad;
    }

    // ---------------------------------------------------------------- entering and leaving

    public void requestEnter() {
        if (active || entering || mc.thePlayer == null) {
            return;
        }
        if (mc.thePlayer.dimension != GameWorld.dimensionId()) {
            mc.thePlayer.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GOLD + "The RTS view opens on the game map. Use /rts spawn to go there."));
            return;
        }
        entering = true;
        enterId = nextCommandId;
        command("rts.enter", new JsonObject(), this::start);
    }

    private void start(JsonObject reply) {
        entering = false;
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        JsonObject me = reply.getAsJsonObject("me");
        state.me = me == null ? mc.thePlayer.getUniqueID()
            .toString()
            : me.get("uuid")
                .getAsString();
        camera = new RtsCamera(mc.theWorld);
        camera.focusX = mc.thePlayer.posX;
        camera.focusY = mc.thePlayer.boundingBox.minY;
        camera.focusZ = mc.thePlayer.posZ;
        camera.fov = Config.rtsCameraFov;
        camera.place();
        savedFov = mc.gameSettings.fovSetting;
        savedThirdPerson = mc.gameSettings.thirdPersonView;
        mc.gameSettings.fovSetting = camera.fov;
        mc.gameSettings.thirdPersonView = 0;
        // No clouds between the camera and the map.
        mc.theWorld.provider.setCloudRenderer(NO_CLOUDS);
        mc.renderViewEntity = camera;
        // Commanders see in the dark. Only this client knows, and only while the view is open.
        if (!mc.thePlayer.isPotionActive(Potion.nightVision)) {
            mc.thePlayer.addPotionEffect(new PotionEffect(Potion.nightVision.id, NIGHT_VISION_TICKS, 0, true));
            nightVision = true;
        }
        active = true;
        lastFrame = System.nanoTime();
        sentX = camera.focusX;
        sentZ = camera.focusZ;
        mc.displayGuiScreen(new GuiRts(this));
    }

    /** Leaves the RTS view and tells the server, which puts the player back where they were. */
    public void leave() {
        if (active) {
            command("rts.leave", new JsonObject(), null);
        }
        stop();
    }

    private void stop() {
        entering = false;
        if (!active) {
            return;
        }
        active = false;
        if (mc.thePlayer != null) {
            mc.renderViewEntity = mc.thePlayer;
        }
        mc.gameSettings.fovSetting = savedFov;
        if (nightVision && mc.thePlayer != null) {
            mc.thePlayer.removePotionEffectClient(Potion.nightVision.id);
        }
        nightVision = false;
        mc.gameSettings.thirdPersonView = savedThirdPerson;
        if (mc.theWorld != null && mc.theWorld.provider.getCloudRenderer() == NO_CLOUDS) {
            mc.theWorld.provider.setCloudRenderer(null);
        }
        for (Map.Entry<Entity, Double> entry : renderWeights.entrySet()) {
            entry.getKey().renderDistanceWeight = entry.getValue();
        }
        renderWeights.clear();
        camera = null;
        placing = null;
        stopLighting();
        if (mc.currentScreen instanceof GuiRts) {
            mc.displayGuiScreen(null);
        }
    }

    // ---------------------------------------------------------------- events

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (teamsKey.isPressed() && mc.currentScreen == null && mc.thePlayer != null) {
            mc.displayGuiScreen(new GuiTeams(this));
            return;
        }
        if (toggleKey.isPressed() && mc.currentScreen == null) {
            if (active) {
                leave();
            } else {
                requestEnter();
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        String message;
        while ((message = inbox.poll()) != null) {
            handle(message);
        }
        if (mc.theWorld == null || mc.thePlayer == null) {
            if (active) {
                stop();
            }
            pending.clear();
            commanders.clear();
            entering = false;
            return;
        }
        if (!active) {
            return;
        }
        ticks++;
        // Dead: the death screen takes over.
        if (camera.worldObj != mc.theWorld || mc.thePlayer.dimension != GameWorld.dimensionId()
            || mc.thePlayer.getHealth() <= 0) {
            stop();
            return;
        }
        // Back to the RTS screen after chat or anything else that took the screen.
        if (mc.currentScreen == null) {
            mc.displayGuiScreen(new GuiRts(this));
        }
        // Units, players and hostile mobs are drawn as far as the camera stands back from what it looks at; animals,
        // items and the like only as far as usual, which saves drawing a crowd nobody can make out.
        double weight = Math.max(1, Math.min(3, (camera.distance + 32) / 64));
        for (Object object : mc.theWorld.loadedEntityList) {
            Entity entity = (Entity) object;
            if (!(entity instanceof EntityLivingBase) || entity instanceof EntityAnimal
                || entity instanceof EntityAmbientCreature
                || entity instanceof EntityWaterMob) {
                continue;
            }
            Double original = renderWeights.get(entity);
            if (original == null) {
                original = entity.renderDistanceWeight;
                renderWeights.put(entity, original);
            }
            entity.renderDistanceWeight = Math.max(original, weight);
        }
        // The player hovers where the camera looks, so that what it looks at is loaded.
        double dx = camera.focusX - sentX;
        double dz = camera.focusZ - sentZ;
        if (dx * dx + dz * dz > 36 && ticks - lastSentTick >= 4) {
            lastSentTick = ticks;
            sentX = camera.focusX;
            sentZ = camera.focusZ;
            JsonObject look = new JsonObject();
            look.addProperty("x", camera.focusX);
            look.addProperty("z", camera.focusZ);
            command("rts.look", look, null);
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !active || camera == null) {
            return;
        }
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        if (mc.currentScreen instanceof GuiRts) {
            moveCamera(dt);
        }
        camera.animate(dt);
        double ground = groundHeight(camera.focusX, camera.focusZ);
        camera.focusY += (ground - camera.focusY) * Math.min(1, dt * 6);
        camera.place();
        mc.renderViewEntity = camera;
    }

    /** WASD or the arrow keys, and the mouse at the edge of the window, move the camera. */
    private void moveCamera(double dt) {
        double right = 0;
        double ahead = 0;
        if (down(mc.gameSettings.keyBindForward.getKeyCode()) || down(Keyboard.KEY_UP)) {
            ahead += 1;
        }
        if (down(mc.gameSettings.keyBindBack.getKeyCode()) || down(Keyboard.KEY_DOWN)) {
            ahead -= 1;
        }
        if (down(mc.gameSettings.keyBindRight.getKeyCode()) || down(Keyboard.KEY_RIGHT)) {
            right += 1;
        }
        if (down(mc.gameSettings.keyBindLeft.getKeyCode()) || down(Keyboard.KEY_LEFT)) {
            right -= 1;
        }
        if (Config.rtsEdgeScroll && Display.isActive() && Mouse.isInsideWindow()) {
            int margin = Math.max(2, mc.displayWidth / 200);
            int mx = Mouse.getX();
            int my = Mouse.getY();
            if (mx <= margin) {
                right -= 1;
            } else if (mx >= mc.displayWidth - 1 - margin) {
                right += 1;
            }
            if (my <= margin) {
                ahead -= 1;
            } else if (my >= mc.displayHeight - 1 - margin) {
                ahead += 1;
            }
        }
        if (right != 0 || ahead != 0) {
            double speed = Math.max(8, camera.distance * 0.9) * dt;
            if (down(Keyboard.KEY_LSHIFT) || down(Keyboard.KEY_RSHIFT)) {
                speed *= 2.5;
            }
            camera.pan(right * speed, ahead * speed);
            clampFocus();
        }
    }

    /** Keeps the focus over the map and its margin. */
    public void clampFocus() {
        RtsState.GameInfo game = state.game;
        if (game == null || camera == null) {
            return;
        }
        int margin = 16;
        camera.focusX = Math.max(game.minX - margin, Math.min(game.maxX + 1 + margin, camera.focusX));
        camera.focusZ = Math.max(game.minZ - margin, Math.min(game.maxZ + 1 + margin, camera.focusZ));
    }

    /** Farthest the camera may stand back: within the distance the client draws the world. */
    public double maxDistance() {
        return Math.max(24, Math.min(128, mc.gameSettings.renderDistanceChunks * 16 * 0.75));
    }

    /** The y on top of the ground at x, z, as the client has it, or above sea level where it is not loaded. */
    public double groundHeight(double x, double z) {
        int bx = MathHelper.floor_double(x);
        int bz = MathHelper.floor_double(z);
        if (mc.theWorld != null && mc.theWorld.getChunkProvider()
            .chunkExists(bx >> 4, bz >> 4)) {
            int top = mc.theWorld.getHeightValue(bx, bz);
            if (top > 0) {
                return top;
            }
        }
        return state.game == null ? 64 : state.game.seaLevel + 1;
    }

    private static boolean down(int key) {
        return key > 0 && key < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(key);
    }

    public void openChat(String text) {
        mc.displayGuiScreen(new GuiChat(text));
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (active && camera != null) {
            worldRenderer.render(event.partialTicks);
        }
    }

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        if (active) {
            event.setCanceled(true);
        }
    }

    /** The hotbar, crosshair and bars are for playing in person; chat and the debug screen stay. */
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        if (!active) {
            return;
        }
        switch (event.type) {
            case HOTBAR:
            case CROSSHAIRS:
            case HEALTH:
            case ARMOR:
            case FOOD:
            case EXPERIENCE:
            case AIR:
            case JUMPBAR:
            case HEALTHMOUNT:
            case HELMET:
            case PORTAL:
                event.setCanceled(true);
                break;
            default:
        }
    }

    /** No fog: the camera stands back, and fog would hide what it looks at. */
    @SubscribeEvent
    public void onRenderFog(EntityViewRenderEvent.RenderFogEvent event) {
        if (active && event.fogMode >= 0) {
            GL11.glFogf(GL11.GL_FOG_START, event.farPlaneDistance * 4);
            GL11.glFogf(GL11.GL_FOG_END, event.farPlaneDistance * 5);
        }
    }

    /** Flying and potions would otherwise widen the view, and picking with the mouse must match what is drawn. */
    @SubscribeEvent
    public void onFovUpdate(FOVUpdateEvent event) {
        if (active) {
            event.newfov = 1;
        }
    }

    /**
     * Commanders hover over what they look at, the viewer too when in the view, and are not drawn there: not even
     * their armor and what they hold, which vanilla draws on the invisible. Their bodies, drawn like any entity, show
     * where they are.
     */
    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (active && event.entityPlayer == mc.thePlayer || commanders.contains(
            event.entityPlayer.getUniqueID()
                .toString())) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- placing and selecting

    public void startPlacing(String type) {
        stopLighting();
        placing = new Placing(type, placing == null ? 0 : placing.rotation);
    }

    public void stopPlacing() {
        placing = null;
    }

    public void selectBuilding(int id) {
        selectBuildings(Collections.singletonList(id), id);
        selected.clear();
    }

    /** Selects these buildings; the bar shows the controls of primary if it is among them, else of the first. */
    public void selectBuildings(Collection<Integer> ids, int primary) {
        selectedBuildings.clear();
        selectedBuildings.addAll(ids);
        selectedBuilding = selectedBuildings.contains(primary) ? primary
            : selectedBuildings.isEmpty() ? 0
                : selectedBuildings.iterator()
                    .next();
    }

    /** The selected buildings that still stand. */
    public List<RtsState.BuildingInfo> selectedBuildingList() {
        List<RtsState.BuildingInfo> buildings = new ArrayList<>();
        for (int id : selectedBuildings) {
            RtsState.BuildingInfo building = state.buildings.get(id);
            if (building != null) {
                buildings.add(building);
            }
        }
        return buildings;
    }

    /** After the selection changed: units and buildings are not selected together. */
    public void changedSelection() {
        if (!selected.isEmpty()) {
            selectBuildings(Collections.emptyList(), 0);
        }
        if (placing != null && selectedWorkers().isEmpty()) {
            placing = null;
        }
    }

    public void mark(double x, double y, double z, boolean attack) {
        orderMark = attack ? new double[] { x, y, z, 1, 0.3, 0.25 } : new double[] { x, y, z, 0.45, 1, 0.5 };
        orderMarkTime = System.currentTimeMillis();
    }

    /** Where the footprint of the building being placed goes and whether it fits there. */
    public static final class Footprint {

        public int x;
        public int z;
        public int sizeX;
        public int sizeZ;
        /** The y of the ground's top block, which the building's ground floor replaces. */
        public int y;
        /** The quarter turn the building is drawn with; a town center takes its settlement's. */
        public int rotation;
        public boolean ok;
    }

    /**
     * Where the building being placed goes and whether it fits: inside the map and clear of other buildings, as on
     * the web map. The server also checks the ground is dry and level enough. Null when nothing is being placed.
     */
    public Footprint footprint() {
        RtsState.BuildingTypeInfo type = placing == null ? null : state.buildingType(placing.type);
        if (type == null || placing.cx == Integer.MIN_VALUE || state.game == null || "wall".equals(placing.type)) {
            return null;
        }
        Footprint spot = new Footprint();
        if ("town_hall".equals(placing.type)) {
            // A town center goes on a free settlement, as the settlement lies.
            RtsState.FeatureInfo settlement = state.freeSettlementNear(placing.cx + 0.5, placing.cz + 0.5, 4);
            if (settlement == null) {
                boolean turned = placing.rotation % 2 != 0;
                spot.sizeX = turned ? type.length : type.width;
                spot.sizeZ = turned ? type.width : type.length;
                spot.x = placing.cx - spot.sizeX / 2;
                spot.z = placing.cz - spot.sizeZ / 2;
                spot.y = (int) groundHeight(placing.cx + 0.5, placing.cz + 0.5) - 1;
                spot.rotation = placing.rotation;
                spot.ok = false;
                return spot;
            }
            spot.x = settlement.x;
            spot.z = settlement.z;
            spot.sizeX = settlement.sizeX;
            spot.sizeZ = settlement.sizeZ;
            spot.y = settlement.y;
            spot.rotation = settlement.rotation;
            spot.ok = true;
            return spot;
        }
        spot.rotation = placing.rotation;
        boolean turned = placing.rotation % 2 != 0;
        spot.sizeX = turned ? type.length : type.width;
        spot.sizeZ = turned ? type.width : type.length;
        spot.x = placing.cx - spot.sizeX / 2;
        spot.z = placing.cz - spot.sizeZ / 2;
        RtsState.GameInfo game = state.game;
        spot.ok = spot.x >= game.minX && spot.z >= game.minZ
            && spot.x + spot.sizeX - 1 <= game.maxX
            && spot.z + spot.sizeZ - 1 <= game.maxZ;
        int gap = game.buildingGap;
        for (RtsState.BuildingInfo b : state.buildings.values()) {
            if (spot.x < b.x + b.sizeX + gap && b.x < spot.x + spot.sizeX + gap
                && spot.z < b.z + b.sizeZ + gap
                && b.z < spot.z + spot.sizeZ + gap) {
                spot.ok = false;
            }
        }
        for (RtsState.FeatureInfo f : state.settlements) {
            if (spot.x < f.x + f.sizeX + gap && f.x < spot.x + spot.sizeX + gap
                && spot.z < f.z + f.sizeZ + gap
                && f.z < spot.z + spot.sizeZ + gap) {
                spot.ok = false;
            }
        }
        for (RtsState.FeatureInfo f : state.mines) {
            if (spot.x < f.x + f.sizeX + gap && f.x < spot.x + spot.sizeX + gap
                && spot.z < f.z + f.sizeZ + gap
                && f.z < spot.z + spot.sizeZ + gap) {
                spot.ok = false;
            }
        }
        spot.y = (int) groundHeight(placing.cx + 0.5, placing.cz + 0.5) - 1;
        return spot;
    }

    /** The wall being laid out, as its pieces' footprints, and whether it fits. */
    public static final class WallSpot {

        public final List<Footprint> pieces = new ArrayList<>();
        public final List<String> types = new ArrayList<>();
        public boolean ok;
    }

    /**
     * The wall being laid out, from where it starts to the pointer, or at the pointer before the first click; null
     * when no wall is being laid out. See {@link WallLine}, which the server lays the wall out with too.
     */
    public WallSpot wallSpot() {
        if (placing == null || !"wall".equals(placing.type) || placing.cx == Integer.MIN_VALUE || state.game == null) {
            return null;
        }
        RtsState.TeamInfo team = state.myTeam();
        int[] end = wallEnd(team, placing.cx, placing.cz);
        int[] start = placing.start == null ? end : wallEnd(team, placing.start[0], placing.start[1]);
        List<WallLine.Piece> pieces = WallLine.pieces(start[0], start[1], end[0], end[1], (minX, minZ) -> {
            for (RtsState.BuildingInfo b : state.buildings.values()) {
                if (team != null && b.team.equals(team.id)
                    && ("tower".equals(b.type) || isWall(b.type))
                    && minX < b.x + b.sizeX
                    && b.x < minX + WallLine.SIZE
                    && minZ < b.z + b.sizeZ
                    && b.z < minZ + WallLine.SIZE) {
                    return true;
                }
            }
            return false;
        });
        WallSpot spot = new WallSpot();
        spot.ok = !pieces.isEmpty();
        RtsState.GameInfo game = state.game;
        int size = WallLine.SIZE;
        for (WallLine.Piece piece : pieces) {
            Footprint footprint = new Footprint();
            footprint.x = piece.minX;
            footprint.z = piece.minZ;
            footprint.sizeX = size;
            footprint.sizeZ = size;
            footprint.rotation = piece.rotation;
            footprint.y = (int) groundHeight(piece.centerX(), piece.centerZ()) - 1;
            footprint.ok = piece.minX >= game.minX && piece.minZ >= game.minZ
                && piece.minX + size - 1 <= game.maxX
                && piece.minZ + size - 1 <= game.maxZ;
            for (RtsState.BuildingInfo b : state.buildings.values()) {
                if (piece.minX < b.x + b.sizeX && b.x < piece.minX + size
                    && piece.minZ < b.z + b.sizeZ
                    && b.z < piece.minZ + size) {
                    footprint.ok = false;
                }
            }
            for (List<RtsState.FeatureInfo> features : Arrays.asList(state.settlements, state.mines)) {
                for (RtsState.FeatureInfo f : features) {
                    if (piece.minX < f.x + f.sizeX + 1 && f.x < piece.minX + size + 1
                        && piece.minZ < f.z + f.sizeZ + 1
                        && f.z < piece.minZ + size + 1) {
                        footprint.ok = false;
                    }
                }
            }
            spot.ok &= footprint.ok;
            spot.pieces.add(footprint);
            spot.types.add(piece.type().id);
        }
        return spot;
    }

    /** A wall end at x, z: the middle of the team's tower there, if there is one, else x, z itself. */
    private int[] wallEnd(RtsState.TeamInfo team, int x, int z) {
        for (RtsState.BuildingInfo b : state.buildings.values()) {
            if (team != null && b.team.equals(team.id)
                && "tower".equals(b.type)
                && x >= b.x - 1
                && x < b.x + b.sizeX + 1
                && z >= b.z - 1
                && z < b.z + b.sizeZ + 1) {
                return new int[] { (int) Math.floor(b.x + b.sizeX / 2.0), (int) Math.floor(b.z + b.sizeZ / 2.0) };
            }
        }
        return new int[] { x, z };
    }

    public static boolean isWall(String type) {
        return "wall".equals(type) || "wall_post".equals(type) || "gate".equals(type);
    }

    /**
     * Where a unit, player or mob is drawn: its client entity's position between ticks when the client has it, else
     * its last reported position. As {x, feet y, z, height}.
     */
    public double[] positionOf(RtsState.Thing thing, float partialTicks) {
        Entity entity = thing.eid < 0 || mc.theWorld == null ? null : mc.theWorld.getEntityByID(thing.eid);
        if (entity != null && !entity.isDead) {
            double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
            double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks
                - (entity.posY - entity.boundingBox.minY);
            double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
            return new double[] { x, y, z, entity.height };
        }
        double height = thing instanceof RtsState.MobInfo ? Math.max(0.5, ((RtsState.MobInfo) thing).height) : 1.8;
        return new double[] { thing.x, thing.y, thing.z, height };
    }

    public void focusOn(double x, double z) {
        if (camera != null) {
            camera.focusX = x;
            camera.focusZ = z;
            clampFocus();
        }
    }
}
