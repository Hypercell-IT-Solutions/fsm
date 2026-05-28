package hypercell.opensource.stateful.fsm.resume;

import hypercell.opensource.stateful.fsm.core.ActionResult;
import hypercell.opensource.stateful.fsm.core.StateDefinition;
import hypercell.opensource.stateful.fsm.core.SubStepDefinition;
import hypercell.opensource.stateful.fsm.execution.ExecutionRecord;

import java.util.Optional;

/**
 * Controls which sub-steps are skipped when a machine resumes after failure.
 * <p>
 * The default implementation ({@link DefaultResumePolicy}) skips any sub-step whose
 * name is recorded as successfully completed in the {@code ExecutionRecord}.
 * Implement this interface to add custom skip logic — for example, to always
 * re-run certain idempotent steps for safety despite having a prior success record.
 *
 * @param <C> the context type flowing through the machine
 */
public interface ResumePolicy<C> {

    /**
     * Decide whether to skip a sub-step during a resume ({@code proceed()}).
     *
     * @param state           the state being resumed
     * @param subStep         the candidate sub-step
     * @param executionRecord the live record populated from the snapshot's completed results
     * @return {@code true} to skip this sub-step (it already succeeded); {@code false} to run it
     */
    boolean shouldSkip(StateDefinition<C> state, SubStepDefinition<C> subStep, ExecutionRecord executionRecord);

    /**
     * Return the stored {@code ActionResult} for a skipped sub-step, so that the
     * execution record can present its original output to downstream steps.
     *
     * @return the stored result, or empty if none was recorded
     */
    Optional<ActionResult> storedResult(StateDefinition<C> state, SubStepDefinition<C> subStep, ExecutionRecord executionRecord);
}
