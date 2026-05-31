package io.hypercell.fsm.core;

/**
 * A condition evaluated before a transition fires.
 * <p>
 * If a transition has a Guard attached, trigger() will only follow that transition
 * if the guard returns true. If multiple transitions match the same event, they are
 * evaluated in definition order — the first one whose guard returns true is taken.
 * <p>
 * IMPORTANT — SIDE EFFECT FREE:
 * Guards must be pure — they should only read the ctx, never modify it.
 * Guards can be called multiple times (once per candidate transition per event),
 * and in the future may be called on threads the caller doesn't control.
 * <p>
 * Guards that need to check external state (DB, cache) are OK as long as they
 * don't modify anything — but keep them fast, since they run synchronously on
 * the calling thread.
 *
 * @param <C> the context type flowing through the machine
 */
@FunctionalInterface
public interface Guard<C> {

    /**
     * @param ctx the current machine ctx (read-only)
     * @return true if the transition should fire, false to skip this transition
     */
    boolean evaluate(C ctx);
}
