package hypercell.opensource.stateful.fsm.exception;

/**
 * Thrown by StateMachineBuilder.build() when the machine definition has structural
 * problems: no initial state, duplicate state names, orphaned transitions, etc.
 * <p>
 * All validation happens at build time, not at runtime. This means you catch
 * misconfiguration at application startup, not in production during a workflow.
 */
public class StateMachineConfigurationException extends StateMachineException {

    public StateMachineConfigurationException(String message) {
        super(message);
    }
}
