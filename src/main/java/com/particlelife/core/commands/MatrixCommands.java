package com.particlelife.core.commands;

import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.events.SimulationEvent;
import com.particlelife.math.DeterministicRandom;
import com.particlelife.species.SpeciesType;

/**
 * Concrete matrix commands. Small, closely related classes are grouped here
 * as nested types to keep the package navigable.
 */
public final class MatrixCommands {

    private MatrixCommands() {
    }

    /** Sets one cell {@code (i, j)} of the matrix. */
    public static final class EditCell extends MatrixCommand {
        private final int i;
        private final int j;
        private final double value;

        public EditCell(int i, int j, double value) {
            this.i = i;
            this.j = j;
            this.value = value;
        }

        @Override
        protected void apply(SimulationWorld world) {
            world.matrix().set(i, j, value);
        }

        @Override
        public String description() {
            return "Edit matrix cell (%d, %d) -> %.2f".formatted(i, j, value);
        }
    }

    /** Fills the matrix with seeded random values. */
    public static final class Randomize extends MatrixCommand {
        private final long seed;

        public Randomize(long seed) {
            this.seed = seed;
        }

        @Override
        protected void apply(SimulationWorld world) {
            world.matrix().randomize(new DeterministicRandom(seed));
        }

        @Override
        public String description() {
            return "Randomize matrix (seed " + seed + ")";
        }
    }

    /** Zeroes the matrix. */
    public static final class Reset extends MatrixCommand {
        @Override
        protected void apply(SimulationWorld world) {
            world.matrix().reset();
        }

        @Override
        public String description() {
            return "Reset matrix";
        }
    }

    /**
     * Validates a round-tripped matrix (from JSON import) before it can be
     * applied. Imported dimension must be square, within the species limit,
     * and exactly match the live species count — the engine keeps the matrix
     * and {@link com.particlelife.species.SpeciesRegistry} sized in lockstep,
     * so importing a differently-sized matrix must never reach the world.
     */
    public static void validateImport(double[][] matrix, int speciesCount) {
        if (matrix == null) {
            throw new IllegalArgumentException("matrix data is null");
        }
        if (matrix.length < 1) {
            throw new IllegalArgumentException("matrix must not be empty");
        }
        if (matrix.length > SpeciesType.MAX_SPECIES) {
            throw new IllegalArgumentException(
                    "matrix has %d species, maximum is %d".formatted(
                            matrix.length, SpeciesType.MAX_SPECIES));
        }
        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i] == null || matrix[i].length != matrix.length) {
                throw new IllegalArgumentException("matrix must be square");
            }
        }
        if (matrix.length != speciesCount) {
            throw new IllegalArgumentException(
                    "matrix size %d does not match the current %d species".formatted(
                            matrix.length, speciesCount));
        }
    }

    /** Replaces the matrix wholesale (import). */
    public static final class SetAll extends MatrixCommand {
        private final double[][] values;

        public SetAll(double[][] values) {
            this.values = values;
        }

        @Override
        protected void apply(SimulationWorld world) {
            if (values.length != world.species().count()) {
                throw new IllegalArgumentException(
                        "cannot import a %d-species matrix into a %d-species world".formatted(
                                values.length, world.species().count()));
            }
            world.matrix().setFrom(values);
        }

        @Override
        public String description() {
            return "Import matrix (" + values.length + " species)";
        }
    }

    /** Toggles symmetric mode. */
    public static final class SetSymmetric extends MatrixCommand {
        private final boolean symmetric;

        public SetSymmetric(boolean symmetric) {
            this.symmetric = symmetric;
        }

        @Override
        protected void apply(SimulationWorld world) {
            world.matrix().setSymmetric(symmetric);
        }

        @Override
        public String description() {
            return (symmetric ? "Enable" : "Disable") + " symmetric mode";
        }
    }

    /**
     * Records a matrix edit whose "before" state was captured externally —
     * used for drag gestures on the heatmap, where many incremental live
     * edits should collapse into a single undo step spanning the gesture.
     */
    public static final class ApplyEdit implements UndoableCommand {
        private final double[][] before;
        private final double[][] after;

        public ApplyEdit(double[][] before, double[][] after) {
            this.before = before;
            this.after = after;
        }

        @Override
        public void execute(SimulationWorld world) {
            boolean symmetric = world.matrix().isSymmetric();
            world.matrix().setSymmetric(false);
            world.matrix().setFrom(after);
            world.matrix().setSymmetric(symmetric);
        }

        @Override
        public void undo(SimulationWorld world) {
            boolean symmetric = world.matrix().isSymmetric();
            world.matrix().setSymmetric(false);
            world.matrix().setFrom(before);
            world.matrix().setSymmetric(symmetric);
        }

        @Override
        public SimulationEvent completionEvent() {
            return new SimulationEvent.MatrixChanged();
        }

        @Override
        public String description() {
            return "Edit matrix (drag gesture)";
        }
    }

    /** Respawns all particles from the current seed (not undoable). */
    public static final class Respawn implements Command {
        private volatile SimulationEvent event;

        @Override
        public void execute(SimulationWorld world) {
            world.respawn();
            event = new SimulationEvent.WorldRespawned(
                    world.store().count(), world.simulationSettings().seed());
        }

        @Override
        public SimulationEvent completionEvent() {
            return event;
        }

        @Override
        public String description() {
            return "Respawn particles";
        }
    }
}
