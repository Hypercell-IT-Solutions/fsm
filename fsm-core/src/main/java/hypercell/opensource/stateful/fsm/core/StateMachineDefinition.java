package hypercell.opensource.stateful.fsm.core;

import hypercell.opensource.stateful.fsm.manager.StateMachineManager;
import hypercell.opensource.stateful.fsm.resume.ExecutionSnapshot;
import hypercell.opensource.stateful.fsm.resume.ResumePolicy;
import hypercell.opensource.stateful.fsm.resume.SnapshotRepository;
import hypercell.opensource.stateful.fsm.retry.RetryCoordinator;

import java.util.List;
import java.util.function.Function;

/**
 * The immutable, validated blueprint of a state machine.
 * <p>
 * Created once via {@code StateMachine.define(id).....build()} and then reused to
 * produce as many instances as needed. The definition itself is thread-safe and
 * stateless — it carries no per-execution data.
 * <p>
 * THREAD SAFETY: immutable after {@code build()}; share freely across threads.
 *
 * @param <C> the context type flowing through the machine
 */
public interface StateMachineDefinition<C> {

    /** Stable identifier for this machine type, set in {@code StateMachine.define(id)}. */
    String id();

    /** The state the machine enters when a new instance is created. */
    StateDefinition<C> initialState();

    /**
     * The snapshot repository configured on this definition, if any.
     * May be {@code null} if no repository was set in the builder.
     */
    SnapshotRepository repository();

    /**
     * Look up a state by name.
     *
     * @throws hypercell.opensource.stateful.fsm.exception.InvalidStateException if no state with that name exists
     */
    StateDefinition<C> stateByName(String name);

    /**
     * All transitions defined from the named state, in definition order.
     * Returns an empty list for terminal states.
     */
    List<TransitionDefinition<C>> transitionsFrom(String stateName);

    /**
     * The policy that decides which sub-steps to skip when resuming after failure.
     * The default ({@link hypercell.opensource.stateful.fsm.resume.DefaultResumePolicy}) skips any
     * sub-step that is recorded as completed in the execution record.
     */
    ResumePolicy<C> resumePolicy();

    /**
     * The retry coordinator wired in at build time, or {@code null} if no retry
     * policy was configured. Used internally; consumers should not call this directly.
     */
    RetryCoordinator<C> retryCoordinator();

    /**
     * Create a fresh instance starting at the initial state.
     * A UUID is generated as the execution ID (used as the snapshot key).
     *
     * @param context the mutable domain object passed to every guard, action, and sub-step
     */
    StateMachineInstance<C> newInstance(C context);

    /**
     * Create a fresh instance with an explicit execution ID.
     * Use this when your business entity already has a meaningful ID
     * (e.g. {@code orderId}) so the snapshot key matches your domain.
     *
     * @param context     the mutable domain object
     * @param executionId stable identifier; becomes the snapshot storage key
     */
    StateMachineInstance<C> newInstance(C context, String executionId);

    /**
     * Create a {@link StateMachineManager} bound to this definition's repository.
     * Equivalent to {@code newManager(definition.repository(), contextLoader)}.
     *
     * @param contextLoader how to load a fresh context given an executionId
     */
    StateMachineManager<C> newManager(Function<String, C> contextLoader);

    /**
     * Create a {@link StateMachineManager} with an explicit repository.
     * Use this when you want the manager to use a different repository than the one
     * set on the definition (e.g. a production DB repo vs. the in-memory one used
     * during definition-time testing).
     *
     * @param repository    where snapshots are stored and loaded
     * @param contextLoader how to load a fresh context given an executionId
     */
    StateMachineManager<C> newManager(SnapshotRepository repository, Function<String, C> contextLoader);

    /**
     * Restore a {@code RUNNING} instance from a checkpoint snapshot.
     * <p>
     * Use this when a process restarted between requests and the snapshot status is
     * {@code RUNNING} (the previous request saved a checkpoint but did not fail).
     * The returned instance is positioned at {@code snapshot.getCurrentStateName()}
     * with status {@code RUNNING}; the caller calls {@code trigger(event)} next.
     * <p>
     * This method does NOT re-run any sub-steps. It is purely a position restore.
     *
     * @param context  a fresh context loaded for this execution
     * @param snapshot a snapshot with status {@code RUNNING}
     */
    StateMachineInstance<C> reconstitute(C context, ExecutionSnapshot snapshot);

    /**
     * Same as {@link #reconstitute(Object, ExecutionSnapshot)} but also binds the
     * provided repository to the reconstituted instance so that subsequent
     * checkpoints are saved there.
     */
    StateMachineInstance<C> reconstitute(C context, ExecutionSnapshot snapshot, SnapshotRepository repository);

    /**
     * Resume a {@code FAILED} instance from a failure snapshot.
     * <p>
     * Use this when the snapshot status is {@code FAILED} and you want to retry.
     * The returned instance is positioned at the failed state; the caller calls
     * {@code proceed()} next to re-run the failed sub-steps (skipping those that
     * already completed).
     * <p>
     * Unlike {@code reconstitute}, this method populates the execution record with
     * the completed sub-step results from the snapshot so the resume policy can
     * correctly skip them.
     *
     * @param context  a fresh context loaded for this execution (see
     *                 <a href="https://github.com/hypercell/fsm-library/blob/main/docs/05-persistence-and-retry.md#context-on-resume">Context on resume</a>)
     * @param snapshot a snapshot with status {@code FAILED}
     */
    StateMachineInstance<C> resume(C context, ExecutionSnapshot snapshot);

    /**
     * Same as {@link #resume(Object, ExecutionSnapshot)} but also binds the provided
     * repository to the resumed instance.
     */
    StateMachineInstance<C> resume(C context, ExecutionSnapshot snapshot,
                                   SnapshotRepository repository);
}
