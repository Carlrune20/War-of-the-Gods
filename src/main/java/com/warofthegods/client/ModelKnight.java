package com.warofthegods.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

import com.warofthegods.client.model.JsonModel;
import com.warofthegods.game.UnitKind;

/**
 * A knight in full plate: the player model with a visored helm, broad pauldrons, gauntlet cuffs, knee guards and
 * sabatons. Black and White Knights wear a plume on the helm, drawn on its own by {@link #renderPlume} so it can be
 * tinted in the team's color. An Elite Black Knight adds a cape that swings as he walks; a Justiciar trades the plume
 * for a crown of spikes and wears a plated skirt. Each look's parts come from its own model, models/white_knight.json,
 * black_knight.json, elite_black_knight.json and justiciar.json, made in Blockbench; they wear their units' 64x64
 * skins. Their head, body, arms and legs move as the player model's do; this only adds the plume, cape and skirt.
 */
public class ModelKnight extends ModelBiped {

    private static final String PLUME = "plume";

    private final JsonModel whiteKnight;
    private final JsonModel blackKnight;
    private final JsonModel eliteBlackKnight;
    private final JsonModel justiciar;
    private JsonModel model;

    public ModelKnight() {
        super(0, 0, 64, 64);
        whiteKnight = new JsonModel(this, "white_knight");
        blackKnight = new JsonModel(this, "black_knight");
        eliteBlackKnight = new JsonModel(this, "elite_black_knight");
        justiciar = new JsonModel(this, "justiciar");
        model = whiteKnight;
    }

    /** Sets which knight it draws next: a White or Black Knight, or what its elite upgrade makes of him. */
    public void setStyle(UnitKind kind, boolean elite) {
        if (kind == UnitKind.BLACK_KNIGHT) {
            model = elite ? eliteBlackKnight : blackKnight;
        } else {
            model = elite ? justiciar : whiteKnight;
        }
    }

    /** Whether the knight it draws next wears a plume. */
    public boolean isPlumed() {
        return model.has(PLUME);
    }

    @Override
    public void render(Entity entity, float swing, float swingAmount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(swing, swingAmount, age, yaw, pitch, scale, entity);
        model.show(PLUME, false);
        model.render(scale);
        model.show(PLUME, true);
    }

    @Override
    public void setRotationAngles(float swing, float swingAmount, float age, float yaw, float pitch, float scale,
        Entity entity) {
        super.setRotationAngles(swing, swingAmount, age, yaw, pitch, scale, entity);
        model.resetPose();
        // The player model's parts, from where they rest in it.
        model.follow("head", bipedHead, 0, 0, 0);
        model.follow("body", bipedBody, 0, 0, 0);
        model.follow("right_arm", bipedRightArm, -5, 2, 0);
        model.follow("left_arm", bipedLeftArm, 5, 2, 0);
        model.follow("right_leg", bipedRightLeg, -1.9F, 12, 0);
        model.follow("left_leg", bipedLeftLeg, 1.9F, 12, 0);
        // The cape hangs a little away from the back and flies out behind as he walks; the skirt follows the legs.
        model.turn("cape", swingAmount * 0.5F + MathHelper.cos(age * 0.08F) * 0.03F, 0, 0);
        float stride = Math.abs(MathHelper.cos(swing * 0.6662F) * 1.4F * swingAmount) * 0.5F;
        model.turn("skirt_front", -stride, 0, 0);
        model.turn("skirt_back", stride, 0, 0);
    }

    /**
     * Draws the plume alone, for the renderer to tint in the team's color; it takes the head's place and turn from the
     * last {@link #render}.
     */
    public void renderPlume(float scale) {
        model.renderOnly(PLUME, scale);
    }
}
