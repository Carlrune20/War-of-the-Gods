package com.warofthegods.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import com.warofthegods.WarOfTheGods;
import com.warofthegods.client.model.JsonModel;

/**
 * Draws one of the mod's own items, such as Zamorak's staff or Silverlight, as a model of many small parts rather than
 * a flat picture, in hand and lying on the ground; parts that glow, such as a gem, glow in the dark. In the inventory
 * it shows its icon, drawn from the same model. The model runs along y, its top up (-y, as models go), held where
 * y = 0.
 */
public class RenderModelItem implements IItemRenderer {

    /** Where the hand holds an item, from its bottom corner, in the square a held item is drawn in. */
    private static final float GRIP = 0.82F;
    private static final float GRIP_UP = 0.2F;

    private final JsonModel model;
    private final ResourceLocation texture;
    /** A bow's models drawn back, as far as each stage of pulling it goes, with their textures. */
    private final JsonModel[] pulling;
    private final ResourceLocation[] pullingTextures;
    /** Blocks of the held item's square a unit of the model takes up. */
    private final float scale;
    /** How far along y the model's middle is from where it is held, in units. */
    private final float middle;
    private final Hold hold;

    /** How an item is held in the player's own view. */
    public enum Hold {
        /** As a sword is. */
        ONE_HAND,
        /** A godsword: held up before them, swaying a little, and swung in a great chop. */
        HEAVY,
        /** A bow held out in the middle of the grip: drawn smaller and further up, so the whole of it shows. */
        BOW
    }

    /** How much smaller a bow is drawn in the player's own view, and how far up it is moved, in the item's square. */
    private static final float BOW_IN_VIEW = 0.6F, BOW_LIFT = 0.45F;
    /** Where a bow's picture is held, in its square, and how much smaller the model is drawn to fit it. */
    private static final float BOW_GRIP_X = 0.78F, BOW_GRIP_Y = 0.53F, BOW_IN_HAND = 0.85F;
    /** Ticks a bow has been drawn for to reach each stage of pulling, as the bow's own pictures go. */
    private static final int[] PULL_TICKS = { 1, 14, 18 };

    /**
     * The model in models/name.json with its texture, drawn so a unit of the model is scale of the square an item is
     * drawn in, and its middle middle units along from where it is held.
     */
    public RenderModelItem(String name, float scale, float middle) {
        this(name, scale, middle, Hold.ONE_HAND);
    }

    /** As above, held in the player's own view as hold says. */
    public RenderModelItem(String name, float scale, float middle, Hold hold) {
        this.hold = hold;
        model = new JsonModel(new ModelBase() {}, name);
        texture = new ResourceLocation(WarOfTheGods.MODID, "textures/models/" + name + ".png");
        int stages = hold == Hold.BOW ? PULL_TICKS.length : 0;
        pulling = new JsonModel[stages];
        pullingTextures = new ResourceLocation[stages];
        for (int i = 0; i < stages; i++) {
            pulling[i] = new JsonModel(new ModelBase() {}, name + "_pulling_" + i);
            pullingTextures[i] = new ResourceLocation(
                WarOfTheGods.MODID,
                "textures/models/" + name + "_pulling_" + i + ".png");
        }
        this.scale = scale;
        this.middle = middle;
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON
            || type == ItemRenderType.ENTITY;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return type == ItemRenderType.ENTITY
            && (helper == ItemRendererHelper.ENTITY_ROTATION || helper == ItemRendererHelper.ENTITY_BOBBING);
    }

    /** A heavy item in the player's own view: raised and swaying as they breathe, chopped down hard as they swing. */
    private static void heavyInView() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        float swing = player.getSwingProgress(1.0F);
        float sway = MathHelper.sin(player.ticksExisted * 0.07F) * 2.5F;
        // About the hand: leaning back over the shoulder at rest, the chop sweeping it forward and down.
        float chop = swing > 0 ? MathHelper.sin(swing * (float) Math.PI) * 70 : 0;
        float lift = swing > 0 && swing < 0.35F ? swing / 0.35F * 25 : 0;
        GL11.glRotatef(15 + sway + lift - chop, 0, 0, 1);
    }

    /** How far the holder has drawn this bow, from 0 to the last stage, or -1 if they are not drawing it. */
    private int pullStage(ItemStack item, Object... data) {
        if (pulling.length == 0 || data.length < 2 || !(data[1] instanceof EntityPlayer)) {
            return -1;
        }
        EntityPlayer player = (EntityPlayer) data[1];
        if (player.getItemInUse() == null || player.getItemInUse()
            .getItem() != item.getItem()) {
            return -1;
        }
        int ticks = player.getItemInUseDuration();
        for (int i = PULL_TICKS.length - 1; i >= 0; i--) {
            if (ticks >= PULL_TICKS[i]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * In another's hand, the player turns the item as an item held like a sword; a bow is held as the bow is, its
     * string toward them.
     */
    private static void bowInHand() {
        // Undo the sword's turn...
        float f = 0.625F;
        GL11.glRotatef(-45, 0, 1, 0);
        GL11.glRotatef(100, 1, 0, 0);
        GL11.glScalef(1 / f, -1 / f, 1 / f);
        GL11.glTranslatef(0, -0.1875F, 0);
        // ...and turn it as the bow's.
        GL11.glTranslatef(0, 0.125F, 0.3125F);
        GL11.glRotatef(-20, 0, 1, 0);
        GL11.glScalef(f, -f, f);
        GL11.glRotatef(-100, 1, 0, 0);
        GL11.glRotatef(45, 0, 1, 0);
        GL11.glTranslatef(BOW_GRIP_X, BOW_GRIP_Y, -0.03125F);
        GL11.glRotatef(45, 0, 0, 1);
        GL11.glScalef(BOW_IN_HAND, BOW_IN_HAND, BOW_IN_HAND);
        // The bow's picture has its string on the far side from where the model has it.
        GL11.glRotatef(180, 0, 1, 0);
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        GL11.glPushMatrix();
        if (type == ItemRenderType.ENTITY) {
            // Standing on its end, a little tilted, turning where it lies.
            GL11.glTranslatef(0, 0.25F, 0);
            GL11.glRotatef(20, 0, 0, 1);
            GL11.glScalef(1.2F, 1.2F, 1.2F);
            GL11.glTranslatef(0, middle * scale, 0);
        } else if (hold == Hold.BOW && type == ItemRenderType.EQUIPPED) {
            bowInHand();
        } else {
            // Across the square from its bottom right to its top left, as a sword is drawn, held near the bottom.
            GL11.glTranslatef(GRIP, GRIP_UP, -0.03125F);
            GL11.glRotatef(45, 0, 0, 1);
            if (hold == Hold.HEAVY && type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
                heavyInView();
            } else if (hold == Hold.BOW && type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
                GL11.glTranslatef(0, BOW_LIFT, 0);
                GL11.glScalef(BOW_IN_VIEW, BOW_IN_VIEW, BOW_IN_VIEW);
            }
        }
        // Models are drawn upside down; turned over, top is up.
        GL11.glRotatef(180, 1, 0, 0);
        int stage = type == ItemRenderType.ENTITY ? -1 : pullStage(item, data);
        JsonModel drawn = stage < 0 ? model : pulling[stage];
        Minecraft.getMinecraft().renderEngine.bindTexture(stage < 0 ? texture : pullingTextures[stage]);
        drawn.render(scale);
        drawn.renderGlow(scale);
        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.locationItemsTexture);
        GL11.glPopMatrix();
    }
}
