package com.warofthegods.world;

/** Seeded 2D value noise with smooth interpolation, summed over octaves. Values are roughly in -1..1. */
final class TerrainNoise {

    private final long seed;

    TerrainNoise(long seed) {
        this.seed = seed;
    }

    /** Fractal noise: octaves of value noise, each at double the frequency and half the amplitude. */
    double fbm(double x, double z, int octaves) {
        double total = 0;
        double amplitude = 1;
        double max = 0;
        for (int i = 0; i < octaves; i++) {
            total += noise(x, z, i) * amplitude;
            max += amplitude;
            amplitude *= 0.5;
            x *= 2;
            z *= 2;
        }
        return total / max;
    }

    private double noise(double x, double z, int octave) {
        int x0 = floor(x);
        int z0 = floor(z);
        double fx = fade(x - x0);
        double fz = fade(z - z0);
        double top = lerp(fx, corner(x0, z0, octave), corner(x0 + 1, z0, octave));
        double bottom = lerp(fx, corner(x0, z0 + 1, octave), corner(x0 + 1, z0 + 1, octave));
        return lerp(fz, top, bottom);
    }

    /** A pseudo random value in -1..1 for one lattice point. */
    private double corner(int x, int z, int octave) {
        long h = seed + octave * 0x9E3779B97F4A7C15L;
        h ^= x * 0x632BE59BD9B4E019L;
        h ^= z * 0x8CB92BA72F3D8DD7L;
        h = (h ^ (h >>> 32)) * 0xD6E8FEB86659FD93L;
        h = (h ^ (h >>> 32)) * 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        return (h & 0xFFFFFF) / (double) 0x7FFFFF - 1;
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
}
