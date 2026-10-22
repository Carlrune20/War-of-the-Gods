package com.warofthegods.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnitLooksTest {

    private static final String SKINS = "/assets/warofthegods/textures/entity/unit/";

    @Test
    void everyLookHasItsSkinAndNoMore() {
        int kinds = 0;
        for (Faction faction : Faction.values()) {
            for (UnitKind kind : UnitKind.values()) {
                int count = UnitLooks.count(faction, kind);
                String name = SKINS + faction.id + "_" + kind.id + "_";
                for (int look = 1; look <= count; look++) {
                    assertNotNull(getClass().getResource(name + look + ".png"), name + look + ".png");
                }
                assertNull(getClass().getResource(name + (count + 1) + ".png"), name + (count + 1) + ".png");
                kinds += count > 0 ? 1 : 0;
            }
        }
        assertTrue(kinds > 0);
    }

    @Test
    void workersOfEveryGodComeInLooks() {
        assertEquals(UnitLooks.LOOKS, UnitLooks.count(Faction.SARADOMIN, UnitKind.WORKER));
        assertEquals(UnitLooks.LOOKS, UnitLooks.count(Faction.ZAMORAK, UnitKind.WORKER));
        assertEquals(UnitLooks.LOOKS, UnitLooks.count(Faction.ARMADYL, UnitKind.WORKER));
        assertEquals(UnitLooks.LOOKS, UnitLooks.count(Faction.BANDOS, UnitKind.SLAVE));
    }

    @Test
    void everyBlueWizardsLookHasItsHat() {
        for (int look = 1; look <= UnitLooks.LOOKS; look++) {
            assertNotNull(getClass().getResource("/assets/warofthegods/textures/models/wizard_hat_" + look + ".png"));
        }
        assertNotNull(getClass().getResource("/assets/warofthegods/models/wizard_hat.json"));
    }
}
