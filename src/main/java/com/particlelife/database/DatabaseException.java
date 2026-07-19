package com.particlelife.database;

/**
 * Wraps checked persistence errors. Unchecked: UI-level callers surface it
 * to the user; nothing mid-stack can meaningfully recover from a broken
 * database file.
 */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
