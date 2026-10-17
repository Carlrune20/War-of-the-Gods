package com.warofthegods.block;

import net.minecraft.block.BlockStairs;
import net.minecraft.creativetab.CreativeTabs;

import com.warofthegods.WarOfTheGods;

/** Steps of graystone bricks, for roofs, stairs and the edges of walls. */
public class BlockGraystoneStairs extends BlockStairs {

    public BlockGraystoneStairs() {
        super(ModBlocks.graystoneBricks, 0);
        setBlockName(WarOfTheGods.MODID + ".graystone_stairs");
        setCreativeTab(CreativeTabs.tabBlock);
        useNeighborBrightness = true;
    }
}
