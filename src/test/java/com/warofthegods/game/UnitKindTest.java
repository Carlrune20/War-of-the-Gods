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
        assertEquals(Faction.BANDOS, Faction.forColor(EnumChatFormatting.GREEN));
    }

    @Test
    void factionsTakeTurnsAndComeBackRound() {
        assertEquals(Faction.ZAMORAK, Faction.SARADOMIN.next());
        assertEquals(Faction.BANDOS, Faction.ZAMORAK.next());
        assertEquals(Faction.SARADOMIN, Faction.BANDOS.next());
    }

    @Test
    void bandosTrainsSlavesRatherThanWorkers() {
        assertFalse(UnitKind.WORKER.trainableBy(Faction.BANDOS));
        assertTrue(UnitKind.WORKER.trainableBy(Faction.ZAMORAK));
        assertTrue(UnitKind.SLAVE.trainableBy(Faction.BANDOS));
        assertFalse(UnitKind.SLAVE.trainableBy(Faction.SARADOMIN));
        assertFalse(UnitKind.GOBLIN.trainableBy(Faction.ZAMORAK));
    }

    @Test
    void slavesGatherAndGoblinsBuildAndFight() {
        assertTrue(UnitKind.SLAVE.gathers());
        assertFalse(UnitKind.SLAVE.fights());
        assertTrue(UnitKind.GOBLIN.builds());
        assertTrue(UnitKind.GOBLIN.fights());
        assertFalse(UnitKind.GOBLIN.gathers());
        assertTrue(UnitKind.GOBLIN_THROWER.builds());
        assertFalse(UnitKind.ORK.builds());
    }

    @Test
    void goblinPriestsFightAndPrayButDoNotWorshipLikeMonks() {
        assertTrue(UnitKind.GOBLIN_PRIEST.fights());
        assertTrue(UnitKind.GOBLIN_PRIEST.praysAtTemple());
        assertFalse(UnitKind.GOBLIN_PRIEST.worships());
    }

    @Test
    void goblinsAreWeakerThanWarriorsAndOgresTougherThanOrks() {
        assertTrue(UnitKind.GOBLIN.healthFactor < UnitKind.SOLDIER.healthFactor);
        assertTrue(UnitKind.OGRE.healthFactor > UnitKind.ORK.healthFactor);
        assertTrue(UnitKind.ORK.speedFactor() < 1);
    }

    @Test
    void everyBandosKindWearsACreatureModel() {
        for (UnitKind kind : UnitKind.values()) {
            if (kind.faction() == Faction.BANDOS && kind != UnitKind.SLAVE) {
                assertTrue(kind.creatureModel() != null, kind + " has no model");
            }
        }
        assertEquals("ork", UnitKind.GRAARDOR.creatureModel());
        assertEquals(null, UnitKind.SOLDIER.creatureModel());
        // Slaves are men, worn out and in rags.
        assertEquals(null, UnitKind.SLAVE.creatureModel());
    }

    @Test
    void bandosHasFourHeroes() {
        assertEquals(
            4,
            Arrays.stream(UnitKind.values())
                .filter(kind -> kind.hero && kind.faction() == Faction.BANDOS)
                .count());
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
