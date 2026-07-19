package com.particlelife.render;

import com.particlelife.render.camera.OrbitCameraController;
import com.particlelife.species.Species;
import com.particlelife.species.SpeciesType;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

import java.util.List;

/**
 * High-performance particle renderer: all particles of one species are drawn
 * as a single {@link TriangleMesh} of camera-facing quads, so the scene
 * graph holds one {@link MeshView} per species regardless of population —
 * the only way JavaFX 3D reaches 10k+ particles at interactive rates.
 *
 * <p>Per frame, quad corner positions are recomputed on the CPU from the
 * camera's view-plane basis (billboarding) and pushed with a single bulk
 * {@code setAll} per species. Point/texcoord/face arrays are preallocated at
 * capacity; face index arrays are truncated only when a species' population
 * changes. With trails enabled, quads are stretched along the particle's
 * motion vector for a cheap motion-blur look.
 *
 * <p>Colors come from species diffuse materials under the scene's ambient
 * light, which renders them flat and fully saturated — appropriate for
 * point-like glowing particles.
 */
public final class BillboardParticleRenderer implements ParticleRenderer {

    /** Trail stretch: how many frames of motion the quad is elongated by. */
    private static final float TRAIL_STRETCH = 6.0f;

    private final Group root = new Group();
    private final int capacity;

    private final MeshView[] views = new MeshView[SpeciesType.MAX_SPECIES];
    private final TriangleMesh[] meshes = new TriangleMesh[SpeciesType.MAX_SPECIES];
    private final PhongMaterial[] materials = new PhongMaterial[SpeciesType.MAX_SPECIES];
    private final float[] speciesRadius = new float[SpeciesType.MAX_SPECIES];

    // Scratch buffers, reused every frame.
    private final float[][] pointBuffers = new float[SpeciesType.MAX_SPECIES][];
    private final int[] quadCounts = new int[SpeciesType.MAX_SPECIES];
    private final int[] lastQuadCounts = new int[SpeciesType.MAX_SPECIES];
    private final double[] right = new double[3];
    private final double[] up = new double[3];

    public BillboardParticleRenderer(int capacity) {
        this.capacity = capacity;
        for (int s = 0; s < SpeciesType.MAX_SPECIES; s++) {
            TriangleMesh mesh = new TriangleMesh();
            mesh.getTexCoords().setAll(0, 0, 1, 0, 1, 1, 0, 1);
            meshes[s] = mesh;
            materials[s] = new PhongMaterial(Color.WHITE);
            MeshView view = new MeshView(mesh);
            view.setMaterial(materials[s]);
            view.setCullFace(CullFace.NONE);
            view.setDrawMode(DrawMode.FILL);
            views[s] = view;
            speciesRadius[s] = 1.0f;
            root.getChildren().add(view);
        }
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void updateSpecies(List<Species> species) {
        for (Species s : species) {
            int i = s.index();
            materials[i].setDiffuseColor(RenderColors.fromRgb(s.colorRgb()));
            speciesRadius[i] = (float) s.radius();
        }
    }

    @Override
    public void render(float[] positions, float[] prevPositions, int[] species, int count,
                       double particleScale, boolean trails, OrbitCameraController camera) {
        camera.basis(right, up);
        float rx = (float) right[0];
        float ry = (float) right[1];
        float rz = (float) right[2];
        float ux = (float) up[0];
        float uy = (float) up[1];
        float uz = (float) up[2];

        java.util.Arrays.fill(quadCounts, 0);

        // Pass 1: write quad corners per species into its point buffer.
        for (int i = 0; i < count; i++) {
            int s = species[i];
            float[] buffer = pointBuffers[s];
            if (buffer == null) {
                buffer = new float[capacity * 12];
                pointBuffers[s] = buffer;
            }
            int q = quadCounts[s]++;
            int base = q * 12;
            int p = i * 3;
            float x = positions[p];
            float y = positions[p + 1];
            float z = positions[p + 2];
            float size = speciesRadius[s] * (float) particleScale;

            float ex = 0;
            float ey = 0;
            float ez = 0;
            if (trails && prevPositions != null) {
                ex = (x - prevPositions[p]) * TRAIL_STRETCH * 0.5f;
                ey = (y - prevPositions[p + 1]) * TRAIL_STRETCH * 0.5f;
                ez = (z - prevPositions[p + 2]) * TRAIL_STRETCH * 0.5f;
            }

            float rxs = rx * size;
            float rys = ry * size;
            float rzs = rz * size;
            float uxs = ux * size;
            float uys = uy * size;
            float uzs = uz * size;

            // Corners: (-r-u) (+r-u) (+r+u) (-r+u), elongated by the motion
            // vector so the head leads and the tail trails.
            buffer[base] = x - rxs - uxs - ex;
            buffer[base + 1] = y - rys - uys - ey;
            buffer[base + 2] = z - rzs - uzs - ez;
            buffer[base + 3] = x + rxs - uxs + ex;
            buffer[base + 4] = y + rys - uys + ey;
            buffer[base + 5] = z + rzs - uzs + ez;
            buffer[base + 6] = x + rxs + uxs + ex;
            buffer[base + 7] = y + rys + uys + ey;
            buffer[base + 8] = z + rzs + uzs + ez;
            buffer[base + 9] = x - rxs + uxs - ex;
            buffer[base + 10] = y - rys + uys - ey;
            buffer[base + 11] = z - rzs + uzs - ez;
        }

        // Pass 2: push buffers into meshes; rebuild faces only on count change.
        for (int s = 0; s < SpeciesType.MAX_SPECIES; s++) {
            int quads = quadCounts[s];
            TriangleMesh mesh = meshes[s];
            if (quads == 0) {
                if (lastQuadCounts[s] != 0) {
                    mesh.getPoints().clear();
                    mesh.getFaces().clear();
                    lastQuadCounts[s] = 0;
                }
                continue;
            }
            mesh.getPoints().setAll(pointBuffers[s], 0, quads * 12);
            if (lastQuadCounts[s] != quads) {
                mesh.getFaces().setAll(buildFaces(quads));
                lastQuadCounts[s] = quads;
            }
        }
    }

    private static int[] buildFaces(int quads) {
        // Two triangles per quad; format: p0,t0, p1,t1, p2,t2 per face.
        int[] faces = new int[quads * 12];
        for (int q = 0; q < quads; q++) {
            int p = q * 4;
            int f = q * 12;
            faces[f] = p;
            faces[f + 1] = 0;
            faces[f + 2] = p + 1;
            faces[f + 3] = 1;
            faces[f + 4] = p + 2;
            faces[f + 5] = 2;
            faces[f + 6] = p;
            faces[f + 7] = 0;
            faces[f + 8] = p + 2;
            faces[f + 9] = 2;
            faces[f + 10] = p + 3;
            faces[f + 11] = 3;
        }
        return faces;
    }

    @Override
    public void dispose() {
        root.getChildren().clear();
    }
}
