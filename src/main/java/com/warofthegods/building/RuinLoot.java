package com.warofthegods.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraft.world.WorldServer;

import com.warofthegods.WarOfTheGods;
import com.warofthegods.game.Ruin;

/**
 * The old chests left in ruins: most ruins have one, against a wall where there is one, holding what was left behind
 * there: ores, food, building materials, a few weapons and pieces of armor, and arrows. Now and then one holds
 * something rare: a piece of runite armor, a runite sword or axe, or, rarer still, the dark bow, a godsword blade, a
 * prayer book, the dragon scimitar or a piece of dragon armor.
 */
final class RuinLoot {

    /** Percent of ruins that have a chest. */
    private static final int CHEST_CHANCE = 65;
    /** How many stacks of ordinary loot a chest holds, at least and at most. */
    private static final int MIN_STACKS = 4, MAX_STACKS = 8;

    private static WeightedRandomChestContent[] loot;

    private RuinLoot() {}

    private static WeightedRandomChestContent item(Item item, int min, int max, int weight) {
        return new WeightedRandomChestContent(item, 0, min, max, weight);
    }

    private static WeightedRandomChestContent block(Block block, int min, int max, int weight) {
        return new WeightedRandomChestContent(Item.getItemFromBlock(block), 0, min, max, weight);
    }

    private static WeightedRandomChestContent[] loot() {
        if (loot == null) {
            loot = new WeightedRandomChestContent[] {
                // Ores, to sell at the town center or smelt.
                item(Items.coal, 2, 8, 10), block(Blocks.iron_ore, 1, 4, 8), item(Items.iron_ingot, 1, 3, 4),
                block(Blocks.gold_ore, 1, 3, 5), item(Items.gold_ingot, 1, 2, 3), item(Items.diamond, 1, 2, 1),
                // Food.
                item(Items.bread, 1, 4, 10), item(Items.apple, 1, 4, 8), item(Items.cooked_beef, 1, 4, 6),
                item(Items.cooked_porkchop, 1, 4, 6), item(Items.cooked_chicken, 1, 4, 5),
                item(Items.baked_potato, 1, 4, 5),
                // Building materials.
                block(Blocks.planks, 4, 16, 10), block(Blocks.cobblestone, 8, 24, 10),
                block(Blocks.stonebrick, 4, 12, 6), block(Blocks.log, 2, 8, 6), block(Blocks.torch, 4, 12, 8),
                block(Blocks.ladder, 2, 6, 3), block(Blocks.glass, 2, 6, 3),
                // Weapons and armor.
                item(Items.stone_sword, 1, 1, 4), item(Items.iron_sword, 1, 1, 3), item(Items.iron_axe, 1, 1, 2),
                item(Items.bow, 1, 1, 4), item(Items.leather_helmet, 1, 1, 3), item(Items.leather_chestplate, 1, 1, 3),
                item(Items.chainmail_helmet, 1, 1, 2), item(Items.chainmail_chestplate, 1, 1, 2),
                item(Items.chainmail_leggings, 1, 1, 2), item(Items.chainmail_boots, 1, 1, 2),
                item(Items.iron_helmet, 1, 1, 2), item(Items.iron_chestplate, 1, 1, 1),
                item(Items.iron_leggings, 1, 1, 1), item(Items.iron_boots, 1, 1, 2),
                // Ammunition.
                item(Items.arrow, 4, 16, 10), };
        }
        return loot;
    }

    /** The rare things a chest may hold, each with its own chance in a thousand. */
    private static Object[][] rarities() {
        return new Object[][] { { WarOfTheGods.darkBow, 30 }, { WarOfTheGods.godswordBlade, 30 },
            { WarOfTheGods.prayerBook, 50 }, { WarOfTheGods.dragonScimitar, 30 }, { WarOfTheGods.dragonArmor[0], 20 },
            { WarOfTheGods.dragonArmor[1], 15 }, { WarOfTheGods.dragonArmor[2], 15 },
            { WarOfTheGods.dragonArmor[3], 20 },
            // Runite, Minecraft's diamond, twice as often as dragon or more.
            { Items.diamond_helmet, 60 }, { Items.diamond_chestplate, 40 }, { Items.diamond_leggings, 45 },
            { Items.diamond_boots, 60 }, { Items.diamond_sword, 60 }, { Items.diamond_axe, 40 }, };
    }

    /**
     * Maybe puts a chest of loot in a ruin just built: on its ground, out of its walls, with room above it to open,
     * against a wall where one stands.
     */
    static void stock(WorldServer world, Ruin ruin, Random random) {
        stock(world, ruin, random, false);
    }

    /** As {@link #stock(WorldServer, Ruin, Random)}, but always, when asked to: a lava maze's chest is always there. */
    static void stock(WorldServer world, Ruin ruin, Random random, boolean always) {
        if (!always && random.nextInt(100) >= CHEST_CHANCE) {
            return;
        }
        int y = ruin.groundY + 1;
        int[] spot = null;
        for (int tries = 0; tries < 40; tries++) {
            int x = ruin.minX + random.nextInt(ruin.sizeX);
            int z = ruin.minZ + random.nextInt(ruin.sizeZ);
            if (!world.isAirBlock(x, y, z) || world.getBlock(x, y + 1, z)
                .isOpaqueCube()
                || !world.getBlock(x, y - 1, z)
                    .isOpaqueCube()) {
                continue;
            }
            spot = new int[] { x, z };
            if (world.getBlock(x + 1, y, z)
                .isOpaqueCube()
                || world.getBlock(x - 1, y, z)
                    .isOpaqueCube()
                || world.getBlock(x, y, z + 1)
                    .isOpaqueCube()
                || world.getBlock(x, y, z - 1)
                    .isOpaqueCube()) {
                break;
            }
        }
        if (spot == null) {
            return;
        }
        world.setBlock(spot[0], y, spot[1], Blocks.chest, random.nextInt(4) + 2, 2);
        TileEntity tile = world.getTileEntity(spot[0], y, spot[1]);
        if (!(tile instanceof TileEntityChest)) {
            return;
        }
        TileEntityChest chest = (TileEntityChest) tile;
        WeightedRandomChestContent
            .generateChestContents(random, loot(), chest, MIN_STACKS + random.nextInt(MAX_STACKS - MIN_STACKS + 1));
        for (Object[] rare : rarities()) {
            if (random.nextInt(1000) < (Integer) rare[1]) {
                put(chest, new ItemStack((Item) rare[0]), random);
                WarOfTheGods.LOG.info(
                    "A {} lies in a chest in the {} at {} {} {}",
                    ((Item) rare[0]).getItemStackDisplayName(new ItemStack((Item) rare[0])),
                    ruin.design,
                    spot[0],
                    y,
                    spot[1]);
            }
        }
    }

    /** Puts a stack in an empty slot of the chest, if it has one. */
    private static void put(TileEntityChest chest, ItemStack stack, Random random) {
        List<Integer> empty = new ArrayList<>();
        for (int slot = 0; slot < chest.getSizeInventory(); slot++) {
            if (chest.getStackInSlot(slot) == null) {
                empty.add(slot);
            }
        }
        if (!empty.isEmpty()) {
            chest.setInventorySlotContents(empty.get(random.nextInt(empty.size())), stack);
        }
    }
}
