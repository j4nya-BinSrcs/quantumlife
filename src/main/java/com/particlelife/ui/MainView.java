package com.particlelife.ui;

import com.particlelife.core.simulation.SimulationState;
import com.particlelife.ui.sidebar.SidebarView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SubScene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

/**
 * Top-level layout: the 3D viewport (with HUD overlay) in the center, the
 * control sidebar on the right.
 *
 * <p>Global shortcuts: {@code Space} play/pause, {@code S} single step,
 * {@code R} reset camera, {@code O} auto-orbit, {@code Tab} toggle sidebar.
 */
public final class MainView extends BorderPane {

    private final SidebarView sidebar;

    public MainView(UiContext ctx) {
        SubScene viewport = ctx.view().subScene();

        // The SubScene tracks its parent pane's size.
        Pane viewportHost = new Pane(viewport);
        viewport.widthProperty().bind(viewportHost.widthProperty());
        viewport.heightProperty().bind(viewportHost.heightProperty());

        HudOverlay hud = new HudOverlay(ctx);
        StackPane.setAlignment(hud, Pos.TOP_LEFT);
        StackPane.setMargin(hud, new Insets(14));
        StackPane center = new StackPane(viewportHost, hud);

        sidebar = new SidebarView(ctx);
        sidebar.setPrefWidth(ctx.config().get().sidebar().width());

        setCenter(center);
        setRight(sidebar);

        addEventFilter(KeyEvent.KEY_PRESSED, event -> handleShortcut(ctx, event));
    }

    private void handleShortcut(UiContext ctx, KeyEvent event) {
        // Don't steal keys from text inputs.
        if (event.getTarget() instanceof javafx.scene.control.TextInputControl) {
            return;
        }
        switch (event.getCode()) {
            case SPACE -> {
                if (ctx.engine().state() == SimulationState.RUNNING) {
                    ctx.engine().pause();
                } else {
                    ctx.engine().play();
                }
                event.consume();
            }
            case S -> {
                ctx.engine().stepOnce();
                event.consume();
            }
            case R -> {
                ctx.view().camera().reset();
                event.consume();
            }
            case O -> {
                ctx.view().camera().setAutoOrbit(!ctx.view().camera().isAutoOrbit());
                event.consume();
            }
            case TAB -> {
                setSidebarVisible(getRight() == null);
                event.consume();
            }
            default -> {
            }
        }
    }

    /** Shows or hides the sidebar. */
    public void setSidebarVisible(boolean visible) {
        setRight(visible ? sidebar : null);
    }

    public boolean isSidebarVisible() {
        return getRight() != null;
    }

    public double sidebarWidth() {
        return sidebar.getWidth() > 0 ? sidebar.getWidth() : sidebar.getPrefWidth();
    }
}
