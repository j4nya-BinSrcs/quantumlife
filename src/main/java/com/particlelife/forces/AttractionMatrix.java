package com.particlelife.forces;

import com.particlelife.math.DeterministicRandom;
import com.particlelife.math.MathUtils;

import java.util.Arrays;

/**
 * The {@code N × N} species interaction matrix at the heart of Particle Life.
 *
 * <p>{@code get(i, j)} is the strength with which species {@code i} is
 * attracted to ({@code > 0}) or repelled by ({@code < 0}) species {@code j}.
 * The matrix may be asymmetric — {@code a[i][j] != a[j][i]} is what produces
 * chase/flee dynamics. An optional <em>symmetric mode</em> mirrors every edit
 * so {@code a[j][i]} always equals {@code a[i][j]}.
 *
 * <p>Values are clamped to {@code [-MAX_VALUE, MAX_VALUE]}. Storage is a flat
 * row-major array for cache-friendly access from the physics hot loop.
 *
 * <p>Not internally synchronized; the engine treats it as read-only during a
 * physics step and applies edits between steps.
 */
public final class AttractionMatrix {

    /** Attraction values live in {@code [-1, 1]}. */
    public static final double MAX_VALUE = 1.0;

    private int size;
    private double[] values;
    private boolean symmetric;

    /** Creates a zero matrix for {@code size} species. */
    public AttractionMatrix(int size) {
        requirePositive(size);
        this.size = size;
        this.values = new double[size * size];
    }

    /** Number of species (rows/columns). */
    public int size() {
        return size;
    }

    /** Attraction of species {@code i} toward species {@code j}. */
    public double get(int i, int j) {
        return values[i * size + j];
    }

    /**
     * Sets the attraction of species {@code i} toward {@code j}, clamped to
     * {@code [-1, 1]}. In symmetric mode the mirror entry is updated too.
     */
    public void set(int i, int j, double value) {
        double v = MathUtils.clamp(value, -MAX_VALUE, MAX_VALUE);
        values[i * size + j] = v;
        if (symmetric && i != j) {
            values[j * size + i] = v;
        }
    }

    /** Whether edits are mirrored across the diagonal. */
    public boolean isSymmetric() {
        return symmetric;
    }

    /**
     * Enables or disables symmetric mode. Enabling immediately symmetrizes
     * the existing matrix by averaging mirrored pairs.
     */
    public void setSymmetric(boolean symmetric) {
        this.symmetric = symmetric;
        if (symmetric) {
            for (int i = 0; i < size; i++) {
                for (int j = i + 1; j < size; j++) {
                    double avg = (get(i, j) + get(j, i)) * 0.5;
                    values[i * size + j] = avg;
                    values[j * size + i] = avg;
                }
            }
        }
    }

    /** Fills the matrix with uniform random values in {@code [-1, 1]}. */
    public void randomize(DeterministicRandom rng) {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (symmetric && j < i) {
                    values[i * size + j] = values[j * size + i];
                } else {
                    values[i * size + j] = rng.nextDouble(-MAX_VALUE, MAX_VALUE);
                }
            }
        }
    }

    /** Sets every entry to zero. */
    public void reset() {
        Arrays.fill(values, 0.0);
    }

    /**
     * Resizes to {@code newSize} species, preserving the overlapping
     * upper-left block; new entries are zero.
     */
    public void resize(int newSize) {
        requirePositive(newSize);
        if (newSize == size) {
            return;
        }
        double[] next = new double[newSize * newSize];
        int overlap = Math.min(size, newSize);
        for (int i = 0; i < overlap; i++) {
            System.arraycopy(values, i * size, next, i * newSize, overlap);
        }
        this.size = newSize;
        this.values = next;
    }

    /** Returns the matrix as a fresh 2D array (row = source species). */
    public double[][] toArray() {
        double[][] out = new double[size][size];
        for (int i = 0; i < size; i++) {
            System.arraycopy(values, i * size, out[i], 0, size);
        }
        return out;
    }

    /** Replaces the contents from a square 2D array. */
    public void setFrom(double[][] source) {
        int n = source.length;
        requirePositive(n);
        double[] next = new double[n * n];
        for (int i = 0; i < n; i++) {
            if (source[i].length != n) {
                throw new IllegalArgumentException("matrix must be square");
            }
            for (int j = 0; j < n; j++) {
                next[i * n + j] = MathUtils.clamp(source[i][j], -MAX_VALUE, MAX_VALUE);
            }
        }
        this.size = n;
        this.values = next;
        if (symmetric) {
            setSymmetric(true);
        }
    }

    /** Returns a deep copy (same size, values, and symmetric flag). */
    public AttractionMatrix copy() {
        AttractionMatrix c = new AttractionMatrix(size);
        c.values = values.clone();
        c.symmetric = symmetric;
        return c;
    }

    private static void requirePositive(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("matrix size must be >= 1: " + n);
        }
    }
}
