package com.warofthegods.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;

import com.warofthegods.WarOfTheGods;

/**
 * Draws a unit with the player model and the soldier skin. Its team shows in the color of its leather armor and of
 * the name above its head, both of which vanilla draws.
 */
public class RenderUnit extends RenderBiped {

    private static final ResourceLocation SKIN = new ResourceLocation(WarOfTheGods.MODID, "textures/entity/unit.png");

    public RenderUnit() {
        super(new ModelBiped(), 0.5F);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityLiving entity) {
        return SKIN;
    }
}
