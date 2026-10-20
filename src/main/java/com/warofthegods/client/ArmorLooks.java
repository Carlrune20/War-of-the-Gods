package com.warofthegods.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.warofthegods.WarOfTheGods;
import com.warofthegods.item.ItemDarkIronArmor;
import com.warofthegods.item.ItemDragonArmor;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Armor as Old School RuneScape has it, on players, units, commanders' bodies, zombies and skeletons: iron, Zamorak's
 * dark iron, runite (Minecraft's diamond) and dragon are a full helm with a red band, a platebody with great
 * pauldrons, platelegs and boots, silver, dark, blue-green or red; the leather cap is a cowl, dyed as leather is.
 */
public final class ArmorLooks {

    /** What a renderer's pass returns for armor it draws once, or once with the enchanted glint over it. */
    private static final int DRAWN = 1, DRAWN_GLINTING = 15;
    /** What {@link #renderPass} returns when Minecraft's own drawing will do. */
    public static final int VANILLA = -2;

    private static ArmorLooks instance;

    private final ModelArmor fullHelm = new ModelArmor("full_helm");
    /** Dragon's full helm, which has horns. */
    private final ModelArmor dragonHelm = new ModelArmor("full_helm_dragon");
    private final ModelArmor cowl = new ModelArmor("leather_cowl");
    private final ModelArmor plate = new ModelArmor("plate");
    private final ResourceLocation cowlTexture = texture("leather_cowl");
    /** By look: silver iron, dark iron, runite and dragon. */
    private final ResourceLocation[] helms = { texture("full_helm"), texture("full_helm_dark"),
        texture("full_helm_runite"), texture("full_helm_dragon") };
    private final ResourceLocation[] plates = { texture("plate"), texture("plate_dark"), texture("plate_runite"),
        texture("plate_dragon") };

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

    /** Draws players' armor this way too. */
    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerArmor(RenderPlayerEvent.SetArmorModel event) {
        int result = renderPass(
            event.renderer,
            event.entityPlayer,
            event.stack,
            3 - event.slot,
            event.renderer.modelBipedMain);
        if (result != VANILLA) {
            event.result = result;
        }
    }

    /** Which plate a piece of armor is: 0 for iron, 1 dark iron, 2 runite, 3 dragon; -1 if it is none of them. */
    private static int look(ItemArmor item) {
        if (item instanceof ItemDarkIronArmor) {
            return 1;
        }
        if (item instanceof ItemDragonArmor) {
            return 3;
        }
        if (item.getArmorMaterial() == ItemArmor.ArmorMaterial.IRON) {
            return 0;
        }
        return item.getArmorMaterial() == ItemArmor.ArmorMaterial.DIAMOND ? 2 : -1;
    }

    /**
     * Gets a piece of worn armor ready to be drawn in a renderer's armor pass, as shouldRenderPass does: binds its
     * texture, tints a cowl with its dye and hands the renderer the model to draw it with, posed as the wearer's own
     * model is.
     *
     * @param pass the armor pass: 0 for the helmet, 1 the chestplate, 2 the leggings, 3 the boots
     * @param worn the model the wearer is drawn with
     * @return what shouldRenderPass returns, or {@link #VANILLA} if this is armor Minecraft draws as it is
     */
    public int renderPass(RendererLivingEntity renderer, EntityLivingBase wearer, ItemStack stack, int pass,
        ModelBiped worn) {
        if (stack == null || !(stack.getItem() instanceof ItemArmor)) {
            return VANILLA;
        }
        ItemArmor item = (ItemArmor) stack.getItem();
        int look = look(item);
        ModelBiped model;
        ResourceLocation texture;
        int color = 0xFFFFFF;
        if (pass == 0 && item.getArmorMaterial() == ItemArmor.ArmorMaterial.CLOTH) {
            model = cowl;
            texture = cowlTexture;
            color = item.getColor(stack);
        } else if (look < 0) {
            return VANILLA;
        } else if (pass == 0) {
            model = look == 3 ? dragonHelm : fullHelm;
            texture = helms[look];
        } else {
            plate.showPiece(pass);
            model = plate;
            texture = plates[look];
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
        GL11.glColor3f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
        return stack.isItemEnchanted() ? DRAWN_GLINTING : DRAWN;
    }
}
