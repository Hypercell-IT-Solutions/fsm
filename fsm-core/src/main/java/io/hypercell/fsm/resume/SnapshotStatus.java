package io.hypercell.fsm.resume;

/**
 * The persisted status of an execution snapshot.
 * <p>
 * This is separate from ExecutionStatus (which is the live runtime status)
 * because the snapshot lives in storage between retries. We need to track
 * whether a retry is already scheduled or in progress to prevent double execution.
 * <p>
 * The transitions are:
 * FAILED → RETRY_SCHEDULED  (RetryCoordinator scheduled an auto-retry)
 * FAILED → RUNNING          (manualRetry() was called)
 * RETRY_SCHEDULED → RUNNING (the scheduled retry fired)
 * RUNNING → FAILED          (the retry also failed)
 * RUNNING → COMPLETED       (the retry succeeded — snapshot gets deleted)
 */
public enum SnapshotStatus {

    /**
     * Execution failed. Waiting for retry (manual or scheduled).
     */
    FAILED,

    /**
     * An automatic retry has been scheduled. A RETRY_SCHEDULED snapshot
     * should not be manually retried until the scheduled retry completes or
     * is canceled — doing so risks double-execution.
     */
    RETRY_SCHEDULED,

    /**
     * A retry is currently executing. manualRetry() will throw
     * ConcurrentRetryException if status is RUNNING.
     */
    RUNNING,

    /**
     * Execution completed successfully. Snapshot should be deleted.
     */
    COMPLETED
}
