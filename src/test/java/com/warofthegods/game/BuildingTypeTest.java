package com.warofthegods.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BuildingTypeTest {

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
    void ageNamesAreRomanNumerals() {
        assertEquals("Age I", BuildingType.ageName(1));
        assertEquals("Age III", BuildingType.ageName(3));
    }
}
