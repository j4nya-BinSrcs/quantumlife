package com.particlelife.forces;

/**
 * Selectable force kernels (factory for {@link ForceFunction} strategies).
 */
public enum ForceFunctionType {

    /** The canonical triangular kernel from the reference video. */
    PIECEWISE_LINEAR("Piecewise linear"),

    /** C¹ raised-cosine variant with softer cluster boundaries. */
    SMOOTH("Smooth");

    private final String displayName;

    ForceFunctionType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Creates the strategy instance for this type with the given {@code beta}. */
    public ForceFunction create(double beta) {
        return switch (this) {
            case PIECEWISE_LINEAR -> new PiecewiseLinearForce(beta);
            case SMOOTH -> new SmoothForce(beta);
        };
    }
}
