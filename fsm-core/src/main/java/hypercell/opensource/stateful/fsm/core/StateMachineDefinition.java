package hypercell.opensource.stateful.fsm.core;

import hypercell.opensource.stateful.fsm.resume.ExecutionSnapshot;
import hypercell.opensource.stateful.fsm.resume.ResumePolicy;
import hypercell.opensource.stateful.fsm.resume.SnapshotRepository;

import java.util.List;

/**
 * The immutable, validated blueprint of a state machine.
 * Created once by the builder, reused to produce many instances.
 *
 * @param <C> the context type flowing through the machine
 */
public interface StateMachineDefinition<C> {

    String id();

    StateDefinition<C> initialState();

    StateDefinition<C> stateByName(String name);

    List<TransitionDefinition<C>> transitionsFrom(String stateName);

    /**
     * The resume policy used by SubStepRunner to decide which steps to skip.
     * Exposed here so the runner can access it without a direct dependency on
     * the concrete definition class.
     */
    ResumePolicy<C> resumePolicy();

    StateMachineInstance<C> newInstance(C context);

    StateMachineInstance<C> resume(C context, ExecutionSnapshot snapshot,
                                   SnapshotRepository repository);
}
