package com.particlelife.utils;

import javafx.application.Platform;

/**
 * FX-thread helpers.
 */
public final class FxThreads {

    private FxThreads() {
    }

    /**
     * Runs {@code action} on the FX thread — immediately if already there,
     * otherwise via {@link Platform#runLater}. Engine-thread event listeners
     * use this to touch the scene graph safely.
     */
    public static void onFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
