package io.hypercell.fsm.builder;

import io.hypercell.fsm.core.Action;
import io.hypercell.fsm.core.Guard;
import io.hypercell.fsm.exception.StateMachineConfigurationException;

/**
 * Fluent builder for defining a single transition.
 * <p>
 * Obtained from {@link StateBuilder#on(String event)}. Set the target state,
 * optionally add a guard and/or action, then call {@link #end()} to register
 * the transition and return to the {@link StateBuilder}.
 * <p>
 * Example:
 * <pre>{@code
 * .on("APPROVE")
 *     .when(ctx -> ctx.isEligible())
 *     .action(ctx -> auditLog.record(ctx.getId(), "approved"))
 *     .to("PROCESSING")
 *     .end()
 * }</pre>
 *
 * @param <C> the context type flowing through the machine
 */
public class TransitionBuilder<C> {
    private final StateBuilder<C> parent;
    private final String event;
    private String targetState;
    private Guard<C> guard;
    private Action<C> action;

    TransitionBuilder(StateBuilder<C> parent, String event) {
        this.parent = parent;
        this.event = event;
    }

    /**
     * Set the target state this transition leads to. Required — {@link #end()} throws
     * if not called.
     *
     * @param targetState name of the destination state; must be defined in the machine
     */
    public TransitionBuilder<C> to(String targetState) {
        this.targetState = targetState;
        return this;
    }

    /**
     * Add a guard condition. The transition fires only if this guard returns {@code true}.
     * <p>
     * Guards must be pure (read-only, side-effect-free). When multiple transitions share
     * the same event, they are evaluated in definition order; the first whose guard returns
     * {@code true} fires. If none match, {@link io.hypercell.fsm.exception.InvalidEventException}
     * is thrown.
     * <p>
     * Omit this call for an unconditional transition (always fires for the event).
     */
    public TransitionBuilder<C> when(Guard<C> guard) {
        this.guard = guard;
        return this;
    }

    /**
     * Add a transition action executed mid-transition: after the source state's
     * {@code onExit} and before the target state's {@code onEntry}.
     * <p>
     * The action is NOT tracked for retry. If it fails, the machine enters {@code FAILED}
     * and is considered still in the source state.
     */
    public TransitionBuilder<C> action(Action<C> a) {
        this.action = a;
        return this;
    }

    /**
     * Register this transition with the parent {@link StateBuilder} and return to it.
     *
     * @throws StateMachineConfigurationException if {@link #to} was not called
     */
    public StateBuilder<C> end() {
        if (targetState == null) throw new StateMachineConfigurationException(
                "Transition for event '" + event + "' has no target. Call .to(\"STATE\").");
        parent.addTransition(event, targetState, guard, action);
        return parent;
    }
}
