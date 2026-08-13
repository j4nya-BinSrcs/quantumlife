package com.particlelife.serialization;

import com.particlelife.core.physics.BoundaryType;
import com.particlelife.core.physics.ComputeBackend;
import com.particlelife.core.physics.PhysicsSettings;
import com.particlelife.core.simulation.SimulationSettings;
import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.forces.ForceFunctionType;
import com.particlelife.species.Species;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps between the live {@link SimulationWorld} and the serializable
 * {@link PresetData} — the single place that knows both shapes, so schema
 * evolution touches one class.
 *
 * <p>{@link #apply} must run on the engine thread (it mutates the world);
 * {@link #capture} only reads and may run anywhere the world is quiescent.
 */
public final class WorldMapper {

    private WorldMapper() {
    }

    /** Captures the world's current configuration as a preset payload. */
    public static PresetData capture(SimulationWorld world) {
        List<PresetData.SpeciesData> species = new ArrayList<>();
        for (Species s : world.species().all()) {
            species.add(new PresetData.SpeciesData(
                    s.name(), s.colorRgb(), s.mass(), s.radius(), s.isEnabled()));
        }
        PhysicsSettings p = world.physicsSettings();
        SimulationSettings s = world.simulationSettings();
        return new PresetData(
                species,
                world.matrix().toArray(),
                world.matrix().isSymmetric(),
                new PresetData.PhysicsData(
                        p.worldSize(), p.interactionRadius(), p.beta(), p.forceMultiplier(),
                        p.frictionHalfLife(), p.maxVelocity(), p.timeStep(), p.damping(),
                        p.boundaryType().name(), p.forceFunctionType().name(),
                        p.computeBackend().name()),
                new PresetData.SimulationData(
                        s.particleCount(), s.speciesCount(), s.seed(),
                        s.spawnRadiusFraction(), s.timeScale()));
    }

    /**
     * Applies a preset payload to the world (settings, species, matrix) and
     * respawns. Unknown enum names and missing sections fall back to current
     * values, so presets from newer versions degrade gracefully.
     */
    public static void apply(PresetData data, SimulationWorld world) {
        SimulationSettings sim = world.simulationSettings();
        if (data.simulation() != null) {
            sim.setParticleCount(data.simulation().particleCount());
            sim.setSpeciesCount(data.simulation().speciesCount());
            sim.setSeed(data.simulation().seed());
            sim.setSpawnRadiusFraction(data.simulation().spawnRadiusFraction());
            sim.setTimeScale(data.simulation().timeScale());
        }

        PhysicsSettings phys = world.physicsSettings();
        if (data.physics() != null) {
            PresetData.PhysicsData p = data.physics();
            phys.setWorldSize(p.worldSize());
            phys.setInteractionRadius(p.interactionRadius());
            phys.setBeta(p.beta());
            phys.setForceMultiplier(p.forceMultiplier());
            phys.setFrictionHalfLife(p.frictionHalfLife());
            phys.setMaxVelocity(p.maxVelocity());
            phys.setTimeStep(p.timeStep());
            phys.setDamping(p.damping());
            phys.setBoundaryType(parseEnum(BoundaryType.class, p.boundaryType(), phys.boundaryType()));
            phys.setForceFunctionType(
                    parseEnum(ForceFunctionType.class, p.forceFunctionType(), phys.forceFunctionType()));
            phys.setComputeBackend(
                    parseEnum(ComputeBackend.class, p.computeBackend(), phys.computeBackend()));
        }

        int speciesCount = data.species() != null && !data.species().isEmpty()
                ? Math.min(data.species().size(), com.particlelife.species.SpeciesType.MAX_SPECIES)
                : sim.speciesCount();
        world.setSpeciesCount(speciesCount);
        if (data.species() != null && data.species().size() >= speciesCount) {
            for (int i = 0; i < speciesCount; i++) {
                PresetData.SpeciesData sd = data.species().get(i);
                Species target = world.species().get(i);
                if (sd.name() != null) {
                    target.setName(sd.name());
                }
                target.setColorRgb(sd.colorRgb());
                if (sd.mass() > 0) {
                    target.setMass(sd.mass());
                }
                if (sd.radius() > 0) {
                    target.setRadius(sd.radius());
                }
                target.setEnabled(sd.enabled());
            }
        }

        if (data.matrix() != null && data.matrix().length > 0) {
            world.matrix().setSymmetric(false);
            double[][] matrix = data.matrix();
            // Fit the imported matrix into the (possibly different) species count.
            double[][] fitted = new double[speciesCount][speciesCount];
            for (int i = 0; i < Math.min(speciesCount, matrix.length); i++) {
                for (int j = 0; j < Math.min(speciesCount, matrix[i].length); j++) {
                    fitted[i][j] = matrix[i][j];
                }
            }
            world.matrix().setFrom(fitted);
            world.matrix().setSymmetric(data.matrixSymmetric());
        }

        world.respawn();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name, E fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
