package com.warofthegods.unit;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.warofthegods.game.Building;
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Team;
import com.warofthegods.item.ItemPrayerBook;
import com.warofthegods.rts.EntityCommanderBody;
import com.warofthegods.rts.PrayerMessage;
import com.warofthegods.rts.RtsNetwork;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Players' prayer points and protection prayers, for those who carry a prayer book. A player has up to
 * {@link #MAX_POINTS} points, as many as their health. A protection prayer, put up with the book, takes three quarters
 * of hits of its style as a hero's does, and drains a point for every {@link #DRAIN_TICKS} it is up; it goes down when
 * the points run out, or the book is no longer carried. Standing by their own team's chaos temple, monastery or Bandos
 * temple fills the points up again. Points are kept with the player, and come back full when they die. The prayer a
 * player has up shows over their head, as a hero's does.
 */
public final class PlayerPrayers {

    public static final int MAX_POINTS = 20;
    /** Six seconds. */
    public static final int DRAIN_TICKS = 120;
    /** How near a temple, in blocks, its team's players recharge. */
    private static final double TEMPLE_REACH = 4;

    private static final String TAG = "warofthegods.prayer";
    /**
     * The player's data watcher slot that tells every client the prayer they have up, to show over their head. Players
     * use slots up to 18 for themselves.
     */
    public static final int WATCH_PRAYER = 27;

    /** What each player's client was last told, so it is told again only when that changes. */
    private static final Map<EntityPlayer, Integer> SENT = new WeakHashMap<>();

    /**
     * Every player has the slot that shows their prayer, on the server and on each client, and so does the body a
     * commander leaves standing, to show it while they look down from the RTS view.
     */
    @SubscribeEvent
    public void onEntityConstructing(EntityEvent.EntityConstructing event) {
        if (event.entity instanceof EntityPlayer || event.entity instanceof EntityCommanderBody) {
            event.entity.getDataWatcher()
                .addObject(WATCH_PRAYER, (byte) 0);
        }
    }

    /** On a client, the prayer a player, or a commander's body, has up, as their server said. */
    public static Prayer watchedOn(Entity entity) {
        return Prayer.byOrdinal(
            entity.getDataWatcher()
                .getWatchableObjectByte(WATCH_PRAYER));
    }

    /** A player's prayer: points, the prayer chosen, whether it is up and how long it has been since the last drain. */
    private static NBTTagCompound state(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(TAG)) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("points", MAX_POINTS);
            tag.setInteger("prayer", Prayer.MELEE.ordinal());
            data.setTag(TAG, tag);
        }
        return data.getCompoundTag(TAG);
    }

    public static int points(EntityPlayer player) {
        return state(player).getInteger("points");
    }

    /** The prayer the player has chosen, up or not. */
    public static Prayer chosen(EntityPlayer player) {
        Prayer prayer = Prayer.byOrdinal(state(player).getInteger("prayer"));
        return prayer == Prayer.NONE ? Prayer.MELEE : prayer;
    }

    /** The prayer the player has up, or NONE. */
    public static Prayer upFor(EntityPlayer player) {
        return state(player).getBoolean("up") ? chosen(player) : Prayer.NONE;
    }

    /** A click with the book: puts the chosen prayer up, or takes it down. */
    public static void toggle(EntityPlayerMP player) {
        if (state(player).getBoolean("up")) {
            setUp(player, false);
            player.worldObj.playSoundAtEntity(player, "random.click", 0.4F, 0.6F);
        } else {
            putUp(player);
        }
    }

    /** Puts the chosen prayer up, if there are points for it. */
    public static void putUp(EntityPlayerMP player) {
        if (points(player) <= 0) {
            tell(
                player,
                EnumChatFormatting.GRAY + "You have no prayer points left. Recharge them at your temple or monastery.");
            setUp(player, false);
            return;
        }
        boolean was = state(player).getBoolean("up");
        setUp(player, true);
        if (!was) {
            player.worldObj.playSoundAtEntity(player, "random.orb", 0.5F, 1.4F);
        }
    }

    /** While the button is held with the book: on to the next prayer, put up at once. */
    public static void switchPrayer(EntityPlayerMP player) {
        Prayer next = Prayer.byOrdinal(chosen(player).ordinal() % 3 + 1);
        state(player).setInteger("prayer", next.ordinal());
        player.worldObj.playSoundAtEntity(player, "random.click", 0.4F, 1.2F);
        sync(player);
    }

    private static void setUp(EntityPlayerMP player, boolean up) {
        NBTTagCompound state = state(player);
        if (state.getBoolean("up") != up) {
            state.setBoolean("up", up);
            state.setInteger("ticks", 0);
        }
        sync(player);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        boolean book = ItemPrayerBook.carriedBy(player);
        NBTTagCompound state = state(player);
        if (state.getBoolean("up")) {
            if (!book) {
                setUp(player, false);
            } else {
                int ticks = state.getInteger("ticks") + 1;
                if (ticks >= DRAIN_TICKS) {
                    ticks = 0;
                    int points = Math.max(0, points(player) - 1);
                    state.setInteger("points", points);
                    if (points == 0) {
                        state.setBoolean("up", false);
                        tell(player, EnumChatFormatting.RED + "You have run out of prayer points.");
                        player.worldObj.playSoundAtEntity(player, "random.fizz", 0.5F, 1.0F);
                    }
                }
                state.setInteger("ticks", ticks);
            }
        }
        if (book && player.ticksExisted % 20 == 0 && points(player) < MAX_POINTS) {
            Building temple = templeNear(player);
            if (temple != null) {
                state.setInteger("points", MAX_POINTS);
                tell(
                    player,
                    EnumChatFormatting.AQUA + "You recharge your prayer points at the "
                        + temple.type.displayName.toLowerCase()
                        + ".");
                player.worldObj.playSoundAtEntity(player, "random.levelup", 0.4F, 1.6F);
            }
        }
        sync(player);
    }

    /** The player's own team's finished temple or monastery they stand by, if any. */
    private static Building templeNear(EntityPlayer player) {
        Game game = GameManager.getGame();
        Team team = game == null ? null : game.getTeamOf(player.getUniqueID());
        if (team == null) {
            return null;
        }
        for (Building building : game.getBuildings(team)) {
            if ((building.type == BuildingType.CHAOS_TEMPLE || building.type == BuildingType.MONASTERY
                || building.type == BuildingType.BANDOS_TEMPLE) && building.isComplete()
                && building.distanceTo(player.posX, player.posZ) <= TEMPLE_REACH) {
                return building;
            }
        }
        return null;
    }

    /** A protection prayer takes most of a hit of its style. */
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.entity instanceof EntityPlayerMP)) {
            return;
        }
        Prayer up = upFor((EntityPlayer) event.entity);
        if (up != Prayer.NONE && up == Prayer.styleOf(event.source)) {
            event.ammount *= Prayer.DAMAGE_TAKEN;
        }
    }

    /** Tells the player's client their points and prayer, if they have changed since it was last told. */
    private static void sync(EntityPlayerMP player) {
        int points = points(player);
        int prayer = chosen(player).ordinal();
        boolean up = state(player).getBoolean("up");
        byte shown = (byte) upFor(player).ordinal();
        if (player.getDataWatcher()
            .getWatchableObjectByte(WATCH_PRAYER) != shown) {
            player.getDataWatcher()
                .updateObject(WATCH_PRAYER, shown);
        }
        int packed = points | prayer << 8 | (up ? 1 << 16 : 0);
        Integer sent = SENT.get(player);
        if (sent == null || sent != packed) {
            SENT.put(player, packed);
            RtsNetwork.sendPrayer(player, new PrayerMessage(points, prayer, up));
        }
    }

    private static void tell(EntityPlayer player, String message) {
        player.addChatMessage(new ChatComponentText(message));
    }
}
