package com.particlelife.core.commands;

import com.particlelife.core.engine.SimulationEngine;
import com.particlelife.events.EventBus;
import com.particlelife.events.SimulationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Executes {@link Command}s on the engine thread and maintains undo/redo
 * history for {@link UndoableCommand}s.
 *
 * <p>All stack manipulation happens inside engine-queue runnables, so the
 * history is confined to the engine thread; {@link #canUndo()} /
 * {@link #canRedo()} are volatile approximations for UI enablement.
 */
public final class CommandManager {

    private static final Logger LOG = LoggerFactory.getLogger(CommandManager.class);
    private static final int HISTORY_LIMIT = 100;

    private final SimulationEngine engine;
    private final EventBus eventBus;

    // Engine-thread confined.
    private final Deque<UndoableCommand> undoStack = new ArrayDeque<>();
    private final Deque<UndoableCommand> redoStack = new ArrayDeque<>();

    private volatile boolean canUndo;
    private volatile boolean canRedo;

    public CommandManager(SimulationEngine engine, EventBus eventBus) {
        this.engine = engine;
        this.eventBus = eventBus;
    }

    /** Submits {@code command} for execution on the engine thread. */
    public void execute(Command command) {
        engine.submit(() -> {
            LOG.debug("Executing command: {}", command.description());
            command.execute(engine.world());
            if (command instanceof UndoableCommand undoable) {
                undoStack.push(undoable);
                if (undoStack.size() > HISTORY_LIMIT) {
                    undoStack.removeLast();
                }
                redoStack.clear();
            }
            publishCompletion(command);
            refreshFlags();
        });
    }

    /** Reverts the most recent undoable command, if any. */
    public void undo() {
        engine.submit(() -> {
            UndoableCommand command = undoStack.poll();
            if (command == null) {
                return;
            }
            LOG.debug("Undoing command: {}", command.description());
            command.undo(engine.world());
            redoStack.push(command);
            publishCompletion(command);
            refreshFlags();
        });
    }

    /** Re-applies the most recently undone command, if any. */
    public void redo() {
        engine.submit(() -> {
            UndoableCommand command = redoStack.poll();
            if (command == null) {
                return;
            }
            LOG.debug("Redoing command: {}", command.description());
            command.execute(engine.world());
            undoStack.push(command);
            publishCompletion(command);
            refreshFlags();
        });
    }

    public boolean canUndo() {
        return canUndo;
    }

    public boolean canRedo() {
        return canRedo;
    }

    private void publishCompletion(Command command) {
        SimulationEvent event = command.completionEvent();
        if (event != null) {
            eventBus.publish(event);
        }
    }

    private void refreshFlags() {
        canUndo = !undoStack.isEmpty();
        canRedo = !redoStack.isEmpty();
    }
}
