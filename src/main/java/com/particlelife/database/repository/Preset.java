package com.particlelife.database.repository;

import com.particlelife.serialization.PresetData;

import java.time.Instant;

/**
 * A stored preset row. {@code data} is {@code null} in summary listings
 * (browsing must not deserialize every payload); {@code findByName} loads it.
 */
public record Preset(
        long id,
        String name,
        int speciesCount,
        int particleCount,
        Instant createdAt,
        Instant modifiedAt,
        PresetData data) {
}
