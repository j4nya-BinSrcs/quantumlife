package com.particlelife.ui.sidebar;

import com.particlelife.core.commands.MatrixCommands;
import com.particlelife.events.SimulationEvent;
import com.particlelife.serialization.JsonSerializer;
import com.particlelife.ui.UiContext;
import com.particlelife.ui.controls.MatrixHeatmapView;
import com.particlelife.ui.controls.SectionPane;
import com.particlelife.utils.FxThreads;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.FlowPane;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sidebar section: the editable heatmap plus matrix-level operations —
 * randomize, reset, symmetric mode, undo/redo, JSON import/export.
 */
public final class MatrixSection extends SectionPane {

    private final UiContext ctx;
    private final MatrixHeatmapView heatmap;
    private final CheckBox symmetric = new CheckBox("Symmetric mode");

    public MatrixSection(UiContext ctx) {
        super("Attraction matrix", true);
        this.ctx = ctx;
        this.heatmap = new MatrixHeatmapView(ctx.engine(), ctx.commands());

        Button randomize = new Button("Randomize");
        randomize.setOnAction(e -> ctx.commands().execute(
                new MatrixCommands.Randomize(ThreadLocalRandom.current().nextLong())));

        Button reset = new Button("Reset");
        reset.setOnAction(e -> ctx.commands().execute(new MatrixCommands.Reset()));

        Button undo = new Button("↶ Undo");
        undo.setOnAction(e -> ctx.commands().undo());
        Button redo = new Button("↷ Redo");
        redo.setOnAction(e -> ctx.commands().redo());

        undo.setDisable(!ctx.commands().canUndo());
        redo.setDisable(!ctx.commands().canRedo());

        Button export = new Button("Export…");
        export.setOnAction(e -> exportMatrix());
        Button importButton = new Button("Import…");
        importButton.setOnAction(e -> importMatrix());

        symmetric.setSelected(ctx.engine().world().matrix().isSymmetric());
        symmetric.setOnAction(e ->
                ctx.commands().execute(new MatrixCommands.SetSymmetric(symmetric.isSelected())));

        FlowPane buttons = new FlowPane(8, 8, randomize, reset, undo, redo, importButton, export);
        addRows(heatmap, symmetric, buttons);

        ctx.eventBus().subscribe(SimulationEvent.MatrixChanged.class, e ->
                FxThreads.onFx(() -> {
                    heatmap.redraw();
                    symmetric.setSelected(ctx.engine().world().matrix().isSymmetric());
                }));
        ctx.eventBus().subscribe(SimulationEvent.SpeciesChanged.class,
                e -> FxThreads.onFx(heatmap::redraw));
        ctx.eventBus().subscribe(SimulationEvent.HistoryChanged.class, e ->
                FxThreads.onFx(() -> {
                    undo.setDisable(!e.canUndo());
                    redo.setDisable(!e.canRedo());
                }));
    }

    private void exportMatrix() {
        FileChooser chooser = jsonChooser("Export attraction matrix");
        chooser.setInitialFileName("attraction-matrix.json");
        var file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            double[][] matrix = ctx.engine().world().matrix().toArray();
            Files.writeString(Path.of(file.getAbsolutePath()), JsonSerializer.toJson(matrix));
        } catch (IOException ex) {
            showError("Export failed", ex.getMessage());
        }
    }

    private void importMatrix() {
        FileChooser chooser = jsonChooser("Import attraction matrix");
        var file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            String json = Files.readString(Path.of(file.getAbsolutePath()));
            double[][] matrix = JsonSerializer.fromJson(json, double[][].class);
            MatrixCommands.validateImport(matrix,
                    ctx.engine().world().species().count());
            ctx.commands().execute(new MatrixCommands.SetAll(matrix));
        } catch (IOException | RuntimeException ex) {
            showError("Import failed", ex.getMessage());
        }
    }

    private static FileChooser jsonChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON files", "*.json"));
        return chooser;
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.show();
    }
}
