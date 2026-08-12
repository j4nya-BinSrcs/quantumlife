package com.particlelife.testing;

import com.particlelife.core.physics.PhysicsEngine;
import com.particlelife.core.physics.PhysicsSettings;
import com.particlelife.core.simulation.SimulationSettings;
import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.math.DeterministicRandom;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance benchmarks for the physics step at the populations named in
 * the requirements (500 / 1k / 5k / 10k). Tagged {@code performance} and
 * excluded from the normal test run; execute with {@code ./gradlew perfTest}.
 *
 * <p>Thresholds are deliberately generous (CI machines vary wildly) — their
 * purpose is to catch order-of-magnitude regressions such as an accidental
 * O(N²) path, not to benchmark precisely.
 */
@Tag("performance")
class PerformanceBenchmarkTest {

    private static final int WARMUP_STEPS = 60;
    private static final int MEASURED_STEPS = 120;

    private double millisPerStep(int particles) {
        PhysicsSettings physics = new PhysicsSettings();
        SimulationSettings simulation = new SimulationSettings();
        simulation.setParticleCount(particles);
        SimulationWorld world = new SimulationWorld(simulation, physics);
        world.matrix().randomize(new DeterministicRandom(1L));
        world.respawn();
        PhysicsEngine engine = new PhysicsEngine(physics);

        for (int i = 0; i < WARMUP_STEPS; i++) {
            engine.step(world.store(), world.matrix(), physics.timeStep());
        }
        long begin = System.nanoTime();
        for (int i = 0; i < MEASURED_STEPS; i++) {
            engine.step(world.store(), world.matrix(), physics.timeStep());
        }
        double millis = (System.nanoTime() - begin) / 1e6 / MEASURED_STEPS;
        System.out.printf("%,6d particles: %8.3f ms/step (%,.0f steps/s)%n",
                particles, millis, 1000.0 / millis);
        return millis;
    }

    /**
     * Best-of-3 measurement. The small populations used as scaling baselines
     * step in a fraction of a millisecond, so a single sample is dominated by
     * timer/scheduler noise; the minimum of a few samples filters those spikes
     * while staying immune to warmup stalls.
     */
    private double bestMillisPerStep(int particles) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            best = Math.min(best, millisPerStep(particles));
        }
        return best;
    }

    @Test
    void benchmark500() {
        assertTrue(millisPerStep(500) < 8.0);
    }

    @Test
    void benchmark1000() {
        assertTrue(millisPerStep(1000) < 16.0);
    }

    @Test
    void benchmark5000() {
        assertTrue(millisPerStep(5000) < 50.0);
    }

    @Test
    void benchmark10000() {
        assertTrue(millisPerStep(10_000) < 100.0);
    }

    @Test
    void gridScalesFarBetterThanQuadratic() {
        double t1k = bestMillisPerStep(1000);
        double t10k = bestMillisPerStep(10_000);
        // With the world size fixed, population growth raises local density and
        // therefore the mean neighbor count, so even a correct grid accumulates
        // force pairs super-linearly (measured ~40-60x for 10x particles here).
        // An accidental O(N²) neighbor search — no spatial pruning at all —
        // reads ~100x. The 75x threshold separates the two regimes with clear
        // margin even under best-of-3 timing noise on a busy box.
        assertTrue(t10k < t1k * 75,
                "10x particles cost %.1fx — neighbor search may have regressed to O(N^2)"
                        .formatted(t10k / t1k));
    }
}
