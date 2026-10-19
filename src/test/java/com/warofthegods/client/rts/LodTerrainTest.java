package com.warofthegods.client.rts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LodTerrainTest {

    @Test
    void eachLevelIsForCellsUpToTwiceItsSize() {
        assertEquals(0, LodTerrain.levelFor(-1, 0.5));
        assertEquals(0, LodTerrain.levelFor(-1, 1.9));
        assertEquals(1, LodTerrain.levelFor(-1, 2));
        assertEquals(2, LodTerrain.levelFor(-1, 5));
        assertEquals(4, LodTerrain.levelFor(-1, 1000));
    }

    @Test
    void aChunkKeepsItsLevelUntilWellPastItsBounds() {
        // Level 1 is for 2 to 4 blocks; it holds from 1.6 to 5.
        assertEquals(1, LodTerrain.levelFor(1, 4.5));
        assertEquals(2, LodTerrain.levelFor(1, 5.5));
        assertEquals(1, LodTerrain.levelFor(1, 1.7));
        assertEquals(0, LodTerrain.levelFor(1, 1.5));
    }
}
