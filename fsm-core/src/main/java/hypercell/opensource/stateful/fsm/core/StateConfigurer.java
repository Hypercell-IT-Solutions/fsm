package hypercell.opensource.stateful.fsm.core;


import hypercell.opensource.stateful.fsm.builder.StateBuilder;

/**
 * Encapsulates the full configuration of one state as a reusable class.
 * <p>
 * This is the class-based alternative to the inline .state("NAME")...and() block.
 * The consumer implements this interface, injects whatever dependencies the state
 * needs, and wires it into the builder with a single .state(configurer) call.
 * <p>
 * The configure() method receives the StateBuilder already initialised with
 * stateName() — the implementation just adds sub-steps, hooks, and transitions.
 * <p>
 * SPRING BOOT USAGE:
 * Mark the implementation {@code @Component}. Inject your services. The machine
 * definition bean can then {@code @Autowire} a list of {@code StateConfigurer<C>} and
 * register them all in a loop — the machine assembles itself from parts.
 *
 * @param <C> the context type flowing through the machine
 */
public interface StateConfigurer<C> {

    /**
     * The state name. Used as the builder key and in all log/event output.
     */
    String stateName();

    /**
     * Configure the state using the provided builder.
     * Call .subStep(), .on(), .onEntry(), .onExit(), .terminal() here.
     * Do NOT call .and() — the machine builder calls that internally.
     */
    void configure(StateBuilder<C> state);
}
