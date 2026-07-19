package com.particlelife.render;

import com.particlelife.render.camera.OrbitCameraController;
import com.particlelife.species.Species;
import com.particlelife.species.SpeciesType;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;

import java.util.ArrayList;
import java.util.List;

/**
 * Quality particle renderer: one low-poly {@link Sphere} node per particle
 * with shared per-species materials, shaded by the scene lights.
 *
 * <p>Nodes are pooled and recycled — the pool only ever grows to the largest
 * population seen, and surplus nodes are hidden rather than removed, so a
 * respawn never reallocates the scene graph. Sensible up to a few thousand
 * particles; beyond that {@link BillboardParticleRenderer} takes over.
 */
public final class SphereParticleRenderer implements ParticleRenderer {

    /** Low-poly divisions: 8 is visually round at particle size. */
    private static final int SPHERE_DIVISIONS = 8;

    private final Group root = new Group();
    private final List<Sphere> pool = new ArrayList<>();
    private final PhongMaterial[] materials = new PhongMaterial[SpeciesType.MAX_SPECIES];
    private final double[] speciesRadius = new double[SpeciesType.MAX_SPECIES];

    public SphereParticleRenderer() {
        for (int s = 0; s < SpeciesType.MAX_SPECIES; s++) {
            PhongMaterial material = new PhongMaterial(Color.WHITE);
            material.setSpecularColor(Color.gray(0.6));
            materials[s] = material;
            speciesRadius[s] = 1.0;
        }
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void updateSpecies(List<Species> species) {
        for (Species s : species) {
            materials[s.index()].setDiffuseColor(RenderColors.fromRgb(s.colorRgb()));
            speciesRadius[s.index()] = s.radius();
        }
    }

    @Override
    public void render(float[] positions, float[] prevPositions, int[] species, int count,
                       double particleScale, boolean trails, OrbitCameraController camera) {
        ensurePoolSize(count);
        for (int i = 0; i < count; i++) {
            Sphere sphere = pool.get(i);
            int s = species[i];
            int base = i * 3;
            sphere.setTranslateX(positions[base]);
            sphere.setTranslateY(positions[base + 1]);
            sphere.setTranslateZ(positions[base + 2]);
            sphere.setRadius(speciesRadius[s] * particleScale);
            if (sphere.getMaterial() != materials[s]) {
                sphere.setMaterial(materials[s]);
            }
            if (!sphere.isVisible()) {
                sphere.setVisible(true);
            }
        }
        for (int i = count; i < pool.size(); i++) {
            Sphere sphere = pool.get(i);
            if (sphere.isVisible()) {
                sphere.setVisible(false);
            }
        }
    }

    private void ensurePoolSize(int count) {
        while (pool.size() < count) {
            Sphere sphere = new Sphere(1.0, SPHERE_DIVISIONS);
            sphere.setMaterial(materials[0]);
            pool.add(sphere);
            root.getChildren().add(sphere);
        }
    }

    @Override
    public void dispose() {
        pool.clear();
        root.getChildren().clear();
    }
}
