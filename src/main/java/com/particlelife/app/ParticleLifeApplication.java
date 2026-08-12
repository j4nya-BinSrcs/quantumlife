package com.particlelife.app;

import com.particlelife.config.AppConfig;
import com.particlelife.logging.LoggingSupport;
import com.particlelife.render.RenderOptions;
import com.particlelife.render.SimulationView;
import com.particlelife.themes.Theme;
import com.particlelife.themes.ThemeManager;
import com.particlelife.ui.MainView;
import com.particlelife.ui.UiContext;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX entry point: builds the {@link ApplicationContext}, assembles the
 * scene, restores persisted state (window, theme, camera, render options,
 * last session), and saves everything back on close.
 */
public final class ParticleLifeApplication extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(ParticleLifeApplication.class);

    private ApplicationContext context;
    private SimulationView view;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        LoggingSupport.install();
        context = new ApplicationContext();
        AppConfig config = context.config().get();

        RenderOptions options = new RenderOptions();
        applyRenderConfig(options, config.render());
        view = new SimulationView(context.engine(), options);
        view.camera().setPose(config.camera().yawDegrees(),
                config.camera().pitchDegrees(), config.camera().distance());
        view.camera().setAutoOrbit(config.render().autoOrbit());

        // The scene must exist before the ThemeManager, which styles it.
        MainView[] mainViewHolder = new MainView[1];
        Scene scene = new Scene(new javafx.scene.layout.StackPane(),
                config.window().width(), config.window().height());
        ThemeManager themes = new ThemeManager(scene);
        themes.addListener(t -> view.setBackground(themes.viewportBackground()));
        themes.applyImmediate(parseTheme(config.theme()));

        UiContext ui = new UiContext(context.engine(), context.commands(), context.presets(),
                view, themes, context.config(), context.eventBus());
        MainView mainView = new MainView(ui);
        mainViewHolder[0] = mainView;
        mainView.setSidebarVisible(config.sidebar().visible());
        scene.setRoot(mainView);
        view.setBackground(themes.viewportBackground());

        stage.setTitle("Particle Life 3D");
        stage.setScene(scene);
        if (config.window().x() >= 0 && config.window().y() >= 0) {
            stage.setX(config.window().x());
            stage.setY(config.window().y());
        }
        stage.setMaximized(config.window().maximized());
        stage.setOnCloseRequest(e -> shutdown(stage, mainViewHolder[0], themes, options));
        stage.show();

        context.engine().start();
        view.start();
        context.engine().play();
        LOG.info("Particle Life 3D started");
    }

    private void shutdown(Stage stage, MainView mainView, ThemeManager themes, RenderOptions options) {
        LOG.info("Shutting down");
        context.engine().pause();
        view.stop();

        AppConfig config = context.config().get()
                .withWindow(new AppConfig.WindowConfig(
                        stage.getWidth(), stage.getHeight(), stage.getX(), stage.getY(),
                        stage.isMaximized()))
                .withTheme(themes.selected().name())
                .withCamera(new AppConfig.CameraConfig(
                        view.camera().yawDegrees(), view.camera().pitchDegrees(),
                        view.camera().distance()))
                .withSidebar(new AppConfig.SidebarConfig(
                        mainView.isSidebarVisible(), mainView.sidebarWidth()))
                .withRender(new AppConfig.RenderConfig(
                        options.particleScale(), options.showGrid(), options.showAxes(),
                        options.showBoundingBox(), options.trails(), options.glow(),
                        view.camera().isAutoOrbit()));
        context.config().update(config);
        context.saveSession();

        context.close();
        Platform.exit();
    }

    private static void applyRenderConfig(RenderOptions options, AppConfig.RenderConfig config) {
        options.setParticleScale(config.particleScale());
        options.setShowGrid(config.showGrid());
        options.setShowAxes(config.showAxes());
        options.setShowBoundingBox(config.showBoundingBox());
        options.setTrails(config.trails());
        options.setGlow(config.glow());
    }

    private static Theme parseTheme(String name) {
        try {
            return Theme.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Theme.DARK;
        }
    }
}
