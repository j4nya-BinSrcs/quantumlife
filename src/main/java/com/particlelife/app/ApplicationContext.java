package com.particlelife.app;

import com.particlelife.config.ConfigService;
import com.particlelife.core.commands.CommandManager;
import com.particlelife.core.engine.SimulationEngine;
import com.particlelife.core.physics.PhysicsEngine;
import com.particlelife.core.physics.PhysicsSettings;
import com.particlelife.core.simulation.SimulationSettings;
import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.database.Database;
import com.particlelife.database.repository.SqlitePresetRepository;
import com.particlelife.database.services.PresetService;
import com.particlelife.events.EventBus;
import com.particlelife.presets.BuiltInPresets;
import com.particlelife.serialization.WorldMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Composition root (manual dependency injection): constructs and wires every
 * long-lived component in the correct order and owns their shutdown. This is
 * the only class that knows the full object graph — everything else receives
 * its collaborators through constructors.
 */
public final class ApplicationContext implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationContext.class);

    private final EventBus eventBus;
    private final SimulationWorld world;
    private final SimulationEngine engine;
    private final CommandManager commands;
    private final ConfigService configService;
    private final Database database;
    private final PresetService presetService;

    /** Wires the application against the default per-user storage locations. */
    public ApplicationContext() {
        this(ConfigService.defaultLocation(), Database.defaultLocation());
    }

    /** Wires the application against explicit storage paths (tests). */
    public ApplicationContext(Path configPath, Path databasePath) {
        this.eventBus = new EventBus();
        this.configService = new ConfigService(configPath);

        PhysicsSettings physicsSettings = new PhysicsSettings();
        SimulationSettings simulationSettings = new SimulationSettings();
        this.world = new SimulationWorld(simulationSettings, physicsSettings);
        this.engine = new SimulationEngine(world, new PhysicsEngine(physicsSettings), eventBus);
        this.commands = new CommandManager(engine, eventBus);

        this.database = new Database(databasePath);
        SqlitePresetRepository repository = new SqlitePresetRepository(database);
        BuiltInPresets.seed(repository);
        this.presetService = new PresetService(repository, engine, eventBus);

        restoreSession();
        LOG.info("Application context wired");
    }

    /** Applies the previous session's saved world, or spawns defaults. */
    private void restoreSession() {
        var session = configService.get().session();
        if (session != null) {
            WorldMapper.apply(session, world);
            LOG.info("Restored previous session ({} particles)", world.store().count());
        } else {
            world.matrix().randomize(
                    new com.particlelife.math.DeterministicRandom(simulationSettings().seed()));
            world.respawn();
            LOG.info("Started fresh session ({} particles)", world.store().count());
        }
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public SimulationWorld world() {
        return world;
    }

    public SimulationEngine engine() {
        return engine;
    }

    public CommandManager commands() {
        return commands;
    }

    public ConfigService config() {
        return configService;
    }

    public PresetService presets() {
        return presetService;
    }

    public SimulationSettings simulationSettings() {
        return world.simulationSettings();
    }

    public PhysicsSettings physicsSettings() {
        return world.physicsSettings();
    }

    /** Persists the current world into the config as the session to restore. */
    public void saveSession() {
        configService.update(configService.get().withSession(WorldMapper.capture(world)));
    }

    @Override
    public void close() {
        engine.close();
        database.close();
        LOG.info("Application context closed");
    }
}
