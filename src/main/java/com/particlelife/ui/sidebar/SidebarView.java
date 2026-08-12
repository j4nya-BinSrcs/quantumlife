package com.particlelife.ui.sidebar;

import com.particlelife.ui.UiContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/**
 * The right-hand control sidebar: all sections stacked in a scrollable
 * column. Section order mirrors a session's workflow — run control first,
 * persistence last.
 */
public final class SidebarView extends ScrollPane {

    public SidebarView(UiContext ctx) {
        VBox column = new VBox(
                new SimulationSection(ctx),
                new ParticlesSection(ctx),
                new PhysicsSection(ctx),
                new SpeciesSection(ctx),
                new MatrixSection(ctx),
                new VisualizationSection(ctx),
                new ThemesSection(ctx),
                new DatabaseSection(ctx));

        setContent(column);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        getStyleClass().add("sidebar");
    }
}
