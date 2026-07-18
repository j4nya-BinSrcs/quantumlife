package com.particlelife.particle;

/**
 * Lifecycle state of a particle slot.
 */
public enum LifeState {

    /** Simulated and rendered. */
    ALIVE,

    /** Hidden and skipped by physics (e.g. its species is disabled). */
    DORMANT,

    /** Slot is free for reuse by the particle pool. */
    DEAD
}
