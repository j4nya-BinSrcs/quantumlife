package com.particlelife.core.physics;

import com.particlelife.forces.AttractionMatrix;
import com.particlelife.forces.PiecewiseLinearForce;
import com.particlelife.math.DeterministicRandom;
import com.particlelife.particle.ParticleStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForceCalculatorTest {

    private static final double EPS = 1e-9;

    private final ForceCalculator calculator = new ForceCalculator();

    private static PhysicsSettings settings(double worldSize, double radius) {
        PhysicsSettings s = new PhysicsSettings();
        s.setWorldSize(worldSize);
        s.setInteractionRadius(radius);
        s.setForceMultiplier(1.0);
        return s;
    }

    @Test
    void mutuallyAttractingPairPullsTogether() {
        PhysicsSettings s = settings(200.0, 20.0);
        ParticleStore store = new ParticleStore(8);
        // Distance 12 = 0.6 * rMax: inside the attraction bump.
        int a = store.spawn(0, -6, 0, 0, 1, 1);
        int b = store.spawn(0, 6, 0, 0, 1, 1);
        AttractionMatrix m = new AttractionMatrix(1);
        m.set(0, 0, 1.0);

        compute(store, m, s);

        assertTrue(store.forces()[a * 3] > 0, "a pulled toward +x");
        assertTrue(store.forces()[b * 3] < 0, "b pulled toward -x");
        assertEquals(store.forces()[a * 3], -store.forces()[b * 3], EPS, "symmetric matrix -> equal opposite");
        assertEquals(0.0, store.forces()[a * 3 + 1], EPS);
        assertEquals(0.0, store.forces()[a * 3 + 2], EPS);
    }

    @Test
    void closePairRepelsRegardlessOfAttraction() {
        PhysicsSettings s = settings(200.0, 20.0);
        ParticleStore store = new ParticleStore(8);
        // Distance 2 = 0.1 * rMax: deep inside the beta repulsion zone.
        int a = store.spawn(0, -1, 0, 0, 1, 1);
        int b = store.spawn(0, 1, 0, 0, 1, 1);
        AttractionMatrix m = new AttractionMatrix(1);
        m.set(0, 0, 1.0); // maximum attraction — repulsion must still win

        compute(store, m, s);

        assertTrue(store.forces()[a * 3] < 0, "a pushed toward -x");
        assertTrue(store.forces()[b * 3] > 0, "b pushed toward +x");
    }

    @Test
    void forceMagnitudeMatchesKernelAnalytically() {
        PhysicsSettings s = settings(200.0, 20.0);
        ParticleStore store = new ParticleStore(8);
        double distance = 13.0; // x = 0.65
        store.spawn(0, 0, 0, 0, 1, 1);
        store.spawn(1, distance, 0, 0, 1, 1);
        AttractionMatrix m = new AttractionMatrix(2);
        m.set(0, 1, 0.5);

        compute(store, m, s);

        double x = distance / 20.0;
        double expected = new PiecewiseLinearForce(s.beta()).evaluate(x, 0.5) * 20.0;
        assertEquals(expected, store.forces()[0], 1e-6);
    }

    @Test
    void asymmetricMatrixProducesChaseDynamics() {
        PhysicsSettings s = settings(200.0, 20.0);
        ParticleStore store = new ParticleStore(8);
        int hunter = store.spawn(0, -6, 0, 0, 1, 1);
        int prey = store.spawn(1, 6, 0, 0, 1, 1);
        AttractionMatrix m = new AttractionMatrix(2);
        m.set(0, 1, 1.0);  // hunter attracted to prey
        m.set(1, 0, -1.0); // prey repelled by hunter

        compute(store, m, s);

        assertTrue(store.forces()[hunter * 3] > 0, "hunter chases +x");
        assertTrue(store.forces()[prey * 3] > 0, "prey flees +x");
    }

    @Test
    void particlesBeyondInteractionRadiusIgnoreEachOther() {
        PhysicsSettings s = settings(200.0, 20.0);
        ParticleStore store = new ParticleStore(8);
        store.spawn(0, -15, 0, 0, 1, 1);
        store.spawn(0, 15, 0, 0, 1, 1); // distance 30 > rMax 20
        AttractionMatrix m = new AttractionMatrix(1);
        m.set(0, 0, 1.0);

        compute(store, m, s);

        for (int k = 0; k < 6; k++) {
            assertEquals(0.0, store.forces()[k], EPS);
        }
    }

    @Test
    void wrapBoundaryInteractsAcrossSeam() {
        PhysicsSettings s = settings(100.0, 20.0);
        s.setBoundaryType(BoundaryType.WRAP);
        ParticleStore store = new ParticleStore(8);
        // 4 units apart through the seam (96 apart directly).
        int a = store.spawn(0, -48, 0, 0, 1, 1);
        int b = store.spawn(0, 48, 0, 0, 1, 1);
        AttractionMatrix m = new AttractionMatrix(1);
        m.set(0, 0, 1.0);

        compute(store, m, s);

        // Distance 4 = 0.2 * rMax < beta: repulsion pushes them apart across the seam.
        assertTrue(store.forces()[a * 3] > 0, "a pushed away from seam (+x)");
        assertTrue(store.forces()[b * 3] < 0, "b pushed away from seam (-x)");
    }

    @Test
    void openBoundaryDoesNotWrapDistances() {
        PhysicsSettings s = settings(100.0, 20.0);
        s.setBoundaryType(BoundaryType.OPEN);
        ParticleStore store = new ParticleStore(8);
        store.spawn(0, -48, 0, 0, 1, 1);
        store.spawn(0, 48, 0, 0, 1, 1);
        AttractionMatrix m = new AttractionMatrix(1);
        m.set(0, 0, 1.0);

        compute(store, m, s);

        for (int k = 0; k < 6; k++) {
            assertEquals(0.0, store.forces()[k], EPS);
        }
    }

    @Test
    void gridPathMatchesBruteForceReference() {
        // The same random scene must produce identical forces via the
        // spatial grid (fine grid) and the brute-force fallback (coarse).
        int count = 400;
        double world = 120.0;
        AttractionMatrix matrix = new AttractionMatrix(4);
        matrix.randomize(new DeterministicRandom(1234L));

        ParticleStore viaGrid = randomScene(new DeterministicRandom(555L), count, world);
        ParticleStore viaBrute = randomScene(new DeterministicRandom(555L), count, world);
        for (int i = 0; i < count * 3; i++) {
            assertEquals(viaGrid.positions()[i], viaBrute.positions()[i], 0.0, "scenes must be identical");
        }

        PhysicsSettings fine = settings(world, 15.0);   // 8 cells/axis -> grid path
        compute(viaGrid, matrix, fine);

        PhysicsSettings coarse = settings(world, 15.0);
        ParticleStore reference = viaBrute;
        // Force brute-force by using a grid that reports no neighbor support.
        SpatialGrid coarseGrid = new SpatialGrid(world, world / 2.0, count);
        new ForceCalculator().compute(reference, matrix,
                coarse.forceFunctionType().create(coarse.beta()), coarseGrid, coarse,
                coarse.boundaryType().create(world).isPeriodic());

        for (int i = 0; i < count * 3; i++) {
            assertEquals(reference.forces()[i], viaGrid.forces()[i], 1e-6,
                    "force mismatch at component " + i);
        }
    }

    private ParticleStore randomScene(DeterministicRandom rng, int count, double world) {
        ParticleStore store = new ParticleStore(count);
        double half = world / 2;
        for (int i = 0; i < count; i++) {
            store.spawn(rng.nextInt(4),
                    rng.nextDouble(-half, half), rng.nextDouble(-half, half),
                    rng.nextDouble(-half, half), 1.0, 1.0);
        }
        return store;
    }

    private void compute(ParticleStore store, AttractionMatrix matrix, PhysicsSettings s) {
        SpatialGrid grid = new SpatialGrid(s.worldSize(), s.interactionRadius(), store.capacity());
        BoundaryStrategy boundary = s.boundaryType().create(s.worldSize());
        calculator.compute(store, matrix, s.forceFunctionType().create(s.beta()),
                grid, s, boundary.isPeriodic());
    }
}
