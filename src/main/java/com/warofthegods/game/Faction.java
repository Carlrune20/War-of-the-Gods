package com.warofthegods.game;

import net.minecraft.util.EnumChatFormatting;

/**
 * Whose god a team follows, which decides how its buildings and units look. Saradomin's people build plain, human
 * stone and timber; Zamorak's build dark and sinister. Ids are stored in save data, so only add new values.
 */
public enum Faction {

    SARADOMIN("saradomin", "Saradomin"),
    ZAMORAK("zamorak", "Zamorak");

    public final String id;
    public final String displayName;

    Faction(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** Red and the dark colors follow Zamorak, every other color Saradomin. */
    public static Faction forColor(EnumChatFormatting color) {
        switch (color) {
            case RED:
            case DARK_RED:
            case DARK_PURPLE:
            case DARK_GRAY:
            case BLACK:
            case GOLD:
                return ZAMORAK;
            default:
                return SARADOMIN;
        }
    }

    public static Faction byId(String id) {
        for (Faction faction : values()) {
            if (faction.id.equalsIgnoreCase(id)) {
                return faction;
            }
        }
        return null;
    }
}
