package com.particlelife.core.commands;

import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.events.SimulationEvent;

/**
 * Command pattern: an encapsulated user action on the simulation world.
 *
 * <p>Commands are executed on the engine thread (via the engine's command
 * queue), which is what makes concurrent UI edits safe — a command never
 * runs concurrently with a physics step. Undoable actions implement
 * {@link UndoableCommand}.
 */
public interface Command {

    /** Human-readable description (used for logs and undo menus). */
    String description();

    /** Applies the action. Runs on the engine thread. */
    void execute(SimulationWorld world);

    /**
     * Event to publish after successful execution (or undo), or {@code null}.
     */
    default SimulationEvent completionEvent() {
        return null;
    }
}
