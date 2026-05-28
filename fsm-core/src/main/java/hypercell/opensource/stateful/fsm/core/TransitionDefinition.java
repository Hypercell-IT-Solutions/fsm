package hypercell.opensource.stateful.fsm.core;

import java.util.Optional;

/**
 * Defines a single transition between two states.
 * <p>
 * A transition fires when:
 * 1. trigger(event) is called with a matching event name
 * 2. The current state matches sourceState()
 * 3. The guard (if present) evaluates to true
 * <p>
 * When a transition fires, in order:
 * 1. sourceState onExit hook runs
 * 2. The transition action (if present) runs
 * 3. targetState onEntry hook runs
 * 4. targetState sub-steps run
 * <p>
 * MULTIPLE TRANSITIONS PER EVENT:
 * A state can have multiple transitions for the same event (guards differentiate
 * them). They are evaluated in the order they were defined in the builder.
 * The first one whose guard returns true wins. If none match, InvalidEventException
 * is thrown.
 *
 * @param <C> the context type flowing through the machine
 */
public interface TransitionDefinition<C> {

    /**
     * The event name that activates this transition.
     */
    String event();

    /**
     * The state this transition leaves from.
     */
    String sourceState();

    /**
     * The state this transition leads to.
     */
    String targetState();

    /**
     * Optional guard condition. If absent, the transition always fires
     * (as long as the event matches).
     */
    Optional<Guard<C>> guard();

    /**
     * Optional action executed mid-transition (after onExit, before onEntry).
     * Useful for logging the transition, updating context with transition data, etc.
     * This action is NOT tracked for retry purposes — if it fails, the whole
     * transition fails and the machine stays in the source state.
     */
    Optional<Action<C>> action();
}
