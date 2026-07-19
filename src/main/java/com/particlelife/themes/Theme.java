package com.particlelife.themes;

/**
 * UI color themes. {@link #SYSTEM} resolves to dark or light from the OS
 * preference at apply time.
 */
public enum Theme {

    DARK("Dark", "/styles/theme-dark.css"),
    LIGHT("Light", "/styles/theme-light.css"),
    SYSTEM("System", null);

    private final String displayName;
    private final String stylesheet;

    Theme(String displayName, String stylesheet) {
        this.displayName = displayName;
        this.stylesheet = stylesheet;
    }

    public String displayName() {
        return displayName;
    }

    /** Classpath stylesheet for concrete themes; {@code null} for SYSTEM. */
    public String stylesheet() {
        return stylesheet;
    }
}
