package com.warofthegods.client;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.warofthegods.WarOfTheGods;
import com.warofthegods.unit.EntityWildMob;
import com.warofthegods.unit.WildKind;

/**
 * Draws Forinthry's wild creatures with {@link ModelWild}, each kind in its own texture and at its own size: a hill
 * giant nearly twice a man's height, the dragons bigger than any horse. A ghost is drawn see-through, its eyes glowing.
 */
public class RenderWildMob extends RenderLiving {

    private static final Map<WildKind, ResourceLocation> TEXTURES = new EnumMap<>(WildKind.class);

    static {
        for (WildKind kind : WildKind.values()) {
            TEXTURES.put(kind, new ResourceLocation(WarOfTheGods.MODID, "textures/models/" + kind.texture + ".png"));
        }
    }

    private final ModelWild wild;

    public RenderWildMob() {
        super(new ModelWild(), 0.5F);
        wild = (ModelWild) mainModel;
    }

    @Override
    public void doRender(EntityLiving entity, double x, double y, double z, float yaw, float partialTicks) {
        WildKind kind = ((EntityWildMob) entity).getKind();
        wild.setKind(kind);
        shadowSize = kind.width * 0.6F;
        super.doRender(entity, x, y, z, yaw, partialTicks);
    }

    @Override
    protected void preRenderCallback(EntityLivingBase entity, float partialTicks) {
        float scale = ((EntityWildMob) entity).getKind().scale;
        GL11.glScalef(scale, scale, scale);
    }

    @Override
    protected void renderModel(EntityLivingBase entity, float swing, float swingAmount, float age, float yaw,
        float pitch, float scale) {
        if (((EntityWildMob) entity).getKind() != WildKind.GHOST) {
            super.renderModel(entity, swing, swingAmount, age, yaw, pitch, scale);
            wild.renderGlow(scale);
            return;
        }
        bindEntityTexture(entity);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glColor4f(1, 1, 1, 0.5F);
        mainModel.render(entity, swing, swingAmount, age, yaw, pitch, scale);
        GL11.glColor4f(1, 1, 1, 1);
        wild.renderGlow(scale);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return TEXTURES.get(((EntityWildMob) entity).getKind());
    }
}
