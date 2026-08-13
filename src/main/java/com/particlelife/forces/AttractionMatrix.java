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
 * physics step and applies edits between steps. The storage is published as a
 * single volatile immutable {@link View}, so a reader (UI paint, snapshot)
 * can never observe a torn {values, size} pairing when the engine swaps the
 * backing array on resize — it sees either the old or the new array with its
 * matching size. A stale-but-consistent snapshot is fine for painting and
 * self-heals next frame.
 */
public final class AttractionMatrix {

    /** Attraction values live in {@code [-1, 1]}. */
    public static final double MAX_VALUE = 1.0;

    /** Immutable published storage; {@code values} is row-major {@code size × size}. */
    private static final class View {
        final double[] values;
        final int size;

        View(double[] values, int size) {
            this.values = values;
            this.size = size;
        }
    }

    private volatile View view;
    private volatile boolean symmetric;

    /** Creates a zero matrix for {@code size} species. */
    public AttractionMatrix(int size) {
        requirePositive(size);
        this.view = new View(new double[size * size], size);
    }

    /** Number of species (rows/columns). */
    public int size() {
        return view.size;
    }

    /**
     * The current row-major backing values ({@code size × size}). Read-only:
     * callers must not modify the returned array — the engine swaps the whole
     * view on resize, so holding this array beyond the current step may be
     * stale but is always internally consistent.
     */
    public double[] values() {
        return view.values;
    }

    /** Attraction of species {@code i} toward species {@code j}. */
    public double get(int i, int j) {
        View v = view;
        return v.values[i * v.size + j];
    }

    /**
     * Sets the attraction of species {@code i} toward {@code j}, clamped to
     * {@code [-1, 1]}. In symmetric mode the mirror entry is updated too.
     */
    public void set(int i, int j, double value) {
        View v = view;
        double clamped = MathUtils.clamp(value, -MAX_VALUE, MAX_VALUE);
        v.values[i * v.size + j] = clamped;
        if (symmetric && i != j) {
            v.values[j * v.size + i] = clamped;
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
        View v = view;
        if (symmetric) {
            for (int i = 0; i < v.size; i++) {
                for (int j = i + 1; j < v.size; j++) {
                    double avg = (get(i, j) + get(j, i)) * 0.5;
                    v.values[i * v.size + j] = avg;
                    v.values[j * v.size + i] = avg;
                }
            }
        }
    }

    /** Fills the matrix with uniform random values in {@code [-1, 1]}. */
    public void randomize(DeterministicRandom rng) {
        View v = view;
        for (int i = 0; i < v.size; i++) {
            for (int j = 0; j < v.size; j++) {
                if (symmetric && j < i) {
                    v.values[i * v.size + j] = v.values[j * v.size + i];
                } else {
                    v.values[i * v.size + j] = rng.nextDouble(-MAX_VALUE, MAX_VALUE);
                }
            }
        }
    }

    /** Sets every entry to zero. */
    public void reset() {
        View v = view;
        Arrays.fill(v.values, 0.0);
    }

    /**
     * Resizes to {@code newSize} species, preserving the overlapping
     * upper-left block; new entries are zero.
     */
    public void resize(int newSize) {
        requirePositive(newSize);
        View v = view;
        if (newSize == v.size) {
            return;
        }
        double[] next = new double[newSize * newSize];
        int overlap = Math.min(v.size, newSize);
        for (int i = 0; i < overlap; i++) {
            System.arraycopy(v.values, i * v.size, next, i * newSize, overlap);
        }
        this.view = new View(next, newSize);
    }

    /** Returns the matrix as a fresh 2D array (row = source species). */
    public double[][] toArray() {
        View v = view;
        double[][] out = new double[v.size][v.size];
        for (int i = 0; i < v.size; i++) {
            System.arraycopy(v.values, i * v.size, out[i], 0, v.size);
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
        this.view = new View(next, n);
        if (symmetric) {
            setSymmetric(true);
        }
    }

    /** Returns a deep copy (same size, values, and symmetric flag). */
    public AttractionMatrix copy() {
        View v = view;
        AttractionMatrix c = new AttractionMatrix(v.size);
        c.view = new View(v.values.clone(), v.size);
        c.symmetric = symmetric;
        return c;
    }

    private static void requirePositive(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("matrix size must be >= 1: " + n);
        }
    }
}