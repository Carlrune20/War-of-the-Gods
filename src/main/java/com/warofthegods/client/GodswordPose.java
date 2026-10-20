package com.warofthegods.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;

import com.warofthegods.item.ItemGodsword;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * How a player stands and strikes with a godsword, which is too great for one hand: at rest both hands on the grip,
 * the blade held up before them and swaying a little as they breathe; striking, it is heaved up over the head and
 * brought down in one great two-handed chop. Players are drawn with a model that takes this pose, as is the armor over
 * them, so it moves with their arms.
 */
public final class GodswordPose {

    /**
     * Where both arms rest, raised forward, and how far each turns in toward the other: the right hand a little, the
     * left across the body until it closes on the grip beside the right.
     */
    private static final float GUARD = -0.95F, LEFT_ACROSS = 1.0F, RIGHT_ACROSS = -0.35F;
    /** How far over the head the sword is heaved, and how far down it comes. */
    private static final float RAISED = -2.9F, CHOPPED = -0.25F;

    private GodswordPose() {}

    /**
     * Draws players with models that take the pose. Forge calls its listeners from generated code, so the listener is
     * this public class, not an anonymous one it could not reach.
     */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(new GodswordPose());
    }

    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Pre event) {
        posePlayers(event.renderer);
    }

    /** Swaps a player renderer's models, once, for ones that take the pose. */
    private static void posePlayers(RenderPlayer renderer) {
        if (renderer.modelBipedMain instanceof Posed) {
            return;
        }
        renderer.modelBipedMain = new Posed(0.0F);
        renderer.modelArmorChestplate = new Posed(1.0F);
        renderer.modelArmor = new Posed(0.5F);
        ReflectionHelper.setPrivateValue(
            RendererLivingEntity.class,
            renderer,
            (ModelBase) renderer.modelBipedMain,
            "mainModel",
            "field_77045_g");
    }

    /** Whether the entity has a godsword in hand. */
    public static boolean holds(Entity entity) {
        if (!(entity instanceof EntityLivingBase)) {
            return false;
        }
        ItemStack held = ((EntityLivingBase) entity).getHeldItem();
        return held != null && held.getItem() instanceof ItemGodsword;
    }

    /**
     * Turns a model's arms to the pose, after its own setRotationAngles, if the entity holds a godsword: age is the
     * entity's age in ticks, and the model's onGround how far through a swing it is.
     */
    public static void apply(ModelBiped model, Entity entity, float age) {
        if (!holds(entity) || model.aimedBow || model.isRiding) {
            return;
        }
        float swing = model.onGround;
        float breathe = MathHelper.sin(age * 0.07F) * 0.04F;
        float pitch;
        float twist = 0;
        if (swing <= 0 || swing >= 1) {
            pitch = GUARD + breathe;
        } else if (swing < 0.4F) {
            // Heaved up over the head.
            float t = swing / 0.4F;
            pitch = GUARD + (RAISED - GUARD) * (1 - (1 - t) * (1 - t));
            twist = -0.25F * t;
        } else if (swing < 0.7F) {
            // Brought down hard.
            float t = (swing - 0.4F) / 0.3F;
            pitch = RAISED + (CHOPPED - RAISED) * t * t;
            twist = -0.25F + 0.45F * t;
        } else {
            // Back to the guard.
            float t = (swing - 0.7F) / 0.3F;
            pitch = CHOPPED + (GUARD - CHOPPED) * t;
            twist = 0.2F * (1 - t);
        }
        model.bipedBody.rotateAngleY = twist;
        model.bipedRightArm.rotationPointZ = MathHelper.sin(twist) * 5.0F;
        model.bipedRightArm.rotationPointX = -MathHelper.cos(twist) * 5.0F;
        model.bipedLeftArm.rotationPointZ = -MathHelper.sin(twist) * 5.0F;
        model.bipedLeftArm.rotationPointX = MathHelper.cos(twist) * 5.0F;
        model.bipedRightArm.rotateAngleX = pitch;
        model.bipedLeftArm.rotateAngleX = pitch;
        model.bipedRightArm.rotateAngleY = RIGHT_ACROSS + twist;
        model.bipedLeftArm.rotateAngleY = LEFT_ACROSS + twist;
        model.bipedRightArm.rotateAngleZ = 0;
        model.bipedLeftArm.rotateAngleZ = 0;
    }

    /** A player model that takes the pose. */
    private static final class Posed extends ModelBiped {

        Posed(float inflate) {
            super(inflate);
        }

        @Override
        public void setRotationAngles(float swing, float swingAmount, float age, float yaw, float pitch, float scale,
            Entity entity) {
            super.setRotationAngles(swing, swingAmount, age, yaw, pitch, scale, entity);
            apply(this, entity, age);
        }
    }
}
