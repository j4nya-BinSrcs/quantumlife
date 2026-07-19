package com.particlelife.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Minimal type-based publish/subscribe bus (Observer pattern) decoupling the
 * simulation engine from the UI.
 *
 * <p>Thread-safe: subscription lists are copy-on-write and publishing walks a
 * stable snapshot, so listeners may subscribe/unsubscribe from any thread —
 * including from inside a callback. Callbacks run on the publisher's thread;
 * UI listeners are responsible for hopping to the FX thread if they touch the
 * scene graph. A misbehaving listener cannot break the publisher: exceptions
 * are caught and logged.
 */
public final class EventBus {

    private static final Logger LOG = LoggerFactory.getLogger(EventBus.class);

    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    /** Registers {@code listener} for events assignable to {@code eventType}. */
    public <E> Subscription subscribe(Class<E> eventType, Consumer<E> listener) {
        List<Consumer<?>> list =
                listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        list.add(listener);
        return () -> list.remove(listener);
    }

    /** Publishes {@code event} to all listeners registered for its exact class. */
    @SuppressWarnings("unchecked")
    public <E> void publish(E event) {
        List<Consumer<?>> list = listeners.get(event.getClass());
        if (list == null) {
            return;
        }
        for (Consumer<?> consumer : list) {
            try {
                ((Consumer<E>) consumer).accept(event);
            } catch (RuntimeException e) {
                LOG.error("Event listener failed for {}", event.getClass().getSimpleName(), e);
            }
        }
    }

    /** Handle returned by {@link #subscribe}; {@link #unsubscribe()} to detach. */
    @FunctionalInterface
    public interface Subscription {
        void unsubscribe();
    }
}
