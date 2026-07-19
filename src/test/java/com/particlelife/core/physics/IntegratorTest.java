package com.particlelife.core.physics;

import com.particlelife.math.Vector3;
import com.particlelife.particle.ParticleStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegratorTest {

    private static final double EPS = 1e-9;

    private final Integrator integrator = new Integrator();

    private static PhysicsSettings noFriction() {
        PhysicsSettings s = new PhysicsSettings();
        s.setFrictionHalfLife(0.0);
        s.setMaxVelocity(1000.0);
        return s;
    }

    private static BoundaryStrategy openBoundary(PhysicsSettings s) {
        return new OpenBoundary(s.worldSize(), 0.0);
    }

    @Test
    void constantVelocityAdvancesPosition() {
        PhysicsSettings s = noFriction();
        ParticleStore store = new ParticleStore(4);
        int i = store.spawn(0, 0, 0, 0, 1, 1);
        store.setVelocity(i, 10, -5, 2);

        integrator.integrate(store, s, openBoundary(s), 0.1);

        assertEquals(new Vector3(1.0, -0.5, 0.2), store.position(i, new Vector3()));
    }

    @Test
    void forceAcceleratesAccordingToMass() {
        PhysicsSettings s = noFriction();
        ParticleStore store = new ParticleStore(4);
        int light = store.spawn(0, 0, 0, 0, 1.0, 1);
        int heavy = store.spawn(0, 0, 10, 0, 4.0, 1);
        store.forces()[light * 3] = 8.0;
        store.forces()[heavy * 3] = 8.0;

        integrator.integrate(store, s, openBoundary(s), 0.5);

        // v = (F/m) * dt
        assertEquals(4.0, store.velocity(light, new Vector3()).x, EPS);
        assertEquals(1.0, store.velocity(heavy, new Vector3()).x, EPS);
    }

    @Test
    void frictionHalvesSpeedOverOneHalfLife() {
        PhysicsSettings s = new PhysicsSettings();
        s.setFrictionHalfLife(0.5);
        s.setMaxVelocity(1000.0);
        ParticleStore store = new ParticleStore(4);
        int i = store.spawn(0, 0, 0, 0, 1, 1);
        store.setVelocity(i, 16, 0, 0);

        // Integrate exactly one half-life in many small steps: the half-life
        // parameterization must make the result step-size independent.
        int steps = 50;
        double dt = 0.5 / steps;
        for (int k = 0; k < steps; k++) {
            integrator.integrate(store, s, openBoundary(s), dt);
        }

        assertEquals(8.0, store.velocity(i, new Vector3()).x, 1e-6);
    }

    @Test
    void frictionIsStepSizeIndependent() {
        PhysicsSettings s = new PhysicsSettings();
        s.setFrictionHalfLife(0.25);
        s.setMaxVelocity(1000.0);

        ParticleStore coarse = new ParticleStore(2);
        int a = coarse.spawn(0, 0, 0, 0, 1, 1);
        coarse.setVelocity(a, 10, 0, 0);
        integrator.integrate(coarse, s, openBoundary(s), 0.1);

        ParticleStore fine = new ParticleStore(2);
        int b = fine.spawn(0, 0, 0, 0, 1, 1);
        fine.setVelocity(b, 10, 0, 0);
        for (int k = 0; k < 10; k++) {
            integrator.integrate(fine, s, openBoundary(s), 0.01);
        }

        assertEquals(coarse.velocity(a, new Vector3()).x,
                fine.velocity(b, new Vector3()).x, 1e-9,
                "velocity after 0.1s must not depend on step size");
    }

    @Test
    void velocityIsClampedToGlobalMaximum() {
        PhysicsSettings s = noFriction();
        s.setMaxVelocity(5.0);
        ParticleStore store = new ParticleStore(4);
        int i = store.spawn(0, 0, 0, 0, 1, 1);
        store.forces()[i * 3] = 1e6;

        integrator.integrate(store, s, openBoundary(s), 0.1);

        assertEquals(5.0, store.velocity(i, new Vector3()).length(), EPS);
    }

    @Test
    void perParticleMaxVelocityTightensGlobalCap() {
        PhysicsSettings s = noFriction();
        s.setMaxVelocity(100.0);
        ParticleStore store = new ParticleStore(4);
        int i = store.spawn(0, 0, 0, 0, 1, 1);
        store.maxVelocities()[i] = 2.0;
        store.forces()[i * 3] = 1e6;

        integrator.integrate(store, s, openBoundary(s), 0.1);

        assertEquals(2.0, store.velocity(i, new Vector3()).length(), EPS);
    }

    @Test
    void perParticleDampingSlowsThatParticleOnly() {
        PhysicsSettings s = noFriction();
        ParticleStore store = new ParticleStore(4);
        int normal = store.spawn(0, 0, 0, 0, 1, 1);
        int damped = store.spawn(0, 0, 10, 0, 1, 1);
        store.setVelocity(normal, 10, 0, 0);
        store.setVelocity(damped, 10, 0, 0);
        store.dampings()[damped] = 0.5;

        integrator.integrate(store, s, openBoundary(s), 0.1);

        assertEquals(10.0, store.velocity(normal, new Vector3()).x, EPS);
        assertEquals(5.0, store.velocity(damped, new Vector3()).x, EPS);
    }

    @Test
    void previousPositionTracksPositionBeforeStep() {
        PhysicsSettings s = noFriction();
        ParticleStore store = new ParticleStore(4);
        int i = store.spawn(0, 3, 4, 5, 1, 1);
        store.setVelocity(i, 1, 0, 0);

        integrator.integrate(store, s, openBoundary(s), 1.0);

        assertEquals(new Vector3(3, 4, 5), store.view(i).previousPosition(new Vector3()));
        assertEquals(new Vector3(4, 4, 5), store.position(i, new Vector3()));
    }

    @Test
    void forcesAreClearedAfterIntegration() {
        PhysicsSettings s = noFriction();
        ParticleStore store = new ParticleStore(4);
        int i = store.spawn(0, 0, 0, 0, 1, 1);
        store.forces()[i * 3] = 7.0;

        integrator.integrate(store, s, openBoundary(s), 0.1);

        assertEquals(0.0, store.forces()[i * 3], EPS);
    }

    @Test
    void wrapBoundaryWrapsPositions() {
        PhysicsSettings s = noFriction();
        s.setWorldSize(100.0);
        ParticleStore store = new ParticleStore(4);
        int i = store.spawn(0, 49, 0, 0, 1, 1);
        store.setVelocity(i, 20, 0, 0);

        integrator.integrate(store, s, new WrapBoundary(100.0), 0.1);

        // 49 + 2 = 51 wraps to -49.
        assertEquals(-49.0, store.position(i, new Vector3()).x, EPS);
    }

    @Test
    void bounceBoundaryReflectsAndDamps() {
        PhysicsSettings s = noFriction();
        s.setWorldSize(100.0);
        ParticleStore store = new ParticleStore(4);
        int i = store.spawn(0, 49, 0, 0, 1, 1);
        store.setVelocity(i, 20, 0, 0);

        integrator.integrate(store, s, new BounceBoundary(100.0, 0.5), 0.1);

        Vector3 pos = store.position(i, new Vector3());
        Vector3 vel = store.velocity(i, new Vector3());
        assertEquals(49.0, pos.x, EPS, "reflected back inside");
        assertEquals(-10.0, vel.x, EPS, "velocity reversed and damped by restitution");
    }

    @Test
    void openBoundaryPullsEscapeesBack() {
        PhysicsSettings s = noFriction();
        s.setWorldSize(100.0);
        ParticleStore store = new ParticleStore(4);
        int i = store.spawn(0, 49.9, 0, 0, 1, 1);
        store.setVelocity(i, 10, 0, 0);
        OpenBoundary boundary = new OpenBoundary(100.0, 20.0);

        double vxBefore = 10.0;
        integrator.integrate(store, s, boundary, 0.1);

        assertTrue(store.position(i, new Vector3()).x > 50.0, "may overshoot the cube");
        assertTrue(store.velocity(i, new Vector3()).x < vxBefore, "pull-back decelerates");
    }
}
