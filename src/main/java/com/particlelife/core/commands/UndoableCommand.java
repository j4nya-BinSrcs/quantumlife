package com.particlelife.core.commands;

import com.particlelife.core.simulation.SimulationWorld;

/**
 * A {@link Command} whose effect can be reverted. {@link #undo} is only
 * called after {@link #execute} completed, on the engine thread.
 */
public interface UndoableCommand extends Command {

    /** Reverts the effect of the last {@link #execute}. */
    void undo(SimulationWorld world);
}
