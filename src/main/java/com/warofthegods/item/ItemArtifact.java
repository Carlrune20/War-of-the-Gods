package com.warofthegods.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.warofthegods.WarOfTheGods;

/**
 * A rare piece that is nothing on its own: the godsword blade, lying in old ruins' chests, and the hilts of the three
 * gods, bought at an armory, that are put together with it to make a godsword.
 */
public class ItemArtifact extends Item {

    private final String hint;

    public ItemArtifact(String name, String hint) {
        setUnlocalizedName(WarOfTheGods.MODID + "." + name);
        setTextureName(WarOfTheGods.MODID + ":" + name);
        setCreativeTab(CreativeTabs.tabMaterials);
        setMaxStackSize(1);
        this.hint = hint;
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.rare;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(EnumChatFormatting.GRAY + hint);
    }
}
