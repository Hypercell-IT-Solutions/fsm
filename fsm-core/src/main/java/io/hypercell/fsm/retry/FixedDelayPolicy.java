package io.hypercell.fsm.retry;

import java.time.Duration;

/**
 * A {@link RetryPolicy} that waits the same fixed duration between every attempt.
 * <p>
 * Example — {@code fixedDelay(3, 30s)}: retries at +30s, +30s, +30s, then stops.
 * <p>
 * Obtain via {@link io.hypercell.fsm.StateMachine#fixedDelay}.
 */
public class FixedDelayPolicy implements RetryPolicy {
    private final int maxAttempts;
    private final Duration delay;

    public FixedDelayPolicy(int maxAttempts, Duration delay) {
        this.maxAttempts = maxAttempts;
        this.delay = delay;
    }

    @Override
    public boolean shouldRetry(int attempt, Throwable err) {
        return attempt <= maxAttempts;
    }

    @Override
    public Duration backoffFor(int attempt) {
        return delay;
    }

    @Override
    public int maxAttempts() {
        return maxAttempts;
    }
}
