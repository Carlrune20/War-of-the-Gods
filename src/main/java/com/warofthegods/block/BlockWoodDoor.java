package com.warofthegods.block;

import java.util.Random;

import net.minecraft.block.BlockDoor;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.world.World;

import com.warofthegods.WarOfTheGods;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A plain wooden door as in the towns of Gielinor: upright boards of dark oak held by two cross braces, with an iron
 * ring for a handle. Anyone can open it, so units and players walk into the buildings that have one.
 */
public class BlockWoodDoor extends BlockDoor {

    public BlockWoodDoor() {
        super(Material.wood);
        setHardness(3.0F);
        setStepSound(soundTypeWood);
        setBlockName(WarOfTheGods.MODID + ".wood_door");
        setBlockTextureName(WarOfTheGods.MODID + ":wood_door");
        disableStats();
    }

    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        return (meta & 8) != 0 ? null : ModBlocks.woodDoorItem;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Item getItem(World world, int x, int y, int z) {
        return ModBlocks.woodDoorItem;
    }
}
