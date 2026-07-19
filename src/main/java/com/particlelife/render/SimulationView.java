package com.particlelife.render;

import com.particlelife.core.engine.SimulationEngine;
import com.particlelife.render.camera.OrbitCameraController;
import javafx.animation.AnimationTimer;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.effect.Bloom;
import javafx.scene.paint.Color;

/**
 * The 3D viewport: a {@link SubScene} containing world decor, the active
 * particle renderer, lights and the orbit camera, driven by an
 * {@link AnimationTimer} that pulls the latest {@code FrameSnapshot} from
 * the engine every pulse (60&nbsp;FPS target).
 *
 * <p>The render loop never touches live physics state — it reads the
 * synchronized snapshot into its own preallocated arrays, so the physics
 * thread is blocked for at most one bulk copy per frame.
 *
 * <p>Owns the {@link RenderMode} strategy switch: when the effective mode
 * changes (including AUTO threshold crossings), the old renderer's node is
 * swapped out for the new one's.
 */
public final class SimulationView {

    private final SimulationEngine engine;
    private final RenderOptions options;
    private final OrbitCameraController cameraController = new OrbitCameraController();
    private final WorldDecor decor = new WorldDecor();

    private final Group worldRoot = new Group();
    private final Group particleRoot = new Group();
    private final SubScene subScene;

    private final SphereParticleRenderer sphereRenderer = new SphereParticleRenderer();
    private final BillboardParticleRenderer billboardRenderer;
    private ParticleRenderer activeRenderer;

    private final float[] positions;
    private final float[] previousPositions;
    private final int[] species;

    private final AnimationTimer renderLoop;
    private final Bloom bloom = new Bloom(0.6);
    private long lastFrameNanos;
    private long lastSeenFrame = -1;

    // Render-FPS measurement (viewport frames, distinct from physics steps).
    private int fpsFrames;
    private long fpsWindowStart;
    private volatile double renderFps;

    public SimulationView(SimulationEngine engine, RenderOptions options) {
        this.engine = engine;
        this.options = options;

        int capacity = engine.world().store().capacity();
        this.billboardRenderer = new BillboardParticleRenderer(capacity);
        this.positions = new float[capacity * 3];
        this.previousPositions = new float[capacity * 3];
        this.species = new int[capacity];

        AmbientLight ambient = new AmbientLight(Color.rgb(255, 255, 255, 0.85));
        PointLight key = new PointLight(Color.rgb(255, 250, 240, 0.55));
        key.setTranslateX(-350);
        key.setTranslateY(-420);
        key.setTranslateZ(-300);

        worldRoot.getChildren().addAll(decor.node(), particleRoot, ambient, key);

        subScene = new SubScene(worldRoot, 800, 600, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.rgb(13, 14, 22));
        cameraController.attachTo(subScene);

        activeRenderer = billboardRenderer;
        particleRoot.getChildren().add(activeRenderer.node());
        refreshSpecies();

        renderLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                renderFrame(now);
            }
        };
    }

    /** The viewport node to embed in the layout. */
    public SubScene subScene() {
        return subScene;
    }

    public OrbitCameraController camera() {
        return cameraController;
    }

    public RenderOptions options() {
        return options;
    }

    /** Viewport frames per second over the last measurement window. */
    public double renderFps() {
        return renderFps;
    }

    /** Starts the render loop. */
    public void start() {
        lastFrameNanos = 0;
        renderLoop.start();
    }

    /** Stops the render loop. */
    public void stop() {
        renderLoop.stop();
    }

    /** Sets the viewport background (theme hook). */
    public void setBackground(Color color) {
        subScene.setFill(color);
    }

    /** Re-reads species colors/radii into the renderers (species change hook). */
    public void refreshSpecies() {
        var all = engine.world().species().all();
        sphereRenderer.updateSpecies(all);
        billboardRenderer.updateSpecies(all);
    }

    private void renderFrame(long now) {
        double elapsed = lastFrameNanos == 0 ? 0.0 : (now - lastFrameNanos) / 1e9;
        lastFrameNanos = now;

        cameraController.tick(elapsed);
        decor.update(engine.world().physicsSettings().worldSize());
        decor.setGridVisible(options.showGrid());
        decor.setAxesVisible(options.showAxes());
        decor.setBoundingBoxVisible(options.showBoundingBox());

        int count = engine.snapshot().count();
        switchRendererIfNeeded(options.renderMode().resolve(count));

        long frame = engine.snapshot().frame();
        boolean newData = frame != lastSeenFrame;
        // Redraw on new physics data or whenever the camera may have moved
        // (billboards must re-face the camera every frame).
        if (newData || activeRenderer == billboardRenderer) {
            int n = engine.snapshot().readInto(
                    positions, options.trails() ? previousPositions : null, species);
            activeRenderer.render(positions,
                    options.trails() ? previousPositions : null,
                    species, n, options.particleScale(), options.trails(), cameraController);
            lastSeenFrame = frame;
        }

        boolean wantGlow = options.glow();
        if (wantGlow != (subScene.getEffect() != null)) {
            subScene.setEffect(wantGlow ? bloom : null);
        }
        trackFps(now);
    }

    private void switchRendererIfNeeded(RenderMode resolved) {
        ParticleRenderer wanted =
                resolved == RenderMode.SPHERES ? sphereRenderer : billboardRenderer;
        if (wanted != activeRenderer) {
            particleRoot.getChildren().setAll(wanted.node());
            activeRenderer = wanted;
            lastSeenFrame = -1; // force redraw with the new strategy
        }
    }

    private void trackFps(long now) {
        fpsFrames++;
        if (now - fpsWindowStart >= 500_000_000L) {
            renderFps = fpsFrames / ((now - fpsWindowStart) / 1e9);
            fpsWindowStart = now;
            fpsFrames = 0;
        }
    }
}
