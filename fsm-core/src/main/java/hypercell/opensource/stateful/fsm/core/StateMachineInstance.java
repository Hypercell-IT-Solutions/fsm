package hypercell.opensource.stateful.fsm.core;

import hypercell.opensource.stateful.fsm.exception.InvalidEventException;
import hypercell.opensource.stateful.fsm.exception.StateMachineException;
import hypercell.opensource.stateful.fsm.exception.SubStepExecutionException;
import hypercell.opensource.stateful.fsm.execution.ExecutionRecord;
import hypercell.opensource.stateful.fsm.resume.ExecutionSnapshot;

/**
 * A live, running instance of a state machine — one specific workflow execution.
 * <p>
 * RELATIONSHIP TO DEFINITION:
 * StateMachineDefinition is the blueprint (shared, immutable, reusable).
 * StateMachineInstance is one execution of that blueprint (mutable, per-workflow).
 * Think of it like a Class vs an Object instance.
 * <p>
 * THREAD SAFETY:
 * An instance is NOT thread safe. Each instance should be used by one thread at
 * a time. The RetryCoordinator handles concurrent retry protection at a higher level.
 * <p>
 * LIFECYCLE:
 * newInstance()  → status = RUNNING
 * trigger(event) → runs transition + sub-steps → may stay RUNNING or become FAILED/COMPLETED
 * proceed()      → resumes from failure → may go back to RUNNING or fail again
 *
 * @param <C> the context type flowing through the machine
 */
public interface StateMachineInstance<C> {

    /**
     * Unique identifier for this execution. Used as the snapshot storage key.
     * Either a generated UUID or the value passed to {@code newInstance(ctx, executionId)}.
     */
    String executionId();

    /**
     * The state the machine is currently positioned in.
     * Never {@code null}; starts at the initial state.
     */
    StateDefinition<C> currentState();

    /**
     * The current lifecycle status: {@code RUNNING}, {@code COMPLETED}, or {@code FAILED}.
     */
    ExecutionStatus status();

    /**
     * The full live execution record — all steps taken so far, including skipped ones.
     * Primarily used internally; exposed for monitoring and debugging.
     */
    ExecutionRecord executionRecord();

    /**
     * The mutable context object being passed through every guard, action, and sub-step.
     * Actions and sub-steps may modify this object.
     */
    C context();

    /**
     * Trigger a transition via an event name.
     * <p>
     * Executes: onExit → transition action → onEntry → all sub-steps of new state.
     * <p>
     * On sub-step failure:
     * - Status becomes FAILED
     * - Snapshot is saved (if a repository is configured)
     * - SubStepExecutionException is thrown
     * <p>
     * On success:
     * - Returns the new current state
     * - Status is RUNNING (or COMPLETED if the new state is terminal)
     *
     * @throws InvalidEventException     if no valid transition exists for this event
     * @throws SubStepExecutionException if a sub-step fails
     * @throws StateMachineException     if the machine is not in RUNNING status
     */
    StateDefinition<C> trigger(String event);

    /**
     * Continue execution from the failed sub-step.
     * <p>
     * Only valid when status is FAILED. Re-runs the sub-steps of the current state,
     * skipping the ones that already completed in the previous attempt.
     * <p>
     * This is called:
     * - By RetryCoordinator automatically (after backoff delay)
     * - By the developer manually via coordinator.manualRetry()
     *
     * @throws InvalidEventException     if called when status is not FAILED
     * @throws SubStepExecutionException if the sub-step fails again
     */
    StateDefinition<C> proceed();

    /**
     * Take a serializable snapshot of the current execution state.
     * <p>
     * Called internally on failure (and by the RetryCoordinator), but also
     * available to callers who want to checkpoint a long-running machine.
     *
     * @param pendingEvent the event that was being processed when this snapshot
     *                     is taken; null if called outside a trigger() ctx
     */
    ExecutionSnapshot takeSnapshot(String pendingEvent);

    ExecutionSnapshot takeCheckpoint();

    /** {@code true} when status is {@code COMPLETED}. */
    boolean isCompleted();

    /** {@code true} when status is {@code FAILED}. */
    boolean isFailed();

    /** {@code true} when status is {@code RUNNING}. */
    boolean isRunning();
}
