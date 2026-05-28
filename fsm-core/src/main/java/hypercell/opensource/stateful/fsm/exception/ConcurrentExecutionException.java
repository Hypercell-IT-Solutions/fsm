package hypercell.opensource.stateful.fsm.exception;

/**
 * Thrown by StateMachineManager when a second request arrives for an executionId
 * that is already being processed by another thread.
 * <p>
 * WHAT THIS PROTECTS:
 * Without this guard, two simultaneous HTTP requests for the same workflow could
 * both load the snapshot, both reconstitute an instance, and both call trigger().
 * The second write would silently overwrite the first checkpoint — leaving the
 * machine in an inconsistent state.
 * <p>
 * WHAT THIS DOES NOT PROTECT:
 * This is an in-process lock (ReentrantLock per executionId). It prevents concurrent
 * access within the same JVM only. For distributed deployments (multiple instances
 * behind a load balancer), the SnapshotRepository implementation must provide its
 * own concurrency control — for example optimistic locking in PostgreSQL, or
 * SET NX in Redis.
 * <p>
 * CALLER GUIDANCE:
 * Catch this exception at the HTTP layer and return HTTP 409 Conflict. The client
 * should retry after a short delay.
 */
public class ConcurrentExecutionException extends StateMachineException {
    private final String executionId;

    public ConcurrentExecutionException(String executionId) {
        super(String.format(
                "Execution '%s' is already being processed by another request. " +
                        "Retry after the in-flight request completes.", executionId));
        this.executionId = executionId;
    }

    public String getExecutionId() {
        return executionId;
    }
}
