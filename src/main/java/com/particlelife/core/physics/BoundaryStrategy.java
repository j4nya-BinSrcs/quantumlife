package com.particlelife.core.physics;

/**
 * Strategy applied to each particle after position integration to enforce
 * the world edge. Implementations operate directly on the store's interleaved
 * arrays for zero-allocation hot-loop use and must be thread-safe (they hold
 * only immutable configuration).
 */
public sealed interface BoundaryStrategy permits WrapBoundary, BounceBoundary, OpenBoundary {

    /**
     * Constrains the particle whose xyz slot starts at {@code base}.
     *
     * @param positions  interleaved xyz positions
     * @param velocities interleaved xyz velocities
     * @param base       index of the particle's x component ({@code 3 * particleIndex})
     * @param dt         the time step, for strategies that apply restoring forces
     */
    void apply(double[] positions, double[] velocities, int base, double dt);

    /**
     * Whether distances must use the minimum-image convention (true for
     * toroidal worlds).
     */
    boolean isPeriodic();
}
