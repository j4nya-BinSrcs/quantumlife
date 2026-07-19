package com.particlelife.core.physics;

/**
 * Reflective boundary: particles bounce off the walls of the cube with a
 * restitution factor slightly below 1 so wall collisions bleed energy
 * instead of pumping it (mirror reflection of position and velocity).
 */
public record BounceBoundary(double worldSize, double restitution) implements BoundaryStrategy {

    /** Default energy retained per bounce. */
    public static final double DEFAULT_RESTITUTION = 0.85;

    public BounceBoundary(double worldSize) {
        this(worldSize, DEFAULT_RESTITUTION);
    }

    @Override
    public void apply(double[] positions, double[] velocities, int base, double dt) {
        double half = worldSize * 0.5;
        for (int axis = 0; axis < 3; axis++) {
            double p = positions[base + axis];
            if (p < -half) {
                positions[base + axis] = -half + (-half - p);
                velocities[base + axis] = Math.abs(velocities[base + axis]) * restitution;
            } else if (p > half) {
                positions[base + axis] = half - (p - half);
                velocities[base + axis] = -Math.abs(velocities[base + axis]) * restitution;
            }
        }
    }

    @Override
    public boolean isPeriodic() {
        return false;
    }
}
