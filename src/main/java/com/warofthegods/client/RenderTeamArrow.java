package com.warofthegods.client;

import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderArrow;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.util.IIcon;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.warofthegods.unit.EntityTeamArrow;

/** Draws a unit's arrow as an arrow, and an axe a goblin threw as an axe turning end over end as it flies. */
public class RenderTeamArrow extends RenderArrow {

    /** Degrees the axe turns each tick. */
    private static final float SPIN = 40;
    private static final float AXE_SIZE = 0.7F;

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        EntityTeamArrow arrow = (EntityTeamArrow) entity;
        if (!arrow.isAxe()) {
            super.doRender(entity, x, y, z, yaw, partialTicks);
            return;
        }
        IIcon icon = Items.iron_axe.getIconFromDamage(0);
        if (icon == null) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        // Turned to face along its flight, as an arrow is, then end over end in the upright plane it flies in.
        GL11.glRotatef(
            arrow.prevRotationYaw + (arrow.rotationYaw - arrow.prevRotationYaw) * partialTicks - 90,
            0,
            1,
            0);
        GL11.glRotatef(-(arrow.ticksExisted + partialTicks) * SPIN, 0, 0, 1);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glScalef(AXE_SIZE, AXE_SIZE, AXE_SIZE);
        GL11.glTranslatef(-0.5F, -0.5F, 0.03125F);
        bindTexture(TextureMap.locationItemsTexture);
        ItemRenderer.renderItemIn2D(
            Tessellator.instance,
            icon.getMaxU(),
            icon.getMinV(),
            icon.getMinU(),
            icon.getMaxV(),
            icon.getIconWidth(),
            icon.getIconHeight(),
            0.0625F);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
    }
}
