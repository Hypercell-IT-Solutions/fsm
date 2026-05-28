package hypercell.opensource.stateful.fsm.execution;

import hypercell.opensource.stateful.fsm.core.*;
import hypercell.opensource.stateful.fsm.exception.InvalidEventException;
import hypercell.opensource.stateful.fsm.exception.StateMachineException;
import hypercell.opensource.stateful.fsm.exception.SubStepExecutionException;
import hypercell.opensource.stateful.fsm.listener.EventBus;
import hypercell.opensource.stateful.fsm.listener.MachineEvent;
import hypercell.opensource.stateful.fsm.resume.ExecutionSnapshot;
import hypercell.opensource.stateful.fsm.resume.SnapshotRepository;
import hypercell.opensource.stateful.fsm.retry.RetryCoordinator;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The runtime engine of the state machine library.
 * <p>
 * WHAT CHANGED WITH EVENTS:
 * This class now holds an EventBus<C> and emits events at every meaningful
 * lifecycle point. The EventBus is always non-null (it's either a real bus
 * with listeners or an empty no-op bus) — no null checks needed here.
 * <p>
 * The event emission points are:
 * trigger()  → TransitionFired, StateExited, StateEntered, MachineCompleted/Failed
 * proceed()  → MachineResumed, StateEntered, MachineCompleted/Failed
 * SubSteps   → SubStepCompleted, SubStepSkipped, SubStepFailed  (emitted by SubStepRunner)
 *
 * @param <C> the context type flowing through the machine
 */
public class DefaultStateMachineInstance<C> implements StateMachineInstance<C> {

    private final String executionId;
    private final StateMachineDefinition<C> definition;
    private final SubStepRunner<C> subStepRunner;
    private final ExecutionRecord executionRecord;
    private final SnapshotRepository snapshotRepository;
    private final RetryCoordinator<C> retryCoordinator;
    private final EventBus<C> eventBus;
    private final C context;

    private StateDefinition<C> currentState;
    private ExecutionStatus executionStatus;

    public DefaultStateMachineInstance(StateMachineDefinition<C> definition,
                                       StateDefinition<C> initialState,
                                       C context,
                                       SnapshotRepository snapshotRepository,
                                       RetryCoordinator<C> retryCoordinator,
                                       EventBus<C> eventBus) {
        this(definition, initialState, context, UUID.randomUUID().toString(), snapshotRepository, retryCoordinator, eventBus);
    }

    public DefaultStateMachineInstance(StateMachineDefinition<C> definition,
                                       StateDefinition<C> initialState,
                                       C context,
                                       String executionId,
                                       SnapshotRepository snapshotRepository,
                                       RetryCoordinator<C> retryCoordinator,
                                       EventBus<C> eventBus) {
        this.executionId = executionId;
        this.definition = definition;
        this.currentState = initialState;
        this.context = context;
        this.snapshotRepository = snapshotRepository;
        this.retryCoordinator = retryCoordinator;
        this.eventBus = eventBus != null ? eventBus : EventBus.empty();
        this.executionStatus = ExecutionStatus.RUNNING;
        this.executionRecord = new ExecutionRecord(executionId, initialState.name());

        this.subStepRunner = new SubStepRunner<>(
                definition.resumePolicy(), this.eventBus, executionId, definition.id());

        runEntryHook(initialState);
        this.eventBus.publish(new MachineEvent.StateEnteredEvent<>(
                executionId, definition.id(), initialState.name()));

        if (!initialState.subSteps().isEmpty()) {
            SubStepRunResult result = subStepRunner.run(initialState, context, executionRecord);
            if (result.isFailed()) {
                handleFailure(initialState.name(), result.getFailedSubStepName(),
                        result.getError(), null);
                throw new SubStepExecutionException(
                        initialState.name(), result.getFailedSubStepName(), result.getError());
            }
        }

        checkTerminal(initialState);
    }

    public DefaultStateMachineInstance(StateMachineDefinition<C> definition,
                                       StateDefinition<C> failedState,
                                       C context,
                                       ExecutionRecord hydratedRecord,
                                       SnapshotRepository snapshotRepository,
                                       RetryCoordinator<C> retryCoordinator,
                                       EventBus<C> eventBus) {
        this.executionId = hydratedRecord.getExecutionId();
        this.definition = definition;
        this.currentState = failedState;
        this.context = context;
        this.executionRecord = hydratedRecord;
        this.snapshotRepository = snapshotRepository;
        this.retryCoordinator = retryCoordinator;
        this.eventBus = eventBus != null ? eventBus : EventBus.empty();
        this.executionStatus = ExecutionStatus.FAILED;

        this.subStepRunner = new SubStepRunner<>(
                definition.resumePolicy(), this.eventBus, executionId, definition.id());
    }

    @Override
    public StateDefinition<C> trigger(String event) {
        if (executionStatus != ExecutionStatus.RUNNING) {
            throw new InvalidEventException(String.format(
                    "Cannot trigger '%s' — machine is %s. Call proceed() if FAILED.",
                    event, executionStatus));
        }

        TransitionDefinition<C> transition = definition.transitionsFrom(currentState.name())
                .stream()
                .filter(t -> t.event().equals(event))
                .filter(t -> t.guard().map(g -> g.evaluate(context)).orElse(true))
                .findFirst()
                .orElseThrow(() -> new InvalidEventException(event, currentState.name()));

        executionRecord.setLastTriggerEvent(event);

        String fromState = currentState.name();
        runExitHook(currentState);
        eventBus.publish(new MachineEvent.StateExitedEvent<>(
                executionId, definition.id(), fromState));

        transition.action().ifPresent(action -> {
            try {
                ActionResult r = action.execute(context);
                if (r != null && r.isFailed()) {
                    throw new StateMachineException("Transition action failed: " + r.getErrorMessage());
                }
            } catch (StateMachineException e) {
                throw e;
            } catch (Exception e) {
                throw new StateMachineException("Transition action threw: " + e.getMessage(), e);
            }
        });

        StateDefinition<C> nextState = definition.stateByName(transition.targetState());
        currentState = nextState;
        executionRecord.setCurrentStateName(nextState.name());

        eventBus.publish(new MachineEvent.TransitionFiredEvent<>(
                executionId, definition.id(), fromState, nextState.name(), event));

        runEntryHook(nextState);
        eventBus.publish(new MachineEvent.StateEnteredEvent<>(
                executionId, definition.id(), nextState.name()));

        if (!nextState.subSteps().isEmpty()) {
            SubStepRunResult runResult = subStepRunner.run(nextState, context, executionRecord);
            if (runResult.isFailed()) {
                handleFailure(nextState.name(), runResult.getFailedSubStepName(),
                        runResult.getError(), event);
                throw new SubStepExecutionException(
                        nextState.name(), runResult.getFailedSubStepName(), runResult.getError());
            }
        }

        checkTerminal(nextState);
        return nextState;
    }

    @Override
    public StateDefinition<C> proceed() {
        if (executionStatus != ExecutionStatus.FAILED) {
            throw new InvalidEventException(
                    "proceed() can only be called when status is FAILED. Current: " + executionStatus);
        }

        eventBus.publish(new MachineEvent.MachineResumedEvent<>(
                executionId, definition.id(),
                executionRecord.getFailedStateName(),
                executionRecord.getFailedSubStepName(),
                executionRecord.getSteps().size()));

        executionRecord.clearFailure();
        executionStatus = ExecutionStatus.RUNNING;

        if (!currentState.subSteps().isEmpty()) {
            SubStepRunResult runResult = subStepRunner.run(currentState, context, executionRecord);
            if (runResult.isFailed()) {
                handleFailure(currentState.name(), runResult.getFailedSubStepName(),
                        runResult.getError(), executionRecord.getLastTriggerEvent());
                throw new SubStepExecutionException(
                        currentState.name(), runResult.getFailedSubStepName(), runResult.getError());
            }
        }

        checkTerminal(currentState);
        return currentState;
    }

    @Override
    public ExecutionSnapshot takeSnapshot(String pendingEvent) {
        return ExecutionSnapshot.fromRecord(executionRecord, pendingEvent,
                executionRecord.getSteps().stream()
                        .filter(s -> s.getResult().isSuccess())
                        .collect(Collectors.toMap(
                                StepRecord::compositeKey,
                                StepRecord::getResult,
                                (existing, replacement) -> replacement)));
    }

    private void checkTerminal(StateDefinition<C> state) {
        if (state.isTerminal()) {
            executionRecord.markCompleted();
            executionStatus = ExecutionStatus.COMPLETED;
            if (snapshotRepository != null) snapshotRepository.delete(executionId);
            eventBus.publish(new MachineEvent.MachineCompletedEvent<>(
                    executionId, definition.id(), state.name()));
        }
    }

    private void handleFailure(String stateName, String subStepName,
                               Throwable error, String pendingEvent) {
        executionStatus = ExecutionStatus.FAILED;
        executionRecord.markFailed(stateName, subStepName);

        long failCount = executionRecord.getSteps().stream()
                .filter(s -> s.getResult().isFailed()).count();

        eventBus.publish(new MachineEvent.MachineFailedEvent<>(
                executionId, definition.id(), stateName, subStepName, (int) failCount));

        if (snapshotRepository != null) {
            snapshotRepository.save(executionId, takeSnapshot(pendingEvent));
        }

        if (retryCoordinator != null) {
            retryCoordinator.onFailure(this, pendingEvent, error);
        }
    }

    private void runEntryHook(StateDefinition<C> state) {
        state.hook().ifPresent(h -> {
            try {
                h.onEntry(context);
            } catch (Exception e) {
                throw new StateMachineException(
                        "onEntry hook failed for '" + state.name() + "': " + e.getMessage(), e);
            }
        });
    }

    private void runExitHook(StateDefinition<C> state) {
        state.hook().ifPresent(h -> {
            try {
                h.onExit(context);
            } catch (Exception e) {
                throw new StateMachineException(
                        "onExit hook failed for '" + state.name() + "': " + e.getMessage(), e);
            }
        });
    }

    @Override
    public String executionId() {
        return executionId;
    }

    @Override
    public StateDefinition<C> currentState() {
        return currentState;
    }

    @Override
    public ExecutionStatus status() {
        return executionStatus;
    }

    @Override
    public ExecutionRecord executionRecord() {
        return executionRecord;
    }

    @Override
    public C context() {
        return context;
    }

    @Override
    public boolean isCompleted() {
        return executionStatus == ExecutionStatus.COMPLETED;
    }

    @Override
    public boolean isFailed() {
        return executionStatus == ExecutionStatus.FAILED;
    }

    @Override
    public boolean isRunning() {
        return executionStatus == ExecutionStatus.RUNNING;
    }
}
