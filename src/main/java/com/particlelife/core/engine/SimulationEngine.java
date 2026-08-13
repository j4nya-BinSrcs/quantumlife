package com.particlelife.core.engine;

import com.particlelife.core.physics.PhysicsEngine;
import com.particlelife.core.simulation.SimulationState;
import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.events.EventBus;
import com.particlelife.events.SimulationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * The simulation loop: a dedicated thread that advances physics at a fixed
 * time step, decoupled from rendering.
 *
 * <p><strong>Threading model.</strong> All mutation of the
 * {@link SimulationWorld} happens on the engine thread. Other threads
 * interact only through:
 * <ul>
 *   <li>{@link #submit(Runnable)} — enqueue a mutation, executed between
 *       steps (the UI's command channel);</li>
 *   <li>the volatile settings objects (validated single-field writes);</li>
 *   <li>the {@link FrameSnapshot} hand-off buffer (synchronized copies).</li>
 * </ul>
 *
 * <p><strong>Pacing.</strong> A fixed-timestep accumulator: real elapsed
 * time (scaled by {@code timeScale}) is banked and consumed in units of
 * {@code timeStep}. Steps per frame are capped so a stall degrades to
 * slow-motion instead of a death spiral. When paused, the thread parks until
 * poked by a command, play, or single-step request.
 */
public final class SimulationEngine implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SimulationEngine.class);

    /** Upper bound on catch-up steps per loop iteration. */
    private static final int MAX_STEPS_PER_FRAME = 4;

    /** How often performance stats are published. */
    private static final long STATS_INTERVAL_NANOS = 500_000_000L;

    private final SimulationWorld world;
    private final PhysicsEngine physicsEngine;
    private final EventBus eventBus;
    private final FrameSnapshot snapshot;

    private final ConcurrentLinkedQueue<Runnable> commandQueue = new ConcurrentLinkedQueue<>();
    private final AtomicReference<SimulationState> state =
            new AtomicReference<>(SimulationState.PAUSED);

    private volatile boolean stepRequested;
    private volatile Thread loopThread;
    private final CountDownLatch stopped = new CountDownLatch(1);

    private long frame;
    private double simulationTime;

    // Stats accumulation (engine thread only).
    private long statsWindowStart;
    private int statsSteps;
    private long statsStepNanos;

    public SimulationEngine(SimulationWorld world, PhysicsEngine physicsEngine, EventBus eventBus) {
        this.world = world;
        this.physicsEngine = physicsEngine;
        this.eventBus = eventBus;
        this.snapshot = new FrameSnapshot(world.store().capacity());
    }

    /** The renderer's read handle. */
    public FrameSnapshot snapshot() {
        return snapshot;
    }

    public SimulationWorld world() {
        return world;
    }

    public SimulationState state() {
        return state.get();
    }

    /** Starts the loop thread (initially paused). Idempotent. */
    public synchronized void start() {
        if (loopThread != null) {
            return;
        }
        Thread thread = new Thread(this::runLoop, "simulation-loop");
        thread.setDaemon(true);
        loopThread = thread;
        thread.start();
        LOG.info("Simulation engine started");
    }

    /** Resumes continuous stepping. */
    public void play() {
        transition(SimulationState.RUNNING);
        wake();
    }

    /** Halts continuous stepping (loop stays alive). */
    public void pause() {
        transition(SimulationState.PAUSED);
    }

    /** Executes exactly one physics step while paused. */
    public void stepOnce() {
        stepRequested = true;
        wake();
    }

    /**
     * Enqueues {@code mutation} for execution on the engine thread between
     * steps — the only legal way for other threads to modify the world.
     */
    public void submit(Runnable mutation) {
        commandQueue.add(mutation);
        wake();
    }

    /** Stops the loop thread and waits for it to exit. */
    @Override
    public synchronized void close() {
        Thread thread = loopThread;
        if (thread == null || state.get() == SimulationState.STOPPED) {
            return;
        }
        transition(SimulationState.STOPPED);
        wake();
        try {
            stopped.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOG.info("Simulation engine stopped after {} steps", frame);
    }

    private void transition(SimulationState next) {
        SimulationState previous = state.getAndSet(next);
        if (previous != next) {
            eventBus.publish(new SimulationEvent.StateChanged(next));
        }
    }

    private void wake() {
        Thread thread = loopThread;
        if (thread != null) {
            LockSupport.unpark(thread);
        }
    }

    private void runLoop() {
        long previousNanos = System.nanoTime();
        double accumulator = 0.0;
        statsWindowStart = previousNanos;

        while (state.get() != SimulationState.STOPPED) {
            drainCommands();

            long now = System.nanoTime();
            double elapsed = (now - previousNanos) / 1e9;
            previousNanos = now;

            boolean running = state.get() == SimulationState.RUNNING;
            double dt = world.physicsSettings().timeStep();

            if (running) {
                accumulator += Math.min(elapsed, dt * MAX_STEPS_PER_FRAME)
                        * world.simulationSettings().timeScale();
                int steps = 0;
                while (accumulator >= dt && steps < MAX_STEPS_PER_FRAME) {
                    step(dt);
                    accumulator -= dt;
                    steps++;
                }
                maybePublishStats(now);
                // Sleep until roughly the next step is due.
                long sleepNanos = (long) ((dt - accumulator) * 1e9);
                if (sleepNanos > 0) {
                    LockSupport.parkNanos(Math.min(sleepNanos, (long) (dt * 1e9)));
                }
            } else if (stepRequested) {
                stepRequested = false;
                step(dt);
                accumulator = 0.0;
                maybePublishStats(now);
            } else {
                accumulator = 0.0;
                LockSupport.park();
            }
        }
        // Tear down GPU resources (and any engine state) on this thread —
        // the GL context was created here.
        physicsEngine.close();
        stopped.countDown();
    }

    private void drainCommands() {
        Runnable command;
        while ((command = commandQueue.poll()) != null) {
            try {
                command.run();
            } catch (RuntimeException e) {
                LOG.error("Engine command failed", e);
            }
        }
    }

    private void step(double dt) {
        long begin = System.nanoTime();
        physicsEngine.step(world.store(), world.matrix(), dt);
        frame++;
        simulationTime += dt;
        snapshot.writeFrom(world.store(), frame, simulationTime);
        statsSteps++;
        statsStepNanos += System.nanoTime() - begin;
    }

    private void maybePublishStats(long now) {
        if (now - statsWindowStart < STATS_INTERVAL_NANOS || statsSteps == 0) {
            return;
        }
        double windowSeconds = (now - statsWindowStart) / 1e9;
        double stepsPerSecond = statsSteps / windowSeconds;
        double stepMillis = statsStepNanos / 1e6 / statsSteps;
        eventBus.publish(new SimulationEvent.StatsUpdated(
                stepsPerSecond, stepMillis, world.store().count(), frame));
        statsWindowStart = now;
        statsSteps = 0;
        statsStepNanos = 0;
    }
}
