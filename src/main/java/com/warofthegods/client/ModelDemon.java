package com.warofthegods.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * A lesser demon: the player model with two horns on its head and a tail that sways behind it as it walks. Both are
 * drawn from the corner of the skin the player model leaves unused, right of the arm.
 */
public class ModelDemon extends ModelBiped {

    private final ModelRenderer tail;

    public ModelDemon() {
        super(0, 0, 64, 32);
        for (int side = -1; side <= 1; side += 2) {
            ModelRenderer horn = new ModelRenderer(this, 56, 16);
            horn.addBox(-0.5F, -3, -0.5F, 1, 3, 1);
            horn.setRotationPoint(side * 3, -8, -1);
            horn.rotateAngleZ = side * 0.35F;
            horn.rotateAngleX = -0.2F;
            bipedHead.addChild(horn);
        }
        tail = new ModelRenderer(this, 56, 20);
        tail.addBox(-0.5F, 0, -0.5F, 1, 8, 1);
        tail.setRotationPoint(0, 10, 2);
        bipedBody.addChild(tail);
    }

    @Override
    public void setRotationAngles(float swing, float swingAmount, float age, float yaw, float pitch, float scale,
        Entity entity) {
        super.setRotationAngles(swing, swingAmount, age, yaw, pitch, scale, entity);
        // Held out behind, swinging side to side with each step and a little at rest.
        tail.rotateAngleX = 0.7F + MathHelper.cos(age * 0.1F) * 0.08F;
        tail.rotateAngleZ = MathHelper.cos(swing * 0.6662F) * 0.4F * swingAmount;
    }
}
