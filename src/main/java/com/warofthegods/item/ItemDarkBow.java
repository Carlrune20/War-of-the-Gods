package com.warofthegods.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
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
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;

import com.warofthegods.WarOfTheGods;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The dark bow, found now and then in the chests of old ruins: it looses two arrows at once, using up two, and each
 * hits much harder than a bow's. Both land, however soon after one another. It never wears out.
 */
public class ItemDarkBow extends ItemBow {

    /** An arrow's damage before its speed counts, to a bow's 2. */
    public static final double ARROW_DAMAGE = 3.5;
    /** Marks the dark bow's arrows, so the second lands though the first has only just hit. */
    private static final String ARROW_TAG = WarOfTheGods.MODID + ".darkBow";

    public ItemDarkBow() {
        setUnlocalizedName(WarOfTheGods.MODID + ".dark_bow");
        setTextureName(WarOfTheGods.MODID + ":dark_bow");
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
        lines.add(EnumChatFormatting.DARK_PURPLE + "Looses two arrows at once");
        lines.add(EnumChatFormatting.BLUE + "Unbreakable");
    }

    /** One picture however far it is drawn, rather than a bow's three. */
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

    /** As a bow, but two arrows, each harder hitting, for two from the quiver: one if only one is left. */
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
        int arrows = infinite ? 2 : Math.min(2, countArrows(player));
        if (arrows == 0) {
            return;
        }
        float pull = charge / 20.0F;
        pull = (pull * pull + pull * 2.0F) / 3.0F;
        if (pull < 0.1) {
            return;
        }
        pull = Math.min(pull, 1.0F);
        int power = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack);
        int punch = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, stack);
        boolean flame = EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, stack) > 0;
        for (int i = 0; i < arrows; i++) {
            EntityArrow arrow = new EntityArrow(world, player, pull * 2.0F);
            arrow.setDamage(ARROW_DAMAGE + (power > 0 ? power * 0.5 + 0.5 : 0));
            arrow.setIsCritical(pull == 1.0F);
            if (punch > 0) {
                arrow.setKnockbackStrength(punch);
            }
            if (flame) {
                arrow.setFire(100);
            }
            if (infinite) {
                arrow.canBePickedUp = 2;
            } else {
                player.inventory.consumeInventoryItem(Items.arrow);
            }
            arrow.getEntityData()
                .setBoolean(ARROW_TAG, true);
            if (!world.isRemote) {
                world.spawnEntityInWorld(arrow);
            }
        }
        world.playSoundAtEntity(player, "random.bow", 1.0F, 0.7F / (itemRand.nextFloat() * 0.4F + 1.2F) + pull * 0.5F);
    }

    private static int countArrows(EntityPlayer player) {
        int count = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && stack.getItem() == Items.arrow) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    /** The dark bow's second arrow lands though the first has only just hit. */
    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        Entity arrow = event.source.getSourceOfDamage();
        if (arrow instanceof EntityArrow && arrow.getEntityData()
            .getBoolean(ARROW_TAG)) {
            event.entityLiving.hurtResistantTime = 0;
        }
    }
}
