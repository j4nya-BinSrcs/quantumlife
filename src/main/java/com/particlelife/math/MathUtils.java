package com.particlelife.math;

/**
 * Small static math helpers shared across the engine.
 */
public final class MathUtils {

    private MathUtils() {
    }

    /** Clamps {@code value} into {@code [min, max]}. */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Clamps {@code value} into {@code [min, max]}. */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Linear interpolation between {@code a} and {@code b} by {@code t}. */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Wraps {@code value} into the half-open interval {@code [0, size)}.
     * Correct for negative inputs (unlike the {@code %} operator).
     */
    public static double wrap(double value, double size) {
        double r = value % size;
        return r < 0 ? r + size : r;
    }

    /**
     * Wraps an axis delta into {@code [-size/2, size/2)} — the
     * <em>minimum-image convention</em> used for distance calculations in a
     * toroidal (wrap-around) world: the force between two particles acts
     * along the shortest of the direct path and the path through the seam.
     */
    public static double minimumImage(double delta, double size) {
        double half = size * 0.5;
        if (delta > half) {
            return delta - size;
        }
        if (delta < -half) {
            return delta + size;
        }
        return delta;
    }

    /** Returns whether {@code a} and {@code b} differ by at most {@code eps}. */
    public static boolean approxEquals(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }
}
