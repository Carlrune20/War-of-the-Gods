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
        assertEquals(Faction.ARMADYL, Faction.BANDOS.next());
        assertEquals(Faction.SARADOMIN, Faction.ARMADYL.next());
    }

    @Test
    void aquaTeamsFollowArmadyl() {
        assertEquals(Faction.ARMADYL, Faction.forColor(EnumChatFormatting.AQUA));
        assertEquals(Faction.ARMADYL, Faction.forColor(EnumChatFormatting.DARK_AQUA));
    }

    @Test
    void aviansieAndArmadylsHeroesFly() {
        for (UnitKind kind : new UnitKind[] { UnitKind.AVIANSIE_RANGER, UnitKind.AVIANSIE_WARRIOR,
            UnitKind.AVIANSIE_MAGE, UnitKind.KILISA, UnitKind.GEERIN, UnitKind.SKREE, UnitKind.KREEARRA }) {
            assertTrue(kind.flies(), kind.id);
            assertEquals(Faction.ARMADYL, kind.faction(), kind.id);
            assertEquals(0.25, kind.buildingDamageFactor(), 1e-9, kind.id);
        }
        assertFalse(UnitKind.GUARDIAN.flies());
        assertFalse(UnitKind.ARCHER.flies());
        // Only the melee fliers come down to fight.
        assertTrue(UnitKind.AVIANSIE_WARRIOR.landsToFight());
        assertTrue(UnitKind.KILISA.landsToFight());
        assertFalse(UnitKind.AVIANSIE_RANGER.landsToFight());
        assertFalse(UnitKind.KREEARRA.landsToFight());
    }

    @Test
    void armadylsWorkersAreFollowers() {
        assertEquals("Follower", UnitKind.WORKER.nameFor(Faction.ARMADYL));
        assertEquals("Worker", UnitKind.WORKER.nameFor(Faction.SARADOMIN));
        assertEquals(UnitKind.KREEARRA, UnitKind.byId("kree'arra"));
        assertEquals(3, UnitKind.AVIANSIE_WARRIOR.age());
        assertEquals(1, UnitKind.AVIANSIE_RANGER.age());
        assertEquals(Upgrade.ARMADYL_ARMOUR, UnitKind.AVIANSIE_MAGE.eliteUpgrade());
        assertEquals("Armoured aviansie ranger", UnitKind.AVIANSIE_RANGER.eliteName(1));
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
    void graardorHasAThousandHealthAtTheDefaultUnitHealth() {
        assertEquals(1000, 20 * UnitKind.GRAARDOR.healthFactor);
    }

    @Test
    void everyBandosKindWearsACreatureModel() {
        for (UnitKind kind : UnitKind.values()) {
            if (kind.faction() == Faction.BANDOS && kind != UnitKind.SLAVE) {
                assertTrue(kind.creatureModel() != null, kind + " has no model");
            }
        }
        assertEquals("graardor", UnitKind.GRAARDOR.creatureModel());
        assertEquals("strongstack", UnitKind.STEELWILL.creatureModel());
        assertTrue(UnitKind.STEELWILL.isGoblin());
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
    void trebuchetsAreBuiltAtStrongholdsWithSiegeWeaponsForWood() {
        assertEquals(Upgrade.SIEGE_WEAPONS, UnitKind.TREBUCHET.requiredUpgrade());
        assertEquals(BuildingType.ARMORY, Upgrade.SIEGE_WEAPONS.at);
        assertEquals(null, UnitKind.SOLDIER.requiredUpgrade());
        for (BuildingType type : BuildingType.values()) {
            assertEquals(type.isFortress(), type.trains.contains(UnitKind.TREBUCHET), type.id);
        }
        assertEquals(java.util.Collections.singletonMap(Resource.WOOD, 400L), UnitKind.TREBUCHET.cost());
        assertEquals(2.5, UnitKind.TREBUCHET.healthFactor);
        assertTrue(UnitKind.TREBUCHET.speedFactor() < UnitKind.OGRE.speedFactor());
        assertTrue(UnitKind.TREBUCHET.ranged && UnitKind.TREBUCHET.isSiege());
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
