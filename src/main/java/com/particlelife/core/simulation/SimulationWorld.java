package com.particlelife.core.simulation;

import com.particlelife.core.physics.PhysicsSettings;
import com.particlelife.forces.AttractionMatrix;
import com.particlelife.math.DeterministicRandom;
import com.particlelife.math.Vector3;
import com.particlelife.particle.ParticleStore;
import com.particlelife.species.Species;
import com.particlelife.species.SpeciesRegistry;

import java.util.List;

/**
 * The complete mutable state of one simulation: particles, species,
 * attraction matrix, and both settings objects.
 *
 * <p>Owns spawning. Spawn positions are drawn from a
 * {@link DeterministicRandom} stream derived from the seed, so a world is
 * fully reproducible from {@code (seed, settings, matrix)}.
 *
 * <p>Mutations (respawn, species count changes) must happen on the engine
 * thread — the UI submits them through the engine's command queue.
 */
public final class SimulationWorld {

    private final SimulationSettings simulationSettings;
    private final PhysicsSettings physicsSettings;
    private final SpeciesRegistry speciesRegistry;
    private final AttractionMatrix matrix;
    private final ParticleStore store;

    public SimulationWorld(SimulationSettings simulationSettings, PhysicsSettings physicsSettings) {
        this.simulationSettings = simulationSettings;
        this.physicsSettings = physicsSettings;
        this.speciesRegistry = new SpeciesRegistry(simulationSettings.speciesCount());
        this.matrix = new AttractionMatrix(simulationSettings.speciesCount());
        this.store = new ParticleStore(SimulationSettings.MAX_PARTICLES);
    }

    public SimulationSettings simulationSettings() {
        return simulationSettings;
    }

    public PhysicsSettings physicsSettings() {
        return physicsSettings;
    }

    public SpeciesRegistry species() {
        return speciesRegistry;
    }

    public AttractionMatrix matrix() {
        return matrix;
    }

    public ParticleStore store() {
        return store;
    }

    /**
     * Clears and respawns {@link SimulationSettings#particleCount()}
     * particles of the enabled species, uniformly distributed in a sphere of
     * {@code spawnRadiusFraction * worldSize/2} around the origin. Species
     * are assigned in even rotation so populations are balanced.
     */
    public void respawn() {
        store.clear();
        List<Species> enabled = speciesRegistry.enabled();
        if (enabled.isEmpty()) {
            return;
        }
        DeterministicRandom rng = new DeterministicRandom(simulationSettings.seed());
        double radius = physicsSettings.worldSize() * 0.5 * simulationSettings.spawnRadiusFraction();
        Vector3 pos = new Vector3();
        int target = simulationSettings.particleCount();
        for (int i = 0; i < target; i++) {
            Species species = enabled.get(i % enabled.size());
            rng.nextInSphere(radius, pos);
            store.spawn(species.index(), pos.x, pos.y, pos.z, species.mass(), species.radius());
        }
    }

    /**
     * Applies a new species count: resizes the registry and the matrix
     * (preserving the overlapping block) and clamps the settings value.
     * Does <em>not</em> respawn — callers decide when to repopulate.
     */
    public void setSpeciesCount(int count) {
        simulationSettings.setSpeciesCount(count);
        int clamped = simulationSettings.speciesCount();
        speciesRegistry.setCount(clamped);
        matrix.resize(clamped);
    }

    /**
     * Removes particles whose species is disabled (used after an
     * enable/disable toggle without a full respawn).
     */
    public void cullDisabledSpecies() {
        for (int i = store.count() - 1; i >= 0; i--) {
            if (!speciesRegistry.get(store.speciesIndex(i)).isEnabled()) {
                store.kill(i);
            }
        }
    }
}
