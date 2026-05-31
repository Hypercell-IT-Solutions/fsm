package io.hypercell.fsm.exception;

/**
 * Thrown when fail to perform an operation related to {@link io.hypercell.fsm.retry.RetryCoordinator}
 */
public class RetryException extends StateMachineException {
    public RetryException(Throwable cause) {
        super(cause);
    }
}
