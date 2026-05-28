package hypercell.opensource.stateful.fsm.retry;

import java.time.Duration;

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
