package com.particlelife.database.repository;

import com.particlelife.serialization.PresetData;

import java.util.List;
import java.util.Optional;

/**
 * Repository for simulation presets (Repository pattern: persistence
 * behind an interface so services and tests never see SQL).
 *
 * <p>Implementations must be thread-safe; the UI calls from the FX thread
 * while exports may run on background threads.
 */
public interface PresetRepository {

    /**
     * Inserts a preset, or overwrites the payload of an existing one with
     * the same name (upsert). Returns the stored row.
     */
    Preset save(String name, PresetData data);

    /** All presets as summaries (no payload), newest first. */
    List<Preset> findAll();

    /** Loads one preset with payload by (case-insensitive) name. */
    Optional<Preset> findByName(String name);

    /** Renames a preset. Returns false if it does not exist. */
    boolean rename(String oldName, String newName);

    /** Deletes a preset. Returns false if it does not exist. */
    boolean delete(String name);

    /** Whether a preset with this name exists. */
    boolean exists(String name);
}
