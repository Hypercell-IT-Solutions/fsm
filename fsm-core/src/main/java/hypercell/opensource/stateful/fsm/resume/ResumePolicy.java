package hypercell.opensource.stateful.fsm.resume;

import hypercell.opensource.stateful.fsm.core.ActionResult;
import hypercell.opensource.stateful.fsm.core.StateDefinition;
import hypercell.opensource.stateful.fsm.core.SubStepDefinition;
import hypercell.opensource.stateful.fsm.execution.ExecutionRecord;

import java.util.Optional;

/**
 * Controls which sub-steps are skipped when a machine resumes after failure.
 * The default implementation (DefaultResumePolicy) skips any sub-step that
 * has a SUCCESS record in the ExecutionRecord.
 */
public interface ResumePolicy<C> {
    boolean shouldSkip(StateDefinition<C> state, SubStepDefinition<C> subStep, ExecutionRecord executionRecord);

    Optional<ActionResult> storedResult(StateDefinition<C> state, SubStepDefinition<C> subStep, ExecutionRecord executionRecord);
}
