package com.warofthegods.game;

import net.minecraft.util.EnumChatFormatting;

/**
 * The three team resources. Saves keep them by id; units' carried loads by ordinal, so only append new values.
 */
public enum Resource {

    FOOD("food", "Food", EnumChatFormatting.GREEN),
    WOOD("wood", "Wood", EnumChatFormatting.GOLD),
    GOLD("gold", "Gold", EnumChatFormatting.YELLOW);

    public final String id;
    public final String displayName;
    public final EnumChatFormatting color;

    Resource(String id, String displayName, EnumChatFormatting color) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
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
