package com.warofthegods.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class BridgeLineTest {

    @Test
    void bridgeAlongXRunsOnTheRowOfTheFirstPoint() {
        List<BridgeLine.Piece> pieces = BridgeLine.pieces(0, 0, 4, 0);

        assertEquals(5, pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            BridgeLine.Piece piece = pieces.get(i);
            assertEquals(i, piece.minX);
            assertEquals(-BridgeLine.WIDTH / 2, piece.minZ);
            assertEquals(0, piece.rotation);
            assertEquals(1, piece.sizeX);
            assertEquals(BridgeLine.WIDTH, piece.sizeZ);
        }
    }

    @Test
    void bridgeAlongZIsRotated() {
        List<BridgeLine.Piece> pieces = BridgeLine.pieces(0, 0, 0, 3);

        assertEquals(4, pieces.size());
        assertEquals(1, pieces.get(0).rotation);
        assertEquals(BridgeLine.WIDTH, pieces.get(0).sizeX);
        assertEquals(1, pieces.get(0).sizeZ);
        assertEquals(-BridgeLine.WIDTH / 2, pieces.get(3).minX);
        assertEquals(3, pieces.get(3).minZ);
    }

    @Test
    void piecesAreInOrderFromTheFirstPoint() {
        List<BridgeLine.Piece> pieces = BridgeLine.pieces(4, 0, 0, 0);

        assertEquals(4, pieces.get(0).minX);
        assertEquals(0, pieces.get(4).minX);
    }

    @Test
    void diagonalBridgeFollowsTheLongerAxis() {
        // 2 along x, 6 along z: the bridge runs along z.
        List<BridgeLine.Piece> pieces = BridgeLine.pieces(0, 0, 2, 6);

        assertEquals(7, pieces.size());
        assertEquals(1, pieces.get(0).rotation);
    }

    @Test
    void singlePointGivesOnePiece() {
        assertEquals(
            1,
            BridgeLine.pieces(3, 3, 3, 3)
                .size());
    }

    @Test
    void lengthIsCappedAtMaxPieces() {
        assertEquals(
            BridgeLine.MAX_PIECES,
            BridgeLine.pieces(0, 0, 1000, 0)
                .size());
    }

    @Test
    void pieceCentersAreMidwayAcrossTheirFootprint() {
        BridgeLine.Piece along = BridgeLine.pieces(0, 0, 4, 0)
            .get(0);
        assertEquals(0.5, along.centerX());
        // Five blocks across, from z -2 up to but not including 3, so the middle is z 0.5.
        assertEquals(0.5, along.centerZ());
    }
}
