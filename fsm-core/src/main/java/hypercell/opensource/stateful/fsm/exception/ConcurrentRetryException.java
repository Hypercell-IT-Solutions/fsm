package hypercell.opensource.stateful.fsm.exception;

/**
 * Thrown by RetryCoordinator.manualRetry() when a retry is attempted while the
 * same execution is already running (either auto-retry or another manual call).
 * <p>
 * This protects against double-execution of sub-steps that call external systems.
 * The caller should wait and retry again later, or check the snapshot status first.
 */
public class ConcurrentRetryException extends StateMachineException {

    private final String executionId;

    public ConcurrentRetryException(String executionId) {
        super(String.format(
                "Cannot start a retry for execution '%s' — it is already RUNNING. " +
                        "Wait for the current run to complete or fail before retrying.", executionId));
        this.executionId = executionId;
    }

    public String getExecutionId() {
        return executionId;
    }
}
