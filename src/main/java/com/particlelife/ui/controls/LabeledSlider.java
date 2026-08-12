package com.particlelife.ui.controls;

import javafx.geometry.HPos;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * A labeled slider row with a live value readout — the workhorse control of
 * the sidebar. Supports linear and logarithmic scales (log is essential for
 * ranges like particle count 100..50000).
 */
public final class LabeledSlider extends GridPane {

    private final Slider slider;
    private final Label valueLabel = new Label();
    private final DoubleFunction<String> formatter;
    private final boolean logarithmic;
    private final double min;
    private final double max;
    private DoubleConsumer onChange = v -> { };
    private boolean muteCallback;

    /**
     * @param labelText  row label
     * @param min        minimum model value
     * @param max        maximum model value
     * @param initial    initial model value
     * @param logarithmic use a log-scaled track
     * @param formatter  model value -> readout text
     */
    public LabeledSlider(String labelText, double min, double max, double initial,
                         boolean logarithmic, DoubleFunction<String> formatter) {
        this.min = min;
        this.max = max;
        this.logarithmic = logarithmic;
        this.formatter = formatter;

        Label label = new Label(labelText);
        label.getStyleClass().add("control-label");
        valueLabel.getStyleClass().add("value-label");

        slider = logarithmic
                ? new Slider(Math.log(min), Math.log(max), Math.log(clampInitial(initial)))
                : new Slider(min, max, clampInitial(initial));
        slider.valueProperty().addListener((obs, oldV, newV) -> {
            double value = modelValue();
            valueLabel.setText(this.formatter.apply(value));
            if (!muteCallback) {
                onChange.accept(value);
            }
        });
        valueLabel.setText(formatter.apply(clampInitial(initial)));

        add(label, 0, 0);
        add(valueLabel, 1, 0);
        add(slider, 0, 1, 2, 1);
        ColumnConstraints grow = new ColumnConstraints();
        grow.setHgrow(Priority.ALWAYS);
        ColumnConstraints right = new ColumnConstraints();
        right.setHalignment(HPos.RIGHT);
        getColumnConstraints().addAll(grow, right);
        getStyleClass().add("labeled-slider");
    }

    private double clampInitial(double v) {
        return Math.max(min, Math.min(max, v));
    }

    /** Registers the change callback (model values, not track positions). */
    public LabeledSlider onChange(DoubleConsumer onChange) {
        this.onChange = onChange;
        return this;
    }

    /** Current model value. */
    public double modelValue() {
        return logarithmic ? Math.exp(slider.getValue()) : slider.getValue();
    }

    /** Moves the slider without firing the callback (external sync). */
    public void setModelValue(double value) {
        muteCallback = true;
        try {
            double clamped = clampInitial(value);
            slider.setValue(logarithmic ? Math.log(clamped) : clamped);
            valueLabel.setText(formatter.apply(clamped));
        } finally {
            muteCallback = false;
        }
    }
}
