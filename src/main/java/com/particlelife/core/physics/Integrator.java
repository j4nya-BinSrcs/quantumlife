package com.particlelife.core.physics;

import com.particlelife.particle.ParticleStore;

import java.util.stream.IntStream;

/**
 * Advances particle state one step with semi-implicit (symplectic) Euler:
 * velocity first (friction, acceleration, damping, speed clamp), then
 * position, then the boundary strategy.
 *
 * <p>Friction uses the half-life parameterization
 * {@code μ = 0.5^(dt / t½)}, which makes damping independent of the time
 * step — the same {@code t½} produces the same trajectories at any dt.
 */
public final class Integrator {

    private static final int PARALLEL_THRESHOLD = 1024;

    /**
     * Integrates the first {@code count} particles of the store over
     * {@code dt} seconds and applies {@code boundary}. Reads and clears the
     * force accumulators.
     */
    public void integrate(ParticleStore store,
                          PhysicsSettings settings,
                          BoundaryStrategy boundary,
                          double dt) {
        int count = store.count();
        if (count == 0 || dt <= 0.0) {
            return;
        }

        double halfLife = settings.frictionHalfLife();
        double friction = halfLife > 0.0 ? Math.pow(0.5, dt / halfLife) : 1.0;
        double globalDamping = 1.0 - settings.damping();
        double maxVelocity = settings.maxVelocity();

        IntStream indices = IntStream.range(0, count);
        if (count >= PARALLEL_THRESHOLD) {
            indices = indices.parallel();
        }
        indices.forEach(i -> integrateOne(
                store, boundary, i, dt, friction, globalDamping, maxVelocity));
    }

    private void integrateOne(ParticleStore store,
                              BoundaryStrategy boundary,
                              int i,
                              double dt,
                              double friction,
                              double globalDamping,
                              double globalMaxVelocity) {
        double[] positions = store.positions();
        double[] velocities = store.velocities();
        double[] forces = store.forces();
        double[] previous = store.previousPositions();
        double[] masses = store.masses();
        double[] maxVelocities = store.maxVelocities();
        double[] dampings = store.dampings();

        int base = i * 3;

        previous[base] = positions[base];
        previous[base + 1] = positions[base + 1];
        previous[base + 2] = positions[base + 2];

        double invMass = 1.0 / masses[i];
        double decay = friction * globalDamping * (1.0 - dampings[i]);

        double vx = velocities[base] * decay + forces[base] * invMass * dt;
        double vy = velocities[base + 1] * decay + forces[base + 1] * invMass * dt;
        double vz = velocities[base + 2] * decay + forces[base + 2] * invMass * dt;

        double cap = globalMaxVelocity;
        double particleCap = maxVelocities[i];
        if (particleCap > 0.0 && particleCap < cap) {
            cap = particleCap;
        }
        double v2 = vx * vx + vy * vy + vz * vz;
        if (v2 > cap * cap) {
            double f = cap / Math.sqrt(v2);
            vx *= f;
            vy *= f;
            vz *= f;
        }

        velocities[base] = vx;
        velocities[base + 1] = vy;
        velocities[base + 2] = vz;

        positions[base] += vx * dt;
        positions[base + 1] += vy * dt;
        positions[base + 2] += vz * dt;

        boundary.apply(positions, velocities, base, dt);

        forces[base] = 0.0;
        forces[base + 1] = 0.0;
        forces[base + 2] = 0.0;
    }
}
