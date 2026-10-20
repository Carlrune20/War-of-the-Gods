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
    LESSER_DEMON("lesser_demon", "Lesser demon", "lesserDemon", 10, 8, 0, 0, 3, false, false),
    /**
     * A centaur of Saradomin, man above and horse below, trained at the monastery for food, wood and favor. It shoots
     * a longbow farther than an archer, harder, and runs as fast as a rider.
     */
    CENTAUR("centaur", "Centaur", "centaur", 4, 0, 2, 8, 4, true, false),
    /**
     * Bandos's slave, a goblin in rags and a shackle: gathers and builds as a worker does, never fights; trained at the
     * town center.
     */
    SLAVE("slave", "Slave", "slave", 1, 0, 0, 0, 1, false, false),
    /**
     * Bandos's goblin, cheap and weaker than a man, with less health and a weaker blow: fights and builds, trained at
     * the town center and goblin caves from Age I.
     */
    GOBLIN("goblin", "Goblin", "goblin", 0.6, -1, 0, 0, 1, false, false),
    /** A goblin who throws axes from a little closer than an archer shoots; builds too; trained at the war pit. */
    GOBLIN_THROWER("goblin_thrower", "Goblin axe-thrower", "goblinThrower", 0.6, 0, 1, -4, 1, true, false),
    /** A hobgoblin with a spear, a head taller than a man, that hits riders twice as hard; trained at the war pit. */
    HOBGOBLIN("hobgoblin", "Hobgoblin", "hobgoblin", 1.5, 6, 0, 0, 1, false, false),
    /** A big, slow ork with a cleaver, that nothing knocks back and that hits buildings hard; at the war pit. */
    ORK("ork", "Ork", "ork", 3, 8, 0, 0, 2, false, false),
    /** A huge, slow ogre with a club that smashes buildings; trained at the Bandos bastion. */
    OGRE("ogre", "Ogre", "ogre", 5, 10, 0, 0, 3, false, false),
    /**
     * Bandos's goblin priest: fights with weak spells and curses, prays at a Bandos temple while idle there, and turns
     * the team's kills near it into favor; trained at the temple.
     */
    GOBLIN_PRIEST("goblin_priest", "Goblin priest", "goblinPriest", 1, 0, 0, 0, 1, true, false),
    /** A ghostly goblin warrior in Bandos's armor, for favor; trained at the Bandos bastion. */
    SPIRITUAL_WARRIOR("spiritual_warrior", "Spiritual warrior", "spiritualWarrior", 3, 3, 0, 0, 2, false, false),
    /** A ghostly goblin ranger with a bow, for favor; trained at the Bandos bastion. */
    SPIRITUAL_RANGER("spiritual_ranger", "Spiritual ranger", "spiritualRanger", 2, 0, 3, 4, 2, true, false),
    /** A ghostly goblin mage casting earth and fire strikes, for favor; trained at the Bandos bastion. */
    SPIRITUAL_MAGE("spiritual_mage", "Spiritual mage", "spiritualMage", 2, 0, 3, 0, 2, true, false),
    /** A goblin hero of the God Wars who fights up close; goblins near him hit harder. At the Bandos bastion. */
    STRONGSTACK("strongstack", "Sergeant Strongstack", "strongstack", 7, 5, 0, 0, 4, false, true),
    /** A goblin hero of the God Wars who casts; goblins near him take less from spells. At the Bandos bastion. */
    STEELWILL("steelwill", "Sergeant Steelwill", "steelwill", 5, 0, 5, 2, 4, true, true),
    /** A goblin hero of the God Wars who shoots a bow farther than any archer. At the Bandos bastion. */
    GRIMSPIKE("grimspike", "Sergeant Grimspike", "grimspike", 5, 0, 5, 8, 4, true, true),
    /**
     * Bandos's general in the God Wars, a giant for favor with 1000 health: nothing knocks him back, and he hits
     * harder while one of his three sergeants fights near him. At the Bandos bastion.
     */
    GRAARDOR("graardor", "General Graardor", "graardor", 50, 12, 0, 0, 4, false, true);

    public final String id;
    public final String displayName;
    /** Name in the config file, such as archer in archerCostFood; empty for the warrior, whose keys came first. */
    public final String configName;
    /** Times a warrior's health it has, before upgrades; a goblin has less than one. */
    public final double healthFactor;
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

    UnitKind(String id, String displayName, String configName, double healthFactor, int extraDamage,
        int extraArrowDamage, int extraRange, int trainFactor, boolean ranged, boolean hero) {
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

    /** Whether it fights; workers and slaves do not, nor horses, chaos priests and monks. */
    public boolean fights() {
        return !gathers() && this != HORSE && !worships();
    }

    /**
     * Whether it earns its team favor and never fights: a chaos priest praying at a chaos temple, or a monk. A goblin
     * priest prays too, but fights; see {@link #praysAtTemple}.
     */
    public boolean worships() {
        return this == CHAOS_PRIEST || this == MONK;
    }

    /** Whether it prays for favor while idle by its god's temple: a chaos priest, or a goblin priest. */
    public boolean praysAtTemple() {
        return this == CHAOS_PRIEST || this == GOBLIN_PRIEST;
    }

    /** Whether it gathers food, wood and gold and carries them home: workers, and Bandos's slaves. */
    public boolean gathers() {
        return this == WORKER || this == SLAVE;
    }

    /** Whether it builds and repairs: workers and slaves, and Bandos's goblins and axe-throwers, who also fight. */
    public boolean builds() {
        return gathers() || this == GOBLIN || this == GOBLIN_THROWER;
    }

    /** The only faction whose teams train it, or null for one any team's buildings may train. */
    public Faction faction() {
        switch (this) {
            case SLAVE:
            case GOBLIN:
            case GOBLIN_THROWER:
            case HOBGOBLIN:
            case ORK:
            case OGRE:
            case GOBLIN_PRIEST:
            case SPIRITUAL_WARRIOR:
            case SPIRITUAL_RANGER:
            case SPIRITUAL_MAGE:
            case STRONGSTACK:
            case STEELWILL:
            case GRIMSPIKE:
            case GRAARDOR:
                return Faction.BANDOS;
            default:
                return null;
        }
    }

    /** Whether a team of the faction trains it: Bandos's teams train slaves rather than workers. */
    public boolean trainableBy(Faction faction) {
        if (faction() != null) {
            return faction() == faction;
        }
        return this != WORKER || faction != Faction.BANDOS;
    }

    /** Whether it casts spells from a distance rather than shooting arrows. */
    public boolean isCaster() {
        return this == DARK_WIZARD || this == ZAMORAK_MAGE
            || this == BLUE_WIZARD
            || this == GOBLIN_PRIEST
            || this == SPIRITUAL_MAGE
            || this == STEELWILL;
    }

    /** The spells it casts, in no order; it picks among them. */
    public Spell[] spells() {
        if (this == ZAMORAK_MAGE) {
            return new Spell[] { Spell.FIRE };
        }
        if (this == GOBLIN_PRIEST) {
            return new Spell[] { Spell.AIR, Spell.WEAKEN, Spell.CONFUSE };
        }
        if (this == SPIRITUAL_MAGE || this == STEELWILL) {
            return new Spell[] { Spell.EARTH, Spell.FIRE };
        }
        return isCaster() ? Spell.WIZARD_SPELLS : new Spell[0];
    }

    /** Whether it throws axes rather than shooting arrows: the goblin axe-thrower. */
    public boolean throwsAxes() {
        return this == GOBLIN_THROWER;
    }

    /** Whether it fights with a spear, hitting riders and centaurs twice as hard: the hobgoblin. */
    public boolean isSpearman() {
        return this == HOBGOBLIN;
    }

    /** Whether it is a ghost, drawn see-through: Bandos's spiritual warriors, rangers and mages. */
    public boolean isSpirit() {
        return this == SPIRITUAL_WARRIOR || this == SPIRITUAL_RANGER || this == SPIRITUAL_MAGE;
    }

    /** Whether it is one of Bandos's goblins, slaves and spirits and the three sergeants among them. */
    public boolean isGoblin() {
        return "goblin".equals(creatureModel()) || isSergeant();
    }

    /** Whether it is one of General Graardor's three sergeants, big goblins with a model of their own. */
    public boolean isSergeant() {
        return this == STRONGSTACK || this == STEELWILL || this == GRIMSPIKE;
    }

    /**
     * The name of the creature model it is drawn with, made by tools/models/generate.py, or null for the player model
     * or a model of its own: goblin, hobgoblin, ork, ogre, the sergeants' (named for Strongstack) or General
     * Graardor's.
     */
    public String creatureModel() {
        switch (this) {
            case GOBLIN:
            case GOBLIN_THROWER:
            case GOBLIN_PRIEST:
            case SPIRITUAL_WARRIOR:
            case SPIRITUAL_RANGER:
            case SPIRITUAL_MAGE:
                return "goblin";
            case STRONGSTACK:
            case STEELWILL:
            case GRIMSPIKE:
                return "strongstack";
            case HOBGOBLIN:
                return "hobgoblin";
            case ORK:
                return "ork";
            case GRAARDOR:
                return "graardor";
            case OGRE:
                return "ogre";
            default:
                return null;
        }
    }

    /** How fast it walks, as a share of a man's pace: orks, ogres and Graardor are slow, riders fast. */
    public double speedFactor() {
        if (isFast()) {
            return 1.5;
        }
        return this == ORK ? 0.75 : this == OGRE ? 0.7 : this == GRAARDOR ? 0.8 : 1;
    }

    /** How many times as hard as others its blows hit buildings: an ork half again, an ogre twice. */
    public double buildingDamageFactor() {
        return this == ORK ? 1.5 : this == OGRE ? 2 : 1;
    }

    /** Whether it is a myth unit, a creature of its god's that costs favor: heroes hit it twice as hard. */
    public boolean isMyth() {
        return this == LESSER_DEMON || this == CENTAUR;
    }

    /** Whether it is drawn with a model of its own rather than as a man: a lesser demon and a centaur. */
    public boolean hasOwnModel() {
        return this == LESSER_DEMON || this == CENTAUR;
    }

    /** Whether it runs as fast as a rider: cavalry, and a centaur on its own four legs. */
    public boolean isFast() {
        return isMounted() || this == CENTAUR;
    }

    /** How much bigger than a man it is drawn and stands. */
    public float scale() {
        return scale(0);
    }

    /**
     * How much bigger than a man it is drawn and stands at an elite tier (see {@link #eliteUpgrade}): a lesser demon
     * grows as a greater demon and again as a black demon.
     */
    public float scale(int tier) {
        if (this == LESSER_DEMON) {
            return tier >= 2 ? 2.0F : tier == 1 ? 1.75F : 1.5F;
        }
        if (this == GRAARDOR) {
            return 1.7F;
        }
        if (isGoblin()) {
            return hero ? 1.1F : 1;
        }
        if (this == HOBGOBLIN) {
            return 1.1F;
        }
        if (this == ORK) {
            return 1.25F;
        }
        if (this == OGRE) {
            return 1.5F;
        }
        return hero ? 1.15F : 1;
    }

    /** Whether it rides a horse: faster, with a horse drawn under it, and hit differently by arrows and blows. */
    public boolean isMounted() {
        return this == CAVALRY;
    }

    /**
     * Whether nothing knocks it back: a lesser demon, knights in their plate, elite or not, Sir Tradigee among them,
     * and Bandos's orks, ogres and General Graardor.
     */
    public boolean isUnshakable() {
        return this == LESSER_DEMON || this == WHITE_KNIGHT
            || this == BLACK_KNIGHT
            || this == TRADIGEE
            || this == ORK
            || this == OGRE
            || this == GRAARDOR;
    }

    /**
     * Whether training it brings out a tame horse for players to ride rather than a unit: it takes no room and is
     * not commanded.
     */
    public boolean isAnimal() {
        return this == HORSE;
    }

    /**
     * Whether it wears its team's armor upgrades for show: warriors, archers and cavalry, and goblins, their
     * axe-throwers and hobgoblins, whose models are drawn armored instead. Knights and heroes keep their own look,
     * though the upgrades still make them stronger.
     */
    public boolean showsArmor() {
        return this == SOLDIER || this == ARCHER
            || this == CAVALRY
            || this == GOBLIN
            || this == GOBLIN_THROWER
            || this == HOBGOBLIN;
    }

    /**
     * The upgrade that makes it elite, with its own look and name and more strength: Elite Black Knights for Black
     * Knights and Justiciars for White Knights at their fortress, greater demons for lesser demons at the chaos
     * temple; null for the rest.
     */
    public Upgrade eliteUpgrade() {
        return this == BLACK_KNIGHT ? Upgrade.ELITE_BLACK_KNIGHTS
            : this == WHITE_KNIGHT ? Upgrade.JUSTICIARS : this == LESSER_DEMON ? Upgrade.GREATER_DEMONS : null;
    }

    /** The upgrade that takes it a tier further after its elite one: black demons for lesser demons; null else. */
    public Upgrade higherUpgrade() {
        return this == LESSER_DEMON ? Upgrade.BLACK_DEMONS : null;
    }

    /** Its name once its team has made it elite, such as Justiciar; its own name for kinds that never are. */
    public String eliteName() {
        return eliteName(1);
    }

    /** Its name at an elite tier: 1 with its elite upgrade, 2 with its higher one too, as a black demon. */
    public String eliteName(int tier) {
        if (this == LESSER_DEMON) {
            return tier >= 2 ? "Black demon" : tier == 1 ? "Greater demon" : displayName;
        }
        return tier == 0 ? displayName
            : this == BLACK_KNIGHT ? "Elite Black Knight" : this == WHITE_KNIGHT ? "Justiciar" : displayName;
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
