package com.particlelife.core.physics;

import com.particlelife.math.MathUtils;

/**
 * Toroidal boundary: each coordinate wraps into {@code [-L/2, L/2)}.
 * Velocity is untouched — the particle sails through the seam.
 */
public record WrapBoundary(double worldSize) implements BoundaryStrategy {

    @Override
    public void apply(double[] positions, double[] velocities, int base, double dt) {
        double half = worldSize * 0.5;
        for (int axis = 0; axis < 3; axis++) {
            positions[base + axis] = MathUtils.wrap(positions[base + axis] + half, worldSize) - half;
        }
    }

    @Override
    public boolean isPeriodic() {
        return true;
    }
}
