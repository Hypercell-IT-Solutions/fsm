# Use cases

Practical recipes for common integration patterns. Each is self-contained.

---

## A. Synchronous in-process workflow

The simplest pattern: define once, run as many instances as you need in the same process. Best for batch jobs, unit tests, command-line tools, and single-threaded pipelines.

```java
StateMachineDefinition<OrderContext> machine = StateMachine.<OrderContext>define("order-workflow")
    .initial("PENDING")
    .state("PENDING")
        .on("APPROVE").to("PROCESSING").end()
        .and()
    .state("PROCESSING")
        .subStep("reserve-stock",  ctx -> reserveStock(ctx))
        .subStep("charge-payment", ctx -> chargePayment(ctx))
        .on("COMPLETE").to("SHIPPED").end()
        .and()
    .state("SHIPPED").terminal().and()
    .build();

// Each order gets its own instance
StateMachineInstance<OrderContext> instance = machine.newInstance(new OrderContext("order-1"));
instance.trigger("APPROVE");
instance.trigger("COMPLETE");

System.out.println(instance.status()); // COMPLETED
```

**One instance = one execution.** Instances are not thread-safe; do not share them across threads. See [Threading & safety](04-threading-and-safety.md).

---

## B. HTTP request-driven workflow

When events arrive across multiple HTTP requests (and potentially to different process replicas), use `StateMachineManager<C>`. It handles the full load → execute → save cycle and prevents concurrent access to the same execution.

```java
// Define once (e.g. as a Spring @Bean)
StateMachineDefinition<OrderContext> definition = StateMachine.<OrderContext>define("order-workflow")
    .initial("PENDING")
    .snapshotRepository(StateMachine.fileRepository(Path.of("/var/fsm-snapshots")))
    .contextLoader(orderId -> orderRepository.findById(orderId)) // how to reload context
    .state("PENDING")
        .on("APPROVE").to("PROCESSING").end()
        .and()
    .state("PROCESSING")
        .subStep("reserve-stock",  ctx -> reserveStock(ctx))
        .subStep("charge-payment", ctx -> chargePayment(ctx))
        .on("COMPLETE").to("SHIPPED").end()
        .and()
    .state("SHIPPED").terminal().and()
    .build();

// Create a manager (also a @Bean in Spring)
StateMachineManager<OrderContext> manager = StateMachine.manager(definition,
    orderId -> orderRepository.findById(orderId));

// --- In your HTTP endpoint ---
// POST /orders/{orderId}/events  body: {"event": "APPROVE"}
ManagedTransitionResult<OrderContext> result = manager.trigger(orderId, "APPROVE");

switch (result.getExecutionStatus()) {
    case RUNNING    -> respond(200, result.getToState());
    case COMPLETED  -> respond(200, "workflow-complete");
    case FAILED     -> respond(500, "failed-at: " + result.getFailedSubStepName());
}
```

### Handling concurrent requests (HTTP 409)

If two requests for the same `executionId` arrive simultaneously, the second throws `ConcurrentExecutionException`. Map it to HTTP 409:

```java
try {
    ManagedTransitionResult<OrderContext> result = manager.trigger(orderId, event);
    return ResponseEntity.ok(result);
} catch (ConcurrentExecutionException e) {
    return ResponseEntity.status(409).body("Another request is already processing this order");
} catch (CompletedMachineException e) {
    return ResponseEntity.status(409).body("Order workflow already completed");
} catch (InvalidEventException e) {
    return ResponseEntity.status(400).body("Invalid event: " + e.getMessage());
}
```

### Startup recovery

Call `recoverPendingRetries()` once on application startup to reschedule any retries that were in-flight when the process was last stopped:

```java
@PostConstruct
void init() {
    manager.recoverPendingRetries();
}
```

---

## C. Automatic retry

Configure a retry policy to automatically re-run failed sub-steps after a backoff delay. Requires `snapshotRepository` and `contextLoader`.

```java
StateMachineDefinition<OrderContext> definition = StateMachine.<OrderContext>define("order-workflow")
    .initial("PENDING")
    .snapshotRepository(StateMachine.fileRepository(Path.of("/var/fsm-snapshots")))
    .retryPolicy(StateMachine.exponentialBackoff(5,
        Duration.ofSeconds(2),   // first retry after 2s
        Duration.ofMinutes(10))) // cap at 10 minutes
    .retryScheduler(StateMachine.threadPoolScheduler(2))
    .contextLoader(orderId -> orderRepository.findById(orderId))
    .state("PENDING")
        .on("APPROVE").to("PROCESSING").end()
        .and()
    .state("PROCESSING")
        .subStep("reserve-stock",  ctx -> reserveStock(ctx))
        .subStep("charge-payment", ctx -> chargePayment(ctx))  // this can fail
        .subStep("send-email",     ctx -> sendEmail(ctx))
        .on("COMPLETE").to("SHIPPED").end()
        .and()
    .state("SHIPPED").terminal().and()
    .build();
```

**Retry delays for `exponentialBackoff(5, 2s, 10min)`:**

| Attempt | Delay |
|---|---|
| 1 | immediate |
| 2 | 2 s |
| 3 | 4 s |
| 4 | 8 s |
| 5 | 10 min (capped) |

After 5 attempts the snapshot stays `FAILED` — you can still call `manager.proceed()` manually.

Fixed delay:
```java
.retryPolicy(StateMachine.fixedDelay(3, Duration.ofSeconds(30)))
```

No auto-retry (snapshot saved, manual retry only):
```java
.retryPolicy(StateMachine.noAutoRetry())
```

---

## D. Manual retry / human-in-the-loop

Use `noAutoRetry()` when a business owner should decide whether to retry (e.g. fraud holds, manual approvals):

```java
StateMachineDefinition<OrderContext> definition = StateMachine.<OrderContext>define("order-workflow")
    .initial("PENDING")
    .snapshotRepository(StateMachine.fileRepository(Path.of("/var/fsm-snapshots")))
    .retryPolicy(StateMachine.noAutoRetry())
    // no contextLoader needed when using manager.proceed(executionId, contextOverride)
    .state("PENDING")
        .on("APPROVE").to("PROCESSING").end()
        .and()
    .state("PROCESSING")
        .subStep("charge-payment", ctx -> chargePayment(ctx))
        .on("COMPLETE").to("SHIPPED").end()
        .and()
    .state("SHIPPED").terminal().and()
    .build();

StateMachineManager<OrderContext> manager = definition.newManager(
    StateMachine.fileRepository(Path.of("/var/fsm-snapshots")),
    orderId -> orderRepository.findById(orderId));

// --- After a support agent approves a retry ---
// POST /orders/{orderId}/retry
ManagedTransitionResult<OrderContext> result = manager.proceed(orderId);

// Or if you have the context in the HTTP request body:
ManagedTransitionResult<OrderContext> result2 = manager.proceed(orderId, requestBodyContext);
```

---

## E. Startup recovery

After a process restart, retries that were scheduled or in-flight may be lost. Call `recoverPendingRetries()` on startup to restore them:

```java
@Component
public class StateMachineConfig {

    @Bean
    public StateMachineManager<OrderContext> orderManager(OrderRepository repo) {
        StateMachineDefinition<OrderContext> def = StateMachine.<OrderContext>define("order-workflow")
            .initial("PENDING")
            .snapshotRepository(StateMachine.fileRepository(Path.of("/var/fsm-snapshots")))
            .retryPolicy(StateMachine.exponentialBackoff(5, Duration.ofSeconds(2), Duration.ofMinutes(10)))
            .retryScheduler(StateMachine.threadPoolScheduler(2))
            .contextLoader(repo::findById)
            // ... states ...
            .build();

        StateMachineManager<OrderContext> manager = StateMachine.manager(def, repo::findById);
        manager.recoverPendingRetries(); // re-schedule any pending retries from last run
        return manager;
    }
}
```

`recoverPendingRetries()` uses the remaining delay for `RETRY_SCHEDULED` snapshots (fires immediately if the scheduled time has already passed). It skips `FAILED` snapshots that were intentionally left for manual retry.

---

## F. Spring DI — class-based states and sub-steps

For complex states with many injected dependencies, implement `StateConfigurer<C>` and `SubStepHandler<C>` as Spring beans instead of inline lambdas.

### Sub-step as a class

```java
@Component
public class ChargePaymentStep implements SubStepHandler<OrderContext> {

    private final PaymentService paymentService;

    public ChargePaymentStep(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public String name() {
        return "charge-payment"; // stable snapshot key — don't rename this
    }

    @Override
    public ActionResult execute(OrderContext ctx) throws Exception {
        boolean ok = paymentService.charge(ctx.getOrderId(), ctx.getAmount());
        return ok ? ActionResult.success() : ActionResult.failed("Payment declined");
    }
}
```

### State as a class

```java
@Component
public class ProcessingStateConfigurer implements StateConfigurer<OrderContext> {

    private final ReserveStockStep reserveStock;
    private final ChargePaymentStep chargePayment;
    private final SendEmailStep sendEmail;

    public ProcessingStateConfigurer(ReserveStockStep r, ChargePaymentStep c, SendEmailStep s) {
        reserveStock = r; chargePayment = c; sendEmail = s;
    }

    @Override
    public String stateName() { return "PROCESSING"; }

    @Override
    public void configure(StateBuilder<OrderContext> state) {
        state
            .subStep(reserveStock)
            .subStep(chargePayment)
            .subStep(sendEmail)
            .on("COMPLETE").to("SHIPPED").end();
    }
}
```

### Wire them together

```java
@Configuration
public class OrderMachineConfig {

    @Bean
    public StateMachineDefinition<OrderContext> orderMachine(
            ProcessingStateConfigurer processing,
            List<MachineEventListener<OrderContext>> listeners,
            SnapshotRepository repository) {

        StateMachineBuilder<OrderContext> builder = StateMachine.<OrderContext>define("order-workflow")
            .initial("PENDING")
            .snapshotRepository(repository);

        listeners.forEach(builder::listener);

        return builder
            .state("PENDING")
                .on("APPROVE").to("PROCESSING").end()
                .and()
            .state(processing)
            .state("SHIPPED").terminal().and()
            .build();
    }
}
```

---

## G. Guards — multiple transitions for one event

Use guards when the same event leads to different states depending on context:

```java
StateMachineDefinition<OrderContext> machine = StateMachine.<OrderContext>define("order-workflow")
    .initial("PAYMENT_RECEIVED")
    .state("PAYMENT_RECEIVED")
        // Same event "PROCESS", two targets selected by guard
        .on("PROCESS")
            .when(ctx -> ctx.requiresManualReview())
            .to("MANUAL_REVIEW")
            .end()
        .on("PROCESS")
            .when(ctx -> !ctx.requiresManualReview())
            .to("PROCESSING")
            .end()
        .and()
    .state("MANUAL_REVIEW")
        .on("APPROVE").to("PROCESSING").end()
        .on("REJECT").to("CANCELLED").end()
        .and()
    .state("PROCESSING").terminal().and()
    .state("CANCELLED").terminal().and()
    .build();
```

Guards are evaluated in **definition order**. The first guard returning `true` fires. If no guard matches, `InvalidEventException` is thrown.

Guards must be **pure** (read-only, side-effect-free). See [Concepts — Guards](01-concepts.md#guards).

---

## H. Transition actions

Use a transition action for work that belongs to the movement between states (auditing, logging a state change to a DB):

```java
.state("PENDING")
    .on("APPROVE")
        .action(ctx -> {
            auditLog.record(ctx.getOrderId(), "PENDING → PROCESSING");
            return ActionResult.success();
        })
        .to("PROCESSING")
        .end()
    .and()
```

The action runs **after** `sourceState.onExit()` and **before** `targetState.onEntry()`. If the transition action fails (throws or returns `ActionResult.failed`), the machine enters `FAILED` with the machine still logically in the source state.

---

## I. Entry and exit hooks

Use hooks for lightweight state setup and teardown (timestamps, context initialization):

```java
.state("PROCESSING")
    .onEntry(ctx -> ctx.setProcessingStartedAt(Instant.now()))
    .onExit(ctx  -> ctx.setProcessingEndedAt(Instant.now()))
    .subStep("charge-payment", ctx -> chargePayment(ctx))
    .on("COMPLETE").to("SHIPPED").end()
    .and()
```

**Hooks are not retry-tracked.** They always run when entering/exiting a state, even on resume. Keep them idempotent or side-effect-free. For anything non-idempotent (API calls, DB writes), use a sub-step instead.
