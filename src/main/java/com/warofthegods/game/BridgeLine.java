package com.warofthegods.game;

import java.util.ArrayList;
import java.util.List;

/**
 * How a bridge runs from one point to another: straight along x or z, whichever the two points lie further apart in,
 * on the row of the first point, as pieces a block long and {@link #WIDTH} wide. The server and the RTS view work the
 * line out the same way, so the see-through bridge that follows the pointer is the bridge that gets built.
 */
public final class BridgeLine {

    /** Blocks across a bridge. */
    public static final int WIDTH = 5;
    /** Most pieces one bridge can have. */
    public static final int MAX_PIECES = 48;

    /** One piece: its footprint, and 0 for a bridge along x, 1 along z. */
    public static final class Piece {

        public final int minX;
        public final int minZ;
        public final int sizeX;
        public final int sizeZ;
        public final int rotation;

        Piece(int minX, int minZ, int rotation) {
            this.minX = minX;
            this.minZ = minZ;
            this.rotation = rotation;
            this.sizeX = rotation == 0 ? 1 : WIDTH;
            this.sizeZ = rotation == 0 ? WIDTH : 1;
        }

        public double centerX() {
            return minX + sizeX / 2.0;
        }

        public double centerZ() {
            return minZ + sizeZ / 2.0;
        }
    }

    private BridgeLine() {}

    /** The pieces of a bridge from x1, z1 to x2, z2, in order along it from the first point. */
    public static List<Piece> pieces(int x1, int z1, int x2, int z2) {
        List<Piece> pieces = new ArrayList<>();
        boolean alongX = Math.abs(x2 - x1) >= Math.abs(z2 - z1);
        int from = alongX ? x1 : z1;
        int to = alongX ? x2 : z2;
        int step = to >= from ? 1 : -1;
        for (int at = from; pieces.size() < MAX_PIECES; at += step) {
            pieces.add(alongX ? new Piece(at, z1 - WIDTH / 2, 0) : new Piece(x1 - WIDTH / 2, at, 1));
            if (at == to) {
                break;
            }
        }
        return pieces;
    }
}
