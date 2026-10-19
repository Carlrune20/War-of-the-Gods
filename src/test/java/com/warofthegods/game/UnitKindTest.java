package com.warofthegods.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import net.minecraft.util.EnumChatFormatting;

import org.junit.jupiter.api.Test;

class UnitKindTest {

    @Test
    void workersHorsesPriestsAndMonksDoNotFight() {
        assertFalse(UnitKind.WORKER.fights());
        assertFalse(UnitKind.HORSE.fights());
        assertFalse(UnitKind.CHAOS_PRIEST.fights());
        assertFalse(UnitKind.MONK.fights());
    }

    @Test
    void warriorsFight() {
        assertTrue(UnitKind.SOLDIER.fights());
    }

    @Test
    void onlyChaosPriestsAndMonksWorship() {
        assertTrue(UnitKind.CHAOS_PRIEST.worships());
        assertTrue(UnitKind.MONK.worships());
        assertFalse(UnitKind.SOLDIER.worships());
    }

    @Test
    void heroesAreSpecialUnitsNotOrdinaryOnes() {
        assertFalse(UnitKind.WORKER.hero);
        assertFalse(UnitKind.SOLDIER.hero);
        assertTrue(
            Arrays.stream(UnitKind.values())
                .anyMatch(kind -> kind.hero));
    }

    @Test
    void trainingCostNeverIncludesZeroAmounts() {
        for (UnitKind kind : UnitKind.values()) {
            kind.cost()
                .values()
                .forEach(amount -> assertTrue(amount > 0, kind + " has a zero cost entry"));
        }
    }

    @Test
    void idsResolveToTheirKind() {
        assertEquals(UnitKind.WORKER, UnitKind.byId("worker"));
        assertEquals(UnitKind.SOLDIER, UnitKind.byId("soldier"));
    }

    @Test
    void factionFollowsTheTeamColor() {
        assertEquals(Faction.ZAMORAK, Faction.forColor(EnumChatFormatting.RED));
        assertEquals(Faction.ZAMORAK, Faction.forColor(EnumChatFormatting.DARK_GRAY));
        assertEquals(Faction.SARADOMIN, Faction.forColor(EnumChatFormatting.BLUE));
        assertEquals(Faction.SARADOMIN, Faction.forColor(EnumChatFormatting.GREEN));
    }

    @Test
    void upgradeIdsResolve() {
        assertEquals(Upgrade.FORGED_BLADES, Upgrade.byId("forged_blades"));
        assertEquals(Upgrade.FORGED_BLADES, Upgrade.byId("FORGED_BLADES"));
        assertEquals(null, Upgrade.byId("no_such_upgrade"));
    }

    @Test
    void upgradeRequirementsPointAtEarlierUpgrades() {
        assertEquals(Upgrade.FORGED_BLADES, Upgrade.TEMPERED_STEEL.requires());
        assertEquals(null, Upgrade.FORGED_BLADES.requires());
    }
}
