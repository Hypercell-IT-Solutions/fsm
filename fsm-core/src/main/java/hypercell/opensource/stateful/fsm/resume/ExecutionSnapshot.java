package hypercell.opensource.stateful.fsm.resume;

import hypercell.opensource.stateful.fsm.core.ActionResult;
import hypercell.opensource.stateful.fsm.execution.ExecutionRecord;
import hypercell.opensource.stateful.fsm.execution.StepRecord;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A serializable point-in-time capture of a failed execution.
 * <p>
 * This is what gets persisted to the SnapshotRepository when a machine fails.
 * On resume, the DefaultStateMachineDefinition reads it to reconstruct an
 * instance positioned at the failure point.
 * <p>
 * KEY DESIGN DECISION — WHAT TO STORE:
 * We store only the COMPLETED sub-step results (not the failed ones), because:
 * - Completed steps are what we want to SKIP on resume
 * - The failed step will be re-executed, so we don't need its old result
 * - This keeps the snapshot minimal
 * <p>
 * SERIALIZATION:
 * This class is designed to be easily serialized to JSON or any other format.
 * All fields are either primitives, Strings, Instants, or Maps of those types.
 * The SnapshotRepository implementation handles the actual serialization.
 * <p>
 * The composite key format for completedSubStepResults is "stateName::subStepName".
 * This separator (::) must not appear in state or sub-step names — the builder
 * validates this.
 */
public class ExecutionSnapshot {

    private final String executionId;
    private final String machineDefinitionId;
    private final String currentStateName;
    private final String failedStateName;
    private final String failedSubStepName;
    private final String lastTriggerEvent;
    private final Map<String, ActionResult> completedSubStepResults;
    private final int attemptNumber;
    private final Instant lastFailedAt;
    private final Instant scheduledRetryAt;
    private final String lastErrorMessage;
    private SnapshotStatus status;
    private final Instant capturedAt;

    private ExecutionSnapshot(Builder builder) {
        this.executionId = builder.executionId;
        this.machineDefinitionId = builder.machineDefinitionId;
        this.currentStateName = builder.currentStateName;
        this.failedStateName = builder.failedStateName;
        this.failedSubStepName = builder.failedSubStepName;
        this.lastTriggerEvent = builder.lastTriggerEvent;
        this.completedSubStepResults = Collections.unmodifiableMap(
                new HashMap<>(builder.completedSubStepResults));
        this.attemptNumber = builder.attemptNumber;
        this.lastFailedAt = builder.lastFailedAt;
        this.scheduledRetryAt = builder.scheduledRetryAt;
        this.lastErrorMessage = builder.lastErrorMessage;
        this.status = builder.status;
        this.capturedAt = builder.capturedAt;
    }

    /**
     * Factory method called by DefaultStateMachineInstance.takeSnapshot().
     * Converts the live ExecutionRecord into a serializable snapshot.
     */
    public static ExecutionSnapshot fromRecord(
            ExecutionRecord executionRecord,
            String pendingEvent,
            String machineDefinitionId,
            Map<String, ActionResult> completedSubStepResults) {
        return new Builder()
                .executionId(executionRecord.getExecutionId())
                .machineDefinitionId(machineDefinitionId)
                .currentStateName(executionRecord.getFailedStateName())
                .failedStateName(executionRecord.getFailedStateName())
                .failedSubStepName(executionRecord.getFailedSubStepName())
                .lastTriggerEvent(pendingEvent)
                .completedSubStepResults(completedSubStepResults)
                .attemptNumber(1)
                .lastFailedAt(Instant.now())
                .lastErrorMessage(null)
                .status(SnapshotStatus.FAILED)
                .capturedAt(Instant.now())
                .build();
    }

    public static ExecutionSnapshot checkpoint(ExecutionRecord executionRecord, String machineDefinitionId) {
        Map<String, ActionResult> completed = executionRecord.getSteps().stream()
                .filter(s -> s.getResult().isSuccess())
                .collect(Collectors.toMap(
                        StepRecord::compositeKey,
                        StepRecord::getResult,
                        (a, b) -> b
                ));

        return new Builder()
                .executionId(executionRecord.getExecutionId())
                .machineDefinitionId(machineDefinitionId)
                .currentStateName(executionRecord.getCurrentStateName())
                .lastTriggerEvent(executionRecord.getLastTriggerEvent())
                .completedSubStepResults(completed)
                .status(SnapshotStatus.RUNNING)
                .capturedAt(Instant.now())
                .build();
    }

    public ExecutionSnapshot withAttemptNumber(int newAttempt) {
        return new Builder(this).attemptNumber(newAttempt).build();
    }

    public ExecutionSnapshot withStatus(SnapshotStatus newStatus) {
        return new Builder(this).status(newStatus).build();
    }

    public ExecutionSnapshot withScheduledRetryAt(Instant retryAt) {
        return new Builder(this).scheduledRetryAt(retryAt)
                .status(SnapshotStatus.RETRY_SCHEDULED).build();
    }

    public ExecutionSnapshot withMachineDefinitionId(String id) {
        return new Builder(this).machineDefinitionId(id).build();
    }

    public boolean isRunning() {
        return status == SnapshotStatus.RUNNING;
    }

    public boolean isFailed() {
        return status == SnapshotStatus.FAILED;
    }

    public boolean isCompleted() {
        return status == SnapshotStatus.COMPLETED;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getMachineDefinitionId() {
        return machineDefinitionId;
    }

    public String getCurrentStateName() {
        return currentStateName;
    }

    public String getFailedStateName() {
        return failedStateName;
    }

    public String getFailedSubStepName() {
        return failedSubStepName;
    }

    public String getLastTriggerEvent() {
        return lastTriggerEvent;
    }

    public Map<String, ActionResult> getCompletedSubStepResults() {
        return completedSubStepResults;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Instant getLastFailedAt() {
        return lastFailedAt;
    }

    public Instant getScheduledRetryAt() {
        return scheduledRetryAt;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public SnapshotStatus getStatus() {
        return status;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void setStatus(SnapshotStatus status) {
        this.status = status;
    }

    /**
     * Check whether a specific sub-step was completed and recorded in this snapshot.
     * Key format: "stateName::subStepName"
     */
    public boolean isSubStepCompleted(String stateName, String subStepName) {
        return completedSubStepResults.containsKey(stateName + "::" + subStepName);
    }

    public static class Builder {
        String executionId = "";
        String machineDefinitionId = "";
        String currentStateName;
        String failedStateName;
        String failedSubStepName;
        String lastTriggerEvent;
        Map<String, ActionResult> completedSubStepResults = new HashMap<>();
        int attemptNumber = 1;
        Instant lastFailedAt = Instant.now();
        Instant scheduledRetryAt;
        String lastErrorMessage;
        SnapshotStatus status = SnapshotStatus.FAILED;
        Instant capturedAt = Instant.now();

        public Builder() {
        }

        Builder(ExecutionSnapshot source) {
            this.executionId = source.executionId;
            this.machineDefinitionId = source.machineDefinitionId;
            this.currentStateName = source.currentStateName;
            this.failedStateName = source.failedStateName;
            this.failedSubStepName = source.failedSubStepName;
            this.lastTriggerEvent = source.lastTriggerEvent;
            this.completedSubStepResults = new HashMap<>(source.completedSubStepResults);
            this.attemptNumber = source.attemptNumber;
            this.lastFailedAt = source.lastFailedAt;
            this.scheduledRetryAt = source.scheduledRetryAt;
            this.lastErrorMessage = source.lastErrorMessage;
            this.status = source.status;
            this.capturedAt = source.capturedAt;
        }

        public Builder executionId(String v) {
            executionId = v;
            return this;
        }

        public Builder machineDefinitionId(String v) {
            machineDefinitionId = v;
            return this;
        }

        public Builder currentStateName(String v) {
            currentStateName = v;
            return this;
        }

        public Builder failedStateName(String v) {
            failedStateName = v;
            return this;
        }

        public Builder failedSubStepName(String v) {
            failedSubStepName = v;
            return this;
        }

        public Builder lastTriggerEvent(String v) {
            lastTriggerEvent = v;
            return this;
        }

        public Builder completedSubStepResults(Map<String, ActionResult> v) {
            completedSubStepResults = v;
            return this;
        }

        public Builder attemptNumber(int v) {
            attemptNumber = v;
            return this;
        }

        public Builder lastFailedAt(Instant v) {
            lastFailedAt = v;
            return this;
        }

        public Builder scheduledRetryAt(Instant v) {
            scheduledRetryAt = v;
            return this;
        }

        public Builder lastErrorMessage(String v) {
            lastErrorMessage = v;
            return this;
        }

        public Builder status(SnapshotStatus v) {
            status = v;
            return this;
        }

        public Builder capturedAt(Instant v) {
            capturedAt = v;
            return this;
        }

        public ExecutionSnapshot build() {
            return new ExecutionSnapshot(this);
        }
    }
}
