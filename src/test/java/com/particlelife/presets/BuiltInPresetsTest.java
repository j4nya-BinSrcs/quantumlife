package com.particlelife.presets;

import com.particlelife.core.physics.PhysicsSettings;
import com.particlelife.core.simulation.SimulationSettings;
import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.database.Database;
import com.particlelife.database.repository.SqlitePresetRepository;
import com.particlelife.serialization.PresetData;
import com.particlelife.serialization.WorldMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Built-in preset seeding must be idempotent and must never clobber a
 * user's preset of the same name.
 */
class BuiltInPresetsTest {

    private Database database;
    private SqlitePresetRepository repository;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        repository = new SqlitePresetRepository(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void seedInsertsAllBuiltIns() {
        BuiltInPresets.seed(repository);
        assertTrue(repository.exists("Primordial Soup"));
        assertTrue(repository.exists("Predator Chains"));
        assertTrue(repository.exists("Cell Clusters"));
        assertEquals(3, repository.findAll().size());
    }

    @Test
    void seedIsIdempotent() {
        BuiltInPresets.seed(repository);
        BuiltInPresets.seed(repository);
        assertEquals(3, repository.findAll().size());
    }

    @Test
    void seedNeverOverwritesExistingUserPreset() {
        PresetData userData = worldWithTwoSpecies();
        repository.save("Primordial Soup", userData);
        BuiltInPresets.seed(repository);

        var loaded = repository.findByName("Primordial Soup").orElseThrow();
        assertEquals(2, loaded.data().matrix().length,
                "user's 2-species payload must survive seeding, not be replaced by the 6-species built-in");
        assertEquals(3, repository.findAll().size());
    }

    private static PresetData worldWithTwoSpecies() {
        SimulationWorld world = new SimulationWorld(new SimulationSettings(), new PhysicsSettings());
        world.setSpeciesCount(2);
        world.simulationSettings().setParticleCount(100);
        world.matrix().set(0, 1, -0.42);
        return WorldMapper.capture(world);
    }
}