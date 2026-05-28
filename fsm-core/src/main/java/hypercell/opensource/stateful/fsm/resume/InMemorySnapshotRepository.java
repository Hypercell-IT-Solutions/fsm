package hypercell.opensource.stateful.fsm.resume;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySnapshotRepository implements SnapshotRepository {
    private final Map<String, ExecutionSnapshot> store = new ConcurrentHashMap<>();

    @Override
    public void save(String id, ExecutionSnapshot s) {
        store.put(id, s);
    }

    @Override
    public Optional<ExecutionSnapshot> load(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }

    @Override
    public List<ExecutionSnapshot> listPendingRetries() {
        return store.values().stream()
                .filter(ExecutionSnapshot::isFailed)
                .toList();
    }

    public int size() {
        return store.size();
    }

    public static InMemorySnapshotRepository create() {
        return new InMemorySnapshotRepository();
    }
}
