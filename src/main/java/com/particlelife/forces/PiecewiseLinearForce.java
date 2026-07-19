package com.particlelife.forces;

/**
 * The canonical Particle Life force kernel (piecewise linear), as presented
 * in Tom Mohr's <em>"How Particle Life emerges from simplicity"</em>:
 *
 * <pre>
 *            ┌  x/β − 1                              x &lt; β    (universal repulsion)
 * f(x, a) =  │  a · (1 − |2x − 1 − β| / (1 − β))     β ≤ x &lt; 1 (matrix-driven bump)
 *            └  0                                    x ≥ 1
 * </pre>
 *
 * <p>The repulsion branch is independent of the matrix and reaches {@code -1}
 * at contact, preventing overlap without collision detection. The matrix
 * branch is a triangle that is zero at {@code β} and {@code 1} and peaks with
 * value {@code a} at the midpoint {@code (1 + β) / 2}, so the force fades
 * continuously to zero at the interaction radius.
 */
public record PiecewiseLinearForce(double beta) implements ForceFunction {

    /** The conventional default repulsion-zone fraction. */
    public static final double DEFAULT_BETA = 0.3;

    public PiecewiseLinearForce {
        if (beta <= 0.0 || beta >= 1.0) {
            throw new IllegalArgumentException("beta must be in (0, 1): " + beta);
        }
    }

    /** Creates the kernel with {@link #DEFAULT_BETA}. */
    public PiecewiseLinearForce() {
        this(DEFAULT_BETA);
    }

    @Override
    public double evaluate(double x, double attraction) {
        if (x < beta) {
            return x / beta - 1.0;
        }
        if (x < 1.0) {
            return attraction * (1.0 - Math.abs(2.0 * x - 1.0 - beta) / (1.0 - beta));
        }
        return 0.0;
    }
}
