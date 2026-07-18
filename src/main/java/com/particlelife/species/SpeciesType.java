package com.particlelife.species;

/**
 * The catalog of built-in species archetypes.
 *
 * <p>Each constant defines the <em>defaults</em> for one species: display
 * name, color, mass and radius. The simulation supports an arbitrary species
 * count ({@code 1..MAX_SPECIES}); the first {@code n} constants seed the
 * runtime {@link Species} instances, which the user may then recolor or
 * rename freely without affecting these defaults.
 */
public enum SpeciesType {

    ALPHA("Alpha", 0xFF5252, 1.00, 1.00),
    BETA("Beta", 0x40C4FF, 1.00, 1.00),
    GAMMA("Gamma", 0x69F0AE, 1.00, 1.00),
    DELTA("Delta", 0xFFD740, 1.00, 1.00),
    EPSILON("Epsilon", 0xE040FB, 1.00, 1.00),
    ZETA("Zeta", 0xFF6E40, 1.00, 1.00),
    ETA("Eta", 0x18FFFF, 1.00, 1.00),
    THETA("Theta", 0xF8F8F2, 1.00, 1.00),
    IOTA("Iota", 0xB2FF59, 1.00, 1.00),
    KAPPA("Kappa", 0xFF80AB, 1.00, 1.00),
    LAMBDA("Lambda", 0x8C9EFF, 1.00, 1.00),
    MU("Mu", 0xFFAB40, 1.00, 1.00),
    NU("Nu", 0x64FFDA, 1.00, 1.00),
    XI("Xi", 0xEA80FC, 1.00, 1.00),
    OMICRON("Omicron", 0xCCFF90, 1.00, 1.00),
    PI("Pi", 0x82B1FF, 1.00, 1.00);

    /** Maximum number of species the simulation supports. */
    public static final int MAX_SPECIES = values().length;

    private final String displayName;
    private final int defaultColorRgb;
    private final double defaultMass;
    private final double defaultRadius;

    SpeciesType(String displayName, int defaultColorRgb, double defaultMass, double defaultRadius) {
        this.displayName = displayName;
        this.defaultColorRgb = defaultColorRgb;
        this.defaultMass = defaultMass;
        this.defaultRadius = defaultRadius;
    }

    /** Human-readable default name (e.g. {@code "Alpha"}). */
    public String displayName() {
        return displayName;
    }

    /** Default color as packed 24-bit RGB ({@code 0xRRGGBB}). */
    public int defaultColorRgb() {
        return defaultColorRgb;
    }

    /** Default particle mass for this species. */
    public double defaultMass() {
        return defaultMass;
    }

    /** Default particle radius (world units) for this species. */
    public double defaultRadius() {
        return defaultRadius;
    }

    /** Returns the {@code index}-th type; {@code index} must be within {@code [0, MAX_SPECIES)}. */
    public static SpeciesType byIndex(int index) {
        return values()[index];
    }
}
