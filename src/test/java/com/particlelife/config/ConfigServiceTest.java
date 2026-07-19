package com.particlelife.config;

import com.particlelife.core.physics.PhysicsSettings;
import com.particlelife.core.simulation.SimulationSettings;
import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.serialization.WorldMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigServiceTest {

    @TempDir
    Path tempDir;

    private Path configPath() {
        return tempDir.resolve("nested/dir/config.json");
    }

    @Test
    void missingFileYieldsDefaults() {
        ConfigService service = new ConfigService(configPath());
        AppConfig config = service.get();
        assertEquals("DARK", config.theme());
        assertEquals(1440, config.window().width(), 1e-9);
        assertTrue(config.sidebar().visible());
    }

    @Test
    void updatePersistsAndReloads() {
        ConfigService service = new ConfigService(configPath());
        service.update(service.get()
                .withTheme("LIGHT")
                .withCamera(new AppConfig.CameraConfig(45, -10, 250))
                .withWindow(new AppConfig.WindowConfig(800, 600, 10, 20, true)));

        ConfigService reloaded = new ConfigService(configPath());
        AppConfig config = reloaded.get();
        assertEquals("LIGHT", config.theme());
        assertEquals(45, config.camera().yawDegrees(), 1e-9);
        assertEquals(250, config.camera().distance(), 1e-9);
        assertEquals(800, config.window().width(), 1e-9);
        assertTrue(config.window().maximized());
    }

    @Test
    void corruptFileDegradesToDefaults() throws Exception {
        Files.createDirectories(configPath().getParent());
        Files.writeString(configPath(), "{{{ definitely not json");

        ConfigService service = new ConfigService(configPath());
        assertEquals("DARK", service.get().theme());
    }

    @Test
    void partialConfigIsFilledWithDefaults() throws Exception {
        Files.createDirectories(configPath().getParent());
        Files.writeString(configPath(), "{\"theme\":\"LIGHT\"}");

        ConfigService service = new ConfigService(configPath());
        AppConfig config = service.get();
        assertEquals("LIGHT", config.theme());
        assertNotNull(config.window(), "missing sections default");
        assertNotNull(config.camera());
        assertNotNull(config.render());
    }

    @Test
    void sessionRoundTripsThroughConfig() {
        SimulationWorld world = new SimulationWorld(new SimulationSettings(), new PhysicsSettings());
        world.simulationSettings().setParticleCount(3333);

        ConfigService service = new ConfigService(configPath());
        service.update(service.get().withSession(WorldMapper.capture(world)));

        ConfigService reloaded = new ConfigService(configPath());
        assertNotNull(reloaded.get().session());
        assertEquals(3333, reloaded.get().session().simulation().particleCount());
    }

    @Test
    void saveIsAtomicNoTempFileLeftBehind() {
        ConfigService service = new ConfigService(configPath());
        service.update(service.get().withTheme("LIGHT"));
        assertTrue(Files.exists(configPath()));
        assertTrue(!Files.exists(configPath().resolveSibling("config.json.tmp")));
    }
}
