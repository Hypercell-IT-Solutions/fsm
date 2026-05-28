package hypercell.opensource.stateful.fsm.retry;

import java.time.Duration;

/**
 * A {@link RetryPolicy} that doubles the delay on each attempt, capped at a maximum.
 * <p>
 * Delay formula: {@code min(baseDelay * 2^(attempt-1), maxDelay)}.
 * <p>
 * Example — {@code exponentialBackoff(5, 2s, 10min)}:
 * <ul>
 *   <li>Attempt 1 (first failure): immediate</li>
 *   <li>Attempt 2: 2 s</li>
 *   <li>Attempt 3: 4 s</li>
 *   <li>Attempt 4: 8 s</li>
 *   <li>Attempt 5: 10 min (capped)</li>
 * </ul>
 * After {@code maxAttempts}, the snapshot stays {@code FAILED} awaiting manual retry.
 * <p>
 * Obtain via {@link hypercell.opensource.stateful.fsm.StateMachine#exponentialBackoff}.
 */
public class ExponentialBackoffPolicy implements RetryPolicy {
    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;

    public ExponentialBackoffPolicy(int maxAttempts, Duration baseDelay, Duration maxDelay) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
    }

    @Override
    public boolean shouldRetry(int attempt, Throwable err) {
        return attempt <= maxAttempts;
    }

    @Override
    public Duration backoffFor(int attempt) {
        long ms = baseDelay.toMillis() * (1L << Math.min(attempt - 1, 30));
        return Duration.ofMillis(Math.min(ms, maxDelay.toMillis()));
    }

    @Override
    public int maxAttempts() {
        return maxAttempts;
    }
}
