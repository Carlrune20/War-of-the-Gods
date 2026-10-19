package com.warofthegods.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerrainNoiseTest {

    @Test
    void sameSeedGivesSameValues() {
        TerrainNoise a = new TerrainNoise(42L);
        TerrainNoise b = new TerrainNoise(42L);

        assertEquals(a.fbm(12.5, -7.25, 4), b.fbm(12.5, -7.25, 4));
    }

    @Test
    void differentSeedsGiveDifferentTerrain() {
        TerrainNoise a = new TerrainNoise(1L);
        TerrainNoise b = new TerrainNoise(2L);

        boolean differs = false;
        for (int x = 0; x < 20 && !differs; x++) {
            differs = a.fbm(x, x, 3) != b.fbm(x, x, 3);
        }
        assertTrue(differs);
    }

    @Test
    void valuesStayWithinMinusOneToOne() {
        TerrainNoise noise = new TerrainNoise(7L);

        for (int x = -200; x <= 200; x += 7) {
            for (int z = -200; z <= 200; z += 11) {
                double value = noise.fbm(x * 0.37, z * 0.37, 5);
                assertTrue(value >= -1.0 && value <= 1.0, "out of range at " + x + "," + z + ": " + value);
            }
        }
    }

    @Test
    void neighbouringPointsDoNotJump() {
        // Smooth interpolation: a step of 0.01 changes the value by very little.
        TerrainNoise noise = new TerrainNoise(99L);

        for (double x = 0; x < 10; x += 0.37) {
            double here = noise.fbm(x, 3.0, 1);
            double next = noise.fbm(x + 0.01, 3.0, 1);
            assertTrue(Math.abs(here - next) < 0.1, "jump at x=" + x);
        }
    }
}
