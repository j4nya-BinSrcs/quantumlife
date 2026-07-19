package com.particlelife.database.services;

import com.particlelife.core.engine.SimulationEngine;
import com.particlelife.database.repository.Preset;
import com.particlelife.database.repository.PresetRepository;
import com.particlelife.events.EventBus;
import com.particlelife.events.SimulationEvent;
import com.particlelife.serialization.JsonSerializer;
import com.particlelife.serialization.PresetData;
import com.particlelife.serialization.WorldMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Application service tying presets to the running simulation: captures and
 * applies world state on the engine thread, delegates storage to the
 * {@link PresetRepository}, and handles JSON file import/export.
 *
 * <p>Methods that touch the live world return {@link CompletableFuture}
 * because the capture/apply must round-trip through the engine's command
 * queue; pure storage operations are synchronous.
 */
public final class PresetService {

    private static final Logger LOG = LoggerFactory.getLogger(PresetService.class);

    private final PresetRepository repository;
    private final SimulationEngine engine;
    private final EventBus eventBus;

    public PresetService(PresetRepository repository, SimulationEngine engine, EventBus eventBus) {
        this.repository = repository;
        this.engine = engine;
        this.eventBus = eventBus;
    }

    /** Captures the current world and stores it under {@code name} (upsert). */
    public CompletableFuture<Preset> saveCurrent(String name) {
        return captureWorld().thenApply(data -> {
            Preset preset = repository.save(name, data);
            LOG.info("Saved preset '{}'", name);
            return preset;
        });
    }

    /** Loads {@code name} into the running simulation (applies + respawns). */
    public CompletableFuture<Boolean> load(String name) {
        Optional<Preset> preset = repository.findByName(name);
        if (preset.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        return applyToWorld(preset.get().data()).thenApply(v -> true);
    }

    /** All stored presets, newest first (summaries without payload). */
    public List<Preset> list() {
        return repository.findAll();
    }

    public boolean delete(String name) {
        boolean deleted = repository.delete(name);
        if (deleted) {
            LOG.info("Deleted preset '{}'", name);
        }
        return deleted;
    }

    public boolean rename(String oldName, String newName) {
        boolean renamed = repository.rename(oldName, newName);
        if (renamed) {
            LOG.info("Renamed preset '{}' -> '{}'", oldName, newName);
        }
        return renamed;
    }

    public boolean exists(String name) {
        return repository.exists(name);
    }

    /** Exports the stored preset {@code name} to a JSON file. */
    public void exportToFile(String name, Path file) throws IOException {
        Preset preset = repository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("No preset named '" + name + "'"));
        Files.writeString(file, JsonSerializer.toJson(preset.data()));
        LOG.info("Exported preset '{}' to {}", name, file);
    }

    /** Exports the live world directly to a JSON file (no stored preset needed). */
    public CompletableFuture<Void> exportCurrentToFile(Path file) {
        return captureWorld().thenAccept(data -> {
            try {
                Files.writeString(file, JsonSerializer.toJson(data));
                LOG.info("Exported current world to {}", file);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    /**
     * Imports a JSON preset file, stores it under {@code presetName} and
     * applies it to the simulation.
     */
    public CompletableFuture<Preset> importFromFile(Path file, String presetName) throws IOException {
        String json = Files.readString(file);
        PresetData data = JsonSerializer.fromJson(json, PresetData.class);
        Preset stored = repository.save(presetName, data);
        LOG.info("Imported preset '{}' from {}", presetName, file);
        return applyToWorld(data).thenApply(v -> stored);
    }

    private CompletableFuture<PresetData> captureWorld() {
        CompletableFuture<PresetData> future = new CompletableFuture<>();
        engine.submit(() -> {
            try {
                future.complete(WorldMapper.capture(engine.world()));
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private CompletableFuture<Void> applyToWorld(PresetData data) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        engine.submit(() -> {
            try {
                WorldMapper.apply(data, engine.world());
                eventBus.publish(new SimulationEvent.WorldRespawned(
                        engine.world().store().count(), engine.world().simulationSettings().seed()));
                eventBus.publish(new SimulationEvent.MatrixChanged());
                eventBus.publish(new SimulationEvent.SpeciesChanged());
                future.complete(null);
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}
