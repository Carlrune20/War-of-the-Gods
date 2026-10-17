package com.warofthegods.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemMultiTexture;

/** The item of graystone bricks, named after its kind. Forge looks its constructor up by the block alone. */
public class ItemGraystone extends ItemMultiTexture {

    public ItemGraystone(Block block) {
        super(block, block, BlockGraystone.KINDS);
    }
}
