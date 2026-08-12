package com.particlelife.species;

import java.util.Objects;

/**
 * Runtime state of one species: a mutable view seeded from a
 * {@link SpeciesType} archetype that the user can rename, recolor, and
 * enable/disable live.
 *
 * <p>Identity is the immutable {@link #index()} — the row/column of this
 * species in the attraction matrix. Everything else is presentation or
 * physics parameterization and may change during a session.
 *
 * <p>The mutable fields are {@code volatile}: written on the simulation
 * engine thread and read concurrently from the FX thread (sidebar rows,
 * renderer palette). Writers accept a slightly stale read; a later change
 * event repaints.
 */
public final class Species {

    private final int index;
    private final SpeciesType type;

    private volatile String name;
    private volatile int colorRgb;
    private volatile double mass;
    private volatile double radius;
    private volatile boolean enabled = true;

    private Species(int index, SpeciesType type) {
        this.index = index;
        this.type = type;
        this.name = type.displayName();
        this.colorRgb = type.defaultColorRgb();
        this.mass = type.defaultMass();
        this.radius = type.defaultRadius();
    }

    /** Creates a species seeded from the archetype at the same index. */
    public static Species fromType(int index) {
        return new Species(index, SpeciesType.byIndex(index));
    }

    /** Immutable matrix row/column index of this species. */
    public int index() {
        return index;
    }

    /** The archetype this species was seeded from. */
    public SpeciesType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    /** Color as packed 24-bit RGB ({@code 0xRRGGBB}). */
    public int colorRgb() {
        return colorRgb;
    }

    public void setColorRgb(int colorRgb) {
        this.colorRgb = colorRgb & 0xFFFFFF;
    }

    public double mass() {
        return mass;
    }

    public void setMass(double mass) {
        if (mass <= 0) {
            throw new IllegalArgumentException("mass must be positive: " + mass);
        }
        this.mass = mass;
    }

    public double radius() {
        return radius;
    }

    public void setRadius(double radius) {
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive: " + radius);
        }
        this.radius = radius;
    }

    /** Disabled species spawn no particles and their particles are hidden. */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Resets name, color, mass and radius to the archetype defaults. */
    public void resetToDefaults() {
        this.name = type.displayName();
        this.colorRgb = type.defaultColorRgb();
        this.mass = type.defaultMass();
        this.radius = type.defaultRadius();
        this.enabled = true;
    }

    @Override
    public String toString() {
        return "Species[%d, %s, #%06X]".formatted(index, name, colorRgb);
    }
}
