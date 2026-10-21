package com.warofthegods.item;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Armadyl's godsword, made with the Armadyl hilt, which no armory sells: it is only found in old ruins' chests, as no
 * team follows Armadyl yet. It hits for 12 as the others do, and one blow in eight lands half again as hard.
 */
public class ItemArmadylGodsword extends ItemGodsword {

    /** One blow in this many lands harder, by this much. */
    public static final int STRONG_ONE_IN = 8;
    public static final float STRONG_HIT = 1.5F;

    public ItemArmadylGodsword() {
        super("armadyl", plain(), null);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(EnumChatFormatting.AQUA + "One blow in " + STRONG_ONE_IN + " lands half again as hard");
        lines.add(EnumChatFormatting.BLUE + "Unbreakable");
    }

    /** Now and then a blow with it lands harder, with a flash of sparks on whoever it hits. */
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        Entity attacker = event.source.getEntity();
        if (event.entity.worldObj.isRemote || !(attacker instanceof EntityLivingBase)
            || event.source.getSourceOfDamage() != attacker) {
            return;
        }
        ItemStack held = ((EntityLivingBase) attacker).getHeldItem();
        if (held == null || held.getItem() != this || event.ammount <= 0
            || attacker.worldObj.rand.nextInt(STRONG_ONE_IN) != 0) {
            return;
        }
        event.ammount *= STRONG_HIT;
        if (attacker instanceof EntityPlayer) {
            ((EntityPlayer) attacker).onEnchantmentCritical(event.entity);
        }
    }
}
