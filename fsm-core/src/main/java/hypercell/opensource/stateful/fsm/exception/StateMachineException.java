package hypercell.opensource.stateful.fsm.exception;

/**
 * Base unchecked exception for all state machine errors.
 * <p>
 * Using unchecked (RuntimeException) deliberately — the caller should not need to
 * wrap every trigger() call in try-catch for expected flow. Failures that need
 * handling (like SubStepExecutionException) are caught at the coordinator level.
 */
public class StateMachineException extends RuntimeException {

    public StateMachineException(String message) {
        super(message);
    }

    public StateMachineException(Throwable cause) {
        super(cause);
    }

    public StateMachineException(String message, Throwable cause) {
        super(message, cause);
    }
}
