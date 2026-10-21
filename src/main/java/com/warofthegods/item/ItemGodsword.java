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

    /** 12 damage, to a runite sword's 7. */
    private static final ToolMaterial GODSWORD = EnumHelper.addToolMaterial("GODSWORD", 0, 0, 8, 8, 15);
    /** Bandos's a heart harder again: 14 damage. */
    private static final ToolMaterial BANDOS = EnumHelper.addToolMaterial("BANDOS_GODSWORD", 0, 0, 8, 10, 15);

    /** Seconds Zamorak's godsword sets whoever it hits alight for. */
    public static final int FIRE_SECONDS = 4;
    /** Part of what Saradomin's godsword deals that it heals its wielder by. */
    public static final float HEAL_PART = 0.05F;

    /** The god whose hilt it has, or null for a god no team follows yet, such as Armadyl. */
    public final Faction god;
    /** The god's id, as its name and textures go. */
    public final String godId;

    public ItemGodsword(Faction god) {
        this(god.id, god == Faction.BANDOS ? BANDOS : GODSWORD, god);
    }

    /** A godsword with the hilt of the god of this id, striking as material does. */
    protected ItemGodsword(String godId, ToolMaterial material, Faction god) {
        super(material);
        this.god = god;
        this.godId = godId;
        setUnlocalizedName(WarOfTheGods.MODID + "." + registryName(godId));
        setTextureName(WarOfTheGods.MODID + ":" + registryName(godId));
        setCreativeTab(CreativeTabs.tabCombat);
        // Unbreakable: an item with no durability is never damaged by use.
        setMaxDamage(0);
    }

    /** The name the godsword of a god is registered under. */
    public static String registryName(Faction god) {
        return registryName(god.id);
    }

    /** The name the godsword of the god of this id is registered under. */
    public static String registryName(String godId) {
        return godId + "_godsword";
    }

    /** The plain godsword's material: 12 damage. */
    protected static ToolMaterial plain() {
        return GODSWORD;
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
