package com.particlelife.serialization;

/**
 * Thrown when JSON (de)serialization fails or the payload is structurally
 * invalid. Unchecked because callers can rarely recover beyond reporting.
 */
public class SerializationException extends RuntimeException {

    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
