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
    /** A Saradomin knight in white plate, stronger than a warrior; trained at the White Knights' castle. */
    WHITE_KNIGHT("white_knight", "White Knight", "whiteKnight", 2, 3, 0, 0, 2, false, false),
    /** A Zamorak knight in black plate, stronger than a warrior; trained at the Kinshra fortress. */
    BLACK_KNIGHT("black_knight", "Black Knight", "blackKnight", 2, 3, 0, 0, 2, false, false),
    /** A ranger hero, the boss of Daemonheim; one at a time, trained at the Kinshra fortress. */
    SAGITTARE("sagittare", "Sagittare", "sagittare", 6, 0, 6, 14, 4, true, true),
    /** A justiciar hero fighting up close; one at a time, trained at the White Knights' castle. */
    TRADIGEE("tradigee", "Sir Tradigee", "tradigee", 8, 6, 0, 0, 4, false, true),
    /**
     * A rider on horseback with a sword, faster than a warrior and stronger; trained at the stables. Hard to hit with
     * arrows on the move, but warriors' blows land harder on it.
     */
    CAVALRY("cavalry", "Cavalryman", "cavalry", 2, 2, 0, 0, 2, false, false),
    /** Not a unit but a tame, saddled horse for a player to ride; trained at the stables. */
    HORSE("horse", "Horse", "horse", 1, 0, 0, 0, 1, false, false),
    /** Zamorak's priest: never fights, but prays at a chaos temple, earning its team favor; trained there. */
    CHAOS_PRIEST("chaos_priest", "Chaos priest", "chaosPriest", 1, 0, 0, 0, 1, false, false),
    /** Saradomin's monk: never fights, but earns its team favor just by living; trained at the monastery. */
    MONK("monk", "Monk", "monk", 1, 0, 0, 0, 1, false, false),
    /**
     * Zamorak's dark wizard: casts air, water, earth and fire spells, and weakens and confuses enemies; trained at the
     * chaos temple. Only dark wizards raise stone circles, where four of them summon a lesser demon.
     */
    DARK_WIZARD("dark_wizard", "Dark wizard", "darkWizard", 1, 0, 0, 0, 1, true, false),
    /** Zamorak's mage, stronger than a dark wizard, with a Zamorak staff: fire spells only; trained at the temple. */
    ZAMORAK_MAGE("zamorak_mage", "Zamorak mage", "zamorakMage", 2, 0, 0, 2, 2, true, false),
    /** Saradomin's wizard: casts the same spells as a dark wizard; trained at the wizards' tower. */
    BLUE_WIZARD("blue_wizard", "Blue wizard", "blueWizard", 1, 0, 0, 0, 1, true, false),
    /**
     * A lesser demon of Zamorak, huge and strong, summoned at a stone circle by four dark wizards for favor, food and
     * gold. Nothing knocks it back, and between its blows it hurls a fireball every 10 to 15 seconds.
     */
    LESSER_DEMON("lesser_demon", "Lesser demon", "lesserDemon", 10, 8, 0, 0, 6, false, false);

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
            if (Resource.amount(amounts, resource) > 0) {
                cost.put(resource, Resource.amount(amounts, resource));
            }
        }
        return cost;
    }

    /** Whether it fights; workers do not, nor horses, priests and monks. */
    public boolean fights() {
        return this != WORKER && this != HORSE && !worships();
    }

    /** Whether it earns its team favor: a chaos priest praying at a chaos temple, or a monk. */
    public boolean worships() {
        return this == CHAOS_PRIEST || this == MONK;
    }

    /** Whether it casts spells from a distance rather than shooting arrows. */
    public boolean isCaster() {
        return this == DARK_WIZARD || this == ZAMORAK_MAGE || this == BLUE_WIZARD;
    }

    /** The spells it casts, in no order; it picks among them. */
    public Spell[] spells() {
        if (this == ZAMORAK_MAGE) {
            return new Spell[] { Spell.FIRE };
        }
        return isCaster() ? Spell.WIZARD_SPELLS : new Spell[0];
    }

    /** How much bigger than a man it is drawn and stands. */
    public float scale() {
        return this == LESSER_DEMON ? 1.5F : hero ? 1.15F : 1;
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

    /**
     * The upgrade at its fortress that makes it elite, with its own look and name and more strength: Elite Black
     * Knights for Black Knights, Justiciars for White Knights; null for the rest.
     */
    public Upgrade eliteUpgrade() {
        return this == BLACK_KNIGHT ? Upgrade.ELITE_BLACK_KNIGHTS : this == WHITE_KNIGHT ? Upgrade.JUSTICIARS : null;
    }

    /** Its name once its team has made it elite, such as Justiciar; its own name for kinds that never are. */
    public String eliteName() {
        return this == BLACK_KNIGHT ? "Elite Black Knight" : this == WHITE_KNIGHT ? "Justiciar" : displayName;
    }

    /** How several are named, such as archers; a hero by his own name, as a team has only one. */
    public String plural() {
        return hero ? displayName : this == CAVALRY ? id : lowerName() + "s";
    }

    /** Its name in the middle of a sentence: lower case, but for the god's name in Zamorak mage. */
    public String lowerName() {
        return this == ZAMORAK_MAGE ? displayName : displayName.toLowerCase();
    }

    /** Ticks training one takes. */
    public int trainTicks() {
        return Math.max(1, Config.trainSeconds * 20 * trainFactor);
    }

    /**
     * warrior is accepted for soldier, the name players see, and tragidee for Sir Tradigee, as he was misspelled in
     * games saved before.
     */
    public static UnitKind byId(String id) {
        if ("warrior".equalsIgnoreCase(id) || "warriors".equalsIgnoreCase(id)) {
            return SOLDIER;
        }
        if ("sir_tradigee".equalsIgnoreCase(id) || "tragidee".equalsIgnoreCase(id)
            || "sir_tragidee".equalsIgnoreCase(id)) {
            return TRADIGEE;
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
