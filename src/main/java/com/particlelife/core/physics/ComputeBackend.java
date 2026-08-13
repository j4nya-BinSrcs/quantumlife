package com.particlelife.core.physics;

/**
 * Where the pairwise force pass is executed.
 *
 * <p>{@link #AUTO} always routes to the CPU spatial grid (the GPU pass is
 * latency-bound below very large populations); {@link #GPU} forces the GPU
 * path when supported and falls back to the CPU otherwise; {@link #CPU} always
 * uses the parallel CPU spatial-grid force calculator.
 */
public enum ComputeBackend {

    /** Let the engine choose based on workload and availability. */
    AUTO("Auto"),

    /** Force the OpenGL compute-shader force pass when available. */
    GPU("GPU"),

    /** Always use the CPU spatial-grid force calculator. */
    CPU("CPU");

    private final String displayName;

    ComputeBackend(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}