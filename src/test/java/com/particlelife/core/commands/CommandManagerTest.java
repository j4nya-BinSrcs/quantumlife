package com.particlelife.core.commands;

import com.particlelife.core.engine.SimulationEngine;
import com.particlelife.core.physics.PhysicsEngine;
import com.particlelife.core.physics.PhysicsSettings;
import com.particlelife.core.simulation.SimulationSettings;
import com.particlelife.core.simulation.SimulationWorld;
import com.particlelife.events.EventBus;
import com.particlelife.events.SimulationEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
class CommandManagerTest {

    private EventBus eventBus;
    private SimulationWorld world;
    private SimulationEngine engine;
    private CommandManager commands;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        PhysicsSettings physicsSettings = new PhysicsSettings();
        world = new SimulationWorld(new SimulationSettings(), physicsSettings);
        engine = new SimulationEngine(world, new PhysicsEngine(physicsSettings), eventBus);
        engine.start(); // paused loop still drains the command queue
        commands = new CommandManager(engine, eventBus);
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    private void sync() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        engine.submit(latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS), "engine queue did not drain");
    }

    @Test
    void editCellAppliesValue() throws Exception {
        commands.execute(new MatrixCommands.EditCell(1, 2, 0.8));
        sync();
        assertEquals(0.8, world.matrix().get(1, 2), 1e-12);
    }

    @Test
    void undoRestoresPreviousMatrix() throws Exception {
        commands.execute(new MatrixCommands.EditCell(0, 0, 0.5));
        commands.execute(new MatrixCommands.EditCell(0, 0, -0.25));
        sync();
        assertEquals(-0.25, world.matrix().get(0, 0), 1e-12);

        commands.undo();
        sync();
        assertEquals(0.5, world.matrix().get(0, 0), 1e-12);

        commands.undo();
        sync();
        assertEquals(0.0, world.matrix().get(0, 0), 1e-12);
    }

    @Test
    void redoReappliesUndoneCommand() throws Exception {
        commands.execute(new MatrixCommands.Randomize(5L));
        sync();
        double value = world.matrix().get(2, 3);

        commands.undo();
        sync();
        assertEquals(0.0, world.matrix().get(2, 3), 1e-12);

        commands.redo();
        sync();
        assertEquals(value, world.matrix().get(2, 3), 1e-12);
    }

    @Test
    void newCommandClearsRedoHistory() throws Exception {
        commands.execute(new MatrixCommands.EditCell(0, 1, 0.1));
        commands.undo();
        commands.execute(new MatrixCommands.EditCell(0, 1, 0.9));
        sync();
        assertFalse(commands.canRedo());
        assertTrue(commands.canUndo());
    }

    @Test
    void undoWithEmptyHistoryIsSafe() throws Exception {
        commands.undo();
        commands.redo();
        sync();
        assertFalse(commands.canUndo());
        assertFalse(commands.canRedo());
    }

    @Test
    void undoRestoresSymmetricFlag() throws Exception {
        commands.execute(new MatrixCommands.SetSymmetric(true));
        sync();
        assertTrue(world.matrix().isSymmetric());

        commands.undo();
        sync();
        assertFalse(world.matrix().isSymmetric());
    }

    @Test
    void matrixCommandsPublishMatrixChanged() throws Exception {
        AtomicInteger events = new AtomicInteger();
        eventBus.subscribe(SimulationEvent.MatrixChanged.class, e -> events.incrementAndGet());

        commands.execute(new MatrixCommands.Randomize(1L));
        commands.undo();
        sync();
        assertEquals(2, events.get(), "execute and undo each publish");
    }

    @Test
    void respawnCommandPublishesWorldRespawned() throws Exception {
        AtomicInteger count = new AtomicInteger(-1);
        eventBus.subscribe(SimulationEvent.WorldRespawned.class, e -> count.set(e.particleCount()));

        commands.execute(new MatrixCommands.Respawn());
        sync();
        assertEquals(world.simulationSettings().particleCount(), count.get());
        assertFalse(commands.canUndo(), "respawn is not undoable");
    }

    @Test
    void historyChangedEventsReflectUndoRedoStackState() throws Exception {
        AtomicInteger undo = new AtomicInteger(-1);
        AtomicInteger redo = new AtomicInteger(-1);
        eventBus.subscribe(SimulationEvent.HistoryChanged.class, e -> {
            undo.set(e.canUndo() ? 1 : 0);
            redo.set(e.canRedo() ? 1 : 0);
        });

        commands.execute(new MatrixCommands.EditCell(0, 0, 0.5));
        sync();
        assertEquals(1, undo.get(), "history with one entry enables undo");
        assertEquals(0, redo.get(), "no redo history yet");

        commands.undo();
        sync();
        assertEquals(0, undo.get());
        assertEquals(1, redo.get());

        commands.redo();
        sync();
        assertEquals(1, undo.get());
        assertEquals(0, redo.get());
    }
}
