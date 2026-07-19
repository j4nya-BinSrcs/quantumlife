package com.particlelife.events;

import com.particlelife.core.simulation.SimulationState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventBusTest {

    private final EventBus bus = new EventBus();

    @Test
    void deliversEventsToMatchingSubscribers() {
        List<SimulationEvent.StateChanged> received = new ArrayList<>();
        bus.subscribe(SimulationEvent.StateChanged.class, received::add);

        bus.publish(new SimulationEvent.StateChanged(SimulationState.RUNNING));

        assertEquals(1, received.size());
        assertEquals(SimulationState.RUNNING, received.get(0).state());
    }

    @Test
    void doesNotDeliverToOtherEventTypes() {
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(SimulationEvent.MatrixChanged.class, e -> count.incrementAndGet());

        bus.publish(new SimulationEvent.StateChanged(SimulationState.PAUSED));

        assertEquals(0, count.get());
    }

    @Test
    void multipleSubscribersAllReceive() {
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(SimulationEvent.MatrixChanged.class, e -> count.incrementAndGet());
        bus.subscribe(SimulationEvent.MatrixChanged.class, e -> count.incrementAndGet());

        bus.publish(new SimulationEvent.MatrixChanged());

        assertEquals(2, count.get());
    }

    @Test
    void unsubscribeStopsDelivery() {
        AtomicInteger count = new AtomicInteger();
        EventBus.Subscription sub =
                bus.subscribe(SimulationEvent.MatrixChanged.class, e -> count.incrementAndGet());

        bus.publish(new SimulationEvent.MatrixChanged());
        sub.unsubscribe();
        bus.publish(new SimulationEvent.MatrixChanged());

        assertEquals(1, count.get());
    }

    @Test
    void throwingListenerDoesNotBreakOthers() {
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(SimulationEvent.MatrixChanged.class, e -> {
            throw new IllegalStateException("boom");
        });
        bus.subscribe(SimulationEvent.MatrixChanged.class, e -> count.incrementAndGet());

        bus.publish(new SimulationEvent.MatrixChanged());

        assertEquals(1, count.get());
    }

    @Test
    void publishWithNoSubscribersIsSafe() {
        bus.publish(new SimulationEvent.SpeciesChanged());
    }
}
