package com.particlelife.ui;

import com.particlelife.events.SimulationEvent;
import com.particlelife.utils.FxThreads;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Translucent performance overlay in the viewport corner: render FPS,
 * physics rate, step cost and particle count. Physics numbers arrive as
 * {@link SimulationEvent.StatsUpdated} events; render FPS is polled from the
 * view on a half-second timeline.
 */
public final class HudOverlay extends VBox {

    private final Label title = new Label("PARTICLE LIFE 3D");
    private final Label renderLine = new Label("render   —");
    private final Label physicsLine = new Label("physics  —");
    private final Label particlesLine = new Label("particles —");

    private final Timeline refresher;

    public HudOverlay(UiContext ctx) {
        setSpacing(2);
        getStyleClass().add("hud");
        title.getStyleClass().add("headline");
        getChildren().addAll(title, renderLine, physicsLine, particlesLine);
        setMouseTransparent(true);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);

        ctx.eventBus().subscribe(SimulationEvent.StatsUpdated.class, stats ->
                FxThreads.onFx(() -> {
                    physicsLine.setText("physics  %5.0f steps/s   %.2f ms/step"
                            .formatted(stats.stepsPerSecond(), stats.stepMillis()));
                    particlesLine.setText("particles %,d".formatted(stats.particleCount()));
                }));

        refresher = new Timeline(new KeyFrame(Duration.millis(500), e ->
                renderLine.setText("render   %5.0f fps".formatted(ctx.view().renderFps()))));
        refresher.setCycleCount(Animation.INDEFINITE);
        refresher.play();
    }
}
