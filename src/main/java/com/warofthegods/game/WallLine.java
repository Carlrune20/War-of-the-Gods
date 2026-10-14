package com.warofthegods.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How a wall runs from one point to another: a line of 5 by 5 pieces on a grid that starts at the first point. It
 * goes straight where it can and steps one piece across, then one along, where it runs on a slant, so a slanting wall
 * is a zigzag of 5 block steps. A tower is 5 by 5 too, so a wall that starts on one lines up with it. Where the grid
 * leaves a gap of less than a piece against a tower or wall it runs up to, a shorter segment, down to a block long,
 * closes it. Where it turns or ends there is a post; everywhere else a segment along the line. The server and both
 * maps work the line out the same way, so the see-through wall that follows the pointer is the wall that gets built.
 */
public final class WallLine {

    /** Blocks across a piece, and along a full one. */
    public static final int SIZE = 5;
    /** Most pieces one wall can have. */
    public static final int MAX_PIECES = 64;

    /** One piece of a wall: its footprint, whether it is a post, and for a segment which way it runs. */
    public static final class Piece {

        public final int minX;
        public final int minZ;
        public final int sizeX;
        public final int sizeZ;
        public final boolean post;
        /** 0 for a segment along x, 1 along z; 0 for a post. */
        public final int rotation;

        public Piece(int minX, int minZ, boolean post, int rotation) {
            this(minX, minZ, SIZE, SIZE, post, rotation);
        }

        public Piece(int minX, int minZ, int sizeX, int sizeZ, boolean post, int rotation) {
            this.minX = minX;
            this.minZ = minZ;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.post = post;
            this.rotation = rotation;
        }

        public BuildingType type() {
            return post ? BuildingType.WALL_POST : BuildingType.WALL;
        }

        /** Whether this is a segment shorter than a full piece, closing a gap. */
        public boolean isShort() {
            return sizeX < SIZE || sizeZ < SIZE;
        }

        public double centerX() {
            return minX + sizeX / 2.0;
        }

        public double centerZ() {
            return minZ + sizeZ / 2.0;
        }
    }

    /**
     * What already stands where a piece would go, such as a tower the wall runs up to: its box as min x, min z, size
     * x, size z, or null to build the piece.
     */
    public interface Blocker {

        int[] at(int minX, int minZ);
    }

    private WallLine() {}

    /**
     * The pieces of a wall from x1, z1 to x2, z2, in order along it, leaving out those where blocker finds something.
     * A piece next to one left out is a post, as the wall ends there. Where a gap is left between what stands in the
     * way and the next piece, or between two things in the way, short segments close it.
     */
    public static List<Piece> pieces(int x1, int z1, int x2, int z2, Blocker blocker) {
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
        // The box each cell takes up: the piece's own, or what stands there.
        int[][] boxes = new int[cells.size()][];
        boolean[] kept = new boolean[cells.size()];
        for (int k = 0; k < cells.size(); k++) {
            int[] cell = cells.get(k);
            int minX = originX + cell[0] * SIZE;
            int minZ = originZ + cell[1] * SIZE;
            int[] there = blocker == null ? null : blocker.at(minX, minZ);
            kept[k] = there == null;
            boxes[k] = kept[k] ? new int[] { minX, minZ, SIZE, SIZE } : there;
        }
        List<Piece> pieces = new ArrayList<>();
        for (int k = 0; k < cells.size(); k++) {
            int[] cell = cells.get(k);
            if (kept[k]) {
                boolean hasBefore = k > 0 && kept[k - 1];
                boolean hasAfter = k < cells.size() - 1 && kept[k + 1];
                int[] in = hasBefore ? step(cells.get(k - 1), cell) : null;
                int[] out = hasAfter ? step(cell, cells.get(k + 1)) : null;
                boolean post = in == null || out == null || in[0] != out[0] || in[1] != out[1];
                int rotation = post ? 0 : in[0] != 0 ? 0 : 1;
                pieces.add(new Piece(boxes[k][0], boxes[k][1], post, rotation));
            }
            if (k < cells.size() - 1 && !(kept[k] && kept[k + 1])) {
                fill(
                    pieces,
                    step(cell, cells.get(k + 1)),
                    boxes[k],
                    boxes[k + 1],
                    originX + cell[0] * SIZE,
                    originZ + cell[1] * SIZE);
            }
        }
        return pieces;
    }

    /**
     * Closes the gap, if any, going the way of step from box a to box b with segments of up to a full piece. They
     * run on the line's own row of cells, whose corner is cellX, cellZ.
     */
    private static void fill(List<Piece> pieces, int[] step, int[] a, int[] b, int cellX, int cellZ) {
        boolean alongX = step[0] != 0;
        int from;
        int to;
        if (alongX) {
            from = step[0] > 0 ? a[0] + a[2] : b[0] + b[2];
            to = step[0] > 0 ? b[0] : a[0];
        } else {
            from = step[1] > 0 ? a[1] + a[3] : b[1] + b[3];
            to = step[1] > 0 ? b[1] : a[1];
        }
        int gap = to - from;
        if (gap <= 0 || gap > 2 * SIZE) {
            return;
        }
        List<Piece> run = new ArrayList<>();
        for (int at = from; at < to; at += SIZE) {
            int length = Math.min(SIZE, to - at);
            run.add(
                alongX ? new Piece(at, cellZ, length, SIZE, false, 0) : new Piece(cellX, at, SIZE, length, false, 1));
        }
        // In order along the wall.
        if (step[0] < 0 || step[1] < 0) {
            Collections.reverse(run);
        }
        pieces.addAll(run);
    }

    private static int[] step(int[] from, int[] to) {
        return new int[] { to[0] - from[0], to[1] - from[1] };
    }
}
