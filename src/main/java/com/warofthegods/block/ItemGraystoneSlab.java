package com.warofthegods.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemSlab;

/** The item of a graystone slab, stacking into a double slab. Forge looks its constructor up by the block alone. */
public class ItemGraystoneSlab extends ItemSlab {

    public ItemGraystoneSlab(Block block) {
        super(block, ModBlocks.graystoneSlab, ModBlocks.graystoneDoubleSlab, block == ModBlocks.graystoneDoubleSlab);
    }
}
