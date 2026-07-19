package com.particlelife.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * Central Gson facade: one configured instance, uniform error translation.
 *
 * <p>Kept deliberately small — DTOs are plain records with JSON-friendly
 * fields, so no custom adapters are needed. All (de)serialization in the app
 * goes through this class so formatting and error handling stay consistent.
 */
public final class JsonSerializer {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private JsonSerializer() {
    }

    /** Serializes {@code value} as pretty-printed JSON. */
    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    /**
     * Deserializes {@code json} into {@code type}.
     *
     * @throws SerializationException if the JSON is malformed or does not
     *                                match the target shape
     */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            T value = GSON.fromJson(json, type);
            if (value == null) {
                throw new SerializationException("Empty JSON payload for " + type.getSimpleName());
            }
            return value;
        } catch (JsonParseException e) {
            throw new SerializationException(
                    "Malformed JSON for " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}
