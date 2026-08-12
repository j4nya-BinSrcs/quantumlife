package com.particlelife.ui.sidebar;

import com.particlelife.core.commands.MatrixCommands;
import com.particlelife.core.simulation.SimulationSettings;
import com.particlelife.events.SimulationEvent;
import com.particlelife.species.SpeciesType;
import com.particlelife.ui.UiContext;
import com.particlelife.ui.controls.LabeledSlider;
import com.particlelife.ui.controls.SectionPane;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sidebar section: population size, species count, spawn shape, seed.
 * Population/species edits take effect on the next respawn (button or the
 * sections that trigger one), keeping slider drags cheap.
 */
public final class ParticlesSection extends SectionPane {

    private final TextField seedField = new TextField();

    public ParticlesSection(UiContext ctx) {
        super("Particles", true);

        SimulationSettings settings = ctx.engine().world().simulationSettings();

        LabeledSlider count = new LabeledSlider("Particle count", 50, SimulationSettings.MAX_PARTICLES,
                settings.particleCount(), true, v -> String.format("%,d", Math.round(v)))
                .onChange(v -> settings.setParticleCount((int) Math.round(v)));

        LabeledSlider species = new LabeledSlider("Species count", 1, SpeciesType.MAX_SPECIES,
                settings.speciesCount(), false, v -> String.valueOf(Math.round(v)))
                .onChange(v -> ctx.engine().submit(() -> {
                    ctx.engine().world().setSpeciesCount((int) Math.round(v));
                    ctx.eventBus().publish(new SimulationEvent.SpeciesChanged());
                }));

        double initialRadius = ctx.engine().world().species().all().get(0).radius();
        LabeledSlider radius = new LabeledSlider("Particle radius", 0.2, 4.0, initialRadius, false,
                v -> String.format("%.2f", v))
                .onChange(v -> ctx.engine().submit(() -> {
                    ctx.engine().world().species().all().forEach(s -> s.setRadius(v));
                    ctx.eventBus().publish(new SimulationEvent.SpeciesChanged());
                }));

        LabeledSlider spawnRadius = new LabeledSlider("Spawn radius", 0.05, 1.0,
                settings.spawnRadiusFraction(), false, v -> String.format("%.0f%%", v * 100))
                .onChange(settings::setSpawnRadiusFraction);

        seedField.setText(String.valueOf(settings.seed()));
        seedField.setOnAction(e -> parseSeed(settings));
        Button dice = new Button("🎲");
        dice.setOnAction(e -> {
            long seed = ThreadLocalRandom.current().nextLong();
            seedField.setText(String.valueOf(seed));
            settings.setSeed(seed);
        });
        Label seedLabel = new Label("Seed");
        seedLabel.getStyleClass().add("control-label");
        HBox seedRow = new HBox(8, seedLabel, seedField, dice);
        HBox.setHgrow(seedField, Priority.ALWAYS);

        Button respawn = new Button("Respawn");
        respawn.getStyleClass().add("primary");
        respawn.setOnAction(e -> {
            parseSeed(settings);
            ctx.commands().execute(new MatrixCommands.Respawn());
        });

        addRows(count, species, radius, spawnRadius, seedRow, respawn);
    }

    private void parseSeed(SimulationSettings settings) {
        try {
            settings.setSeed(Long.parseLong(seedField.getText().trim()));
        } catch (NumberFormatException ex) {
            seedField.setText(String.valueOf(settings.seed()));
        }
    }
}
