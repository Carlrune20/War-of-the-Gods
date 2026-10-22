package com.warofthegods.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;

import com.warofthegods.WarOfTheGods;

/**
 * The plain ground and rock of Forinthry, the Wilderness burnt black in the God Wars: charred earth, ash, scorched
 * stone and ember rock, whose cracks still glow.
 */
public class BlockWilderness extends Block {

    public BlockWilderness(String name, Material material, float hardness, SoundType sound, float light) {
        super(material);
        setHardness(hardness);
        setResistance(hardness * 5);
        setStepSound(sound);
        setLightLevel(light);
        setBlockName(WarOfTheGods.MODID + "." + name);
        setBlockTextureName(WarOfTheGods.MODID + ":" + name);
        setCreativeTab(CreativeTabs.tabBlock);
        setHarvestLevel(material == Material.rock ? "pickaxe" : "shovel", 0);
    }
}
