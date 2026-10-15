package com.warofthegods.game;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.warofthegods.Config;

/** The kinds of building. Ids are stored in save data, so only add new values. */
public enum BuildingType {

    /**
     * Every team starts with one: trains workers, takes in what workers carry, houses units, and shoots arrows at
     * enemies once upgraded. Workers can build more, but only on a free settlement. Its id is from when it was called
     * the town hall.
     */
    TOWN_HALL("town_hall", "Town center", "townHall", 3, true, true, UnitKind.WORKER),
    /** Workers working at it gather food. */
    FARM("farm", "Farm", "farm", 2, true, false),
    /** Trains warriors and archers. */
    BARRACKS("barracks", "Barracks", "barracks", 2, true, false, UnitKind.SOLDIER, UnitKind.ARCHER),
    /** Takes in what workers carry, so they need not walk all the way home; researches economic upgrades. */
    STOREHOUSE("storehouse", "Storehouse", "storehouse", 2, true, true),
    /** Houses more units. */
    HOUSE("house", "House", "house", 2, true, false),
    /** Researches military upgrades. */
    ARMORY("armory", "Armory", "armory", 2, true, false),
    /** Watches over the land round it; shoots arrows at enemies once its team has watch towers. */
    TOWER("tower", "Tower", "tower", 2, true, false),
    /** A straight piece of wall, laid out with others in a line by {@link WallLine}. */
    WALL("wall", "Wall", "wall", 3, true, false),
    /** Where a wall turns or ends; comes with walls. */
    WALL_POST("wall_post", "Wall post", "wallPost", 3, false, false),
    /** A wall segment made into a gate, which opens for the team and its allies. */
    GATE("gate", "Gate", "gate", 3, false, false),
    /**
     * Saradomin's stronghold in Age III: a white stone keep with much health that shoots many arrows, training White
     * Knights and Sir Tragidee.
     */
    WHITE_KNIGHTS_FORTRESS("white_knights_fortress", "White Knights' fortress", "whiteKnightsFortress", 1, true, false,
        UnitKind.WHITE_KNIGHT, UnitKind.TRAGIDEE),
    /**
     * Zamorak's stronghold in Age III: a black stone keep with much health that shoots many arrows, training Black
     * Knights and Sagittare.
     */
    BLACK_KNIGHTS_FORTRESS("black_knights_fortress", "Black Knights' fortress", "blackKnightsFortress", 1, true, false,
        UnitKind.BLACK_KNIGHT, UnitKind.SAGITTARE);

    public final String id;
    public final String displayName;
    /** Name in the config file, such as farm in farmCostWood. */
    public final String configName;
    /**
     * Levels it has, each with its own look. The town center goes up a level to take its team into the next age;
     * every other building takes on the look of the age by itself.
     */
    public final int levels;
    /** Whether workers can build it. */
    public final boolean buildable;
    /** Whether workers bring what they gather here. */
    public final boolean storesResources;
    /** The units it trains, if any. */
    public final List<UnitKind> trains;

    /**
     * The age a team has to reach, by taking its town center up a level, before it can build this. Age I has no
     * military and no farms: food comes from hunting until then.
     */
    public int age() {
        if (isFortress()) {
            return 3;
        }
        return this == ARMORY || this == BARRACKS || this == FARM ? 2 : 1;
    }

    /** Whether it is one of the knights' fortresses. */
    public boolean isFortress() {
        return this == WHITE_KNIGHTS_FORTRESS || this == BLACK_KNIGHTS_FORTRESS;
    }

    /**
     * The only faction whose teams can build it, or null when every team can: the White Knights serve Saradomin and
     * the Black Knights Zamorak.
     */
    public Faction faction() {
        return this == WHITE_KNIGHTS_FORTRESS ? Faction.SARADOMIN
            : this == BLACK_KNIGHTS_FORTRESS ? Faction.ZAMORAK : null;
    }

    /** Whether a team of the faction can build it. */
    public boolean buildableBy(Faction faction) {
        return buildable && (faction() == null || faction() == faction);
    }

    /** The name of an age, such as "Age II". */
    public static String ageName(int age) {
        return "Age " + (age == 2 ? "II" : age == 3 ? "III" : "I");
    }

    BuildingType(String id, String displayName, String configName, int levels, boolean buildable,
        boolean storesResources, UnitKind... trains) {
        this.id = id;
        this.displayName = displayName;
        this.configName = configName;
        this.levels = levels;
        this.buildable = buildable;
        this.storesResources = storesResources;
        this.trains = Collections.unmodifiableList(Arrays.asList(trains));
    }

    /**
     * Whether it is a piece of wall: a segment, a post or a gate. Walls look their team's wall upgrades, not its age.
     */
    public boolean isWall() {
        return this == WALL || this == WALL_POST || this == GATE;
    }

    /** Whether workers can work here, gathering resources. */
    public boolean isWorkplace() {
        return this == FARM;
    }

    /** Whether it can shoot at enemies at that level; a tower only once its team has watch towers. */
    public boolean shoots(int level) {
        return this == TOWER || isFortress() || (this == TOWN_HALL && level >= 2);
    }

    /** Arrows it shoots at once, each at another of the nearest enemies: a fortress shoots many. */
    public int arrowsPerVolley() {
        return isFortress() ? Config.fortressArrows : 1;
    }

    /** Whether upgrades are researched here. */
    public boolean researches() {
        return this == ARMORY || this == STOREHOUSE || this == TOWN_HALL || this == TOWER;
    }

    /** What building one costs, leaving out resources it costs none of. */
    public Map<Resource, Long> cost() {
        return amounts(Config.buildingCost.get(this), 1);
    }

    /**
     * What taking one up to the level costs. Only the town center goes up by itself, taking its team into the next
     * age; for other buildings this is the building's own cost and a half for each level, as it was before ages.
     */
    public Map<Resource, Long> upgradeCost(int level) {
        if (this == TOWN_HALL) {
            return amounts(level <= 2 ? Config.townCenterLevel2Cost : Config.townCenterLevel3Cost, 1);
        }
        return amounts(Config.buildingCost.get(this), 1.5 * (level - 1));
    }

    /** Seconds an upgrade to the level takes. */
    public int upgradeTicks(int level) {
        return Math.max(1, Config.upgradeSeconds * 20 * (level - 1));
    }

    private static Map<Resource, Long> amounts(long[] amounts, double factor) {
        Map<Resource, Long> cost = new LinkedHashMap<>();
        for (Resource resource : Resource.values()) {
            long amount = Math.round(amounts[resource.ordinal()] * factor);
            if (amount > 0) {
                cost.put(resource, amount);
            }
        }
        return cost;
    }

    /** Health at level 1. */
    public int maxHealth() {
        return Config.buildingHealth.get(this);
    }

    /**
     * Health at a level: the same at every level, which only changes how it looks, except for walls, which have
     * twice as much in stone and three times as much in stone brick.
     */
    public int maxHealth(int level) {
        return isWall() ? maxHealth() * level : maxHealth();
    }

    /** Ticks of work one worker puts in to build it; two workers take half as long. */
    public int buildTicks() {
        return Math.max(1, Config.buildSeconds.get(this) * 20);
    }

    /** Resources one worker gathers here per harvest at a level, by resource ordinal; all zero where nothing is. */
    public long[] yield(int level) {
        long[] amounts = new long[Resource.values().length];
        // The same at every level, which only changes how it looks.
        if (this == FARM) {
            amounts[Resource.FOOD.ordinal()] = Config.farmFood;
        }
        return amounts;
    }

    /** Most workers that can work here at once. */
    public int maxWorkers() {
        return this == FARM ? Config.farmWorkers : 0;
    }

    /** Units it houses at a level: the town center houses more in each age, a house always as many. */
    public int population(int level) {
        if (this == TOWN_HALL) {
            return Config.townCenterPopulation + (level - 1) * Config.townCenterPopulation / 2;
        }
        if (this == HOUSE) {
            return Config.housePopulation;
        }
        return 0;
    }

    /** Blocks it shoots at a level, before upgrades; 0 if it does not shoot. */
    public int range(int level) {
        if (!shoots(level)) {
            return 0;
        }
        return isFortress() ? Config.fortressRange : Config.towerRange;
    }

    public static BuildingType byId(String id) {
        if ("town_center".equalsIgnoreCase(id) || "towncenter".equalsIgnoreCase(id)) {
            return TOWN_HALL;
        }
        for (BuildingType type : values()) {
            if (type.id.equalsIgnoreCase(id) || type.name()
                .equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
