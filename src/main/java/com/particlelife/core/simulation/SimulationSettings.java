package com.particlelife.core.simulation;

import com.particlelife.math.MathUtils;
import com.particlelife.species.SpeciesType;

/**
 * Live-tunable simulation (non-physics) parameters: population, spawning,
 * and time scaling. Same concurrency contract as
 * {@link com.particlelife.core.physics.PhysicsSettings}: volatile fields,
 * clamping setters, written by the UI and read by the engine loop.
 */
public final class SimulationSettings {

    public static final int MAX_PARTICLES = 50_000;
    public static final int DEFAULT_PARTICLE_COUNT = 2000;
    public static final int DEFAULT_SPECIES_COUNT = 6;
    public static final long DEFAULT_SEED = 42L;
    public static final double DEFAULT_SPAWN_RADIUS_FRACTION = 0.8;
    public static final double DEFAULT_TIME_SCALE = 1.0;

    private volatile int particleCount = DEFAULT_PARTICLE_COUNT;
    private volatile int speciesCount = DEFAULT_SPECIES_COUNT;
    private volatile long seed = DEFAULT_SEED;
    private volatile double spawnRadiusFraction = DEFAULT_SPAWN_RADIUS_FRACTION;
    private volatile double timeScale = DEFAULT_TIME_SCALE;

    /** Target number of particles for the next (re)spawn. */
    public int particleCount() {
        return particleCount;
    }

    public void setParticleCount(int count) {
        this.particleCount = MathUtils.clamp(count, 1, MAX_PARTICLES);
    }

    /** Number of species in play. */
    public int speciesCount() {
        return speciesCount;
    }

    public void setSpeciesCount(int count) {
        this.speciesCount = MathUtils.clamp(count, 1, SpeciesType.MAX_SPECIES);
    }

    /** Seed for particle spawning; the same seed reproduces the same world. */
    public long seed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    /** Spawn sphere radius as a fraction of the world half-size, {@code (0, 1]}. */
    public double spawnRadiusFraction() {
        return spawnRadiusFraction;
    }

    public void setSpawnRadiusFraction(double fraction) {
        this.spawnRadiusFraction = MathUtils.clamp(fraction, 0.05, 1.0);
    }

    /** Simulation-seconds per real second (speed multiplier). */
    public double timeScale() {
        return timeScale;
    }

    public void setTimeScale(double timeScale) {
        this.timeScale = MathUtils.clamp(timeScale, 0.05, 10.0);
    }

    /** Restores all defaults (seed included). */
    public void resetToDefaults() {
        particleCount = DEFAULT_PARTICLE_COUNT;
        speciesCount = DEFAULT_SPECIES_COUNT;
        seed = DEFAULT_SEED;
        spawnRadiusFraction = DEFAULT_SPAWN_RADIUS_FRACTION;
        timeScale = DEFAULT_TIME_SCALE;
    }
}
