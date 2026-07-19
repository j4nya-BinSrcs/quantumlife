package com.particlelife.core.engine;

import com.particlelife.particle.ParticleStore;

/**
 * Thread-safe hand-off buffer between the physics thread and the renderer.
 *
 * <p>The physics thread copies positions/species out of the live store after
 * each step ({@link #writeFrom}); the render thread copies them into its own
 * mesh arrays ({@link #readInto}). Both copies are bulk {@code arraycopy}s
 * of primitive arrays — ~120&nbsp;KB at 10k particles, microseconds per
 * frame — so a plain mutex is faster in practice than lock-free triple
 * buffering and trivially correct. The renderer never blocks physics for
 * longer than one copy.
 *
 * <p>Positions are narrowed to {@code float}: JavaFX meshes consume floats,
 * and render precision does not need the simulation's doubles.
 */
public final class FrameSnapshot {

    private final float[] positions;
    private final int[] species;
    private int count;
    private long frame;
    private double simulationTime;

    /** Creates a buffer sized for {@code capacity} particles. */
    public FrameSnapshot(int capacity) {
        this.positions = new float[capacity * 3];
        this.species = new int[capacity];
    }

    /** Copies the current state of {@code store} into this buffer (physics thread). */
    public synchronized void writeFrom(ParticleStore store, long frame, double simulationTime) {
        int n = store.count();
        double[] src = store.positions();
        for (int i = 0; i < n * 3; i++) {
            positions[i] = (float) src[i];
        }
        System.arraycopy(store.speciesIndices(), 0, species, 0, n);
        this.count = n;
        this.frame = frame;
        this.simulationTime = simulationTime;
    }

    /**
     * Copies the latest frame into the caller's arrays (render thread) and
     * returns the particle count. Arrays must hold at least
     * {@code capacity * 3} / {@code capacity} elements respectively.
     */
    public synchronized int readInto(float[] outPositions, int[] outSpecies) {
        System.arraycopy(positions, 0, outPositions, 0, count * 3);
        System.arraycopy(species, 0, outSpecies, 0, count);
        return count;
    }

    /** Monotonic step counter of the latest frame. */
    public synchronized long frame() {
        return frame;
    }

    /** Simulation-time seconds of the latest frame. */
    public synchronized double simulationTime() {
        return simulationTime;
    }

    /** Particle count of the latest frame. */
    public synchronized int count() {
        return count;
    }
}
