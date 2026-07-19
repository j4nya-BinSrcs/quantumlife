package com.particlelife.core.physics;

import com.particlelife.forces.AttractionMatrix;
import com.particlelife.math.DeterministicRandom;
import com.particlelife.math.Vector3;
import com.particlelife.particle.ParticleStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration-level physics tests: whole steps through the engine, checking
 * stability, determinism, and boundary containment over many frames.
 */
class PhysicsEngineTest {

    private static ParticleStore randomStore(int count, double worldSize, int speciesCount, long seed) {
        DeterministicRandom rng = new DeterministicRandom(seed);
        ParticleStore store = new ParticleStore(count);
        double half = worldSize / 2;
        for (int i = 0; i < count; i++) {
            store.spawn(rng.nextInt(speciesCount),
                    rng.nextDouble(-half, half), rng.nextDouble(-half, half),
                    rng.nextDouble(-half, half), 1.0, 1.0);
        }
        return store;
    }

    @Test
    void remainsNumericallyStableOverManySteps() {
        PhysicsSettings settings = new PhysicsSettings();
        settings.setWorldSize(150.0);
        settings.setInteractionRadius(20.0);
        settings.setForceMultiplier(5.0); // deliberately hot
        PhysicsEngine engine = new PhysicsEngine(settings);

        AttractionMatrix matrix = new AttractionMatrix(5);
        matrix.randomize(new DeterministicRandom(3L));
        ParticleStore store = randomStore(500, 150.0, 5, 4L);

        for (int step = 0; step < 600; step++) {
            engine.step(store, matrix, settings.timeStep());
        }

        double maxSpeed = settings.maxVelocity();
        Vector3 v = new Vector3();
        for (int i = 0; i < store.count(); i++) {
            store.position(i, v);
            assertTrue(v.isFinite(), "position went non-finite at particle " + i);
            assertTrue(Math.abs(v.x) <= 75.0 && Math.abs(v.y) <= 75.0 && Math.abs(v.z) <= 75.0,
                    "wrap boundary must contain particles: " + v);
            store.velocity(i, v);
            assertTrue(v.isFinite(), "velocity went non-finite at particle " + i);
            assertTrue(v.length() <= maxSpeed + 1e-6, "speed cap violated: " + v.length());
        }
    }

    @Test
    void extremeSettingsDoNotExplode() {
        PhysicsSettings settings = new PhysicsSettings();
        settings.setWorldSize(60.0);
        settings.setInteractionRadius(30.0); // coarse grid -> brute-force path
        settings.setForceMultiplier(50.0);
        settings.setFrictionHalfLife(0.001);
        settings.setTimeStep(0.1);
        settings.setBoundaryType(BoundaryType.BOUNCE);
        PhysicsEngine engine = new PhysicsEngine(settings);

        AttractionMatrix matrix = new AttractionMatrix(3);
        matrix.randomize(new DeterministicRandom(8L));
        ParticleStore store = randomStore(200, 60.0, 3, 9L);

        for (int step = 0; step < 200; step++) {
            engine.step(store, matrix, settings.timeStep());
        }

        Vector3 v = new Vector3();
        for (int i = 0; i < store.count(); i++) {
            assertTrue(store.position(i, v).isFinite());
            assertTrue(store.velocity(i, v).isFinite());
        }
    }

    @Test
    void identicalSeedsProduceIdenticalTrajectories() {
        PhysicsSettings settingsA = new PhysicsSettings();
        PhysicsSettings settingsB = new PhysicsSettings();
        PhysicsEngine engineA = new PhysicsEngine(settingsA);
        PhysicsEngine engineB = new PhysicsEngine(settingsB);

        AttractionMatrix matrixA = new AttractionMatrix(4);
        AttractionMatrix matrixB = new AttractionMatrix(4);
        matrixA.randomize(new DeterministicRandom(77L));
        matrixB.randomize(new DeterministicRandom(77L));

        ParticleStore storeA = randomStore(600, settingsA.worldSize(), 4, 55L);
        ParticleStore storeB = randomStore(600, settingsB.worldSize(), 4, 55L);

        for (int step = 0; step < 120; step++) {
            engineA.step(storeA, matrixA, settingsA.timeStep());
            engineB.step(storeB, matrixB, settingsB.timeStep());
        }

        for (int i = 0; i < storeA.count() * 3; i++) {
            assertEquals(storeA.positions()[i], storeB.positions()[i], 0.0,
                    "determinism violated at component " + i
                            + " (parallel force pass must not affect results)");
        }
    }

    @Test
    void engineAdaptsWhenSettingsChangeMidRun() {
        PhysicsSettings settings = new PhysicsSettings();
        PhysicsEngine engine = new PhysicsEngine(settings);
        AttractionMatrix matrix = new AttractionMatrix(3);
        matrix.randomize(new DeterministicRandom(6L));
        ParticleStore store = randomStore(300, settings.worldSize(), 3, 7L);

        for (int step = 0; step < 30; step++) {
            engine.step(store, matrix, settings.timeStep());
        }
        // Live-edit everything the strategy cache keys on.
        settings.setWorldSize(120.0);
        settings.setInteractionRadius(40.0);
        settings.setBoundaryType(BoundaryType.BOUNCE);
        settings.setBeta(0.5);
        for (int step = 0; step < 30; step++) {
            engine.step(store, matrix, settings.timeStep());
        }

        Vector3 v = new Vector3();
        for (int i = 0; i < store.count(); i++) {
            assertTrue(store.position(i, v).isFinite());
        }
    }

    @Test
    void restingClusterStaysBoundedUnderMutualAttraction() {
        // Two mutually attracting particles must settle near the kernel's
        // zero crossing (distance = beta * rMax), not oscillate divergently.
        PhysicsSettings settings = new PhysicsSettings();
        settings.setWorldSize(200.0);
        settings.setInteractionRadius(20.0);
        settings.setBoundaryType(BoundaryType.OPEN);
        PhysicsEngine engine = new PhysicsEngine(settings);

        AttractionMatrix matrix = new AttractionMatrix(1);
        matrix.set(0, 0, 1.0);
        ParticleStore store = new ParticleStore(2);
        store.spawn(0, -8, 0, 0, 1, 1);
        store.spawn(0, 8, 0, 0, 1, 1);

        for (int step = 0; step < 2000; step++) {
            engine.step(store, matrix, settings.timeStep());
        }

        Vector3 a = store.position(0, new Vector3());
        Vector3 b = store.position(1, new Vector3());
        double distance = a.distanceTo(b);
        double equilibrium = settings.beta() * settings.interactionRadius();
        assertTrue(Math.abs(distance - equilibrium) < equilibrium * 0.25,
                "expected settling near %.2f, got %.2f".formatted(equilibrium, distance));
    }
}
