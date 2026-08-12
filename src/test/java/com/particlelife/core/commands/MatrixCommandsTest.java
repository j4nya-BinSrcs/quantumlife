package com.particlelife.core.commands;

import com.particlelife.core.simulation.SimulationSettings;
import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.species.SpeciesType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers matrix import validation and the drag-gesture {@code ApplyEdit}
 * command — the pieces added for safe heatmap editing.
 */
class MatrixCommandsTest {

    private SimulationWorld world;

    @BeforeEach
    void setUp() {
        world = new SimulationWorld(new SimulationSettings(), new com.particlelife.core.physics.PhysicsSettings());
    }

    // ------------------------------------------------------------------
    // validateImport
    // ------------------------------------------------------------------

    @Test
    void validateImportAcceptsSquareMatrixMatchingSpeciesCount() {
        world.setSpeciesCount(4);
        assertDoesNotThrow(() -> MatrixCommands.validateImport(
                new double[][]{{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}}, 4));
    }

    @Test
    void validateImportRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> MatrixCommands.validateImport(null, 4));
    }

    @Test
    void validateImportRejectsEmptyMatrix() {
        assertThrows(IllegalArgumentException.class,
                () -> MatrixCommands.validateImport(new double[0][], 4));
    }

    @Test
    void validateImportRejectsOversizedMatrix() {
        double[][] oversized = new double[SpeciesType.MAX_SPECIES + 1][SpeciesType.MAX_SPECIES + 1];
        assertThrows(IllegalArgumentException.class,
                () -> MatrixCommands.validateImport(oversized, SpeciesType.MAX_SPECIES + 1));
    }

    @Test
    void validateImportRejectsRaggedRows() {
        double[][] ragged = {{1, 0}, {0}};
        assertThrows(IllegalArgumentException.class, () -> MatrixCommands.validateImport(ragged, 2));
    }

    @Test
    void validateImportRejectsDimensionMismatch() {
        assertThrows(IllegalArgumentException.class,
                () -> MatrixCommands.validateImport(new double[3][3], 4));
        assertThrows(IllegalArgumentException.class,
                () -> MatrixCommands.validateImport(new double[5][5], 4));
    }

    // ------------------------------------------------------------------
    // SetAll — the import command guards the matrix/species invariant
    // ------------------------------------------------------------------

    @Test
    void setAllAppliesMatchingMatrix() {
        world.setSpeciesCount(2);
        double[][] values = {{0.1, -0.2}, {0.3, 0.4}};
        MatrixCommands.SetAll command = new MatrixCommands.SetAll(values);
        command.execute(world);
        assertEquals(0.1, world.matrix().get(0, 0), 1e-12);
        assertEquals(-0.2, world.matrix().get(0, 1), 1e-12);
        assertEquals(0.3, world.matrix().get(1, 0), 1e-12);
        assertEquals(0.4, world.matrix().get(1, 1), 1e-12);
        assertEquals(2, world.species().count(), "matrix size must stay in sync with species");
    }

    @Test
    void setAllRejectsDimensionMismatch() {
        world.setSpeciesCount(3);
        MatrixCommands.SetAll command = new MatrixCommands.SetAll(new double[4][4]);
        assertThrows(IllegalArgumentException.class, () -> command.execute(world));
        assertEquals(3, world.species().count(), "failed import must not resize species");
    }

    // ------------------------------------------------------------------
    // ApplyEdit — the drag-gesture command
    // ------------------------------------------------------------------

    @Test
    void applyEditAppliesAfterThenUndoRestoresBefore() {
        world.setSpeciesCount(2);
        double[][] before = {{0.1, 0.2}, {0.3, 0.4}};
        double[][] after = {{0.5, 0.6}, {0.7, 0.8}};
        world.matrix().setFrom(before);

        MatrixCommands.ApplyEdit command = new MatrixCommands.ApplyEdit(before, after);
        command.execute(world);
        assertEquals(0.5, world.matrix().get(0, 0), 1e-12);
        assertEquals(0.8, world.matrix().get(1, 1), 1e-12);

        command.undo(world);
        assertEquals(0.1, world.matrix().get(0, 0), 1e-12);
        assertEquals(0.4, world.matrix().get(1, 1), 1e-12);
    }

    @Test
    void applyEditPreservesSymmetricModeAcrossUndo() {
        world.setSpeciesCount(2);
        world.matrix().setSymmetric(true);
        world.matrix().set(0, 1, 0.75);

        double[][] before = world.matrix().toArray();
        double[][] after = new double[before.length][before.length];
        for (int i = 0; i < before.length; i++) {
            System.arraycopy(before[i], 0, after[i], 0, before.length);
        }
        after[0][1] = after[1][0] = -0.5;

        MatrixCommands.ApplyEdit command = new MatrixCommands.ApplyEdit(before, after);
        command.execute(world);
        assertEquals(-0.5, world.matrix().get(0, 1), 1e-12);
        assertEquals(-0.5, world.matrix().get(1, 0), 1e-12);
        assertTrue(world.matrix().isSymmetric());

        command.undo(world);
        assertEquals(0.75, world.matrix().get(0, 1), 1e-12);
        assertTrue(world.matrix().isSymmetric());
    }
}