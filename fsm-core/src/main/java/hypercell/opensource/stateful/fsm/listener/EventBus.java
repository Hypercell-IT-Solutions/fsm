package hypercell.opensource.stateful.fsm.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Manages a list of listeners and dispatches events to all of them.
 * <p>
 * This is an internal class — callers register listeners via the builder,
 * not by touching the EventBus directly.
 * <p>
 * DESIGN DECISIONS:
 * <p>
 * 1. Listeners are copied to an immutable list at construction time of each
 * machine instance (via snapshot in the builder). This means adding a
 * listener to the builder after build() has no effect on existing instances
 * — which is the correct, predictable behavior.
 * <p>
 * 2. Exceptions from listeners are caught and printed (not re-thrown).
 * A misbehaving listener should never stop the machine.
 * <p>
 * 3. Dispatch order is insertion order — the order .listener() was called
 * in the builder. This is predictable and easy to reason about.
 *
 * @param <C> the context type of the machine
 */
public class EventBus<C> {
    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final List<MachineEventListener<C>> listeners;

    public EventBus(List<MachineEventListener<C>> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    /**
     * No-op bus used when no listeners are registered (avoids null checks everywhere).
     */
    public static <C> EventBus<C> empty() {
        return new EventBus<>(Collections.emptyList());
    }

    public boolean hasListeners() {
        return !listeners.isEmpty();
    }

    /**
     * Dispatch an event to all registered listeners.
     * Exceptions are caught per-listener so one bad listener can't break others.
     */
    public void publish(MachineEvent<C> event) {
        for (MachineEventListener<C> listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                log.error("[EventBus] Listener {} threw on event {}: {}", listener.getClass().getSimpleName(),
                        event.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }
}
