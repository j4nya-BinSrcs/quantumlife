package com.particlelife.core.engine;

import com.particlelife.core.physics.PhysicsEngine;
import com.particlelife.core.physics.PhysicsSettings;
import com.particlelife.core.simulation.SimulationSettings;
import com.particlelife.core.simulation.SimulationState;
import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.events.EventBus;
import com.particlelife.events.SimulationEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
class SimulationEngineTest {

    private EventBus eventBus;
    private SimulationWorld world;
    private SimulationEngine engine;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        SimulationSettings simulationSettings = new SimulationSettings();
        simulationSettings.setParticleCount(200);
        PhysicsSettings physicsSettings = new PhysicsSettings();
        world = new SimulationWorld(simulationSettings, physicsSettings);
        world.respawn();
        engine = new SimulationEngine(world, new PhysicsEngine(physicsSettings), eventBus);
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void startsPausedWithoutStepping() throws Exception {
        engine.start();
        Thread.sleep(100);
        assertEquals(SimulationState.PAUSED, engine.state());
        assertEquals(0, engine.snapshot().frame());
    }

    @Test
    void playAdvancesFramesContinuously() throws Exception {
        engine.start();
        engine.play();
        waitForFrames(10, 5000);
        assertEquals(SimulationState.RUNNING, engine.state());
        assertTrue(engine.snapshot().frame() >= 10);
    }

    @Test
    void pauseHaltsStepping() throws Exception {
        engine.start();
        engine.play();
        waitForFrames(5, 5000);
        engine.pause();
        Thread.sleep(80); // let an in-flight frame settle
        long frozen = engine.snapshot().frame();
        Thread.sleep(200);
        assertEquals(frozen, engine.snapshot().frame(), "no frames may pass while paused");
    }

    @Test
    void stepOnceAdvancesExactlyOneFrameWhilePaused() throws Exception {
        engine.start();
        long before = engine.snapshot().frame();
        engine.stepOnce();
        waitForFrames(before + 1, 5000);
        Thread.sleep(150);
        assertEquals(before + 1, engine.snapshot().frame());
    }

    @Test
    void snapshotContainsSpawnedParticles() throws Exception {
        engine.start();
        engine.stepOnce();
        waitForFrames(1, 5000);

        float[] positions = new float[world.store().capacity() * 3];
        int[] species = new int[world.store().capacity()];
        int count = engine.snapshot().readInto(positions, species);
        assertEquals(200, count);
    }

    @Test
    void submittedMutationsRunBetweenSteps() throws Exception {
        engine.start();
        engine.play();
        waitForFrames(3, 5000);

        CountDownLatch done = new CountDownLatch(1);
        engine.submit(() -> {
            world.simulationSettings().setSeed(7L);
            world.respawn();
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(200, world.store().count());
    }

    @Test
    void publishesStateChangeEvents() throws Exception {
        List<SimulationEvent.StateChanged> events = new CopyOnWriteArrayList<>();
        eventBus.subscribe(SimulationEvent.StateChanged.class, events::add);

        engine.start();
        engine.play();
        waitForFrames(1, 5000);
        engine.pause();

        deadline(() -> events.size() >= 2, 5000);
        assertEquals(SimulationState.RUNNING, events.get(0).state());
        assertEquals(SimulationState.PAUSED, events.get(1).state());
    }

    @Test
    void publishesStatsWhileRunning() throws Exception {
        List<SimulationEvent.StatsUpdated> stats = new CopyOnWriteArrayList<>();
        eventBus.subscribe(SimulationEvent.StatsUpdated.class, stats::add);

        engine.start();
        engine.play();
        deadline(() -> !stats.isEmpty(), 10_000);

        SimulationEvent.StatsUpdated sample = stats.get(0);
        assertTrue(sample.stepsPerSecond() > 0);
        assertEquals(200, sample.particleCount());
    }

    @Test
    void closeStopsLoopAndIsIdempotent() {
        engine.start();
        engine.play();
        engine.close();
        assertEquals(SimulationState.STOPPED, engine.state());
        engine.close(); // second close must be a no-op
    }

    private void waitForFrames(long target, long timeoutMillis) throws InterruptedException {
        deadline(() -> engine.snapshot().frame() >= target, timeoutMillis);
    }

    private void deadline(java.util.function.BooleanSupplier condition, long timeoutMillis)
            throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("condition not met within " + timeoutMillis + "ms");
            }
            Thread.sleep(10);
        }
    }
}
