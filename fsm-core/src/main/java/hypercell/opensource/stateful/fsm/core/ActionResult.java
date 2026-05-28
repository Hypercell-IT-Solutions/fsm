package hypercell.opensource.stateful.fsm.core;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable outcome of executing an Action or SubStep.
 * <p>
 * WHY THIS EXISTS AS A SEPARATE CLASS:
 * We need to store action outcomes in the ExecutionSnapshot so they survive
 * serialization and can be replayed on resume. A boolean "success/fail" isn't
 * enough — we also need the output data (so later sub-steps can use it) and
 * the error details (so retry decisions can be made).
 * <p>
 * SERIALIZATION NOTE:
 * The output map must contain only serialization-friendly types (String, Number,
 * Boolean, etc.). The caller is responsible for this — the library won't enforce
 * it at compile time but the SnapshotRepository will fail at runtime if the
 * values aren't serializable by the chosen storage mechanism.
 */
public final class ActionResult {

    public enum Status {SUCCESS, FAILED, SKIPPED}

    private final Status status;

    /**
     * Key-value output produced by the action.
     * Later sub-steps and transition actions can read this from the context.
     * Stored in the snapshot so it survives across retry attempts.
     */
    private final Map<String, Object> output;

    /**
     * Human-readable error description when status is FAILED.
     * Stored as a String (not Throwable) so it can be serialized cleanly.
     */
    private final String errorMessage;

    /**
     * The fully-qualified class name of the original exception, for retry
     * policy decisions (e.g. "don't retry on IllegalArgumentException").
     */
    private final String errorType;

    private ActionResult(Status status, Map<String, Object> output,
                         String errorMessage, String errorType) {
        this.status = status;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
        this.output = output != null ? Map.copyOf(output) : Collections.emptyMap();
    }

    /**
     * Action succeeded with no output data.
     */
    public static ActionResult success() {
        return new ActionResult(Status.SUCCESS, null, null, null);
    }

    /**
     * Action succeeded and produced output that later steps can read.
     */
    public static ActionResult success(Map<String, Object> output) {
        return new ActionResult(Status.SUCCESS, output, null, null);
    }

    /**
     * Action failed with an exception. The throwable is decomposed into strings.
     */
    public static ActionResult failed(Throwable error) {
        return new ActionResult(Status.FAILED, null,
                error.getMessage(),
                error.getClass().getName());
    }

    /**
     * Action failed with a plain message (no exception available).
     */
    public static ActionResult failed(String message) {
        return new ActionResult(Status.FAILED, null, message, null);
    }

    /**
     * Sub-step was skipped because it was already recorded as completed
     * in the snapshot from a previous run. The library sets this internally
     * during resume — callers should not construct SKIPPED results themselves.
     */
    public static ActionResult skipped() {
        return new ActionResult(Status.SKIPPED, null, null, null);
    }

    public Status getStatus() {
        return status;
    }

    /** Key-value data produced by the action; empty map if the action produced no output. */
    public Map<String, Object> getOutput() {
        return output;
    }

    /** Human-readable error description; {@code null} unless status is {@code FAILED}. */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Fully-qualified class name of the original exception; {@code null} if the failure
     * was not created from a {@code Throwable} (e.g. {@link #failed(String)}).
     * Useful for retry policy decisions: retry only on {@code TimeoutException}, etc.
     */
    public String getErrorType() {
        return errorType;
    }

    /** {@code true} when status is {@code SUCCESS}. */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /** {@code true} when status is {@code FAILED}. */
    public boolean isFailed() {
        return status == Status.FAILED;
    }

    /** {@code true} when status is {@code SKIPPED} (set internally by the library on resume). */
    public boolean isSkipped() {
        return status == Status.SKIPPED;
    }

    @Override
    public String toString() {
        return "ActionResult{status=" + status + (errorMessage != null ? ", error='" + errorMessage + "'" : "") + "}";
    }
}
