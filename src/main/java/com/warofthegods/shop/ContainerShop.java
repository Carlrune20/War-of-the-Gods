package com.warofthegods.shop;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.warofthegods.game.Building;

/**
 * A shop chest's window, run on the server only: the player sees a vanilla chest of the shop's wares over their own
 * inventory, laid out as a chest's is, so no client mod is needed for it. Clicking a ware buys it; shift-clicking a
 * stack of one's own sells it, where the shop buys it. Nothing can be put on or taken off the shelves by hand.
 */
final class ContainerShop extends Container {

    final InventoryBasic shelves;
    final Building building;
    private final Shop.Stall stall;

    ContainerShop(EntityPlayerMP player, InventoryBasic shelves, Shop.Stall stall, Building building) {
        this.shelves = shelves;
        this.stall = stall;
        this.building = building;
        int rows = shelves.getSizeInventory() / 9;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < 9; column++) {
                addSlotToContainer(new Shelf(shelves, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }
        int offset = (rows - 4) * 18;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlotToContainer(
                    new Slot(player.inventory, column + row * 9 + 9, 8 + column * 18, 103 + row * 18 + offset));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlotToContainer(new Slot(player.inventory, column, 8 + column * 18, 161 + offset));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return Shop.stillOpen(player, building);
    }

    @Override
    public ItemStack slotClick(int slot, int button, int mode, EntityPlayer player) {
        int shelfSlots = shelves.getSizeInventory();
        if (!(player instanceof EntityPlayerMP)) {
            return null;
        }
        EntityPlayerMP buyer = (EntityPlayerMP) player;
        if (slot >= 0 && slot < shelfSlots) {
            Shop.clickShelf(buyer, this, stall, slot);
            refresh(buyer);
            return null;
        }
        if (slot >= shelfSlots && slot < inventorySlots.size() && mode == 1) {
            Slot own = (Slot) inventorySlots.get(slot);
            if (own.getHasStack()) {
                Shop.sellStack(buyer, this, stall, own);
            }
            refresh(buyer);
            return null;
        }
        return super.slotClick(slot, button, mode, player);
    }

    /** Puts the player's view of the window right, after a click the server did something else with. */
    private void refresh(EntityPlayerMP player) {
        Shop.fillShelves(player, this, stall);
        player.sendContainerToPlayer(this);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slot) {
        return null;
    }

    /** A slot of the shop's wares, which shows its ware but is never taken from or put into. */
    private static final class Shelf extends Slot {

        Shelf(IInventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return false;
        }
    }
}
