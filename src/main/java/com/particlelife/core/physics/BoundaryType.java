package com.particlelife.core.physics;

/**
 * How the world edge treats particles (factory for
 * {@link BoundaryStrategy} implementations).
 */
public enum BoundaryType {

    /** Toroidal world: positions wrap, forces act across the seam. */
    WRAP("Wrap (toroidal)"),

    /** Solid walls: particles reflect with slight energy loss. */
    BOUNCE("Bounce"),

    /** No walls: a gentle spring pulls escapees back toward the cube. */
    OPEN("Open (soft pull-back)");

    private final String displayName;

    BoundaryType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Creates the strategy for a world of edge length {@code worldSize}. */
    public BoundaryStrategy create(double worldSize) {
        return switch (this) {
            case WRAP -> new WrapBoundary(worldSize);
            case BOUNCE -> new BounceBoundary(worldSize);
            case OPEN -> new OpenBoundary(worldSize);
        };
    }
}
