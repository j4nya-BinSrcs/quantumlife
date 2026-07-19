package com.particlelife.database.repository;

import com.particlelife.core.physics.PhysicsSettings;
import com.particlelife.core.simulation.SimulationSettings;
import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.database.Database;
import com.particlelife.database.DatabaseException;
import com.particlelife.math.DeterministicRandom;
import com.particlelife.serialization.PresetData;
import com.particlelife.serialization.WorldMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlitePresetRepositoryTest {

    private Database database;
    private SqlitePresetRepository repository;
    private MutableClock clock;

    /** Minimal controllable clock for timestamp assertions. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-07-19T12:00:00Z");

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        clock = new MutableClock();
        repository = new SqlitePresetRepository(database, clock);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private static PresetData sampleData(long matrixSeed) {
        SimulationWorld world = new SimulationWorld(new SimulationSettings(), new PhysicsSettings());
        world.matrix().randomize(new DeterministicRandom(matrixSeed));
        return WorldMapper.capture(world);
    }

    @Test
    void saveAndLoadRoundTripsPayload() {
        PresetData data = sampleData(1L);
        Preset saved = repository.save("My Preset", data);
        assertTrue(saved.id() > 0);

        Optional<Preset> loaded = repository.findByName("My Preset");
        assertTrue(loaded.isPresent());
        assertEquals("My Preset", loaded.get().name());
        assertEquals(data.matrix().length, loaded.get().data().matrix().length);
        for (int i = 0; i < data.matrix().length; i++) {
            for (int j = 0; j < data.matrix().length; j++) {
                assertEquals(data.matrix()[i][j], loaded.get().data().matrix()[i][j], 1e-12);
            }
        }
        assertEquals(data.simulation().particleCount(), loaded.get().particleCount());
        assertEquals(data.species().size(), loaded.get().speciesCount());
    }

    @Test
    void findByNameIsCaseInsensitive() {
        repository.save("Primordial Soup", sampleData(1L));
        assertTrue(repository.findByName("primordial soup").isPresent());
        assertTrue(repository.exists("PRIMORDIAL SOUP"));
    }

    @Test
    void saveWithExistingNameOverwritesKeepingCreatedAt() {
        repository.save("Evolving", sampleData(1L));
        Instant created = repository.findByName("Evolving").orElseThrow().createdAt();

        clock.advanceSeconds(100);
        repository.save("Evolving", sampleData(2L));

        Preset updated = repository.findByName("Evolving").orElseThrow();
        assertEquals(created, updated.createdAt(), "created_at survives overwrite");
        assertEquals(created.plusSeconds(100), updated.modifiedAt());
        List<Preset> all = repository.findAll();
        assertEquals(1, all.size(), "overwrite must not duplicate");
    }

    @Test
    void findAllReturnsSummariesNewestFirst() {
        repository.save("First", sampleData(1L));
        clock.advanceSeconds(10);
        repository.save("Second", sampleData(2L));
        clock.advanceSeconds(10);
        repository.save("Third", sampleData(3L));

        List<Preset> all = repository.findAll();
        assertEquals(3, all.size());
        assertEquals("Third", all.get(0).name());
        assertEquals("First", all.get(2).name());
        all.forEach(p -> assertNull(p.data(), "summaries must not carry payloads"));
    }

    @Test
    void renameChangesNameAndRejectsCollisions() {
        repository.save("Old", sampleData(1L));
        repository.save("Taken", sampleData(2L));

        assertTrue(repository.rename("Old", "New"));
        assertFalse(repository.exists("Old"));
        assertTrue(repository.exists("New"));

        assertThrows(DatabaseException.class, () -> repository.rename("New", "taken"));
        assertFalse(repository.rename("DoesNotExist", "Whatever"));
    }

    @Test
    void deleteRemovesPreset() {
        repository.save("Doomed", sampleData(1L));
        assertTrue(repository.delete("Doomed"));
        assertFalse(repository.exists("Doomed"));
        assertFalse(repository.delete("Doomed"), "second delete reports absence");
    }

    @Test
    void blankAndOversizedNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> repository.save("", sampleData(1L)));
        assertThrows(IllegalArgumentException.class, () -> repository.save("   ", sampleData(1L)));
        assertThrows(IllegalArgumentException.class,
                () -> repository.save("x".repeat(101), sampleData(1L)));
    }

    @Test
    void persistsAcrossReopenOnDisk(@TempDir Path tempDir) {
        Path file = tempDir.resolve("presets.db");
        try (Database onDisk = new Database(file)) {
            new SqlitePresetRepository(onDisk, clock).save("Durable", sampleData(5L));
        }
        try (Database reopened = new Database(file)) {
            SqlitePresetRepository repo = new SqlitePresetRepository(reopened, clock);
            Optional<Preset> loaded = repo.findByName("Durable");
            assertTrue(loaded.isPresent());
            assertEquals("Durable", loaded.get().name());
        }
    }

    @Test
    void unlimitedPresetsAreSupported() {
        for (int i = 0; i < 150; i++) {
            repository.save("Preset " + i, sampleData(i));
        }
        assertEquals(150, repository.findAll().size());
    }
}
