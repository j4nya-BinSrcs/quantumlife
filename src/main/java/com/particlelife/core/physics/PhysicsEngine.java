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
    private final GpuForceEngine gpuForce = new GpuForceEngine();
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
        boolean wantGpu = wantsGpu(settings.computeBackend(), store.count(), kernelTypeInUse);
        if (wantGpu) {
            gpuForce.ensureInitialized();
        }
        if (wantGpu && gpuForce.available()) {
            gpuForce.compute(
                    store.positions(), store.speciesIndices(), store.forces(),
                    store.count(), store.capacity(),
                    matrix.values(), matrix.size(),
                    settings.interactionRadius(), settings.minDistance(),
                    settings.forceMultiplier() * settings.interactionRadius(),
                    settings.worldSize(), boundary.isPeriodic(), settings.beta());
        } else {
            forceCalculator.compute(store, matrix, kernel, grid, settings, boundary.isPeriodic());
        }
        integrator.integrate(store, settings, boundary, dt);
    }

    /**
     * Whether the force pass should run on the GPU given the current selection,
     * workload, and active kernel. {@code AUTO} always routes to the CPU
     * spatial grid (on current hardware the GPU pass loses to it below very
     * large populations due to per-frame transfer/sync latency), so the GPU
     * engine is only used when explicitly selected. The smooth kernel always
     * runs on the CPU.
     */
    static boolean wantsGpu(ComputeBackend selected, int particleCount, ForceFunctionType kernelType) {
        if (kernelType != ForceFunctionType.PIECEWISE_LINEAR) {
            return false;
        }
        return switch (selected) {
            case AUTO -> false;
            case GPU -> true;
            case CPU -> false;
        };
    }

    /**
     * Releases GPU resources. Must be called from the engine thread once the
     * simulation loop has stopped; the CPU path needs no cleanup.
     */
    public void close() {
        gpuForce.close();
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
