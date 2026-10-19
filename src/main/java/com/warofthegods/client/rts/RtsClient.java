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

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.WorldRenderer;
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
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldEvent;
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
import com.warofthegods.game.BridgeLine;
import com.warofthegods.game.WallLine;
import com.warofthegods.rts.RtsNetwork;
import com.warofthegods.rts.TerrainMessage;
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
    private JsonObject lobby;
    private boolean nightVision;
    /** Whether the world is drawn lit by the fog of war, rather than as it is. */
    private boolean lit;
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
    /** Whether the next click sends the selected warriors and archers on an attack move. */
    public boolean attackMoving;
    /** The first corner of that area, x, y, z, once clicked; null before. */
    public double[] lightCorner;
    final Minimap minimap = new Minimap();
    /** What the viewer's team has seen and sees now. */
    final FogOfWar fog = new FogOfWar();
    /** The map's surface beyond the chunks the client has. */
    final LodTerrain terrain = new LodTerrain();
    /** The world's own sky renderer while the view draws the sky and then the map's surface; null for Minecraft's. */
    private IRenderHandler worldSky;
    /** Draws the sky, then the map's surface beyond the chunks the client has, before the world. */
    private final IRenderHandler skyAndTerrain = new IRenderHandler() {

        @Override
        public void render(float partialTicks, WorldClient world, Minecraft mc) {
            world.provider.setSkyRenderer(worldSky);
            try {
                if (worldSky != null) {
                    worldSky.render(partialTicks, world, mc);
                } else {
                    mc.renderGlobal.renderSky(partialTicks);
                }
            } finally {
                world.provider.setSkyRenderer(this);
            }
            if (active && camera != null) {
                terrain.render(RtsClient.this, partialTicks);
            }
        }
    };
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

    /** Some of the map's surface from the server; called on a Netty thread. */
    public void receiveTerrain(TerrainMessage message) {
        terrain.receive(message);
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
            if (message.has("notice")) {
                showToast(
                    message.get("notice")
                        .getAsString(),
                    true);
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
            case "lobby":
                onLobby(message);
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

    /** The new game lobby as the server last told it, or null while it is closed. */
    public JsonObject getLobby() {
        return lobby;
    }

    /** The lobby opened, changed or closed: it comes up when it opens and goes away when it closes. */
    private void onLobby(JsonObject message) {
        boolean open = message.has("open") && message.get("open")
            .getAsBoolean();
        boolean wasOpen = lobby != null;
        lobby = open ? message : null;
        if (open && !wasOpen && !(mc.currentScreen instanceof GuiLobby) && mc.thePlayer != null) {
            if (active) {
                stop();
            }
            mc.displayGuiScreen(new GuiLobby(this));
        } else if (!open && mc.currentScreen instanceof GuiLobby) {
            mc.displayGuiScreen(null);
        } else if (mc.currentScreen instanceof GuiLobby) {
            ((GuiLobby) mc.currentScreen).lobbyChanged();
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
        if (placing != null && selectedBuilders().isEmpty()) {
            placing = null;
        }
        if (lighting && selectedWorkers().isEmpty()) {
            stopLighting();
        }
        if (attackMoving && selectedFighters().isEmpty()) {
            attackMoving = false;
        }
    }

    /** The selected warriors and archers. */
    public Set<String> selectedFighters() {
        Set<String> fighters = new LinkedHashSet<>();
        for (String id : selected) {
            RtsState.UnitInfo unit = state.units.get(id);
            if (unit != null && !unit.isWorker()) {
                fighters.add(id);
            }
        }
        return fighters;
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

    /** The selected workers, or with none of them, the selected dark wizards, who raise stone circles. */
    public Set<String> selectedBuilders() {
        Set<String> builders = selectedWorkers();
        if (builders.isEmpty()) {
            for (String id : selected) {
                RtsState.UnitInfo unit = state.units.get(id);
                if (unit != null && unit.isDarkWizard()) {
                    builders.add(id);
                }
            }
        }
        return builders;
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
        JsonObject enter = new JsonObject();
        // The server sends the map's surface the client does not hold yet.
        if (terrain.mapId() != null) {
            enter.addProperty("terrain", terrain.mapId());
        }
        command("rts.enter", enter, this::start);
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
        worldSky = mc.theWorld.provider.getSkyRenderer();
        mc.theWorld.provider.setSkyRenderer(skyAndTerrain);
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
        if (lit) {
            lit = false;
            mc.renderGlobal.loadRenderers();
        }
        mc.gameSettings.thirdPersonView = savedThirdPerson;
        if (mc.theWorld != null && mc.theWorld.provider.getCloudRenderer() == NO_CLOUDS) {
            mc.theWorld.provider.setCloudRenderer(null);
        }
        if (mc.theWorld != null && mc.theWorld.provider.getSkyRenderer() == skyAndTerrain) {
            mc.theWorld.provider.setSkyRenderer(worldSky);
        }
        worldSky = null;
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
            mc.displayGuiScreen(lobby != null ? new GuiLobby(this) : new GuiTeams(this));
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
        terrain.update();
        if (mc.theWorld == null || mc.thePlayer == null) {
            if (active) {
                stop();
            }
            pending.clear();
            commanders.clear();
            entering = false;
            lobby = null;
            fog.forgetWalked();
            return;
        }
        if (!active) {
            if (mc.thePlayer.dimension == GameWorld.dimensionId()) {
                fog.walk(this, mc.thePlayer.posX, mc.thePlayer.posZ);
            }
            return;
        }
        ticks++;
        fog.update(this);
        light();
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

    /**
     * Farthest the camera may stand back: within the distance the client draws the world, or once the server has sent
     * the map's surface, as far as the config lets it.
     */
    public double maxDistance() {
        double drawn = Math.max(24, Math.min(128, mc.gameSettings.renderDistanceChunks * 16 * 0.75));
        return terrain.isEmpty() ? drawn : Math.max(drawn, Config.rtsMaxZoomOut);
    }

    /**
     * The y on top of the ground at x, z, as the client has it, else as the map's surface the server sent has it, else
     * above sea level.
     */
    public double groundHeight(double x, double z) {
        int bx = MathHelper.floor_double(x);
        int bz = MathHelper.floor_double(z);
        if (isLoaded(bx, bz)) {
            int top = mc.theWorld.getHeightValue(bx, bz);
            if (top > 0) {
                return top;
            }
        }
        int top = terrain.top(bx, bz);
        if (top > 0) {
            return top;
        }
        return state.game == null ? 64 : state.game.seaLevel + 1;
    }

    /** Whether the client has the chunk holding block x, z. */
    public boolean isLoaded(int x, int z) {
        return mc.theWorld != null && !mc.theWorld.getChunkFromChunkCoords(x >> 4, z >> 4)
            .isEmpty();
    }

    /** The y just above the top block at x, z in the map's surface the server sent, or -1 where it is not known. */
    public int surfaceTop(int x, int z) {
        return terrain.top(x, z);
    }

    /**
     * Whether Minecraft draws the thing itself: it is loaded, within its render distance and before the far plane.
     * Others the view marks with a dot.
     */
    public boolean isDrawn(RtsState.Thing thing) {
        Entity entity = thing.eid < 0 || mc.theWorld == null ? null : mc.theWorld.getEntityByID(thing.eid);
        Entity view = mc.renderViewEntity;
        if (entity == null || entity.isDead || view == null) {
            return false;
        }
        double far = mc.gameSettings.renderDistanceChunks * 16 * 2 * 0.95;
        return entity.getDistanceSqToEntity(view) < far * far
            && entity.isInRangeToRender3d(view.posX, view.posY, view.posZ);
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

    /**
     * What moves in the fog of war is not drawn; the viewer's team always sees its own, as they stand in sight. What is
     * drawn is in full light, like the ground it stands on.
     */
    @SubscribeEvent
    public void onRenderLiving(RenderLivingEvent.Pre event) {
        if (active && fog.isOn()
            && event.entity != mc.thePlayer
            && !fog.isVisible(event.entity.posX, event.entity.posZ)) {
            event.setCanceled(true);
        } else if (lit) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        }
    }

    /**
     * The fog of war as light: while there is fog, the world is drawn again lit by what the team has seen, and night
     * vision, which would light it all, is taken off; without fog it is back, and the world is drawn as it is.
     */
    private void light() {
        if (lit != fog.isOn()) {
            lit = fog.isOn();
            mc.renderGlobal.loadRenderers();
        } else if (lit) {
            fog.redrawChanged(mc);
        }
        if (lit && nightVision) {
            mc.thePlayer.removePotionEffectClient(Potion.nightVision.id);
            nightVision = false;
        } else if (!lit && !mc.thePlayer.isPotionActive(Potion.nightVision)) {
            mc.thePlayer.addPotionEffect(new PotionEffect(Potion.nightVision.id, NIGHT_VISION_TICKS, 0, true));
            nightVision = true;
        }
    }

    /** Chunks drawn while the world is lit by the fog of war take their light from it. */
    @SubscribeEvent
    public void onRenderChunk(RenderWorldEvent.Pre event) {
        if (lit && !(event.renderBlocks.blockAccess instanceof FogChunkCache)) {
            WorldRenderer chunk = event.renderer;
            event.renderBlocks.blockAccess = new FogChunkCache(
                chunk.worldObj,
                chunk.posX - 1,
                chunk.posY - 1,
                chunk.posZ - 1,
                chunk.posX + 17,
                chunk.posY + 17,
                chunk.posZ + 17,
                fog);
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
        if (placing != null && selectedBuilders().isEmpty()) {
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
        if (type == null || placing.cx == Integer.MIN_VALUE || state.game == null || isLine(placing.type)) {
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
        if ("quarry".equals(placing.type)) {
            // A quarry goes in the middle of a mined out mine with none on it yet.
            RtsState.FeatureInfo mine = state.freeMineNear(placing.cx + 0.5, placing.cz + 0.5, 4);
            if (mine == null) {
                spot.x = placing.cx - spot.sizeX / 2;
                spot.z = placing.cz - spot.sizeZ / 2;
                spot.y = (int) groundHeight(placing.cx + 0.5, placing.cz + 0.5) - 1;
                spot.ok = false;
                return spot;
            }
            spot.x = mine.x + (mine.sizeX - spot.sizeX) / 2;
            spot.z = mine.z + (mine.sizeZ - spot.sizeZ) / 2;
            spot.y = mine.y;
            spot.ok = true;
            return spot;
        }
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

    /** The wall or bridge being laid out, as its pieces' footprints, and whether it fits. */
    public static final class WallSpot {

        public final List<Footprint> pieces = new ArrayList<>();
        public final List<String> types = new ArrayList<>();
        public boolean ok;
    }

    /**
     * The wall or bridge being laid out, from where it starts to the pointer, or at the pointer before the first
     * click; null when none is being laid out. See {@link WallLine} and {@link BridgeLine}, which the server lays them
     * out with too.
     */
    public WallSpot wallSpot() {
        if (placing != null && BRIDGE.equals(placing.type) && placing.cx != Integer.MIN_VALUE && state.game != null) {
            return bridgeSpot();
        }
        if (placing == null || !"wall".equals(placing.type) || placing.cx == Integer.MIN_VALUE || state.game == null) {
            return null;
        }
        RtsState.TeamInfo team = state.myTeam();
        int[] end = wallEnd(team, placing.cx, placing.cz);
        int[] start = placing.start == null ? end : wallEnd(team, placing.start[0], placing.start[1]);
        List<WallLine.Piece> pieces = WallLine.pieces(start[0], start[1], end[0], end[1], (minX, minZ) -> {
            int[] box = null;
            for (RtsState.BuildingInfo b : state.buildings.values()) {
                if (team != null && b.team.equals(team.id)
                    && ("tower".equals(b.type) || isWall(b.type))
                    && minX < b.x + b.sizeX
                    && b.x < minX + WallLine.SIZE
                    && minZ < b.z + b.sizeZ
                    && b.z < minZ + WallLine.SIZE) {
                    if (box == null) {
                        box = new int[] { b.x, b.z, b.sizeX, b.sizeZ };
                    } else {
                        int x = Math.min(box[0], b.x);
                        int z = Math.min(box[1], b.z);
                        box = new int[] { x, z, Math.max(box[0] + box[2], b.x + b.sizeX) - x,
                            Math.max(box[1] + box[3], b.z + b.sizeZ) - z };
                    }
                }
            }
            return box;
        });
        WallSpot spot = new WallSpot();
        spot.ok = true;
        RtsState.GameInfo game = state.game;
        for (WallLine.Piece piece : pieces) {
            int sizeX = piece.sizeX;
            int sizeZ = piece.sizeZ;
            Footprint footprint = new Footprint();
            footprint.x = piece.minX;
            footprint.z = piece.minZ;
            footprint.sizeX = sizeX;
            footprint.sizeZ = sizeZ;
            footprint.rotation = piece.rotation;
            footprint.y = (int) groundHeight(piece.centerX(), piece.centerZ()) - 1;
            footprint.ok = piece.minX >= game.minX && piece.minZ >= game.minZ
                && piece.minX + sizeX - 1 <= game.maxX
                && piece.minZ + sizeZ - 1 <= game.maxZ;
            for (RtsState.BuildingInfo b : state.buildings.values()) {
                if (piece.minX < b.x + b.sizeX && b.x < piece.minX + sizeX
                    && piece.minZ < b.z + b.sizeZ
                    && b.z < piece.minZ + sizeZ) {
                    footprint.ok = false;
                }
            }
            for (List<RtsState.FeatureInfo> features : Arrays.asList(state.settlements, state.mines)) {
                for (RtsState.FeatureInfo f : features) {
                    if (piece.minX < f.x + f.sizeX + 1 && f.x < piece.minX + sizeX + 1
                        && piece.minZ < f.z + f.sizeZ + 1
                        && f.z < piece.minZ + sizeZ + 1) {
                        footprint.ok = false;
                    }
                }
            }
            if (!footprint.ok && piece.isShort()) {
                // The server leaves out a short segment that does not fit, as the wall stands without it.
                continue;
            }
            spot.ok &= footprint.ok;
            spot.pieces.add(footprint);
            spot.types.add(piece.type().id);
        }
        spot.ok &= !spot.pieces.isEmpty();
        return spot;
    }

    /**
     * The bridge being laid out, as the server will lay it: pieces where the team has bridge already are left out, and
     * its deck lies a block over the water, or in the ground on land.
     */
    private WallSpot bridgeSpot() {
        RtsState.TeamInfo team = state.myTeam();
        int[] start = placing.start == null ? new int[] { placing.cx, placing.cz } : placing.start;
        WallSpot spot = new WallSpot();
        spot.ok = true;
        RtsState.GameInfo game = state.game;
        for (BridgeLine.Piece piece : BridgeLine.pieces(start[0], start[1], placing.cx, placing.cz)) {
            Footprint footprint = new Footprint();
            footprint.x = piece.minX;
            footprint.z = piece.minZ;
            footprint.sizeX = piece.sizeX;
            footprint.sizeZ = piece.sizeZ;
            footprint.rotation = piece.rotation;
            footprint.ok = piece.minX >= game.minX && piece.minZ >= game.minZ
                && piece.minX + piece.sizeX - 1 <= game.maxX
                && piece.minZ + piece.sizeZ - 1 <= game.maxZ;
            boolean built = false;
            for (RtsState.BuildingInfo b : state.buildings.values()) {
                if (piece.minX < b.x + b.sizeX && b.x < piece.minX + piece.sizeX
                    && piece.minZ < b.z + b.sizeZ
                    && b.z < piece.minZ + piece.sizeZ) {
                    if (team != null && b.team.equals(team.id) && BRIDGE.equals(b.type)) {
                        built = true;
                    } else {
                        footprint.ok = false;
                    }
                }
            }
            if (built) {
                continue;
            }
            for (List<RtsState.FeatureInfo> features : Arrays.asList(state.settlements, state.mines)) {
                for (RtsState.FeatureInfo f : features) {
                    if (piece.minX < f.x + f.sizeX && f.x < piece.minX + piece.sizeX
                        && piece.minZ < f.z + f.sizeZ
                        && f.z < piece.minZ + piece.sizeZ) {
                        footprint.ok = false;
                    }
                }
            }
            int x = (int) Math.floor(piece.centerX());
            int z = (int) Math.floor(piece.centerZ());
            int top = mc.theWorld == null ? 63 : waterOrGround(x, z);
            boolean wet = mc.theWorld != null && mc.theWorld.getBlock(x, top, z)
                .getMaterial()
                .isLiquid();
            // The ghost is drawn a block over its ground, so a block under the deck.
            footprint.y = (wet ? top + 1 : top) - 1;
            spot.ok &= footprint.ok;
            spot.pieces.add(footprint);
            spot.types.add(BRIDGE);
        }
        spot.ok &= !spot.pieces.isEmpty();
        return spot;
    }

    /** The y of the topmost ground or water block at x, z, past air, plants and leaves, as the server lays bridges. */
    private int waterOrGround(int x, int z) {
        int y = Math.min(255, mc.theWorld.getHeightValue(x, z));
        for (; y > 0; y--) {
            Material material = mc.theWorld.getBlock(x, y, z)
                .getMaterial();
            if (material != Material.air && material != Material.plants
                && material != Material.vine
                && material != Material.leaves
                && material != Material.snow) {
                break;
            }
        }
        return y;
    }

    /** A wall end at x, z: the middle of the team's tower there, if there is one, else x, z itself. */
    private int[] wallEnd(RtsState.TeamInfo team, int x, int z) {
        RtsState.BuildingInfo b = towerNear(team, x, z);
        return b != null ? new int[] { (int) Math.floor(b.x + b.sizeX / 2.0), (int) Math.floor(b.z + b.sizeZ / 2.0) }
            : new int[] { x, z };
    }

    /** The team's tower at x, z or a block from it, or null. */
    private RtsState.BuildingInfo towerNear(RtsState.TeamInfo team, int x, int z) {
        for (RtsState.BuildingInfo b : state.buildings.values()) {
            if (team != null && b.team.equals(team.id)
                && "tower".equals(b.type)
                && x >= b.x - 1
                && x < b.x + b.sizeX + 1
                && z >= b.z - 1
                && z < b.z + b.sizeZ + 1) {
                return b;
            }
        }
        return null;
    }

    public static boolean isWall(String type) {
        return "wall".equals(type) || "wall_post".equals(type) || "gate".equals(type);
    }

    /** The type of the pieces of a bridge. */
    public static final String BRIDGE = "bridge";

    /** Whether the type is laid out from one point to another, a piece at a time: a wall or a bridge. */
    public static boolean isLine(String type) {
        return isWall(type) || BRIDGE.equals(type);
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
