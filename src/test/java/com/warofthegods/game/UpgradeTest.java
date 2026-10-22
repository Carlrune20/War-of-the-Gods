package com.warofthegods.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class UpgradeTest {

    @Test
    void onlyBandosArmsGoblinsAtTheTownCenterInAgeTwo() {
        assertSame(Faction.BANDOS, Upgrade.ARMED_GOBLINS.faction());
        assertSame(BuildingType.TOWN_HALL, Upgrade.ARMED_GOBLINS.at);
        assertEquals(2, Upgrade.ARMED_GOBLINS.level);
        assertSame(Upgrade.ARMED_GOBLINS, Upgrade.byId("armed_goblins"));
    }

    @Test
    void everyGodsUpgradesAndTheirOwnFortresses() {
        assertNull(Upgrade.FORTIFIED_WALLS.faction());
        assertSame(Faction.ZAMORAK, Upgrade.GREATER_DEMONS.faction());
        assertSame(Faction.SARADOMIN, Upgrade.JUSTICIARS.faction());
        assertSame(Faction.ARMADYL, Upgrade.ARMADYL_ARMOUR.faction());
        assertSame(Faction.ARMADYL, Upgrade.ARMADYL_CROSSBOWS.faction());
        assertSame(BuildingType.ARMORY, Upgrade.ARMADYL_CROSSBOWS.at);
        assertSame(BuildingType.OBSERVATORY, Upgrade.ARMADYLS_EYE.at);
    }
}
