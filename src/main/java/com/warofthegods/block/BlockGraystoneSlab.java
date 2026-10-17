package com.warofthegods.block;

import java.util.Random;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import com.warofthegods.WarOfTheGods;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** A half block of graystone bricks, the bottom or top half by metadata 0 or 8; doubled, a full block. */
public class BlockGraystoneSlab extends BlockSlab {

    public BlockGraystoneSlab(boolean doubled) {
        super(doubled, Material.rock);
        setHardness(2.0F);
        setResistance(10.0F);
        setStepSound(soundTypePiston);
        setBlockName(WarOfTheGods.MODID + (doubled ? ".graystone_double_slab" : ".graystone_slab"));
        if (!doubled) {
            setCreativeTab(CreativeTabs.tabBlock);
            useNeighborBrightness = true;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return ModBlocks.graystoneBricks.getIcon(side, 0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {}

    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        return Item.getItemFromBlock(ModBlocks.graystoneSlab);
    }

    @Override
    public int quantityDropped(Random random) {
        return field_150004_a ? 2 : 1;
    }

    @Override
    protected ItemStack createStackedBlock(int meta) {
        return new ItemStack(ModBlocks.graystoneSlab, 2, 0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Item getItem(World world, int x, int y, int z) {
        return Item.getItemFromBlock(ModBlocks.graystoneSlab);
    }

    @Override
    public String func_150002_b(int meta) {
        return getUnlocalizedName();
    }
}
