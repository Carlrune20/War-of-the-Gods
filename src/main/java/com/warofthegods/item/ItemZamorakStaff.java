package com.warofthegods.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.warofthegods.WarOfTheGods;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Spell;
import com.warofthegods.game.Team;
import com.warofthegods.unit.EntitySpell;
import com.warofthegods.unit.Units;

/**
 * Zamorak's staff: a long silver staff banded in gold with a red hook at its head, as Zamorak mages carry. Using it
 * casts a fire strike where its holder looks, once a second, that flies past the holder's own team and its allies.
 */
public class ItemZamorakStaff extends Item {

    /** Damage of the fire strike it casts. */
    public static final float DAMAGE = 8;
    /** Ticks between casts. */
    private static final int COOLDOWN = 20;

    public ItemZamorakStaff() {
        setUnlocalizedName(WarOfTheGods.MODID + ".zamorak_staff");
        setTextureName(WarOfTheGods.MODID + ":zamorak_staff");
        setMaxStackSize(1);
        setFull3D();
        setCreativeTab(CreativeTabs.tabCombat);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        long now = world.getTotalWorldTime();
        if (stack.hasTagCompound() && now - stack.getTagCompound()
            .getLong("LastCast") < COOLDOWN
            && now >= stack.getTagCompound()
                .getLong("LastCast")) {
            return stack;
        }
        player.swingItem();
        if (!world.isRemote) {
            if (!stack.hasTagCompound()) {
                stack.setTagCompound(new NBTTagCompound());
            }
            stack.getTagCompound()
                .setLong("LastCast", now);
            Game game = GameManager.getGame();
            Team team = game == null ? null : Units.teamOf(game, player);
            EntitySpell spell = new EntitySpell(world, player, team == null ? "" : team.getId(), Spell.FIRE, DAMAGE);
            world.spawnEntityInWorld(spell);
        }
        return stack;
    }
}
