package hypercell.opensource.stateful.fsm.builder;

import hypercell.opensource.stateful.fsm.core.Action;
import hypercell.opensource.stateful.fsm.core.Guard;
import hypercell.opensource.stateful.fsm.exception.StateMachineConfigurationException;

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

    public TransitionBuilder<C> to(String targetState) {
        this.targetState = targetState;
        return this;
    }

    public TransitionBuilder<C> when(Guard<C> guard) {
        this.guard = guard;
        return this;
    }

    public TransitionBuilder<C> action(Action<C> a) {
        this.action = a;
        return this;
    }

    public StateBuilder<C> end() {
        if (targetState == null) throw new StateMachineConfigurationException(
                "Transition for event '" + event + "' has no target. Call .to(\"STATE\").");
        parent.addTransition(event, targetState, guard, action);
        return parent;
    }
}
