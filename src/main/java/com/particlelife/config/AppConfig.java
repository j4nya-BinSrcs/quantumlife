package com.particlelife.config;

import com.particlelife.serialization.PresetData;

/**
 * Everything remembered between application runs: window geometry, theme,
 * camera pose, sidebar state, and the last session's full simulation setup.
 *
 * <p>Immutable record — updates go through {@code with*} copies so the
 * config service can atomically swap and persist whole states.
 */
public record AppConfig(
        WindowConfig window,
        String theme,
        CameraConfig camera,
        SidebarConfig sidebar,
        RenderConfig render,
        PresetData session) {

    /** Window geometry and state. */
    public record WindowConfig(double width, double height, double x, double y, boolean maximized) {
        public static WindowConfig defaults() {
            return new WindowConfig(1440, 900, -1, -1, false);
        }
    }

    /** Orbit camera pose. */
    public record CameraConfig(double yawDegrees, double pitchDegrees, double distance) {
        public static CameraConfig defaults() {
            return new CameraConfig(-30, -20, 420);
        }
    }

    /** Sidebar layout state. */
    public record SidebarConfig(boolean visible, double width) {
        public static SidebarConfig defaults() {
            return new SidebarConfig(true, 340);
        }
    }

    /** Visualization toggles that live outside the physics model. */
    public record RenderConfig(
            double particleScale,
            boolean showGrid,
            boolean showAxes,
            boolean showBoundingBox,
            boolean trails,
            boolean glow,
            boolean autoOrbit) {
        public static RenderConfig defaults() {
            return new RenderConfig(1.0, true, true, true, false, true, false);
        }
    }

    /** A fresh config with all defaults and no saved session. */
    public static AppConfig defaults() {
        return new AppConfig(
                WindowConfig.defaults(),
                "DARK",
                CameraConfig.defaults(),
                SidebarConfig.defaults(),
                RenderConfig.defaults(),
                null);
    }

    /**
     * Returns this config with any missing (null) section replaced by its
     * default — the forward-compatibility path for configs written by older
     * versions.
     */
    public AppConfig withDefaultsFilled() {
        return new AppConfig(
                window != null ? window : WindowConfig.defaults(),
                theme != null ? theme : "DARK",
                camera != null ? camera : CameraConfig.defaults(),
                sidebar != null ? sidebar : SidebarConfig.defaults(),
                render != null ? render : RenderConfig.defaults(),
                session);
    }

    public AppConfig withWindow(WindowConfig window) {
        return new AppConfig(window, theme, camera, sidebar, render, session);
    }

    public AppConfig withTheme(String theme) {
        return new AppConfig(window, theme, camera, sidebar, render, session);
    }

    public AppConfig withCamera(CameraConfig camera) {
        return new AppConfig(window, theme, camera, sidebar, render, session);
    }

    public AppConfig withSidebar(SidebarConfig sidebar) {
        return new AppConfig(window, theme, camera, sidebar, render, session);
    }

    public AppConfig withRender(RenderConfig render) {
        return new AppConfig(window, theme, camera, sidebar, render, session);
    }

    public AppConfig withSession(PresetData session) {
        return new AppConfig(window, theme, camera, sidebar, render, session);
    }
}
