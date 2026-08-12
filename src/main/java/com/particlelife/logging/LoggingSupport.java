package com.particlelife.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Process-wide logging glue: routes uncaught exceptions from any thread
 * (including the FX and simulation threads) into SLF4J instead of stderr.
 */
public final class LoggingSupport {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingSupport.class);

    private LoggingSupport() {
    }

    /** Installs the default uncaught-exception handler. Call once at startup. */
    public static void install() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                LOG.error("Uncaught exception on thread '{}'", thread.getName(), error));
    }
}
