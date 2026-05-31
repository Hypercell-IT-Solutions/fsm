package io.hypercell.fsm.manager;

import io.hypercell.fsm.core.ContextLoader;
import io.hypercell.fsm.core.ExecutionStatus;
import io.hypercell.fsm.core.StateMachineDefinition;
import io.hypercell.fsm.core.StateMachineInstance;
import io.hypercell.fsm.exception.CompletedMachineException;
import io.hypercell.fsm.exception.ConcurrentExecutionException;
import io.hypercell.fsm.exception.StateMachineException;
import io.hypercell.fsm.exception.SubStepExecutionException;
import io.hypercell.fsm.resume.ExecutionSnapshot;
import io.hypercell.fsm.resume.SnapshotRepository;
import io.hypercell.fsm.resume.SnapshotStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Default implementation of StateMachineManager.
 * <p>
 * CONCURRENCY MODEL:
 * A {@code ConcurrentHashMap<executionId, ReentrantLock>} provides per-execution in-process
 * locking. {@code ReentrantLock.tryLock()} returns immediately (non-blocking): if the lock
 * is held by another thread, {@code ConcurrentExecutionException} is thrown rather than
 * blocking the caller. This maps naturally to HTTP: return 409 Conflict immediately
 * rather than making the client wait.
 * <p>
 * Lock entries are cleaned up after each call to avoid unbounded map growth. Entries
 * are only removed when no thread is waiting — checked via {@code ReentrantLock.hasQueuedThreads()}.
 * <p>
 * AUTO-PROCEED BEHAVIOR:
 * When a FAILED snapshot is found and a new event arrives, the manager calls proceed()
 * first (which retries failed sub-steps, skipping the ones that already succeeded),
 * then calls trigger(event) to apply the new transition. This matches the requirement:
 * "auto-proceed, but completed sub-steps should not be evaluated twice."
 *
 * @param <C> the context type flowing through the machine
 */
public class DefaultStateMachineManager<C> implements StateMachineManager<C> {
    private static final Logger log = LoggerFactory.getLogger(DefaultStateMachineManager.class);

    private final StateMachineDefinition<C> definition;
    private final SnapshotRepository repository;
    private final ContextLoader<C> contextLoader;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService recoveryExecutor;

    DefaultStateMachineManager(StateMachineDefinition<C> definition,
                               SnapshotRepository repository,
                               ContextLoader<C> contextLoader) {
        this.definition = definition;
        this.repository = repository;
        this.contextLoader = contextLoader;
        this.recoveryExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "fsm-recovery");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public ManagedTransitionResult<C> trigger(String executionId, String event) {
        return trigger(executionId, event, null);
    }

    @Override
    public ManagedTransitionResult<C> trigger(String executionId, String event, C contextOverride) {
        ReentrantLock lock = acquireLock(executionId);
        try {
            return doTrigger(executionId, event, contextOverride);
        } finally {
            releaseLock(executionId, lock);
        }
    }

    @Override
    public ManagedTransitionResult<C> proceed(String executionId) {
        return proceed(executionId, null);
    }

    @Override
    public ManagedTransitionResult<C> proceed(String executionId, C contextOverride) {
        ReentrantLock lock = acquireLock(executionId);
        try {
            return doProceed(executionId, contextOverride);
        } finally {
            releaseLock(executionId, lock);
        }
    }

    @Override
    public ManagedTransitionResult<C> initialize(String executionId) {
        return initialize(executionId, null);
    }

    @Override
    public ManagedTransitionResult<C> initialize(String executionId, C contextOverride) {
        ReentrantLock lock = acquireLock(executionId);
        try {
            return doInitialize(executionId, contextOverride);
        } finally {
            releaseLock(executionId, lock);
        }
    }

    @Override
    public Optional<ExecutionSnapshot> snapshotOf(String executionId) {
        return repository.load(executionId);
    }

    @Override
    public StateMachineManager<C> withContextLoader(ContextLoader<C> contextLoader) {
        return new DefaultStateMachineManager<>(definition, repository, contextLoader);
    }

    @Override
    public boolean isInitialState(String stateName) {
        return definition.isInitialState(stateName);
    }

    @Override
    public boolean isTerminal(String stateName) {
        return definition.isTerminal(stateName);
    }

    @Override
    public void recoverPendingRetries() {
        if (definition.retryCoordinator() == null) {
            return;
        }

        List<ExecutionSnapshot> pending = repository.listPendingRetries();

        for (ExecutionSnapshot snapshot : pending) {
            boolean shouldRecover = (snapshot.getStatus() == SnapshotStatus.RETRY_SCHEDULED || snapshot.getStatus() == SnapshotStatus.FAILED)
                    && definition.retryCoordinator().getRetryPolicy().shouldRetry(snapshot.getAttemptNumber(), null);

            if (!shouldRecover) continue;

            Duration delay = Duration.ZERO;
            if (snapshot.getScheduledRetryAt() != null) {
                Duration remaining = Duration.between(
                        Instant.now(), snapshot.getScheduledRetryAt());
                if (!remaining.isNegative()) {
                    delay = remaining;
                }
            }

            String executionId = snapshot.getExecutionId();
            long delayMs = delay.toMillis();

            recoveryExecutor.schedule(() -> {
                try {
                    proceed(executionId);
                } catch (Exception e) {
                    log.warn("Recovery retry failed for '{}': {}", executionId, e.getMessage());
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    private ManagedTransitionResult<C> doTrigger(String executionId, String event,
                                                 C contextOverride) {
        Optional<ExecutionSnapshot> snapshotOpt = repository.load(executionId);
        C ctx = resolveContext(executionId, contextOverride);

        if (snapshotOpt.isEmpty()) {
            return firstTrigger(executionId, event, ctx);
        }

        ExecutionSnapshot snapshot = snapshotOpt.get();

        if (snapshot.isCompleted()) {
            throw new CompletedMachineException(
                    executionId, snapshot.getCurrentStateName());
        }

        if (snapshot.isFailed()) {
            return proceedThenTrigger(executionId, event, ctx, snapshot);
        }

        return reconstituteThenTrigger(executionId, event, ctx, snapshot);
    }

    /**
     * First event ever for this executionId.
     * Creates a fresh instance (initial state sub-steps run in constructor),
     * then fires the transition event.
     */
    private ManagedTransitionResult<C> firstTrigger(String executionId, String event, C ctx) {
        String fromState = definition.initialState().name();

        StateMachineInstance<C> instance;
        try {
            instance = definition.newInstance(ctx, executionId);
        } catch (SubStepExecutionException e) {
            return ManagedTransitionResult.<C>builder()
                    .executionId(executionId)
                    .fromState(fromState)
                    .toState(fromState)
                    .executionStatus(ExecutionStatus.FAILED)
                    .failedStateName(e.getStateName())
                    .failedSubStepName(e.getSubStepName())
                    .rootCause(e.getCause())
                    .build();
        }

        return executeTrigger(instance, event, fromState, false);
    }

    /**
     * FAILED snapshot + new event.
     * Auto-proceeds (retrying failed sub-steps, skipping completed ones),
     * then fires the new event if proceed() succeeds.
     */
    private ManagedTransitionResult<C> proceedThenTrigger(String executionId, String event,
                                                          C ctx, ExecutionSnapshot snapshot) {
        StateMachineInstance<C> instance = definition.resume(ctx, snapshot, repository);
        String fromState = instance.currentState().name();

        try {
            instance.proceed();
        } catch (SubStepExecutionException e) {
            return ManagedTransitionResult.<C>builder()
                    .executionId(executionId)
                    .fromState(fromState)
                    .toState(fromState)
                    .executionStatus(ExecutionStatus.FAILED)
                    .proceededFromFailure(true)
                    .failedStateName(e.getStateName())
                    .failedSubStepName(e.getSubStepName())
                    .rootCause(e.getCause())
                    .build();
        }

        return executeTrigger(instance, event, fromState, true);
    }

    /**
     * RUNNING snapshot — process restarted between requests.
     * Reconstitutes at currentStateName and fires the event.
     */
    private ManagedTransitionResult<C> reconstituteThenTrigger(String executionId, String event,
                                                               C ctx, ExecutionSnapshot snapshot) {
        StateMachineInstance<C> instance = definition.reconstitute(ctx, snapshot, repository);
        String fromState = instance.currentState().name();
        return executeTrigger(instance, event, fromState, false);
    }

    /**
     * Fires trigger(event) on an instance and builds the result.
     * SubStepExecutionException is caught here — the snapshot is already saved
     * inside handleFailure() so we just build a FAILED result.
     */
    private ManagedTransitionResult<C> executeTrigger(StateMachineInstance<C> instance,
                                                      String event, String fromState,
                                                      boolean proceededFromFailure) {
        try {
            instance.trigger(event);
            return ManagedTransitionResult.<C>builder()
                    .executionId(instance.executionId())
                    .fromState(fromState)
                    .toState(instance.currentState().name())
                    .executionStatus(instance.status())
                    .proceededFromFailure(proceededFromFailure)
                    .build();
        } catch (SubStepExecutionException e) {
            return ManagedTransitionResult.<C>builder()
                    .executionId(instance.executionId())
                    .fromState(fromState)
                    .toState(instance.currentState().name())
                    .executionStatus(ExecutionStatus.FAILED)
                    .proceededFromFailure(proceededFromFailure)
                    .failedStateName(e.getStateName())
                    .failedSubStepName(e.getSubStepName())
                    .rootCause(e.getCause())
                    .build();
        }
    }

    /**
     * Manual proceed — retry failed sub-steps without a new event.
     */
    private ManagedTransitionResult<C> doProceed(String executionId, C contextOverride) {
        ExecutionSnapshot snapshot = repository.load(executionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No snapshot found for executionId: " + executionId));

        if (snapshot.isCompleted()) {
            throw new CompletedMachineException(executionId, snapshot.getCurrentStateName());
        }
        if (!snapshot.isFailed()) {
            throw new IllegalStateException(
                    "proceed() requires a FAILED snapshot. Current status: " + snapshot.getStatus());
        }

        C ctx = resolveContext(executionId, contextOverride);
        StateMachineInstance<C> instance = definition.resume(ctx, snapshot, repository);
        String fromState = instance.currentState().name();

        try {
            instance.proceed();
            return ManagedTransitionResult.<C>builder()
                    .executionId(executionId)
                    .fromState(fromState)
                    .toState(instance.currentState().name())
                    .executionStatus(instance.status())
                    .build();
        } catch (SubStepExecutionException e) {
            return ManagedTransitionResult.<C>builder()
                    .executionId(executionId)
                    .fromState(fromState)
                    .toState(instance.currentState().name())
                    .executionStatus(ExecutionStatus.FAILED)
                    .failedStateName(e.getStateName())
                    .failedSubStepName(e.getSubStepName())
                    .rootCause(e.getCause())
                    .build();
        }
    }

    /**
     * Initialize a new execution: create instance, run initial sub-steps, save checkpoint,
     * stay in initial state. If already initialized, return the current state.
     */
    private ManagedTransitionResult<C> doInitialize(String executionId, C contextOverride) {
        Optional<ExecutionSnapshot> existing = repository.load(executionId);
        if (existing.isPresent()) {
            ExecutionSnapshot snapshot = existing.get();
            if (snapshot.isCompleted()) {
                throw new CompletedMachineException(
                        executionId, snapshot.getCurrentStateName());
            }
            ExecutionStatus status = snapshot.isFailed() ? ExecutionStatus.FAILED : ExecutionStatus.RUNNING;
            ManagedTransitionResult.Builder<C> builder = ManagedTransitionResult.<C>builder()
                    .executionId(executionId)
                    .fromState(snapshot.getCurrentStateName())
                    .toState(snapshot.getCurrentStateName())
                    .executionStatus(status);
            if (snapshot.isFailed()) {
                builder.failedStateName(snapshot.getFailedStateName())
                        .failedSubStepName(snapshot.getFailedSubStepName());
            }
            return builder.build();
        }

        String initialStateName = definition.initialState().name();
        C ctx = resolveContext(executionId, contextOverride);

        try {
            definition.newInstance(ctx, executionId);
            return ManagedTransitionResult.<C>builder()
                    .executionId(executionId)
                    .fromState(initialStateName)
                    .toState(initialStateName)
                    .executionStatus(ExecutionStatus.RUNNING)
                    .build();
        } catch (SubStepExecutionException e) {
            return ManagedTransitionResult.<C>builder()
                    .executionId(executionId)
                    .fromState(initialStateName)
                    .toState(initialStateName)
                    .executionStatus(ExecutionStatus.FAILED)
                    .failedStateName(e.getStateName())
                    .failedSubStepName(e.getSubStepName())
                    .rootCause(e.getCause())
                    .build();
        }
    }

    /**
     * Resolves the context for this request.
     * If contextOverride is provided, use it directly.
     * Otherwise, delegate to the manager's configured contextLoader.
     * <p>
     * Checked exceptions from the context loader are wrapped in StateMachineException;
     * unchecked exceptions are propagated as-is.
     */
    private C resolveContext(String executionId, C contextOverride) {
        if (contextOverride != null) {
            return contextOverride;
        }
        if (contextLoader != null) {
            try {
                return contextLoader.load(executionId);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new StateMachineException(
                        "Failed to load context for executionId '" + executionId + "'", e);
            }
        }
        throw new IllegalStateException(
                "No ctx available for executionId '" + executionId + "'. " +
                        "Either configure a contextLoader or pass a contextOverride.");
    }

    /**
     * Acquire the per-executionId lock. Returns immediately.
     * Throws ConcurrentExecutionException if another thread holds the lock.
     * <p>
     * NOTE: This is in-process only. For distributed deployments, implement
     * optimistic locking in your SnapshotRepository (e.g. compare-and-swap
     * in PostgreSQL, or SET NX with TTL in Redis).
     */
    private ReentrantLock acquireLock(String executionId) {
        ReentrantLock lock = locks.computeIfAbsent(executionId, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new ConcurrentExecutionException(executionId);
        }
        return lock;
    }

    /**
     * Release the lock and clean up the map entry if no threads are waiting.
     * Prevents unbounded growth of the locks map for long-running applications.
     */
    private void releaseLock(String executionId, ReentrantLock lock) {
        lock.unlock();
        locks.computeIfPresent(executionId, (k, l) ->
                l.hasQueuedThreads() ? l : null);
    }
}
