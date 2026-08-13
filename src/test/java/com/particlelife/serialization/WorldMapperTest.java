package com.particlelife.serialization;

import com.particlelife.core.physics.BoundaryType;
import com.particlelife.core.physics.ComputeBackend;
import com.particlelife.core.physics.PhysicsSettings;
import com.particlelife.core.simulation.SimulationSettings;
import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.forces.ForceFunctionType;
import com.particlelife.math.DeterministicRandom;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldMapperTest {

    private static SimulationWorld newWorld() {
        return new SimulationWorld(new SimulationSettings(), new PhysicsSettings());
    }

    private static SimulationWorld customizedWorld() {
        SimulationWorld world = newWorld();
        world.setSpeciesCount(4);
        world.species().get(0).setName("Hunters");
        world.species().get(0).setColorRgb(0x123456);
        world.species().get(1).setEnabled(false);
        world.species().get(2).setMass(2.5);
        world.matrix().randomize(new DeterministicRandom(11L));
        world.matrix().setSymmetric(true);
        world.physicsSettings().setInteractionRadius(33.0);
        world.physicsSettings().setBoundaryType(BoundaryType.BOUNCE);
        world.physicsSettings().setForceFunctionType(ForceFunctionType.SMOOTH);
        world.simulationSettings().setParticleCount(1234);
        world.simulationSettings().setSeed(777L);
        world.simulationSettings().setTimeScale(2.0);
        return world;
    }

    @Test
    void captureThenApplyRoundTripsFullConfiguration() {
        SimulationWorld source = customizedWorld();
        PresetData data = WorldMapper.capture(source);

        // Serialize through JSON to prove the whole chain round-trips.
        String json = JsonSerializer.toJson(data);
        PresetData restored = JsonSerializer.fromJson(json, PresetData.class);

        SimulationWorld target = newWorld();
        WorldMapper.apply(restored, target);

        assertEquals(4, target.species().count());
        assertEquals("Hunters", target.species().get(0).name());
        assertEquals(0x123456, target.species().get(0).colorRgb());
        assertFalse(target.species().get(1).isEnabled());
        assertEquals(2.5, target.species().get(2).mass(), 1e-12);

        assertTrue(target.matrix().isSymmetric());
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                assertEquals(source.matrix().get(i, j), target.matrix().get(i, j), 1e-12);
            }
        }

        assertEquals(33.0, target.physicsSettings().interactionRadius(), 1e-12);
        assertEquals(BoundaryType.BOUNCE, target.physicsSettings().boundaryType());
        assertEquals(ForceFunctionType.SMOOTH, target.physicsSettings().forceFunctionType());
        assertEquals(1234, target.simulationSettings().particleCount());
        assertEquals(777L, target.simulationSettings().seed());
        assertEquals(2.0, target.simulationSettings().timeScale(), 1e-12);
    }

    @Test
    void applyRespawnsParticles() {
        SimulationWorld source = customizedWorld();
        SimulationWorld target = newWorld();
        WorldMapper.apply(WorldMapper.capture(source), target);
        assertEquals(1234, target.store().count());
    }

    @Test
    void applyToleratesUnknownEnumNames() {
        SimulationWorld world = newWorld();
        PresetData data = WorldMapper.capture(world);
        PresetData tampered = new PresetData(
                data.species(), data.matrix(), false,
                new PresetData.PhysicsData(200, 24, 0.3, 1, 0.04, 60, 1 / 60.0, 0,
                        "FUTURE_BOUNDARY", "FUTURE_KERNEL", "FUTURE_BACKEND"),
                data.simulation());

        WorldMapper.apply(tampered, world);

        assertEquals(BoundaryType.WRAP, world.physicsSettings().boundaryType());
        assertEquals(ForceFunctionType.PIECEWISE_LINEAR, world.physicsSettings().forceFunctionType());
        assertEquals(ComputeBackend.AUTO, world.physicsSettings().computeBackend());
    }

    @Test
    void applyFitsMatrixWhenSpeciesCountDiffers() {
        SimulationWorld source = newWorld();
        source.setSpeciesCount(6);
        source.matrix().randomize(new DeterministicRandom(3L));
        PresetData data = WorldMapper.capture(source);

        // Preset claims 6 species but carries only 3 species entries.
        PresetData truncated = new PresetData(
                data.species().subList(0, 3), data.matrix(), false,
                data.physics(), data.simulation());

        SimulationWorld target = newWorld();
        WorldMapper.apply(truncated, target);

        assertEquals(3, target.species().count());
        assertEquals(3, target.matrix().size());
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(source.matrix().get(i, j), target.matrix().get(i, j), 1e-12);
            }
        }
    }

    @Test
    void applyWithNullSectionsKeepsCurrentValues() {
        SimulationWorld world = newWorld();
        world.physicsSettings().setInteractionRadius(40.0);
        PresetData minimal = new PresetData(List.of(), null, false, null, null);

        WorldMapper.apply(minimal, world);

        assertEquals(40.0, world.physicsSettings().interactionRadius(), 1e-12);
        assertEquals(SimulationSettings.DEFAULT_SPECIES_COUNT, world.species().count());
    }

    @Test
    void malformedJsonRaisesSerializationException() {
        org.junit.jupiter.api.Assertions.assertThrows(SerializationException.class,
                () -> JsonSerializer.fromJson("{not json]", PresetData.class));
        org.junit.jupiter.api.Assertions.assertThrows(SerializationException.class,
                () -> JsonSerializer.fromJson("", PresetData.class));
    }
}
