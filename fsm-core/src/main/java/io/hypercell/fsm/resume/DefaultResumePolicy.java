package io.hypercell.fsm.resume;

import io.hypercell.fsm.core.ActionResult;
import io.hypercell.fsm.core.StateDefinition;
import io.hypercell.fsm.core.SubStepDefinition;
import io.hypercell.fsm.execution.ExecutionRecord;

import java.util.Optional;

/**
 * Default {@link ResumePolicy} implementation.
 * <p>
 * Skips a sub-step if and only if it has a recorded success entry in the
 * {@code ExecutionRecord} (which is pre-populated from the snapshot's completed
 * sub-step results before {@code proceed()} runs). This is the correct behaviour
 * for the common case: don't repeat work that already succeeded.
 * <p>
 * Singleton via {@link #getInstance()} — stateless, safe to share.
 *
 * @param <C> the context type flowing through the machine
 */
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
