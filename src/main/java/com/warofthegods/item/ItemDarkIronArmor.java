package com.warofthegods.item;

import net.minecraft.item.ItemArmor;

import com.warofthegods.WarOfTheGods;

/**
 * Zamorak's dark iron armor: as strong as iron, mended with iron ingots, and drawn as RuneScape's black armor (see
 * com.warofthegods.client.ArmorLooks). Zamorak's armories sell it in place of iron, and their units wear it.
 */
public class ItemDarkIronArmor extends ItemArmor {

    /** Minecraft's iron armor's textures, for anything that draws it without the mod's own look. */
    private static final int IRON_RENDER_INDEX = 2;
    private static final String[] PIECES = { "helmet", "chestplate", "leggings", "boots" };

    /** The piece for the slot: 0 for the helmet to 3 for the boots. */
    public ItemDarkIronArmor(int slot) {
        super(ArmorMaterial.IRON, IRON_RENDER_INDEX, slot);
        setUnlocalizedName(WarOfTheGods.MODID + ".dark_iron_" + PIECES[slot]);
        setTextureName(WarOfTheGods.MODID + ":dark_iron_" + PIECES[slot]);
    }

    /** The name it is registered under. */
    public String registryName() {
        return "dark_iron_" + PIECES[armorType];
    }
}
