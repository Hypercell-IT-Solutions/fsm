package hypercell.opensource.stateful.fsm.exception;

/**
 * Thrown by StateMachineManager when an event is triggered on an execution that
 * has already reached a terminal state.
 * <p>
 * A completed execution is immutable — no further events can change its state.
 * The snapshot was deleted from the repository upon completion, so this exception
 * is thrown when the manager finds no snapshot AND can determine (via the deletion
 * policy) that the execution is done, or when a COMPLETED status is explicitly
 * stored.
 * <p>
 * CALLER GUIDANCE:
 * Return HTTP 409 Conflict or HTTP 422 Unprocessable Entity depending on your API
 * design. Log the attempt for audit purposes — triggering on a completed workflow
 * is usually a client bug (duplicate request, stale event from a queue).
 */
public class CompletedMachineException extends StateMachineException {

    private final String executionId;
    private final String finalState;

    public CompletedMachineException(String executionId, String finalState) {
        super(String.format(
                "Execution '%s' has already completed at terminal state '%s'. " +
                        "No further events can be triggered.", executionId, finalState));
        this.executionId = executionId;
        this.finalState = finalState;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getFinalState() {
        return finalState;
    }
}
