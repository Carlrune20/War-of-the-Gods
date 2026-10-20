package com.warofthegods.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.warofthegods.WarOfTheGods;
import com.warofthegods.unit.PlayerPrayers;

/**
 * The prayer book, found now and then in the chests of old ruins. Whoever carries it has prayer points, shown above
 * their hunger, and can put up a protection prayer with it as the heroes do: a click with it turns the prayer on or
 * off, and holding the button down goes through the three, melee, missiles and magic, putting up whichever it is on
 * when let go. See {@link PlayerPrayers}.
 */
public class ItemPrayerBook extends Item {

    /** How long the button is held, in ticks, before it goes through the prayers rather than turning one on or off. */
    public static final int HOLD_TICKS = 8;
    /** Ticks between one prayer and the next while the button is held. */
    public static final int SWITCH_TICKS = 12;
    private static final int USE_TICKS = 72000;

    public ItemPrayerBook() {
        setUnlocalizedName(WarOfTheGods.MODID + ".prayer_book");
        setTextureName(WarOfTheGods.MODID + ":prayer_book");
        setCreativeTab(CreativeTabs.tabMisc);
        setMaxStackSize(1);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.rare;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(EnumChatFormatting.GRAY + "Click: turn your protection prayer on or off");
        lines.add(EnumChatFormatting.GRAY + "Hold: switch between melee, missiles and magic");
        lines.add(EnumChatFormatting.GRAY + "Recharge at your temple or monastery");
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.none;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        player.setItemInUse(stack, getMaxItemUseDuration(stack));
        return stack;
    }

    @Override
    public void onUsingTick(ItemStack stack, EntityPlayer player, int count) {
        int held = USE_TICKS - count;
        if (player instanceof EntityPlayerMP && held >= HOLD_TICKS && (held - HOLD_TICKS) % SWITCH_TICKS == 0) {
            PlayerPrayers.switchPrayer((EntityPlayerMP) player);
        }
    }

    @Override
    public void onPlayerStoppedUsing(ItemStack stack, World world, EntityPlayer player, int timeLeft) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        if (USE_TICKS - timeLeft < HOLD_TICKS) {
            PlayerPrayers.toggle((EntityPlayerMP) player);
        } else {
            PlayerPrayers.putUp((EntityPlayerMP) player);
        }
    }

    /** Whether a player carries a prayer book anywhere in their inventory. */
    public static boolean carriedBy(EntityPlayer player) {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && stack.getItem() instanceof ItemPrayerBook) {
                return true;
            }
        }
        return false;
    }
}
