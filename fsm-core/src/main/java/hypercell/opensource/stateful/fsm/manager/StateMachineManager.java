package hypercell.opensource.stateful.fsm.manager;

import hypercell.opensource.stateful.fsm.core.ContextLoader;
import hypercell.opensource.stateful.fsm.core.StateMachineDefinition;
import hypercell.opensource.stateful.fsm.resume.ExecutionSnapshot;
import hypercell.opensource.stateful.fsm.resume.SnapshotRepository;

import java.util.Optional;

/**
 * Orchestrates the full request lifecycle for HTTP-driven state machine workflows.
 * <p>
 * This is the component your HTTP endpoint talks to directly. Each call to trigger()
 * encapsulates the complete load → reconstruct → execute → save cycle, so the
 * endpoint never needs to know about snapshots, reconstitution, or machine instances.
 * <p>
 * FULL LIFECYCLE PER REQUEST:
 * <p>
 * 1. Acquire per-executionId lock (throws ConcurrentExecutionException if locked)
 * 2. Load snapshot from repository
 * 3. Resolve context (from contextLoader or caller-supplied override)
 * 4. Branch on snapshot status:
 * No snapshot  → newInstance(ctx, executionId) → trigger(event)
 * RUNNING      → reconstitute(ctx, snapshot)   → trigger(event)
 * FAILED       → resume(ctx, snapshot)         → proceed() → trigger(event)
 * COMPLETED    → throw CompletedMachineException
 * 5. Checkpoint is saved internally by trigger() / proceed()
 * 6. Release lock
 * 7. Return ManagedTransitionResult
 * <p>
 * FAILED + NEW EVENT (step 4 FAILED branch):
 * When the previous request failed mid-sub-step, the manager auto-retries the failed
 * sub-steps via proceed() before applying the new event. The sub-steps that already
 * completed in the previous run are skipped — they are NOT re-executed.
 * <p>
 * CONCURRENCY:
 * Per-executionId in-process locking prevents concurrent access within the same JVM.
 * For distributed deployments, the SnapshotRepository implementation should add
 * optimistic locking (e.g. database compare-and-swap, Redis SET NX).
 * <p>
 * SPRING BOOT USAGE:
 * <pre>{@code
 * @Bean
 * public StateMachineManager<OrderContext> orderMachineManager(
 *         StateMachineDefinition<OrderContext> definition,
 *         SnapshotRepository repository) {
 *     return definition.newManager(repository);
 * }
 *
 * // In your controller:
 * ManagedTransitionResult<OrderContext> result =
 *     manager.trigger(orderId, request.getEvent());
 * }</pre>
 *
 * @param <C> the context type flowing through the machine
 */
public interface StateMachineManager<C> {

    /**
     * Process an event using the manager's configured contextLoader to supply ctx.
     *
     * @param executionId your business entity ID (orderId, transactionId, etc.)
     * @param event       the event name from the incoming request
     */
    ManagedTransitionResult<C> trigger(String executionId, String event);

    /**
     * Process an event with a caller-supplied context that overrides the contextLoader
     * for this call only. Useful when the context is already available in the request
     * (e.g. the HTTP request body contains the order data).
     *
     * @param contextOverride the ctx to use; null falls back to the contextLoader
     */
    ManagedTransitionResult<C> trigger(String executionId, String event, C contextOverride);

    /**
     * Manually retry failed sub-steps without triggering a new event.
     * <p>
     * Use this when you want to retry a failed execution before the next business
     * event arrives — for example, a scheduled job that retries all FAILED executions.
     * The caller controls when to retry; the library handles which sub-steps to skip.
     */
    ManagedTransitionResult<C> proceed(String executionId);

    /**
     * Manually retry with a context override.
     */
    ManagedTransitionResult<C> proceed(String executionId, C contextOverride);

    /**
     * Load the current snapshot without changing anything.
     * Returns empty if the execution has never started or has completed and been cleaned up.
     */
    Optional<ExecutionSnapshot> snapshotOf(String executionId);

    /**
     * Re-schedules retries for all FAILED/RETRY_SCHEDULED executions found
     * in the repository. Call this once on application startup.
     * <p>
     * For RETRY_SCHEDULED snapshots: re-schedules using the remaining delay
     * (or immediately if the scheduled time has already passed).
     * For FAILED snapshots awaiting manual retry: skips them — the developer
     * decided not to auto-retry these, that decision should be respected.
     */
    void recoverPendingRetries();

    /**
     * Create a new manager with a different context loader.
     * Useful when the definition's context loader doesn't fit your use case,
     * but you don't want to pass contextOverride on every trigger/proceed call.
     * <p>
     * The returned manager delegates to this one for all operations
     * except context loading.
     *
     * @param contextLoader the context loader to use instead of the definition's
     * @return a new manager bound to the provided context loader
     */
    StateMachineManager<C> withContextLoader(ContextLoader<C> contextLoader);

    /**
     * The only way to create a StateMachineManager.
     * Keeps DefaultStateMachineManager invisible to consumers.
     * Uses the context loader from the definition.
     */
    static <C> StateMachineManager<C> create(StateMachineDefinition<C> definition,
                                             SnapshotRepository repository) {
        return new DefaultStateMachineManager<>(definition, repository, definition.contextLoader());
    }
}
