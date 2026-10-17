package com.warofthegods.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

import com.warofthegods.client.model.JsonModel;

/**
 * Saradomin's centaur: a bearded man from the waist up on a chestnut horse's body, a longbow in his left hand and a
 * quiver on his back. Its parts come from models/centaur.json (made by tools/models/generate.py); this only moves
 * them. It walks as a horse does, a foreleg with the hind leg across from it, its tail swishing; drawing its bow, it
 * holds it out upright in its left hand and pulls the string back to its chin with its right.
 */
public class ModelCentaur extends ModelBiped {

    private static final float HALF_PI = (float) Math.PI / 2;
    /** How far its arms are turned from hanging straight down as it was made: the bow arm forward a little more. */
    private static final float BOW_ARM_REST = -0.35F;
    private static final float STRING_ARM_REST = -0.12F;

    private final JsonModel model;

    public ModelCentaur() {
        super(0, 0, 128, 128);
        model = new JsonModel(this, "centaur");
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
        float stride = swing * 0.6662F;
        float step = MathHelper.cos(stride) * 1.1F * swingAmount;
        float breathe = MathHelper.sin(age * 0.07F);
        // Legs: diagonal pairs swing together, the shins folding as each leg comes forward.
        model.turn("front_left_leg", step, 0, 0);
        model.turn("back_right_leg", step, 0, 0);
        model.turn("front_right_leg", -step, 0, 0);
        model.turn("back_left_leg", -step, 0, 0);
        float fold = Math.max(0, MathHelper.sin(stride)) * 0.6F * swingAmount;
        float foldOther = Math.max(0, -MathHelper.sin(stride)) * 0.6F * swingAmount;
        model.turn("front_left_shin", fold, 0, 0);
        model.turn("back_right_shin", fold, 0, 0);
        model.turn("front_right_shin", foldOther, 0, 0);
        model.turn("back_left_shin", foldOther, 0, 0);
        model.turn("body", MathHelper.cos(stride * 2) * 0.02F * swingAmount, 0, 0);
        model.turn("torso", breathe * 0.02F, step * 0.05F, 0);
        model.turn("head", pitch / 57.3F, yaw / 57.3F, 0);
        model.turn("tail", Math.abs(step) * 0.3F, MathHelper.sin(age * 0.08F) * 0.15F + step * 0.2F, 0);
        model.turn("tail_end", 0, MathHelper.sin(age * 0.08F - 0.6F) * 0.12F, 0);
        if (aimedBow) {
            // The bow held out upright toward the target, the string drawn back to the chin.
            float aim = pitch / 57.3F;
            model.turn("left_arm", -HALF_PI - BOW_ARM_REST + aim, 0.15F, 0.1F);
            model.turn("bow", HALF_PI + BOW_ARM_REST, 0, 0);
            model.turn("right_arm", -HALF_PI - STRING_ARM_REST + aim, -0.55F, -0.1F);
        } else {
            float sway = MathHelper.cos(stride) * 0.25F * swingAmount;
            model.turn("left_arm", -sway, 0, breathe * 0.03F);
            model.turn("right_arm", sway, 0, -breathe * 0.03F);
        }
    }
}
