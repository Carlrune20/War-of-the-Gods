package com.warofthegods.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.warofthegods.WarOfTheGods;
import com.warofthegods.game.Faction;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Armor as Old School RuneScape has it, on players, units and commanders' bodies: the iron helmet is a full helm,
 * silver with a red band, and for Zamorak's teams black, as the rest of their iron armor is; the diamond helmet is the
 * runite full helm, blue-green, and the rest of the diamond armor is tinted to match it; the leather cap is a cowl,
 * dyed
 * as leather is. Whose god armor is comes from the unit wearing it, or for a player from the mark the shop put on it
 * (see {@link Faction#mark}); unmarked iron is silver. Mobs keep Minecraft's own look.
 */
public final class ArmorLooks {

    /** What Minecraft's silver iron is tinted for Zamorak, and its pale diamond for runite. */
    private static final float[] BLACK_IRON = { 0.34F, 0.33F, 0.35F };
    private static final float[] RUNITE = { 0.9F, 0.54F, 0.57F };
    private static final float[] WHITE = { 1, 1, 1 };
    /** What a renderer's pass returns for armor it draws once, or once with the enchanted glint over it. */
    private static final int DRAWN = 1, DRAWN_GLINTING = 15;
    /** What {@link #renderPass} returns when Minecraft's own drawing will do. */
    public static final int VANILLA = -2;

    private static ArmorLooks instance;

    private final ModelHelm fullHelm = new ModelHelm("full_helm");
    private final ModelHelm cowl = new ModelHelm("leather_cowl");
    private final ModelBiped armor = new TintedBiped(1.0F);
    private final ModelBiped leggings = new TintedBiped(0.5F);
    private final ResourceLocation silverHelm = texture("full_helm");
    private final ResourceLocation blackHelm = texture("full_helm_zamorak");
    private final ResourceLocation runiteHelm = texture("full_helm_runite");
    private final ResourceLocation cowlTexture = texture("leather_cowl");
    private IIcon blackHelmIcon;

    private ArmorLooks() {}

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(WarOfTheGods.MODID, "textures/models/" + name + ".png");
    }

    /** The one there is, made the first time it is needed. */
    public static ArmorLooks get() {
        if (instance == null) {
            instance = new ArmorLooks();
        }
        return instance;
    }

    /** Draws players' armor this way, and Zamorak's iron black in the inventory too. */
    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
        IItemRenderer icons = new BlackIronIcons();
        for (Item item : new Item[] { Items.iron_helmet, Items.iron_chestplate, Items.iron_leggings,
            Items.iron_boots }) {
            MinecraftForgeClient.registerItemRenderer(item, icons);
        }
    }

    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Pre event) {
        // Items' atlas: Zamorak's full helm has an icon of its own, its band still red.
        if (event.map.getTextureType() == 1) {
            blackHelmIcon = event.map.registerIcon(WarOfTheGods.MODID + ":zamorak_iron_helmet");
        }
    }

    @SubscribeEvent
    public void onPlayerArmor(RenderPlayerEvent.SetArmorModel event) {
        int result = renderPass(
            event.renderer,
            event.entityPlayer,
            event.stack,
            3 - event.slot,
            Faction.of(event.stack),
            event.renderer.modelBipedMain);
        if (result != VANILLA) {
            event.result = result;
        }
    }

    /**
     * Gets a piece of worn armor ready to be drawn in a renderer's armor pass, as shouldRenderPass does: binds its
     * texture, tints it and hands the renderer the model to draw it with, posed as the wearer's own model is.
     *
     * @param pass    the armor pass: 0 for the helmet, 1 the chestplate, 2 the leggings, 3 the boots
     * @param faction whose god the armor is, for its look; null if no one's
     * @param worn    the model the wearer is drawn with
     * @return what shouldRenderPass returns, or {@link #VANILLA} if this is armor Minecraft draws as it is
     */
    public int renderPass(RendererLivingEntity renderer, EntityLivingBase wearer, ItemStack stack, int pass,
        Faction faction, ModelBiped worn) {
        if (stack == null || !(stack.getItem() instanceof ItemArmor)) {
            return VANILLA;
        }
        ItemArmor item = (ItemArmor) stack.getItem();
        ItemArmor.ArmorMaterial material = item.getArmorMaterial();
        boolean black = material == ItemArmor.ArmorMaterial.IRON && faction == Faction.ZAMORAK;
        ModelBiped model;
        ResourceLocation texture;
        float[] tint = WHITE;
        if (pass == 0 && material == ItemArmor.ArmorMaterial.IRON) {
            model = fullHelm;
            texture = black ? blackHelm : silverHelm;
        } else if (pass == 0 && material == ItemArmor.ArmorMaterial.DIAMOND) {
            model = fullHelm;
            texture = runiteHelm;
        } else if (pass == 0 && material == ItemArmor.ArmorMaterial.CLOTH) {
            model = cowl;
            texture = cowlTexture;
            int color = item.getColor(stack);
            tint = new float[] { (color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F };
        } else if (black || material == ItemArmor.ArmorMaterial.DIAMOND) {
            model = pass == 2 ? leggings : armor;
            texture = RenderBiped.getArmorResource(wearer, stack, pass, null);
            tint = black ? BLACK_IRON : RUNITE;
            model.bipedHead.showModel = false;
            model.bipedHeadwear.showModel = false;
            model.bipedBody.showModel = pass == 1 || pass == 2;
            model.bipedRightArm.showModel = pass == 1;
            model.bipedLeftArm.showModel = pass == 1;
            model.bipedRightLeg.showModel = pass == 2 || pass == 3;
            model.bipedLeftLeg.showModel = pass == 2 || pass == 3;
        } else {
            return VANILLA;
        }
        model.onGround = worn.onGround;
        model.isRiding = worn.isRiding;
        model.isChild = worn.isChild;
        model.isSneak = worn.isSneak;
        model.heldItemLeft = worn.heldItemLeft;
        model.heldItemRight = worn.heldItemRight;
        model.aimedBow = worn.aimedBow;
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);
        renderer.setRenderPassModel(model);
        GL11.glColor3f(tint[0], tint[1], tint[2]);
        return stack.isItemEnchanted() ? DRAWN_GLINTING : DRAWN;
    }

    /** The player model's armor, tinted as it was when drawn, the tint put back to white after. */
    private static class TintedBiped extends ModelBiped {

        TintedBiped(float inflate) {
            super(inflate);
        }

        @Override
        public void render(Entity entity, float swing, float swingAmount, float age, float yaw, float pitch,
            float scale) {
            super.render(entity, swing, swingAmount, age, yaw, pitch, scale);
            GL11.glColor4f(1, 1, 1, 1);
        }
    }

    /** Zamorak's iron armor in the inventory: its own full helm, the rest Minecraft's icons drawn black. */
    private class BlackIronIcons implements IItemRenderer {

        private final RenderItem icons = new RenderItem();

        @Override
        public boolean handleRenderType(ItemStack item, ItemRenderType type) {
            return type == ItemRenderType.INVENTORY && Faction.of(item) == Faction.ZAMORAK;
        }

        @Override
        public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
            return false;
        }

        @Override
        public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
            boolean helm = item.getItem() == Items.iron_helmet && blackHelmIcon != null;
            float[] tint = helm ? WHITE : BLACK_IRON;
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationItemsTexture);
            GL11.glColor4f(tint[0], tint[1], tint[2], 1);
            icons.renderIcon(0, 0, helm ? blackHelmIcon : item.getIconIndex(), 16, 16);
            GL11.glColor4f(1, 1, 1, 1);
        }
    }
}
