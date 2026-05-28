package hypercell.opensource.stateful.fsm.exception;

/**
 * Thrown when trigger() is called with an event that has no valid transition
 * from the current state — either because no transition is defined for that event,
 * or because all matching transitions have guards that evaluate to false.
 */
public class InvalidEventException extends StateMachineException {

    private final String event;
    private final String currentState;

    public InvalidEventException(String event, String currentState) {
        super(String.format(
                "No valid transition for event '%s' from state '%s'. " +
                        "Either no transition is defined, or all guards evaluated to false.",
                event, currentState));
        this.event = event;
        this.currentState = currentState;
    }

    public InvalidEventException(String message) {
        super(message);
        this.event = null;
        this.currentState = null;
    }

    public String getEvent() {
        return event;
    }

    public String getCurrentState() {
        return currentState;
    }
}
