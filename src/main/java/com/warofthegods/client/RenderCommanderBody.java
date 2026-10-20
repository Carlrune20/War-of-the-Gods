package com.warofthegods.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.warofthegods.game.Faction;
import com.warofthegods.rts.EntityCommanderBody;

/**
 * Draws the body a commanding player left behind like the player: the player model in their skin, their armor (as
 * {@link ArmorLooks} draws it) and what they hold, and their name above. The skin comes from the textures the server
 * passed on, loaded the way vanilla
 * loads other players' skins; until then, or offline, it is the default one.
 */
public class RenderCommanderBody extends RenderBiped {

    private static final Map<UUID, ResourceLocation> SKINS = new HashMap<>();
    private static final Set<UUID> ASKED = new HashSet<>();

    public RenderCommanderBody() {
        super(new ModelBiped(0), 0.5F);
    }

    @Override
    protected int shouldRenderPass(EntityLiving entity, int pass, float partialTicks) {
        ItemStack stack = entity.func_130225_q(3 - pass);
        int drawn = ArmorLooks.get()
            .renderPass(this, entity, stack, pass, Faction.of(stack), modelBipedMain);
        return drawn != ArmorLooks.VANILLA ? drawn : super.shouldRenderPass(entity, pass, partialTicks);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityLiving entity) {
        EntityCommanderBody body = (EntityCommanderBody) entity;
        UUID id = body.getUniqueID();
        ResourceLocation skin = SKINS.get(id);
        if (skin != null) {
            return skin;
        }
        // The owner may be drawn nearby already, skin and all.
        EntityPlayer owner = body.worldObj.func_152378_a(id);
        if (owner instanceof AbstractClientPlayer && ((AbstractClientPlayer) owner).func_152123_o()) {
            return ((AbstractClientPlayer) owner).getLocationSkin();
        }
        if (ASKED.add(id)) {
            GameProfile profile = new GameProfile(id, body.getOwnerName());
            if (!body.getTextures()
                .isEmpty()) {
                String signature = body.getTexturesSignature();
                profile.getProperties()
                    .put(
                        "textures",
                        new Property("textures", body.getTextures(), signature.isEmpty() ? null : signature));
            }
            Minecraft.getMinecraft()
                .func_152342_ad()
                .func_152790_a(profile, (type, location) -> {
                    if (type == MinecraftProfileTexture.Type.SKIN) {
                        SKINS.put(id, location);
                    }
                }, false);
        }
        return AbstractClientPlayer.locationStevePng;
    }
}
