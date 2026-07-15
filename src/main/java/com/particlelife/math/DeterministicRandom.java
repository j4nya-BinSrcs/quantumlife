package com.particlelife.math;

/**
 * A fast, seedable pseudo-random number generator based on SplitMix64.
 *
 * <p>Used instead of {@link java.util.Random} for two reasons:
 * <ul>
 *   <li><strong>Reproducibility</strong> — the algorithm is fixed and
 *       documented, so a simulation seed reproduces the exact same initial
 *       state on every platform and Java version.</li>
 *   <li><strong>Speed</strong> — SplitMix64 is a handful of arithmetic
 *       instructions with excellent statistical quality for simulation
 *       purposes.</li>
 * </ul>
 */
public final class DeterministicRandom {

    private long state;

    /** Creates a generator with the given seed. */
    public DeterministicRandom(long seed) {
        this.state = seed;
    }

    /** Returns the next raw 64-bit value. */
    public long nextLong() {
        long z = state += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Returns a uniform double in {@code [0, 1)}. */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** Returns a uniform double in {@code [min, max)}. */
    public double nextDouble(double min, double max) {
        return min + nextDouble() * (max - min);
    }

    /** Returns a uniform int in {@code [0, bound)}; {@code bound} must be positive. */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        return (int) Math.floorMod(nextLong(), bound);
    }

    /**
     * Writes a uniformly distributed point inside a sphere of the given
     * radius (centered at the origin) into {@code out}, using rejection
     * sampling for a distribution uniform in volume.
     */
    public Vector3 nextInSphere(double radius, Vector3 out) {
        double x;
        double y;
        double z;
        do {
            x = nextDouble(-1.0, 1.0);
            y = nextDouble(-1.0, 1.0);
            z = nextDouble(-1.0, 1.0);
        } while (x * x + y * y + z * z > 1.0);
        return out.set(x * radius, y * radius, z * radius);
    }

    /**
     * Returns an independent generator seeded from this one — used to give
     * each subsystem (spawning, matrix randomization, colors) its own stream
     * so that, e.g., re-randomizing the matrix does not perturb particle
     * spawn positions.
     */
    public DeterministicRandom split() {
        return new DeterministicRandom(nextLong());
    }
}
