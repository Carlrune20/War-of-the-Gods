package com.warofthegods.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.warofthegods.WarOfTheGods;
import com.warofthegods.game.Faction;
import com.warofthegods.game.UnitKind;
import com.warofthegods.unit.EntityUnit;
import com.warofthegods.unit.Prayer;

/**
 * Draws a unit with the player model in the skin of its kind and its team's faction, with bands round its arms and
 * waist in its team's color, and any armor its team's upgrades give it. The name above its head is in its team's
 * color too. Archers raise their bow as they draw it, and heroes stand taller than the rest. A hero's protection
 * prayer shows above his name while it is up.
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
    private static final Map<Prayer, ResourceLocation> OVERHEADS = new HashMap<>();

    static {
        for (Prayer prayer : Prayer.values()) {
            OVERHEADS.put(
                prayer,
                new ResourceLocation(WarOfTheGods.MODID, "textures/gui/prayer/overhead_" + prayer.id + ".png"));
        }
    }

    /** How wide the prayer icon above a hero's head is, in blocks. */
    private static final float OVERHEAD_SIZE = 0.6F;

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

    /** The name above its head, and above that the protection prayer it has up, facing the camera. */
    @Override
    protected void passSpecialRender(EntityLivingBase entity, double x, double y, double z) {
        super.passSpecialRender(entity, x, y, z);
        Prayer prayer = ((EntityUnit) entity).getWatchedPrayer();
        if (prayer == Prayer.NONE || entity.isInvisible()) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslated(x, y + entity.height + 0.9, z);
        GL11.glNormal3f(0, 1, 0);
        GL11.glRotatef(-renderManager.playerViewY, 0, 1, 0);
        GL11.glRotatef(renderManager.playerViewX, 1, 0, 0);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glColor4f(1, 1, 1, 1);
        bindTexture(OVERHEADS.get(prayer));
        float half = OVERHEAD_SIZE / 2;
        Tessellator tessellator = Tessellator.instance;
        // Turned to the camera as the name is, its right is toward -x.
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(half, half, 0, 0, 0);
        tessellator.addVertexWithUV(half, -half, 0, 0, 1);
        tessellator.addVertexWithUV(-half, -half, 0, 1, 1);
        tessellator.addVertexWithUV(-half, half, 0, 1, 0);
        tessellator.draw();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
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
