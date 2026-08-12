package com.particlelife.species;

import com.particlelife.math.DeterministicRandom;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns the live list of species for a simulation.
 *
 * <p>The registry always holds {@code count} species with contiguous indices
 * {@code 0..count-1} so matrix rows and species line up by construction.
 * Growing re-seeds new slots from their {@link SpeciesType} archetype;
 * shrinking discards state of the removed slots.
 *
 * <p>The backing list is {@link CopyOnWriteArrayList}: the UI reads
 * {@link #all()} from the FX thread while the engine thread mutates the
 * registry (species-count changes), so iteration must never see a partial
 * add/remove. Growth/shrink is rare, so the copy cost is negligible.
 */
public final class SpeciesRegistry {

    /** Golden-angle hue stepping gives maximally separated random colors. */
    private static final double GOLDEN_ANGLE_DEGREES = 137.507764;

    private final List<Species> species = new CopyOnWriteArrayList<>();

    /** Creates a registry with {@code count} species seeded from archetype defaults. */
    public SpeciesRegistry(int count) {
        setCount(count);
    }

    /** Number of active species. */
    public int count() {
        return species.size();
    }

    /**
     * Grows or shrinks the species list to {@code count}
     * ({@code 1..SpeciesType.MAX_SPECIES}). Existing species keep their
     * customizations; new slots get archetype defaults.
     */
    public void setCount(int count) {
        if (count < 1 || count > SpeciesType.MAX_SPECIES) {
            throw new IllegalArgumentException(
                    "species count must be in [1, %d]: %d".formatted(SpeciesType.MAX_SPECIES, count));
        }
        while (species.size() > count) {
            species.remove(species.size() - 1);
        }
        while (species.size() < count) {
            species.add(Species.fromType(species.size()));
        }
    }

    /** Returns the species at {@code index}. */
    public Species get(int index) {
        return species.get(index);
    }

    /** Read-only view of all species in index order. */
    public List<Species> all() {
        return Collections.unmodifiableList(species);
    }

    /** Species currently enabled, in index order. */
    public List<Species> enabled() {
        return species.stream().filter(Species::isEnabled).toList();
    }

    /**
     * Assigns every species a random, perceptually well-separated color by
     * stepping the hue wheel by the golden angle from a random start.
     */
    public void randomizeColors(DeterministicRandom rng) {
        double hue = rng.nextDouble() * 360.0;
        for (Species s : species) {
            s.setColorRgb(hsvToRgb(hue, 0.75, 1.0));
            hue = (hue + GOLDEN_ANGLE_DEGREES) % 360.0;
        }
    }

    /** Resets every species to its archetype defaults. */
    public void resetAllToDefaults() {
        species.forEach(Species::resetToDefaults);
    }

    /**
     * Converts HSV to packed 24-bit RGB. {@code h} in degrees,
     * {@code s}, {@code v} in {@code [0, 1]}.
     */
    static int hsvToRgb(double h, double s, double v) {
        double c = v * s;
        double hp = (h % 360.0 + 360.0) % 360.0 / 60.0;
        double x = c * (1.0 - Math.abs(hp % 2.0 - 1.0));
        double r;
        double g;
        double b;
        switch ((int) hp) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        double m = v - c;
        int ri = (int) Math.round((r + m) * 255.0);
        int gi = (int) Math.round((g + m) * 255.0);
        int bi = (int) Math.round((b + m) * 255.0);
        return (ri << 16) | (gi << 8) | bi;
    }
}
