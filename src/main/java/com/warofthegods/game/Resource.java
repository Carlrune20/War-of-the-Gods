package com.warofthegods.game;

import net.minecraft.util.EnumChatFormatting;

/** The four team resources. Ordinals are stored in save data, so only append new values. */
public enum Resource {

    FOOD("food", "Food", EnumChatFormatting.GREEN),
    WOOD("wood", "Wood", EnumChatFormatting.GOLD),
    STONE("stone", "Stone", EnumChatFormatting.GRAY),
    METAL("metal", "Metal", EnumChatFormatting.AQUA);

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
