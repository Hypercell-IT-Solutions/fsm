package hypercell.opensource.stateful.fsm.exception;

/**
 * Thrown during build-time validation when a transition references a target state
 * that was never defined. This is a configuration error, not a runtime error.
 */
public class InvalidStateException extends StateMachineException {

    private final String stateName;

    public InvalidStateException(String stateName) {
        super(String.format(
                "State '%s' is referenced in a transition but was never defined. " +
                        "Call .state(\"%s\") in the builder.", stateName, stateName));
        this.stateName = stateName;
    }

    public String getStateName() {
        return stateName;
    }
}
