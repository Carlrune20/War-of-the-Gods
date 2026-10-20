package com.warofthegods.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
    void bentWallRunsOutAcrossAndBackWithoutGapsOrOverlaps() {
        List<WallLine.Piece> pieces = WallLine.bent(0, 0, 30, 0, 0, 12, NOTHING);

        // Out two pieces, across six, back two: corners shared, nothing put in twice.
        assertEquals(1 + 2 + 6 + 2, pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            for (int j = i + 1; j < pieces.size(); j++) {
                assertFalse(overlap(pieces.get(i), pieces.get(j)), "pieces " + i + " and " + j + " overlap");
            }
        }
        for (int i = 1; i < pieces.size(); i++) {
            assertTrue(touch(pieces.get(i - 1), pieces.get(i)), "gap before piece " + i);
        }
        assertEquals(-2, pieces.get(0).minX);
        assertEquals(-2, pieces.get(0).minZ);
        WallLine.Piece last = pieces.get(pieces.size() - 1);
        assertEquals(28, last.minX);
        assertEquals(-2, last.minZ);
    }

    @Test
    void bentWallBetweenPointsOffTheGridStillEndsAtTheSecond() {
        // The far end is off the first point's grid; the last run comes back in to it.
        List<WallLine.Piece> pieces = WallLine.bent(0, 0, 23, 17, 9, -11, NOTHING);

        // Where the way back in doubles back over the run across, it is still one wall: every piece joined to the
        // first.
        List<WallLine.Piece> joined = new ArrayList<>();
        joined.add(pieces.get(0));
        for (int added = 1; added > 0;) {
            added = 0;
            for (WallLine.Piece piece : pieces) {
                if (!joined.contains(piece) && joined.stream()
                    .anyMatch(other -> touch(other, piece))) {
                    joined.add(piece);
                    added++;
                }
            }
        }
        assertEquals(pieces.size(), joined.size());
        assertTrue(
            pieces.stream()
                .anyMatch(p -> p.minX <= 23 && 23 < p.minX + p.sizeX && p.minZ <= 17 && 17 < p.minZ + p.sizeZ));
    }

    @Test
    void bentWallWithNoWayOutIsStraight() {
        assertEquals(
            WallLine.pieces(0, 0, 20, 0, NOTHING)
                .size(),
            WallLine.bent(0, 0, 20, 0, 1, 2, NOTHING)
                .size());
    }

    private static boolean overlap(WallLine.Piece a, WallLine.Piece b) {
        return a.minX < b.minX + b.sizeX && b.minX < a.minX + a.sizeX
            && a.minZ < b.minZ + b.sizeZ
            && b.minZ < a.minZ + a.sizeZ;
    }

    /** Whether two pieces share a side, so no unit gets between them. */
    private static boolean touch(WallLine.Piece a, WallLine.Piece b) {
        boolean alongX = (a.minX + a.sizeX == b.minX || b.minX + b.sizeX == a.minX) && a.minZ < b.minZ + b.sizeZ
            && b.minZ < a.minZ + a.sizeZ;
        boolean alongZ = (a.minZ + a.sizeZ == b.minZ || b.minZ + b.sizeZ == a.minZ) && a.minX < b.minX + b.sizeX
            && b.minX < a.minX + a.sizeX;
        return alongX || alongZ;
    }

    @Test
    void lengthIsCappedAtMaxPieces() {
        assertEquals(
            WallLine.MAX_PIECES,
            WallLine.pieces(0, 0, 10000, 0, NOTHING)
                .size());
    }
}
