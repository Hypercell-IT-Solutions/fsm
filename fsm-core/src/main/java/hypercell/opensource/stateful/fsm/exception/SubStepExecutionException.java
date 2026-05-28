package hypercell.opensource.stateful.fsm.exception;

/**
 * Thrown by StateMachineInstance.trigger() or proceed() when a sub-step action
 * throws or returns ActionResult.failed().
 * <p>
 * This exception carries the full context of the failure so the caller can decide
 * what to do: log it, alert, inspect which sub-step failed, etc.
 * <p>
 * When this is thrown, the snapshot has already been saved (if a repository is
 * configured), so the execution can be resumed later without re-running the work
 * that already succeeded.
 */
public class SubStepExecutionException extends StateMachineException {

    private final String stateName;
    private final String subStepName;

    public SubStepExecutionException(String stateName, String subStepName, Throwable cause) {
        super(String.format(
                "Sub-step '%s' in state '%s' failed: %s",
                subStepName, stateName, cause != null ? cause.getMessage() : "unknown error"), cause);
        this.stateName = stateName;
        this.subStepName = subStepName;
    }

    public SubStepExecutionException(String stateName, String subStepName, String message) {
        super(String.format("Sub-step '%s' in state '%s' failed: %s",
                subStepName, stateName, message));
        this.stateName = stateName;
        this.subStepName = subStepName;
    }

    public String getStateName() {
        return stateName;
    }

    public String getSubStepName() {
        return subStepName;
    }
}
