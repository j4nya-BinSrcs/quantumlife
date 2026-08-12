package com.particlelife.themes;

import javafx.animation.FadeTransition;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Applies UI themes to the scene with a short cross-fade.
 *
 * <p>{@link Theme#SYSTEM} resolves against the desktop environment
 * (best-effort: GTK/desktop hints on Linux, otherwise dark). Listeners are
 * notified after each change so non-CSS surfaces (the 3D viewport
 * background) can follow the theme.
 */
public final class ThemeManager {

    private static final Logger LOG = LoggerFactory.getLogger(ThemeManager.class);
    private static final Duration FADE = Duration.millis(140);

    private final Scene scene;
    private final List<Consumer<Theme>> listeners = new ArrayList<>();
    private Theme selected = Theme.DARK;

    public ThemeManager(Scene scene) {
        this.scene = scene;
    }

    /** The theme as chosen by the user (may be SYSTEM). */
    public Theme selected() {
        return selected;
    }

    /** The concrete theme in effect (SYSTEM resolved). */
    public Theme effective() {
        return resolve(selected);
    }

    /** Viewport background color matching the effective theme. */
    public Color viewportBackground() {
        return effective() == Theme.DARK ? Color.rgb(13, 14, 22) : Color.rgb(228, 231, 238);
    }

    /** Registers a listener invoked after each theme change. */
    public void addListener(Consumer<Theme> listener) {
        listeners.add(listener);
    }

    /** Applies {@code theme} with an animated transition. */
    public void apply(Theme theme) {
        selected = theme;
        Theme concrete = resolve(theme);
        Parent root = scene.getRoot();

        FadeTransition out = new FadeTransition(FADE, root);
        out.setToValue(0.55);
        out.setOnFinished(e -> {
            applyStylesheet(concrete);
            FadeTransition in = new FadeTransition(FADE, root);
            in.setToValue(1.0);
            in.play();
            listeners.forEach(l -> l.accept(concrete));
        });
        out.play();
        LOG.info("Theme set to {} (effective {})", theme, concrete);
    }

    /** Applies without animation (startup). */
    public void applyImmediate(Theme theme) {
        selected = theme;
        Theme concrete = resolve(theme);
        applyStylesheet(concrete);
        listeners.forEach(l -> l.accept(concrete));
    }

    private void applyStylesheet(Theme concrete) {
        scene.getStylesheets().clear();
        String stylesheet = concrete.stylesheet();
        var url = getClass().getResource(stylesheet);
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        } else {
            LOG.warn("Stylesheet not found: {}", stylesheet);
        }
    }

    /** Resolves SYSTEM to a concrete theme (best-effort OS detection). */
    static Theme resolve(Theme theme) {
        if (theme != Theme.SYSTEM) {
            return theme;
        }
        return detectSystemPrefersLight() ? Theme.LIGHT : Theme.DARK;
    }

    /** Best-effort OS light-mode detection; defaults to dark. */
    static boolean detectSystemPrefersLight() {
        String gtkTheme = System.getenv("GTK_THEME");
        if (gtkTheme != null) {
            return !gtkTheme.toLowerCase().contains("dark");
        }
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac")) {
            // macOS exposes dark mode via this defaults key when active.
            return System.getProperty("apple.awt.application.appearance", "")
                    .toLowerCase().contains("light");
        }
        return false;
    }
}
