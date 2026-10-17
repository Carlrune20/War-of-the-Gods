package com.warofthegods.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.warofthegods.WarOfTheGods;
import com.warofthegods.game.UnitKind;
import com.warofthegods.unit.EntityUnit;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Silverlight, the sword that slew the demon Delrith: a fine sword against anyone, and against demons a terrible one,
 * its blows striking a lesser demon, or any creature of the Nether, many times as hard. There is one in each game,
 * lying in a shrine somewhere on the map. It never wears out.
 */
public class ItemSilverlight extends ItemSword {

    /** Times as hard as its usual blow it strikes a demon. */
    public static final float DEMON_FACTOR = 6;

    /** As keen as a diamond sword, though no harder a blow against men. */
    private static final ToolMaterial SILVER = EnumHelper.addToolMaterial("SILVERLIGHT", 0, 2500, 8, 3, 22);

    public ItemSilverlight() {
        super(SILVER);
        setUnlocalizedName(WarOfTheGods.MODID + ".silverlight");
        setTextureName(WarOfTheGods.MODID + ":silverlight");
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
        lines.add(EnumChatFormatting.AQUA + "Slays demons");
        lines.add(EnumChatFormatting.BLUE + "Unbreakable");
    }

    /** Whether the entity is a demon: Zamorak's lesser demons, and the creatures of the Nether. */
    public static boolean isDemon(Entity entity) {
        if (entity instanceof EntityUnit) {
            return ((EntityUnit) entity).getKind() == UnitKind.LESSER_DEMON;
        }
        return entity instanceof EntityBlaze || entity instanceof EntityGhast
            || entity instanceof EntityPigZombie
            || entity instanceof EntityMagmaCube
            || entity instanceof EntityWither;
    }

    /** A blow with Silverlight at a demon lands many times as hard, in a flash of light. */
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        Entity attacker = event.source.getEntity();
        if (event.entity.worldObj.isRemote || !(attacker instanceof EntityLivingBase)
            || event.source.getSourceOfDamage() != attacker
            || !isDemon(event.entityLiving)) {
            return;
        }
        ItemStack held = ((EntityLivingBase) attacker).getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemSilverlight)) {
            return;
        }
        event.ammount *= DEMON_FACTOR;
        EntityLivingBase demon = event.entityLiving;
        ((WorldServer) demon.worldObj).func_147487_a(
            "magicCrit",
            demon.posX,
            demon.posY + demon.height * 0.6,
            demon.posZ,
            30,
            demon.width * 0.5,
            demon.height * 0.3,
            demon.width * 0.5,
            0.3);
        demon.worldObj.playSoundAtEntity(demon, "random.anvil_land", 0.4F, 1.8F);
    }
}
