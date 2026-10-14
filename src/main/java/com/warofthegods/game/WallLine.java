package com.warofthegods.game;

import java.util.ArrayList;
import java.util.List;

/**
 * How a wall runs from one point to another: a line of 5 by 5 pieces on a grid that starts at the first point. It
 * goes straight where it can and steps one piece across, then one along, where it runs on a slant, so a slanting wall
 * is a zigzag of 5 block steps. A tower is 5 by 5 too, so a wall that starts on one lines up with it; at the other
 * end the last piece is moved up against the tower it runs to. Where it turns or ends there is a post; everywhere
 * else a segment along the line. The server and both maps work the line out the same way, so the see-through wall
 * that follows the pointer is the wall that gets built.
 */
public final class WallLine {

    /** Blocks across a piece. */
    public static final int SIZE = 5;
    /** Most pieces one wall can have. */
    public static final int MAX_PIECES = 64;

    /** One piece of a wall: its corner, whether it is a post, and for a segment which way it runs. */
    public static final class Piece {

        public final int minX;
        public final int minZ;
        public final boolean post;
        /** 0 for a segment along x, 1 along z; 0 for a post. */
        public final int rotation;

        public Piece(int minX, int minZ, boolean post, int rotation) {
            this.minX = minX;
            this.minZ = minZ;
            this.post = post;
            this.rotation = rotation;
        }

        public BuildingType type() {
            return post ? BuildingType.WALL_POST : BuildingType.WALL;
        }

        public double centerX() {
            return minX + SIZE / 2.0;
        }

        public double centerZ() {
            return minZ + SIZE / 2.0;
        }
    }

    /** Tells which pieces to leave out, such as those on a tower the wall runs up to. */
    public interface Skip {

        boolean skip(int minX, int minZ);
    }

    private WallLine() {}

    /**
     * The pieces of a wall from x1, z1 to x2, z2, leaving out those skip says to. A piece next to one left out is a
     * post, as the wall ends there. With an end box (min x, min z, size x, size z of the tower the wall runs to), the
     * last piece before the box is moved up against it, so no gap is left.
     */
    public static List<Piece> pieces(int x1, int z1, int x2, int z2, Skip skip, int[] endBox) {
        int originX = x1 - SIZE / 2;
        int originZ = z1 - SIZE / 2;
        int endI = Math.floorDiv(x2 - originX, SIZE);
        int endJ = Math.floorDiv(z2 - originZ, SIZE);
        List<int[]> cells = new ArrayList<>();
        int i = 0;
        int j = 0;
        cells.add(new int[] { 0, 0 });
        while ((i != endI || j != endJ) && cells.size() < MAX_PIECES) {
            int stepI = Integer.signum(endI - i);
            int stepJ = Integer.signum(endJ - j);
            if (stepI != 0 && stepJ != 0) {
                // Whichever step keeps closer to the straight line from start to end; along x on a tie.
                long alongI = Math.abs((long) endJ * (i + stepI) - (long) endI * j);
                long alongJ = Math.abs((long) endJ * i - (long) endI * (j + stepJ));
                if (alongJ < alongI) {
                    stepI = 0;
                } else {
                    stepJ = 0;
                }
            }
            i += stepI;
            j += stepJ;
            cells.add(new int[] { i, j });
        }
        boolean[] kept = new boolean[cells.size()];
        for (int k = 0; k < cells.size(); k++) {
            int[] cell = cells.get(k);
            kept[k] = skip == null || !skip.skip(originX + cell[0] * SIZE, originZ + cell[1] * SIZE);
        }
        List<Piece> pieces = new ArrayList<>();
        for (int k = 0; k < cells.size(); k++) {
            if (!kept[k]) {
                continue;
            }
            int[] cell = cells.get(k);
            boolean hasBefore = k > 0 && kept[k - 1];
            boolean hasAfter = k < cells.size() - 1 && kept[k + 1];
            int[] in = hasBefore ? step(cells.get(k - 1), cell) : null;
            int[] out = hasAfter ? step(cell, cells.get(k + 1)) : null;
            boolean post = in == null || out == null || in[0] != out[0] || in[1] != out[1];
            int rotation = post ? 0 : in[0] != 0 ? 0 : 1;
            pieces.add(new Piece(originX + cell[0] * SIZE, originZ + cell[1] * SIZE, post, rotation));
        }
        int last = cells.size() - 1;
        while (last >= 0 && !kept[last]) {
            last--;
        }
        if (endBox != null && last >= 0 && last < cells.size() - 1 && !pieces.isEmpty()) {
            int[] toward = step(cells.get(last), cells.get(last + 1));
            Piece piece = pieces.get(pieces.size() - 1);
            int gap = toward[0] > 0 ? endBox[0] - (piece.minX + SIZE)
                : toward[0] < 0 ? piece.minX - (endBox[0] + endBox[2])
                    : toward[1] > 0 ? endBox[1] - (piece.minZ + SIZE) : piece.minZ - (endBox[1] + endBox[3]);
            if (gap > 0 && gap < SIZE) {
                pieces.set(
                    pieces.size() - 1,
                    new Piece(piece.minX + toward[0] * gap, piece.minZ + toward[1] * gap, piece.post, piece.rotation));
            }
        }
        return pieces;
    }

    private static int[] step(int[] from, int[] to) {
        return new int[] { to[0] - from[0], to[1] - from[1] };
    }
}
