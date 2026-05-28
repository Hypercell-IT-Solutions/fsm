package hypercell.opensource.stateful.fsm.retry;

import java.time.Duration;

/**
 * Pluggable mechanism for scheduling automatic retries after a backoff delay.
 * Built-in implementation: ThreadPoolRetryScheduler.
 */
public interface RetryScheduler {
    void schedule(String executionId, Duration delay, Runnable retryAction);

    void cancel(String executionId);

    default void shutdown() {
    }
}
