package com.warofthegods.block;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDoor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.warofthegods.WarOfTheGods;

/** Places a {@link BlockWoodDoor}, as the oak door item places an oak door. */
public class ItemWoodDoor extends Item {

    public ItemWoodDoor() {
        maxStackSize = 1;
        setUnlocalizedName(WarOfTheGods.MODID + ".wood_door");
        setTextureName(WarOfTheGods.MODID + ":wood_door");
        setCreativeTab(CreativeTabs.tabRedstone);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (side != 1) {
            return false;
        }
        y++;
        if (!player.canPlayerEdit(x, y, z, side, stack) || !player.canPlayerEdit(x, y + 1, z, side, stack)
            || !ModBlocks.woodDoor.canPlaceBlockAt(world, x, y, z)) {
            return false;
        }
        int facing = MathHelper.floor_double((player.rotationYaw + 180.0F) * 4.0F / 360.0F - 0.5D) & 3;
        ItemDoor.placeDoorBlock(world, x, y, z, facing, ModBlocks.woodDoor);
        stack.stackSize--;
        return true;
    }
}
