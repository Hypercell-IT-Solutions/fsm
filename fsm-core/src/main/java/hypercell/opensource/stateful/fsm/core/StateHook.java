package hypercell.opensource.stateful.fsm.core;

/**
 * Lifecycle callbacks called when entering or exiting a state.
 * <p>
 * WHY HOOKS ARE SEPARATE FROM SUB-STEPS:
 * Sub-steps are the real work of a state — they're tracked, recorded in the
 * execution history, and skipped on resume. Hooks are lightweight
 * setup/teardown that always run (they are NOT skipped on resume).
 * <p>
 * Use hooks for:
 * - Logging / metrics ("entered PROCESSING state")
 * - Setting up context data needed by sub-steps ("mark order as in-progress")
 * - Cleanup after a state exits ("release a lock held during PROCESSING")
 * <p>
 * Use sub-steps for:
 * - Calling external APIs
 * - Writing to databases
 * - Any work that must NOT be repeated if it already succeeded
 * <p>
 * EXCEPTION HANDLING:
 * If a hook throws, the machine treats it as a hard failure and stops.
 * Unlike sub-step failures, hook failures are not retried at the sub-step
 * granularity — the whole transition is rolled back conceptually.
 *
 * @param <C> the context type flowing through the machine
 */
public interface StateHook<C> {

    /**
     * Called after the machine transitions INTO this state, before sub-steps run.
     */
    default void onEntry(C context) {
    }

    /**
     * Called before the machine transitions OUT of this state.
     * Only called when a transition actually fires — not called if the machine
     * fails mid-state (the machine stays in the failed state for retry).
     */
    default void onExit(C context) {
    }
}
