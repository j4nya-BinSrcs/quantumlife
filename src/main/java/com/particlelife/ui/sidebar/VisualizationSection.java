package com.particlelife.ui.sidebar;

import com.particlelife.render.RenderMode;
import com.particlelife.render.RenderOptions;
import com.particlelife.ui.UiContext;
import com.particlelife.ui.controls.LabeledSlider;
import com.particlelife.ui.controls.SectionPane;
import com.particlelife.utils.FxThreads;
import javafx.collections.FXCollections;
import javafx.scene.PerspectiveCamera;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * Sidebar section: visual options (particle size, background, world decor,
 * trails, glow, render strategy) and camera actions (reset, focus,
 * auto-orbit, projection).
 */
public final class VisualizationSection extends SectionPane {

    /** Lens presets that map onto a {@link PerspectiveCamera} field of view. */
    private enum Projection {
        PERSPECTIVE("Perspective 45°", 45),
        WIDE("Wide 65°", 65),
        TELEPHOTO("Telephoto 30°", 30);

        private final String displayName;
        private final double fov;

        Projection(String displayName, double fov) {
            this.displayName = displayName;
            this.fov = fov;
        }
    }

    public VisualizationSection(UiContext ctx) {
        super("Visualization", false);

        RenderOptions options = ctx.view().options();

        LabeledSlider size = new LabeledSlider("Particle size", 0.1, 6.0,
                options.particleScale(), false, v -> String.format("%.1f×", v))
                .onChange(options::setParticleScale);

        ColorPicker background = new ColorPicker(ctx.themes().viewportBackground());
        background.setOnAction(e -> ctx.view().setBackground(background.getValue()));
        ctx.themes().addListener(t ->
                FxThreads.onFx(() -> background.setValue(ctx.themes().viewportBackground())));
        HBox backgroundRow = labeledRow("Background", background);

        CheckBox grid = check("Grid", options.showGrid(), options::setShowGrid);
        CheckBox axes = check("Axes", options.showAxes(), options::setShowAxes);
        CheckBox bounds = check("Bounding box", options.showBoundingBox(), options::setShowBoundingBox);
        CheckBox trails = check("Trails", options.trails(), options::setTrails);
        CheckBox glow = check("Glow", options.glow(), options::setGlow);
        FlowPane toggles = new FlowPane(10, 8, grid, axes, bounds, trails, glow);

        ComboBox<RenderMode> mode = new ComboBox<>(
                FXCollections.observableArrayList(RenderMode.values()));
        mode.setValue(options.renderMode());
        mode.setOnAction(e -> options.setRenderMode(mode.getValue()));
        mode.setMaxWidth(Double.MAX_VALUE);

        ComboBox<Projection> projection = new ComboBox<>(
                FXCollections.observableArrayList(Projection.values()));
        projection.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Projection p) {
                return p == null ? "" : p.displayName;
            }

            @Override
            public Projection fromString(String s) {
                return null;
            }
        });
        projection.setValue(fromFieldOfView(ctx.view().camera().camera().getFieldOfView()));
        projection.setOnAction(e -> {
            Projection p = projection.getValue();
            if (p != null) {
                ctx.view().camera().camera().setFieldOfView(p.fov);
            }
        });
        projection.setMaxWidth(Double.MAX_VALUE);

        Button resetCamera = new Button("Reset camera");
        resetCamera.setOnAction(e -> ctx.view().camera().reset());
        Button focus = new Button("Focus origin");
        focus.setOnAction(e -> ctx.view().camera().focusOrigin());
        ToggleButton orbit = new ToggleButton("Auto orbit");
        orbit.setSelected(ctx.view().camera().isAutoOrbit());
        orbit.setOnAction(e -> ctx.view().camera().setAutoOrbit(orbit.isSelected()));
        FlowPane cameraButtons = new FlowPane(8, 8, resetCamera, focus, orbit);

        addRows(size, backgroundRow, toggles,
                labeledRow("Renderer", mode),
                labeledRow("Projection", projection),
                cameraButtons);
    }

    private static CheckBox check(String text, boolean initial,
                                  java.util.function.Consumer<Boolean> setter) {
        CheckBox box = new CheckBox(text);
        box.setSelected(initial);
        box.setOnAction(e -> setter.accept(box.isSelected()));
        return box;
    }

    private static HBox labeledRow(String text, javafx.scene.Node control) {
        Label label = new Label(text);
        label.getStyleClass().add("control-label");
        label.setMinWidth(90);
        HBox row = new HBox(8, label, control);
        HBox.setHgrow(control, Priority.ALWAYS);
        return row;
    }

    private static Projection fromFieldOfView(double fov) {
        for (Projection p : Projection.values()) {
            if (Double.compare(p.fov, fov) == 0) {
                return p;
            }
        }
        return Projection.PERSPECTIVE;
    }
}
