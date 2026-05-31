package io.hypercell.fsm.core;

/**
 * A unit of executable work within the state machine.
 * <p>
 * Used in two places:
 * 1. SubStepDefinition — the actual work a state performs (call API, write DB, etc.)
 * 2. TransitionDefinition — optional work executed when moving between states
 * <p>
 * SYNC VS ASYNC:
 * This is the synchronous version. The library executes this on the calling thread,
 * blocking until it returns. When async support is added later, a separate
 * {@code AsyncAction<C>} interface will be introduced — this interface stays unchanged.
 * <p>
 * ERROR HANDLING:
 * Actions should never swallow exceptions silently. Either:
 * - Return ActionResult.failed(exception) if the failure is expected and recoverable
 * - Throw an exception if something unexpected happened (the library will catch it
 * and convert it to ActionResult.failed() internally)
 * Both paths result in the machine entering FAILED state and saving a snapshot.
 *
 * @param <C> the context type flowing through the machine
 */
@FunctionalInterface
public interface Action<C> {

    /**
     * Execute the action.
     *
     * @param ctx the current machine ctx (read/write — actions may update it)
     * @return the outcome; never return null (use ActionResult.success() for void actions)
     * @throws Exception any unexpected error; will be caught and wrapped by the library
     */
    @SuppressWarnings("java:S112")
    ActionResult execute(C ctx) throws Exception;
}
