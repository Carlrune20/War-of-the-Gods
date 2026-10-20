package com.warofthegods.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;

import org.lwjgl.opengl.GL11;

import com.warofthegods.client.model.JsonModel;

/**
 * Armor worn in place of Minecraft's: the full helm, the leather cowl or the plate, from models/full_helm.json,
 * leather_cowl.json or plate.json (made by tools/models/generate.py, see tools/models/armor.py). Each part moves with
 * the wearer's part of the same name: the head (and a cowl's mantle, with the body), the body and its belt, the arms,
 * and the legs and the boots on them. The plate shows only the piece being drawn: the platebody, the platelegs with
 * their belt, or the boots. The dye a cowl is tinted with is put back to white once it is drawn.
 */
public class ModelArmor extends ModelBiped {

    /** Where the player model's arms and legs rest, from which the parts' turns and moves are copied. */
    private static final float ARM_X = 5, ARM_Y = 2, LEG_X = 1.9F, LEG_Y = 12;

    private final JsonModel model;

    public ModelArmor(String name) {
        super(0);
        model = new JsonModel(this, name);
    }

    /** Shows the plate's piece for an armor pass: 1 for the platebody, 2 the platelegs, 3 the boots. */
    public void showPiece(int pass) {
        model.show("body", pass == 1);
        model.show("right_arm", pass == 1);
        model.show("left_arm", pass == 1);
        model.show("belt", pass == 2);
        model.show("right_leg", pass == 2);
        model.show("left_leg", pass == 2);
        model.show("right_boot", pass == 3);
        model.show("left_boot", pass == 3);
    }

    @Override
    public void render(Entity entity, float swing, float swingAmount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(swing, swingAmount, age, yaw, pitch, scale, entity);
        model.resetPose();
        model.follow("head", bipedHead, 0, 0, 0);
        model.follow("mantle", bipedBody, 0, 0, 0);
        model.follow("body", bipedBody, 0, 0, 0);
        model.follow("belt", bipedBody, 0, 0, 0);
        model.follow("right_arm", bipedRightArm, -ARM_X, ARM_Y, 0);
        model.follow("left_arm", bipedLeftArm, ARM_X, ARM_Y, 0);
        model.follow("right_leg", bipedRightLeg, -LEG_X, LEG_Y, 0);
        model.follow("left_leg", bipedLeftLeg, LEG_X, LEG_Y, 0);
        model.follow("right_boot", bipedRightLeg, -LEG_X, LEG_Y, 0);
        model.follow("left_boot", bipedLeftLeg, LEG_X, LEG_Y, 0);
        model.render(scale);
        GL11.glColor4f(1, 1, 1, 1);
    }
}
