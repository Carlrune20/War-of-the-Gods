package com.warofthegods.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;

import com.warofthegods.WarOfTheGods;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The stem and cap of the giant mushrooms that grow in Forinthry's ash, the Wilderness's food: workers harvest one
 * with a hoe and carry its food home (see {@code Trees}). The caps glow faintly in the endless night there. A stem
 * counts as a tree's log and a cap as its leaves, so a mushroom is found, harvested and falls the way a tree does.
 */
public class BlockWildMushroom extends Block {

    private final boolean cap;
    @SideOnly(Side.CLIENT)
    private IIcon inside;

    public BlockWildMushroom(boolean cap) {
        super(cap ? Material.leaves : Material.wood);
        this.cap = cap;
        setHardness(cap ? 0.4F : 0.8F);
        setStepSound(soundTypeWood);
        setLightLevel(cap ? 0.6F : 0);
        String name = cap ? "mushroom_cap" : "mushroom_stem";
        setBlockName(WarOfTheGods.MODID + "." + name);
        setBlockTextureName(WarOfTheGods.MODID + ":" + name);
        setCreativeTab(CreativeTabs.tabDecorations);
    }

    @Override
    public boolean isOpaqueCube() {
        return true;
    }

    /** Nothing comes of breaking one by hand; its food is had only by harvesting it. */
    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        // A stem's cut ends and the underside of a cap show its pale flesh.
        return side == 0 || !cap && side == 1 ? inside : blockIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        blockIcon = register.registerIcon(getTextureName());
        inside = register.registerIcon(WarOfTheGods.MODID + ":mushroom_inside");
    }
}
