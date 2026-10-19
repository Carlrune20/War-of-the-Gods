package com.warofthegods.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapAreaTest {

    private final MapArea area = new MapArea(-100, -50, 100, 50, 48);

    @Test
    void edgesAreInside() {
        assertTrue(area.isInside(-100, -50));
        assertTrue(area.isInside(100, 50));
        assertTrue(area.isInside(0, 0));
    }

    @Test
    void justOutsideIsOutside() {
        assertFalse(area.isInside(101, 0));
        assertFalse(area.isInside(0, -51));
    }

    @Test
    void distanceIsZeroInside() {
        assertEquals(0.0, area.distanceOutside(5, 5));
    }

    @Test
    void distanceOutsideAnEdgeIsStraightLine() {
        assertEquals(3.0, area.distanceOutside(103, 0));
    }

    @Test
    void distanceOutsideACornerIsDiagonal() {
        // 3 blocks out along x and 4 along z: a 3-4-5 triangle.
        assertEquals(5.0, area.distanceOutside(103, 54));
    }

    @Test
    void centerIsMidpointOfTheSquare() {
        MapArea centered = new MapArea(-10, -20, 10, 20, 0);
        assertEquals(0, centered.getCenterX());
        assertEquals(0, centered.getCenterZ());

        MapArea offset = new MapArea(0, 0, 10, 20, 0);
        assertEquals(5, offset.getCenterX());
        assertEquals(10, offset.getCenterZ());
    }
}
