package io.hypercell.fsm.exception;

/**
 * Thrown when fail to perform an operation related to {@link io.hypercell.fsm.resume.SnapshotRepository}
 */
public class SnapshotException extends StateMachineException {
    public SnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
