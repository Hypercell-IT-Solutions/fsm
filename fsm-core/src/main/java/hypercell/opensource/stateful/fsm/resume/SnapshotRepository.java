package hypercell.opensource.stateful.fsm.resume;

import java.util.List;
import java.util.Optional;

/**
 * Pluggable storage for execution snapshots.
 * The library calls this — the caller provides the implementation.
 * See InMemorySnapshotRepository for a reference implementation.
 */
public interface SnapshotRepository {
    void save(String executionId, ExecutionSnapshot snapshot);

    Optional<ExecutionSnapshot> load(String executionId);

    void delete(String executionId);

    /**
     * Returns all snapshots with status FAILED or RETRY_SCHEDULED.
     * Called on startup by StateMachineManager.recoverPendingRetries()
     * to re-schedule retries that were in flight when the process stopped.
     * <p>
     * Implementations backed by a database should query by status column.
     * The InMemory implementation scans the in-memory map.
     */
    List<ExecutionSnapshot> listPendingRetries();
}
