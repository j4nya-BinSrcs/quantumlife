package com.particlelife.ui.sidebar;

import com.particlelife.core.physics.BoundaryType;
import com.particlelife.core.physics.PhysicsSettings;
import com.particlelife.forces.ForceFunctionType;
import com.particlelife.ui.UiContext;
import com.particlelife.ui.controls.LabeledSlider;
import com.particlelife.ui.controls.SectionPane;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;

/**
 * Sidebar section: all live physics parameters. Sliders write straight to
 * the volatile {@link PhysicsSettings}; the engine picks changes up on the
 * next step, so every control is safely editable mid-simulation.
 */
public final class PhysicsSection extends SectionPane {

    public PhysicsSection(UiContext ctx) {
        super("Physics", false);

        PhysicsSettings p = ctx.engine().world().physicsSettings();

        LabeledSlider radius = new LabeledSlider("Interaction radius", 2, 100,
                p.interactionRadius(), false, v -> String.format("%.0f", v))
                .onChange(p::setInteractionRadius);

        LabeledSlider force = new LabeledSlider("Force multiplier", 0.0, 10.0,
                p.forceMultiplier(), false, v -> String.format("%.2f", v))
                .onChange(p::setForceMultiplier);

        LabeledSlider friction = new LabeledSlider("Friction half-life", 0.005, 1.0,
                p.frictionHalfLife(), true, v -> String.format("%.0f ms", v * 1000))
                .onChange(p::setFrictionHalfLife);

        LabeledSlider damping = new LabeledSlider("Damping", 0.0, 0.5,
                p.damping(), false, v -> String.format("%.0f%%", v * 100))
                .onChange(p::setDamping);

        LabeledSlider maxVelocity = new LabeledSlider("Max velocity", 1, 1000,
                p.maxVelocity(), true, v -> String.format("%.0f", v))
                .onChange(p::setMaxVelocity);

        LabeledSlider timeStep = new LabeledSlider("Time step", 0.001, 0.05,
                p.timeStep(), true, v -> String.format("%.1f ms", v * 1000))
                .onChange(p::setTimeStep);

        LabeledSlider beta = new LabeledSlider("Repulsion zone β", 0.05, 0.9,
                p.beta(), false, v -> String.format("%.2f", v))
                .onChange(p::setBeta);

        ComboBox<BoundaryType> boundary = new ComboBox<>(
                FXCollections.observableArrayList(BoundaryType.values()));
        boundary.setValue(p.boundaryType());
        boundary.setConverter(displayNameConverter(BoundaryType::displayName));
        boundary.setOnAction(e -> p.setBoundaryType(boundary.getValue()));
        boundary.setMaxWidth(Double.MAX_VALUE);

        ComboBox<ForceFunctionType> kernel = new ComboBox<>(
                FXCollections.observableArrayList(ForceFunctionType.values()));
        kernel.setValue(p.forceFunctionType());
        kernel.setConverter(displayNameConverter(ForceFunctionType::displayName));
        kernel.setOnAction(e -> p.setForceFunctionType(kernel.getValue()));
        kernel.setMaxWidth(Double.MAX_VALUE);

        addRows(radius, force, friction, damping, maxVelocity, timeStep, beta,
                labeledRow("Boundary", boundary),
                labeledRow("Force kernel", kernel));
    }

    private static HBox labeledRow(String text, javafx.scene.Node control) {
        Label label = new Label(text);
        label.getStyleClass().add("control-label");
        label.setMinWidth(90);
        HBox row = new HBox(8, label, control);
        HBox.setHgrow(control, Priority.ALWAYS);
        return row;
    }

    private static <T> StringConverter<T> displayNameConverter(
            java.util.function.Function<T, String> nameOf) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : nameOf.apply(value);
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        };
    }
}
