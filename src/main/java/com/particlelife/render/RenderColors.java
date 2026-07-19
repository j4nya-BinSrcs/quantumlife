package com.particlelife.render;

import javafx.scene.paint.Color;

/**
 * Color conversion helpers shared by renderers and UI.
 */
public final class RenderColors {

    private RenderColors() {
    }

    /** Converts packed 24-bit RGB to a JavaFX color. */
    public static Color fromRgb(int rgb) {
        return Color.rgb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    /** Converts a JavaFX color to packed 24-bit RGB. */
    public static int toRgb(Color color) {
        int r = (int) Math.round(color.getRed() * 255);
        int g = (int) Math.round(color.getGreen() * 255);
        int b = (int) Math.round(color.getBlue() * 255);
        return (r << 16) | (g << 8) | b;
    }
}
