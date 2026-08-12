package com.particlelife.ui.sidebar;

import com.particlelife.core.commands.MatrixCommands;
import com.particlelife.core.simulation.SimulationState;
import com.particlelife.events.SimulationEvent;
import com.particlelife.ui.UiContext;
import com.particlelife.ui.controls.LabeledSlider;
import com.particlelife.ui.controls.SectionPane;
import com.particlelife.utils.FxThreads;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sidebar section: run control (play/pause/step/reset/restart/randomize),
 * simulation speed, and live performance readouts.
 */
public final class SimulationSection extends SectionPane {

    private final ToggleButton playPause = new ToggleButton("▶ Play");
    private final Label statsLabel = new Label("—");

    public SimulationSection(UiContext ctx) {
        super("Simulation", true);

        playPause.setOnAction(e -> {
            if (playPause.isSelected()) {
                ctx.engine().play();
            } else {
                ctx.engine().pause();
            }
        });

        Button step = new Button("Step");
        step.setOnAction(e -> ctx.engine().stepOnce());

        Button reset = new Button("Reset");
        reset.setOnAction(e -> {
            ctx.engine().pause();
            ctx.commands().execute(new MatrixCommands.Respawn());
        });

        Button restart = new Button("Restart");
        restart.setOnAction(e -> {
            ctx.commands().execute(new MatrixCommands.Respawn());
            ctx.engine().play();
        });

        Button randomize = new Button("Randomize");
        randomize.setOnAction(e -> {
            long seed = ThreadLocalRandom.current().nextLong();
            ctx.engine().submit(() ->
                    ctx.engine().world().simulationSettings().setSeed(seed));
            ctx.commands().execute(new MatrixCommands.Randomize(seed));
            ctx.commands().execute(new MatrixCommands.Respawn());
        });

        FlowPane buttons = new FlowPane(8, 8, playPause, step, reset, restart, randomize);

        LabeledSlider speed = new LabeledSlider("Simulation speed", 0.05, 10.0,
                ctx.engine().world().simulationSettings().timeScale(), true,
                v -> String.format("%.2f×", v))
                .onChange(v -> ctx.engine().world().simulationSettings().setTimeScale(v));

        statsLabel.getStyleClass().add("hover-label");
        addRows(buttons, speed, statsLabel);

        // Reflect engine state and performance on the FX thread.
        ctx.eventBus().subscribe(SimulationEvent.StateChanged.class, event ->
                FxThreads.onFx(() -> {
                    boolean running = event.state() == SimulationState.RUNNING;
                    playPause.setSelected(running);
                    playPause.setText(running ? "⏸ Pause" : "▶ Play");
                }));
        ctx.eventBus().subscribe(SimulationEvent.StatsUpdated.class, event ->
                FxThreads.onFx(() -> statsLabel.setText(
                        "physics %.0f steps/s · %.2f ms/step · frame %,d".formatted(
                                event.stepsPerSecond(), event.stepMillis(), event.frame()))));
    }
}
