package com.warofthegods.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.warofthegods.WarOfTheGods;
import com.warofthegods.game.Faction;
import com.warofthegods.game.UnitKind;
import com.warofthegods.unit.EntityUnit;

/**
 * Draws a unit with the player model in the skin of its kind and its team's faction, with bands round its arms and
 * waist in its team's color, and any armor its team's upgrades give it. The name above its head is in its team's
 * color too.
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

    private final ModelBiped overlayModel = new ModelBiped(0.08F);

    public RenderUnit() {
        super(new ModelBiped(), 0.5F);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityLiving entity) {
        EntityUnit unit = (EntityUnit) entity;
        return SKINS.get(unit.getWatchedFaction().id + "_" + unit.getWatchedKind().id);
    }

    /**
     * The first pass draws the team bands over the model, tinted the team's color. The other three draw the armor a
     * team's upgrades give its fighters: helmet, then chest, then legs.
     */
    @Override
    protected int shouldRenderPass(EntityLiving entity, int pass, float partialTicks) {
        if (pass != 0) {
            // The tint would otherwise carry over to the armor and the item in its hand.
            GL11.glColor4f(1, 1, 1, 1);
            return super.shouldRenderPass(entity, pass - 1, partialTicks);
        }
        int color = ((EntityUnit) entity).getTeamColor();
        bindTexture(OVERLAY);
        overlayModel.onGround = mainModel.onGround;
        overlayModel.isRiding = mainModel.isRiding;
        overlayModel.heldItemRight = modelBipedMain.heldItemRight;
        overlayModel.aimedBow = modelBipedMain.aimedBow;
        setRenderPassModel(overlayModel);
        GL11.glColor3f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
        return 1;
    }
}
