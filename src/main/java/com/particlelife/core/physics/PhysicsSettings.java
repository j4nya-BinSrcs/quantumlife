package com.particlelife.core.physics;

import com.particlelife.forces.ForceFunctionType;
import com.particlelife.math.MathUtils;

/**
 * Live-tunable physics parameters.
 *
 * <p>Written by the UI thread, read by the physics thread every step — all
 * fields are {@code volatile} so edits are visible without locking, and each
 * setter validates/clamps so the engine can trust any combination of values.
 * (Individual parameters are independent; there are no multi-field
 * invariants, so per-field volatility is sufficient.)
 */
public final class PhysicsSettings {

    // Defaults tuned for a 200-unit world with a few thousand particles.
    public static final double DEFAULT_WORLD_SIZE = 200.0;
    public static final double DEFAULT_INTERACTION_RADIUS = 24.0;
    public static final double DEFAULT_BETA = 0.3;
    public static final double DEFAULT_FORCE_MULTIPLIER = 1.0;
    public static final double DEFAULT_FRICTION_HALF_LIFE = 0.04;
    public static final double DEFAULT_MAX_VELOCITY = 60.0;
    public static final double DEFAULT_TIME_STEP = 1.0 / 60.0;
    public static final double DEFAULT_DAMPING = 0.0;
    public static final double MIN_DISTANCE_FRACTION = 0.01;

    private volatile double worldSize = DEFAULT_WORLD_SIZE;
    private volatile double interactionRadius = DEFAULT_INTERACTION_RADIUS;
    private volatile double beta = DEFAULT_BETA;
    private volatile double forceMultiplier = DEFAULT_FORCE_MULTIPLIER;
    private volatile double frictionHalfLife = DEFAULT_FRICTION_HALF_LIFE;
    private volatile double maxVelocity = DEFAULT_MAX_VELOCITY;
    private volatile double timeStep = DEFAULT_TIME_STEP;
    private volatile double damping = DEFAULT_DAMPING;
    private volatile BoundaryType boundaryType = BoundaryType.WRAP;
    private volatile ForceFunctionType forceFunctionType = ForceFunctionType.PIECEWISE_LINEAR;

    /** Edge length of the cubic world, centered on the origin. */
    public double worldSize() {
        return worldSize;
    }

    public void setWorldSize(double worldSize) {
        this.worldSize = MathUtils.clamp(worldSize, 10.0, 10_000.0);
    }

    /** Maximum distance at which particles interact ({@code r_max}). */
    public double interactionRadius() {
        return interactionRadius;
    }

    public void setInteractionRadius(double radius) {
        this.interactionRadius = MathUtils.clamp(radius, 0.1, worldSize);
    }

    /** Repulsion-zone fraction of the interaction radius, in {@code (0, 1)}. */
    public double beta() {
        return beta;
    }

    public void setBeta(double beta) {
        this.beta = MathUtils.clamp(beta, 0.01, 0.99);
    }

    /** Global scale applied to all pairwise forces. */
    public double forceMultiplier() {
        return forceMultiplier;
    }

    public void setForceMultiplier(double multiplier) {
        this.forceMultiplier = MathUtils.clamp(multiplier, 0.0, 100.0);
    }

    /**
     * Time (seconds) for velocity to decay to half under friction.
     * {@code 0} disables friction entirely.
     */
    public double frictionHalfLife() {
        return frictionHalfLife;
    }

    public void setFrictionHalfLife(double halfLife) {
        this.frictionHalfLife = MathUtils.clamp(halfLife, 0.0, 10.0);
    }

    /** Global speed cap (world units / second). */
    public double maxVelocity() {
        return maxVelocity;
    }

    public void setMaxVelocity(double maxVelocity) {
        this.maxVelocity = MathUtils.clamp(maxVelocity, 0.1, 10_000.0);
    }

    /** Fixed physics step in seconds. */
    public double timeStep() {
        return timeStep;
    }

    public void setTimeStep(double timeStep) {
        this.timeStep = MathUtils.clamp(timeStep, 1.0 / 1000.0, 1.0 / 10.0);
    }

    /** Extra per-step velocity damping in {@code [0, 0.99]} (0 = none). */
    public double damping() {
        return damping;
    }

    public void setDamping(double damping) {
        this.damping = MathUtils.clamp(damping, 0.0, 0.99);
    }

    public BoundaryType boundaryType() {
        return boundaryType;
    }

    public void setBoundaryType(BoundaryType type) {
        this.boundaryType = type == null ? BoundaryType.WRAP : type;
    }

    public ForceFunctionType forceFunctionType() {
        return forceFunctionType;
    }

    public void setForceFunctionType(ForceFunctionType type) {
        this.forceFunctionType = type == null ? ForceFunctionType.PIECEWISE_LINEAR : type;
    }

    /**
     * Minimum center distance used when normalizing force directions, as a
     * guard against division blow-up for coincident particles.
     */
    public double minDistance() {
        return interactionRadius * MIN_DISTANCE_FRACTION;
    }

    /** Restores every parameter to its default. */
    public void resetToDefaults() {
        worldSize = DEFAULT_WORLD_SIZE;
        interactionRadius = DEFAULT_INTERACTION_RADIUS;
        beta = DEFAULT_BETA;
        forceMultiplier = DEFAULT_FORCE_MULTIPLIER;
        frictionHalfLife = DEFAULT_FRICTION_HALF_LIFE;
        maxVelocity = DEFAULT_MAX_VELOCITY;
        timeStep = DEFAULT_TIME_STEP;
        damping = DEFAULT_DAMPING;
        boundaryType = BoundaryType.WRAP;
        forceFunctionType = ForceFunctionType.PIECEWISE_LINEAR;
    }
}
