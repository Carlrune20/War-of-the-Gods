package com.warofthegods.block;

import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.IIcon;

import com.warofthegods.WarOfTheGods;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The burnt trunk of a dead tree in Forinthry. A trunk of these is a tree whether or not it has leaves, so workers
 * chop the Wilderness's dead trees for wood; ruins never use it.
 */
public class BlockCharredLog extends BlockRotatedPillar {

    @SideOnly(Side.CLIENT)
    private IIcon top;

    public BlockCharredLog() {
        super(Material.wood);
        setHardness(2.0F);
        setStepSound(soundTypeWood);
        setBlockName(WarOfTheGods.MODID + ".charred_log");
        setBlockTextureName(WarOfTheGods.MODID + ":charred_log");
        setCreativeTab(CreativeTabs.tabBlock);
        setHarvestLevel("axe", 0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected IIcon getSideIcon(int meta) {
        return blockIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected IIcon getTopIcon(int meta) {
        return top;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        blockIcon = register.registerIcon(WarOfTheGods.MODID + ":charred_log");
        top = register.registerIcon(WarOfTheGods.MODID + ":charred_log_top");
    }
}
