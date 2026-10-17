package com.warofthegods.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFire;
import net.minecraft.item.Item;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.warofthegods.WarOfTheGods;

/**
 * Fire for show, as in braziers, hearths and burning buildings: it looks and sounds like fire but never spreads,
 * burns nothing away, goes out by itself or sets anyone alight.
 */
public class BlockStaticFire extends BlockFire {

    public BlockStaticFire() {
        setTickRandomly(false);
        setLightLevel(1.0F);
        setBlockName(WarOfTheGods.MODID + ".static_fire");
        setBlockTextureName("fire");
        disableStats();
    }

    @Override
    public void updateTick(World world, int x, int y, int z, Random random) {}

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {}

    @Override
    public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        return !world.isAirBlock(x, y - 1, z);
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbour) {
        if (world.isAirBlock(x, y - 1, z)) {
            world.setBlockToAir(x, y, z);
        }
    }

    @Override
    public boolean isBurning(IBlockAccess world, int x, int y, int z) {
        return false;
    }

    @Override
    public int tickRate(World world) {
        return 1000000;
    }

    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        return null;
    }
}
