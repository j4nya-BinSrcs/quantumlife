package com.particlelife.render;

import com.particlelife.render.camera.OrbitCameraController;
import com.particlelife.species.Species;
import javafx.scene.Node;

import java.util.List;

/**
 * Strategy for drawing the particle population each frame.
 *
 * <p>Two implementations trade quality against scale:
 * {@link SphereParticleRenderer} (true shaded geometry, small populations)
 * and {@link BillboardParticleRenderer} (batched camera-facing quads, tens
 * of thousands of particles). {@link RenderMode} selects between them.
 *
 * <p>All methods run on the JavaFX application thread.
 */
public interface ParticleRenderer {

    /** Root node to attach to the 3D scene. */
    Node node();

    /**
     * Redraws the population from the latest snapshot.
     *
     * @param positions     interleaved xyz positions ({@code count * 3} valid)
     * @param prevPositions interleaved previous positions (for trails), may be
     *                      {@code null} when {@code trails} is false
     * @param species       per-particle species index
     * @param count         number of particles
     * @param particleScale global size multiplier from the UI
     * @param trails        stretch particles along their motion vector
     * @param camera        camera rig, for billboard orientation
     */
    void render(float[] positions, float[] prevPositions, int[] species, int count,
                double particleScale, boolean trails, OrbitCameraController camera);

    /** Refreshes per-species colors/radii after a species change. */
    void updateSpecies(List<Species> species);

    /** Releases pooled scene nodes. */
    void dispose();
}
