package hypercell.opensource.stateful.fsm.exception;

/**
 * Thrown when fail to perform an operation related to {@link hypercell.opensource.stateful.fsm.retry.RetryCoordinator}
 */
public class RetryException extends StateMachineException {
    public RetryException(Throwable cause) {
        super(cause);
    }
}
