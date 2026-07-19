package com.particlelife.core.physics;

import com.particlelife.math.DeterministicRandom;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialGridTest {

    @Test
    void cellCountMatchesWorldAndRadius() {
        SpatialGrid grid = new SpatialGrid(200.0, 24.0, 100);
        // floor(200 / 24) = 8 cells per axis, each of size 25 >= 24.
        assertEquals(8, grid.cellsPerAxis());
        assertTrue(grid.supportsNeighborSearch());
    }

    @Test
    void coarseGridReportsNoNeighborSearchSupport() {
        SpatialGrid grid = new SpatialGrid(100.0, 45.0, 10);
        assertEquals(2, grid.cellsPerAxis());
        assertFalse(grid.supportsNeighborSearch());
    }

    @Test
    void cellCoordMapsAndClamps() {
        SpatialGrid grid = new SpatialGrid(100.0, 10.0, 10);
        assertEquals(0, grid.cellCoord(-50.0));
        assertEquals(9, grid.cellCoord(49.999));
        assertEquals(5, grid.cellCoord(0.0));
        // Out-of-bounds coordinates clamp instead of exploding (open boundary).
        assertEquals(0, grid.cellCoord(-999.0));
        assertEquals(9, grid.cellCoord(999.0));
    }

    @Test
    void rebuildPartitionsEveryParticleExactlyOnce() {
        int count = 500;
        double[] positions = randomPositions(count, 200.0, 42L);
        SpatialGrid grid = new SpatialGrid(200.0, 24.0, count);
        grid.rebuild(positions, count);

        Set<Integer> seen = new HashSet<>();
        int[] start = grid.cellStart();
        int[] entries = grid.entries();
        int cells = grid.cellsPerAxis() * grid.cellsPerAxis() * grid.cellsPerAxis();
        for (int c = 0; c < cells; c++) {
            for (int e = start[c]; e < start[c + 1]; e++) {
                assertTrue(seen.add(entries[e]), "particle listed twice: " + entries[e]);
            }
        }
        assertEquals(count, seen.size());
    }

    @Test
    void particlesAreListedInTheirOwnCell() {
        int count = 200;
        double[] positions = randomPositions(count, 100.0, 7L);
        SpatialGrid grid = new SpatialGrid(100.0, 10.0, count);
        grid.rebuild(positions, count);

        int[] start = grid.cellStart();
        int[] entries = grid.entries();
        for (int i = 0; i < count; i++) {
            int cell = grid.cellIndex(
                    grid.cellCoord(positions[i * 3]),
                    grid.cellCoord(positions[i * 3 + 1]),
                    grid.cellCoord(positions[i * 3 + 2]));
            boolean found = false;
            for (int e = start[cell]; e < start[cell + 1] && !found; e++) {
                found = entries[e] == i;
            }
            assertTrue(found, "particle " + i + " missing from its cell");
        }
    }

    @Test
    void neighborhoodOfAdjacentCellsCoversInteractionRadius() {
        // Any pair within rMax must land in cells at most 1 apart per axis.
        int count = 300;
        double rMax = 15.0;
        double world = 120.0;
        double[] positions = randomPositions(count, world, 99L);
        SpatialGrid grid = new SpatialGrid(world, rMax, count);
        grid.rebuild(positions, count);

        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                double dx = positions[i * 3] - positions[j * 3];
                double dy = positions[i * 3 + 1] - positions[j * 3 + 1];
                double dz = positions[i * 3 + 2] - positions[j * 3 + 2];
                if (dx * dx + dy * dy + dz * dz < rMax * rMax) {
                    pairs.add(new int[] {i, j});
                }
            }
        }
        assertFalse(pairs.isEmpty(), "test needs interacting pairs");
        for (int[] pair : pairs) {
            for (int axis = 0; axis < 3; axis++) {
                int ca = grid.cellCoord(positions[pair[0] * 3 + axis]);
                int cb = grid.cellCoord(positions[pair[1] * 3 + axis]);
                assertTrue(Math.abs(ca - cb) <= 1,
                        "pair within rMax spans %d cells on axis %d".formatted(Math.abs(ca - cb), axis));
            }
        }
    }

    @Test
    void matchesDetectsGeometryChanges() {
        SpatialGrid grid = new SpatialGrid(200.0, 24.0, 100);
        assertTrue(grid.matches(200.0, 24.0, 100));
        assertFalse(grid.matches(300.0, 24.0, 100));
        assertFalse(grid.matches(200.0, 10.0, 100));
        assertFalse(grid.matches(200.0, 24.0, 200));
        // Same cell resolution from a slightly different radius still matches.
        assertTrue(grid.matches(200.0, 25.0, 100));
    }

    private static double[] randomPositions(int count, double worldSize, long seed) {
        DeterministicRandom rng = new DeterministicRandom(seed);
        double half = worldSize / 2;
        double[] positions = new double[count * 3];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = rng.nextDouble(-half, half * 0.999);
        }
        return positions;
    }
}
