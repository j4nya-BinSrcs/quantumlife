package com.particlelife.render;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * Static world scenery: bounding cube wireframe, floor grid, and XYZ axes —
 * the depth anchors without which a 3D particle cloud is unreadable.
 *
 * <p>JavaFX 3D has no line primitive, so "lines" are thin {@link Box}es —
 * standard practice, and cheap at the ~80 nodes involved. Geometry is
 * rebuilt only when the world size changes.
 */
public final class WorldDecor {

    private static final double LINE_THICKNESS = 0.35;
    private static final int GRID_DIVISIONS = 10;

    private final Group root = new Group();
    private final Group boundingBox = new Group();
    private final Group grid = new Group();
    private final Group axes = new Group();

    private final PhongMaterial boxMaterial = flat(Color.rgb(120, 130, 160, 1.0));
    private final PhongMaterial gridMaterial = flat(Color.rgb(90, 96, 120, 1.0));
    private final PhongMaterial xAxisMaterial = flat(Color.rgb(235, 84, 84));
    private final PhongMaterial yAxisMaterial = flat(Color.rgb(96, 220, 120));
    private final PhongMaterial zAxisMaterial = flat(Color.rgb(96, 140, 245));

    private double builtWorldSize = -1;

    public WorldDecor() {
        root.getChildren().addAll(grid, boundingBox, axes);
    }

    private static PhongMaterial flat(Color color) {
        PhongMaterial material = new PhongMaterial(color);
        material.setSpecularColor(Color.TRANSPARENT);
        return material;
    }

    public Group node() {
        return root;
    }

    /** Rebuilds geometry if {@code worldSize} changed since the last call. */
    public void update(double worldSize) {
        if (worldSize == builtWorldSize) {
            return;
        }
        builtWorldSize = worldSize;
        rebuildBoundingBox(worldSize);
        rebuildGrid(worldSize);
        rebuildAxes(worldSize);
    }

    public void setBoundingBoxVisible(boolean visible) {
        boundingBox.setVisible(visible);
    }

    public void setGridVisible(boolean visible) {
        grid.setVisible(visible);
    }

    public void setAxesVisible(boolean visible) {
        axes.setVisible(visible);
    }

    private void rebuildBoundingBox(double size) {
        boundingBox.getChildren().clear();
        double h = size / 2;
        List<Box> edges = new ArrayList<>(12);
        // 4 edges along each axis, at the 4 corner combinations of the other two.
        double[][] corners = {{-h, -h}, {-h, h}, {h, -h}, {h, h}};
        for (double[] c : corners) {
            edges.add(edge(size, LINE_THICKNESS, LINE_THICKNESS, 0, c[0], c[1]));
            edges.add(edge(LINE_THICKNESS, size, LINE_THICKNESS, c[0], 0, c[1]));
            edges.add(edge(LINE_THICKNESS, LINE_THICKNESS, size, c[0], c[1], 0));
        }
        edges.forEach(e -> e.setMaterial(boxMaterial));
        boundingBox.getChildren().addAll(edges);
    }

    private void rebuildGrid(double size) {
        grid.getChildren().clear();
        double h = size / 2;
        double step = size / GRID_DIVISIONS;
        // Floor grid at the bottom face (y = +h; JavaFX Y grows downward).
        for (int i = 0; i <= GRID_DIVISIONS; i++) {
            double offset = -h + i * step;
            Box alongX = edge(size, LINE_THICKNESS * 0.6, LINE_THICKNESS * 0.6, 0, h, offset);
            Box alongZ = edge(LINE_THICKNESS * 0.6, LINE_THICKNESS * 0.6, size, offset, h, 0);
            alongX.setMaterial(gridMaterial);
            alongZ.setMaterial(gridMaterial);
            grid.getChildren().addAll(alongX, alongZ);
        }
    }

    private void rebuildAxes(double size) {
        axes.getChildren().clear();
        double length = size * 0.56;
        double t = LINE_THICKNESS * 2.2;
        Box x = edge(length, t, t, length / 2, 0, 0);
        Box y = edge(t, length, t, 0, -length / 2, 0); // up on screen
        Box z = edge(t, t, length, 0, 0, length / 2);
        x.setMaterial(xAxisMaterial);
        y.setMaterial(yAxisMaterial);
        z.setMaterial(zAxisMaterial);
        axes.getChildren().addAll(x, y, z);
    }

    private static Box edge(double w, double hgt, double d, double tx, double ty, double tz) {
        Box box = new Box(w, hgt, d);
        box.setTranslateX(tx);
        box.setTranslateY(ty);
        box.setTranslateZ(tz);
        return box;
    }
}
