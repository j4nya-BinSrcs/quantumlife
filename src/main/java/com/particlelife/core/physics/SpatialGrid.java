package com.particlelife.core.physics;

import com.particlelife.math.MathUtils;

/**
 * Uniform spatial grid over the world cube, the structure that turns the
 * O(N²) pair search into O(N·k).
 *
 * <p>The cube {@code [-L/2, L/2]³} is divided into {@code m³} cubic cells
 * with {@code m = floor(L / r_max)} (at least 1), so the cell edge is always
 * {@code >= r_max} and any particle's interaction partners lie in its own
 * cell or the 26 adjacent ones.
 *
 * <p>Storage is a counting-sort layout into two flat arrays reused across
 * frames — no per-cell collections, no per-frame allocation:
 * <ul>
 *   <li>{@code cellStart[c]..cellStart[c+1]} — the index range in
 *       {@code entries} holding cell {@code c}'s particles;</li>
 *   <li>{@code entries} — particle indices grouped by cell.</li>
 * </ul>
 *
 * <p>A uniform grid is chosen over an octree deliberately: Particle Life has
 * a single global interaction radius and near-uniform particle density,
 * which is the ideal case for a grid (O(1) insert, perfect cache locality)
 * and the degenerate case for an octree (rebuild cost, pointer chasing).
 *
 * <p>{@link #rebuild} must not run concurrently with queries; the engine
 * rebuilds at the start of each step, before the parallel force pass.
 */
public final class SpatialGrid {

    private final double worldSize;
    private final int cellsPerAxis;
    private final double inverseCellSize;

    private final int[] cellStart;
    private final int[] cursor;
    private final int[] entries;
    private final int[] cellOfParticle;

    /**
     * Creates a grid for a world of edge {@code worldSize} and interaction
     * radius {@code interactionRadius}, sized for {@code capacity} particles.
     */
    public SpatialGrid(double worldSize, double interactionRadius, int capacity) {
        if (worldSize <= 0 || interactionRadius <= 0) {
            throw new IllegalArgumentException("worldSize and interactionRadius must be positive");
        }
        this.worldSize = worldSize;
        this.cellsPerAxis = Math.max(1, (int) (worldSize / interactionRadius));
        double cellSize = worldSize / cellsPerAxis;
        this.inverseCellSize = 1.0 / cellSize;
        int cellCount = cellsPerAxis * cellsPerAxis * cellsPerAxis;
        this.cellStart = new int[cellCount + 1];
        this.cursor = new int[cellCount + 1];
        this.entries = new int[capacity];
        this.cellOfParticle = new int[capacity];
    }

    /** Number of cells along each axis. */
    public int cellsPerAxis() {
        return cellsPerAxis;
    }

    /**
     * Whether the grid is fine enough for the 27-cell neighborhood search to
     * be exact and duplicate-free. Below 3 cells per axis the engine falls
     * back to brute force (which at that radius/world ratio is the honest
     * algorithm anyway).
     */
    public boolean supportsNeighborSearch() {
        return cellsPerAxis >= 3;
    }

    /** Grid cell coordinate of a world coordinate, clamped into range. */
    public int cellCoord(double worldCoord) {
        int c = (int) ((worldCoord + worldSize * 0.5) * inverseCellSize);
        return MathUtils.clamp(c, 0, cellsPerAxis - 1);
    }

    /** Flat index of the cell at grid coordinates {@code (cx, cy, cz)}. */
    public int cellIndex(int cx, int cy, int cz) {
        return (cz * cellsPerAxis + cy) * cellsPerAxis + cx;
    }

    /**
     * Rebuilds the index from the first {@code count} particles of the
     * interleaved {@code positions} array. O(N) counting sort.
     */
    public void rebuild(double[] positions, int count) {
        java.util.Arrays.fill(cellStart, 0);

        // Pass 1: histogram cell occupancy (shifted by one for prefix sum).
        for (int i = 0; i < count; i++) {
            int base = i * 3;
            int cell = cellIndex(
                    cellCoord(positions[base]),
                    cellCoord(positions[base + 1]),
                    cellCoord(positions[base + 2]));
            cellOfParticle[i] = cell;
            cellStart[cell + 1]++;
        }

        // Pass 2: exclusive prefix sum -> start offsets.
        for (int c = 0; c < cellStart.length - 1; c++) {
            cellStart[c + 1] += cellStart[c];
        }

        // Pass 3: scatter particle indices into their cell ranges.
        System.arraycopy(cellStart, 0, cursor, 0, cellStart.length);
        for (int i = 0; i < count; i++) {
            entries[cursor[cellOfParticle[i]]++] = i;
        }
    }

    /** Start offsets per cell; cell {@code c} spans {@code [start[c], start[c+1])}. */
    public int[] cellStart() {
        return cellStart;
    }

    /** Particle indices grouped by cell (valid up to the rebuilt count). */
    public int[] entries() {
        return entries;
    }

    /**
     * Returns whether this grid still matches the given world geometry and
     * capacity (used by the engine to decide when to reallocate).
     */
    public boolean matches(double worldSize, double interactionRadius, int capacity) {
        int wantedCells = Math.max(1, (int) (worldSize / interactionRadius));
        return this.worldSize == worldSize
                && this.cellsPerAxis == wantedCells
                && this.entries.length == capacity;
    }
}
