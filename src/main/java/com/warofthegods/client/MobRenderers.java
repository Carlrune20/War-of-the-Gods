package com.warofthegods.client;

import net.minecraft.client.renderer.entity.RenderSkeleton;
import net.minecraft.client.renderer.entity.RenderZombie;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;

import cpw.mods.fml.client.registry.RenderingRegistry;

/** Zombies and skeletons drawn as Minecraft draws them, but wearing their armor as {@link ArmorLooks} draws it. */
public final class MobRenderers {

    private MobRenderers() {}

    public static void register() {
        RenderingRegistry.registerEntityRenderingHandler(EntityZombie.class, new Zombie());
        RenderingRegistry.registerEntityRenderingHandler(EntitySkeleton.class, new Skeleton());
    }

    private static class Zombie extends RenderZombie {

        @Override
        protected int shouldRenderPass(EntityLiving entity, int pass, float partialTicks) {
            int drawn = ArmorLooks.get()
                .renderPass(this, entity, entity.func_130225_q(3 - pass), pass, modelBipedMain);
            return drawn != ArmorLooks.VANILLA ? drawn : super.shouldRenderPass(entity, pass, partialTicks);
        }
    }

    private static class Skeleton extends RenderSkeleton {

        @Override
        protected int shouldRenderPass(EntityLiving entity, int pass, float partialTicks) {
            int drawn = ArmorLooks.get()
                .renderPass(this, entity, entity.func_130225_q(3 - pass), pass, modelBipedMain);
            return drawn != ArmorLooks.VANILLA ? drawn : super.shouldRenderPass(entity, pass, partialTicks);
        }
    }
}
