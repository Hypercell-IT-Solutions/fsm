package hypercell.opensource.stateful.fsm.execution;

import hypercell.opensource.stateful.fsm.core.*;
import hypercell.opensource.stateful.fsm.exception.InvalidStateException;
import hypercell.opensource.stateful.fsm.listener.EventBus;
import hypercell.opensource.stateful.fsm.manager.StateMachineManager;
import hypercell.opensource.stateful.fsm.resume.ExecutionSnapshot;
import hypercell.opensource.stateful.fsm.resume.ResumePolicy;
import hypercell.opensource.stateful.fsm.resume.SnapshotRepository;
import hypercell.opensource.stateful.fsm.resume.SnapshotStatus;
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
    private final ContextLoader<C> contextLoader;

    public DefaultStateMachineDefinition(String id,
                                         StateDefinition<C> initialState,
                                         Map<String, StateDefinition<C>> states,
                                         Map<String, List<TransitionDefinition<C>>> transitions,
                                         ResumePolicy<C> resumePolicy,
                                         SnapshotRepository snapshotRepository,
                                         RetryCoordinator<C> retryCoordinator,
                                         EventBus<C> eventBus,
                                         ContextLoader<C> contextLoader) {
        this.id = id;
        this.initialState = initialState;
        this.states = Collections.unmodifiableMap(states);
        this.transitions = Collections.unmodifiableMap(transitions);
        this.resumePolicy = resumePolicy;
        this.snapshotRepository = snapshotRepository;
        this.retryCoordinator = retryCoordinator;
        this.eventBus = eventBus != null ? eventBus : EventBus.empty();
        this.contextLoader = contextLoader;
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
    public SnapshotRepository repository() {
        return snapshotRepository;
    }

    @Override
    public ResumePolicy<C> resumePolicy() {
        return resumePolicy;
    }

    @Override
    public RetryCoordinator<C> retryCoordinator() {
        return retryCoordinator;
    }

    @Override
    public ContextLoader<C> contextLoader() {
        return contextLoader;
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
    public StateMachineInstance<C> newInstance(C ctx) {
        return new DefaultStateMachineInstance<>(
                this, initialState, ctx, snapshotRepository, retryCoordinator, eventBus);
    }

    @Override
    public StateMachineInstance<C> newInstance(C ctx, String executionId) {
        return new DefaultStateMachineInstance<>(this, initialState, ctx, executionId,
                snapshotRepository, retryCoordinator, eventBus);
    }

    @Override
    public StateMachineManager<C> newManager() {
        return newManager(snapshotRepository);
    }

    @Override
    public StateMachineManager<C> newManager(SnapshotRepository repository) {
        return StateMachineManager.create(this, repository);
    }

    @Override
    public StateMachineInstance<C> reconstitute(C ctx, ExecutionSnapshot snapshot) {
        return reconstitute(ctx, snapshot, snapshotRepository);
    }

    @Override
    public StateMachineInstance<C> reconstitute(C ctx, ExecutionSnapshot snapshot, SnapshotRepository repository) {
        StateDefinition<C> currentState = stateByName(snapshot.getCurrentStateName());

        ExecutionRecord executionRecord = new ExecutionRecord(snapshot.getExecutionId(), snapshot.getCurrentStateName());

        executionRecord.setStatus(ExecutionStatus.RUNNING);
        if (snapshot.getLastTriggerEvent() != null) {
            executionRecord.setLastTriggerEvent(snapshot.getLastTriggerEvent());
        }

        if (repository != null) {
            repository.save(snapshot.getExecutionId(), snapshot.withStatus(SnapshotStatus.RUNNING));
        }

        return new DefaultStateMachineInstance<>(this, currentState, ctx, snapshot.getAttemptNumber(),
                executionRecord, ExecutionStatus.RUNNING, repository, retryCoordinator, eventBus);
    }

    @Override
    public StateMachineInstance<C> resume(C ctx, ExecutionSnapshot snapshot) {
        return resume(ctx, snapshot, snapshotRepository);
    }

    @Override
    public StateMachineInstance<C> resume(C ctx, ExecutionSnapshot snapshot,
                                          SnapshotRepository repository) {
        StateDefinition<C> failedState = stateByName(snapshot.getFailedStateName());
        ExecutionRecord hydratedRecord = hydrateRecord(snapshot);

        if (repository != null) {
            repository.save(snapshot.getExecutionId(), snapshot.withStatus(SnapshotStatus.RUNNING));
        }

        return new DefaultStateMachineInstance<>(
                this, failedState, ctx, snapshot.getAttemptNumber(), hydratedRecord,
                ExecutionStatus.FAILED, repository != null ? repository : snapshotRepository, retryCoordinator,
                eventBus);
    }

    private ExecutionRecord hydrateRecord(ExecutionSnapshot snapshot) {
        ExecutionRecord executionRecord = new ExecutionRecord(
                snapshot.getExecutionId(), snapshot.getFailedStateName());

        for (Map.Entry<String, ActionResult> entry
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
