package com.warofthegods.game;

/** The maps a game can be played on. Ids are stored in save data, so only add new values. */
public enum GameMap {

    /** Grassland and forest with a river across it and three stone bridges. */
    MISTHALIN("misthalin", "Misthalin"),
    /**
     * The Wilderness north of Edgeville, burnt black in the God Wars: charred earth and stone, dead trees, lava
     * rivers, mushrooms to eat, and far more that wants the players dead. It is always night there.
     */
    FORINTHRY("forinthry", "Forinthry");

    public final String id;
    public final String displayName;

    GameMap(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** The map with the id, or null. */
    public static GameMap byId(String id) {
        for (GameMap map : values()) {
            if (map.id.equalsIgnoreCase(id)) {
                return map;
            }
        }
        return null;
    }
}
