package com.particlelife.ui.sidebar;

import com.particlelife.themes.Theme;
import com.particlelife.ui.UiContext;
import com.particlelife.ui.controls.SectionPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

/**
 * Sidebar section: theme selection (dark / light / system) with animated
 * transitions handled by the {@code ThemeManager}.
 */
public final class ThemesSection extends SectionPane {

    public ThemesSection(UiContext ctx) {
        super("Theme", false);

        ToggleGroup group = new ToggleGroup();
        HBox row = new HBox(8);
        for (Theme theme : Theme.values()) {
            ToggleButton button = new ToggleButton(theme.displayName());
            button.setToggleGroup(group);
            button.setSelected(ctx.themes().selected() == theme);
            button.setOnAction(e -> {
                if (button.isSelected()) {
                    ctx.themes().apply(theme);
                    ctx.config().update(ctx.config().get().withTheme(theme.name()));
                } else {
                    button.setSelected(true); // one theme is always active
                }
            });
            row.getChildren().add(button);
        }
        addRows(row);
    }
}
