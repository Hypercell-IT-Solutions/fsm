package hypercell.opensource.stateful.fsm.core;

import hypercell.opensource.stateful.fsm.manager.StateMachineManager;
import hypercell.opensource.stateful.fsm.resume.ExecutionSnapshot;
import hypercell.opensource.stateful.fsm.resume.ResumePolicy;
import hypercell.opensource.stateful.fsm.resume.SnapshotRepository;
import hypercell.opensource.stateful.fsm.retry.RetryCoordinator;

import java.util.List;
import java.util.function.Function;

/**
 * The immutable, validated blueprint of a state machine.
 * Created once by the builder, reused to produce many instances.
 *
 * @param <C> the context type flowing through the machine
 */
public interface StateMachineDefinition<C> {

    String id();

    StateDefinition<C> initialState();

    SnapshotRepository repository();

    StateDefinition<C> stateByName(String name);

    List<TransitionDefinition<C>> transitionsFrom(String stateName);

    /**
     * The resume policy used by SubStepRunner to decide which steps to skip.
     * Exposed here so the runner can access it without a direct dependency on
     * the concrete definition class.
     */
    ResumePolicy<C> resumePolicy();

    RetryCoordinator<C> retryCoordinator();

    StateMachineInstance<C> newInstance(C context);

    StateMachineInstance<C> newInstance(C context, String executionId);

    StateMachineManager<C> newManager(Function<String, C> contextLoader);

    StateMachineManager<C> newManager(SnapshotRepository repository, Function<String, C> contextLoader);

    StateMachineInstance<C> reconstitute(C context, ExecutionSnapshot snapshot);

    /**
     * Restore a RUNNING instance from a checkpoint (process restarted between requests).
     * Positions at snapshot.currentStateName() with RUNNING status.
     * Caller calls trigger(event) next.
     */
    StateMachineInstance<C> reconstitute(C context, ExecutionSnapshot snapshot, SnapshotRepository repository);

    StateMachineInstance<C> resume(C context, ExecutionSnapshot snapshot);

    StateMachineInstance<C> resume(C context, ExecutionSnapshot snapshot,
                                   SnapshotRepository repository);
}
