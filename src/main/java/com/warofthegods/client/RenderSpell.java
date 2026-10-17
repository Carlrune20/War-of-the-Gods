package com.warofthegods.client;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.warofthegods.game.Spell;
import com.warofthegods.unit.EntitySpell;

/**
 * A spell is drawn by the particles it trails, which it spawns itself; a lesser demon's fireball is drawn as well, as a
 * great glowing ball of flame turned to face the camera.
 */
public class RenderSpell extends Render {

    /** How big a fireball is drawn, in blocks. */
    private static final float FIREBALL_SIZE = 1.2F;

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        if (((EntitySpell) entity).getSpell() != Spell.FIREBALL) {
            return;
        }
        GL11.glPushMatrix();
        bindEntityTexture(entity);
        GL11.glTranslatef((float) x, (float) y, (float) z);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glScalef(FIREBALL_SIZE, FIREBALL_SIZE, FIREBALL_SIZE);
        GL11.glRotatef(180.0F - renderManager.playerViewY, 0, 1, 0);
        GL11.glRotatef(-renderManager.playerViewX, 1, 0, 0);
        // Spinning as it flies, and lit by its own fire.
        GL11.glRotatef((entity.ticksExisted + partialTicks) * 20, 0, 0, 1);
        float lastX = OpenGlHelper.lastBrightnessX;
        float lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        IIcon icon = Items.fire_charge.getIconFromDamage(0);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setNormal(0, 1, 0);
        tessellator.addVertexWithUV(-0.5, -0.5, 0, icon.getMinU(), icon.getMaxV());
        tessellator.addVertexWithUV(0.5, -0.5, 0, icon.getMaxU(), icon.getMaxV());
        tessellator.addVertexWithUV(0.5, 0.5, 0, icon.getMaxU(), icon.getMinV());
        tessellator.addVertexWithUV(-0.5, 0.5, 0, icon.getMinU(), icon.getMinV());
        tessellator.draw();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return TextureMap.locationItemsTexture;
    }
}
