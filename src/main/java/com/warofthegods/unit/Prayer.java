package com.warofthegods.unit;

import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;

import com.warofthegods.game.UnitKind;

/**
 * A protection prayer a hero keeps up: against the attack style it protects from, hits do a quarter of their damage
 * and never knock him back. Sir Tragidee switches between them freely; Sagittare always protects from missiles and
 * cannot protect from melee.
 */
public enum Prayer {

    NONE("none", "None"),
    /** Against swords, fists and claws: hits from whoever does the damage themselves. */
    MELEE("melee", "Protect from Melee"),
    /** Against arrows and anything else shot or thrown. */
    MISSILES("missiles", "Protect from Missiles");

    /** What is left of a protected hit's damage. */
    public static final float DAMAGE_TAKEN = 0.25F;

    public final String id;
    public final String displayName;

    Prayer(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** The style of attack a hit is, as a prayer protecting from it; NONE when no prayer does, such as fire. */
    public static Prayer styleOf(DamageSource source) {
        if (source.isProjectile()) {
            return MISSILES;
        }
        if (source.isMagicDamage() || source.isExplosion() || source.isFireDamage()) {
            return NONE;
        }
        Entity attacker = source.getEntity();
        return attacker != null && source.getSourceOfDamage() == attacker ? MELEE : NONE;
    }

    /** Whether a unit of the kind may have this prayer up. */
    public boolean allowedFor(UnitKind kind) {
        switch (kind) {
            case TRAGIDEE:
                return true;
            case SAGITTARE:
                return this == MISSILES;
            default:
                return this == NONE;
        }
    }

    /** The prayer a unit of the kind has up when it has none of its own choosing: Sagittare's missiles. */
    public static Prayer always(UnitKind kind) {
        return kind == UnitKind.SAGITTARE ? MISSILES : NONE;
    }

    public static Prayer byId(String id) {
        for (Prayer prayer : values()) {
            if (prayer.id.equalsIgnoreCase(id) || prayer.name()
                .equalsIgnoreCase(id)) {
                return prayer;
            }
        }
        return null;
    }

    public static Prayer byOrdinal(int ordinal) {
        Prayer[] prayers = values();
        return ordinal >= 0 && ordinal < prayers.length ? prayers[ordinal] : NONE;
    }
}
