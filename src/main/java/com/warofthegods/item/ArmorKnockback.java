package com.warofthegods.item;

import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.warofthegods.WarOfTheGods;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Heavy armor keeps its wearer on their feet: each piece of runite takes a tenth of every knockback away, and each
 * piece of dragon armor a quarter, so a full dragon suit is never knocked back at all. Players and units alike.
 */
public class ArmorKnockback {

    /** The knockback each piece takes away. */
    public static final double RUNITE = 0.1, DRAGON = 0.25;

    private static final UUID MODIFIER = UUID.fromString("5b0f2d1e-7a43-4c3e-9e61-2f6d0c9a8b14");

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.entityLiving;
        if (entity.worldObj.isRemote) {
            return;
        }
        double resist = 0;
        for (int slot = 1; slot <= 4; slot++) {
            resist += of(entity.getEquipmentInSlot(slot));
        }
        IAttributeInstance attribute = entity.getEntityAttribute(SharedMonsterAttributes.knockbackResistance);
        if (attribute == null) {
            return;
        }
        AttributeModifier current = attribute.getModifier(MODIFIER);
        if (current != null && current.getAmount() == resist) {
            return;
        }
        if (current != null) {
            attribute.removeModifier(current);
        }
        if (resist > 0) {
            attribute.applyModifier(new AttributeModifier(MODIFIER, WarOfTheGods.MODID + ".armorKnockback", resist, 0));
        }
    }

    /** Runite's knockback resistance in its tooltip, as dragon armor has. */
    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        if (isRunite(event.itemStack.getItem())) {
            event.toolTip.add(EnumChatFormatting.BLUE + "+" + Math.round(RUNITE * 100) + "% knockback resistance");
        }
    }

    private static double of(ItemStack stack) {
        if (stack == null) {
            return 0;
        }
        Item item = stack.getItem();
        if (item instanceof ItemDragonArmor) {
            return DRAGON;
        }
        return isRunite(item) ? RUNITE : 0;
    }

    private static boolean isRunite(Item item) {
        return item == Items.diamond_helmet || item == Items.diamond_chestplate
            || item == Items.diamond_leggings
            || item == Items.diamond_boots;
    }
}
