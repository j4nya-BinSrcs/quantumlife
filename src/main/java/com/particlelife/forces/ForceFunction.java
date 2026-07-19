package com.particlelife.forces;

/**
 * Strategy for the pairwise force kernel.
 *
 * <p>A force function maps a <em>normalized</em> distance
 * {@code x = r / r_max} in {@code [0, 1]} and an attraction-matrix entry
 * {@code a} in {@code [-1, 1]} to a dimensionless force magnitude:
 * positive pulls the particles together, negative pushes them apart. The
 * engine scales the result by {@code r_max} and the global force multiplier.
 *
 * <p>Implementations must be:
 * <ul>
 *   <li><strong>bounded</strong> — {@code |f| <= max(1, |a|)} so stability
 *       guards can reason about worst-case impulses;</li>
 *   <li><strong>zero at and beyond {@code x = 1}</strong> — a hard support of
 *       {@code r_max} is what makes the spatial-grid cutoff exact rather than
 *       approximate;</li>
 *   <li><strong>stateless and thread-safe</strong> — the kernel is evaluated
 *       concurrently from many worker threads.</li>
 * </ul>
 */
public sealed interface ForceFunction permits PiecewiseLinearForce, SmoothForce {

    /**
     * Evaluates the kernel.
     *
     * @param x          normalized distance {@code r / r_max}, in {@code [0, 1]}
     * @param attraction matrix entry for the ordered species pair, in {@code [-1, 1]}
     * @return dimensionless force; {@code > 0} attracts, {@code < 0} repels
     */
    double evaluate(double x, double attraction);

    /**
     * The normalized distance below which universal repulsion applies,
     * in {@code (0, 1)}. Also the equilibrium spacing of mutually attracted
     * particles, i.e. the cluster density knob.
     */
    double beta();
}
