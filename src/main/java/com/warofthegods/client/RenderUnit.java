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
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
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
 * model of its own (see {@link ModelDemon}) and stands tallest of all, its belt and arm bands in its team's color; so
 * does a centaur (see {@link ModelCentaur}), its blanket, belt, quiver strap and arm bands in its team's color. A
 * hero's protection
 * prayer shows above his name while it is up. Knights wear full plate with a plume in their team's color, and their
 * elite upgrades change their armor. Cavalry sit on a horse, white for Saradomin and black for Zamorak, drawn
 * with the horse's own model; it is part of the unit, not a horse of its own.
 */
public class RenderUnit extends RenderBiped {

    private static final ResourceLocation OVERLAY = new ResourceLocation(
        WarOfTheGods.MODID,
        "textures/entity/unit/team_overlay.png");
    /** The lesser, greater and black demon's own textures, and where on them their team's color goes. */
    private static final ResourceLocation[] DEMON = demonTextures("");
    private static final ResourceLocation[] DEMON_TEAM = demonTextures("_team");
    /** A centaur's own texture, and where on it its team's color goes. */
    private static final ResourceLocation CENTAUR = new ResourceLocation(
        WarOfTheGods.MODID,
        "textures/models/centaur.png");
    private static final ResourceLocation CENTAUR_TEAM = new ResourceLocation(
        WarOfTheGods.MODID,
        "textures/models/centaur_team.png");
    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();
    /**
     * Bandos's creatures' textures, and where on them their team's color goes, by texture name: the unit kind's id,
     * and for goblins and hobgoblins that show their team's armor that with _padded or _plate after it.
     */
    private static final Map<String, ResourceLocation> CREATURE = new HashMap<>();
    private static final Map<String, ResourceLocation> CREATURE_TEAM = new HashMap<>();

    static {
        for (UnitKind kind : UnitKind.values()) {
            if (kind.creatureModel() != null) {
                String[] looks = kind.showsArmor() ? new String[] { "", "_padded", "_plate" } : new String[] { "" };
                for (String look : looks) {
                    String name = kind.id + look;
                    CREATURE.put(name, new ResourceLocation(WarOfTheGods.MODID, "textures/models/" + name + ".png"));
                    CREATURE_TEAM
                        .put(name, new ResourceLocation(WarOfTheGods.MODID, "textures/models/" + name + "_team.png"));
                }
            }
        }
    }

    /**
     * The name of the texture one of Bandos's creatures is drawn with: a goblin or hobgoblin wearing its team's armor,
     * which the server puts on it as it does on a warrior, is drawn in padded or plate armor of its own instead.
     */
    private static String creatureTexture(EntityUnit unit) {
        UnitKind kind = unit.getWatchedKind();
        ItemStack chest = kind.showsArmor() ? unit.getEquipmentInSlot(3) : null;
        if (chest != null && chest.getItem() == Items.iron_chestplate) {
            return kind.id + "_plate";
        }
        if (chest != null && chest.getItem() == Items.chainmail_chestplate) {
            return kind.id + "_padded";
        }
        return kind.id;
    }

    /** How much of a spiritual warrior, ranger or mage shows; the rest is see-through. */
    private static final float SPIRIT_ALPHA = 0.6F;

    private static ResourceLocation[] demonTextures(String suffix) {
        String[] names = { "lesser_demon", "greater_demon", "black_demon" };
        ResourceLocation[] textures = new ResourceLocation[names.length];
        for (int i = 0; i < names.length; i++) {
            textures[i] = new ResourceLocation(WarOfTheGods.MODID, "textures/models/" + names[i] + suffix + ".png");
        }
        return textures;
    }

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

    /** Elite Black Knights' and Justiciars' skins, by faction and the knight's own kind. */
    private static final Map<String, ResourceLocation> ELITE_SKINS = new HashMap<>();

    static {
        for (Faction faction : Faction.values()) {
            for (UnitKind kind : UnitKind.values()) {
                if (kind.eliteUpgrade() != null && !kind.hasOwnModel()) {
                    ELITE_SKINS.put(
                        faction.id + "_" + kind.id,
                        new ResourceLocation(
                            WarOfTheGods.MODID,
                            "textures/entity/unit/" + faction.id + "_" + kind.id + "_elite.png"));
                }
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
    private final ModelCentaur centaurModel = new ModelCentaur();
    private final ModelKnight knightModel = new ModelKnight();
    private final ModelCreature creatureModel = new ModelCreature();
    /** The horse drawn under each rider, kept only to be drawn; it is in no world's entity list. */
    private final Map<EntityUnit, EntityHorse> horses = new WeakHashMap<>();

    public RenderUnit() {
        super(new ModelBiped(), 0.5F);
        manModel = modelBipedMain;
    }

    /**
     * An archer drawing its bow holds it up in both hands, as a player does; a goblin axe-thrower swings its axe
     * instead.
     */
    @Override
    public void doRender(EntityLiving entity, double x, double y, double z, float yaw, float partialTicks) {
        boolean aiming = ((EntityUnit) entity).getDrawTicks() > 0 && !((EntityUnit) entity).getWatchedKind()
            .throwsAxes();
        modelBipedMain.aimedBow = aiming;
        field_82423_g.aimedBow = aiming;
        field_82425_h.aimedBow = aiming;
        EntityUnit unit = (EntityUnit) entity;
        // A lesser demon and a centaur have models of their own, and knights wear full plate with a plume.
        UnitKind kind = unit.getWatchedKind();
        if (kind == UnitKind.LESSER_DEMON) {
            demonModel.setTier(unit.getWatchedEliteTier());
            modelBipedMain = demonModel;
        } else if (kind == UnitKind.CENTAUR) {
            modelBipedMain = centaurModel;
        } else if (kind.creatureModel() != null) {
            creatureModel.setModel(kind.creatureModel());
            modelBipedMain = creatureModel;
        } else if (kind.eliteUpgrade() != null) {
            boolean elite = unit.isWatchedElite();
            knightModel.setStyle(kind, elite);
            modelBipedMain = knightModel;
        } else {
            modelBipedMain = manModel;
        }
        mainModel = modelBipedMain;
        modelBipedMain.aimedBow = aiming;
        if (unit.getWatchedKind()
            .isMounted()) {
            renderHorse(unit, x, y, z, yaw, partialTicks);
        }
        super.doRender(entity, x, y, z, yaw, partialTicks);
    }

    /**
     * Arrows that stick in a unit are drawn in its arms and body; a demon's and a centaur's parts are their own, so
     * none show on them.
     */
    @Override
    protected void renderArrowsStuckInEntity(EntityLivingBase entity, float partialTicks) {
        UnitKind kind = ((EntityUnit) entity).getWatchedKind();
        if (!kind.hasOwnModel() && kind.creatureModel() == null) {
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
        float size = kind.scale(((EntityUnit) entity).getWatchedEliteTier());
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
            return DEMON[Math.min(DEMON.length - 1, unit.getWatchedEliteTier())];
        }
        if (unit.getWatchedKind() == UnitKind.CENTAUR) {
            return CENTAUR;
        }
        if (unit.getWatchedKind()
            .creatureModel() != null) {
            return CREATURE.get(creatureTexture(unit));
        }
        String key = unit.getWatchedFaction().id + "_" + unit.getWatchedKind().id;
        return unit.isWatchedElite() && ELITE_SKINS.containsKey(key) ? ELITE_SKINS.get(key) : SKINS.get(key);
    }

    /**
     * Bandos's creatures wear no player armor over their models: an armored goblin's or hobgoblin's texture shows its
     * armor.
     */
    @Override
    protected int shouldRenderPass(EntityLiving entity, int pass, float partialTicks) {
        if (((EntityUnit) entity).getWatchedKind()
            .creatureModel() != null) {
            return -1;
        }
        return super.shouldRenderPass(entity, pass, partialTicks);
    }

    /**
     * Draws the team bands over the model, tinted the team's color. They go with the model itself, not in a render
     * pass of their own: there are only four passes, and the armor needs all of them, from helmet to boots.
     */
    @Override
    protected void renderModel(EntityLivingBase entity, float swing, float swingAmount, float age, float yaw,
        float pitch, float scale) {
        UnitKind kind = ((EntityUnit) entity).getWatchedKind();
        if (mainModel == creatureModel) {
            renderCreature(entity, kind, swing, swingAmount, age, yaw, pitch, scale);
            return;
        }
        super.renderModel(entity, swing, swingAmount, age, yaw, pitch, scale);
        if (entity.isInvisible()) {
            return;
        }
        int color = ((EntityUnit) entity).getTeamColor();
        if (mainModel == demonModel) {
            // Its belt, loincloth and arm bands, drawn again over themselves in the team's color.
            bindTexture(DEMON_TEAM[Math.min(DEMON_TEAM.length - 1, ((EntityUnit) entity).getWatchedEliteTier())]);
            GL11.glColor3f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
            GL11.glDepthFunc(GL11.GL_EQUAL);
            demonModel.renderAgain(scale);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glColor4f(1, 1, 1, 1);
            return;
        }
        if (mainModel == centaurModel) {
            // Its blanket, belt, quiver strap and arm bands, drawn again over themselves in the team's color.
            bindTexture(CENTAUR_TEAM);
            GL11.glColor3f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
            GL11.glDepthFunc(GL11.GL_EQUAL);
            centaurModel.renderAgain(scale);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glColor4f(1, 1, 1, 1);
            return;
        }
        if (mainModel == knightModel && knightModel.isPlumed()) {
            // The plume, in the team's color, from the skin still bound.
            GL11.glColor3f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
            knightModel.renderPlume(scale);
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

    /**
     * One of Bandos's creatures, its tunic, belt or bands drawn again over themselves in the team's color; the spirits
     * of the bastion are half see-through.
     */
    private void renderCreature(EntityLivingBase entity, UnitKind kind, float swing, float swingAmount, float age,
        float yaw, float pitch, float scale) {
        boolean spirit = kind.isSpirit();
        if (spirit) {
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            GL11.glColor4f(1, 1, 1, SPIRIT_ALPHA);
        }
        super.renderModel(entity, swing, swingAmount, age, yaw, pitch, scale);
        if (!entity.isInvisible()) {
            int color = ((EntityUnit) entity).getTeamColor();
            bindTexture(CREATURE_TEAM.get(creatureTexture((EntityUnit) entity)));
            GL11.glColor4f(
                (color >> 16 & 255) / 255F,
                (color >> 8 & 255) / 255F,
                (color & 255) / 255F,
                spirit ? SPIRIT_ALPHA : 1);
            GL11.glDepthFunc(GL11.GL_EQUAL);
            creatureModel.renderAgain(scale);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
        }
        GL11.glColor4f(1, 1, 1, 1);
        if (spirit) {
            GL11.glDisable(GL11.GL_BLEND);
        }
    }
}
