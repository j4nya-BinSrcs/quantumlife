package com.particlelife.presets;

import com.particlelife.core.physics.BoundaryType;
import com.particlelife.core.physics.PhysicsSettings;
import com.particlelife.core.simulation.SimulationSettings;
import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.database.repository.PresetRepository;
import com.particlelife.math.DeterministicRandom;
import com.particlelife.serialization.PresetData;
import com.particlelife.serialization.WorldMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Curated starter presets seeded into the database on first run, so a new
 * install opens with interesting worlds one click away. Existing presets
 * with the same names are never overwritten.
 */
public final class BuiltInPresets {

    private static final Logger LOG = LoggerFactory.getLogger(BuiltInPresets.class);

    private BuiltInPresets() {
    }

    /** Inserts any missing built-in presets into {@code repository}. */
    public static void seed(PresetRepository repository) {
        for (Map.Entry<String, PresetData> entry : build().entrySet()) {
            if (!repository.exists(entry.getKey())) {
                repository.save(entry.getKey(), entry.getValue());
                LOG.info("Seeded built-in preset '{}'", entry.getKey());
            }
        }
    }

    private static Map<String, PresetData> build() {
        Map<String, PresetData> presets = new LinkedHashMap<>();
        presets.put("Primordial Soup", primordialSoup());
        presets.put("Predator Chains", predatorChains());
        presets.put("Cell Clusters", cellClusters());
        return presets;
    }

    /** Fully random asymmetric matrix — classic chaotic Particle Life. */
    private static PresetData primordialSoup() {
        SimulationWorld world = blankWorld(6, 3000);
        world.matrix().randomize(new DeterministicRandom(20260101L));
        return WorldMapper.capture(world);
    }

    /**
     * Cyclic food chain: each species chases the next and flees the
     * previous — produces long pursuing streams and rotating vortices.
     */
    private static PresetData predatorChains() {
        SimulationWorld world = blankWorld(5, 2500);
        int n = world.matrix().size();
        for (int i = 0; i < n; i++) {
            world.matrix().set(i, i, 0.35);                 // mild self-cohesion
            world.matrix().set(i, (i + 1) % n, 0.9);        // chase the next
            world.matrix().set((i + 1) % n, i, -0.7);       // ...which flees
        }
        return WorldMapper.capture(world);
    }

    /**
     * Strong self-attraction with cross-species repulsion — separates into
     * distinct quivering cell-like blobs.
     */
    private static PresetData cellClusters() {
        SimulationWorld world = blankWorld(4, 2400);
        int n = world.matrix().size();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                world.matrix().set(i, j, i == j ? 0.85 : -0.25);
            }
        }
        world.physicsSettings().setBoundaryType(BoundaryType.BOUNCE);
        return WorldMapper.capture(world);
    }

    private static SimulationWorld blankWorld(int speciesCount, int particleCount) {
        SimulationWorld world = new SimulationWorld(new SimulationSettings(), new PhysicsSettings());
        world.setSpeciesCount(speciesCount);
        world.simulationSettings().setParticleCount(particleCount);
        return world;
    }
}
