package hypercell.opensource.stateful.fsm.retry;

import java.time.Duration;

/**
 * Defines the rules for automatic retry after a sub-step failure.
 * Built-in implementations: ExponentialBackoffPolicy, FixedDelayPolicy, NoAutoRetryPolicy.
 */
public interface RetryPolicy {
    boolean shouldRetry(int attemptNumber, Throwable lastError);

    Duration backoffFor(int attemptNumber);

    int maxAttempts();
}
