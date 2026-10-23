package com.warofthegods.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

import com.warofthegods.client.model.JsonModel;

/**
 * Bandos's creatures: the goblin, the hobgoblin, the ork, the ogre, Graardor's sergeants and General Graardor, each a
 * model of its own from models/goblin.json, hobgoblin.json, ork.json, ogre.json, strongstack.json and graardor.json
 * (made by tools/models/generate.py, see tools/models/creatures.py). Their head, body, arms and legs move as the player
 * model's do, from where they are on each creature, and the player
 * model's arms are moved to the creature's shoulders so what a goblin holds is in its hand. Goblins' ears twitch, and
 * the big ones sway a little from side to side as they walk. Armadyl's aviansie and his heroes share
 * models/aviansie.json
 * (see tools/models/armadyl.py), and beat their wings as they go.
 */
public class ModelCreature extends ModelBiped {

    /** Where the player model's arms rest, from which its parts' turns and moves are copied. */
    private static final float ARM_X = 5, ARM_Y = 2;

    private final Map<String, JsonModel> models = new HashMap<>();
    /**
     * By model, where the player model's right arm goes for what it holds to be in the creature's hand: x, y and z; a
     * goblin's long arms put it lower than its shoulder.
     */
    private final Map<String, float[]> shoulders = new HashMap<>();
    private JsonModel model;
    private float[] shoulder;

    public ModelCreature() {
        super(0, 0, 64, 64);
        add("goblin", 4, 11, -1);
        add("hobgoblin", 6.5F, 3, 0);
        add("ork", 7.5F, 4, 0);
        add("ogre", 8.5F, 3, 0);
        add("strongstack", 7.5F, 3, 0);
        add("graardor", 9, 3, 0);
        add("aviansie", 6, 3, 0);
        setModel("goblin");
    }

    private void add(String name, float x, float y, float z) {
        models.put(name, new JsonModel(this, name));
        shoulders.put(name, new float[] { x, y, z });
    }

    /** Draws it as the named creature from now on: goblin, hobgoblin, ork, ogre, strongstack, graardor or aviansie. */
    public void setModel(String name) {
        model = models.containsKey(name) ? models.get(name) : models.get("goblin");
        shoulder = shoulders.containsKey(name) ? shoulders.get(name) : shoulders.get("goblin");
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
        // Back where the player model has them, as the last frame left them at the creature's shoulders.
        bipedRightArm.rotationPointY = ARM_Y;
        bipedLeftArm.rotationPointY = ARM_Y;
        super.setRotationAngles(swing, swingAmount, age, yaw, pitch, scale, entity);
        model.resetPose();
        model.follow("head", bipedHead, 0, 0, 0);
        model.follow("body", bipedBody, 0, 0, 0);
        model.follow("right_arm", bipedRightArm, -ARM_X, ARM_Y, 0);
        model.follow("left_arm", bipedLeftArm, ARM_X, ARM_Y, 0);
        model.follow("right_leg", bipedRightLeg, -1.9F, 12, 0);
        model.follow("left_leg", bipedLeftLeg, 1.9F, 12, 0);
        // The big ones lumber, rolling a little from side to side as they walk.
        float roll = MathHelper.cos(swing * 0.6662F) * 0.06F * swingAmount;
        model.turn("body", bipedBody.rotateAngleX, bipedBody.rotateAngleY, roll);
        float twitch = MathHelper.sin(age * 0.11F) > 0.93F ? 0.2F : 0;
        model.turn("left_ear", 0, 0, -twitch);
        model.turn("right_ear", 0, 0, twitch);
        // Aviansie's wings beat slowly as they hover, and harder as they fly.
        float flap = MathHelper.sin(age * (0.25F + 0.35F * swingAmount)) * (0.12F + 0.3F * swingAmount);
        model.turn("left_wing", 0, 0, flap);
        model.turn("right_wing", 0, 0, -flap);
        // The player model's arms at the creature's shoulders, so a goblin's axe is in its hand.
        bipedRightArm.rotationPointX += -shoulder[0] + ARM_X;
        bipedRightArm.rotationPointY = shoulder[1];
        bipedRightArm.rotationPointZ += shoulder[2];
        bipedLeftArm.rotationPointX += shoulder[0] - ARM_X;
        bipedLeftArm.rotationPointY = shoulder[1];
        bipedLeftArm.rotationPointZ += shoulder[2];
    }
}
