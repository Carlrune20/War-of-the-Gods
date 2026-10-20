package com.warofthegods.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;

import org.lwjgl.opengl.GL11;

import com.warofthegods.client.model.JsonModel;

/**
 * A helmet worn in place of Minecraft's box helmet: the full helm or the leather cowl, from models/full_helm.json or
 * leather_cowl.json (made by tools/models/generate.py, see tools/models/armor.py). Its head turns with the wearer's
 * head and a cowl's mantle moves with the body. The color it was tinted with is put back to white once it is drawn.
 */
public class ModelHelm extends ModelBiped {

    private final JsonModel model;

    public ModelHelm(String name) {
        super(0);
        model = new JsonModel(this, name);
    }

    @Override
    public void render(Entity entity, float swing, float swingAmount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(swing, swingAmount, age, yaw, pitch, scale, entity);
        model.resetPose();
        model.follow("head", bipedHead, 0, 0, 0);
        model.follow("mantle", bipedBody, 0, 0, 0);
        model.render(scale);
        GL11.glColor4f(1, 1, 1, 1);
    }
}
