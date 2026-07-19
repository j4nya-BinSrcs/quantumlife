package com.particlelife.forces;

/**
 * A smooth (C¹) variant of the Particle Life kernel.
 *
 * <p>Same structure as {@link PiecewiseLinearForce} — universal repulsion
 * below {@code β}, matrix-driven bump between {@code β} and {@code 1} — but
 * the bump is a raised cosine instead of a triangle, removing the derivative
 * kinks. Produces slightly softer, more fluid cluster boundaries at the cost
 * of one {@code cos} per interacting pair.
 */
public record SmoothForce(double beta) implements ForceFunction {

    public SmoothForce {
        if (beta <= 0.0 || beta >= 1.0) {
            throw new IllegalArgumentException("beta must be in (0, 1): " + beta);
        }
    }

    /** Creates the kernel with the conventional default {@code β}. */
    public SmoothForce() {
        this(PiecewiseLinearForce.DEFAULT_BETA);
    }

    @Override
    public double evaluate(double x, double attraction) {
        if (x < beta) {
            return x / beta - 1.0;
        }
        if (x < 1.0) {
            // Raised cosine over [beta, 1]: 0 at both ends, peak = attraction at the midpoint.
            double t = (x - beta) / (1.0 - beta);
            return attraction * 0.5 * (1.0 - Math.cos(2.0 * Math.PI * t));
        }
        return 0.0;
    }
}
