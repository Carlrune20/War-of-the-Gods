package com.warofthegods.client;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.warofthegods.unit.EntityCarcass;

/**
 * Draws a carcass as the animal it was, with Minecraft's own model, tipped over on its side the way animals fall when
 * they die, and its name, such as Dead pig, over it from close by.
 */
public class RenderCarcass extends Render {

    /** How high each kind's body lies off the ground once on its side, about half its thickness. */
    private static final float[] LIFT = { 0.38F, 0.32F, 0.4F, 0.2F };

    /** A stand-in animal of each kind to draw, for the world they are in. */
    private final EntityLiving[] dummies = new EntityLiving[EntityCarcass.KINDS.length];
    private World dummyWorld;

    public RenderCarcass() {
        shadowSize = 0.4F;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        EntityCarcass carcass = (EntityCarcass) entity;
        int kind = carcass.getKind();
        EntityLiving dummy = dummy(kind, entity.worldObj);
        Render render = RenderManager.instance.getEntityRenderObject(dummy);
        if (render == null) {
            return;
        }
        dummy.setPosition(entity.posX, entity.posY, entity.posZ);
        // Facing nowhere of its own, still: the turn and the fall are done here.
        dummy.renderYawOffset = dummy.prevRenderYawOffset = 180;
        dummy.rotationYawHead = dummy.prevRotationYawHead = 180;
        dummy.rotationPitch = dummy.prevRotationPitch = 0;
        dummy.limbSwing = dummy.limbSwingAmount = dummy.prevLimbSwingAmount = 0;
        dummy.hurtTime = 0;
        dummy.deathTime = 0;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y + LIFT[kind], z);
        GL11.glRotatef(180 - entity.rotationYaw, 0, 1, 0);
        GL11.glRotatef(90, 0, 0, 1);
        render.doRender(dummy, 0, 0, 0, 0, partialTicks);
        GL11.glPopMatrix();
        func_147906_a(entity, entity.getCommandSenderName(), x, y, z, 8);
    }

    private EntityLiving dummy(int kind, World world) {
        if (world != dummyWorld) {
            dummyWorld = world;
            dummies[0] = new EntityCow(world);
            dummies[1] = new EntityPig(world);
            dummies[2] = new EntitySheep(world);
            dummies[3] = new EntityChicken(world);
        }
        return dummies[kind];
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }
}
