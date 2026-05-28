package hypercell.opensource.stateful.fsm.retry;

import java.time.Duration;

/**
 * Pluggable mechanism for scheduling automatic retries after a backoff delay.
 * <p>
 * Built-in implementation: {@link ThreadPoolRetryScheduler}.
 * Implement this interface to integrate with a distributed task queue, a
 * database-backed scheduler, or any other mechanism.
 */
public interface RetryScheduler {

    /**
     * Schedule a retry to run after the given delay.
     * The scheduler must ensure that only one retry per {@code executionId} is
     * pending at a time (i.e. a new {@code schedule} call should replace any
     * existing pending retry for the same ID).
     *
     * @param executionId  identifies the execution being retried; used for cancellation
     * @param delay        how long to wait before invoking {@code retryAction}
     * @param retryAction  the retry logic to run; exceptions should be caught and logged
     */
    void schedule(String executionId, Duration delay, Runnable retryAction);

    /**
     * Cancel a previously scheduled retry, if one is pending.
     * If no retry is pending for this ID, this is a no-op.
     * Does not interrupt a retry that is already running.
     */
    void cancel(String executionId);

    /**
     * Gracefully shut down this scheduler, waiting for in-progress retries to complete.
     * The default implementation is a no-op; override in implementations that manage threads.
     */
    default void shutdown() {
    }
}
