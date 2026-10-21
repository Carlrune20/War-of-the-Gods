package com.warofthegods.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.model.ModelBase;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

import com.warofthegods.client.model.JsonModel;
import com.warofthegods.unit.WildKind;

/**
 * Forinthry's wild creatures (see {@link WildKind}), each a model of its own from models/ghost.json, hill_giant.json,
 * hellhound.json, red_spider.json, gargoyle.json, dragon.json and bandit.json (made by tools/models/wild.py). This
 * only moves their parts: the two-legged walk with their arms swinging and a blow struck down with the right arm; the
 * hound and the dragons on four legs in diagonal pairs, their jaws snapping as they strike and their tails swaying;
 * the spider's eight legs scuttling; the ghost bobbing as it floats, its arms reaching; and the folded wings of the
 * gargoyle and the dragons stirring now and then.
 */
public class ModelWild extends ModelBase {

    private static final int TAIL_LINKS = 9;

    private final Map<String, JsonModel> models = new HashMap<>();
    private JsonModel model;
    private WildKind kind = WildKind.GHOST;

    public ModelWild() {
        for (WildKind each : WildKind.values()) {
            if (!models.containsKey(each.model)) {
                models.put(each.model, new JsonModel(this, each.model));
            }
        }
        setKind(WildKind.GHOST);
    }

    /** Draws it as the kind given from now on. */
    public void setKind(WildKind kind) {
        this.kind = kind;
        model = models.get(kind.model);
    }

    @Override
    public void render(Entity entity, float swing, float swingAmount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(swing, swingAmount, age, yaw, pitch, scale, entity);
        model.render(scale);
    }

    /** Draws its glowing parts, such as a ghost's eyes, again at full brightness. */
    public void renderGlow(float scale) {
        model.renderGlow(scale);
    }

    @Override
    public void setRotationAngles(float swing, float swingAmount, float age, float yaw, float pitch, float scale,
        Entity entity) {
        model.resetPose();
        float stride = swing * 0.6662F;
        float step = MathHelper.cos(stride) * swingAmount;
        float breathe = MathHelper.sin(age * 0.07F);
        float blow = onGround > 0 ? MathHelper.sin(onGround * (float) Math.PI) : 0;
        model.turn("head", pitch / 57.3F * (kind.isBeast() ? 0.5F : 1), yaw / 57.3F * (kind.isBeast() ? 0.6F : 1), 0);
        if (kind == WildKind.RED_SPIDER) {
            spider(stride, swingAmount, blow);
        } else if (kind.isBeast()) {
            beast(stride, step, swingAmount, breathe, blow, age);
        } else {
            biped(step, breathe, blow, age);
        }
    }

    private void biped(float step, float breathe, float blow, float age) {
        if (kind == WildKind.GHOST) {
            // It floats, bobbing, its arms reaching out and drifting, its robe trailing as it goes.
            float bob = MathHelper.sin(age * 0.1F) * 1.2F - 1.5F;
            for (String part : new String[] { "body", "head", "left_arm", "right_arm" }) {
                model.move(part, 0, bob, 0);
            }
            model.turn("left_arm", MathHelper.sin(age * 0.08F) * 0.1F - blow * 0.6F, 0, 0);
            model.turn("right_arm", MathHelper.cos(age * 0.08F) * 0.1F - blow * 0.6F, 0, 0);
            model.turn("body", Math.abs(step) * 0.15F, 0, 0);
            return;
        }
        float heavy = kind == WildKind.HILL_GIANT || kind == WildKind.GARGOYLE ? 0.8F : 1;
        model.turn("right_leg", step * 1.4F * heavy, 0, 0);
        model.turn("left_leg", -step * 1.4F * heavy, 0, 0);
        float rightArm = -step * 0.8F * heavy;
        float leftArm = step * 0.8F * heavy;
        if (blow > 0) {
            // A blow struck down from up over its shoulder.
            rightArm -= blow * (kind == WildKind.HILL_GIANT ? 2.2F : 1.6F);
            leftArm += blow * 0.3F;
        }
        model.turn("right_arm", rightArm, 0, breathe * 0.03F);
        model.turn("left_arm", leftArm, 0, -breathe * 0.03F);
        // A gargoyle's folded wings stir now and then.
        float stir = Math.max(0, MathHelper.sin(age * 0.03F)) * 0.15F;
        model.turn("left_wing", 0, stir, 0);
        model.turn("right_wing", 0, -stir, 0);
    }

    private void beast(float stride, float step, float swingAmount, float breathe, float blow, float age) {
        float legs = kind.isDragon() ? 0.7F : 1.1F;
        model.turn("front_left_leg", step * legs, 0, 0);
        model.turn("back_right_leg", step * legs, 0, 0);
        model.turn("front_right_leg", -step * legs, 0, 0);
        model.turn("back_left_leg", -step * legs, 0, 0);
        model.turn("body", MathHelper.cos(stride * 2) * 0.02F * swingAmount + breathe * 0.01F, 0, 0);
        // Snapping: the head thrust forward and down, the jaw dropped.
        model.turn("jaw", 0.05F + breathe * 0.04F + blow * 0.7F, 0, 0);
        model.turn("neck", blow * 0.4F + breathe * 0.02F, 0, 0);
        if (kind == WildKind.HELLHOUND) {
            model.turn("head", blow * 0.3F, 0, 0);
        }
        for (int i = 0; i < TAIL_LINKS; i++) {
            float wave = MathHelper.sin(age * 0.09F - i * 0.5F) * (0.06F + i * 0.01F);
            float walk = MathHelper.cos(stride - i * 0.4F) * 0.12F * swingAmount;
            model.turn("tail" + i, 0, wave + walk, 0);
        }
        // Folded wings, shifting a little as it breathes; they do not fly.
        float stir = breathe * 0.04F + Math.max(0, MathHelper.sin(age * 0.025F)) * 0.12F;
        model.turn("left_wing", 0, 0, -stir);
        model.turn("right_wing", 0, 0, stir);
    }

    private void spider(float stride, float swingAmount, float blow) {
        for (int i = 0; i < 8; i++) {
            float side = i % 2 == 0 ? -1 : 1;
            float phase = (i / 2 % 2 == 0) == (i % 2 == 0) ? 0 : (float) Math.PI;
            float sweep = MathHelper.cos(stride * 1.6F + phase) * 0.4F * swingAmount;
            float lift = Math.max(0, MathHelper.sin(stride * 1.6F + phase)) * 0.4F * swingAmount;
            model.turn("leg" + i, 0, sweep, side * -lift);
        }
        model.turn("left_fang", -blow * 0.6F, 0, 0);
        model.turn("right_fang", -blow * 0.6F, 0, 0);
        model.turn("abdomen", MathHelper.cos(stride * 1.6F) * 0.05F * swingAmount, 0, 0);
    }
}
