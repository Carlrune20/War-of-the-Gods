package com.warofthegods.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.ISpecialArmor;
import net.minecraftforge.common.util.EnumHelper;

import com.warofthegods.WarOfTheGods;

/**
 * Dragon armor, found now and then in the chests of old ruins: a full suit guards better than runite (Minecraft's
 * diamond), 23 points of armor to its 20, and it never wears out. A full suit also stands firm against every
 * knockback (see ArmorKnockback). Drawn red, as RuneScape's dragon armor is (see com.warofthegods.client.ArmorLooks).
 */
public class ItemDragonArmor extends ItemArmor implements ISpecialArmor {

    /** Helmet, platebody, platelegs, boots: a point more than runite for all but the boots. */
    private static final ArmorMaterial DRAGON = EnumHelper.addArmorMaterial("DRAGON", 40, new int[] { 4, 9, 7, 3 }, 12);
    /** Minecraft's diamond armor's textures, for anything that draws it without the mod's own look. */
    private static final int DIAMOND_RENDER_INDEX = 3;
    private static final String[] PIECES = { "helmet", "chestplate", "leggings", "boots" };

    /** The piece for the slot: 0 for the helmet to 3 for the boots. */
    public ItemDragonArmor(int slot) {
        super(DRAGON, DIAMOND_RENDER_INDEX, slot);
        setUnlocalizedName(WarOfTheGods.MODID + ".dragon_" + PIECES[slot]);
        setTextureName(WarOfTheGods.MODID + ":dragon_" + PIECES[slot]);
        setCreativeTab(CreativeTabs.tabCombat);
        // Unbreakable: an item with no durability is never damaged by use.
        setMaxDamage(0);
    }

    /** The name it is registered under. */
    public String registryName() {
        return "dragon_" + PIECES[armorType];
    }

    /**
     * Its full points against every hit armor guards against. Forge would otherwise let plain armor take no more of a
     * hit than it has durability left, which for armor that never wears out is a single point.
     */
    @Override
    public ArmorProperties getProperties(EntityLivingBase player, ItemStack armor, DamageSource source, double damage,
        int slot) {
        if (source.isUnblockable()) {
            return new ArmorProperties(0, 0, 0);
        }
        return new ArmorProperties(0, damageReduceAmount / 25D, Integer.MAX_VALUE);
    }

    @Override
    public int getArmorDisplay(EntityPlayer player, ItemStack armor, int slot) {
        return damageReduceAmount;
    }

    /** Never wears out. */
    @Override
    public void damageArmor(EntityLivingBase entity, ItemStack stack, DamageSource source, int damage, int slot) {}

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.rare;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(EnumChatFormatting.BLUE + "+" + damageReduceAmount + " armor");
        lines.add(EnumChatFormatting.BLUE + "+" + Math.round(ArmorKnockback.DRAGON * 100) + "% knockback resistance");
        lines.add(EnumChatFormatting.BLUE + "Unbreakable");
    }
}
