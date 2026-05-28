package hypercell.opensource.stateful.fsm.retry;

import java.time.Duration;

public class NoAutoRetryPolicy implements RetryPolicy {
    public static final NoAutoRetryPolicy INSTANCE = new NoAutoRetryPolicy();

    @Override
    public boolean shouldRetry(int attempt, Throwable err) {
        return false;
    }

    @Override
    public Duration backoffFor(int attempt) {
        return Duration.ZERO;
    }

    @Override
    public int maxAttempts() {
        return 0;
    }
}
