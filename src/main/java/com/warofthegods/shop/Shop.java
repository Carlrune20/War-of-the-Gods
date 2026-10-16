package com.warofthegods.shop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.play.server.S2DPacketOpenWindow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.warofthegods.WarOfTheGods;
import com.warofthegods.building.Buildings;
import com.warofthegods.game.Building;
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Team;
import com.warofthegods.world.GameWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Shop chests, where commanders kit themselves out from their team's stores. The chest in a team's town center sells
 * cooked food for food, building blocks, stone tools and leather armor, and buys ores and ingots for gold, so what
 * players dig out of caves goes to their team. The chest in its armory sells iron tools, iron armor, bows and arrows
 * from Age II and diamond tools and armor from Age III. Everything is paid for out of the team's resources, and the
 * whole team is told what was bought. Nothing a shop sells can be sold back.
 */
public final class Shop {

    /** Blocks from the chest a player may stand and still use the shop. */
    private static final double REACH = 8;

    /** One thing on sale: what it is, what it costs in food, wood and gold, and the age it needs. */
    static final class Ware {

        final ItemStack item;
        final long[] cost;
        final int age;

        Ware(ItemStack item, long food, long wood, long gold, int age) {
            this.item = item;
            this.cost = new long[] { food, wood, gold };
            this.age = age;
        }

        Map<Resource, Long> cost() {
            Map<Resource, Long> cost = new LinkedHashMap<>();
            for (Resource resource : Resource.values()) {
                if (this.cost[resource.ordinal()] > 0) {
                    cost.put(resource, this.cost[resource.ordinal()]);
                }
            }
            return cost;
        }
    }

    /** A shop's wares, slot by slot, and whether it buys ores. */
    static final class Stall {

        final String title;
        final Ware[] wares = new Ware[54];
        final boolean buysOres;

        Stall(String title, boolean buysOres) {
            this.title = title;
            this.buysOres = buysOres;
        }

        void put(int row, int column, Ware ware) {
            wares[row * 9 + column] = ware;
        }
    }

    /** The slot of the window that shows the team's resources. */
    private static final int INFO_SLOT = 45;
    /** The slot of the town center's "sell everything" button. */
    private static final int SELL_SLOT = 53;

    private static final Stall TOWN_CENTER = new Stall("Town center shop", true);
    private static final Stall ARMORY = new Stall("Armory", false);

    /** Gold the town center pays for each ore, gem or ingot. */
    private static final Map<Item, Integer> ORE_PRICES = new LinkedHashMap<>();

    static {
        TOWN_CENTER.put(0, 0, new Ware(new ItemStack(Items.cooked_beef, 8), 25, 0, 0, 1));
        TOWN_CENTER.put(0, 1, new Ware(new ItemStack(Items.cooked_porkchop, 8), 25, 0, 0, 1));
        TOWN_CENTER.put(0, 2, new Ware(new ItemStack(Items.cooked_chicken, 8), 20, 0, 0, 1));
        TOWN_CENTER.put(0, 3, new Ware(new ItemStack(Items.bread, 8), 15, 0, 0, 1));
        TOWN_CENTER.put(0, 4, new Ware(new ItemStack(Items.baked_potato, 8), 15, 0, 0, 1));
        TOWN_CENTER.put(0, 5, new Ware(new ItemStack(Items.apple, 8), 10, 0, 0, 1));

        TOWN_CENTER.put(1, 0, new Ware(new ItemStack(Blocks.torch, 16), 0, 5, 0, 1));
        TOWN_CENTER.put(1, 1, new Ware(new ItemStack(Blocks.planks, 32), 0, 15, 0, 1));
        TOWN_CENTER.put(1, 2, new Ware(new ItemStack(Blocks.cobblestone, 32), 0, 0, 10, 1));
        TOWN_CENTER.put(1, 3, new Ware(new ItemStack(Blocks.ladder, 8), 0, 5, 0, 1));
        TOWN_CENTER.put(1, 4, new Ware(new ItemStack(Blocks.fence, 16), 0, 10, 0, 1));
        TOWN_CENTER.put(1, 5, new Ware(new ItemStack(Blocks.crafting_table), 0, 5, 0, 1));
        TOWN_CENTER.put(1, 6, new Ware(new ItemStack(Blocks.furnace), 0, 0, 5, 1));

        TOWN_CENTER.put(2, 0, new Ware(new ItemStack(Items.stone_sword), 0, 10, 5, 1));
        TOWN_CENTER.put(2, 1, new Ware(new ItemStack(Items.stone_pickaxe), 0, 10, 0, 1));
        TOWN_CENTER.put(2, 2, new Ware(new ItemStack(Items.stone_axe), 0, 10, 0, 1));
        TOWN_CENTER.put(2, 3, new Ware(new ItemStack(Items.stone_shovel), 0, 10, 0, 1));
        TOWN_CENTER.put(2, 5, new Ware(new ItemStack(Items.leather_helmet), 0, 0, 12, 1));
        TOWN_CENTER.put(2, 6, new Ware(new ItemStack(Items.leather_chestplate), 0, 0, 20, 1));
        TOWN_CENTER.put(2, 7, new Ware(new ItemStack(Items.leather_leggings), 0, 0, 16, 1));
        TOWN_CENTER.put(2, 8, new Ware(new ItemStack(Items.leather_boots), 0, 0, 12, 1));

        ARMORY.put(0, 0, new Ware(new ItemStack(Items.iron_sword), 0, 0, 40, 2));
        ARMORY.put(0, 1, new Ware(new ItemStack(Items.iron_pickaxe), 0, 0, 30, 2));
        ARMORY.put(0, 2, new Ware(new ItemStack(Items.iron_axe), 0, 0, 30, 2));
        ARMORY.put(0, 3, new Ware(new ItemStack(Items.iron_shovel), 0, 0, 30, 2));
        ARMORY.put(0, 5, new Ware(new ItemStack(Items.iron_helmet), 0, 0, 50, 2));
        ARMORY.put(0, 6, new Ware(new ItemStack(Items.iron_chestplate), 0, 0, 80, 2));
        ARMORY.put(0, 7, new Ware(new ItemStack(Items.iron_leggings), 0, 0, 70, 2));
        ARMORY.put(0, 8, new Ware(new ItemStack(Items.iron_boots), 0, 0, 40, 2));
        ARMORY.put(1, 0, new Ware(new ItemStack(Items.bow), 0, 20, 10, 2));
        ARMORY.put(1, 1, new Ware(new ItemStack(Items.arrow, 16), 0, 10, 0, 2));

        ARMORY.put(3, 0, new Ware(new ItemStack(Items.diamond_sword), 0, 0, 250, 3));
        ARMORY.put(3, 1, new Ware(new ItemStack(Items.diamond_pickaxe), 0, 0, 200, 3));
        ARMORY.put(3, 2, new Ware(new ItemStack(Items.diamond_axe), 0, 0, 200, 3));
        ARMORY.put(3, 3, new Ware(new ItemStack(Items.diamond_shovel), 0, 0, 200, 3));
        ARMORY.put(3, 5, new Ware(new ItemStack(Items.diamond_helmet), 0, 0, 250, 3));
        ARMORY.put(3, 6, new Ware(new ItemStack(Items.diamond_chestplate), 0, 0, 400, 3));
        ARMORY.put(3, 7, new Ware(new ItemStack(Items.diamond_leggings), 0, 0, 350, 3));
        ARMORY.put(3, 8, new Ware(new ItemStack(Items.diamond_boots), 0, 0, 200, 3));

        ORE_PRICES.put(Items.coal, 2);
        ORE_PRICES.put(Item.getItemFromBlock(Blocks.coal_ore), 2);
        ORE_PRICES.put(Item.getItemFromBlock(Blocks.iron_ore), 6);
        ORE_PRICES.put(Items.iron_ingot, 7);
        ORE_PRICES.put(Item.getItemFromBlock(Blocks.gold_ore), 12);
        ORE_PRICES.put(Items.gold_ingot, 13);
        ORE_PRICES.put(Items.emerald, 30);
        ORE_PRICES.put(Item.getItemFromBlock(Blocks.emerald_ore), 30);
        ORE_PRICES.put(Items.diamond, 50);
        ORE_PRICES.put(Item.getItemFromBlock(Blocks.diamond_ore), 50);
    }

    /** The shop a building keeps, or null for a building with none. */
    private static Stall stallOf(BuildingType type) {
        return type == BuildingType.TOWN_HALL ? TOWN_CENTER : type == BuildingType.ARMORY ? ARMORY : null;
    }

    /** Whether a kind of building keeps a shop chest. */
    public static boolean keepsShop(BuildingType type) {
        return stallOf(type) != null;
    }

    /** Right-clicking a chest in a finished town center or armory opens its shop instead of the chest. */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.world.isRemote
            || !(event.entityPlayer instanceof EntityPlayerMP)
            || !GameWorld.isGameWorld(event.world)
            || event.world.getBlock(event.x, event.y, event.z) != Blocks.chest) {
            return;
        }
        Game game = GameManager.getGame();
        Building building = game == null ? null
            : Buildings.get()
                .buildingWithBlock(game, event.x, event.y, event.z);
        Stall stall = building == null ? null : stallOf(building.type);
        if (stall == null) {
            return;
        }
        event.setCanceled(true);
        EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
        Team owner = game.getTeam(building.team);
        Team team = game.getTeamOf(player.getUniqueID());
        if (owner == null || team != owner) {
            tell(player, EnumChatFormatting.GRAY + "Only the " + building.describe(game) + "'s own team can shop here.");
            return;
        }
        if (!building.isComplete()) {
            tell(player, EnumChatFormatting.GRAY + "The shop opens once the building is finished.");
            return;
        }
        open(player, stall, building);
    }

    private static void open(EntityPlayerMP player, Stall stall, Building building) {
        InventoryBasic shelves = new InventoryBasic(stall.title, true, stall.wares.length);
        ContainerShop container = new ContainerShop(player, shelves, stall, building);
        fillShelves(player, container, stall);
        if (player.openContainer != player.inventoryContainer) {
            player.closeScreen();
        }
        player.getNextWindowId();
        player.playerNetServerHandler.sendPacket(
            new S2DPacketOpenWindow(player.currentWindowId, 0, stall.title, shelves.getSizeInventory(), true));
        player.openContainer = container;
        container.windowId = player.currentWindowId;
        container.addCraftingToCrafters(player);
    }

    /** Whether a player can go on using a shop: still on the team that owns it, still standing by it. */
    static boolean stillOpen(EntityPlayer player, Building building) {
        Game game = GameManager.getGame();
        if (game == null || game.getBuilding(building.id) != building || !building.isComplete()) {
            return false;
        }
        Team team = game.getTeamOf(player.getUniqueID());
        return team != null && team == game.getTeam(building.team)
            && building.distanceTo(player.posX, player.posZ) <= REACH;
    }

    /** Lays the wares out on the shelves, each labelled with its price and whether the team can have it yet. */
    static void fillShelves(EntityPlayerMP player, ContainerShop container, Stall stall) {
        Game game = GameManager.getGame();
        Team team = game == null ? null : game.getTeamOf(player.getUniqueID());
        int age = team == null ? 1 : team.getAge();
        for (int slot = 0; slot < stall.wares.length; slot++) {
            Ware ware = stall.wares[slot];
            ItemStack shown = null;
            if (ware != null) {
                shown = goods(ware, team);
                List<String> lore = new ArrayList<>();
                boolean affordable = team != null && team.canAfford(ware.cost());
                lore.add((affordable ? EnumChatFormatting.GREEN : EnumChatFormatting.RED) + "Costs " + costText(ware));
                if (ware.age > age) {
                    lore.add(EnumChatFormatting.RED + "Needs Age " + roman(ware.age));
                } else {
                    lore.add(EnumChatFormatting.GRAY + "Click to buy");
                }
                label(shown, null, lore);
            } else if (slot == INFO_SLOT && team != null) {
                shown = new ItemStack(Items.book);
                List<String> lore = new ArrayList<>();
                for (Resource resource : Resource.values()) {
                    lore.add(resource.color + resource.displayName + ": " + team.getResource(resource));
                }
                lore.add(EnumChatFormatting.GRAY + "Age " + roman(age));
                label(shown, EnumChatFormatting.GOLD + team.getName() + "'s stores", lore);
            } else if (slot == SELL_SLOT && stall.buysOres) {
                shown = new ItemStack(Blocks.hopper);
                List<String> lore = new ArrayList<>();
                lore.add(EnumChatFormatting.GRAY + "Sells every ore, gem and ingot you carry");
                for (Map.Entry<Item, Integer> entry : ORE_PRICES.entrySet()) {
                    lore.add(
                        EnumChatFormatting.YELLOW + new ItemStack(entry.getKey()).getDisplayName()
                            + ": "
                            + entry.getValue()
                            + " gold");
                }
                lore.add(EnumChatFormatting.GRAY + "Or shift-click a stack of your own to sell just that");
                label(shown, EnumChatFormatting.YELLOW + "Sell ores for gold", lore);
            }
            container.shelves.setInventorySlotContents(slot, shown);
        }
    }

    /** Buys what is on a shelf, or sells the player's ores from the sell button. */
    static void clickShelf(EntityPlayerMP player, ContainerShop container, Stall stall, int slot) {
        Game game = GameManager.getGame();
        Team team = game == null ? null : game.getTeamOf(player.getUniqueID());
        if (team == null || !stillOpen(player, container.building)) {
            return;
        }
        if (slot == SELL_SLOT && stall.buysOres) {
            sellAll(player, team);
            return;
        }
        Ware ware = slot < stall.wares.length ? stall.wares[slot] : null;
        if (ware == null) {
            return;
        }
        ItemStack goods = goods(ware, team);
        if (ware.age > team.getAge()) {
            tell(player, EnumChatFormatting.RED + goods.getDisplayName() + " needs Age " + roman(ware.age) + ".");
            return;
        }
        if (!team.spend(ware.cost())) {
            tell(player, EnumChatFormatting.RED + "Your team cannot afford " + costText(ware) + ".");
            return;
        }
        if (!player.inventory.addItemStackToInventory(goods) && goods.stackSize > 0) {
            player.dropPlayerItemWithRandomChoice(goods, false);
        }
        String what = (ware.item.stackSize > 1 ? ware.item.stackSize + " " : "") + ware.item.getDisplayName();
        tellTeam(
            game,
            team,
            EnumChatFormatting.GOLD + player.getCommandSenderName()
                + " bought "
                + what
                + " ("
                + costText(ware)
                + ")");
        WarOfTheGods.LOG.info("{} bought {} for {}", player.getCommandSenderName(), what, costText(ware));
    }

    /** Sells one stack the player shift-clicked, if the town center buys it. */
    static void sellStack(EntityPlayerMP player, ContainerShop container, Stall stall, Slot slot) {
        Game game = GameManager.getGame();
        Team team = game == null ? null : game.getTeamOf(player.getUniqueID());
        if (team == null || !stall.buysOres || !stillOpen(player, container.building)) {
            return;
        }
        ItemStack stack = slot.getStack();
        Integer price = stack == null || stack.hasTagCompound() ? null : ORE_PRICES.get(stack.getItem());
        if (price == null) {
            tell(player, EnumChatFormatting.GRAY + "The town center only buys ores, gems and ingots.");
            return;
        }
        long gold = (long) price * stack.stackSize;
        String what = stack.stackSize + " " + stack.getDisplayName();
        slot.putStack(null);
        sold(game, team, player, what, gold);
    }

    /** Sells every ore, gem and ingot the player carries. */
    private static void sellAll(EntityPlayerMP player, Team team) {
        long gold = 0;
        int count = 0;
        ItemStack[] stacks = player.inventory.mainInventory;
        for (int i = 0; i < stacks.length; i++) {
            ItemStack stack = stacks[i];
            Integer price = stack == null || stack.hasTagCompound() ? null : ORE_PRICES.get(stack.getItem());
            if (price != null) {
                gold += (long) price * stack.stackSize;
                count += stack.stackSize;
                stacks[i] = null;
            }
        }
        if (count == 0) {
            tell(player, EnumChatFormatting.GRAY + "You carry no ores, gems or ingots to sell.");
            return;
        }
        sold(GameManager.getGame(), team, player, count + " ores and ingots", gold);
    }

    private static void sold(Game game, Team team, EntityPlayerMP player, String what, long gold) {
        team.addResource(Resource.GOLD, gold);
        tellTeam(
            game,
            team,
            EnumChatFormatting.GOLD + player.getCommandSenderName() + " sold " + what + " for " + gold + " gold");
        WarOfTheGods.LOG.info("{} sold {} for {} gold", player.getCommandSenderName(), what, gold);
    }

    /** What a ware gives: a fresh copy, with leather armor dyed in the team's color. */
    private static ItemStack goods(Ware ware, Team team) {
        ItemStack stack = ware.item.copy();
        if (team != null && (stack.getItem() == Items.leather_helmet || stack.getItem() == Items.leather_chestplate
            || stack.getItem() == Items.leather_leggings
            || stack.getItem() == Items.leather_boots)) {
            NBTTagCompound display = new NBTTagCompound();
            display.setInteger("color", team.getRgb());
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag("display", display);
            stack.setTagCompound(tag);
        }
        return stack;
    }

    /** Gives a stack a name and lines of lore, as shown when it is hovered over. */
    private static void label(ItemStack stack, String name, List<String> lore) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound display = stack.getTagCompound()
            .getCompoundTag("display");
        if (name != null) {
            display.setString("Name", name);
        }
        NBTTagList lines = new NBTTagList();
        for (String line : lore) {
            lines.appendTag(new NBTTagString(line));
        }
        display.setTag("Lore", lines);
        stack.getTagCompound()
            .setTag("display", display);
    }

    private static String costText(Ware ware) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<Resource, Long> entry : ware.cost()
            .entrySet()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(entry.getValue())
                .append(" ")
                .append(
                    entry.getKey().displayName.toLowerCase());
        }
        return text.length() == 0 ? "nothing" : text.toString();
    }

    private static String roman(int age) {
        return age <= 1 ? "I" : age == 2 ? "II" : "III";
    }

    private static void tell(EntityPlayer player, String message) {
        player.addChatMessage(new ChatComponentText(message));
    }

    /** Tells everyone on a team who is online. */
    private static void tellTeam(Game game, Team team, String message) {
        for (Object object : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            EntityPlayerMP member = (EntityPlayerMP) object;
            if (game.getTeamOf(member.getUniqueID()) == team) {
                tell(member, message);
            }
        }
    }
}
