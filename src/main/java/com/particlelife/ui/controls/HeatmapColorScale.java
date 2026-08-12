package com.particlelife.ui.controls;

import javafx.scene.paint.Color;

/**
 * The diverging color scale of the attraction-matrix heatmap:
 *
 * <pre>
 * -1.0        -0.4        0.0        +0.4        +1.0
 * deep blue → light blue → white → orange → red
 * (strong repulsion)     (neutral)     (strong attraction)
 * </pre>
 *
 * <p>Pure value→color mapping (no scene-graph dependencies), so it is unit
 * tested headlessly.
 */
public final class HeatmapColorScale {

    private static final double[] STOPS = {-1.0, -0.4, 0.0, 0.4, 1.0};
    private static final Color[] COLORS = {
            Color.rgb(24, 42, 190),    // deep blue
            Color.rgb(88, 166, 242),   // light blue
            Color.rgb(245, 245, 245),  // white
            Color.rgb(245, 158, 66),   // orange
            Color.rgb(226, 42, 35)     // red
    };

    private HeatmapColorScale() {
    }

    /** Maps an attraction value in {@code [-1, 1]} to its heatmap color. */
    public static Color colorFor(double value) {
        double v = Math.max(-1.0, Math.min(1.0, value));
        for (int i = 0; i < STOPS.length - 1; i++) {
            if (v <= STOPS[i + 1]) {
                double t = (v - STOPS[i]) / (STOPS[i + 1] - STOPS[i]);
                return COLORS[i].interpolate(COLORS[i + 1], t);
            }
        }
        return COLORS[COLORS.length - 1];
    }
}
