package hypercell.opensource.stateful.fsm.examples;

import hypercell.opensource.stateful.fsm.StateMachine;
import hypercell.opensource.stateful.fsm.core.ActionResult;
import hypercell.opensource.stateful.fsm.core.StateMachineDefinition;
import hypercell.opensource.stateful.fsm.core.StateMachineInstance;
import hypercell.opensource.stateful.fsm.exception.SubStepExecutionException;

/**
 * Demonstrates a synchronous in-process order workflow.
 *
 * <p>Run with: {@code mvn -pl fsm-examples compile exec:java -Dexec.mainClass=hypercell.opensource.stateful.fsm.examples.SynchronousWorkflowExample}
 *
 * <p>What this example shows:
 * <ul>
 *   <li>Defining a state machine with sub-steps using the fluent DSL</li>
 *   <li>Creating a per-execution instance and triggering events</li>
 *   <li>How failure mid-state leaves the machine in FAILED status</li>
 *   <li>How {@code proceed()} resumes from the failed sub-step, skipping completed ones</li>
 * </ul>
 *
 * <p>See also: <a href="../../../../../../../../../../docs/02-getting-started.md">Getting started</a>
 */
public class SynchronousWorkflowExample {

    // -------------------------------------------------------------------------
    // Domain ctx
    // -------------------------------------------------------------------------

    static class OrderContext {
        final String orderId;
        String reservationId;
        boolean paymentCharged;
        boolean confirmationSent;

        OrderContext(String orderId) {
            this.orderId = orderId;
        }
    }

    // -------------------------------------------------------------------------
    // Simulated service calls
    // sub-step "charge-payment" fails on the first attempt to demonstrate recovery
    // -------------------------------------------------------------------------

    static int paymentAttempts = 0;

    static ActionResult reserveStock(OrderContext ctx) {
        ctx.reservationId = "RSV-" + ctx.orderId;
        System.out.printf("  [reserve-stock]    Reservation created: %s%n", ctx.reservationId);
        return ActionResult.success();
    }

    static ActionResult chargePayment(OrderContext ctx) {
        paymentAttempts++;
        if (paymentAttempts == 1) {
            System.out.printf("  [charge-payment]   Gateway timeout (attempt %d) — failing%n",
                    paymentAttempts);
            return ActionResult.failed("Payment gateway timeout");
        }
        ctx.paymentCharged = true;
        System.out.printf("  [charge-payment]   Payment processed (attempt %d)%n", paymentAttempts);
        return ActionResult.success();
    }

    static ActionResult sendConfirmation(OrderContext ctx) {
        ctx.confirmationSent = true;
        System.out.println("  [send-confirmation] Confirmation email sent");
        return ActionResult.success();
    }

    // -------------------------------------------------------------------------
    // Machine definition (one instance shared across all order executions)
    // -------------------------------------------------------------------------

    static StateMachineDefinition<OrderContext> buildMachine() {
        return StateMachine.<OrderContext>define("order-workflow")
                .initial("PENDING")
                .listener(StateMachine.loggingListener("[ORDER]"))
                .snapshotRepository(StateMachine.inMemoryRepository())

                .state("PENDING")
                    .on("APPROVE").to("PROCESSING").end()
                    .on("CANCEL").to("CANCELLED").end()
                    .and()

                .state("PROCESSING")
                    .subStep("reserve-stock",    ctx -> reserveStock(ctx))
                    .subStep("charge-payment",   ctx -> chargePayment(ctx))
                    .subStep("send-confirmation", ctx -> sendConfirmation(ctx))
                    .on("COMPLETE").to("SHIPPED").end()
                    .and()

                .state("SHIPPED").terminal().and()
                .state("CANCELLED").terminal().and()
                .build();
    }

    // -------------------------------------------------------------------------
    // main
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        StateMachineDefinition<OrderContext> machine = buildMachine();
        OrderContext ctx = new OrderContext("order-42");

        // Supply an explicit execution ID so the snapshot key matches the order ID
        StateMachineInstance<OrderContext> instance = machine.newInstance(ctx, "order-42");

        System.out.println("=== Approving order (PENDING → PROCESSING) ===");
        System.out.println("Expected: reserve-stock succeeds, charge-payment fails\n");

        try {
            instance.trigger("APPROVE");
        } catch (SubStepExecutionException e) {
            System.out.printf("%nSub-step failed: %s%n", e.getMessage());
            System.out.printf("Instance status:  %s%n", instance.status());
            System.out.printf("Current state:    %s%n", instance.currentState().name());
            System.out.printf("reserve-stock done: %b (reservationId=%s)%n",
                    ctx.reservationId != null, ctx.reservationId);
        }

        System.out.println("\n=== Retrying (proceed) — reserve-stock will be skipped ===\n");

        // proceed() re-runs from the failed sub-step, skipping those already completed
        instance.proceed();

        System.out.printf("%nInstance status after proceed: %s%n", instance.status());

        System.out.println("\n=== Completing the order (PROCESSING → SHIPPED) ===\n");

        instance.trigger("COMPLETE");

        System.out.printf("%nFinal status:  %s%n", instance.status());
        System.out.printf("Final state:   %s%n", instance.currentState().name());
        System.out.printf("Payment charged:       %b%n", ctx.paymentCharged);
        System.out.printf("Confirmation sent:     %b%n", ctx.confirmationSent);
    }
}
