package hypercell.opensource.stateful.fsm.listener;

/**
 * Listener interface for state machine lifecycle events.
 * <p>
 * DESIGN: TWO WAYS TO LISTEN
 * <p>
 * Option 1 — handle all events in one method (good for generic logging):
 * machine.addListener(event -> log(event));
 * <p>
 * Option 2 — override only the events you care about (good for specific reactions):
 * machine.addListener(new MachineEventListener<>() {
 *
 * @param <C> the context type of the machine being observed
 * @Override public void onSubStepFailed(MachineEvent.SubStepFailedEvent<C> e) {
 * alertOps("Payment step failed: " + e.getErrorMessage());
 * }
 * @Override public void onMachineCompleted(MachineEvent.MachineCompletedEvent<C> e) {
 * metrics.increment("orders.completed");
 * }
 * });
 * <p>
 * THREADING:
 * Listeners are called synchronously on the same thread as the machine execution.
 * Keep listeners fast — don't block inside them. If you need to do heavy work
 * (send a webhook, write to a slow DB), publish to a queue and process async.
 * <p>
 * EXCEPTION HANDLING:
 * Exceptions thrown by a listener are caught and logged but do NOT propagate —
 * a bad listener should not kill the machine execution.
 */
public interface MachineEventListener<C> {

    /**
     * Called for every event. Override this for generic handling.
     * The default implementation delegates to the specific typed methods below.
     */
    default void onEvent(MachineEvent<C> event) {
        if (event instanceof MachineEvent.TransitionFiredEvent<C> e) onTransitionFired(e);
        else if (event instanceof MachineEvent.StateEnteredEvent<C> e) onStateEntered(e);
        else if (event instanceof MachineEvent.StateExitedEvent<C> e) onStateExited(e);
        else if (event instanceof MachineEvent.SubStepCompletedEvent<C> e) onSubStepCompleted(e);
        else if (event instanceof MachineEvent.SubStepSkippedEvent<C> e) onSubStepSkipped(e);
        else if (event instanceof MachineEvent.SubStepFailedEvent<C> e) onSubStepFailed(e);
        else if (event instanceof MachineEvent.MachineCompletedEvent<C> e) onMachineCompleted(e);
        else if (event instanceof MachineEvent.MachineFailedEvent<C> e) onMachineFailed(e);
        else if (event instanceof MachineEvent.MachineResumedEvent<C> e) onMachineResumed(e);
    }

    default void onTransitionFired(MachineEvent.TransitionFiredEvent<C> event) {
    }

    default void onStateEntered(MachineEvent.StateEnteredEvent<C> event) {
    }

    default void onStateExited(MachineEvent.StateExitedEvent<C> event) {
    }

    default void onSubStepCompleted(MachineEvent.SubStepCompletedEvent<C> event) {
    }

    default void onSubStepSkipped(MachineEvent.SubStepSkippedEvent<C> event) {
    }

    default void onSubStepFailed(MachineEvent.SubStepFailedEvent<C> event) {
    }

    default void onMachineCompleted(MachineEvent.MachineCompletedEvent<C> event) {
    }

    default void onMachineFailed(MachineEvent.MachineFailedEvent<C> event) {
    }

    default void onMachineResumed(MachineEvent.MachineResumedEvent<C> event) {
    }
}
