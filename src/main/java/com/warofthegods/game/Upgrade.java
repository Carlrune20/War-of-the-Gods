package com.warofthegods.game;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Team-wide improvements researched at a building: military ones at the armory, economic ones, tunnel mining among
 * them, at the storehouse,
 * fortified, stone and citadel walls at the town center, and for Bandos armed goblins there too, watch and guard towers
 * at a tower, the knights' own at
 * their fortress, and greater and black demons, for favor too, at Zamorak's chaos temple. Some need another
 * first: runite tools
 * and swords their iron ones, plate armor padded armor, fire arrows fletching, guard towers watch towers and tunnel
 * mining, which lets workers dig quarries, runite pickaxes. Siege weapons at the armory let a stronghold build
 * trebuchets. Each is
 * researched once and lasts the game. Armadyl's teams research his eye and keen sight at the observatory, updrafts at
 * the roost, his armour for aviansie at the eyrie, divine law at his temple and his crossbows at the armory. Ids are
 * stored in save data, so only add new values.
 */
public enum Upgrade {

    FORGED_BLADES("forged_blades", "Iron swords", "Warriors carry iron swords and hit 2 harder", BuildingType.ARMORY, 1,
        null, 0, 60, 40, 30),
    TEMPERED_STEEL("tempered_steel", "Runite swords", "Warriors carry runite swords and hit 2 harder again",
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
    DIAMOND_AXES("diamond_axes", "Runite axes", "Workers carry runite axes and chop twice as fast",
        BuildingType.STOREHOUSE, 3, "sharp_axes", 100, 100, 150, 45),
    DIAMOND_PICKAXES("diamond_pickaxes", "Runite pickaxes", "Workers carry runite pickaxes and mine gold twice as fast",
        BuildingType.STOREHOUSE, 3, "pickaxes", 100, 150, 150, 45),
    DIAMOND_HOES("diamond_hoes", "Runite hoes", "Workers carry runite hoes; farms yield twice as much food",
        BuildingType.STOREHOUSE, 3, "ploughs", 150, 100, 150, 45),
    MASONRY("masonry", "Masonry", "Buildings have a fifth more health", BuildingType.STOREHOUSE, 2, null, 0, 100, 100,
        40),
    FORTIFIED_WALLS("fortified_walls", "Fortified walls", "Buildings have half again as much health",
        BuildingType.TOWN_HALL, 2, null, 0, 150, 150, 45),
    WATCH_TOWER("watch_tower", "Watch towers",
        "Towers shoot arrows at enemies, flying as fast and hitting as hard as archers'", BuildingType.TOWER, 2, null,
        0, 100, 100, 40),
    GUARD_TOWER("guard_tower", "Guard towers", "Towers see enemies and shoot 8 farther", BuildingType.TOWER, 3,
        "watch_tower", 0, 150, 200, 50),
    STONE_WALLS("stone_walls", "Stone walls", "Walls are rebuilt in stone and have twice the health",
        BuildingType.TOWN_HALL, 2, null, 0, 100, 150, 40),
    CITADEL_WALLS("citadel_walls", "Citadel walls",
        "Walls are rebuilt in stone brick, or nether brick for Zamorak, and have three times the health",
        BuildingType.TOWN_HALL, 3, "stone_walls", 0, 150, 300, 50),
    ELITE_BLACK_KNIGHTS("elite_black_knights", "Elite Black Knights",
        "Black Knights become elite: gold-trimmed plate and a red cape of Zamorak, 20 more health and hit 3 harder",
        BuildingType.BLACK_KNIGHTS_FORTRESS, 3, null, 400, 0, 600, 90),
    JUSTICIARS("justiciars", "Justiciars",
        "White Knights become justiciars: gold-trimmed plate and a crowned helm, 20 more health and hit 3 harder",
        BuildingType.WHITE_KNIGHTS_FORTRESS, 3, null, 400, 0, 600, 90),
    GREATER_DEMONS("greater_demons", "Greater demons",
        "Lesser demons become greater demons: winged and bigger, with 40 more health, hitting 3 harder",
        BuildingType.CHAOS_TEMPLE, 2, null, 300, 0, 400, 20, 60),
    BLACK_DEMONS("black_demons", "Black demons",
        "Greater demons become black demons: black, horned in bone and bigger still, with 100 more health than a "
            + "lesser demon, hitting 6 harder",
        BuildingType.CHAOS_TEMPLE, 3, "greater_demons", 500, 0, 700, 40, 90),
    TUNNEL_MINING("tunnel_mining", "Tunnel mining", "Workers can dig quarries into mined out gold mines",
        BuildingType.STOREHOUSE, 3, "diamond_pickaxes", 400, 400, 0, 60),
    SIEGE_WEAPONS("siege_weapons", "Siege weapons",
        "Your stronghold builds trebuchets, which hurl stones that burst where they land, smashing buildings from afar",
        BuildingType.ARMORY, 3, null, 300, 600, 600, 90),
    ARMED_GOBLINS("armed_goblins", "Armed goblins",
        "Goblins carry axes: they hit as hard as their swords upgrades let them, and hit buildings three times as hard",
        BuildingType.TOWN_HALL, 2, null, 50, 75, 0, 25),
    ARMADYLS_EYE("armadyls_eye", "Armadyl's eye",
        "The observatory can show a far spot of the map for 10 seconds, once every 5 minutes, for 3 favor a look; "
            + "the team watched sees the eye",
        BuildingType.OBSERVATORY, 3, null, 0, 200, 300, 60),
    KEEN_SIGHT("keen_sight", "Keen sight", "Archers and towers see and shoot 2 farther", BuildingType.OBSERVATORY, 2,
        null, 150, 0, 150, 40),
    UPDRAFTS("updrafts", "Updrafts", "Aviansie fly a quarter faster", BuildingType.ROOST, 2, null, 200, 0, 200, 5, 45),
    ARMADYL_ARMOUR("armadyl_armour", "Armadyl armour",
        "Aviansie wear Armadyl armour, white and teal with gold trim, and have half again as much health",
        BuildingType.ARMADYL_EYRIE, 3, null, 400, 0, 600, 15, 90),
    ARMADYL_CROSSBOWS("armadyl_crossbows", "Armadyl crossbows",
        "Archers carry Armadyl crossbows and shoot 2 harder, as fast as before", BuildingType.ARMORY, 3, null, 0, 300,
        400, 60),
    DIVINE_LAW("divine_law", "Divine law",
        "Armadyl grants favor for slaying anyone who attacked your team in the last 60 seconds, not 30",
        BuildingType.ARMADYL_TEMPLE, 3, null, 300, 0, 300, 60);

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
        this(id, displayName, description, at, level, requiresId, food, wood, gold, 0, seconds);
    }

    Upgrade(String id, String displayName, String description, BuildingType at, int level, String requiresId, long food,
        long wood, long gold, long favor, int seconds) {
        this.id = id;
        this.requiresId = requiresId;
        this.displayName = displayName;
        this.description = description;
        this.at = at;
        this.level = level;
        this.cost = new long[] { food, wood, gold, favor };
        this.seconds = seconds;
    }

    public Map<Resource, Long> cost() {
        Map<Resource, Long> result = new LinkedHashMap<>();
        for (Resource resource : Resource.values()) {
            if (Resource.amount(cost, resource) > 0) {
                result.put(resource, Resource.amount(cost, resource));
            }
        }
        return result;
    }

    /** The only faction whose teams research it, or null when every team can. */
    public Faction faction() {
        if (this == ARMADYL_CROSSBOWS) {
            return Faction.ARMADYL;
        }
        return this == ARMED_GOBLINS ? Faction.BANDOS : at.faction();
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
