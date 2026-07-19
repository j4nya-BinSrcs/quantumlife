package com.particlelife.core.commands;

import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.events.SimulationEvent;
import com.particlelife.math.DeterministicRandom;

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

    /** Replaces the matrix wholesale (import). */
    public static final class SetAll extends MatrixCommand {
        private final double[][] values;

        public SetAll(double[][] values) {
            this.values = values;
        }

        @Override
        protected void apply(SimulationWorld world) {
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
