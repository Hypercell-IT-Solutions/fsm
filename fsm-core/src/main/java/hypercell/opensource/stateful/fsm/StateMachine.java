package hypercell.opensource.stateful.fsm;

import hypercell.opensource.stateful.fsm.builder.StateMachineBuilder;
import hypercell.opensource.stateful.fsm.core.StateMachineDefinition;
import hypercell.opensource.stateful.fsm.listener.LoggingEventListener;
import hypercell.opensource.stateful.fsm.listener.MachineEventListener;
import hypercell.opensource.stateful.fsm.manager.StateMachineManager;
import hypercell.opensource.stateful.fsm.resume.FileSnapshotRepository;
import hypercell.opensource.stateful.fsm.resume.InMemorySnapshotRepository;
import hypercell.opensource.stateful.fsm.resume.SnapshotRepository;
import hypercell.opensource.stateful.fsm.retry.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Function;

/**
 * The single entry point to the state machine library.
 * <p>
 * All library usage starts with StateMachine.define("id").
 * Infrastructure components are available via static factory methods.
 */
public final class StateMachine {

    private StateMachine() {
    }

    /**
     * Begin defining a state machine.
     * Use when the entire workflow runs within a single thread or process,
     * and you control the instance lifecycle yourself.
     * Examples: unit tests, batch jobs, synchronous pipelines, building a custom orchestrator on top of this library.
     *
     * @param id  stable identifier for this machine type
     * @param <C> context type flowing through the machine
     */
    public static <C> StateMachineBuilder<C> define(String id) {
        return new StateMachineBuilder<>(id);
    }

    /**
     * Convenience factory for StateMachineManager.
     * Equivalent to: definition.newManager(repository, contextLoader)
     * Use when events arrive across multiple HTTP requests, the process may restart between requests.
     * <p>
     * The manager handles: load snapshot -> reconstruct -> execute -> save, concurrency protection,
     * and auto-retry of failed sub-steps.
     */
    public static <C> StateMachineManager<C> manager(StateMachineDefinition<C> definition,
                                                     SnapshotRepository repository,
                                                     Function<String, C> contextLoader) {
        return definition.newManager(repository, contextLoader);
    }

    /**
     * Convenience factory for StateMachineManager.
     * Equivalent to: definition.newManager(repository, contextLoader)
     */
    public static <C> StateMachineManager<C> manager(StateMachineDefinition<C> definition,
                                                     Function<String, C> contextLoader) {
        return definition.newManager(definition.repository(), contextLoader);
    }

    /**
     * In-memory store. Lost on restart. Best for testing and single-run scripts.
     */
    public static SnapshotRepository inMemoryRepository() {
        return InMemorySnapshotRepository.create();
    }

    /**
     * File-based store. Survives JVM restart (as long as the directory persists).
     * Fine for single-instance deployments; use a DB/Redis store for multi-instance.
     */
    public static SnapshotRepository fileRepository(Path directory) {
        return new FileSnapshotRepository(directory);
    }

    /**
     * Exponential backoff: delays double on each attempt, capped at maxDelay.
     * <p>
     * Example — 5 retries, starting at 2s, capped at 10 minutes:
     * exponentialBackoff(5, Duration.ofSeconds(2), Duration.ofMinutes(10))
     * Delays: 2s, 4s, 8s, 16s, 10min
     */
    public static RetryPolicy exponentialBackoff(int maxAttempts, Duration baseDelay, Duration maxDelay) {
        return new ExponentialBackoffPolicy(maxAttempts, baseDelay, maxDelay);
    }

    /**
     * Fixed delay: every retry waits the same amount of time.
     */
    public static RetryPolicy fixedDelay(int maxAttempts, Duration delay) {
        return new FixedDelayPolicy(maxAttempts, delay);
    }

    /**
     * No automatic retry. The snapshot is still saved on failure so manual
     * retry remains possible. Use when you want human-in-the-loop retry control.
     */
    public static RetryPolicy noAutoRetry() {
        return NoAutoRetryPolicy.INSTANCE;
    }

    /**
     * Default thread-pool scheduler. Good for most single-JVM deployments.
     *
     * @param threadPoolSize number of threads dedicated to running retries.
     *                       2 is sufficient unless you have many concurrent
     *                       machines failing simultaneously.
     */
    public static RetryScheduler threadPoolScheduler(int threadPoolSize) {
        return new ThreadPoolRetryScheduler(threadPoolSize);
    }

    /**
     * A structured logging listener that prints every lifecycle event.
     * Wire it in via builder.listener(StateMachine.loggingListener("[ORDER]")).
     */
    public static <C> MachineEventListener<C> loggingListener(String prefix) {
        return LoggingEventListener.withPrefix(prefix);
    }

    public static <C> MachineEventListener<C> loggingListener() {
        return LoggingEventListener.create();
    }
}
