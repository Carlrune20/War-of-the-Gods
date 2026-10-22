package com.warofthegods.game;

/**
 * How many looks a kind of unit comes in, for a faction's teams: workers of every god (Bandos's slaves), Saradomin's
 * and Zamorak's warriors and archers, and the blue and dark wizards each have eight, told apart by face, hair, beard,
 * hat and the shade of their clothes. A unit takes one at random when it is trained; its skin is the kind's with the
 * look's number after it (saradomin_worker_1.png to saradomin_worker_8.png), drawn by tools/textures/looks.py, which
 * must draw as many as this says. A blue wizard also wears a pointed hat to match, wizard_hat_1.png and on.
 */
public final class UnitLooks {

    /** How many looks a kind has that has any. */
    public static final int LOOKS = 8;

    private UnitLooks() {}

    /** How many looks units of the kind trained by the faction's teams come in: 0 when they all look the same. */
    public static int count(Faction faction, UnitKind kind) {
        switch (kind) {
            case WORKER:
                return faction == Faction.BANDOS ? 0 : LOOKS;
            case SLAVE:
                return faction == Faction.BANDOS ? LOOKS : 0;
            case SOLDIER:
            case ARCHER:
                return faction == Faction.SARADOMIN || faction == Faction.ZAMORAK ? LOOKS : 0;
            case DARK_WIZARD:
            case BLUE_WIZARD:
                return LOOKS;
            default:
                return 0;
        }
    }
}
