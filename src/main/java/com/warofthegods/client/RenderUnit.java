package com.warofthegods.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.warofthegods.WarOfTheGods;
import com.warofthegods.game.Faction;
import com.warofthegods.game.UnitKind;
import com.warofthegods.unit.EntityUnit;

/**
 * Draws a unit with the player model in the skin of its kind and its team's faction, with bands round its arms and
 * waist in its team's color, and any armor its team's upgrades give it. The name above its head is in its team's
 * color too. Archers raise their bow as they draw it, and heroes stand taller than the rest.
 */
public class RenderUnit extends RenderBiped {

    private static final ResourceLocation OVERLAY = new ResourceLocation(
        WarOfTheGods.MODID,
        "textures/entity/unit/team_overlay.png");
    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    static {
        for (Faction faction : Faction.values()) {
            for (UnitKind kind : UnitKind.values()) {
                SKINS.put(
                    faction.id + "_" + kind.id,
                    new ResourceLocation(
                        WarOfTheGods.MODID,
                        "textures/entity/unit/" + faction.id + "_" + kind.id + ".png"));
            }
        }
    }

    private static final float HERO_SCALE = 1.15F;

    private final ModelBiped overlayModel = new ModelBiped(0.08F);

    public RenderUnit() {
        super(new ModelBiped(), 0.5F);
    }

    /** An archer drawing its bow holds it up in both hands, as a player does. */
    @Override
    public void doRender(EntityLiving entity, double x, double y, double z, float yaw, float partialTicks) {
        boolean aiming = ((EntityUnit) entity).getDrawTicks() > 0;
        modelBipedMain.aimedBow = aiming;
        field_82423_g.aimedBow = aiming;
        field_82425_h.aimedBow = aiming;
        super.doRender(entity, x, y, z, yaw, partialTicks);
    }

    /** Heroes stand a head taller than other units. */
    @Override
    protected void preRenderCallback(EntityLivingBase entity, float partialTicks) {
        if (((EntityUnit) entity).getWatchedKind().hero) {
            GL11.glScalef(HERO_SCALE, HERO_SCALE, HERO_SCALE);
        }
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityLiving entity) {
        EntityUnit unit = (EntityUnit) entity;
        return SKINS.get(unit.getWatchedFaction().id + "_" + unit.getWatchedKind().id);
    }

    /**
     * Draws the team bands over the model, tinted the team's color. They go with the model itself, not in a render
     * pass of their own: there are only four passes, and the armor needs all of them, from helmet to boots.
     */
    @Override
    protected void renderModel(EntityLivingBase entity, float swing, float swingAmount, float age, float yaw,
        float pitch, float scale) {
        super.renderModel(entity, swing, swingAmount, age, yaw, pitch, scale);
        if (entity.isInvisible()) {
            return;
        }
        int color = ((EntityUnit) entity).getTeamColor();
        bindTexture(OVERLAY);
        overlayModel.onGround = mainModel.onGround;
        overlayModel.isRiding = mainModel.isRiding;
        overlayModel.heldItemRight = modelBipedMain.heldItemRight;
        overlayModel.aimedBow = modelBipedMain.aimedBow;
        GL11.glColor3f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
        overlayModel.render(entity, swing, swingAmount, age, yaw, pitch, scale);
        // The tint would otherwise carry over to the armor and the item in its hand.
        GL11.glColor4f(1, 1, 1, 1);
    }
}
