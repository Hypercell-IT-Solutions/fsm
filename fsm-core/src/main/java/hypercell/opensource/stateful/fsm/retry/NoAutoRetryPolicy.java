package hypercell.opensource.stateful.fsm.retry;

import java.time.Duration;

/**
 * A {@link RetryPolicy} that never schedules an automatic retry.
 * <p>
 * The snapshot is still saved on failure, so manual retry via
 * {@code StateMachineInstance.proceed()} or
 * {@code StateMachineManager.proceed(executionId)} remains available.
 * <p>
 * This is the default policy when no retry policy is configured in the builder.
 * Use it when a human or external process should decide whether and when to retry.
 * <p>
 * Singleton via {@link #INSTANCE}; obtain via
 * {@link hypercell.opensource.stateful.fsm.StateMachine#noAutoRetry()}.
 */
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
