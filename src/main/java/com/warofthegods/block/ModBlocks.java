package com.warofthegods.block;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The mod's own blocks, and the ids the buildings' .schematic files give them. A schematic holds block ids of one
 * byte, and the game hands out mods' block ids as it likes, so the structure generator writes these blocks with fixed
 * ids above Minecraft's own, which {@link #fromStored} turns back into the blocks. The web map uses the same ids.
 */
public final class ModBlocks {

    /** Ids of the mod's blocks in schematics, as tools/structures/generate.py writes them. */
    public static final int GRAYSTONE_ID = 200, GRAYSTONE_SLAB_ID = 201, GRAYSTONE_DOUBLE_SLAB_ID = 202,
        GRAYSTONE_STAIRS_ID = 203, WOOD_DOOR_ID = 204, BANNER_ID = 205, STATIC_FIRE_ID = 209, BANDOS_BANNER_ID = 210,
        LAST_ID = 211;

    /**
     * The banners' kinds, in the order of their ids: Saradomin's and Zamorak's from {@link #BANNER_ID}, Bandos's, which
     * came after the fire, from {@link #BANDOS_BANNER_ID}.
     */
    public static final String[] BANNER_KINDS = { "saradomin", "saradomin_inverted", "zamorak", "zamorak_inverted",
        "bandos", "bandos_inverted" };

    public static Block graystoneBricks;
    public static BlockGraystoneSlab graystoneSlab;
    public static BlockGraystoneSlab graystoneDoubleSlab;
    public static Block graystoneStairs;
    public static Block woodDoor;
    public static Item woodDoorItem;
    public static BlockBanner[] banners;
    public static Block staticFire;

    private static final Block[] STORED = new Block[256];

    private ModBlocks() {}

    public static void register() {
        graystoneBricks = new BlockGraystone();
        GameRegistry.registerBlock(graystoneBricks, ItemGraystone.class, "graystone_bricks");
        graystoneSlab = new BlockGraystoneSlab(false);
        graystoneDoubleSlab = new BlockGraystoneSlab(true);
        GameRegistry.registerBlock(graystoneSlab, ItemGraystoneSlab.class, "graystone_slab");
        GameRegistry.registerBlock(graystoneDoubleSlab, ItemGraystoneSlab.class, "graystone_double_slab");
        graystoneStairs = new BlockGraystoneStairs();
        GameRegistry.registerBlock(graystoneStairs, "graystone_stairs");
        woodDoor = new BlockWoodDoor();
        GameRegistry.registerBlock(woodDoor, "wood_door");
        woodDoorItem = new ItemWoodDoor();
        GameRegistry.registerItem(woodDoorItem, "wood_door_item");
        banners = new BlockBanner[BANNER_KINDS.length];
        for (int i = 0; i < banners.length; i++) {
            banners[i] = new BlockBanner(BANNER_KINDS[i]);
            GameRegistry.registerBlock(banners[i], ItemBlock.class, "banner_" + BANNER_KINDS[i]);
            STORED[i < 4 ? BANNER_ID + i : BANDOS_BANNER_ID + i - 4] = banners[i];
        }
        staticFire = new BlockStaticFire();
        GameRegistry.registerBlock(staticFire, "static_fire");

        STORED[GRAYSTONE_ID] = graystoneBricks;
        STORED[GRAYSTONE_SLAB_ID] = graystoneSlab;
        STORED[GRAYSTONE_DOUBLE_SLAB_ID] = graystoneDoubleSlab;
        STORED[GRAYSTONE_STAIRS_ID] = graystoneStairs;
        STORED[WOOD_DOOR_ID] = woodDoor;
        STORED[STATIC_FIRE_ID] = staticFire;
    }

    /** The block a schematic's block id stands for: one of the mod's own, or Minecraft's block of that id. */
    public static Block fromStored(int id) {
        Block block = id >= 0 && id < STORED.length ? STORED[id] : null;
        return block != null ? block : Block.getBlockById(id);
    }

    /** The id a block has in schematics and on the web map: its stored id if it is one of the mod's, else its own. */
    public static int storedId(Block block) {
        for (int id = GRAYSTONE_ID; id <= LAST_ID; id++) {
            if (STORED[id] == block && block != null) {
                return id;
            }
        }
        return Block.getIdFromBlock(block);
    }
}
