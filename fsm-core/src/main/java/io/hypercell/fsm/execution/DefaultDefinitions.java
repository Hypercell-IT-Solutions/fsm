package io.hypercell.fsm.execution;

import io.hypercell.fsm.core.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Default, immutable implementation of SubStepDefinition.
 * Built by StateBuilder and stored in DefaultStateDefinition.
 */
final class DefaultSubStepDefinition<C> implements SubStepDefinition<C> {

    private final String name;
    private final Action<C> action;

    DefaultSubStepDefinition(String name, Action<C> action) {
        this.name = name;
        this.action = action;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Action<C> action() {
        return action;
    }
}

/**
 * Default, immutable implementation of StateDefinition.
 * Built by StateBuilder and registered in DefaultStateMachineDefinition.
 */
final class DefaultStateDefinition<C> implements StateDefinition<C> {

    private final String name;
    private final boolean terminal;
    private final List<SubStepDefinition<C>> subSteps;
    private final StateHook<C> hook;

    DefaultStateDefinition(String name, boolean terminal,
                           List<SubStepDefinition<C>> subSteps,
                           StateHook<C> hook) {
        this.name = name;
        this.terminal = terminal;
        this.subSteps = Collections.unmodifiableList(subSteps);
        this.hook = hook;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isTerminal() {
        return terminal;
    }

    @Override
    public List<SubStepDefinition<C>> subSteps() {
        return subSteps;
    }

    @Override
    public Optional<StateHook<C>> hook() {
        return Optional.ofNullable(hook);
    }
}

/**
 * Default, immutable implementation of TransitionDefinition.
 * Built by TransitionBuilder and registered in DefaultStateMachineDefinition.
 */
final class DefaultTransitionDefinition<C> implements TransitionDefinition<C> {

    private final String event;
    private final String sourceState;
    private final String targetState;
    private final Guard<C> guard;
    private final Action<C> action;

    DefaultTransitionDefinition(String event, String sourceState, String targetState,
                                Guard<C> guard, Action<C> action) {
        this.event = event;
        this.sourceState = sourceState;
        this.targetState = targetState;
        this.guard = guard;
        this.action = action;
    }

    @Override
    public String event() {
        return event;
    }

    @Override
    public String sourceState() {
        return sourceState;
    }

    @Override
    public String targetState() {
        return targetState;
    }

    @Override
    public Optional<Guard<C>> guard() {
        return Optional.ofNullable(guard);
    }

    @Override
    public Optional<Action<C>> action() {
        return Optional.ofNullable(action);
    }
}
