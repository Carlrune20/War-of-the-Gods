package com.warofthegods.game;

import net.minecraft.util.EnumChatFormatting;

/**
 * The team resources: food, wood and gold, which workers gather, and favor, which the gods grant for worship. Saves
 * keep them by id; units' carried loads by ordinal, so only append new values.
 */
public enum Resource {

    FOOD("food", "Food", EnumChatFormatting.GREEN),
    WOOD("wood", "Wood", EnumChatFormatting.GOLD),
    GOLD("gold", "Gold", EnumChatFormatting.YELLOW),
    /** Granted by the gods for worship: Zamorak's priests praying at his temples, Saradomin's monks just living. */
    FAVOR("favor", "Favor", EnumChatFormatting.LIGHT_PURPLE);

    /** The resources workers gather and carry. */
    public static final Resource[] GATHERED = { FOOD, WOOD, GOLD };

    public final String id;
    public final String displayName;
    public final EnumChatFormatting color;

    Resource(String id, String displayName, EnumChatFormatting color) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
    }

    /**
     * The amount of the resource in amounts kept by ordinal, such as a cost; 0 where amounts stops short of it, as
     * costs from before favor do.
     */
    public static long amount(long[] amounts, Resource resource) {
        return resource.ordinal() < amounts.length ? amounts[resource.ordinal()] : 0;
    }

    public static Resource byId(String id) {
        for (Resource resource : values()) {
            if (resource.id.equalsIgnoreCase(id)) {
                return resource;
            }
        }
        return null;
    }
}
