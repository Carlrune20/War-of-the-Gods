package com.warofthegods.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import com.warofthegods.WarOfTheGods;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Graystone bricks: dark grey dressed stone like the walls of Varrock and the Kinshra's keep, which Zamorak's people
 * build in from Age III. Mossy and cracked kinds show on damaged buildings and ruins, as with stone bricks.
 */
public class BlockGraystone extends Block {

    /** The kinds by metadata. */
    public static final String[] KINDS = { "graystone_bricks", "graystone_bricks_mossy", "graystone_bricks_cracked" };

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public BlockGraystone() {
        super(Material.rock);
        setHardness(1.5F);
        setResistance(10.0F);
        setStepSound(soundTypePiston);
        setBlockName(WarOfTheGods.MODID + ".graystone_bricks");
        setBlockTextureName(WarOfTheGods.MODID + ":graystone_bricks");
        setCreativeTab(CreativeTabs.tabBlock);
    }

    @Override
    public int damageDropped(int meta) {
        return meta < KINDS.length ? meta : 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return icons[meta < KINDS.length ? meta : 0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        icons = new IIcon[KINDS.length];
        for (int i = 0; i < KINDS.length; i++) {
            icons[i] = register.registerIcon(WarOfTheGods.MODID + ":" + KINDS[i]);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < KINDS.length; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }
}
