package com.particlelife.ui.sidebar;

import com.particlelife.events.SimulationEvent;
import com.particlelife.math.DeterministicRandom;
import com.particlelife.render.RenderColors;
import com.particlelife.species.Species;
import com.particlelife.ui.UiContext;
import com.particlelife.ui.controls.SectionPane;
import com.particlelife.utils.FxThreads;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sidebar section: per-species rows (color, rename, enable/disable) plus
 * bulk color randomization. Rows are rebuilt on
 * {@link SimulationEvent.SpeciesChanged} so external changes (species count
 * slider, preset load) stay in sync.
 */
public final class SpeciesSection extends SectionPane {

    private final UiContext ctx;
    private final VBox rows = new VBox(6);

    public SpeciesSection(UiContext ctx) {
        super("Species", false);
        this.ctx = ctx;

        Button randomColors = new Button("Random colors");
        randomColors.setOnAction(e -> {
            long seed = ThreadLocalRandom.current().nextLong();
            ctx.engine().submit(() -> {
                ctx.engine().world().species().randomizeColors(new DeterministicRandom(seed));
                ctx.eventBus().publish(new SimulationEvent.SpeciesChanged());
            });
        });

        addRows(rows, randomColors);
        rebuildRows();

        ctx.eventBus().subscribe(SimulationEvent.SpeciesChanged.class,
                e -> FxThreads.onFx(this::rebuildRows));
    }

    private void rebuildRows() {
        rows.getChildren().clear();
        for (Species species : ctx.engine().world().species().all()) {
            rows.getChildren().add(buildRow(species));
        }
        ctx.view().refreshSpecies();
    }

    private HBox buildRow(Species species) {
        int index = species.index();

        ColorPicker color = new ColorPicker(RenderColors.fromRgb(species.colorRgb()));
        color.getStyleClass().add("button");
        color.setPrefWidth(46);
        color.setOnAction(e -> {
            int rgb = RenderColors.toRgb(color.getValue());
            submitSpeciesEdit(index, s -> s.setColorRgb(rgb));
        });

        TextField name = new TextField(species.name());
        name.setOnAction(e -> {
            String text = name.getText().isBlank() ? species.name() : name.getText().trim();
            submitSpeciesEdit(index, s -> s.setName(text));
        });
        HBox.setHgrow(name, Priority.ALWAYS);

        CheckBox enabled = new CheckBox();
        enabled.setSelected(species.isEnabled());
        enabled.setOnAction(e -> {
            boolean value = enabled.isSelected();
            ctx.engine().submit(() -> {
                ctx.engine().world().species().get(index).setEnabled(value);
                if (!value) {
                    ctx.engine().world().cullDisabledSpecies();
                } else {
                    ctx.engine().world().respawn();
                }
                ctx.eventBus().publish(new SimulationEvent.SpeciesChanged());
                ctx.eventBus().publish(new SimulationEvent.MatrixChanged());
            });
        });

        return new HBox(8, color, name, enabled);
    }

    private void submitSpeciesEdit(int index, java.util.function.Consumer<Species> edit) {
        ctx.engine().submit(() -> {
            edit.accept(ctx.engine().world().species().get(index));
            ctx.eventBus().publish(new SimulationEvent.SpeciesChanged());
        });
    }
}
