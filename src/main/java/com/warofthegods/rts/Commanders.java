package com.warofthegods.rts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.unit.UnitCommands;
import com.warofthegods.web.Viewer;
import com.warofthegods.web.WebCommand;
import com.warofthegods.web.WebJson;
import com.warofthegods.web.WebRequest;
import com.warofthegods.world.GameWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * Players in the in-game RTS view, on the server thread. While a player commands from above, they hover invisible and
 * unhurt over the spot the camera looks at, so the chunks and entities there load for their client, and an
 * {@link EntityCommanderBody} stands in for them where they were: everyone sees it, and whatever hurts it hurts them.
 * They get the same game and live state messages as browsers, and their commands run through the same
 * {@link WebCommand}s. Leaving puts them back into their body, as they were.
 *
 * <p>
 * Besides the web map's commands a client sends:
 *
 * <pre>
 *   {type: "rts.enter"}         start commanding; answered with {type: "rts.entered", me: {uuid, name}}
 *   {type: "rts.leave"}         stop; answered with {type: "rts.left"}
 *   {type: "rts.look", x, z}    the camera moved: the body follows
 * </pre>
 *
 * What a player needs to be put back is kept in their saved data as well, so a crash does not leave them flying.
 */
public final class Commanders {

    /** Commands waiting for the server thread before more are dropped. */
    private static final int MAX_QUEUED = 1024;
    private static final int COMMANDS_PER_TICK = 64;
    /** Blocks above the ground the body hovers. */
    private static final int HOVER = 10;
    /** Player NBT key holding where a commanding player was and how they were. */
    private static final String SAVED_KEY = "warofthegods.rts";

    private static final Commanders INSTANCE = new Commanders();

    private final Queue<Incoming> inbox = new ConcurrentLinkedQueue<>();
    private final Set<UUID> commanding = new HashSet<>();
    private final Map<UUID, EntityCommanderBody> bodies = new HashMap<>();
    /**
     * Players back from commanding who get back on the horse their body sat on, with the ticks left before they do.
     * Put straight back in the saddle, their client would still have the body sitting there as well and, once the
     * body went, take it off the horse, leaving them riding a horse that does not carry them until they dismount.
     */
    private final Map<UUID, Remount> remounts = new HashMap<>();
    private String gameMessage = WebJson.message("game")
        .toString();
    private String stateMessage;
    private int ticks;

    private static final class Incoming {

        final EntityPlayerMP player;
        final String type;
        final JsonObject body;

        Incoming(EntityPlayerMP player, String type, JsonObject body) {
            this.player = player;
            this.type = type;
            this.body = body;
        }
    }

    private static final class Remount {

        final Entity mount;
        int ticks = REMOUNT_TICKS;

        Remount(Entity mount) {
            this.mount = mount;
        }
    }

    /** Ticks a player back from commanding waits to get back on their horse, once the body is gone for their client. */
    private static final int REMOUNT_TICKS = 5;

    private Commanders() {}

    public static Commanders get() {
        return INSTANCE;
    }

    /** Queues a message from a player's client; called on a Netty thread. */
    static void receive(EntityPlayerMP player, String json) {
        JsonObject body;
        String type;
        try {
            JsonElement parsed = new JsonParser().parse(json);
            body = parsed.getAsJsonObject();
            type = body.get("type")
                .getAsString();
        } catch (RuntimeException e) {
            return;
        }
        if (INSTANCE.inbox.size() < MAX_QUEUED) {
            INSTANCE.inbox.add(new Incoming(player, type, body));
        }
    }

    public boolean any() {
        return !commanding.isEmpty();
    }

    /** Whether the player is commanding from the RTS view, and so not really standing anywhere. */
    public boolean isCommanding(UUID player) {
        return commanding.contains(player);
    }

    /** The game message for new commanders, and for the ones already there when the game changes. */
    public void setGame(JsonObject message) {
        gameMessage = message.toString();
        stateMessage = null;
        for (EntityPlayerMP player : online()) {
            RtsNetwork.sendTo(player, gameMessage);
        }
    }

    public void setState(JsonObject message) {
        stateMessage = message.toString();
        for (EntityPlayerMP player : online()) {
            RtsNetwork.sendTo(player, stateMessage);
        }
    }

    /** Runs what clients sent and checks on the players commanding; called every server tick. */
    public void tick() {
        for (int i = 0; i < COMMANDS_PER_TICK; i++) {
            Incoming incoming = inbox.poll();
            if (incoming == null) {
                break;
            }
            if (isOnline(incoming.player)) {
                run(incoming);
            }
        }
        if (!remounts.isEmpty()) {
            remount();
        }
        if (++ticks % 20 == 0 && any()) {
            for (UUID id : new ArrayList<>(commanding)) {
                EntityPlayerMP player = find(id);
                if (player == null) {
                    commanding.remove(id);
                    EntityCommanderBody body = bodies.remove(id);
                    if (body != null) {
                        body.setDead();
                    }
                    tellCommanders();
                } else if (GameManager.getGame() == null || player.dimension != GameWorld.dimensionId()
                    || !player.getEntityData()
                        .hasKey(SAVED_KEY)) {
                            // The game ended, they went elsewhere, or they died and came back as a new entity.
                            leave(player);
                            RtsNetwork.sendTo(
                                player,
                                WebJson.message("rts.left")
                                    .toString());
                        } else {
                            applyCommanding(player);
                            keepBody(player);
                        }
            }
        }
    }

    /** Puts players back on their horses once they have waited long enough; see {@link #remounts}. */
    private void remount() {
        for (UUID id : new ArrayList<>(remounts.keySet())) {
            Remount remount = remounts.get(id);
            if (--remount.ticks > 0) {
                continue;
            }
            remounts.remove(id);
            EntityPlayerMP player = find(id);
            Entity mount = remount.mount;
            if (player != null && !commanding.contains(id)
                && player.ridingEntity == null
                && player.isEntityAlive()
                && mount.isEntityAlive()
                && mount.riddenByEntity == null
                && mount.worldObj == player.worldObj
                && mount.getDistanceSqToEntity(player) < 9) {
                player.mountEntity(mount);
            }
        }
    }

    private void run(Incoming incoming) {
        EntityPlayerMP player = incoming.player;
        WebRequest request = new WebRequest(
            Viewer.player(player.getUniqueID(), player.getCommandSenderName()),
            reply -> RtsNetwork.sendTo(player, reply.toString()),
            incoming.type,
            incoming.body);
        switch (incoming.type) {
            case "rts.enter":
                enter(player, request);
                return;
            case "rts.leave":
                leave(player);
                request.reply(WebJson.message("rts.left"));
                return;
            case "rts.look":
                if (commanding.contains(player.getUniqueID())) {
                    Double x = UnitCommands.doubleField(request.body, "x");
                    Double z = UnitCommands.doubleField(request.body, "z");
                    if (x != null && z != null) {
                        hoverOver(player, x, z);
                    }
                }
                return;
            default:
        }
        WebCommand command = WebCommand.REGISTRY.get(request.type);
        if (command == null) {
            request.replyError("Unknown command " + request.type);
            return;
        }
        try {
            command.run(request);
        } catch (RuntimeException e) {
            WarOfTheGods.LOG.warn("RTS command {} from {} failed", request.type, request.viewer.getName(), e);
            request.replyError("Command failed");
        }
    }

    private void enter(EntityPlayerMP player, WebRequest request) {
        Game game = GameManager.getGame();
        if (game == null) {
            request.replyError("No game is running");
            return;
        }
        if (player.dimension != GameWorld.dimensionId()) {
            request.replyError("Go to the game map first, with /rts spawn");
            return;
        }
        if (!commanding.contains(player.getUniqueID()) || !player.getEntityData()
            .hasKey(SAVED_KEY)) {
            NBTTagCompound saved = new NBTTagCompound();
            saved.setInteger("dimension", player.dimension);
            saved.setDouble("x", player.posX);
            saved.setDouble("y", player.posY);
            saved.setDouble("z", player.posZ);
            saved.setFloat("yaw", player.rotationYaw);
            saved.setFloat("pitch", player.rotationPitch);
            saved.setBoolean("allowFlying", player.capabilities.allowFlying);
            saved.setBoolean("flying", player.capabilities.isFlying);
            saved.setBoolean("disableDamage", player.capabilities.disableDamage);
            player.getEntityData()
                .setTag(SAVED_KEY, saved);
            commanding.add(player.getUniqueID());
            // A rider's body stays in the saddle, so the horse is still under them when they come back.
            Entity mount = player.ridingEntity;
            if (mount != null) {
                player.mountEntity(null);
            }
            EntityCommanderBody body = new EntityCommanderBody(player.worldObj, player);
            player.worldObj.spawnEntityInWorld(body);
            if (mount != null && mount.isEntityAlive() && mount.riddenByEntity == null) {
                body.mountEntity(mount);
            }
            bodies.put(player.getUniqueID(), body);
            tellCommanders();
            applyCommanding(player);
            hoverOver(player, player.posX, player.posZ);
            WarOfTheGods.LOG.info("{} is commanding from the RTS view", player.getCommandSenderName());
        }
        JsonObject reply = WebJson.message("rts.entered");
        JsonObject me = new JsonObject();
        me.addProperty(
            "uuid",
            player.getUniqueID()
                .toString());
        me.addProperty("name", player.getCommandSenderName());
        reply.add("me", me);
        request.reply(reply);
        RtsNetwork.sendTo(player, gameMessage);
        if (stateMessage != null) {
            RtsNetwork.sendTo(player, stateMessage);
        }
    }

    /** Flying, unhurt and unseen, every so often in case something such as a game mode change undid it. */
    private static void applyCommanding(EntityPlayerMP player) {
        boolean changed = !player.capabilities.allowFlying || !player.capabilities.isFlying
            || !player.capabilities.disableDamage;
        player.capabilities.allowFlying = true;
        player.capabilities.isFlying = true;
        player.capabilities.disableDamage = true;
        if (changed) {
            player.sendPlayerAbilities();
        }
        player.setInvisible(true);
        player.fallDistance = 0;
        player.extinguish();
    }

    /** Moves the body over x, z, inside the map, high enough to clear the ground. */
    private static void hoverOver(EntityPlayerMP player, double x, double z) {
        Game game = GameManager.getGame();
        if (game == null) {
            return;
        }
        x = Math.max(game.getMinX() + 0.5, Math.min(game.getMaxX() + 0.5, x));
        z = Math.max(game.getMinZ() + 0.5, Math.min(game.getMaxZ() + 0.5, z));
        WorldServer world = player.getServerForPlayer();
        int top = world.getHeightValue(MathHelper.floor_double(x), MathHelper.floor_double(z));
        double y = Math.min(250, top + HOVER);
        double dx = x - player.posX;
        double dz = z - player.posZ;
        if (dx * dx + dz * dz < 4 && Math.abs(y - player.posY) < 3) {
            return;
        }
        player.playerNetServerHandler.setPlayerLocation(x, y, z, player.rotationYaw, player.rotationPitch);
        player.fallDistance = 0;
    }

    /** Puts the player back as they were before commanding, if they were. */
    public void leave(EntityPlayerMP player) {
        if (commanding.remove(player.getUniqueID())) {
            tellCommanders();
        }
        EntityCommanderBody body = bodies.remove(player.getUniqueID());
        if (body != null) {
            body.setDead();
        }
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(SAVED_KEY)) {
            return;
        }
        NBTTagCompound saved = data.getCompoundTag(SAVED_KEY);
        data.removeTag(SAVED_KEY);
        player.capabilities.allowFlying = saved.getBoolean("allowFlying");
        player.capabilities.isFlying = saved.getBoolean("flying");
        player.capabilities.disableDamage = saved.getBoolean("disableDamage");
        player.sendPlayerAbilities();
        player.setInvisible(player.isPotionActive(Potion.invisibility));
        if (body != null && body.worldObj == player.worldObj && player.playerNetServerHandler != null) {
            // Back into the body, wherever it was pushed, and back into the saddle if it sat on a horse.
            Entity mount = body.ridingEntity;
            double x = body.posX;
            double y = body.posY;
            double z = body.posZ;
            if (mount != null) {
                body.mountEntity(null);
            }
            player.playerNetServerHandler.setPlayerLocation(x, y, z, body.rotationYaw, body.rotationPitch);
            player.fallDistance = body.fallDistance;
            if (body.isBurning()) {
                player.setFire(1);
            }
            if (mount != null && mount.isEntityAlive()) {
                remounts.put(player.getUniqueID(), new Remount(mount));
            }
        } else if (player.dimension == saved.getInteger("dimension") && player.playerNetServerHandler != null) {
            player.playerNetServerHandler.setPlayerLocation(
                saved.getDouble("x"),
                saved.getDouble("y"),
                saved.getDouble("z"),
                saved.getFloat("yaw"),
                saved.getFloat("pitch"));
            player.fallDistance = 0;
        }
        WarOfTheGods.LOG.info("{} left the RTS view", player.getCommandSenderName());
    }

    /** Puts everyone back, as when the server stops. */
    public void leaveAll() {
        for (EntityPlayerMP player : online()) {
            leave(player);
        }
        commanding.clear();
        bodies.clear();
        remounts.clear();
        inbox.clear();
    }

    /** Players who log out are put back first, so they are saved where they stood. */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            leave((EntityPlayerMP) event.player);
        }
    }

    /** A player saved while commanding, as when the server crashed, logs in as they were before. */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            leave((EntityPlayerMP) event.player);
            if (any()) {
                tellCommanders();
            }
        }
    }

    /** A player who died, which only /kill can do while commanding, comes back in person. */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        EntityCommanderBody body = bodies.remove(event.player.getUniqueID());
        if (body != null) {
            body.setDead();
        }
        if (event.player instanceof EntityPlayerMP && commanding.remove(event.player.getUniqueID())) {
            tellCommanders();
            RtsNetwork.sendTo(
                (EntityPlayerMP) event.player,
                WebJson.message("rts.left")
                    .toString());
        }
    }

    /**
     * A commanding player killed through their body steps back into it to die, so they fall and drop what they carry
     * there, and their client shows the death screen.
     */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.entityLiving instanceof EntityPlayerMP) || !commanding.contains(event.entityLiving.getUniqueID())) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.entityLiving;
        leave(player);
        RtsNetwork.sendTo(
            player,
            WebJson.message("rts.left")
                .toString());
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP && commanding.contains(event.player.getUniqueID())) {
            leave((EntityPlayerMP) event.player);
            RtsNetwork.sendTo(
                (EntityPlayerMP) event.player,
                WebJson.message("rts.left")
                    .toString());
        }
    }

    /** Tells every client who is commanding, so they do not draw them where they hover. */
    private void tellCommanders() {
        JsonObject message = WebJson.message("rts.commanders");
        JsonArray ids = new JsonArray();
        for (UUID id : commanding) {
            ids.add(new JsonPrimitive(id.toString()));
        }
        message.add("ids", ids);
        String json = message.toString();
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }
        for (Object object : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) object;
            if (player.playerNetServerHandler != null) {
                RtsNetwork.sendTo(player, json);
            }
        }
    }

    /** The body of a commanding player, while it is in the world; null for anyone else. */
    public EntityCommanderBody bodyOf(UUID player) {
        EntityCommanderBody body = bodies.get(player);
        return body != null && body.isEntityAlive() && isInWorld(body) ? body : null;
    }

    /** The body of a commanding player, or where it was if its chunk is unloaded; null for anyone else. */
    public EntityCommanderBody lastBodyOf(UUID player) {
        return commanding.contains(player) ? bodies.get(player) : null;
    }

    /** Puts a new body where the old one was if its chunk unloaded and is back, as when the owner flew off and back. */
    private void keepBody(EntityPlayerMP player) {
        EntityCommanderBody body = bodies.get(player.getUniqueID());
        if (body == null || isInWorld(body) || body.worldObj != player.worldObj || player.getHealth() <= 0) {
            return;
        }
        int chunkX = MathHelper.floor_double(body.posX) >> 4;
        int chunkZ = MathHelper.floor_double(body.posZ) >> 4;
        if (!body.worldObj.getChunkProvider()
            .chunkExists(chunkX, chunkZ)) {
            return;
        }
        EntityCommanderBody fresh = new EntityCommanderBody(body.worldObj, player);
        fresh.setLocationAndAngles(body.posX, body.posY, body.posZ, body.rotationYaw, body.rotationPitch);
        fresh.rotationYawHead = body.rotationYawHead;
        fresh.renderYawOffset = body.renderYawOffset;
        fresh.fallDistance = 0;
        body.worldObj.spawnEntityInWorld(fresh);
        bodies.put(player.getUniqueID(), fresh);
    }

    private static boolean isInWorld(EntityCommanderBody body) {
        return !body.isDead && body.worldObj.getEntityByID(body.getEntityId()) == body;
    }

    private List<EntityPlayerMP> online() {
        List<EntityPlayerMP> players = new ArrayList<>();
        for (UUID id : commanding) {
            EntityPlayerMP player = find(id);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    static EntityPlayerMP find(UUID id) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return null;
        }
        for (Object object : server.getConfigurationManager().playerEntityList) {
            EntityPlayer player = (EntityPlayer) object;
            if (player.getUniqueID()
                .equals(id)) {
                return (EntityPlayerMP) player;
            }
        }
        return null;
    }

    private static boolean isOnline(EntityPlayerMP player) {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null && player.playerNetServerHandler != null
            && server.getConfigurationManager().playerEntityList.contains(player);
    }
}
