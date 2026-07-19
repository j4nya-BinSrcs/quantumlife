package com.particlelife.database.repository;

import com.particlelife.database.Database;
import com.particlelife.database.DatabaseException;
import com.particlelife.serialization.JsonSerializer;
import com.particlelife.serialization.PresetData;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link PresetRepository} over the embedded SQLite
 * database. All access is serialized on this instance's monitor — SQLite is
 * single-writer anyway, and a desktop app's preset traffic is tiny.
 */
public final class SqlitePresetRepository implements PresetRepository {

    private final Database database;
    private final Clock clock;

    public SqlitePresetRepository(Database database) {
        this(database, Clock.systemUTC());
    }

    /** Clock injection point for deterministic timestamp tests. */
    public SqlitePresetRepository(Database database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    @Override
    public synchronized Preset save(String name, PresetData data) {
        requireValidName(name);
        String payload = JsonSerializer.toJson(data);
        Instant now = clock.instant();
        int speciesCount = data.species() != null ? data.species().size() : 0;
        int particleCount = data.simulation() != null ? data.simulation().particleCount() : 0;
        try {
            // Manual upsert keeps created_at stable across overwrites.
            Optional<Preset> existing = findByName(name);
            if (existing.isPresent()) {
                try (PreparedStatement update = database.connection().prepareStatement("""
                        UPDATE presets
                        SET payload = ?, species_count = ?, particle_count = ?, modified_at = ?
                        WHERE name = ? COLLATE NOCASE
                        """)) {
                    update.setString(1, payload);
                    update.setInt(2, speciesCount);
                    update.setInt(3, particleCount);
                    update.setString(4, now.toString());
                    update.setString(5, name);
                    update.executeUpdate();
                }
                return new Preset(existing.get().id(), existing.get().name(), speciesCount,
                        particleCount, existing.get().createdAt(), now, data);
            }
            try (PreparedStatement insert = database.connection().prepareStatement("""
                    INSERT INTO presets (name, payload, species_count, particle_count, created_at, modified_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, name);
                insert.setString(2, payload);
                insert.setInt(3, speciesCount);
                insert.setInt(4, particleCount);
                insert.setString(5, now.toString());
                insert.setString(6, now.toString());
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    long id = keys.next() ? keys.getLong(1) : -1;
                    return new Preset(id, name, speciesCount, particleCount, now, now, data);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not save preset '" + name + "'", e);
        }
    }

    @Override
    public synchronized List<Preset> findAll() {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                SELECT id, name, species_count, particle_count, created_at, modified_at
                FROM presets ORDER BY modified_at DESC
                """);
             ResultSet rs = statement.executeQuery()) {
            List<Preset> presets = new ArrayList<>();
            while (rs.next()) {
                presets.add(mapRow(rs, null));
            }
            return presets;
        } catch (SQLException e) {
            throw new DatabaseException("Could not list presets", e);
        }
    }

    @Override
    public synchronized Optional<Preset> findByName(String name) {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                SELECT id, name, species_count, particle_count, created_at, modified_at, payload
                FROM presets WHERE name = ? COLLATE NOCASE
                """)) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                PresetData data = JsonSerializer.fromJson(rs.getString("payload"), PresetData.class);
                return Optional.of(mapRow(rs, data));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load preset '" + name + "'", e);
        }
    }

    @Override
    public synchronized boolean rename(String oldName, String newName) {
        requireValidName(newName);
        if (exists(newName) && !oldName.equalsIgnoreCase(newName)) {
            throw new DatabaseException("A preset named '" + newName + "' already exists");
        }
        try (PreparedStatement statement = database.connection().prepareStatement("""
                UPDATE presets SET name = ?, modified_at = ? WHERE name = ? COLLATE NOCASE
                """)) {
            statement.setString(1, newName);
            statement.setString(2, clock.instant().toString());
            statement.setString(3, oldName);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Could not rename preset '" + oldName + "'", e);
        }
    }

    @Override
    public synchronized boolean delete(String name) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "DELETE FROM presets WHERE name = ? COLLATE NOCASE")) {
            statement.setString(1, name);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete preset '" + name + "'", e);
        }
    }

    @Override
    public synchronized boolean exists(String name) {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT 1 FROM presets WHERE name = ? COLLATE NOCASE")) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not check preset '" + name + "'", e);
        }
    }

    private static Preset mapRow(ResultSet rs, PresetData data) throws SQLException {
        return new Preset(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getInt("species_count"),
                rs.getInt("particle_count"),
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("modified_at")),
                data);
    }

    private static void requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Preset name must not be blank");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Preset name too long (max 100 characters)");
        }
    }
}
