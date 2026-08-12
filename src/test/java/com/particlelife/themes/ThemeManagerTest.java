package com.particlelife.themes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless checks on theme resolution and on the stylesheet resources being
 * packaged. Constructing a {@link ThemeManager} needs a live JavaFX {@code
 * Scene}, so the runnable-away-from-toolkit logic (resolution + resource
 * wiring) is exercised directly.
 */
class ThemeManagerTest {

    @Test
    void resolveMapsConcreteThemesToThemselves() {
        assertEquals(Theme.DARK, ThemeManager.resolve(Theme.DARK));
        assertEquals(Theme.LIGHT, ThemeManager.resolve(Theme.LIGHT));
    }

    @Test
    void resolveMapsSystemToAConcreteTheme() {
        Theme resolved = ThemeManager.resolve(Theme.SYSTEM);
        assertTrue(resolved == Theme.DARK || resolved == Theme.LIGHT,
                "SYSTEM must resolve to a concrete theme, got " + resolved);
    }

    @Test
    void concreteThemesPackageTheirStylesheets() {
        assertNotNull(ThemeManager.class.getResource(Theme.DARK.stylesheet()));
        assertNotNull(ThemeManager.class.getResource(Theme.LIGHT.stylesheet()));
    }

    @Test
    void systemThemeHasNoStylesheetOfItsOwn() {
        assertNull(Theme.SYSTEM.stylesheet());
    }

    @Test
    void displayNamesArePresent() {
        assertEquals("Dark", Theme.DARK.displayName());
        assertEquals("Light", Theme.LIGHT.displayName());
        assertEquals("System", Theme.SYSTEM.displayName());
    }
}