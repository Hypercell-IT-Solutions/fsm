package hypercell.opensource.stateful.fsm.execution;

import hypercell.opensource.stateful.fsm.core.StateDefinition;
import hypercell.opensource.stateful.fsm.core.StateMachineDefinition;
import hypercell.opensource.stateful.fsm.core.StateMachineInstance;
import hypercell.opensource.stateful.fsm.core.TransitionDefinition;
import hypercell.opensource.stateful.fsm.exception.InvalidStateException;
import hypercell.opensource.stateful.fsm.listener.EventBus;
import hypercell.opensource.stateful.fsm.resume.ExecutionSnapshot;
import hypercell.opensource.stateful.fsm.resume.ResumePolicy;
import hypercell.opensource.stateful.fsm.resume.SnapshotRepository;
import hypercell.opensource.stateful.fsm.retry.RetryCoordinator;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The validated, immutable state machine blueprint.
 * <p>
 * Now holds an EventBus that is passed to every instance it creates.
 * The EventBus is constructed once in the builder with all registered listeners
 * and then shared across instances (it's read-only after construction).
 *
 * @param <C> the context type flowing through the machine
 */
public class DefaultStateMachineDefinition<C> implements StateMachineDefinition<C> {

    private final String id;
    private final StateDefinition<C> initialState;
    private final Map<String, StateDefinition<C>> states;
    private final Map<String, List<TransitionDefinition<C>>> transitions;
    private final ResumePolicy<C> resumePolicy;
    private final SnapshotRepository snapshotRepository;
    private final RetryCoordinator<C> retryCoordinator;
    private final EventBus<C> eventBus;

    public DefaultStateMachineDefinition(String id,
                                         StateDefinition<C> initialState,
                                         Map<String, StateDefinition<C>> states,
                                         Map<String, List<TransitionDefinition<C>>> transitions,
                                         ResumePolicy<C> resumePolicy,
                                         SnapshotRepository snapshotRepository,
                                         RetryCoordinator<C> retryCoordinator,
                                         EventBus<C> eventBus) {
        this.id = id;
        this.initialState = initialState;
        this.states = Collections.unmodifiableMap(states);
        this.transitions = Collections.unmodifiableMap(transitions);
        this.resumePolicy = resumePolicy;
        this.snapshotRepository = snapshotRepository;
        this.retryCoordinator = retryCoordinator;
        this.eventBus = eventBus != null ? eventBus : EventBus.empty();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public StateDefinition<C> initialState() {
        return initialState;
    }

    @Override
    public ResumePolicy<C> resumePolicy() {
        return resumePolicy;
    }

    @Override
    public StateDefinition<C> stateByName(String name) {
        StateDefinition<C> s = states.get(name);
        if (s == null) throw new InvalidStateException(name);
        return s;
    }

    @Override
    public List<TransitionDefinition<C>> transitionsFrom(String stateName) {
        return transitions.getOrDefault(stateName, Collections.emptyList());
    }

    @Override
    public StateMachineInstance<C> newInstance(C context) {
        return new DefaultStateMachineInstance<>(
                this, initialState, context, snapshotRepository, retryCoordinator, eventBus);
    }

    @Override
    public StateMachineInstance<C> resume(C context, ExecutionSnapshot snapshot,
                                          SnapshotRepository repository) {
        StateDefinition<C> failedState = stateByName(snapshot.getFailedStateName());
        ExecutionRecord hydratedRecord = hydrateRecord(snapshot);

        if (repository != null) {
            repository.save(snapshot.getExecutionId(),
                    snapshot.withStatus(hypercell.opensource.stateful.fsm.resume.SnapshotStatus.RUNNING));
        }

        return new DefaultStateMachineInstance<>(
                this, failedState, context, hydratedRecord,
                repository != null ? repository : snapshotRepository,
                retryCoordinator, eventBus);
    }

    private ExecutionRecord hydrateRecord(ExecutionSnapshot snapshot) {
        ExecutionRecord executionRecord = new ExecutionRecord(
                snapshot.getExecutionId(), snapshot.getFailedStateName());

        for (Map.Entry<String, hypercell.opensource.stateful.fsm.core.ActionResult> entry
                : snapshot.getCompletedSubStepResults().entrySet()) {
            String[] parts = entry.getKey().split("::", 2);
            if (parts.length == 2) {
                executionRecord.recordStep(parts[0], parts[1], entry.getValue());
            }
        }

        if (snapshot.getLastTriggerEvent() != null) {
            executionRecord.setLastTriggerEvent(snapshot.getLastTriggerEvent());
        }
        executionRecord.markFailed(snapshot.getFailedStateName(), snapshot.getFailedSubStepName());
        return executionRecord;
    }
}
