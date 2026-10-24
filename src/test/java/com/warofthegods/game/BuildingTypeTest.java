package com.warofthegods.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BuildingTypeTest {

    @Test
    void bandosBuildsCavesAndWarPitsInsteadOfHousesAndBarracks() {
        assertFalse(BuildingType.HOUSE.buildableBy(Faction.BANDOS));
        assertFalse(BuildingType.BARRACKS.buildableBy(Faction.BANDOS));
        assertFalse(BuildingType.STABLES.buildableBy(Faction.BANDOS));
        assertTrue(BuildingType.GOBLIN_CAVE.buildableBy(Faction.BANDOS));
        assertTrue(BuildingType.WAR_PIT.buildableBy(Faction.BANDOS));
        assertFalse(BuildingType.WAR_PIT.buildableBy(Faction.SARADOMIN));
        assertTrue(BuildingType.HOUSE.buildableBy(Faction.ZAMORAK));
    }

    @Test
    void goblinCavesAreFreeAndHouseUnitsFromAgeOne() {
        assertEquals(1, BuildingType.GOBLIN_CAVE.age());
        assertTrue(
            BuildingType.GOBLIN_CAVE.cost()
                .isEmpty());
    }

    @Test
    void bandosTownCenterTrainsSlavesAndGoblinsButNotWorkers() {
        assertEquals(
            java.util.Arrays.asList(UnitKind.SLAVE, UnitKind.GOBLIN),
            BuildingType.TOWN_HALL.trainsFor(Faction.BANDOS));
        assertEquals(java.util.Arrays.asList(UnitKind.WORKER), BuildingType.TOWN_HALL.trainsFor(Faction.SARADOMIN));
    }

    @Test
    void theBastionIsBandossFortress() {
        assertTrue(BuildingType.BANDOS_BASTION.isFortress());
        assertEquals(Faction.BANDOS, BuildingType.BANDOS_BASTION.faction());
        assertEquals(3, BuildingType.BANDOS_BASTION.age());
    }

    @Test
    void idsResolveCaseInsensitively() {
        assertEquals(BuildingType.FARM, BuildingType.byId("farm"));
        assertEquals(BuildingType.FARM, BuildingType.byId("FARM"));
        assertNull(BuildingType.byId("castle_of_nothing"));
    }

    @Test
    void oldAndNewTownCenterNamesBothResolve() {
        assertEquals(BuildingType.TOWN_HALL, BuildingType.byId("town_center"));
        assertEquals(BuildingType.TOWN_HALL, BuildingType.byId("town_hall"));
        assertEquals(BuildingType.BLACK_KNIGHTS_FORTRESS, BuildingType.byId("kinshra_fortress"));
    }

    @Test
    void knightsFortressesAreAgeThreeAndFortresses() {
        assertEquals(3, BuildingType.WHITE_KNIGHTS_FORTRESS.age());
        assertEquals(3, BuildingType.BLACK_KNIGHTS_FORTRESS.age());
        assertTrue(BuildingType.BLACK_KNIGHTS_FORTRESS.isFortress());
        assertFalse(BuildingType.BARRACKS.isFortress());
    }

    @Test
    void housesAndTownCentersAreAvailableFromAgeOne() {
        assertEquals(1, BuildingType.HOUSE.age());
        assertEquals(1, BuildingType.TOWN_HALL.age());
        assertEquals(2, BuildingType.BARRACKS.age());
    }

    @Test
    void quarryNeedsTunnelMining() {
        assertEquals(Upgrade.TUNNEL_MINING, BuildingType.QUARRY.requiredUpgrade());
        assertNull(BuildingType.FARM.requiredUpgrade());
        // Researched where its runite pickaxes come from, so the storehouse offers it once those are done.
        assertEquals(BuildingType.STOREHOUSE, Upgrade.TUNNEL_MINING.at);
        assertEquals(Upgrade.TUNNEL_MINING.requires().at, Upgrade.TUNNEL_MINING.at);
    }

    @Test
    void godsBuildingsBelongToTheirFaction() {
        assertEquals(Faction.SARADOMIN, BuildingType.WIZARDS_TOWER.faction());
        assertEquals(Faction.ZAMORAK, BuildingType.CHAOS_TEMPLE.faction());
        assertNull(BuildingType.FARM.faction());
    }

    @Test
    void onlyFarmsAndQuarriesHaveWorkers() {
        assertTrue(BuildingType.FARM.maxWorkers() > 0);
        assertTrue(BuildingType.QUARRY.maxWorkers() > 0);
        assertEquals(0, BuildingType.HOUSE.maxWorkers());
    }

    @Test
    void townCenterHousesMoreUnitsInEachAge() {
        assertTrue(BuildingType.TOWN_HALL.population(2) > BuildingType.TOWN_HALL.population(1));
        assertTrue(BuildingType.TOWN_HALL.population(3) > BuildingType.TOWN_HALL.population(2));
    }

    @Test
    void wallsGetStrongerWithEachLevel() {
        assertEquals(BuildingType.WALL.maxHealth() * 2, BuildingType.WALL.maxHealth(2));
    }

    @Test
    void upgradesTakeAtLeastOneTick() {
        assertTrue(BuildingType.TOWN_HALL.upgradeTicks(1) >= 1);
        assertTrue(BuildingType.TOWN_HALL.upgradeTicks(3) >= BuildingType.TOWN_HALL.upgradeTicks(2));
    }

    @Test
    void goingUpATierTakesAgeOfMythologysAdvanceTimes() {
        assertEquals(60 * 20, BuildingType.TOWN_HALL.upgradeTicks(2));
        assertEquals(75 * 20, BuildingType.TOWN_HALL.upgradeTicks(3));
        assertEquals(
            400L,
            (long) BuildingType.TOWN_HALL.upgradeCost(2)
                .get(Resource.FOOD));
        assertEquals(
            500L,
            (long) BuildingType.TOWN_HALL.upgradeCost(3)
                .get(Resource.GOLD));
    }

    @Test
    void ageNamesAreRomanNumerals() {
        assertEquals("Tier I", BuildingType.ageName(1));
        assertEquals("Tier III", BuildingType.ageName(3));
    }

    @Test
    void armadylBuildsTowerHousesAndARoostButNoStables() {
        assertFalse(BuildingType.HOUSE.buildableBy(Faction.ARMADYL));
        assertFalse(BuildingType.STABLES.buildableBy(Faction.ARMADYL));
        assertTrue(BuildingType.TOWER_HOUSE.buildableBy(Faction.ARMADYL));
        assertTrue(BuildingType.ROOST.buildableBy(Faction.ARMADYL));
        assertFalse(BuildingType.ROOST.buildableBy(Faction.ZAMORAK));
        assertEquals(4, BuildingType.ROOST.minHeight());
        assertEquals(0, BuildingType.HOUSE.minHeight());
        assertTrue(BuildingType.ARMADYL_EYRIE.isFortress());
        assertTrue(BuildingType.ARMADYL_EYRIE.trains.contains(UnitKind.KREEARRA));
    }

    @Test
    void everyGodsTeamsBuildAMarketFromAgeTwo() {
        for (Faction faction : Faction.values()) {
            assertTrue(BuildingType.MARKET.buildableBy(faction));
        }
        assertEquals(2, BuildingType.MARKET.age());
        assertTrue(BuildingType.MARKET.trades());
        assertFalse(BuildingType.STOREHOUSE.trades());
        assertEquals(BuildingType.MARKET, BuildingType.byId("market"));
    }
}
