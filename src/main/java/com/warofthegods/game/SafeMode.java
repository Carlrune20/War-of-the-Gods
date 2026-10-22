package com.warofthegods.game;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Eases the server's load while its ticks run long: units off fighting look for work and plan their way less often,
 * AI players think less often, the live state goes to players and browsers less often, and no wilderness mobs spawn.
 * It comes on once ticks have run long for a while, and goes off once they have been quick for a while longer.
 */
public final class SafeMode {

    /** Ticks between looks at how long ticks take. */
    private static final int CHECK_TICKS = 20;
    /** How much less often the eased work is done while safe mode is on. */
    public static final int SLOWDOWN = 2;

    private static boolean active;

    private final Tracker tracker = new Tracker();
    private int ticks;

    /** Whether safe mode is on. */
    public static boolean isActive() {
        return active;
    }

    /** So many ticks, stretched while safe mode is on. */
    public static int stretch(int ticks) {
        return active ? ticks * SLOWDOWN : ticks;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % CHECK_TICKS != 0) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }
        boolean was = active;
        active = Config.safeModeEnabled && tracker.update(averageMs(server.tickTimeArray), CHECK_TICKS);
        if (active != was) {
            announce(server, active);
        }
    }

    /** The average of the server's recent tick times, in milliseconds. */
    static double averageMs(long[] tickNanos) {
        if (tickNanos == null || tickNanos.length == 0) {
            return 0;
        }
        long total = 0;
        for (long nanos : tickNanos) {
            total += nanos;
        }
        return total / (double) tickNanos.length / 1.0E6;
    }

    private static void announce(MinecraftServer server, boolean on) {
        String message = on
            ? "The server is struggling to keep up, so War of the Gods has gone into safe mode: units not fighting"
                + " think less often and no wild mobs spawn until it catches up."
            : "The server has caught up, and War of the Gods has left safe mode.";
        WarOfTheGods.LOG.info(message);
        for (Object player : server.getConfigurationManager().playerEntityList) {
            ((EntityPlayerMP) player).addChatMessage(
                new ChatComponentText((on ? EnumChatFormatting.RED : EnumChatFormatting.GREEN) + message));
        }
    }

    /**
     * Decides when safe mode is on from tick times: on once they have stayed over the slow mark for the time to go
     * in, off once they have stayed under the quick mark for the time to come out.
     */
    static final class Tracker {

        private boolean on;
        private int slowTicks;
        private int quickTicks;

        /** Takes in the average tick time over the last so many ticks; returns whether safe mode is on. */
        boolean update(double averageMs, int ticks) {
            slowTicks = averageMs > Config.safeModeEnterMs ? slowTicks + ticks : 0;
            quickTicks = averageMs < Config.safeModeExitMs ? quickTicks + ticks : 0;
            if (!on && slowTicks >= Config.safeModeEnterSeconds * 20) {
                on = true;
                quickTicks = 0;
            } else if (on && quickTicks >= Config.safeModeExitSeconds * 20) {
                on = false;
                slowTicks = 0;
            }
            return on;
        }
    }
}
