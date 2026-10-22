package com.warofthegods.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;

import com.warofthegods.WarOfTheGods;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Armadyl crossbow, found now and then in the chests of old ruins: it shoots arrows as a bow does, but is wound to
 * full power in half a bow's time, and its arrows hit twice as hard. It never wears out.
 */
public class ItemArmadylCrossbow extends ItemBow {

    /** An arrow's damage before its speed counts, to a bow's 2. */
    public static final double ARROW_DAMAGE = 4.0;
    /** Ticks it takes to wind to full power, to a bow's 20. */
    public static final float FULL_DRAW = 10.0F;

    public ItemArmadylCrossbow() {
        setUnlocalizedName(WarOfTheGods.MODID + ".armadyl_crossbow");
        setTextureName(WarOfTheGods.MODID + ":armadyl_crossbow");
        setCreativeTab(CreativeTabs.tabCombat);
        // Unbreakable: an item with no durability is never damaged by use.
        setMaxDamage(0);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.epic;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(EnumChatFormatting.AQUA + "Winds fast, hits hard");
        lines.add(EnumChatFormatting.BLUE + "Unbreakable");
    }

    /** Held as a sword is, drawn as a model (see com.warofthegods.client.RenderModelItem). */
    @Override
    public boolean isFull3D() {
        return true;
    }

    /** One picture however far it is wound, rather than a bow's three. */
    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        itemIcon = register.registerIcon(getIconString());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getItemIconForUseDuration(int stage) {
        return itemIcon;
    }

    /** As a bow, wound twice as fast, its arrow hitting twice as hard. */
    @Override
    public void onPlayerStoppedUsing(ItemStack stack, World world, EntityPlayer player, int timeLeft) {
        int charge = getMaxItemUseDuration(stack) - timeLeft;
        ArrowLooseEvent loose = new ArrowLooseEvent(player, stack, charge);
        if (MinecraftForge.EVENT_BUS.post(loose)) {
            return;
        }
        charge = loose.charge;
        boolean infinite = player.capabilities.isCreativeMode
            || EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId, stack) > 0;
        if (!infinite && !player.inventory.hasItem(Items.arrow)) {
            return;
        }
        float pull = charge / FULL_DRAW;
        pull = (pull * pull + pull * 2.0F) / 3.0F;
        if (pull < 0.1) {
            return;
        }
        pull = Math.min(pull, 1.0F);
        EntityArrow arrow = new EntityArrow(world, player, pull * 2.0F);
        int power = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack);
        arrow.setDamage(ARROW_DAMAGE + (power > 0 ? power * 0.5 + 0.5 : 0));
        arrow.setIsCritical(pull == 1.0F);
        int punch = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, stack);
        if (punch > 0) {
            arrow.setKnockbackStrength(punch);
        }
        if (EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, stack) > 0) {
            arrow.setFire(100);
        }
        if (infinite) {
            arrow.canBePickedUp = 2;
        } else {
            player.inventory.consumeInventoryItem(Items.arrow);
        }
        world.playSoundAtEntity(player, "random.bow", 1.0F, 1.2F / (itemRand.nextFloat() * 0.4F + 1.2F) + pull * 0.5F);
        if (!world.isRemote) {
            world.spawnEntityInWorld(arrow);
        }
    }
}
