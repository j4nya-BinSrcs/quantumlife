package com.particlelife.ui.sidebar;

import com.particlelife.database.repository.Preset;
import com.particlelife.ui.UiContext;
import com.particlelife.ui.controls.SectionPane;
import com.particlelife.utils.FxThreads;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Sidebar section: preset persistence — save/load/delete/rename against the
 * SQLite store, plus JSON file export/import. Storage calls that round-trip
 * through the engine complete asynchronously; UI refreshes on completion via
 * {@link FxThreads#onFx}.
 */
public final class DatabaseSection extends SectionPane {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final UiContext ctx;
    private final ObservableList<Preset> presets = FXCollections.observableArrayList();
    private final ListView<Preset> list = new ListView<>(presets);
    private final TextField nameField = new TextField();

    public DatabaseSection(UiContext ctx) {
        super("Presets", false);
        this.ctx = ctx;

        nameField.setPromptText("Preset name…");
        Button save = new Button("Save");
        save.getStyleClass().add("primary");
        save.setOnAction(e -> savePreset());
        HBox saveRow = new HBox(8, nameField, save);
        HBox.setHgrow(nameField, Priority.ALWAYS);

        list.setPrefHeight(160);
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Preset preset, boolean empty) {
                super.updateItem(preset, empty);
                setText(empty || preset == null ? null
                        : "%s  ·  %d species, %,d particles  ·  %s".formatted(
                                preset.name(), preset.speciesCount(), preset.particleCount(),
                                DATE_FORMAT.format(preset.modifiedAt())));
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, preset) -> {
            if (preset != null) {
                nameField.setText(preset.name());
            }
        });

        Button load = new Button("Load");
        load.setOnAction(e -> withSelection(this::loadPreset));
        Button delete = new Button("Delete");
        delete.setOnAction(e -> withSelection(this::deletePreset));
        Button rename = new Button("Rename…");
        rename.setOnAction(e -> withSelection(this::renamePreset));
        Button export = new Button("Export…");
        export.setOnAction(e -> withSelection(this::exportPreset));
        Button importButton = new Button("Import…");
        importButton.setOnAction(e -> importPreset());
        FlowPane actions = new FlowPane(8, 8, load, rename, delete, export, importButton);

        addRows(saveRow, list, actions);
        refresh();
    }

    private void withSelection(java.util.function.Consumer<Preset> action) {
        Preset selected = list.getSelectionModel().getSelectedItem();
        if (selected != null) {
            action.accept(selected);
        }
    }

    private void savePreset() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showError("Save preset", "Enter a preset name first.");
            return;
        }
        ctx.presets().saveCurrent(name).whenComplete((preset, error) ->
                FxThreads.onFx(() -> {
                    if (error != null) {
                        showError("Save failed", error.getMessage());
                    } else {
                        refresh();
                    }
                }));
    }

    private void loadPreset(Preset preset) {
        ctx.presets().load(preset.name()).whenComplete((ok, error) ->
                FxThreads.onFx(() -> {
                    if (error != null) {
                        showError("Load failed", error.getMessage());
                    } else if (!ok) {
                        showError("Load failed", "Preset '" + preset.name() + "' no longer exists.");
                        refresh();
                    }
                }));
    }

    private void deletePreset(Preset preset) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete preset '" + preset.name() + "'?");
        confirm.setHeaderText(null);
        Optional<javafx.scene.control.ButtonType> answer = confirm.showAndWait();
        if (answer.isPresent() && answer.get() == javafx.scene.control.ButtonType.OK) {
            boolean deleted = ctx.presets().delete(preset.name());
            if (!deleted) {
                showError("Delete failed", "Preset '" + preset.name() + "' no longer exists.");
            }
            refresh();
        }
    }

    private void renamePreset(Preset preset) {
        TextInputDialog dialog = new TextInputDialog(preset.name());
        dialog.setHeaderText("Rename preset '" + preset.name() + "'");
        dialog.setContentText("New name:");
        dialog.showAndWait().ifPresent(newName -> {
            try {
                if (!newName.isBlank() && !newName.equals(preset.name())) {
                    boolean renamed = ctx.presets().rename(preset.name(), newName.trim());
                    if (!renamed) {
                        showError("Rename failed", "Preset '" + preset.name() + "' no longer exists.");
                    }
                    refresh();
                }
            } catch (RuntimeException ex) {
                showError("Rename failed", ex.getMessage());
            }
        });
    }

    private void exportPreset(Preset preset) {
        FileChooser chooser = jsonChooser("Export preset");
        chooser.setInitialFileName(preset.name().replaceAll("\\W+", "-").toLowerCase() + ".json");
        var file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            ctx.presets().exportToFile(preset.name(), file.toPath());
        } catch (IOException | RuntimeException ex) {
            showError("Export failed", ex.getMessage());
        }
    }

    private void importPreset() {
        FileChooser chooser = jsonChooser("Import preset");
        var file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        String name = file.getName().replaceFirst("\\.json$", "");
        try {
            ctx.presets().importFromFile(file.toPath(), name).whenComplete((preset, error) ->
                    FxThreads.onFx(() -> {
                        if (error != null) {
                            showError("Import failed", error.getMessage());
                        } else {
                            refresh();
                        }
                    }));
        } catch (IOException | RuntimeException ex) {
            showError("Import failed", ex.getMessage());
        }
    }

    /** Reloads the preset list from the repository. */
    public void refresh() {
        presets.setAll(ctx.presets().list());
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
