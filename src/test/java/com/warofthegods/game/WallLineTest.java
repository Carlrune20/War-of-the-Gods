package com.warofthegods.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class WallLineTest {

    private static final WallLine.Blocker NOTHING = (minX, minZ) -> null;

    @Test
    void straightWallHasPostsAtBothEndsAndSegmentsBetween() {
        List<WallLine.Piece> pieces = WallLine.pieces(0, 0, 10, 0, NOTHING);

        assertEquals(3, pieces.size());
        assertTrue(pieces.get(0).post);
        assertFalse(pieces.get(1).post);
        assertTrue(pieces.get(2).post);
        assertEquals(0, pieces.get(1).rotation);
        assertEquals(WallLine.SIZE, pieces.get(1).sizeX);
    }

    @Test
    void wallAlongZIsRotatedSegment() {
        List<WallLine.Piece> pieces = WallLine.pieces(0, 0, 0, 10, NOTHING);

        assertEquals(3, pieces.size());
        assertFalse(pieces.get(1).post);
        assertEquals(1, pieces.get(1).rotation);
    }

    @Test
    void postsAreWallPostsAndSegmentsAreWalls() {
        WallLine.Piece post = new WallLine.Piece(0, 0, true, 0);
        WallLine.Piece segment = new WallLine.Piece(0, 0, false, 0);

        assertEquals(BuildingType.WALL_POST, post.type());
        assertEquals(BuildingType.WALL, segment.type());
    }

    @Test
    void cornerIsAPost() {
        // Two sides of a square meeting at (0, 0): the corner piece must be a post.
        List<WallLine.Piece> pieces = WallLine.pieces(0, 0, 10, 10, NOTHING);

        assertTrue(
            pieces.stream()
                .anyMatch(p -> p.post));
        assertTrue(pieces.size() > 2);
    }

    @Test
    void slantingWallStaysConnected() {
        List<WallLine.Piece> pieces = WallLine.pieces(0, 0, 20, 10, NOTHING);

        // Every piece after the first starts where the previous one ended, give or take the piece's own width.
        for (int i = 1; i < pieces.size(); i++) {
            WallLine.Piece before = pieces.get(i - 1);
            WallLine.Piece after = pieces.get(i);
            int gapX = Math.abs(after.minX - (before.minX + before.sizeX));
            int gapZ = Math.abs(after.minZ - (before.minZ + before.sizeZ));
            assertTrue(gapX <= WallLine.SIZE && gapZ <= WallLine.SIZE, "gap before piece " + i);
        }
    }

    @Test
    void pieceWhereSomethingStandsIsLeftOut() {
        // A tower already stands on the middle piece, so only the two ends are built, and they become posts.
        WallLine.Blocker towerInTheMiddle = (minX, minZ) -> minX == 3 ? new int[] { 3, -2, 5, 5 } : null;

        List<WallLine.Piece> pieces = WallLine.pieces(0, 0, 10, 0, towerInTheMiddle);

        assertEquals(2, pieces.size());
        assertTrue(pieces.get(0).post);
        assertTrue(pieces.get(1).post);
        assertTrue(
            pieces.stream()
                .noneMatch(p -> p.minX == 3));
    }

    @Test
    void shortGapBesideAnObstacleIsClosedWithShortSegments() {
        // The middle piece is a tower set one block further along, so a one-block segment has to close each side.
        WallLine.Blocker offsetTower = (minX, minZ) -> minX == 3 ? new int[] { 4, -2, 3, 5 } : null;

        List<WallLine.Piece> pieces = WallLine.pieces(0, 0, 10, 0, offsetTower);

        assertEquals(4, pieces.size());
        assertEquals(3, pieces.get(1).minX);
        assertEquals(1, pieces.get(1).sizeX);
        assertTrue(
            pieces.get(1)
                .isShort());
        assertEquals(7, pieces.get(2).minX);
        assertEquals(1, pieces.get(2).sizeX);
    }

    @Test
    void lengthIsCappedAtMaxPieces() {
        assertEquals(
            WallLine.MAX_PIECES,
            WallLine.pieces(0, 0, 10000, 0, NOTHING)
                .size());
    }
}
