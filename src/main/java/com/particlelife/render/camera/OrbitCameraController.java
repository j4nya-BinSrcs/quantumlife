package com.particlelife.render.camera;

import com.particlelife.math.MathUtils;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

/**
 * Orbit camera rig: yaw/pitch rotation around a pivot point plus dolly
 * distance — the standard 3D-viewer navigation model.
 *
 * <p>Transform chain (outermost first):
 * {@code pivot translate → yaw (Y axis) → pitch (X axis) → dolly (-Z)}.
 *
 * <p>Interactions on the attached {@link SubScene}:
 * <ul>
 *   <li>primary drag — orbit (yaw/pitch, pitch clamped past the poles)</li>
 *   <li>secondary or middle drag — pan the pivot in the view plane</li>
 *   <li>scroll — exponential dolly zoom</li>
 *   <li>{@link #reset()} — return to the default pose;
 *       {@link #focusOrigin()} — recenter the pivot only</li>
 *   <li>{@link #setAutoOrbit(boolean)} — slow continuous yaw, driven by
 *       {@link #tick(double)} from the render loop</li>
 * </ul>
 */
public final class OrbitCameraController {

    private static final double DEFAULT_YAW = -30;
    private static final double DEFAULT_PITCH = -20;
    private static final double DEFAULT_DISTANCE = 420;
    private static final double MIN_DISTANCE = 20;
    private static final double MAX_DISTANCE = 4000;
    private static final double ORBIT_SENSITIVITY = 0.35;
    private static final double PAN_SENSITIVITY = 0.0011;
    private static final double ZOOM_FACTOR_PER_NOTCH = 1.12;
    private static final double AUTO_ORBIT_DEGREES_PER_SECOND = 8.0;

    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final Translate pivot = new Translate(0, 0, 0);
    private final Rotate yaw = new Rotate(DEFAULT_YAW, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(DEFAULT_PITCH, Rotate.X_AXIS);
    private final Translate dolly = new Translate(0, 0, -DEFAULT_DISTANCE);

    private volatile boolean autoOrbit;
    private double lastX;
    private double lastY;

    public OrbitCameraController() {
        camera.setNearClip(0.5);
        camera.setFarClip(12_000);
        camera.setFieldOfView(45);
        camera.getTransforms().addAll(pivot, yaw, pitch, dolly);
    }

    public PerspectiveCamera camera() {
        return camera;
    }

    /** Installs mouse handlers on {@code subScene} and sets its camera. */
    public void attachTo(SubScene subScene) {
        subScene.setCamera(camera);
        subScene.setOnMousePressed(this::onMousePressed);
        subScene.setOnMouseDragged(this::onMouseDragged);
        subScene.setOnScroll(this::onScroll);
    }

    private void onMousePressed(MouseEvent event) {
        lastX = event.getSceneX();
        lastY = event.getSceneY();
    }

    private void onMouseDragged(MouseEvent event) {
        double dx = event.getSceneX() - lastX;
        double dy = event.getSceneY() - lastY;
        lastX = event.getSceneX();
        lastY = event.getSceneY();

        if (event.getButton() == MouseButton.PRIMARY) {
            yaw.setAngle(yaw.getAngle() + dx * ORBIT_SENSITIVITY);
            pitch.setAngle(MathUtils.clamp(pitch.getAngle() - dy * ORBIT_SENSITIVITY, -89.9, 89.9));
        } else if (event.getButton() == MouseButton.SECONDARY
                || event.getButton() == MouseButton.MIDDLE) {
            pan(dx, dy);
        }
    }

    private void pan(double dxPixels, double dyPixels) {
        // Move the pivot in the camera's view plane; scale by distance so a
        // drag covers the same screen-space regardless of zoom.
        double scale = distance() * PAN_SENSITIVITY;
        double yawRad = Math.toRadians(yaw.getAngle());
        double pitchRad = Math.toRadians(pitch.getAngle());

        // Camera right vector (view-plane x).
        double rightX = Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);
        // Camera up vector (view-plane y), tilted by pitch.
        double upX = Math.sin(yawRad) * Math.sin(pitchRad);
        double upY = Math.cos(pitchRad);
        double upZ = Math.cos(yawRad) * Math.sin(pitchRad);

        pivot.setX(pivot.getX() - (dxPixels * rightX) * scale + (dyPixels * upX) * scale);
        pivot.setY(pivot.getY() + dyPixels * upY * scale);
        pivot.setZ(pivot.getZ() - (dxPixels * rightZ) * scale + (dyPixels * upZ) * scale);
    }

    private void onScroll(ScrollEvent event) {
        if (event.getDeltaY() == 0) {
            return;
        }
        double factor = event.getDeltaY() > 0 ? 1.0 / ZOOM_FACTOR_PER_NOTCH : ZOOM_FACTOR_PER_NOTCH;
        setDistance(distance() * factor);
    }

    /** Advances auto-orbit; call once per rendered frame with elapsed seconds. */
    public void tick(double elapsedSeconds) {
        if (autoOrbit) {
            yaw.setAngle((yaw.getAngle() + AUTO_ORBIT_DEGREES_PER_SECOND * elapsedSeconds) % 360.0);
        }
    }

    /** Restores the default pose (yaw, pitch, distance, pivot). */
    public void reset() {
        yaw.setAngle(DEFAULT_YAW);
        pitch.setAngle(DEFAULT_PITCH);
        setDistance(DEFAULT_DISTANCE);
        focusOrigin();
    }

    /** Re-centers the pivot on the world origin, keeping orientation. */
    public void focusOrigin() {
        pivot.setX(0);
        pivot.setY(0);
        pivot.setZ(0);
    }

    public boolean isAutoOrbit() {
        return autoOrbit;
    }

    public void setAutoOrbit(boolean autoOrbit) {
        this.autoOrbit = autoOrbit;
    }

    public double yawDegrees() {
        return yaw.getAngle();
    }

    public double pitchDegrees() {
        return pitch.getAngle();
    }

    public double distance() {
        return -dolly.getZ();
    }

    /** Restores a persisted pose. */
    public void setPose(double yawDegrees, double pitchDegrees, double distance) {
        yaw.setAngle(yawDegrees);
        pitch.setAngle(MathUtils.clamp(pitchDegrees, -89.9, 89.9));
        setDistance(distance);
    }

    private void setDistance(double distance) {
        dolly.setZ(-MathUtils.clamp(distance, MIN_DISTANCE, MAX_DISTANCE));
    }

    /**
     * Writes the camera's view-plane basis (unit right and up vectors in
     * world space) into the given 3-element arrays — used to orient
     * billboard quads toward the camera without per-node transforms.
     */
    public void basis(double[] right, double[] up) {
        double yawRad = Math.toRadians(yaw.getAngle());
        double pitchRad = Math.toRadians(pitch.getAngle());
        right[0] = Math.cos(yawRad);
        right[1] = 0;
        right[2] = -Math.sin(yawRad);
        up[0] = Math.sin(yawRad) * Math.sin(pitchRad);
        up[1] = Math.cos(pitchRad);
        up[2] = Math.cos(yawRad) * Math.sin(pitchRad);
    }

    /** Unit vector pointing from the camera toward the pivot (view direction). */
    public double[] viewDirection() {
        double yawRad = Math.toRadians(yaw.getAngle());
        double pitchRad = Math.toRadians(pitch.getAngle());
        double cosPitch = Math.cos(pitchRad);
        return new double[] {
                -Math.sin(yawRad) * cosPitch,
                Math.sin(pitchRad),
                Math.cos(yawRad) * cosPitch
        };
    }
}
