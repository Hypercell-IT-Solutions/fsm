package hypercell.opensource.stateful.fsm.examples;

import hypercell.opensource.stateful.fsm.StateMachine;
import hypercell.opensource.stateful.fsm.core.ActionResult;
import hypercell.opensource.stateful.fsm.core.StateMachineDefinition;
import hypercell.opensource.stateful.fsm.exception.ConcurrentExecutionException;
import hypercell.opensource.stateful.fsm.exception.InvalidEventException;
import hypercell.opensource.stateful.fsm.manager.ManagedTransitionResult;
import hypercell.opensource.stateful.fsm.manager.StateMachineManager;
import hypercell.opensource.stateful.fsm.resume.InMemorySnapshotRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * Demonstrates the {@link StateMachineManager} pattern for HTTP-driven workflows.
 *
 * <p>Run with: {@code mvn -pl fsm-examples compile exec:java -Dexec.mainClass=hypercell.opensource.stateful.fsm.examples.HttpManagerExample}
 *
 * <p>What this example shows:
 * <ul>
 *   <li>Manager as a long-lived singleton that handles the full load→execute→save cycle</li>
 *   <li>Each simulated "HTTP request" calls {@code manager.trigger(executionId, event)}</li>
 *   <li>{@code ConcurrentExecutionException} when two requests race for the same execution
 *       (map to HTTP 409)</li>
 *   <li>{@code InvalidEventException} for an event with no valid transition (HTTP 400)</li>
 *   <li>{@code CompletedMachineException} when triggering on a completed execution (HTTP 409)</li>
 *   <li>{@code recoverPendingRetries()} called once at startup</li>
 * </ul>
 *
 * <p>See also: <a href="../../../../../../../../../../docs/03-use-cases.md#b-http-request-driven-workflow">Use cases — HTTP manager</a>
 */
public class HttpManagerExample {

    // -------------------------------------------------------------------------
    // Domain context
    // -------------------------------------------------------------------------

    static class OrderContext {
        final String orderId;
        String reservationId;
        boolean paymentCharged;

        OrderContext(String orderId) {
            this.orderId = orderId;
        }
    }

    // -------------------------------------------------------------------------
    // Simulated "database" — context loaded by executionId
    // In a real app this would be orderRepository.findById(id)
    // -------------------------------------------------------------------------

    static final Map<String, OrderContext> DB = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Machine definition and manager (singleton in a real app, e.g. Spring @Bean)
    // -------------------------------------------------------------------------

    static final InMemorySnapshotRepository REPOSITORY = InMemorySnapshotRepository.create();

    static final StateMachineDefinition<OrderContext> DEFINITION =
            StateMachine.<OrderContext>define("order-workflow")
                    .initial("PENDING")
                    .listener(StateMachine.loggingListener("[ORDER]"))
                    .snapshotRepository(REPOSITORY)

                    .state("PENDING")
                        .on("APPROVE").to("PROCESSING").end()
                        .on("CANCEL").to("CANCELLED").end()
                        .and()

                    .state("PROCESSING")
                        .subStep("reserve-stock", ctx -> {
                            ctx.reservationId = "RSV-" + ctx.orderId;
                            return ActionResult.success();
                        })
                        .subStep("charge-payment", ctx -> {
                            ctx.paymentCharged = true;
                            return ActionResult.success();
                        })
                        .on("COMPLETE").to("SHIPPED").end()
                        .and()

                    .state("SHIPPED").terminal().and()
                    .state("CANCELLED").terminal().and()
                    .build();

    // contextLoader: how to reload a fresh OrderContext given only the executionId
    static final StateMachineManager<OrderContext> MANAGER =
            StateMachine.manager(DEFINITION, REPOSITORY, id -> DB.get(id));

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    static void simulateRequest(String label, Runnable request) {
        System.out.printf("%n=== %s ===%n", label);
        request.run();
    }

    static void logResult(ManagedTransitionResult<OrderContext> r) {
        System.out.printf("  %s → %s  [%s]%s%n",
                r.getFromState(), r.getToState(), r.getExecutionStatus(),
                r.isProceededFromFailure() ? "  (auto-proceeded from prior failure)" : "");
    }

    // -------------------------------------------------------------------------
    // main
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws InterruptedException {
        // Seed the "database" — in a real app the context exists before the first event
        String orderId = "order-42";
        DB.put(orderId, new OrderContext(orderId));

        // --- Startup recovery (call once at application boot) ---
        // Re-schedules any retries that were in-flight when the process last stopped.
        // Safe to call even when nothing is pending.
        System.out.println("=== Startup: recoverPendingRetries() ===");
        MANAGER.recoverPendingRetries();
        System.out.println("  (nothing pending on first start)");

        // --- Request 1: first event — creates a new execution ---
        simulateRequest("Request 1: APPROVE (first event, creates new execution)", () -> {
            ManagedTransitionResult<OrderContext> r = MANAGER.trigger(orderId, "APPROVE");
            logResult(r);
        });

        // --- Request 2: invalid event from current state ---
        simulateRequest("Request 2: APPROVE again (no valid transition from PROCESSING)", () -> {
            try {
                MANAGER.trigger(orderId, "APPROVE");
            } catch (InvalidEventException e) {
                System.out.println("  HTTP 400 — " + e.getMessage());
            }
        });

        // --- Request 3: concurrent request (two threads, same executionId) ---
        simulateRequest("Request 3: two concurrent requests for the same execution", () -> {
            CountDownLatch firstHoldsLock = new CountDownLatch(1);
            CountDownLatch allowFirstToFinish = new CountDownLatch(1);

            // First thread: slow sub-step that holds the lock
            StateMachineDefinition<OrderContext> slowDef =
                    StateMachine.<OrderContext>define("slow-workflow")
                            .initial("START")
                            .snapshotRepository(InMemorySnapshotRepository.create())
                            .state("START")
                                .subStep("slow-step", ctx -> {
                                    firstHoldsLock.countDown();       // signal: lock is held
                                    try { allowFirstToFinish.await(); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                                    return ActionResult.success();
                                })
                                .on("GO").to("DONE").end()
                                .and()
                            .state("DONE").terminal().and()
                            .build();

            String concurrentId = "order-99";
            DB.put(concurrentId, new OrderContext(concurrentId));
            StateMachineManager<OrderContext> slowManager =
                    StateMachine.manager(slowDef, InMemorySnapshotRepository.create(), id -> DB.get(id));

            Thread t1 = new Thread(() -> slowManager.trigger(concurrentId, "GO"));
            Thread t2 = new Thread(() -> {
                try {
                    firstHoldsLock.await();  // wait until t1 holds the lock
                    slowManager.trigger(concurrentId, "GO");
                } catch (ConcurrentExecutionException e) {
                    System.out.println("  HTTP 409 (thread 2) — " + e.getMessage());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            });

            t1.start();
            t2.start();
            try {
                firstHoldsLock.await();
                allowFirstToFinish.countDown();
                t1.join();
                t2.join();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        });

        // --- Request 4: complete the order ---
        simulateRequest("Request 4: COMPLETE", () -> {
            ManagedTransitionResult<OrderContext> r = MANAGER.trigger(orderId, "COMPLETE");
            logResult(r);
        });

        // --- Request 5: trigger on an already-completed execution ---
        //
        // NOTE ON CURRENT BEHAVIOR:
        // The library deletes the snapshot when an execution completes. After deletion, the
        // repository has no entry for this executionId, so the manager treats it as a brand-new
        // execution and starts from the initial state (PENDING). Triggering "COMPLETE" from PENDING
        // has no valid transition → InvalidEventException.
        //
        // CompletedMachineException is only thrown by manager.proceed() when a snapshot
        // with status COMPLETED is found in the repository — which the built-in repositories
        // currently never store (they delete on completion). This is a known limitation.
        //
        // Workaround: check snapshotOf() before triggering to detect a missing/absent snapshot
        // as a "completed" signal; or use a custom repository that keeps COMPLETED snapshots.
        simulateRequest("Request 5: trigger on completed execution (snapshot was deleted)", () -> {
            boolean snapshotGone = MANAGER.snapshotOf(orderId).isEmpty();
            System.out.println("  Snapshot present: " + !snapshotGone
                    + " (deleted when machine completed)");

            // Guard against re-triggering completed executions using snapshotOf()
            if (snapshotGone) {
                System.out.println("  HTTP 409 — execution already completed (no snapshot found)");
                return;
            }
            // If a snapshot were present with COMPLETED status, the manager would throw:
            // CompletedMachineException — but current built-in repos delete rather than
            // retain completed snapshots.
        });
    }
}
