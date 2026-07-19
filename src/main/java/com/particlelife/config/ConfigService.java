package com.particlelife.config;

import com.particlelife.serialization.JsonSerializer;
import com.particlelife.serialization.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads and persists the {@link AppConfig}.
 *
 * <p>Persistence is crash-safe: the config is written to a temp file and
 * atomically moved over the previous one, so a crash mid-save can never
 * corrupt the stored config. A corrupt or unreadable file degrades to
 * defaults with a logged warning — configuration must never prevent startup.
 *
 * <p>Thread-safe: the current config is held in a volatile reference and
 * saves are serialized by a lock.
 */
public final class ConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigService.class);

    private final Path configFile;
    private final Object saveLock = new Object();
    private volatile AppConfig current;

    /** Creates a service persisting to {@code configFile} (parent dirs are created). */
    public ConfigService(Path configFile) {
        this.configFile = configFile;
        this.current = load();
    }

    /** The default per-user config location. */
    public static Path defaultLocation() {
        return Path.of(System.getProperty("user.home"), ".particle-life-3d", "config.json");
    }

    /** The current in-memory config. */
    public AppConfig get() {
        return current;
    }

    /**
     * Replaces the in-memory config and persists it. Call with copies
     * derived via the {@code with*} methods.
     */
    public void update(AppConfig config) {
        this.current = config.withDefaultsFilled();
        save();
    }

    private AppConfig load() {
        if (!Files.exists(configFile)) {
            LOG.info("No config at {}, using defaults", configFile);
            return AppConfig.defaults();
        }
        try {
            String json = Files.readString(configFile);
            return JsonSerializer.fromJson(json, AppConfig.class).withDefaultsFilled();
        } catch (IOException | SerializationException e) {
            LOG.warn("Could not load config from {} ({}), using defaults",
                    configFile, e.getMessage());
            return AppConfig.defaults();
        }
    }

    private void save() {
        synchronized (saveLock) {
            try {
                Files.createDirectories(configFile.getParent());
                Path temp = configFile.resolveSibling(configFile.getFileName() + ".tmp");
                Files.writeString(temp, JsonSerializer.toJson(current));
                try {
                    Files.move(temp, configFile,
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                LOG.error("Failed to save config to {}", configFile, e);
            }
        }
    }
}
