package com.warofthegods.ai;

/**
 * The computer's sense of the map's territory, kept apart from the game so it can be tested: which of the spots the
 * teams contest, gold mines and free settlements, it claims next, where the front between it and its enemies runs,
 * and which of its attacks go at an enemy's main base rather than at what the enemy holds round the map. Spots and
 * bases are given as { x, z }.
 */
final class Territory {

    /** The first attack that goes at an enemy's main base, and after it every so many. */
    static final int ASSAULT_WAVE = 3;
    static final int ASSAULT_EVERY = 2;
    /** How much nearer its own town centers than an enemy's base a spot must be, as a share, for it to claim it. */
    static final double CONTEST = 1.25;

    private Territory() {}

    /**
     * The spot it claims next: the nearest to its own town centers of those no further from them than CONTEST times
     * the distance to the nearest enemy base, so it takes its side of the map and the middle before reaching into the
     * enemy's; or -1 for none.
     */
    static int claimNext(double[][] spots, double[][] own, double[][] enemies) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < spots.length; i++) {
            double mine = nearest(spots[i], own);
            double theirs = nearest(spots[i], enemies);
            if (mine <= theirs * CONTEST && mine < bestDistance) {
                best = i;
                bestDistance = mine;
            }
        }
        return best;
    }

    /**
     * The spot on the front: the one about as far from its own town centers as from the nearest enemy base, nearer
     * ones first, where its armies go to hold the ground; or -1 when there are no spots or no enemies.
     */
    static int front(double[][] spots, double[][] own, double[][] enemies) {
        if (enemies.length == 0 || own.length == 0) {
            return -1;
        }
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < spots.length; i++) {
            double mine = nearest(spots[i], own);
            double theirs = nearest(spots[i], enemies);
            double score = Math.abs(mine - theirs) + mine / 2;
            if (score < bestScore) {
                best = i;
                bestScore = score;
            }
        }
        return best;
    }

    /** Whether the attack with this number, counting from 0, goes at an enemy's main base. */
    static boolean isAssault(int wave) {
        return wave >= ASSAULT_WAVE && (wave - ASSAULT_WAVE) % ASSAULT_EVERY == 0;
    }

    /** Blocks from the spot to the nearest of the points; very far if there are none. */
    static double nearest(double[] spot, double[][] points) {
        double best = Double.MAX_VALUE;
        for (double[] point : points) {
            double dx = point[0] - spot[0];
            double dz = point[1] - spot[1];
            best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
        }
        return best;
    }
}
