package com.particlelife.core.physics;

import com.particlelife.core.simulation.SimulationSettings;
import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.forces.ForceFunctionType;
import com.particlelife.serialization.WorldMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputeBackendTest {

    @Test
    void autoAlwaysPrefersCpu() {
        assertFalse(PhysicsEngine.wantsGpu(
                ComputeBackend.AUTO, 1, ForceFunctionType.PIECEWISE_LINEAR));
        assertFalse(PhysicsEngine.wantsGpu(
                ComputeBackend.AUTO, 10_000, ForceFunctionType.PIECEWISE_LINEAR));
        assertFalse(PhysicsEngine.wantsGpu(
                ComputeBackend.AUTO, 100_000, ForceFunctionType.PIECEWISE_LINEAR));
    }

    @Test
    void smoothKernelAlwaysRoutesToCpu() {
        assertFalse(PhysicsEngine.wantsGpu(
                ComputeBackend.GPU, 10_000, ForceFunctionType.SMOOTH));
        assertFalse(PhysicsEngine.wantsGpu(
                ComputeBackend.AUTO, 10_000, ForceFunctionType.SMOOTH));
    }

    @Test
    void explicitGpuForcesGpuRegardlessOfCount() {
        assertTrue(PhysicsEngine.wantsGpu(
                ComputeBackend.GPU, 10, ForceFunctionType.PIECEWISE_LINEAR));
    }

    @Test
    void cpuSelectionAlwaysWins() {
        assertFalse(PhysicsEngine.wantsGpu(
                ComputeBackend.CPU, 10_000, ForceFunctionType.PIECEWISE_LINEAR));
    }

    @Test
    void settingsDefaultResetAndNullHandling() {
        PhysicsSettings s = new PhysicsSettings();
        assertEquals(ComputeBackend.AUTO, s.computeBackend());

        s.setComputeBackend(ComputeBackend.GPU);
        assertEquals(ComputeBackend.GPU, s.computeBackend());

        s.setComputeBackend(null);
        assertEquals(ComputeBackend.AUTO, s.computeBackend());

        s.setComputeBackend(ComputeBackend.CPU);
        s.resetToDefaults();
        assertEquals(ComputeBackend.AUTO, s.computeBackend());
    }

    @Test
    void computeBackendSurvivesPresetRoundTrip() {
        SimulationWorld source = new SimulationWorld(new SimulationSettings(), new PhysicsSettings());
        source.physicsSettings().setComputeBackend(ComputeBackend.GPU);
        SimulationWorld target = new SimulationWorld(new SimulationSettings(), new PhysicsSettings());

        WorldMapper.apply(WorldMapper.capture(source), target);

        assertEquals(ComputeBackend.GPU, target.physicsSettings().computeBackend());
    }
}