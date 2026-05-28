package hypercell.opensource.stateful.fsm.execution;

/**
 * The aggregate outcome of running SubStepRunner over one state's sub-steps.
 * <p>
 * This is an internal class — callers never see it. The SubStepRunner returns it,
 * and DefaultStateMachineInstance inspects it to decide whether to continue
 * or enter FAILED status.
 */
public final class SubStepRunResult {

    private final boolean completed;
    private final String failedSubStepName;
    private final Throwable error;

    private SubStepRunResult(boolean completed, String failedSubStepName, Throwable error) {
        this.completed = completed;
        this.failedSubStepName = failedSubStepName;
        this.error = error;
    }

    /**
     * All sub-steps completed successfully.
     */
    public static SubStepRunResult completed() {
        return new SubStepRunResult(true, null, null);
    }

    /**
     * A sub-step failed.
     */
    public static SubStepRunResult failed(String subStepName, Throwable error) {
        return new SubStepRunResult(false, subStepName, error);
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isFailed() {
        return !completed;
    }

    public String getFailedSubStepName() {
        return failedSubStepName;
    }

    public Throwable getError() {
        return error;
    }
}
