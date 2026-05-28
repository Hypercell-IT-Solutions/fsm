package hypercell.opensource.stateful.fsm.resume;

import hypercell.opensource.stateful.fsm.core.ActionResult;
import hypercell.opensource.stateful.fsm.core.StateDefinition;
import hypercell.opensource.stateful.fsm.core.SubStepDefinition;
import hypercell.opensource.stateful.fsm.execution.ExecutionRecord;

import java.util.Optional;

public class DefaultResumePolicy<C> implements ResumePolicy<C> {
    private static final DefaultResumePolicy<?> INSTANCE = new DefaultResumePolicy<>();

    @SuppressWarnings("unchecked")
    public static <C> DefaultResumePolicy<C> getInstance() {
        return (DefaultResumePolicy<C>) INSTANCE;
    }

    @Override
    public boolean shouldSkip(StateDefinition<C> state, SubStepDefinition<C> subStep, ExecutionRecord executionRecord) {
        return executionRecord.isSubStepCompleted(state.name(), subStep.name());
    }

    @Override
    public Optional<ActionResult> storedResult(StateDefinition<C> state, SubStepDefinition<C> subStep, ExecutionRecord executionRecord) {
        return executionRecord.resultOf(state.name(), subStep.name());
    }
}
