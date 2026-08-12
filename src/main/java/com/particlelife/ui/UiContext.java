package com.particlelife.ui;

import com.particlelife.config.ConfigService;
import com.particlelife.core.commands.CommandManager;
import com.particlelife.core.engine.SimulationEngine;
import com.particlelife.database.services.PresetService;
import com.particlelife.events.EventBus;
import com.particlelife.render.SimulationView;
import com.particlelife.themes.ThemeManager;

/**
 * Dependency bundle handed to UI components (constructor injection without
 * a framework). Assembled once by the application's composition root.
 */
public record UiContext(
        SimulationEngine engine,
        CommandManager commands,
        PresetService presets,
        SimulationView view,
        ThemeManager themes,
        ConfigService config,
        EventBus eventBus) {
}
