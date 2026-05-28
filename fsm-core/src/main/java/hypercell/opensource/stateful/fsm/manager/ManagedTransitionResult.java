package hypercell.opensource.stateful.fsm.manager;

import hypercell.opensource.stateful.fsm.core.ExecutionStatus;

/**
 * The result of a StateMachineManager.trigger() or proceed() call.
 * <p>
 * Contains everything the HTTP endpoint needs to build a response:
 * the new state, the overall status, and whether a prior failure was
 * auto-retried before the event was applied.
 *
 * @param <C> the context type (carried for type safety, not always populated)
 */
public final class ManagedTransitionResult<C> {

    private final String executionId;
    private final String fromState;
    private final String toState;
    private final ExecutionStatus executionStatus;

    /**
     * True when the manager found a FAILED snapshot and auto-proceeded
     * (retried the failed sub-steps) before applying the incoming event.
     * Useful for logging or responding with extra context to the caller.
     */
    private final boolean proceededFromFailure;

    /**
     * Non-null only when executionStatus == FAILED.
     * Tells the caller which sub-step failed so they can surface it in their response.
     */
    private final String failedSubStepName;
    private final String failedStateName;

    private ManagedTransitionResult(Builder<C> b) {
        this.executionId = b.executionId;
        this.fromState = b.fromState;
        this.toState = b.toState;
        this.executionStatus = b.executionStatus;
        this.proceededFromFailure = b.proceededFromFailure;
        this.failedSubStepName = b.failedSubStepName;
        this.failedStateName = b.failedStateName;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getFromState() {
        return fromState;
    }

    public String getToState() {
        return toState;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public boolean isProceededFromFailure() {
        return proceededFromFailure;
    }

    public String getFailedSubStepName() {
        return failedSubStepName;
    }

    public String getFailedStateName() {
        return failedStateName;
    }

    public boolean isCompleted() {
        return executionStatus == ExecutionStatus.COMPLETED;
    }

    public boolean isRunning() {
        return executionStatus == ExecutionStatus.RUNNING;
    }

    public boolean isFailed() {
        return executionStatus == ExecutionStatus.FAILED;
    }

    @Override
    public String toString() {
        return "ManagedTransitionResult{" +
                "executionId='" + executionId + '\'' +
                ", " + fromState + " → " + toState +
                ", status=" + executionStatus +
                (proceededFromFailure ? ", proceededFromFailure=true" : "") +
                (failedSubStepName != null ? ", failedAt=" + failedStateName + "/" + failedSubStepName : "") +
                '}';
    }

    public static <C> Builder<C> builder() {
        return new Builder<>();
    }

    public static final class Builder<C> {
        private String executionId;
        private String fromState;
        private String toState;
        private ExecutionStatus executionStatus;
        private boolean proceededFromFailure = false;
        private String failedSubStepName;
        private String failedStateName;

        public Builder<C> executionId(String v) {
            executionId = v;
            return this;
        }

        public Builder<C> fromState(String v) {
            fromState = v;
            return this;
        }

        public Builder<C> toState(String v) {
            toState = v;
            return this;
        }

        public Builder<C> executionStatus(ExecutionStatus v) {
            executionStatus = v;
            return this;
        }

        public Builder<C> proceededFromFailure(boolean v) {
            proceededFromFailure = v;
            return this;
        }

        public Builder<C> failedSubStepName(String v) {
            failedSubStepName = v;
            return this;
        }

        public Builder<C> failedStateName(String v) {
            failedStateName = v;
            return this;
        }

        public ManagedTransitionResult<C> build() {
            return new ManagedTransitionResult<>(this);
        }
    }
}
