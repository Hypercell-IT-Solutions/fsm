package hypercell.opensource.stateful.fsm.retry;

import java.time.Duration;

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
