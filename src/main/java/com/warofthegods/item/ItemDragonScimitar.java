package com.warofthegods.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.EnumHelper;

import com.warofthegods.WarOfTheGods;

/**
 * The dragon scimitar, found now and then in the chests of old ruins: a heart keener than a runite sword, and it
 * never wears out.
 */
public class ItemDragonScimitar extends ItemSword {

    /** 9 damage, to a runite sword's 7. */
    private static final ToolMaterial DRAGON = EnumHelper.addToolMaterial("DRAGON_SCIMITAR", 0, 0, 8, 5, 15);

    public ItemDragonScimitar() {
        super(DRAGON);
        setUnlocalizedName(WarOfTheGods.MODID + ".dragon_scimitar");
        setTextureName(WarOfTheGods.MODID + ":dragon_scimitar");
        setCreativeTab(CreativeTabs.tabCombat);
        setMaxDamage(0);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.rare;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(EnumChatFormatting.BLUE + "Unbreakable");
    }
}
