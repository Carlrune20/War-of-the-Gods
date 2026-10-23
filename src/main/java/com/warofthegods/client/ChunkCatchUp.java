package com.warofthegods.client;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Builds chunks that wait to be drawn in a little of each frame's spare time. OptiFine's default chunk loading
 * builds a set number of chunks a frame (Video Settings > Performance > Chunk Updates, 1 unless changed) however
 * idle the computer is, so when a shader pack holds the frame rate down, the world fills in slowly with the CPU and
 * GPU barely working. Vanilla Minecraft already spends spare frame time on chunks, and OptiFine's Smooth and
 * Multi-Core chunk loading schedule their own, so this only steps in for OptiFine's default.
 */
public final class ChunkCatchUp {

    private final Minecraft mc = Minecraft.getMinecraft();
    /** OptiFine's WrUpdates.hasWrUpdater, or null without OptiFine or once it could not be called. */
    private Method hasWrUpdater;

    private ChunkCatchUp() {
        try {
            hasWrUpdater = Class.forName("WrUpdates")
                .getMethod("hasWrUpdater");
        } catch (ReflectiveOperationException | LinkageError e) {
            hasWrUpdater = null;
        }
    }

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new ChunkCatchUp());
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        EntityLivingBase view = mc.renderViewEntity;
        if (event.phase != TickEvent.Phase.END || Config.chunkUpdateMillis <= 0
            || hasWrUpdater == null
            || mc.theWorld == null
            || view == null
            || hasWrUpdater()) {
            return;
        }
        long end = System.nanoTime() + Config.chunkUpdateMillis * 1_000_000L;
        // OptiFine's updateRenderers builds Chunk Updates' worth of chunks, the nearest first, and is false once
        // none are left.
        while (mc.renderGlobal.updateRenderers(view, false) && System.nanoTime() < end) {}
    }

    /** Whether OptiFine's Chunk Loading is set to Smooth or Multi-Core, which build chunks their own way. */
    private boolean hasWrUpdater() {
        try {
            return (Boolean) hasWrUpdater.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            WarOfTheGods.LOG.warn("Cannot tell how OptiFine loads chunks; leaving chunk building to it", e);
            hasWrUpdater = null;
            return true;
        }
    }
}
