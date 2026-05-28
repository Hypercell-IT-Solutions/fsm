package hypercell.opensource.stateful.fsm.resume;

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
}
