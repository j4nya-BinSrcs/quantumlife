package com.particlelife.ui.controls;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless UI test: the heatmap's color scale is pure data mapping and
 * needs no JavaFX toolkit.
 */
class HeatmapColorScaleTest {

    @Test
    void extremesMatchSpecifiedColors() {
        Color repulsion = HeatmapColorScale.colorFor(-1.0);
        Color attraction = HeatmapColorScale.colorFor(1.0);
        // Deep blue: blue dominant.
        assertTrue(repulsion.getBlue() > 0.6 && repulsion.getRed() < 0.2);
        // Red: red dominant.
        assertTrue(attraction.getRed() > 0.8 && attraction.getBlue() < 0.2);
    }

    @Test
    void neutralIsNearWhite() {
        Color neutral = HeatmapColorScale.colorFor(0.0);
        assertTrue(neutral.getRed() > 0.9);
        assertTrue(neutral.getGreen() > 0.9);
        assertTrue(neutral.getBlue() > 0.9);
    }

    @Test
    void midpointsPassThroughLightBlueAndOrange() {
        Color lightBlue = HeatmapColorScale.colorFor(-0.4);
        assertTrue(lightBlue.getBlue() > lightBlue.getRed(), "negative side is blue");
        assertTrue(lightBlue.getBrightness() > HeatmapColorScale.colorFor(-1.0).getBrightness(),
                "light blue is lighter than deep blue");

        Color orange = HeatmapColorScale.colorFor(0.4);
        assertTrue(orange.getRed() > orange.getBlue(), "positive side is warm");
        assertTrue(orange.getGreen() > HeatmapColorScale.colorFor(1.0).getGreen(),
                "orange has more green than pure red");
    }

    @Test
    void warmthIncreasesMonotonicallyWithValue() {
        double previous = -Double.MAX_VALUE;
        for (double v = -1.0; v <= 1.0; v += 0.05) {
            Color c = HeatmapColorScale.colorFor(v);
            double warmth = c.getRed() - c.getBlue();
            assertTrue(warmth >= previous - 1e-9,
                    "red-vs-blue balance must not decrease (v=" + v + ")");
            previous = warmth;
        }
    }

    @Test
    void outOfRangeValuesClamp() {
        assertEquals(HeatmapColorScale.colorFor(-1.0), HeatmapColorScale.colorFor(-5.0));
        assertEquals(HeatmapColorScale.colorFor(1.0), HeatmapColorScale.colorFor(7.0));
    }
}
