package com.particlelife.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the SQLite connection and the schema lifecycle.
 *
 * <p>SQLite is embedded and single-file; one long-lived connection guarded
 * by the repository's synchronization is the recommended usage for a desktop
 * app (a pool would only add contention on the file lock). Schema versioning
 * uses {@code PRAGMA user_version} so future releases can migrate in order.
 */
public final class Database implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Database.class);
    private static final int SCHEMA_VERSION = 1;

    private final Connection connection;

    /** Opens (creating if needed) the database at {@code file} and migrates it. */
    public Database(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + file);
            configure();
            migrate();
            LOG.info("Database ready at {}", file);
        } catch (Exception e) {
            throw new DatabaseException("Could not open database at " + file, e);
        }
    }

    /** Opens an in-memory database (tests). */
    public static Database inMemory() {
        try {
            Database db = new Database();
            db.configure();
            db.migrate();
            return db;
        } catch (SQLException e) {
            throw new DatabaseException("Could not open in-memory database", e);
        }
    }

    private Database() throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    /** The default per-user database location. */
    public static Path defaultLocation() {
        return Path.of(System.getProperty("user.home"), ".particle-life-3d", "presets.db");
    }

    private void configure() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
        }
    }

    private void migrate() throws SQLException {
        int version = currentVersion();
        if (version < 1) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS presets (
                            id             INTEGER PRIMARY KEY AUTOINCREMENT,
                            name           TEXT NOT NULL UNIQUE COLLATE NOCASE,
                            payload        TEXT NOT NULL,
                            species_count  INTEGER NOT NULL,
                            particle_count INTEGER NOT NULL,
                            created_at     TEXT NOT NULL,
                            modified_at    TEXT NOT NULL
                        )
                        """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_presets_name ON presets(name)");
            }
            setVersion(1);
            LOG.info("Migrated database schema to version 1");
        }
        // Future migrations chain here: if (version < 2) { ... }
        if (currentVersion() != SCHEMA_VERSION) {
            throw new DatabaseException(
                    "Schema version mismatch: expected " + SCHEMA_VERSION + ", got " + currentVersion());
        }
    }

    private int currentVersion() throws SQLException {
        try (Statement statement = connection.createStatement();
             var rs = statement.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void setVersion(int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = " + version);
        }
    }

    /** The underlying connection; callers must synchronize on the repository. */
    public Connection connection() {
        return connection;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            LOG.warn("Error closing database", e);
        }
    }
}
