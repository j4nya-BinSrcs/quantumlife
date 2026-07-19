package com.particlelife.render;

/**
 * Particle rendering strategies selectable in the UI.
 */
public enum RenderMode {

    /** Batched billboard quads — scales to tens of thousands of particles. */
    BILLBOARDS("Billboards (fast)"),

    /** True sphere geometry with lighting — best under ~2500 particles. */
    SPHERES("Spheres (quality)"),

    /** Spheres for small populations, billboards beyond the threshold. */
    AUTO("Auto");

    /** Population above which AUTO switches to billboards. */
    public static final int AUTO_THRESHOLD = 2500;

    private final String displayName;

    RenderMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Resolves AUTO to a concrete mode for the given population. */
    public RenderMode resolve(int particleCount) {
        if (this != AUTO) {
            return this;
        }
        return particleCount <= AUTO_THRESHOLD ? SPHERES : BILLBOARDS;
    }
}
