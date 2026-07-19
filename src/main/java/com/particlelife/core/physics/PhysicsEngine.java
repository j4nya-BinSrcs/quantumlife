package com.particlelife.core.physics;

import com.particlelife.forces.AttractionMatrix;
import com.particlelife.forces.ForceFunction;
import com.particlelife.forces.ForceFunctionType;
import com.particlelife.particle.ParticleStore;

/**
 * Orchestrates one physics step: spatial index → force pass → integration.
 *
 * <p>Owns the {@link SpatialGrid} (recreated only when world size,
 * interaction radius, or capacity change) and rebuilds the boundary/kernel
 * strategies when their settings change; both are cheap immutable records,
 * so "rebuild" is a comparison and, rarely, a small allocation.
 *
 * <p>Not thread-safe: {@link #step} must be called from a single thread (the
 * simulation loop). Internally it fans work out across the common pool.
 */
public final class PhysicsEngine {

    private final PhysicsSettings settings;
    private final ForceCalculator forceCalculator = new ForceCalculator();
    private final Integrator integrator = new Integrator();

    private SpatialGrid grid;
    private BoundaryStrategy boundary;
    private BoundaryType boundaryTypeInUse;
    private double boundaryWorldSize;
    private ForceFunction kernel;
    private ForceFunctionType kernelTypeInUse;
    private double kernelBetaInUse;

    public PhysicsEngine(PhysicsSettings settings) {
        this.settings = settings;
    }

    /** The live settings this engine reads each step. */
    public PhysicsSettings settings() {
        return settings;
    }

    /**
     * Advances the simulation by {@code dt} seconds.
     *
     * @param store  particle storage (first {@code count()} slots are live)
     * @param matrix species attraction matrix; treated as read-only during the step
     * @param dt     time step in seconds (already includes any time scaling)
     */
    public void step(ParticleStore store, AttractionMatrix matrix, double dt) {
        refreshStrategies(store.capacity());
        forceCalculator.compute(store, matrix, kernel, grid, settings, boundary.isPeriodic());
        integrator.integrate(store, settings, boundary, dt);
    }

    private void refreshStrategies(int capacity) {
        double worldSize = settings.worldSize();
        double radius = settings.interactionRadius();
        if (grid == null || !grid.matches(worldSize, radius, capacity)) {
            grid = new SpatialGrid(worldSize, radius, capacity);
        }
        BoundaryType boundaryType = settings.boundaryType();
        if (boundary == null || boundaryTypeInUse != boundaryType || boundaryWorldSize != worldSize) {
            boundary = boundaryType.create(worldSize);
            boundaryTypeInUse = boundaryType;
            boundaryWorldSize = worldSize;
        }
        ForceFunctionType kernelType = settings.forceFunctionType();
        double beta = settings.beta();
        if (kernel == null || kernelTypeInUse != kernelType || kernelBetaInUse != beta) {
            kernel = kernelType.create(beta);
            kernelTypeInUse = kernelType;
            kernelBetaInUse = beta;
        }
    }
}
