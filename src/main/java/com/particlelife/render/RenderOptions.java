package com.particlelife.render;

import com.particlelife.math.MathUtils;

/**
 * Live visualization options (FX-thread confined; the render loop reads them
 * every frame, the sidebar writes them).
 */
public final class RenderOptions {

    private double particleScale = 1.0;
    private boolean showGrid = true;
    private boolean showAxes = true;
    private boolean showBoundingBox = true;
    private boolean trails = false;
    private boolean glow = true;
    private RenderMode renderMode = RenderMode.AUTO;

    public double particleScale() {
        return particleScale;
    }

    public void setParticleScale(double scale) {
        this.particleScale = MathUtils.clamp(scale, 0.1, 10.0);
    }

    public boolean showGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    public boolean showAxes() {
        return showAxes;
    }

    public void setShowAxes(boolean showAxes) {
        this.showAxes = showAxes;
    }

    public boolean showBoundingBox() {
        return showBoundingBox;
    }

    public void setShowBoundingBox(boolean showBoundingBox) {
        this.showBoundingBox = showBoundingBox;
    }

    public boolean trails() {
        return trails;
    }

    public void setTrails(boolean trails) {
        this.trails = trails;
    }

    public boolean glow() {
        return glow;
    }

    public void setGlow(boolean glow) {
        this.glow = glow;
    }

    public RenderMode renderMode() {
        return renderMode;
    }

    public void setRenderMode(RenderMode renderMode) {
        this.renderMode = renderMode == null ? RenderMode.AUTO : renderMode;
    }
}
