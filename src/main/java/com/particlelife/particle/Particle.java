package com.particlelife.particle;

import com.particlelife.math.Vector3;

/**
 * Object-oriented view of one particle inside a {@link ParticleStore}.
 *
 * <p>Exposes the full particle model — id, species, position, velocity,
 * acceleration, force, previous position, mass, radius, life state — as a
 * conventional object without giving up the store's cache-friendly flat
 * layout: a {@code Particle} holds only a store reference and an index.
 *
 * <p>Views are cheap to create but are invalidated when the underlying slot
 * is reused (after {@link ParticleStore#kill(int)} or
 * {@link ParticleStore#clear()}); do not retain them across mutations of the
 * store. Intended for UI, tests, and tools — hot loops should use the store's
 * raw arrays.
 */
public final class Particle {

    private final ParticleStore store;
    private final int index;

    Particle(ParticleStore store, int index) {
        this.store = store;
        this.index = index;
    }

    /** Index of this particle in its store. */
    public int index() {
        return index;
    }

    /** Stable unique id. */
    public long id() {
        return store.id(index);
    }

    /** Species (matrix row/column) index. */
    public int speciesIndex() {
        return store.speciesIndex(index);
    }

    /** Copies the current position into {@code out} and returns it. */
    public Vector3 position(Vector3 out) {
        return store.position(index, out);
    }

    /** Copies the current velocity into {@code out} and returns it. */
    public Vector3 velocity(Vector3 out) {
        return store.velocity(index, out);
    }

    /** Copies the position at the previous step into {@code out}. */
    public Vector3 previousPosition(Vector3 out) {
        double[] prev = store.previousPositions();
        int base = index * 3;
        return out.set(prev[base], prev[base + 1], prev[base + 2]);
    }

    /** Copies the current accumulated force into {@code out}. */
    public Vector3 force(Vector3 out) {
        double[] f = store.forces();
        int base = index * 3;
        return out.set(f[base], f[base + 1], f[base + 2]);
    }

    /**
     * Copies the current acceleration ({@code F / m}) into {@code out}.
     */
    public Vector3 acceleration(Vector3 out) {
        force(out);
        return out.scale(1.0 / mass());
    }

    public double mass() {
        return store.masses()[index];
    }

    public double radius() {
        return store.radii()[index];
    }

    /** Per-particle velocity cap; {@code 0} means the global cap applies alone. */
    public double maxVelocity() {
        return store.maxVelocities()[index];
    }

    /** Per-particle extra damping in {@code [0, 1)}. */
    public double damping() {
        return store.dampings()[index];
    }

    public LifeState lifeState() {
        return store.lifeState(index);
    }

    @Override
    public String toString() {
        Vector3 p = position(new Vector3());
        return "Particle[id=%d, species=%d, pos=%s]".formatted(id(), speciesIndex(), p);
    }
}
