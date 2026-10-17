package com.warofthegods.client;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityHorse;
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
 * color too. Archers raise their bow as they draw it, and heroes stand taller than the rest. A lesser demon has a
 * model of its own (see {@link ModelDemon}) and stands tallest of all, its belt and arm bands in its team's color. A
 * hero's protection
 * prayer shows above his name while it is up. Cavalry sit on a horse, white for Saradomin and black for Zamorak, drawn
 * with the horse's own model; it is part of the unit, not a horse of its own.
 */
public class RenderUnit extends RenderBiped {

    private static final ResourceLocation OVERLAY = new ResourceLocation(
        WarOfTheGods.MODID,
        "textures/entity/unit/team_overlay.png");
    /** A lesser demon's own texture, and where on it its team's color goes. */
    private static final ResourceLocation DEMON = new ResourceLocation(
        WarOfTheGods.MODID,
        "textures/models/lesser_demon.png");
    private static final ResourceLocation DEMON_TEAM = new ResourceLocation(
        WarOfTheGods.MODID,
        "textures/models/lesser_demon_team.png");
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

    /** How far up a rider sits, in blocks, to be in the saddle. */
    private static final float RIDER_LIFT = 0.7F;
    /** Horse coats: the low byte of a horse's variant. */
    private static final int HORSE_WHITE = 0;
    private static final int HORSE_BLACK = 4;

    private final ModelBiped overlayModel = new ModelBiped(0.08F);
    private final ModelBiped manModel;
    private final ModelDemon demonModel = new ModelDemon();
    /** The horse drawn under each rider, kept only to be drawn; it is in no world's entity list. */
    private final Map<EntityUnit, EntityHorse> horses = new WeakHashMap<>();

    public RenderUnit() {
        super(new ModelBiped(), 0.5F);
        manModel = modelBipedMain;
    }

    /** An archer drawing its bow holds it up in both hands, as a player does. */
    @Override
    public void doRender(EntityLiving entity, double x, double y, double z, float yaw, float partialTicks) {
        boolean aiming = ((EntityUnit) entity).getDrawTicks() > 0;
        modelBipedMain.aimedBow = aiming;
        field_82423_g.aimedBow = aiming;
        field_82425_h.aimedBow = aiming;
        EntityUnit unit = (EntityUnit) entity;
        // A lesser demon has a model of its own.
        modelBipedMain = unit.getWatchedKind() == UnitKind.LESSER_DEMON ? demonModel : manModel;
        mainModel = modelBipedMain;
        modelBipedMain.aimedBow = aiming;
        if (unit.getWatchedKind()
            .isMounted()) {
            renderHorse(unit, x, y, z, yaw, partialTicks);
        }
        super.doRender(entity, x, y, z, yaw, partialTicks);
    }

    /** Arrows that stick in a unit are drawn in its arms and body; a demon's parts are its own, so none show on it. */
    @Override
    protected void renderArrowsStuckInEntity(EntityLivingBase entity, float partialTicks) {
        if (((EntityUnit) entity).getWatchedKind() != UnitKind.LESSER_DEMON) {
            super.renderArrowsStuckInEntity(entity, partialTicks);
        }
    }

    /** Draws a rider's horse where the rider is, walking as it walks and flashing red when it is hurt. */
    private void renderHorse(EntityUnit unit, double x, double y, double z, float yaw, float partialTicks) {
        EntityHorse horse = horses.get(unit);
        if (horse == null || horse.worldObj != unit.worldObj) {
            horse = new EntityHorse(unit.worldObj);
            horse.setHorseType(0);
            horse.setHorseTamed(true);
            horse.setHorseSaddled(true);
            horses.put(unit, horse);
        }
        int variant = unit.getWatchedFaction() == Faction.ZAMORAK ? HORSE_BLACK : HORSE_WHITE;
        if (horse.getHorseVariant() != variant) {
            horse.setHorseVariant(variant);
        }
        horse.posX = unit.posX;
        horse.posY = unit.posY;
        horse.posZ = unit.posZ;
        horse.prevPosX = unit.prevPosX;
        horse.prevPosY = unit.prevPosY;
        horse.prevPosZ = unit.prevPosZ;
        horse.lastTickPosX = unit.lastTickPosX;
        horse.lastTickPosY = unit.lastTickPosY;
        horse.lastTickPosZ = unit.lastTickPosZ;
        horse.rotationYaw = unit.rotationYaw;
        horse.prevRotationYaw = unit.prevRotationYaw;
        horse.renderYawOffset = unit.renderYawOffset;
        horse.prevRenderYawOffset = unit.prevRenderYawOffset;
        horse.rotationYawHead = unit.rotationYawHead;
        horse.prevRotationYawHead = unit.prevRotationYawHead;
        horse.limbSwing = unit.limbSwing;
        horse.limbSwingAmount = unit.limbSwingAmount;
        horse.prevLimbSwingAmount = unit.prevLimbSwingAmount;
        horse.ticksExisted = unit.ticksExisted;
        horse.hurtTime = unit.hurtTime;
        horse.deathTime = unit.deathTime;
        horse.onGround = unit.onGround;
        RenderManager.instance.renderEntityWithPosYaw(horse, x, y, z, yaw, partialTicks);
    }

    /**
     * Heroes stand a head taller than other units, and a lesser demon taller still; riders sit in the saddle with their
     * legs astride.
     */
    @Override
    protected void preRenderCallback(EntityLivingBase entity, float partialTicks) {
        UnitKind kind = ((EntityUnit) entity).getWatchedKind();
        float size = kind.scale();
        if (size != 1) {
            GL11.glScalef(size, size, size);
        }
        if (kind.isMounted()) {
            modelBipedMain.isRiding = true;
            // Drawn upside down at this point, so up is down.
            GL11.glTranslatef(0, -RIDER_LIFT, 0);
        }
    }

    /** The name above its head, and above that the protection prayer it has up, facing the camera. */
    @Override
    protected void passSpecialRender(EntityLivingBase entity, double x, double y, double z) {
        // Above a rider's head, not where a man on foot would have it.
        if (((EntityUnit) entity).getWatchedKind()
            .isMounted()) {
            y += RIDER_LIFT;
        }
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
        if (unit.getWatchedKind() == UnitKind.LESSER_DEMON) {
            return DEMON;
        }
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
        if (mainModel == demonModel) {
            // Its belt, loincloth and arm bands, drawn again over themselves in the team's color.
            bindTexture(DEMON_TEAM);
            GL11.glColor3f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
            GL11.glDepthFunc(GL11.GL_EQUAL);
            demonModel.renderAgain(scale);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glColor4f(1, 1, 1, 1);
            return;
        }
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
