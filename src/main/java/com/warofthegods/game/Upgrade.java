package com.warofthegods.game;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Team-wide improvements researched at a building: military ones at the armory, economic ones at the storehouse,
 * fortified walls at the town center, and watch and guard towers at a tower. Some need another first: diamond tools
 * and swords their iron ones, plate armor padded armor, fire arrows fletching and guard towers watch towers. Each is
 * researched once and lasts the game. Ids are stored in save data, so only add new values.
 */
public enum Upgrade {

    FORGED_BLADES("forged_blades", "Iron swords", "Warriors carry iron swords and hit 2 harder", BuildingType.ARMORY, 1,
        null, 0, 60, 40, 30),
    TEMPERED_STEEL("tempered_steel", "Diamond swords", "Warriors carry diamond swords and hit 2 harder again",
        BuildingType.ARMORY, 3, "forged_blades", 0, 0, 150, 45),
    FLETCHING("fletching", "Fletching", "Archers and towers shoot 1 harder and 4 farther", BuildingType.ARMORY, 1, null,
        0, 80, 20, 30),
    FIRE_ARROWS("fire_arrows", "Fire arrows", "Archers' arrows set what they hit on fire", BuildingType.ARMORY, 2,
        "fletching", 0, 80, 120, 45),
    PADDED_ARMOR("padded_armor", "Padded armor", "Warriors and archers have 6 more health", BuildingType.ARMORY, 1,
        null, 60, 40, 20, 30),
    PLATE_ARMOR("plate_armor", "Plate armor", "Warriors and archers have 8 more health", BuildingType.ARMORY, 2,
        "padded_armor", 80, 0, 130, 45),
    BASKETS("baskets", "Bigger baskets", "Workers carry 10 more", BuildingType.STOREHOUSE, 1, null, 40, 60, 0, 25),
    SHARP_AXES("sharp_axes", "Iron axes", "Workers carry iron axes and chop half again as fast",
        BuildingType.STOREHOUSE, 1, null, 50, 50, 0, 25),
    PICKAXES("pickaxes", "Iron pickaxes", "Workers carry iron pickaxes and mine gold half again as fast",
        BuildingType.STOREHOUSE, 1, null, 50, 100, 20, 30),
    PLOUGHS("ploughs", "Iron hoes", "Workers carry iron hoes; farms yield half again as much food",
        BuildingType.STOREHOUSE, 1, null, 75, 75, 0, 30),
    DIAMOND_AXES("diamond_axes", "Diamond axes", "Workers carry diamond axes and chop twice as fast",
        BuildingType.STOREHOUSE, 3, "sharp_axes", 100, 100, 150, 45),
    DIAMOND_PICKAXES("diamond_pickaxes", "Diamond pickaxes",
        "Workers carry diamond pickaxes and mine gold twice as fast", BuildingType.STOREHOUSE, 3, "pickaxes", 100, 150,
        150, 45),
    DIAMOND_HOES("diamond_hoes", "Diamond hoes", "Workers carry diamond hoes; farms yield twice as much food",
        BuildingType.STOREHOUSE, 3, "ploughs", 150, 100, 150, 45),
    MASONRY("masonry", "Masonry", "Buildings have a fifth more health", BuildingType.STOREHOUSE, 2, null, 0, 100, 100,
        40),
    FORTIFIED_WALLS("fortified_walls", "Fortified walls", "Buildings have half again as much health",
        BuildingType.TOWN_HALL, 2, null, 0, 150, 150, 45),
    WATCH_TOWER("watch_tower", "Watch towers",
        "Towers shoot arrows at enemies, flying as fast and hitting as hard as archers'", BuildingType.TOWER, 2, null,
        0, 100, 100, 40),
    GUARD_TOWER("guard_tower", "Guard towers", "Towers see enemies and shoot 8 farther", BuildingType.TOWER, 3,
        "watch_tower", 0, 150, 200, 50);

    public final String id;
    public final String displayName;
    public final String description;
    /** The building it is researched at. */
    public final BuildingType at;
    /** The level that building has to be at, which is the age its team has to be in. */
    public final int level;
    /** The id of the upgrade the team has to have first, or null. Until then this one is not offered. */
    private final String requiresId;
    private final long[] cost;
    public final int seconds;

    Upgrade(String id, String displayName, String description, BuildingType at, int level, String requiresId, long food,
        long wood, long gold, int seconds) {
        this.id = id;
        this.requiresId = requiresId;
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

    /** The upgrade the team has to have first, or null. */
    public Upgrade requires() {
        return requiresId == null ? null : byId(requiresId);
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
