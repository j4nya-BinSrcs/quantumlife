package com.particlelife.core.commands;

import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.events.SimulationEvent;

/**
 * Base for undoable attraction-matrix mutations. Undo is implemented by
 * snapshot/restore of the whole matrix — at most {@code 16 × 16} doubles,
 * which is far simpler and no less efficient than per-command inverse logic.
 */
public abstract class MatrixCommand implements UndoableCommand {

    private double[][] before;
    private boolean symmetricBefore;

    @Override
    public final void execute(SimulationWorld world) {
        before = world.matrix().toArray();
        symmetricBefore = world.matrix().isSymmetric();
        apply(world);
    }

    /** The actual matrix mutation. */
    protected abstract void apply(SimulationWorld world);

    @Override
    public final void undo(SimulationWorld world) {
        world.matrix().setSymmetric(false);
        world.matrix().setFrom(before);
        world.matrix().setSymmetric(symmetricBefore);
    }

    @Override
    public SimulationEvent completionEvent() {
        return new SimulationEvent.MatrixChanged();
    }
}
