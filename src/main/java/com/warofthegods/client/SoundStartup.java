package com.warofthegods.client;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;

import com.warofthegods.WarOfTheGods;

import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Keeps Minecraft 1.7.10 from starting its sound engine twice at once.
 *
 * <p>
 * The engine starts on its own thread while mods load, and Forge restarts it as soon as loading finishes. A restart
 * only shuts the old engine down once it has finished starting, so when mods load faster than OpenAL starts, two
 * engines start side by side: the second cannot get OpenAL ("Only one OpenAL context may be instantiated"), the first
 * is torn down under it, and the game later crashes with an {@code UnsatisfiedLinkError} in {@code AL10}. Waiting
 * here for the first start to finish lets the restart shut it down properly.
 */
final class SoundStartup {

    private static final long MAX_WAIT_MS = 35_000;

    private SoundStartup() {}

    static void awaitFirstStart() {
        try {
            SoundHandler handler = Minecraft.getMinecraft()
                .getSoundHandler();
            if (handler == null) {
                return;
            }
            Field managerField = ReflectionHelper.findField(SoundHandler.class, "sndManager", "field_147694_f");
            SoundManager manager = (SoundManager) managerField.get(handler);
            Field loadedField = ReflectionHelper.findField(SoundManager.class, "loaded", "field_148617_f");
            long start = System.currentTimeMillis();
            long deadline = start + MAX_WAIT_MS;
            while (!loadedField.getBoolean(manager) && loaderRunning() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            long waited = System.currentTimeMillis() - start;
            if (waited > 0) {
                WarOfTheGods.LOG.info("Waited " + waited + " ms for the sound engine to finish starting");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        } catch (Exception e) {
            WarOfTheGods.LOG.warn("Could not check whether the sound engine has started", e);
        }
    }

    /** Whether Minecraft's thread that starts the sound engine is still at work. */
    private static boolean loaderRunning() {
        for (Thread thread : Thread.getAllStackTraces()
            .keySet()) {
            if ("Sound Library Loader".equals(thread.getName()) && thread.isAlive()) {
                return true;
            }
        }
        return false;
    }
}
