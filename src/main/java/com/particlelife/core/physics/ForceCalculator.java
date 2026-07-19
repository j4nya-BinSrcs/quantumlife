package com.particlelife.core.physics;

import com.particlelife.forces.AttractionMatrix;
import com.particlelife.forces.ForceFunction;
import com.particlelife.math.MathUtils;
import com.particlelife.particle.ParticleStore;

import java.util.stream.IntStream;

/**
 * Computes the pairwise Particle Life forces for one physics step.
 *
 * <p>This is the hot loop. Design constraints, in priority order:
 * <ol>
 *   <li><strong>Correctness</strong> — results are identical to the brute
 *       force reference (verified by tests) and independent of thread count,
 *       because each particle's force is accumulated by exactly one thread
 *       over a deterministic neighbor order.</li>
 *   <li><strong>No allocation</strong> — operates on the store's flat arrays
 *       and locals only.</li>
 *   <li><strong>Parallelism</strong> — particles are partitioned across the
 *       common pool; positions are read-only during the pass and each thread
 *       writes only its own particles' force slots.</li>
 * </ol>
 *
 * <p>When the grid is too coarse for a valid 27-cell search
 * (world smaller than ~3 interaction radii), it falls back to exact brute
 * force over all pairs.
 */
public final class ForceCalculator {

    /**
     * Below this particle count the parallel fork/join overhead outweighs
     * the work; run single-threaded instead.
     */
    private static final int PARALLEL_THRESHOLD = 256;

    /**
     * Accumulates forces into {@code store.forces()} for the first
     * {@code count} particles. Overwrites previous force values.
     *
     * @param periodic whether distances use the minimum-image convention
     */
    public void compute(ParticleStore store,
                        AttractionMatrix matrix,
                        ForceFunction kernel,
                        SpatialGrid grid,
                        PhysicsSettings settings,
                        boolean periodic) {
        int count = store.count();
        if (count == 0) {
            return;
        }
        grid.rebuild(store.positions(), count);

        boolean useGrid = grid.supportsNeighborSearch();
        IntStream indices = IntStream.range(0, count);
        if (count >= PARALLEL_THRESHOLD) {
            indices = indices.parallel();
        }
        double rMax = settings.interactionRadius();
        double minDistance = settings.minDistance();
        double scale = settings.forceMultiplier() * rMax;
        double worldSize = settings.worldSize();

        if (useGrid) {
            indices.forEach(i -> accumulateWithGrid(
                    store, matrix, kernel, grid, i, rMax, minDistance, scale, periodic, worldSize));
        } else {
            indices.forEach(i -> accumulateBruteForce(
                    store, matrix, kernel, i, count, rMax, minDistance, scale, periodic, worldSize));
        }
    }

    private void accumulateWithGrid(ParticleStore store,
                                    AttractionMatrix matrix,
                                    ForceFunction kernel,
                                    SpatialGrid grid,
                                    int i,
                                    double rMax,
                                    double minDistance,
                                    double scale,
                                    boolean periodic,
                                    double worldSize) {
        double[] positions = store.positions();
        int[] species = store.speciesIndices();
        int[] cellStart = grid.cellStart();
        int[] entries = grid.entries();
        int m = grid.cellsPerAxis();

        int base = i * 3;
        double px = positions[base];
        double py = positions[base + 1];
        double pz = positions[base + 2];
        int si = species[i];

        int cx = grid.cellCoord(px);
        int cy = grid.cellCoord(py);
        int cz = grid.cellCoord(pz);

        double fx = 0;
        double fy = 0;
        double fz = 0;

        for (int dz = -1; dz <= 1; dz++) {
            int ncz = neighborCoord(cz + dz, m, periodic);
            if (ncz < 0) {
                continue;
            }
            for (int dy = -1; dy <= 1; dy++) {
                int ncy = neighborCoord(cy + dy, m, periodic);
                if (ncy < 0) {
                    continue;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    int ncx = neighborCoord(cx + dx, m, periodic);
                    if (ncx < 0) {
                        continue;
                    }
                    int cell = grid.cellIndex(ncx, ncy, ncz);
                    int end = cellStart[cell + 1];
                    for (int e = cellStart[cell]; e < end; e++) {
                        int j = entries[e];
                        if (j == i) {
                            continue;
                        }
                        int jBase = j * 3;
                        double ddx = positions[jBase] - px;
                        double ddy = positions[jBase + 1] - py;
                        double ddz = positions[jBase + 2] - pz;
                        if (periodic) {
                            ddx = MathUtils.minimumImage(ddx, worldSize);
                            ddy = MathUtils.minimumImage(ddy, worldSize);
                            ddz = MathUtils.minimumImage(ddz, worldSize);
                        }
                        double d2 = ddx * ddx + ddy * ddy + ddz * ddz;
                        if (d2 >= rMax * rMax || d2 == 0.0) {
                            continue;
                        }
                        double r = Math.sqrt(d2);
                        double f = kernel.evaluate(r / rMax, matrix.get(si, species[j]));
                        // scale/r folds the direction normalization into the magnitude.
                        double s = f * scale / Math.max(r, minDistance);
                        fx += ddx * s;
                        fy += ddy * s;
                        fz += ddz * s;
                    }
                }
            }
        }

        double[] forces = store.forces();
        forces[base] = fx;
        forces[base + 1] = fy;
        forces[base + 2] = fz;
    }

    private void accumulateBruteForce(ParticleStore store,
                                      AttractionMatrix matrix,
                                      ForceFunction kernel,
                                      int i,
                                      int count,
                                      double rMax,
                                      double minDistance,
                                      double scale,
                                      boolean periodic,
                                      double worldSize) {
        double[] positions = store.positions();
        int[] species = store.speciesIndices();

        int base = i * 3;
        double px = positions[base];
        double py = positions[base + 1];
        double pz = positions[base + 2];
        int si = species[i];

        double fx = 0;
        double fy = 0;
        double fz = 0;

        for (int j = 0; j < count; j++) {
            if (j == i) {
                continue;
            }
            int jBase = j * 3;
            double ddx = positions[jBase] - px;
            double ddy = positions[jBase + 1] - py;
            double ddz = positions[jBase + 2] - pz;
            if (periodic) {
                ddx = MathUtils.minimumImage(ddx, worldSize);
                ddy = MathUtils.minimumImage(ddy, worldSize);
                ddz = MathUtils.minimumImage(ddz, worldSize);
            }
            double d2 = ddx * ddx + ddy * ddy + ddz * ddz;
            if (d2 >= rMax * rMax || d2 == 0.0) {
                continue;
            }
            double r = Math.sqrt(d2);
            double f = kernel.evaluate(r / rMax, matrix.get(si, species[j]));
            double s = f * scale / Math.max(r, minDistance);
            fx += ddx * s;
            fy += ddy * s;
            fz += ddz * s;
        }

        double[] forces = store.forces();
        forces[base] = fx;
        forces[base + 1] = fy;
        forces[base + 2] = fz;
    }

    /**
     * Maps a raw neighbor cell coordinate into the grid: wraps when periodic,
     * returns {@code -1} (skip) when out of range otherwise.
     */
    private static int neighborCoord(int c, int cellsPerAxis, boolean periodic) {
        if (c < 0) {
            return periodic ? c + cellsPerAxis : -1;
        }
        if (c >= cellsPerAxis) {
            return periodic ? c - cellsPerAxis : -1;
        }
        return c;
    }
}
