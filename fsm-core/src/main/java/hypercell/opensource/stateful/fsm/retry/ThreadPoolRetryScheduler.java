package hypercell.opensource.stateful.fsm.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

public class ThreadPoolRetryScheduler implements RetryScheduler {
    private static final Logger log = LoggerFactory.getLogger(ThreadPoolRetryScheduler.class);

    private final ScheduledExecutorService executor;
    private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public ThreadPoolRetryScheduler(int threadPoolSize) {
        this.executor = Executors.newScheduledThreadPool(threadPoolSize, r -> {
            Thread t = new Thread(r, "fsm-retry-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

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
