# API Reference

Complete reference for all public types. Grouped by package.

---

## Entry point — `hypercell.opensource.stateful.fsm`

### `StateMachine`

The single entry point to the library. All usage starts here. Not instantiable.

#### Static factory methods

```java
// Begin defining a machine type
static <C> StateMachineBuilder<C> define(String id)
```
Returns a fluent builder. `id` is a stable identifier for this machine *type* (not a specific execution).

```java
// Create a manager (convenience — equivalent to definition.newManager(...))
static <C> StateMachineManager<C> manager(
    StateMachineDefinition<C> definition,
    SnapshotRepository repository,
    Function<String, C> contextLoader)

static <C> StateMachineManager<C> manager(
    StateMachineDefinition<C> definition,
    Function<String, C> contextLoader)  // uses repository already set on definition
```

```java
// Snapshot repositories
static SnapshotRepository inMemoryRepository()   // ConcurrentHashMap; testing only
static SnapshotRepository fileRepository(Path directory)  // .properties files; single-JVM
```

```java
// Retry policies
static RetryPolicy exponentialBackoff(int maxAttempts, Duration baseDelay, Duration maxDelay)
static RetryPolicy fixedDelay(int maxAttempts, Duration delay)
static RetryPolicy noAutoRetry()   // default; snapshot saved but no auto-retry
```

```java
// Retry scheduler
static RetryScheduler threadPoolScheduler(int threadPoolSize)
```

```java
// Event listeners
static <C> MachineEventListener<C> loggingListener()
static <C> MachineEventListener<C> loggingListener(String prefix)
```

---

## Core interfaces — `hypercell.opensource.stateful.fsm.core`

### `StateMachineDefinition<C>`

Immutable blueprint of a state machine. Thread-safe; share freely.

```java
String id()
StateDefinition<C> initialState()
StateDefinition<C> stateByName(String name)
List<TransitionDefinition<C>> transitionsFrom(String stateName)
SnapshotRepository repository()
ResumePolicy<C> resumePolicy()
RetryCoordinator<C> retryCoordinator()

// Create instances
StateMachineInstance<C> newInstance(C context)
StateMachineInstance<C> newInstance(C context, String executionId)

// Reconstitute from checkpoint (process restarted — snapshot status was RUNNING)
// Positions at snapshot.currentStateName(), status RUNNING; caller calls trigger() next
StateMachineInstance<C> reconstitute(C context, ExecutionSnapshot snapshot)
StateMachineInstance<C> reconstitute(C context, ExecutionSnapshot snapshot, SnapshotRepository repo)

// Resume from failure (snapshot status was FAILED)
// Positions at failed state; caller calls proceed() next
StateMachineInstance<C> resume(C context, ExecutionSnapshot snapshot)
StateMachineInstance<C> resume(C context, ExecutionSnapshot snapshot, SnapshotRepository repo)

// Create a manager bound to this definition
StateMachineManager<C> newManager(Function<String, C> contextLoader)
StateMachineManager<C> newManager(SnapshotRepository repository, Function<String, C> contextLoader)
```

---

### `StateMachineInstance<C>`

One live execution. **Not thread-safe.** Protect with the manager's lock or your own.

```java
String executionId()               // snapshot storage key
StateDefinition<C> currentState()  // current position
ExecutionStatus status()           // RUNNING | COMPLETED | FAILED
ExecutionRecord executionRecord()  // full step history (internal use)
C context()                        // the mutable context object

boolean isRunning()
boolean isCompleted()
boolean isFailed()
```

#### `trigger(String event)`

```java
StateDefinition<C> trigger(String event)
    throws InvalidEventException,       // no valid transition for this event
           SubStepExecutionException,   // a sub-step failed
           StateMachineException        // status != RUNNING
```

Execution order: `sourceState.onExit` → transition action → move → `targetState.onEntry` → sub-steps → checkpoint or complete.

#### `proceed()`

```java
StateDefinition<C> proceed()
    throws InvalidEventException,       // status != FAILED
           SubStepExecutionException    // sub-step failed again
```

Re-runs sub-steps of the current state, skipping those already recorded as completed. Transitions status `FAILED → RUNNING` on start.

#### Snapshot methods

```java
// Checkpoint the running machine (for manual use — manager calls this automatically)
ExecutionSnapshot takeCheckpoint()

// Snapshot on failure with the pending event recorded
ExecutionSnapshot takeSnapshot(String pendingEvent)
```

---

### `StateDefinition<C>`

Describes one state in the machine graph.

```java
String name()                              // unique within this machine
boolean isTerminal()                       // true if no outgoing transitions
List<SubStepDefinition<C>> subSteps()      // ordered work to execute on entry
Optional<StateHook<C>> hook()              // entry/exit callbacks, if any
```

---

### `TransitionDefinition<C>`

Describes one edge in the state graph.

```java
String event()                    // event name that triggers this transition
String sourceState()              // source state name
String targetState()              // target state name
Optional<Guard<C>> guard()        // condition (empty = always fire)
Optional<Action<C>> action()      // work during the transition (empty = none)
```

---

### `Guard<C>`

```java
@FunctionalInterface
boolean evaluate(C context)
```

Pure read-only predicate. Must not modify the context. Called synchronously. Multiple guards for the same event are evaluated in definition order; first `true` wins. `InvalidEventException` is thrown if none match.

---

### `Action<C>`

```java
@FunctionalInterface
ActionResult execute(C context) throws Exception
```

A unit of work. May read and write the context. Throws are caught by the library and converted to `ActionResult.failed(exception)`. Never return `null`; use `ActionResult.success()` for void actions.

---

### `StateHook<C>`

Lifecycle callbacks for state entry/exit. Default no-op implementations provided.

```java
default void onEntry(C context)   // runs after targetState is entered, before sub-steps
default void onExit(C context)    // runs before sourceState is exited (transition about to fire)
```

Hooks are **not retry-tracked** — they re-run on every entry/exit even after a resume. Keep them idempotent or side-effect-free.

---

### `SubStepDefinition<C>`

A named unit of work within a state.

```java
String name()         // stable snapshot key — treat like a DB column name
Action<C> action()    // the work to execute
```

Sub-step names must be unique within a state and must not contain `::`.

---

### `ActionResult`

Immutable outcome of an action or sub-step.

```java
// Factory methods
static ActionResult success()
static ActionResult success(Map<String, Object> output)   // output must be serializable
static ActionResult failed(Throwable error)
static ActionResult failed(String message)
static ActionResult skipped()   // set internally by the library; do not construct

// Accessors
ActionResult.Status getStatus()   // SUCCESS | FAILED | SKIPPED
Map<String, Object> getOutput()   // empty map if no output
String getErrorMessage()          // null if not failed
String getErrorType()             // fully-qualified exception class name; null if not from Throwable

boolean isSuccess()
boolean isFailed()
boolean isSkipped()
```

Output map values must be serialization-friendly (String, Number, Boolean, etc.).

---

### `ExecutionStatus`

```java
enum ExecutionStatus { RUNNING, COMPLETED, FAILED }
```

| Value | Meaning |
|---|---|
| `RUNNING` | Normal; `trigger()` and `proceed()` are accepted |
| `COMPLETED` | Terminal state reached; `trigger()` throws |
| `FAILED` | Sub-step or hook failed; `proceed()` transitions back to `RUNNING` |

---

### `StateConfigurer<C>`

Class-based alternative to inline `.state("NAME")...and()`. Useful for Spring DI.

```java
String stateName()                     // state identifier
void configure(StateBuilder<C> state)  // add sub-steps, hooks, transitions here; do NOT call .and()
```

Register via `StateMachineBuilder.state(StateConfigurer<C>)`.

---

### `SubStepHandler<C>`

Class-based alternative to an inline lambda for sub-steps. Useful for Spring DI and unit testing.

```java
String name()                              // stable snapshot key
ActionResult execute(C context) throws Exception
default Action<C> asAction()               // adapter for places that expect Action<C>
```

Register via `StateBuilder.subStep(SubStepHandler<C>)`.

---

## Builders — `hypercell.opensource.stateful.fsm.builder`

### `StateMachineBuilder<C>`

Returned by `StateMachine.define(id)`.

```java
StateMachineBuilder<C> initial(String stateName)
StateMachineBuilder<C> snapshotRepository(SnapshotRepository r)
StateMachineBuilder<C> retryPolicy(RetryPolicy p)
StateMachineBuilder<C> retryScheduler(RetryScheduler s)
StateMachineBuilder<C> contextLoader(Function<String, C> loader)
StateMachineBuilder<C> resumePolicy(ResumePolicy<C> p)
StateMachineBuilder<C> listener(MachineEventListener<C> listener)  // repeatable

StateBuilder<C> state(String name)              // inline state definition
StateMachineBuilder<C> state(StateConfigurer<C> configurer)  // class-based

StateMachineDefinition<C> build()               // validates and produces the immutable definition
```

**Auto-retry prerequisites:** if any non-`NoAutoRetry` policy is set, both `snapshotRepository` and `contextLoader` must also be set; `build()` will throw `StateMachineConfigurationException` otherwise.

---

### `StateBuilder<C>`

Returned by `StateMachineBuilder.state(String name)`.

```java
StateBuilder<C> subStep(String name, Action<C> action)
StateBuilder<C> subStep(SubStepHandler<C> handler)
StateBuilder<C> onEntry(Consumer<C> fn)   // composable: multiple calls stack
StateBuilder<C> onExit(Consumer<C> fn)    // composable: multiple calls stack
StateBuilder<C> terminal()
TransitionBuilder<C> on(String event)     // begin defining a transition
StateMachineBuilder<C> and()              // close state, return to machine builder
```

Sub-step names must be unique within the state and must not contain `::`.

---

### `TransitionBuilder<C>`

Returned by `StateBuilder.on(String event)`.

```java
TransitionBuilder<C> to(String targetState)     // required
TransitionBuilder<C> when(Guard<C> guard)        // optional; no guard = always fire
TransitionBuilder<C> action(Action<C> action)    // optional; runs between onExit and onEntry
StateBuilder<C> end()                            // close transition, return to StateBuilder
```

---

## Listeners — `hypercell.opensource.stateful.fsm.listener`

### `MachineEventListener<C>`

All methods have default no-op implementations. Override only what you need.

```java
void onTransitionFired(MachineEvent.TransitionFiredEvent<C> event)
void onStateEntered(MachineEvent.StateEnteredEvent<C> event)
void onStateExited(MachineEvent.StateExitedEvent<C> event)
void onSubStepCompleted(MachineEvent.SubStepCompletedEvent<C> event)
void onSubStepSkipped(MachineEvent.SubStepSkippedEvent<C> event)
void onSubStepFailed(MachineEvent.SubStepFailedEvent<C> event)
void onMachineCompleted(MachineEvent.MachineCompletedEvent<C> event)
void onMachineFailed(MachineEvent.MachineFailedEvent<C> event)
void onMachineResumed(MachineEvent.MachineResumedEvent<C> event)
```

Listeners run **synchronously** on the calling thread. Exceptions are caught per-listener and logged; they do not propagate to the caller. See [Threading & safety — EventBus](04-threading-and-safety.md#eventbus-and-listeners--synchronous-on-the-calling-thread).

---

### `MachineEvent<C>` (sealed base)

Each event type carries `executionId()`, `machineId()`, and `occurredAt()`. Additional fields by subtype:

| Event type | Extra fields |
|---|---|
| `TransitionFiredEvent` | `fromState()`, `toState()`, `event()` |
| `StateEnteredEvent` | `stateName()` |
| `StateExitedEvent` | `stateName()` |
| `SubStepCompletedEvent` | `stateName()`, `subStepName()`, `result()` |
| `SubStepSkippedEvent` | `stateName()`, `subStepName()` |
| `SubStepFailedEvent` | `stateName()`, `subStepName()`, `error()` |
| `MachineCompletedEvent` | `finalState()` |
| `MachineFailedEvent` | `failedStateName()`, `failedSubStepName()`, `error()` |
| `MachineResumedEvent` | `stateName()`, `subStepName()` |

---

## Manager — `hypercell.opensource.stateful.fsm.manager`

### `StateMachineManager<C>`

```java
ManagedTransitionResult<C> trigger(String executionId, String event)
ManagedTransitionResult<C> trigger(String executionId, String event, C contextOverride)
ManagedTransitionResult<C> proceed(String executionId)
ManagedTransitionResult<C> proceed(String executionId, C contextOverride)
Optional<ExecutionSnapshot> snapshotOf(String executionId)   // read-only
void recoverPendingRetries()   // call once on startup
```

Throws:
- `ConcurrentExecutionException` — another request holds the lock; map to HTTP 409
- `CompletedMachineException` — execution already completed; map to HTTP 409 or 422
- `InvalidEventException` — no valid transition for the event; map to HTTP 400
- `ConcurrentRetryException` — a retry is in progress; do not manually proceed

See [Use cases — HTTP manager](03-use-cases.md#b-http-request-driven-workflow) for a complete example.

---

### `ManagedTransitionResult<C>`

```java
String getExecutionId()
String getFromState()
String getToState()
ExecutionStatus getExecutionStatus()     // RUNNING | COMPLETED | FAILED
boolean isProceededFromFailure()         // true if manager auto-proceeded before applying the event
String getFailedSubStepName()           // non-null only when status == FAILED
String getFailedStateName()             // non-null only when status == FAILED

boolean isRunning()
boolean isCompleted()
boolean isFailed()
```

---

## Persistence — `hypercell.opensource.stateful.fsm.resume`

### `SnapshotRepository`

```java
void save(String executionId, ExecutionSnapshot snapshot)
Optional<ExecutionSnapshot> load(String executionId)
void delete(String executionId)
List<ExecutionSnapshot> listPendingRetries()   // returns FAILED + RETRY_SCHEDULED snapshots
```

`listPendingRetries()` is called by `recoverPendingRetries()` on startup. DB-backed implementations should query by status column.

---

### `ExecutionSnapshot`

Immutable. Thread-safe. Do not construct directly — use the factory methods.

```java
// Accessors
String getExecutionId()
String getMachineDefinitionId()
String getCurrentStateName()
String getFailedStateName()          // null if not failed
String getFailedSubStepName()        // null if not failed
int getAttemptNumber()               // starts at 1
String getLastErrorMessage()
String getLastErrorType()            // fully-qualified exception class name
Instant getLastFailedAt()
Instant getScheduledRetryAt()        // null if not scheduled
SnapshotStatus getStatus()
Map<String, ActionResult> getCompletedSubStepResults()   // keyed "stateName::subStepName"

boolean isSubStepCompleted(String stateName, String subStepName)
boolean isRunning()
boolean isFailed()
boolean isCompleted()
```

---

### `SnapshotStatus`

```java
enum SnapshotStatus { FAILED, RETRY_SCHEDULED, RUNNING, COMPLETED }
```

See [Persistence & retry — SnapshotStatus lifecycle](05-persistence-and-retry.md#snapshotstatus-lifecycle) for the transition diagram.

---

### `ResumePolicy<C>`

Controls which sub-steps are skipped when resuming. The default (`DefaultResumePolicy`) skips any sub-step whose name appears in `snapshot.getCompletedSubStepResults()` for the current state.

```java
boolean shouldSkip(String stateName, String subStepName, ExecutionSnapshot snapshot, C context)
```

Custom implementations can add additional skip logic (e.g. always re-run certain idempotent steps for safety).

---

## Retry — `hypercell.opensource.stateful.fsm.retry`

### `RetryPolicy`

```java
boolean shouldRetry(int attemptNumber, Throwable lastError)
Duration backoffFor(int attemptNumber)
int maxAttempts()
```

`attemptNumber` starts at 1 and increments on each failure. `lastError` may be `null` (e.g. when called from `recoverPendingRetries()`).

Built-in implementations: `ExponentialBackoffPolicy`, `FixedDelayPolicy`, `NoAutoRetryPolicy`.

---

### `RetryScheduler`

```java
void schedule(String executionId, Duration delay, Runnable retryAction)
void cancel(String executionId)
default void shutdown()   // graceful; default no-op
```

Built-in implementation: `ThreadPoolRetryScheduler`.

---

## Exceptions — `hypercell.opensource.stateful.fsm.exception`

| Exception | When thrown |
|---|---|
| `StateMachineException` | Base class; `trigger()` / `proceed()` called on wrong status |
| `InvalidEventException` | No valid transition for the event (no guard matched, or event unknown) |
| `InvalidStateException` | State referenced by name does not exist |
| `SubStepExecutionException` | A sub-step threw or returned `ActionResult.failed()`; wraps the original error |
| `StateMachineConfigurationException` | Invalid machine definition at `build()` time |
| `ConcurrentExecutionException` | Manager lock not acquired; another request holds it. Map to HTTP 409. |
| `ConcurrentRetryException` | Manual retry attempted while auto-retry is in progress |
| `CompletedMachineException` | Event triggered on an already-`COMPLETED` execution |
| `RetryException` | Internal retry infrastructure error |
| `SnapshotException` | Snapshot repository I/O failure |
