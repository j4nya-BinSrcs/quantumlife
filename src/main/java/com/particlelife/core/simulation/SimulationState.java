package com.particlelife.core.simulation;

/**
 * Lifecycle state of the simulation loop.
 */
public enum SimulationState {

    /** Physics steps are being executed continuously. */
    RUNNING,

    /** Loop is alive but not stepping; single-step is allowed. */
    PAUSED,

    /** Loop thread has exited (application shutdown). */
    STOPPED
}
