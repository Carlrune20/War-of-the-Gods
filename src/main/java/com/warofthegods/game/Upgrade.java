package com.warofthegods.game;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Team-wide improvements researched at a building: military ones at the armory, economic ones at the storehouse,
 * and fortified walls at the town center.
 * Each is researched once and lasts the game. Ids are stored in save data, so only add new values.
 */
public enum Upgrade {

    FORGED_BLADES("forged_blades", "Forged blades", "Warriors hit 2 harder", BuildingType.ARMORY, 1, 0, 60, 40, 30),
    FLETCHING("fletching", "Fletching", "Archers and towers shoot 1 harder and 4 farther", BuildingType.ARMORY, 1, 0,
        80, 20, 30),
    PADDED_ARMOR("padded_armor", "Padded armor", "Warriors and archers have 6 more health", BuildingType.ARMORY, 1, 60,
        40, 20, 30),
    TEMPERED_STEEL("tempered_steel", "Tempered steel", "Warriors hit 2 harder again", BuildingType.ARMORY, 2, 0, 0,
        130, 45),
    PLATE_ARMOR("plate_armor", "Plate armor", "Warriors and archers have 8 more health", BuildingType.ARMORY, 2, 80, 0,
        130, 45),
    BASKETS("baskets", "Bigger baskets", "Workers carry 10 more", BuildingType.STOREHOUSE, 1, 40, 60, 0, 25),
    SHARP_AXES("sharp_axes", "Sharp axes", "Workers chop wood half again as fast", BuildingType.STOREHOUSE, 1, 50, 50,
        0, 25),
    PLOUGHS("ploughs", "Ploughs", "Farms yield half again as much food", BuildingType.STOREHOUSE, 1, 75, 75, 0, 30),
    PICKAXES("pickaxes", "Iron pickaxes", "Quarries yield half again as much", BuildingType.STOREHOUSE, 2, 50, 100,
        20, 30),
    MASONRY("masonry", "Masonry", "Buildings have a fifth more health", BuildingType.STOREHOUSE, 2, 0, 100, 100, 40),
    FORTIFIED_WALLS("fortified_walls", "Fortified walls", "Buildings have half again as much health",
        BuildingType.TOWN_HALL, 2, 0, 150, 150, 45);

    public final String id;
    public final String displayName;
    public final String description;
    /** The building it is researched at. */
    public final BuildingType at;
    /** The level that building has to be at, which is the age its team has to be in. */
    public final int level;
    private final long[] cost;
    public final int seconds;

    Upgrade(String id, String displayName, String description, BuildingType at, int level, long food, long wood,
        long gold, int seconds) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.at = at;
        this.level = level;
        this.cost = new long[] { food, wood, gold };
        this.seconds = seconds;
    }

    public Map<Resource, Long> cost() {
        Map<Resource, Long> result = new LinkedHashMap<>();
        for (Resource resource : Resource.values()) {
            if (cost[resource.ordinal()] > 0) {
                result.put(resource, cost[resource.ordinal()]);
            }
        }
        return result;
    }

    public int ticks() {
        return Math.max(1, seconds * 20);
    }

    public static Upgrade byId(String id) {
        for (Upgrade upgrade : values()) {
            if (upgrade.id.equalsIgnoreCase(id) || upgrade.name()
                .equalsIgnoreCase(id)) {
                return upgrade;
            }
        }
        return null;
    }
}
