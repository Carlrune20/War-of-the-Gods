package com.warofthegods.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.renderer.OpenGlHelper;

import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.GL11;

/**
 * Draws what glows in the dark, such as a demon's eyes, as Minecraft draws a spider's: at full brightness, unlit, over
 * what was just drawn, added to it so it shines in daylight too. With OptiFine's shaders on, it goes through the shader
 * pack's spider eyes program (gbuffers_spidereyes), so a pack can make it glow and bloom; without OptiFine it is drawn
 * as it is.
 * <p>
 * OptiFine keeps the entities' program's entityColor, the red of a hurt creature, in one copy shared by every program,
 * and forgets it whenever the program changes; it clears the red after a hurt creature is drawn only when that copy
 * says it is still red. A glow drawn on a hurt creature changes the program and back, so the red would never be
 * cleared and every creature drawn after it would be red too. Ending the glow writes the color back.
 */
public final class Glow {

    private static Method beginSpiderEyes;
    private static Method endSpiderEyes;
    private static Field entityColor;
    private static Method entityColorValues;
    private static Method entityColorLocation;
    private static final float[] color = new float[4];
    private static boolean colorKept;
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
            keepEntityColor();
            shaders(true);
        }
    }

    /** Goes back to drawing as before {@link #begin(boolean)}. */
    public static void end() {
        if (throughShaders) {
            shaders(false);
            restoreEntityColor();
        }
        GL11.glPopAttrib();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
    }

    private static void shaders(boolean begin) {
        look();
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

    /** Finds OptiFine's shaders, once. */
    private static void look() {
        if (!looked) {
            looked = true;
            for (String name : new String[] { "shadersmod.client.Shaders", "net.optifine.shaders.Shaders" }) {
                Class<?> shaders;
                try {
                    shaders = Class.forName(name);
                    beginSpiderEyes = shaders.getMethod("beginSpiderEyes");
                    endSpiderEyes = shaders.getMethod("endSpiderEyes");
                } catch (ReflectiveOperationException | LinkageError e) {
                    beginSpiderEyes = null;
                    endSpiderEyes = null;
                    continue;
                }
                try {
                    entityColor = shaders.getField("uniformEntityColor");
                    entityColorValues = entityColor.getType()
                        .getMethod("getValues");
                    entityColorLocation = entityColor.getType()
                        .getMethod("getLocation");
                } catch (ReflectiveOperationException | LinkageError e) {
                    entityColor = null;
                }
                break;
            }
        }
    }

    /** Notes the entities' program's entityColor as OptiFine has it, before the glow changes the program. */
    private static void keepEntityColor() {
        colorKept = false;
        look();
        if (entityColor == null || beginSpiderEyes == null) {
            return;
        }
        try {
            Object uniform = entityColor.get(null);
            if (uniform == null || (Integer) entityColorLocation.invoke(uniform) < 0) {
                return;
            }
            System.arraycopy((float[]) entityColorValues.invoke(uniform), 0, color, 0, 4);
            colorKept = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            entityColor = null;
        }
    }

    /**
     * Writes the entityColor noted before the glow into the entities' program, now that it is back, and into
     * OptiFine's copy of it, so that clearing a hurt creature's red afterwards takes.
     */
    private static void restoreEntityColor() {
        if (!colorKept || entityColor == null) {
            return;
        }
        colorKept = false;
        try {
            Object uniform = entityColor.get(null);
            int location = (Integer) entityColorLocation.invoke(uniform);
            if (location < 0) {
                return;
            }
            ARBShaderObjects.glUniform4fARB(location, color[0], color[1], color[2], color[3]);
            System.arraycopy(color, 0, (float[]) entityColorValues.invoke(uniform), 0, 4);
        } catch (ReflectiveOperationException | RuntimeException e) {
            entityColor = null;
        }
    }
}
