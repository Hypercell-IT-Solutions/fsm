package io.hypercell.fsm.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

/**
 * A {@link RetryScheduler} backed by a {@link java.util.concurrent.ScheduledExecutorService}.
 * <p>
 * Executes retries on a pool of named daemon threads ({@code "fsm-retry-scheduler"}).
 * Pending futures are tracked in a {@link java.util.concurrent.ConcurrentHashMap} to
 * support cancellation. Exceptions thrown by a retry action are caught and logged;
 * they do not affect other scheduled retries.
 * <p>
 * DEFAULT POOL SIZE: 10 threads (configurable via constructor).
 * For most single-JVM deployments, 2 threads is sufficient. Use a larger pool only
 * if many executions can fail and need simultaneous retry.
 * <p>
 * THREAD SAFETY: thread-safe.
 * <p>
 * Obtain via {@link io.hypercell.fsm.StateMachine#threadPoolScheduler(int)}.
 */
public class ThreadPoolRetryScheduler implements RetryScheduler {
    private static final Logger log = LoggerFactory.getLogger(ThreadPoolRetryScheduler.class);

    private final ScheduledExecutorService executor;
    private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    /**
     * Create a scheduler with the given thread pool size.
     *
     * @param threadPoolSize number of daemon threads dedicated to running retries;
     *                       2 is sufficient for most single-JVM deployments
     */
    public ThreadPoolRetryScheduler(int threadPoolSize) {
        this.executor = Executors.newScheduledThreadPool(threadPoolSize, r -> {
            Thread t = new Thread(r, "fsm-retry-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /** Create a scheduler with the default pool size of 10 threads. */
    public ThreadPoolRetryScheduler() {
        this(10);
    }

    @Override
    public void schedule(String executionId, Duration delay, Runnable retryAction) {
        log.info("[RetryScheduler] Scheduling retry for '{}' in {} ms", executionId, delay.toMillis());
        ScheduledFuture<?> future = executor.schedule(() -> {
            pending.remove(executionId);
            try {
                retryAction.run();
            } catch (Exception e) {
                log.error("[RetryScheduler] Retry for '{}' threw: {}", executionId, e.getMessage(), e);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
        pending.put(executionId, future);
    }

    @Override
    public void cancel(String executionId) {
        ScheduledFuture<?> f = pending.remove(executionId);
        if (f != null) {
            f.cancel(false);
            log.info("[RetryScheduler] Cancelled retry for '{}'", executionId);
        }
    }

    @Override
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
