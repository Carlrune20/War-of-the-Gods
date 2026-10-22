package com.warofthegods.client;

import java.lang.reflect.Method;

import net.minecraft.client.renderer.OpenGlHelper;

import org.lwjgl.opengl.GL11;

/**
 * Draws what glows in the dark, such as a demon's eyes, as Minecraft draws a spider's: at full brightness, unlit, over
 * what was just drawn, added to it so it shines in daylight too. With OptiFine's shaders on, it goes through the shader
 * pack's spider eyes program (gbuffers_spidereyes), so a pack can make it glow and bloom; without OptiFine it is drawn
 * as it is.
 */
public final class Glow {

    private static Method beginSpiderEyes;
    private static Method endSpiderEyes;
    private static boolean looked;
    private static boolean throughShaders;
    private static float lastX;
    private static float lastY;

    private Glow() {}

    /**
     * Starts drawing at full brightness; what follows is drawn over itself, its see-through parts leaving it be. Only
     * an entity's glow goes through the shaders' program, which hands back to the entities' program when it ends: an
     * item held in the hand is drawn by a program of its own.
     */
    public static void begin(boolean entity) {
        throughShaders = entity;
        lastX = OpenGlHelper.lastBrightnessX;
        lastY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(false);
        // The shaders' program sets its own blending, for the pack to draw the glow as it will.
        if (throughShaders) {
            shaders(true);
        }
    }

    /** Goes back to drawing as before {@link #begin(boolean)}. */
    public static void end() {
        if (throughShaders) {
            shaders(false);
        }
        GL11.glPopAttrib();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
    }

    private static void shaders(boolean begin) {
        if (!looked) {
            looked = true;
            for (String name : new String[] { "shadersmod.client.Shaders", "net.optifine.shaders.Shaders" }) {
                try {
                    Class<?> shaders = Class.forName(name);
                    beginSpiderEyes = shaders.getMethod("beginSpiderEyes");
                    endSpiderEyes = shaders.getMethod("endSpiderEyes");
                    break;
                } catch (ReflectiveOperationException | LinkageError e) {
                    beginSpiderEyes = null;
                    endSpiderEyes = null;
                }
            }
        }
        Method method = begin ? beginSpiderEyes : endSpiderEyes;
        if (method == null) {
            return;
        }
        try {
            method.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // A version of OptiFine that does it otherwise: the glow is drawn without the pack's program.
            beginSpiderEyes = null;
            endSpiderEyes = null;
        }
    }
}
