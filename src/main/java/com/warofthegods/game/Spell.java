package com.warofthegods.game;

import net.minecraft.potion.Potion;

/**
 * The spells wizards and mages cast: the four elemental strikes, which hurt, fire setting the target alight too, and
 * Armadyl's wind throwing it back, and the curses, which hurt no one but weaken an enemy for a few seconds, so it hits
 * softer, or confuse it with nausea.
 * Ids are sent to clients by ordinal, so only append new values.
 */
public enum Spell {

    AIR("air", "Air strike", 4, 0, 0, "cloud", 0xE6F0F5),
    WATER("water", "Water strike", 5, 0, 0, "splash", 0x3C64E6),
    EARTH("earth", "Earth strike", 6, 0, 0, "blockcrack_3_0", 0x6E4B28),
    FIRE("fire", "Fire strike", 7, 0, 0, "flame", 0xF06414),
    /** Weakness for a few seconds: the enemy's blows do less. */
    WEAKEN("weaken", "Weaken", 0, Potion.weakness.id, 60, "witchMagic", 0x8C8C9C),
    /** Nausea for six seconds. */
    CONFUSE("confuse", "Confuse", 0, Potion.confusion.id, 120, "portal", 0xA03CC8),
    /**
     * A lesser demon's fireball: a great ball of flame that bursts where it lands, burning whoever it hits and the
     * caster's enemies round them, but no blocks. Wizards do not cast it.
     */
    FIREBALL("fireball", "Fireball", 10, 0, 0, "flame", 0xFF5A14),
    /**
     * Armadyl's wind strike, cast by aviansie mages and Wingman Skree: a gust that hurts and throws whoever it hits
     * back a little. Wizards do not cast it.
     */
    WIND("wind", "Wind strike", 6, 0, 0, "cloud", 0xC8F0FF);

    /** The spells wizards cast, picking among them. */
    public static final Spell[] WIZARD_SPELLS = { AIR, WATER, EARTH, FIRE, WEAKEN, CONFUSE };

    public final String id;
    public final String displayName;
    /** Damage an elemental strike does before the caster's own strength; 0 for a curse. */
    public final int damage;
    /** The effect a curse puts on, and for how many ticks. */
    public final int potion;
    public final int ticks;
    /** The particles it trails, and the color of the glow at its heart. */
    public final String particle;
    public final int color;

    Spell(String id, String displayName, int damage, int potion, int ticks, String particle, int color) {
        this.id = id;
        this.displayName = displayName;
        this.damage = damage;
        this.potion = potion;
        this.ticks = ticks;
        this.particle = particle;
        this.color = color;
    }

    /** Whether it is a curse, which hurts no one but puts an effect on an enemy. */
    public boolean isCurse() {
        return potion != 0;
    }

    public static Spell byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : FIRE;
    }
}
