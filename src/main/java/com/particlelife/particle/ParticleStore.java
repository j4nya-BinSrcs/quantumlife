package com.particlelife.particle;

import com.particlelife.math.Vector3;

/**
 * Structure-of-arrays storage for all particles.
 *
 * <p>This is the engine's core data layout decision: positions, velocities
 * and forces live in flat primitive arrays indexed by particle rather than in
 * an object graph. The physics loop then walks linear memory — no pointer
 * chasing, no per-particle allocation, and trivially partitionable across
 * worker threads. Benchmarks of Particle Life kernels on the JVM show this
 * layout is worth a large constant factor over particle objects.
 *
 * <p>The object-oriented {@link Particle} API is a lightweight <em>view</em>
 * over one index of this store; use it at boundaries (UI, tests, tools), not
 * inside hot loops.
 *
 * <p>Capacity is fixed at construction; the store is a pool. {@link #spawn}
 * reuses the lowest dead slot. All live particles occupy indices
 * {@code [0, count)} — {@link #kill(int)} swap-removes with the last live
 * particle so hot loops never branch on life state.
 */
public final class ParticleStore {

    private final int capacity;
    private int count;

    private final long[] ids;
    private long nextId = 1;

    private final int[] speciesIndices;
    private final byte[] lifeStates;

    // Hot data, one slot of 3 doubles per particle (x, y, z interleaved).
    private final double[] positions;
    private final double[] velocities;
    private final double[] forces;
    private final double[] previousPositions;

    // Per-particle scalar attributes.
    private final double[] masses;
    private final double[] radii;
    private final double[] maxVelocities;
    private final double[] dampings;

    /** Creates an empty store with room for {@code capacity} particles. */
    public ParticleStore(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1: " + capacity);
        }
        this.capacity = capacity;
        this.ids = new long[capacity];
        this.speciesIndices = new int[capacity];
        this.lifeStates = new byte[capacity];
        this.positions = new double[capacity * 3];
        this.velocities = new double[capacity * 3];
        this.forces = new double[capacity * 3];
        this.previousPositions = new double[capacity * 3];
        this.masses = new double[capacity];
        this.radii = new double[capacity];
        this.maxVelocities = new double[capacity];
        this.dampings = new double[capacity];
    }

    /** Maximum number of particles this store can hold. */
    public int capacity() {
        return capacity;
    }

    /** Number of live (or dormant) particles, occupying indices {@code [0, count)}. */
    public int count() {
        return count;
    }

    /**
     * Spawns a particle and returns its index, or {@code -1} if the store is
     * full.
     */
    public int spawn(int speciesIndex, double x, double y, double z, double mass, double radius) {
        if (count >= capacity) {
            return -1;
        }
        int i = count++;
        ids[i] = nextId++;
        speciesIndices[i] = speciesIndex;
        lifeStates[i] = (byte) LifeState.ALIVE.ordinal();
        int base = i * 3;
        positions[base] = x;
        positions[base + 1] = y;
        positions[base + 2] = z;
        previousPositions[base] = x;
        previousPositions[base + 1] = y;
        previousPositions[base + 2] = z;
        velocities[base] = 0;
        velocities[base + 1] = 0;
        velocities[base + 2] = 0;
        forces[base] = 0;
        forces[base + 1] = 0;
        forces[base + 2] = 0;
        masses[i] = mass;
        radii[i] = radius;
        // No per-particle limits by default; the integrator combines these
        // with the global physics settings (0 = "no extra limit/damping").
        maxVelocities[i] = 0.0;
        dampings[i] = 0.0;
        return i;
    }

    /**
     * Kills the particle at {@code index} by swapping the last live particle
     * into its slot. Indices above {@code index} are invalidated by this call.
     */
    public void kill(int index) {
        checkIndex(index);
        int last = count - 1;
        if (index != last) {
            ids[index] = ids[last];
            speciesIndices[index] = speciesIndices[last];
            lifeStates[index] = lifeStates[last];
            masses[index] = masses[last];
            radii[index] = radii[last];
            maxVelocities[index] = maxVelocities[last];
            dampings[index] = dampings[last];
            System.arraycopy(positions, last * 3, positions, index * 3, 3);
            System.arraycopy(velocities, last * 3, velocities, index * 3, 3);
            System.arraycopy(forces, last * 3, forces, index * 3, 3);
            System.arraycopy(previousPositions, last * 3, previousPositions, index * 3, 3);
        }
        lifeStates[last] = (byte) LifeState.DEAD.ordinal();
        count = last;
    }

    /** Removes all particles. */
    public void clear() {
        count = 0;
    }

    // ------------------------------------------------------------------
    // Raw array access for the physics hot path. Callers must treat the
    // arrays as owned by the store and only touch indices < count().
    // ------------------------------------------------------------------

    /** Interleaved xyz positions; slot {@code i} occupies {@code [3i, 3i+3)}. */
    public double[] positions() {
        return positions;
    }

    /** Interleaved xyz velocities. */
    public double[] velocities() {
        return velocities;
    }

    /** Interleaved xyz force accumulators. */
    public double[] forces() {
        return forces;
    }

    /** Interleaved xyz positions from the previous step. */
    public double[] previousPositions() {
        return previousPositions;
    }

    /** Per-particle species index. */
    public int[] speciesIndices() {
        return speciesIndices;
    }

    /** Per-particle mass. */
    public double[] masses() {
        return masses;
    }

    /** Per-particle radius. */
    public double[] radii() {
        return radii;
    }

    /** Per-particle velocity cap; {@code 0} means "global cap only". */
    public double[] maxVelocities() {
        return maxVelocities;
    }

    /** Per-particle extra damping in {@code [0, 1)}; {@code 0} means none. */
    public double[] dampings() {
        return dampings;
    }

    // ------------------------------------------------------------------
    // Per-particle accessors (boundary/API use).
    // ------------------------------------------------------------------

    /** Stable unique id of the particle at {@code index}. */
    public long id(int index) {
        checkIndex(index);
        return ids[index];
    }

    /** Species index of the particle at {@code index}. */
    public int speciesIndex(int index) {
        checkIndex(index);
        return speciesIndices[index];
    }

    /** Life state of the particle at {@code index}. */
    public LifeState lifeState(int index) {
        checkIndex(index);
        return LifeState.values()[lifeStates[index]];
    }

    /** Sets the life state of the particle at {@code index}. */
    public void setLifeState(int index, LifeState state) {
        checkIndex(index);
        lifeStates[index] = (byte) state.ordinal();
    }

    /** Copies the position of particle {@code index} into {@code out}. */
    public Vector3 position(int index, Vector3 out) {
        checkIndex(index);
        int base = index * 3;
        return out.set(positions[base], positions[base + 1], positions[base + 2]);
    }

    /** Sets the position of particle {@code index}. */
    public void setPosition(int index, double x, double y, double z) {
        checkIndex(index);
        int base = index * 3;
        positions[base] = x;
        positions[base + 1] = y;
        positions[base + 2] = z;
    }

    /** Copies the velocity of particle {@code index} into {@code out}. */
    public Vector3 velocity(int index, Vector3 out) {
        checkIndex(index);
        int base = index * 3;
        return out.set(velocities[base], velocities[base + 1], velocities[base + 2]);
    }

    /** Sets the velocity of particle {@code index}. */
    public void setVelocity(int index, double x, double y, double z) {
        checkIndex(index);
        int base = index * 3;
        velocities[base] = x;
        velocities[base + 1] = y;
        velocities[base + 2] = z;
    }

    /** Returns a {@link Particle} view of the slot at {@code index}. */
    public Particle view(int index) {
        checkIndex(index);
        return new Particle(this, index);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException(
                    "particle index %d out of [0, %d)".formatted(index, count));
        }
    }
}
