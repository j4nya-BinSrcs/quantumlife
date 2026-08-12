package com.particlelife.ui.controls;

import com.particlelife.core.commands.CommandManager;
import com.particlelife.core.commands.MatrixCommands;
import com.particlelife.core.engine.SimulationEngine;
import com.particlelife.render.RenderColors;
import com.particlelife.species.Species;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

/**
 * The editable attraction-matrix heatmap.
 *
 * <p>Rows are the <em>source</em> species ("how much does row-species like
 * column-species"), columns the target; species color swatches border the
 * grid. Cell colors follow {@link HeatmapColorScale}
 * (deep blue → light blue → white → orange → red).
 *
 * <p>Editing gestures:
 * <ul>
 *   <li><strong>vertical drag</strong> on a cell — adjust its value
 *       continuously (live physics feedback while dragging);</li>
 *   <li><strong>scroll</strong> — nudge by ±0.05;</li>
 *   <li><strong>right-click</strong> — zero the cell.</li>
 * </ul>
 * A drag collapses into one undo entry (before-state captured at press).
 * Live edits run through the engine's command queue; the widget itself only
 * reads the matrix for painting.
 */
public final class MatrixHeatmapView extends VBox {

    private static final double SWATCH = 14;
    private static final double GAP = 2;
    private static final double DRAG_SENSITIVITY = 0.008;
    private static final double SCROLL_STEP = 0.05;

    private final SimulationEngine engine;
    private final CommandManager commands;
    private final Canvas canvas = new Canvas(300, 300);
    private final Label hoverLabel = new Label(" ");

    private int dragRow = -1;
    private int dragCol = -1;
    private double dragStartValue;
    private double dragStartY;
    private double[][] gestureBefore;

    public MatrixHeatmapView(SimulationEngine engine, CommandManager commands) {
        this.engine = engine;
        this.commands = commands;
        setSpacing(6);
        hoverLabel.getStyleClass().add("hover-label");
        getChildren().addAll(canvas, hoverLabel);

        canvas.setOnMousePressed(this::onPressed);
        canvas.setOnMouseDragged(this::onDragged);
        canvas.setOnMouseReleased(e -> endGesture());
        canvas.setOnMouseMoved(this::onMoved);
        canvas.setOnMouseExited(e -> hoverLabel.setText(" "));
        canvas.setOnScroll(e -> {
            int[] cell = cellAt(e.getX(), e.getY());
            if (cell != null) {
                double current = engine.world().matrix().get(cell[0], cell[1]);
                double next = current + (e.getDeltaY() > 0 ? SCROLL_STEP : -SCROLL_STEP);
                commands.execute(new MatrixCommands.EditCell(cell[0], cell[1], next));
            }
        });
        redraw();
    }

    /** Repaints from the current matrix; call on MatrixChanged/SpeciesChanged. */
    public void redraw() {
        int n = engine.world().matrix().size();
        double cell = cellSize(n);
        double side = SWATCH + GAP + n * cell;
        if (canvas.getWidth() != side) {
            canvas.setWidth(side);
            canvas.setHeight(side);
        }
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, side, side);

        var species = engine.world().species().all();
        // Border swatches.
        for (int i = 0; i < n; i++) {
            Color color = swatchColor(species, i);
            double offset = SWATCH + GAP + i * cell;
            g.setFill(color);
            g.fillRect(offset, 0, cell - 1, SWATCH - 2);   // column header
            g.fillRect(0, offset, SWATCH - 2, cell - 1);   // row header
        }
        // Cells.
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double value = engine.world().matrix().get(i, j);
                g.setFill(HeatmapColorScale.colorFor(value));
                g.fillRect(SWATCH + GAP + j * cell, SWATCH + GAP + i * cell,
                        cell - 1, cell - 1);
                if (cell >= 30) {
                    g.setFill(Math.abs(value) > 0.55 ? Color.WHITE : Color.rgb(40, 40, 40));
                    g.setTextAlign(TextAlignment.CENTER);
                    g.fillText(String.format("%.1f", value),
                            SWATCH + GAP + j * cell + cell / 2,
                            SWATCH + GAP + i * cell + cell / 2 + 4);
                }
            }
        }
    }

    private static Color swatchColor(java.util.List<Species> species, int index) {
        Color color = RenderColors.fromRgb(species.get(index).colorRgb());
        return species.get(index).isEnabled() ? color : color.deriveColor(0, 0.25, 0.7, 1.0);
    }

    private double cellSize(int n) {
        return Math.max(16, Math.min(44, 300.0 / n));
    }

    /** Returns {@code {row, col}} under the point, or {@code null}. */
    private int[] cellAt(double x, double y) {
        int n = engine.world().matrix().size();
        double cell = cellSize(n);
        int col = (int) ((x - SWATCH - GAP) / cell);
        int row = (int) ((y - SWATCH - GAP) / cell);
        if (x < SWATCH + GAP || y < SWATCH + GAP || row < 0 || col < 0 || row >= n || col >= n) {
            return null;
        }
        return new int[] {row, col};
    }

    private void onPressed(MouseEvent e) {
        int[] cell = cellAt(e.getX(), e.getY());
        if (cell == null) {
            return;
        }
        if (e.getButton() == MouseButton.SECONDARY) {
            commands.execute(new MatrixCommands.EditCell(cell[0], cell[1], 0.0));
            return;
        }
        dragRow = cell[0];
        dragCol = cell[1];
        dragStartValue = engine.world().matrix().get(dragRow, dragCol);
        dragStartY = e.getY();
        gestureBefore = engine.world().matrix().toArray();
    }

    private void onDragged(MouseEvent e) {
        if (dragRow < 0) {
            return;
        }
        double value = dragStartValue + (dragStartY - e.getY()) * DRAG_SENSITIVITY;
        int row = dragRow;
        int col = dragCol;
        // Live feedback without polluting undo history; the gesture commits
        // a single undoable command on release.
        engine.submit(() -> engine.world().matrix().set(row, col, value));
        updateHoverLabel(row, col, value);
        redraw();
    }

    private void endGesture() {
        if (dragRow < 0) {
            return;
        }
        final double[][] before = gestureBefore;
        dragRow = -1;
        dragCol = -1;
        gestureBefore = null;
        // Capture "after" on the engine thread: the queue is FIFO, so this
        // runs after every live set() enqueued during the drag and before
        // the committed ApplyEdit, giving an undo entry that matches what
        // the user last saw.
        engine.submit(() -> {
            double[][] after = engine.world().matrix().toArray();
            commands.execute(new MatrixCommands.ApplyEdit(before, after));
        });
    }

    private void onMoved(MouseEvent e) {
        int[] cell = cellAt(e.getX(), e.getY());
        if (cell == null) {
            hoverLabel.setText(" ");
            return;
        }
        updateHoverLabel(cell[0], cell[1], engine.world().matrix().get(cell[0], cell[1]));
    }

    private void updateHoverLabel(int row, int col, double value) {
        var species = engine.world().species().all();
        hoverLabel.setText("%s → %s:  %+.2f".formatted(
                species.get(row).name(), species.get(col).name(), value));
    }
}
