package io.hypercell.fsm.retry;

import java.time.Duration;

/**
 * Defines the rules for automatic retry after a sub-step failure.
 * <p>
 * Built-in implementations: {@link ExponentialBackoffPolicy}, {@link FixedDelayPolicy},
 * {@link NoAutoRetryPolicy}.
 * Implement this interface for custom retry logic (e.g. retry only on certain exception types,
 * or use a non-linear backoff curve).
 */
public interface RetryPolicy {

    /**
     * Decide whether another retry attempt should be made.
     *
     * @param attemptNumber the number of attempts that have already occurred (starts at 1
     *                      for the first failure, 2 for the first retry failure, etc.)
     * @param lastError     the exception from the most recent failure; may be {@code null}
     *                      when called from startup recovery
     * @return {@code true} to schedule another retry; {@code false} to leave the snapshot
     *         in {@code FAILED} state awaiting manual retry
     */
    boolean shouldRetry(int attemptNumber, Throwable lastError);

    /**
     * Return the delay to wait before the next retry attempt.
     *
     * @param attemptNumber same semantics as in {@link #shouldRetry}
     * @return the backoff duration; must not be negative
     */
    Duration backoffFor(int attemptNumber);

    /**
     * The maximum number of attempts before auto-retry stops.
     * Informational — the actual stop condition is determined by {@link #shouldRetry}.
     */
    int maxAttempts();
}
