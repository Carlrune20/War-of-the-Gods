package com.warofthegods.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

import com.warofthegods.client.model.JsonModel;

/**
 * A lesser demon, as in Old School RuneScape: hunched and heavy, horned, fanged and clawed, on legs bent like a
 * beast's, with a long tail. Its parts come from models/lesser_demon.json (made by tools/models/generate.py); this only
 * moves them. It walks with a heavy swing of its arms, its tail swaying behind it; it opens its jaw and rakes with its
 * right claw when it strikes, and throws up its arm when it hurls a fireball.
 */
public class ModelDemon extends ModelBiped {

    private static final int TAIL_LINKS = 9;

    private final JsonModel model;

    public ModelDemon() {
        super(0, 0, 128, 128);
        model = new JsonModel(this, "lesser_demon");
    }

    @Override
    public void render(Entity entity, float swing, float swingAmount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(swing, swingAmount, age, yaw, pitch, scale, entity);
        model.render(scale);
    }

    /** Draws it over again, on top of itself, for its team's color. */
    public void renderAgain(float scale) {
        model.render(scale);
    }

    @Override
    public void setRotationAngles(float swing, float swingAmount, float age, float yaw, float pitch, float scale,
        Entity entity) {
        model.resetPose();
        float stride = swing * 0.5F;
        float step = MathHelper.cos(stride) * swingAmount;
        float breathe = MathHelper.sin(age * 0.07F);
        model.turn("body", breathe * 0.025F + Math.abs(step) * 0.06F, step * 0.12F, 0);
        model.turn("head", pitch / 57.3F * 0.6F, yaw / 57.3F * 0.7F, 0);
        model.turn("jaw", 0.06F + breathe * 0.05F + onGround * 0.6F, 0, 0);
        // Legs: the thigh swings, the shin folds in under it as the leg comes forward.
        model.turn("left_leg", step * 0.8F, 0, 0);
        model.turn("right_leg", -step * 0.8F, 0, 0);
        model.turn("left_shin", Math.max(0, MathHelper.sin(stride)) * 0.5F * swingAmount, 0, 0);
        model.turn("right_shin", Math.max(0, -MathHelper.sin(stride)) * 0.5F * swingAmount, 0, 0);
        // Arms hang heavy and swing against the legs.
        float leftArm = -step * 0.6F;
        float rightArm = step * 0.6F;
        float rightForearm = 0;
        float sway = breathe * 0.04F;
        if (onGround > 0) {
            // A raking blow of the right claw, from up and back down across.
            float blow = MathHelper.sin(onGround * (float) Math.PI);
            rightArm -= blow * 1.9F;
            rightForearm -= blow * 0.6F;
            leftArm += blow * 0.3F;
        }
        if (aimedBow) {
            // Hurling a fireball: the right arm thrown up and forward.
            rightArm = -2.2F;
            rightForearm = -0.3F;
        }
        model.turn("left_arm", leftArm, 0, sway);
        model.turn("right_arm", rightArm, 0, -sway);
        model.turn("right_forearm", rightForearm, 0, 0);
        // The tail sways side to side, more as it walks, each link a little behind the one before.
        for (int i = 0; i < TAIL_LINKS; i++) {
            float wave = MathHelper.sin(age * 0.09F - i * 0.55F) * (0.08F + i * 0.012F);
            float walk = MathHelper.cos(stride - i * 0.4F) * 0.18F * swingAmount;
            model.turn("tail" + i, MathHelper.sin(age * 0.05F - i * 0.3F) * 0.03F, wave + walk, 0);
        }
    }
}
