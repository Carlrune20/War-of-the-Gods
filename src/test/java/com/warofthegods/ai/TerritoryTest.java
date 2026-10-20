package com.warofthegods.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerritoryTest {

    private static final double[][] OWN = { { 0, 0 } };
    private static final double[][] ENEMY = { { 200, 0 } };

    @Test
    void claimsTheNearestSpotOnItsSideFirst() {
        double[][] spots = { { 150, 0 }, { 40, 10 }, { 90, 0 } };

        assertEquals(1, Territory.claimNext(spots, OWN, ENEMY));
    }

    @Test
    void claimsTheMiddleButNotDeepInEnemyLand() {
        assertEquals(0, Territory.claimNext(new double[][] { { 105, 0 } }, OWN, ENEMY));
        assertEquals(-1, Territory.claimNext(new double[][] { { 170, 0 } }, OWN, ENEMY));
    }

    @Test
    void claimsAnythingWithNoEnemyLeft() {
        assertEquals(0, Territory.claimNext(new double[][] { { 500, 0 } }, OWN, new double[0][]));
    }

    @Test
    void frontIsTheSpotHalfwayToTheEnemy() {
        double[][] spots = { { 20, 0 }, { 100, 40 }, { 180, 0 } };

        assertEquals(1, Territory.front(spots, OWN, ENEMY));
    }

    @Test
    void noFrontWithoutEnemies() {
        assertEquals(-1, Territory.front(new double[][] { { 10, 0 } }, OWN, new double[0][]));
    }

    @Test
    void mainBaseAssaultsComeLaterAndInTurn() {
        assertFalse(Territory.isAssault(0));
        assertFalse(Territory.isAssault(2));
        assertTrue(Territory.isAssault(3));
        assertFalse(Territory.isAssault(4));
        assertTrue(Territory.isAssault(5));
    }
}
