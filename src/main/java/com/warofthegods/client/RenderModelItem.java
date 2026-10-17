package com.warofthegods.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
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
    /** Blocks of the held item's square a unit of the model takes up. */
    private final float scale;
    /** How far along y the model's middle is from where it is held, in units. */
    private final float middle;

    /**
     * The model in models/name.json with its texture, drawn so a unit of the model is scale of the square an item is
     * drawn in, and its middle middle units along from where it is held.
     */
    public RenderModelItem(String name, float scale, float middle) {
        model = new JsonModel(new ModelBase() {}, name);
        texture = new ResourceLocation(WarOfTheGods.MODID, "textures/models/" + name + ".png");
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

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        GL11.glPushMatrix();
        if (type == ItemRenderType.ENTITY) {
            // Standing on its end, a little tilted, turning where it lies.
            GL11.glTranslatef(0, 0.25F, 0);
            GL11.glRotatef(20, 0, 0, 1);
            GL11.glScalef(1.2F, 1.2F, 1.2F);
            GL11.glTranslatef(0, middle * scale, 0);
        } else {
            // Across the square from its bottom right to its top left, as a sword is drawn, held near the bottom.
            GL11.glTranslatef(GRIP, GRIP_UP, -0.03125F);
            GL11.glRotatef(45, 0, 0, 1);
        }
        // Models are drawn upside down; turned over, top is up.
        GL11.glRotatef(180, 1, 0, 0);
        Minecraft.getMinecraft().renderEngine.bindTexture(texture);
        model.render(scale);
        model.renderGlow(scale);
        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.locationItemsTexture);
        GL11.glPopMatrix();
    }
}
