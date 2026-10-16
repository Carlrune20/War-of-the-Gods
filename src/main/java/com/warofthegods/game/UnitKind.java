package com.warofthegods.game;

import java.util.LinkedHashMap;
import java.util.Map;

import com.warofthegods.Config;

/** The kinds of unit a team can train. Ids are stored in save data, so only add new values. */
public enum UnitKind {

    /** Fights up close with a sword; trained at a barracks. Saved as soldier, from before archers. */
    SOLDIER("soldier", "Warrior", "", 1, 0, 0, 0, 1, false, false),
    /** Builds, farms, mines gold and chops trees, never fights; trained at the town center. */
    WORKER("worker", "Worker", "worker", 1, 0, 0, 0, 1, false, false),
    /** Shoots arrows from a distance; trained at a barracks. */
    ARCHER("archer", "Archer", "archer", 1, 0, 0, 0, 1, true, false),
    /** A Saradomin knight in white plate, stronger than a warrior; trained at the White Knights' fortress. */
    WHITE_KNIGHT("white_knight", "White Knight", "whiteKnight", 2, 3, 0, 0, 2, false, false),
    /** A Zamorak knight in black plate, stronger than a warrior; trained at the Black Knights' fortress. */
    BLACK_KNIGHT("black_knight", "Black Knight", "blackKnight", 2, 3, 0, 0, 2, false, false),
    /** A ranger hero, the boss of Daemonheim; one at a time, trained at the Black Knights' fortress. */
    SAGITTARE("sagittare", "Sagittare", "sagittare", 6, 0, 6, 14, 4, true, true),
    /** A justiciar hero fighting up close; one at a time, trained at the White Knights' fortress. */
    TRAGIDEE("tragidee", "Sir Tragidee", "tragidee", 8, 6, 0, 0, 4, false, true),
    /**
     * A rider on horseback with a sword, faster than a warrior and stronger; trained at the stables. Hard to hit with
     * arrows on the move, but warriors' blows land harder on it.
     */
    CAVALRY("cavalry", "Cavalryman", "cavalry", 2, 2, 0, 0, 2, false, false),
    /** Not a unit but a tame, saddled horse for a player to ride; trained at the stables. */
    HORSE("horse", "Horse", "horse", 1, 0, 0, 0, 1, false, false);

    public final String id;
    public final String displayName;
    /** Name in the config file, such as archer in archerCostFood; empty for the warrior, whose keys came first. */
    public final String configName;
    /** Times a warrior's health it has, before upgrades. */
    public final int healthFactor;
    /** Damage it does up close on top of a warrior's, before upgrades. */
    public final int extraDamage;
    /** Damage its arrows do on top of an archer's, before upgrades. */
    public final int extraArrowDamage;
    /** Blocks it shoots farther than an archer. */
    public final int extraRange;
    /** Times as long as a warrior it takes to train. */
    public final int trainFactor;
    /** Whether it shoots arrows rather than fighting up close. */
    public final boolean ranged;
    /** Whether it is a hero: a team has at most one of each alive or in training, and may train it again once dead. */
    public final boolean hero;

    UnitKind(String id, String displayName, String configName, int healthFactor, int extraDamage, int extraArrowDamage,
        int extraRange, int trainFactor, boolean ranged, boolean hero) {
        this.id = id;
        this.displayName = displayName;
        this.configName = configName;
        this.healthFactor = healthFactor;
        this.extraDamage = extraDamage;
        this.extraArrowDamage = extraArrowDamage;
        this.extraRange = extraRange;
        this.trainFactor = trainFactor;
        this.ranged = ranged;
        this.hero = hero;
    }

    /** What training one costs, leaving out resources it costs none of. */
    public Map<Resource, Long> cost() {
        long[] amounts = Config.unitCosts.get(this);
        Map<Resource, Long> cost = new LinkedHashMap<>();
        for (Resource resource : Resource.values()) {
            if (amounts[resource.ordinal()] > 0) {
                cost.put(resource, amounts[resource.ordinal()]);
            }
        }
        return cost;
    }

    /** Whether it fights; workers do not, nor horses. */
    public boolean fights() {
        return this != WORKER && this != HORSE;
    }

    /** Whether it rides a horse: faster, with a horse drawn under it, and hit differently by arrows and blows. */
    public boolean isMounted() {
        return this == CAVALRY;
    }

    /**
     * Whether training it brings out a tame horse for players to ride rather than a unit: it takes no room and is
     * not commanded.
     */
    public boolean isAnimal() {
        return this == HORSE;
    }

    /**
     * Whether it wears its team's armor upgrades for show. Knights and heroes keep their own look, though the upgrades
     * still make them stronger.
     */
    public boolean showsArmor() {
        return this == SOLDIER || this == ARCHER;
    }

    /** How several are named, such as archers; a hero by his own name, as a team has only one. */
    public String plural() {
        return hero ? displayName : this == CAVALRY ? id : id + "s";
    }

    /** Ticks training one takes. */
    public int trainTicks() {
        return Math.max(1, Config.trainSeconds * 20 * trainFactor);
    }

    /** warrior is accepted for soldier, the name players see. */
    public static UnitKind byId(String id) {
        if ("warrior".equalsIgnoreCase(id) || "warriors".equalsIgnoreCase(id)) {
            return SOLDIER;
        }
        if ("sir_tragidee".equalsIgnoreCase(id)) {
            return TRAGIDEE;
        }
        for (UnitKind kind : values()) {
            if (kind.id.equalsIgnoreCase(id) || kind.name()
                .equalsIgnoreCase(id)) {
                return kind;
            }
        }
        return null;
    }
}
