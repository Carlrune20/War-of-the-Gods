package com.warofthegods.unit;

/**
 * The kinds of wild creature that roam Forinthry, each drawn with its own model (made by tools/models/wild.py) and
 * texture. Ordinals are sent to clients and ids saved with the creature, so only add new values at the end.
 */
public enum WildKind {

    /** Floats about the graveyards reaching for the living; arrows and spells mostly pass through it. */
    GHOST("ghost", "Ghost", "ghost", "ghost", 24, 4, 0.26, 0.6F, 1.9F, 1.0F, false),
    /** Nearly twice a man's height, slow, with a club that knocks men flying. */
    HILL_GIANT("hill_giant", "Hill giant", "hill_giant", "hill_giant", 90, 10, 0.22, 1.1F, 3.4F, 1.8F, false),
    /** Zamorak's hound, quick and fireproof, hunting in packs. */
    HELLHOUND("hellhound", "Hellhound", "hellhound", "hellhound", 50, 7, 0.32, 1.0F, 1.3F, 1.0F, true),
    /** Fast, and its bite poisons. */
    RED_SPIDER("red_spider", "Deadly red spider", "red_spider", "red_spider", 18, 3, 0.33, 1.4F, 0.9F, 1.0F, false),
    /** Stone through and through: everything does it less harm, and fire none. */
    GARGOYLE("gargoyle", "Gargoyle", "gargoyle", "gargoyle", 80, 9, 0.24, 0.9F, 2.5F, 1.3F, true),
    /** The chromatic dragons: they do not fly or breathe fire, but wander, and bite and claw whatever comes near. */
    GREEN_DRAGON("green_dragon", "Green dragon", "dragon", "dragon_green", 200, 12, 0.24, 2.0F, 2.4F, 1.3F, true),
    BLUE_DRAGON("blue_dragon", "Blue dragon", "dragon", "dragon_blue", 240, 13, 0.24, 2.0F, 2.4F, 1.3F, true),
    RED_DRAGON("red_dragon", "Red dragon", "dragon", "dragon", 280, 14, 0.24, 2.0F, 2.4F, 1.3F, true),
    BLACK_DRAGON("black_dragon", "Black dragon", "dragon", "dragon_black", 360, 16, 0.24, 2.0F, 2.4F, 1.3F, true),
    /** Keeps the bandits' camps, with a short sword. */
    BANDIT("bandit", "Bandit", "bandit", "bandit", 26, 5, 0.27, 0.6F, 1.8F, 1.0F, false);

    public final String id;
    public final String displayName;
    /** The model it is drawn with, models/model.json, and its texture, textures/models/texture.png. */
    public final String model;
    public final String texture;
    public final double health;
    public final double damage;
    public final double speed;
    public final float width;
    public final float height;
    /** How much bigger than its model it is drawn. */
    public final float scale;
    public final boolean fireproof;

    WildKind(String id, String displayName, String model, String texture, double health, double damage, double speed,
        float width, float height, float scale, boolean fireproof) {
        this.id = id;
        this.displayName = displayName;
        this.model = model;
        this.texture = texture;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.fireproof = fireproof;
    }

    public boolean isDragon() {
        return this == GREEN_DRAGON || this == BLUE_DRAGON || this == RED_DRAGON || this == BLACK_DRAGON;
    }

    /** Whether it walks on four legs (or eight) rather than two. */
    public boolean isBeast() {
        return isDragon() || this == HELLHOUND || this == RED_SPIDER;
    }

    /** How much of the harm it takes from arrows and spells it takes: a ghost a third, a gargoyle less too. */
    public float projectileFactor() {
        return this == GHOST ? 0.35F : this == GARGOYLE ? 0.6F : 1;
    }

    /** How much of the harm it takes from blows it takes: a gargoyle's stone turns some. */
    public float meleeFactor() {
        return this == GARGOYLE ? 0.7F : 1;
    }

    /** How much nothing knocks it back: giants, gargoyles and dragons stand their ground. */
    public double knockbackResistance() {
        return isDragon() ? 1 : this == HILL_GIANT || this == GARGOYLE ? 0.7 : 0;
    }

    public static WildKind byId(String id) {
        for (WildKind kind : values()) {
            if (kind.id.equalsIgnoreCase(id)) {
                return kind;
            }
        }
        return null;
    }

    public static WildKind byOrdinal(int ordinal) {
        WildKind[] kinds = values();
        return ordinal >= 0 && ordinal < kinds.length ? kinds[ordinal] : GHOST;
    }
}
