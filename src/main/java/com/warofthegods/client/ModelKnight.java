package com.warofthegods.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * A knight in full plate: the player model with a visored helm, broad pauldrons, gauntlet cuffs, knee guards and
 * sabatons. Black and White Knights wear a plume on the helm, drawn on its own by {@link #renderPlume} so it can be
 * tinted in the team's color. An Elite Black Knight adds a cape that swings as he walks; a Justiciar trades the plume
 * for a crown of spikes and wears a plated skirt. The extra parts are drawn from the lower half of a 64x64 skin, the
 * upper half being the player model's own.
 */
public class ModelKnight extends ModelBiped {

    /** The extra parts' corners in the skin, as tools/textures/generate.py paints them. */
    private static final int[] VISOR = { 0, 32 };
    private static final int[] PLUME = { 16, 32 };
    private static final int[] SPIKE = { 28, 32 };
    private static final int[] PAULDRON = { 0, 37 };
    private static final int[] PAULDRON_CAP = { 20, 56 };
    private static final int[] CUFF = { 0, 56 };
    private static final int[] KNEE = { 24, 43 };
    private static final int[] BOOT = { 0, 47 };
    private static final int[] SKIRT = { 22, 48 };
    private static final int[] CAPE = { 42, 37 };

    /** Follows the head, but is in no part's children, so the plume is drawn only when asked. */
    private final ModelRenderer plume;
    private final ModelRenderer crown;
    private final ModelRenderer cape;
    private final ModelRenderer skirtFront;
    private final ModelRenderer skirtBack;

    private boolean plumed = true;

    public ModelKnight() {
        super(0, 0, 64, 64);
        // The helm's visor juts out in front of the face.
        ModelRenderer visor = part(VISOR);
        visor.addBox(-3.5F, -6.5F, -5.5F, 7, 4, 1);
        bipedHead.addChild(visor);

        // The plume rises from the crown of the helm and sweeps back, in four segments.
        plume = new ModelRenderer(this, 0, 0);
        ModelRenderer segment = part(PLUME);
        segment.addBox(-1, -3, -1.5F, 2, 3, 3);
        segment.setRotationPoint(0, -8.3F, -1);
        segment.rotateAngleX = 0.2F;
        plume.addChild(segment);
        float[] bends = { -0.3F, -0.5F, -0.6F };
        for (float bend : bends) {
            ModelRenderer next = part(PLUME);
            next.addBox(-1, -3, -1.5F, 2, 3, 3, -0.05F);
            next.setRotationPoint(0, -2.2F, 0);
            next.rotateAngleX = bend;
            segment.addChild(next);
            segment = next;
        }

        // A Justiciar's crown: spikes round the brow of the helm, tallest at the front.
        crown = new ModelRenderer(this, 0, 0);
        // Each as x and z on the brow, how far it rises and how far it leans out to the side.
        float[][] spikes = { { 0, -4.6F, 4.5F, 0 }, { -3, -4.6F, 3.5F, -0.25F }, { 3, -4.6F, 3.5F, 0.25F },
            { -4.6F, -1.5F, 3, -0.5F }, { 4.6F, -1.5F, 3, 0.5F }, { -4.6F, 2.5F, 3, -0.5F }, { 4.6F, 2.5F, 3, 0.5F },
            { 0, 4.6F, 3, 0 } };
        for (float[] spike : spikes) {
            ModelRenderer point = part(SPIKE);
            point.addBox(-0.5F, -spike[2], -0.5F, 1, 4, 1);
            point.setRotationPoint(spike[0], -7.5F, spike[1]);
            point.rotateAngleZ = spike[3];
            point.rotateAngleX = spike[1] < -3 ? 0.2F : spike[1] > 3 ? -0.2F : 0;
            crown.addChild(point);
        }
        bipedHead.addChild(crown);

        for (int side = -1; side <= 1; side += 2) {
            ModelRenderer arm = side < 0 ? bipedRightArm : bipedLeftArm;
            // Arms' boxes run from x -3 to 1 on the right and -1 to 3 on the left; the plate sits on the outside.
            float out = side < 0 ? -1 : 1;
            ModelRenderer pauldron = part(PAULDRON);
            pauldron.mirror = side > 0;
            pauldron.addBox(out - 3, -3, -3, 6, 4, 6);
            arm.addChild(pauldron);
            ModelRenderer cap = part(PAULDRON_CAP);
            cap.mirror = side > 0;
            cap.addBox(out - 2.5F, -4, -2.5F, 5, 1, 5);
            arm.addChild(cap);
            ModelRenderer cuff = part(CUFF);
            cuff.mirror = side > 0;
            cuff.addBox(out - 2.5F, 6, -2.5F, 5, 3, 5);
            arm.addChild(cuff);

            ModelRenderer leg = side < 0 ? bipedRightLeg : bipedLeftLeg;
            ModelRenderer knee = part(KNEE);
            knee.mirror = side > 0;
            knee.addBox(-2.5F, 4, -3, 5, 3, 2);
            leg.addChild(knee);
            ModelRenderer boot = part(BOOT);
            boot.mirror = side > 0;
            boot.addBox(-2.5F, 9, -3.5F, 5, 3, 6);
            leg.addChild(boot);
        }

        cape = part(CAPE);
        cape.addBox(-5, 0, 0, 10, 20, 1);
        cape.setRotationPoint(0, 0.5F, 2.2F);
        bipedBody.addChild(cape);

        skirtFront = part(SKIRT);
        skirtFront.addBox(-4, 0, -1, 8, 7, 1, 0.2F);
        skirtFront.setRotationPoint(0, 10, -1.6F);
        bipedBody.addChild(skirtFront);
        skirtBack = part(SKIRT);
        skirtBack.addBox(-4, 0, 0, 8, 7, 1, 0.2F);
        skirtBack.setRotationPoint(0, 10, 1.6F);
        bipedBody.addChild(skirtBack);
        setStyle(false, false);
    }

    private ModelRenderer part(int[] corner) {
        return new ModelRenderer(this, corner[0], corner[1]);
    }

    /**
     * Sets which knight it draws next: an Elite Black Knight has a cape, and a Justiciar a crown and skirt in place of
     * the plume. A plain knight has only the plume.
     */
    public void setStyle(boolean eliteBlack, boolean justiciar) {
        plumed = !justiciar;
        crown.showModel = justiciar;
        skirtFront.showModel = justiciar;
        skirtBack.showModel = justiciar;
        cape.showModel = eliteBlack;
    }

    /** Whether the knight it draws next wears a plume. */
    public boolean isPlumed() {
        return plumed;
    }

    @Override
    public void setRotationAngles(float swing, float swingAmount, float age, float yaw, float pitch, float scale,
        Entity entity) {
        super.setRotationAngles(swing, swingAmount, age, yaw, pitch, scale, entity);
        plume.rotationPointX = bipedHead.rotationPointX;
        plume.rotationPointY = bipedHead.rotationPointY;
        plume.rotationPointZ = bipedHead.rotationPointZ;
        plume.rotateAngleX = bipedHead.rotateAngleX;
        plume.rotateAngleY = bipedHead.rotateAngleY;
        plume.rotateAngleZ = bipedHead.rotateAngleZ;
        // The cape hangs a little away from the back and flies out behind as he walks; the skirt follows the legs.
        cape.rotateAngleX = 0.08F + swingAmount * 0.5F + MathHelper.cos(age * 0.08F) * 0.03F;
        float stride = Math.abs(MathHelper.cos(swing * 0.6662F) * 1.4F * swingAmount) * 0.5F;
        skirtFront.rotateAngleX = -0.05F - stride;
        skirtBack.rotateAngleX = 0.05F + stride;
    }

    /**
     * Draws the plume alone, for the renderer to tint in the team's color; it takes the head's place and turn from the
     * last {@link #render}.
     */
    public void renderPlume(float scale) {
        if (plumed) {
            plume.render(scale);
        }
    }
}
