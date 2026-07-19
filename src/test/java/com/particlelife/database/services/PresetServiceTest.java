package com.particlelife.database.services;

import com.particlelife.core.engine.SimulationEngine;
import com.particlelife.core.physics.PhysicsEngine;
import com.particlelife.core.physics.PhysicsSettings;
import com.particlelife.core.simulation.SimulationSettings;
import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.database.Database;
import com.particlelife.database.repository.SqlitePresetRepository;
import com.particlelife.events.EventBus;
import com.particlelife.events.SimulationEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests: service + real repository + real engine thread.
 */
@Timeout(30)
class PresetServiceTest {

    private Database database;
    private SimulationEngine engine;
    private SimulationWorld world;
    private EventBus eventBus;
    private PresetService service;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        eventBus = new EventBus();
        PhysicsSettings physicsSettings = new PhysicsSettings();
        world = new SimulationWorld(new SimulationSettings(), physicsSettings);
        world.respawn();
        engine = new SimulationEngine(world, new PhysicsEngine(physicsSettings), eventBus);
        engine.start();
        service = new PresetService(new SqlitePresetRepository(database), engine, eventBus);
    }

    @AfterEach
    void tearDown() {
        engine.close();
        database.close();
    }

    @Test
    void saveCurrentCapturesLiveWorldState() throws Exception {
        engine.submit(() -> {
            world.simulationSettings().setParticleCount(4321);
            world.matrix().set(0, 1, 0.62);
        });

        var preset = service.saveCurrent("Snapshot").get(10, TimeUnit.SECONDS);

        assertEquals("Snapshot", preset.name());
        assertEquals(4321, preset.data().simulation().particleCount());
        assertEquals(0.62, preset.data().matrix()[0][1], 1e-12);
    }

    @Test
    void loadAppliesPresetAndRespawns() throws Exception {
        engine.submit(() -> world.matrix().set(2, 3, -0.4));
        service.saveCurrent("ToRestore").get(10, TimeUnit.SECONDS);

        engine.submit(() -> {
            world.matrix().reset();
            world.simulationSettings().setParticleCount(50);
            world.respawn();
        });

        assertTrue(service.load("ToRestore").get(10, TimeUnit.SECONDS));
        sync();
        assertEquals(-0.4, world.matrix().get(2, 3), 1e-12);
        assertEquals(SimulationSettings.DEFAULT_PARTICLE_COUNT, world.store().count());
    }

    @Test
    void loadMissingPresetReturnsFalse() throws Exception {
        assertFalse(service.load("Ghost").get(10, TimeUnit.SECONDS));
    }

    @Test
    void loadPublishesRefreshEvents() throws Exception {
        AtomicInteger matrixEvents = new AtomicInteger();
        AtomicInteger respawnEvents = new AtomicInteger();
        eventBus.subscribe(SimulationEvent.MatrixChanged.class, e -> matrixEvents.incrementAndGet());
        eventBus.subscribe(SimulationEvent.WorldRespawned.class, e -> respawnEvents.incrementAndGet());

        service.saveCurrent("Evented").get(10, TimeUnit.SECONDS);
        service.load("Evented").get(10, TimeUnit.SECONDS);

        assertEquals(1, matrixEvents.get());
        assertEquals(1, respawnEvents.get());
    }

    @Test
    void exportAndImportRoundTripThroughFile(@TempDir Path tempDir) throws Exception {
        engine.submit(() -> world.matrix().set(1, 1, 0.77));
        service.saveCurrent("Exported").get(10, TimeUnit.SECONDS);

        Path file = tempDir.resolve("exported.json");
        service.exportToFile("Exported", file);
        assertTrue(Files.size(file) > 100);

        engine.submit(() -> world.matrix().reset());
        var imported = service.importFromFile(file, "Imported").get(10, TimeUnit.SECONDS);
        sync();

        assertEquals("Imported", imported.name());
        assertTrue(service.exists("Imported"));
        assertEquals(0.77, world.matrix().get(1, 1), 1e-12);
    }

    @Test
    void exportCurrentWritesLiveStateWithoutStoredPreset(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("live.json");
        service.exportCurrentToFile(file).get(10, TimeUnit.SECONDS);
        String json = Files.readString(file);
        assertTrue(json.contains("\"matrix\""));
        assertTrue(json.contains("\"physics\""));
    }

    @Test
    void listDeleteRenameDelegateToRepository() throws Exception {
        service.saveCurrent("A").get(10, TimeUnit.SECONDS);
        service.saveCurrent("B").get(10, TimeUnit.SECONDS);

        assertEquals(2, service.list().size());
        assertTrue(service.rename("A", "C"));
        assertTrue(service.exists("C"));
        assertTrue(service.delete("B"));
        assertEquals(1, service.list().size());
    }

    private void sync() throws Exception {
        var latch = new java.util.concurrent.CountDownLatch(1);
        engine.submit(latch::countDown);
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
}
