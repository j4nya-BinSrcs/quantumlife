package com.particlelife.core.physics;

/**
 * Open world: no hard walls. Beyond the cube a spring acceleration
 * proportional to the overshoot pulls particles back, so the swarm can
 * breathe past the boundary but never escapes to infinity.
 */
public record OpenBoundary(double worldSize, double pullStrength) implements BoundaryStrategy {

    /** Default spring constant (1/s²) of the pull-back zone. */
    public static final double DEFAULT_PULL_STRENGTH = 20.0;

    public OpenBoundary(double worldSize) {
        this(worldSize, DEFAULT_PULL_STRENGTH);
    }

    @Override
    public void apply(double[] positions, double[] velocities, int base, double dt) {
        double half = worldSize * 0.5;
        for (int axis = 0; axis < 3; axis++) {
            double p = positions[base + axis];
            if (p < -half) {
                velocities[base + axis] += (-half - p) * pullStrength * dt;
            } else if (p > half) {
                velocities[base + axis] -= (p - half) * pullStrength * dt;
            }
        }
    }

    @Override
    public boolean isPeriodic() {
        return false;
    }
}
