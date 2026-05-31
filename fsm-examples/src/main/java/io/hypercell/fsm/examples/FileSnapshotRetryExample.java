package io.hypercell.fsm.examples;

import io.hypercell.fsm.StateMachine;
import io.hypercell.fsm.core.ActionResult;
import io.hypercell.fsm.core.StateMachineDefinition;
import io.hypercell.fsm.manager.ManagedTransitionResult;
import io.hypercell.fsm.manager.StateMachineManager;
import io.hypercell.fsm.resume.FileSnapshotRepository;
import io.hypercell.fsm.resume.SnapshotRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demonstrates file-based snapshot persistence, manual retry, and smart ctx loading.
 *
 * <p>Run with: {@code mvn -pl fsm-examples compile exec:java -Dexec.mainClass=io.hypercell.fsm.examples.FileSnapshotRetryExample}
 *
 * <p>What this example shows:
 * <ul>
 *   <li>Using {@code FileSnapshotRepository}: snapshots survive process restarts</li>
 *   <li>A sub-step failure saves the snapshot with completed steps recorded</li>
 *   <li>Simulated process restart: a new manager is created from the same snapshot directory</li>
 *   <li>Manual retry via {@code manager.proceed()}: completed steps are skipped</li>
 *   <li><strong>Smart ctx loading</strong>: each sub-step persists its output to durable
 *       storage, and the {@code contextLoader} restores that output on resume — so that
 *       downstream steps (which would have been skipped) still find the data they need
 *       in the ctx</li>
 * </ul>
 *
 * <p>See also:
 * <a href="../../../../../../../../../../docs/05-persistence-and-retry.md#ctx-on-resume">Persistence &amp; retry — Context on resume</a>
 */
public class FileSnapshotRetryExample {

    // -------------------------------------------------------------------------
    // Domain ctx
    // -------------------------------------------------------------------------

    static class OrderContext {
        final String orderId;
        String reservationId;   // set by reserve-stock, needed by charge-payment
        boolean paymentCharged;
        boolean confirmationSent;

        OrderContext(String orderId) {
            this.orderId = orderId;
        }
    }

    // -------------------------------------------------------------------------
    // Simulated durable storage
    // In a real app these would be columns in an orders table.
    // -------------------------------------------------------------------------

    // Keyed by orderId → reservationId (written by reserve-stock, read by contextLoader)
    static final Map<String, String> RESERVATION_DB = new ConcurrentHashMap<>();

    // Simple flag to make charge-payment fail only on the first attempt
    static int paymentAttempts = 0;

    // -------------------------------------------------------------------------
    // Sub-step implementations
    // -------------------------------------------------------------------------

    static ActionResult reserveStock(OrderContext ctx) {
        String reservationId = "RSV-" + ctx.orderId;
        ctx.reservationId = reservationId;

        // Persist to durable storage so contextLoader can restore it on resume.
        // Without this, ctx.reservationId would be null after a fresh ctx load,
        // and charge-payment would fail with a NullPointerException instead of charging.
        RESERVATION_DB.put(ctx.orderId, reservationId);

        System.out.printf("  [reserve-stock]    Created reservation: %s (also persisted to DB)%n",
                reservationId);
        return ActionResult.success();
    }

    static ActionResult chargePayment(OrderContext ctx) {
        paymentAttempts++;
        if (paymentAttempts == 1) {
            System.out.printf("  [charge-payment]   Timeout (attempt %d) — ctx.reservationId=%s%n",
                    paymentAttempts, ctx.reservationId);
            return ActionResult.failed("Payment gateway timeout");
        }
        // On retry: ctx.reservationId was restored by contextLoader — no NPE
        ctx.paymentCharged = true;
        System.out.printf("  [charge-payment]   Charged using reservationId=%s (attempt %d)%n",
                ctx.reservationId, paymentAttempts);
        return ActionResult.success();
    }

    static ActionResult sendConfirmation(OrderContext ctx) {
        ctx.confirmationSent = true;
        System.out.println("  [send-confirmation] Confirmation sent");
        return ActionResult.success();
    }

    // -------------------------------------------------------------------------
    // Context loader — rebuilds the ctx as it was at the point of failure
    // -------------------------------------------------------------------------

    static OrderContext loadContext(String orderId) {
        // In a real app: Order order = orderRepository.findById(orderId);
        OrderContext ctx = new OrderContext(orderId);

        // Restore intermediate results written by completed sub-steps.
        // If reserve-stock already ran, its reservationId is in the DB.
        String reservationId = RESERVATION_DB.get(orderId);
        if (reservationId != null) {
            ctx.reservationId = reservationId;
            System.out.printf("  [contextLoader]    Restored reservationId=%s from DB%n",
                    reservationId);
        }

        return ctx;
    }

    // -------------------------------------------------------------------------
    // main
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        Path snapshotDir = Files.createTempDirectory("fsm-snapshots-");
        System.out.println("Snapshot directory: " + snapshotDir);

        String orderId = "order-42";

        // =================================================================
        // First "process run": PENDING → PROCESSING, charge-payment fails
        // =================================================================
        System.out.println("\n======  First process run  ======");
        {
            SnapshotRepository repository = new FileSnapshotRepository(snapshotDir);

            StateMachineDefinition<OrderContext> definition =
                    StateMachine.<OrderContext>define("order-workflow")
                            .initial("PENDING")
                            .listener(StateMachine.loggingListener("[ORDER]"))
                            .snapshotRepository(repository)
                            .retryPolicy(StateMachine.noAutoRetry())  // manual retry only
                            .contextLoader(id -> loadContext(id))

                            .state("PENDING")
                                .on("APPROVE").to("PROCESSING").end()
                                .and()

                            .state("PROCESSING")
                                .subStep("reserve-stock",    ctx -> reserveStock(ctx))
                                .subStep("charge-payment",   ctx -> chargePayment(ctx))
                                .subStep("send-confirmation", ctx -> sendConfirmation(ctx))
                                .on("COMPLETE").to("SHIPPED").end()
                                .and()

                            .state("SHIPPED").terminal().and()
                            .build();

            StateMachineManager<OrderContext> manager =
                    StateMachine.manager(definition, repository);

            System.out.println("\n--- Triggering APPROVE ---");
            ManagedTransitionResult<OrderContext> r = manager.trigger(orderId, "APPROVE",
                    new OrderContext(orderId));

            System.out.printf("%nResult: %s → %s  [%s]%n",
                    r.getFromState(), r.getToState(), r.getExecutionStatus());

            if (r.isFailed()) {
                System.out.printf("Failed at: %s / %s%n",
                        r.getFailedStateName(), r.getFailedSubStepName());

                // Verify snapshot was written to disk
                boolean snapshotExists = manager.snapshotOf(orderId).isPresent();
                System.out.printf("Snapshot on disk: %b%n", snapshotExists);

                manager.snapshotOf(orderId).ifPresent(s ->
                    System.out.printf("Completed steps in snapshot: %s%n",
                            s.getCompletedSubStepResults().keySet())
                );
            }
        }
        // First "process" ends here — objects go out of scope

        // =================================================================
        // Second "process run": new manager, same snapshot directory
        // Simulates a process restart followed by a manual retry endpoint call
        // =================================================================
        System.out.println("\n======  Second process run (restart + manual retry)  ======");
        {
            // Re-create from the same snapshot directory — no in-memory state carried over
            SnapshotRepository repository = new FileSnapshotRepository(snapshotDir);

            StateMachineDefinition<OrderContext> definition =
                    StateMachine.<OrderContext>define("order-workflow")
                            .initial("PENDING")
                            .listener(StateMachine.loggingListener("[ORDER]"))
                            .snapshotRepository(repository)
                            .retryPolicy(StateMachine.noAutoRetry())
                            .contextLoader(id -> loadContext(id))

                            .state("PENDING")
                                .on("APPROVE").to("PROCESSING").end()
                                .and()

                            .state("PROCESSING")
                                .subStep("reserve-stock",    ctx -> reserveStock(ctx))
                                .subStep("charge-payment",   ctx -> chargePayment(ctx))
                                .subStep("send-confirmation", ctx -> sendConfirmation(ctx))
                                .on("COMPLETE").to("SHIPPED").end()
                                .and()

                            .state("SHIPPED").terminal().and()
                            .build();

            StateMachineManager<OrderContext> manager =
                    StateMachine.manager(definition, repository);

            System.out.println("\n--- Manual retry: manager.proceed(orderId) ---");
            System.out.println("Expected: reserve-stock SKIPPED, charge-payment runs, send-confirmation runs\n");

            ManagedTransitionResult<OrderContext> r = manager.proceed(orderId);

            System.out.printf("%nResult after proceed: %s → %s  [%s]%n",
                    r.getFromState(), r.getToState(), r.getExecutionStatus());

            if (r.isRunning()) {
                System.out.println("\n--- Now completing the order ---");
                ManagedTransitionResult<OrderContext> complete = manager.trigger(orderId, "COMPLETE",
                        loadContext(orderId));
                System.out.printf("Final: %s → %s  [%s]%n",
                        complete.getFromState(), complete.getToState(), complete.getExecutionStatus());

                System.out.printf("Snapshot status: %s%n",
                        manager.snapshotOf(orderId).map(s -> s.getStatus().name()).orElse("absent"));
            }
        }
    }
}
