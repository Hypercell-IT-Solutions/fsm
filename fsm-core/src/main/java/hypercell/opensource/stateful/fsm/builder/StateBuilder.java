package hypercell.opensource.stateful.fsm.builder;

import hypercell.opensource.stateful.fsm.core.Action;
import hypercell.opensource.stateful.fsm.core.StateHook;
import hypercell.opensource.stateful.fsm.core.SubStepDefinition;
import hypercell.opensource.stateful.fsm.exception.StateMachineConfigurationException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StateBuilder<C> {
    private final StateMachineBuilder<C> parent;
    private final String name;
    private boolean terminal = false;
    private final List<SubStepDefinition<C>> subSteps = new ArrayList<>();
    private StateHook<C> compositeHook;
    final List<Object[]> transitionData = new ArrayList<>();

    StateBuilder(StateMachineBuilder<C> parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    public StateBuilder<C> onEntry(Consumer<C> fn) {
        StateHook<C> existing = compositeHook;
        compositeHook = new StateHook<C>() {
            @Override
            public void onEntry(C ctx) {
                if (existing != null) existing.onEntry(ctx);
                fn.accept(ctx);
            }

            @Override
            public void onExit(C ctx) {
                if (existing != null) existing.onExit(ctx);
            }
        };
        return this;
    }

    public StateBuilder<C> onExit(Consumer<C> fn) {
        StateHook<C> existing = compositeHook;
        compositeHook = new StateHook<C>() {
            @Override
            public void onEntry(C ctx) {
                if (existing != null) existing.onEntry(ctx);
            }

            @Override
            public void onExit(C ctx) {
                if (existing != null) existing.onExit(ctx);
                fn.accept(ctx);
            }
        };
        return this;
    }

    public StateBuilder<C> subStep(String name, Action<C> action) {
        if (subSteps.stream().anyMatch(s -> s.name().equals(name)))
            throw new StateMachineConfigurationException(
                    "Duplicate sub-step '" + name + "' in state '" + this.name + "'.");
        if (name.contains("::"))
            throw new StateMachineConfigurationException(
                    "Sub-step name '" + name + "' contains reserved separator '::'.");
        subSteps.add(new SimpleSubStep<>(name, action));
        return this;
    }

    public TransitionBuilder<C> on(String event) {
        return new TransitionBuilder<>(this, event);
    }

    public StateBuilder<C> terminal() {
        this.terminal = true;
        return this;
    }

    public StateMachineBuilder<C> and() {
        parent.registerState(this);
        return parent;
    }

    void addTransition(String event, String target, hypercell.opensource.stateful.fsm.core.Guard<C> guard, Action<C> action) {
        transitionData.add(new Object[]{event, target, guard, action});
    }

    String getName() {
        return name;
    }

    boolean isTerminal() {
        return terminal;
    }

    List<SubStepDefinition<C>> getSubSteps() {
        return subSteps;
    }

    StateHook<C> getCompositeHook() {
        return compositeHook;
    }

    private record SimpleSubStep<C>(String n, Action<C> a) implements SubStepDefinition<C> {
        @Override
        public String name() {
            return n;
        }

        @Override
        public Action<C> action() {
            return a;
        }
    }
}
