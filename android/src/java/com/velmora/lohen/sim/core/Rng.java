/*
 * LOHEN — sim/core/Rng.java
 *
 * Generateur deterministe a graine (xorshift128+ simplifie).
 * 18.06 : les tests JVM rejouent une partie entiere a la graine pres.
 * 04.12 : aucune allocation, aucun Math.random() dans les boucles chaudes.
 */
package com.velmora.lohen.sim.core;

public final class Rng {

    private long s0;
    private long s1;
    private long calls;

    public Rng(long seed) {
        setSeed(seed);
    }

    public void setSeed(long seed) {
        s0 = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
        s1 = mix(s0 ^ 0x6A09E667F3BCC908L);
        if (s1 == 0) {
            s1 = 0x2545F4914F6CDD1DL;
        }
        calls = 0;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public long nextLong() {
        long x = s0;
        long y = s1;
        s0 = y;
        x ^= x << 23;
        s1 = x ^ y ^ (x >>> 17) ^ (y >>> 26);
        calls++;
        return s1 + y;
    }

    public int nextInt() {
        return (int) (nextLong() >>> 32);
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            return 0;
        }
        int r = nextInt() % bound;
        return r < 0 ? r + bound : r;
    }

    public int range(int minInclusive, int maxExclusive) {
        return minInclusive + nextInt(maxExclusive - minInclusive);
    }

    public float nextFloat() {
        return (float) ((nextLong() >>> 40) / (double) (1L << 24));
    }

    public float range(float min, float max) {
        return min + nextFloat() * (max - min);
    }

    public boolean chance(float probability) {
        return nextFloat() < probability;
    }

    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public long calls() {
        return calls;
    }

    /** Etat serialisable (sauvegarde deterministe). */
    public long[] state() {
        return new long[]{s0, s1, calls};
    }

    public void restore(long[] state) {
        if (state != null && state.length >= 3) {
            s0 = state[0];
            s1 = state[1];
            calls = state[2];
        }
    }

    /** Graine derivee d'un identifiant de sequence (S1..S8). */
    public static long seedFor(String sequence, int checkpoint) {
        long h = 1469598103934665603L;
        String s = sequence == null ? "" : sequence;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 1099511628211L;
        }
        h ^= checkpoint * 2654435761L;
        return h == 0 ? 1L : h;
    }
}
