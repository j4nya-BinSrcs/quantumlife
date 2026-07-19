package com.particlelife.serialization;

import java.util.List;

/**
 * Serializable snapshot of everything that defines a simulation setup —
 * the payload of presets (SQLite and JSON export) and of the auto-saved
 * session in the app config.
 *
 * <p>Immutable records; Gson maps them directly. All nested types use only
 * JSON-friendly primitives so the schema is stable and diff-able.
 */
public record PresetData(
        List<SpeciesData> species,
        double[][] matrix,
        boolean matrixSymmetric,
        PhysicsData physics,
        SimulationData simulation) {

    /** One species' persisted state. */
    public record SpeciesData(
            String name,
            int colorRgb,
            double mass,
            double radius,
            boolean enabled) {
    }

    /** Physics settings snapshot (field names mirror {@code PhysicsSettings}). */
    public record PhysicsData(
            double worldSize,
            double interactionRadius,
            double beta,
            double forceMultiplier,
            double frictionHalfLife,
            double maxVelocity,
            double timeStep,
            double damping,
            String boundaryType,
            String forceFunctionType) {
    }

    /** Simulation settings snapshot. */
    public record SimulationData(
            int particleCount,
            int speciesCount,
            long seed,
            double spawnRadiusFraction,
            double timeScale) {
    }
}
