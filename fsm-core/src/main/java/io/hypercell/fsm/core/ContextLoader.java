package io.hypercell.fsm.core;

/**
 * Loads a fresh context for a given execution ID.
 * <p>
 * Called by the manager on every request and by the retry coordinator before
 * each auto-retry attempt. The loader must restore all intermediate results
 * that completed sub-steps wrote to durable storage, so that the resumed
 * context is equivalent to the context at the point of failure.
 * <p>
 * EXCEPTION HANDLING:
 * Implementations may throw any checked or unchecked exception. The caller
 * (manager or retry coordinator) will propagate it as-is for unchecked exceptions,
 * or wrap checked exceptions in a {@code StateMachineException}.
 * <p>
 * Example — loading from a database:
 * <pre>{@code
 * definition.contextLoader(orderId -> orderRepository.findById(orderId))
 * }</pre>
 *
 * @param <C> the context type
 */
@FunctionalInterface
public interface ContextLoader<C> {

    /**
     * Load a fresh context for the given execution ID.
     *
     * @param executionId the execution ID (typically a business entity ID like orderId)
     * @return a restored context with all intermediate results in place
     * @throws Exception if loading fails (e.g., database error, file not found)
     */
    @SuppressWarnings("java:S112")
    C load(String executionId) throws Exception;
}
