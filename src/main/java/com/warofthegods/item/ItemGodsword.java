package com.warofthegods.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.warofthegods.WarOfTheGods;
import com.warofthegods.game.Faction;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * A godsword: the great blade found in old ruins with the hilt of a god fitted to it. It strikes harder than any
 * sword, and each god's hilt gives it more: Zamorak's sets whoever it hits alight, Saradomin's heals its wielder by
 * part of what it deals, and Bandos's simply hits harder still. It never wears out.
 */
public class ItemGodsword extends ItemSword {

    /** Two hearts harder than a runite sword: 9 damage. */
    private static final ToolMaterial GODSWORD = EnumHelper.addToolMaterial("GODSWORD", 0, 0, 8, 5, 15);
    /** Bandos's a half heart harder again: 10 damage. */
    private static final ToolMaterial BANDOS = EnumHelper.addToolMaterial("BANDOS_GODSWORD", 0, 0, 8, 6, 15);

    /** Seconds Zamorak's godsword sets whoever it hits alight for. */
    public static final int FIRE_SECONDS = 4;
    /** Part of what Saradomin's godsword deals that it heals its wielder by. */
    public static final float HEAL_PART = 0.05F;

    public final Faction god;

    public ItemGodsword(Faction god) {
        super(god == Faction.BANDOS ? BANDOS : GODSWORD);
        this.god = god;
        setUnlocalizedName(WarOfTheGods.MODID + "." + registryName(god));
        setTextureName(WarOfTheGods.MODID + ":" + registryName(god));
        setCreativeTab(CreativeTabs.tabCombat);
        // Unbreakable: an item with no durability is never damaged by use.
        setMaxDamage(0);
    }

    /** The name the godsword of a god is registered under. */
    public static String registryName(Faction god) {
        return god.id + "_godsword";
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.epic;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        switch (god) {
            case ZAMORAK:
                lines.add(EnumChatFormatting.RED + "Sets its foes alight");
                break;
            case SARADOMIN:
                lines.add(
                    EnumChatFormatting.AQUA + "Heals you by " + Math.round(HEAL_PART * 100) + "% of what it deals");
                break;
            default:
                lines.add(EnumChatFormatting.DARK_GREEN + "Hits harder");
                break;
        }
        lines.add(EnumChatFormatting.BLUE + "Unbreakable");
    }

    @Override
    public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
        if (god == Faction.ZAMORAK && !target.worldObj.isRemote) {
            target.setFire(FIRE_SECONDS);
        }
        return super.hitEntity(stack, target, attacker);
    }

    /** A blow with Saradomin's godsword heals whoever struck it by part of the damage it does. */
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        Entity attacker = event.source.getEntity();
        if (god != Faction.SARADOMIN || event.entity.worldObj.isRemote
            || !(attacker instanceof EntityLivingBase)
            || event.source.getSourceOfDamage() != attacker) {
            return;
        }
        ItemStack held = ((EntityLivingBase) attacker).getHeldItem();
        if (held != null && held.getItem() == this && event.ammount > 0) {
            ((EntityLivingBase) attacker).heal(event.ammount * HEAL_PART);
        }
    }
}
