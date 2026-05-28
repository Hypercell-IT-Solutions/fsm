package hypercell.opensource.stateful.fsm.exception;

/**
 * Thrown when fail to perform an operation related to {@link hypercell.opensource.stateful.fsm.resume.SnapshotRepository}
 */
public class SnapshotException extends StateMachineException {
    public SnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
