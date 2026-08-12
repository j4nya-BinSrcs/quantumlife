package com.particlelife.ui.controls;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

/**
 * A collapsible sidebar section: a styled {@link TitledPane} around a
 * consistently spaced content column.
 */
public class SectionPane extends TitledPane {

    private final VBox content = new VBox(10);

    public SectionPane(String title, boolean expanded) {
        setText(title);
        setExpanded(expanded);
        setAnimated(true);
        content.setPadding(new Insets(12));
        setContent(content);
        getStyleClass().add("section-pane");
    }

    /** Appends controls to the section body. */
    public void addRows(Node... nodes) {
        content.getChildren().addAll(nodes);
    }
}
