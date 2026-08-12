package com.particlelife.events;

import com.particlelife.core.simulation.SimulationState;

/**
 * Events published by the simulation subsystem on the {@link EventBus}.
 */
public sealed interface SimulationEvent {

    /** The engine transitioned between running/paused/stopped. */
    record StateChanged(SimulationState state) implements SimulationEvent {
    }

    /**
     * Periodic performance sample from the engine loop.
     *
     * @param stepsPerSecond physics steps executed per wall-clock second
     * @param stepMillis     average duration of one physics step
     * @param particleCount  particles currently simulated
     * @param frame          monotonically increasing step counter
     */
    record StatsUpdated(double stepsPerSecond, double stepMillis, int particleCount, long frame)
            implements SimulationEvent {
    }

    /** Particles were (re)spawned — renderer and UI counters should refresh. */
    record WorldRespawned(int particleCount, long seed) implements SimulationEvent {
    }

    /** The attraction matrix changed (edit, randomize, import, resize). */
    record MatrixChanged() implements SimulationEvent {
    }

    /** Species configuration changed (count, color, name, enabled). */
    record SpeciesChanged() implements SimulationEvent {
    }

    /**
     * The undo/redo history changed — UI should re-bind {@code canUndo} /
     * {@code canRedo} button enablement.
     */
    record HistoryChanged(boolean canUndo, boolean canRedo) implements SimulationEvent {
    }
}
