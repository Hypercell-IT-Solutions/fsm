package io.hypercell.fsm.execution;

import io.hypercell.fsm.core.ActionResult;
import io.hypercell.fsm.core.StateDefinition;
import io.hypercell.fsm.core.SubStepDefinition;
import io.hypercell.fsm.listener.EventBus;
import io.hypercell.fsm.listener.MachineEvent;
import io.hypercell.fsm.resume.ResumePolicy;

/**
 * Executes the sub-steps of a state sequentially.
 * <p>
 * This class is the heart of the retry/resume mechanism. Before executing each
 * sub-step it checks the ResumePolicy — if a step is already recorded as complete
 * it is skipped (and a SubStepSkippedEvent is emitted). Otherwise, the step runs,
 * its result is recorded, and the appropriate event is emitted.
 * <p>
 * The EventBus is injected so this class never knows whether there are zero or
 * a hundred listeners — it always calls publish() and the bus handles dispatch.
 *
 * @param <C> the context type flowing through the machine
 */
public class SubStepRunner<C> {

    private final ResumePolicy<C> resumePolicy;
    private final EventBus<C> eventBus;
    private final String executionId;
    private final String machineId;

    public SubStepRunner(ResumePolicy<C> resumePolicy, EventBus<C> eventBus,
                         String executionId, String machineId) {
        this.resumePolicy = resumePolicy;
        this.eventBus = eventBus;
        this.executionId = executionId;
        this.machineId = machineId;
    }

    public SubStepRunResult run(StateDefinition<C> state, C ctx, ExecutionRecord executionRecord) {

        for (SubStepDefinition<C> subStep : state.subSteps()) {

            if (resumePolicy.shouldSkip(state, subStep, executionRecord)) {
                ActionResult stored = resumePolicy
                        .storedResult(state, subStep, executionRecord)
                        .orElse(ActionResult.skipped());
                executionRecord.recordStep(state.name(), subStep.name(), stored);

                eventBus.publish(new MachineEvent.SubStepSkippedEvent<>(
                        executionId, machineId, state.name(), subStep.name()));
                continue;
            }

            ActionResult result;
            try {
                result = subStep.action().execute(ctx);
                if (result == null) {
                    result = ActionResult.failed(
                            "Action returned null — use ActionResult.success() for void actions");
                }
            } catch (Exception e) {
                result = ActionResult.failed(e);
            }

            executionRecord.recordStep(state.name(), subStep.name(), result);

            if (result.isFailed()) {
                eventBus.publish(new MachineEvent.SubStepFailedEvent<>(
                        executionId, machineId,
                        state.name(), subStep.name(),
                        result.getErrorMessage(), result.getErrorType()));

                return SubStepRunResult.failed(subStep.name(),
                        new RuntimeException(result.getErrorMessage()));
            }

            eventBus.publish(new MachineEvent.SubStepCompletedEvent<>(
                    executionId, machineId, state.name(), subStep.name(), result));
        }

        return SubStepRunResult.completed();
    }
}
