package com.particlelife.core.simulation;

import com.particlelife.core.physics.PhysicsSettings;
import com.particlelife.math.Vector3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationWorldTest {

    private SimulationWorld world;

    @BeforeEach
    void setUp() {
        world = new SimulationWorld(new SimulationSettings(), new PhysicsSettings());
    }

    @Test
    void respawnCreatesConfiguredParticleCount() {
        world.simulationSettings().setParticleCount(500);
        world.respawn();
        assertEquals(500, world.store().count());
    }

    @Test
    void respawnBalancesSpeciesPopulations() {
        world.simulationSettings().setParticleCount(600);
        world.simulationSettings().setSpeciesCount(6);
        world.setSpeciesCount(6);
        world.respawn();

        Map<Integer, Integer> populations = new HashMap<>();
        for (int i = 0; i < world.store().count(); i++) {
            populations.merge(world.store().speciesIndex(i), 1, Integer::sum);
        }
        assertEquals(6, populations.size());
        populations.values().forEach(count -> assertEquals(100, count));
    }

    @Test
    void respawnIsDeterministicPerSeed() {
        world.simulationSettings().setSeed(1234L);
        world.simulationSettings().setParticleCount(200);
        world.respawn();
        double[] first = world.store().positions().clone();

        world.respawn();
        for (int i = 0; i < 200 * 3; i++) {
            assertEquals(first[i], world.store().positions()[i], 0.0);
        }

        world.simulationSettings().setSeed(9999L);
        world.respawn();
        boolean anyDifferent = false;
        for (int i = 0; i < 200 * 3 && !anyDifferent; i++) {
            anyDifferent = first[i] != world.store().positions()[i];
        }
        assertTrue(anyDifferent, "different seed must give a different world");
    }

    @Test
    void respawnConfinesParticlesToSpawnSphere() {
        world.simulationSettings().setParticleCount(300);
        world.simulationSettings().setSpawnRadiusFraction(0.5);
        world.respawn();

        double maxRadius = world.physicsSettings().worldSize() * 0.5 * 0.5;
        Vector3 pos = new Vector3();
        for (int i = 0; i < world.store().count(); i++) {
            world.store().position(i, pos);
            assertTrue(pos.length() <= maxRadius + 1e-9,
                    "particle outside spawn sphere: " + pos.length());
        }
    }

    @Test
    void respawnSkipsDisabledSpecies() {
        world.setSpeciesCount(4);
        world.species().get(2).setEnabled(false);
        world.simulationSettings().setParticleCount(300);
        world.respawn();

        assertEquals(300, world.store().count());
        for (int i = 0; i < world.store().count(); i++) {
            assertTrue(world.store().speciesIndex(i) != 2, "disabled species spawned");
        }
    }

    @Test
    void respawnWithAllSpeciesDisabledYieldsEmptyWorld() {
        world.species().all().forEach(s -> s.setEnabled(false));
        world.respawn();
        assertEquals(0, world.store().count());
    }

    @Test
    void setSpeciesCountKeepsRegistryAndMatrixInSync() {
        world.setSpeciesCount(9);
        assertEquals(9, world.species().count());
        assertEquals(9, world.matrix().size());

        world.setSpeciesCount(3);
        assertEquals(3, world.species().count());
        assertEquals(3, world.matrix().size());
    }

    @Test
    void cullDisabledSpeciesRemovesOnlyThose() {
        world.setSpeciesCount(3);
        world.simulationSettings().setParticleCount(300);
        world.respawn();

        world.species().get(1).setEnabled(false);
        world.cullDisabledSpecies();

        assertEquals(200, world.store().count());
        for (int i = 0; i < world.store().count(); i++) {
            assertTrue(world.store().speciesIndex(i) != 1);
        }
    }

    @Test
    void useOfSpeciesMassAndRadiusAtSpawn() {
        world.setSpeciesCount(2);
        world.species().get(0).setMass(2.5);
        world.species().get(0).setRadius(0.8);
        world.simulationSettings().setParticleCount(10);
        world.respawn();

        for (int i = 0; i < world.store().count(); i++) {
            if (world.store().speciesIndex(i) == 0) {
                assertEquals(2.5, world.store().masses()[i], 1e-12);
                assertEquals(0.8, world.store().radii()[i], 1e-12);
            }
        }
    }
}
