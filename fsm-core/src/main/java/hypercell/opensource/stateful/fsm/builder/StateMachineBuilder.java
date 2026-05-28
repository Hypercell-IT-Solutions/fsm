package hypercell.opensource.stateful.fsm.builder;

import hypercell.opensource.stateful.fsm.core.*;
import hypercell.opensource.stateful.fsm.exception.StateMachineConfigurationException;
import hypercell.opensource.stateful.fsm.execution.DefaultStateMachineDefinition;
import hypercell.opensource.stateful.fsm.listener.EventBus;
import hypercell.opensource.stateful.fsm.listener.MachineEventListener;
import hypercell.opensource.stateful.fsm.manager.StateMachineManager;
import hypercell.opensource.stateful.fsm.resume.DefaultResumePolicy;
import hypercell.opensource.stateful.fsm.resume.ExecutionSnapshot;
import hypercell.opensource.stateful.fsm.resume.ResumePolicy;
import hypercell.opensource.stateful.fsm.resume.SnapshotRepository;
import hypercell.opensource.stateful.fsm.retry.NoAutoRetryPolicy;
import hypercell.opensource.stateful.fsm.retry.RetryCoordinator;
import hypercell.opensource.stateful.fsm.retry.RetryPolicy;
import hypercell.opensource.stateful.fsm.retry.RetryScheduler;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Fluent builder for state machine definitions.
 * <p>
 * Usage:
 * StateMachine.<Ctx>define("machine-id")
 * .initial("STATE_A")
 * .listener(StateMachine.loggingListener("[TAG]"))
 * .snapshotRepository(StateMachine.inMemoryRepository())
 * .state("STATE_A")
 * .subStep("step-1", ctx -> doWork(ctx))
 * .on("EVENT").to("STATE_B").end()
 * .and()
 * .state("STATE_B").terminal().and()
 * .build();
 *
 * @param <C> the context type flowing through the machine
 */
public class StateMachineBuilder<C> {

    private final String id;
    private String initialStateName;
    private final List<StateBuilder<C>> stateBuilders = new ArrayList<>();
    private final Set<String> registeredNames = new HashSet<>();
    private final List<MachineEventListener<C>> listeners = new ArrayList<>();

    private ResumePolicy<C> resumePolicy = DefaultResumePolicy.getInstance();
    private SnapshotRepository snapshotRepository = null;
    private RetryPolicy retryPolicy = NoAutoRetryPolicy.INSTANCE;
    private RetryScheduler retryScheduler = null;
    private Function<String, C> contextLoader = null;

    public StateMachineBuilder(String id) {
        this.id = id;
    }

    public StateMachineBuilder<C> initial(String s) {
        initialStateName = s;
        return this;
    }

    public StateMachineBuilder<C> snapshotRepository(SnapshotRepository r) {
        snapshotRepository = r;
        return this;
    }

    public StateMachineBuilder<C> retryPolicy(RetryPolicy p) {
        retryPolicy = p;
        return this;
    }

    public StateMachineBuilder<C> retryScheduler(RetryScheduler s) {
        retryScheduler = s;
        return this;
    }

    public StateMachineBuilder<C> contextLoader(Function<String, C> l) {
        contextLoader = l;
        return this;
    }

    public StateMachineBuilder<C> resumePolicy(ResumePolicy<C> p) {
        resumePolicy = p;
        return this;
    }

    /**
     * Register an event listener. Multiple calls add multiple listeners;
     * events are dispatched in registration order.
     */
    public StateMachineBuilder<C> listener(MachineEventListener<C> listener) {
        listeners.add(listener);
        return this;
    }

    /**
     * Register a state from a configurer class.
     * The configurer's stateName() becomes the builder key.
     * The configurer's configure() call adds sub-steps, hooks, and transitions.
     */
    public StateMachineBuilder<C> state(StateConfigurer<C> configurer) {
        StateBuilder<C> stateBuilder = new StateBuilder<>(this, configurer.stateName());
        configurer.configure(stateBuilder);
        registerState(stateBuilder);
        return this;
    }

    public StateBuilder<C> state(String name) {
        if (name.contains("::")) throw new StateMachineConfigurationException(
                "State name '" + name + "' contains reserved separator '::'.");
        return new StateBuilder<>(this, name);
    }

    void registerState(StateBuilder<C> sb) {
        if (!registeredNames.add(sb.getName())) throw new StateMachineConfigurationException(
                "Duplicate state name: '" + sb.getName() + "'");
        stateBuilders.add(sb);
    }

    @SuppressWarnings("unchecked")
    public StateMachineDefinition<C> build() {
        if (stateBuilders.isEmpty()) throw new StateMachineConfigurationException(
                "No states defined. Call .state(\"NAME\")...and().");
        if (initialStateName == null) throw new StateMachineConfigurationException(
                "No initial state. Call .initial(\"STATE_NAME\").");
        if (!registeredNames.contains(initialStateName)) throw new StateMachineConfigurationException(
                "Initial state '" + initialStateName + "' is not defined.");

        boolean hasAutoRetry = !(retryPolicy instanceof NoAutoRetryPolicy);
        if (hasAutoRetry) {
            if (snapshotRepository == null) throw new StateMachineConfigurationException(
                    "retryPolicy requires snapshotRepository.");
            if (contextLoader == null) throw new StateMachineConfigurationException(
                    "retryPolicy requires contextLoader.");
        }

        Map<String, StateDefinition<C>> stateMap = new LinkedHashMap<>();
        for (StateBuilder<C> sb : stateBuilders) {
            stateMap.put(sb.getName(), new SimpleStateDef<>(
                    sb.getName(), sb.isTerminal(), sb.getSubSteps(), sb.getCompositeHook()));
        }

        Map<String, List<TransitionDefinition<C>>> transitionMap = new LinkedHashMap<>();
        for (StateBuilder<C> sb : stateBuilders) {
            if (sb.isTerminal() && !sb.transitionData.isEmpty())
                throw new StateMachineConfigurationException(
                        "Terminal state '" + sb.getName() + "' has outgoing transitions.");
            List<TransitionDefinition<C>> ts = new ArrayList<>();
            for (Object[] td : sb.transitionData) {
                String target = (String) td[1];
                if (!stateMap.containsKey(target)) throw new StateMachineConfigurationException(
                        "Transition from '" + sb.getName() + "' targets unknown state '" + target + "'.");
                ts.add(new SimpleTransDef<>((String) td[0], sb.getName(), target,
                        (Guard<C>) td[2], (Action<C>) td[3]));
            }
            transitionMap.put(sb.getName(), ts);
        }

        EventBus<C> bus = listeners.isEmpty() ? EventBus.empty() : new EventBus<>(listeners);

        DefinitionRef<C> ref = new DefinitionRef<>();
        RetryCoordinator<C> coordinator = null;
        if (hasAutoRetry) {
            RetryScheduler sched = retryScheduler != null ? retryScheduler : new InlineScheduler();
            coordinator = new RetryCoordinator<>(retryPolicy, sched, snapshotRepository, ref, contextLoader);
        }

        DefaultStateMachineDefinition<C> def = new DefaultStateMachineDefinition<>(
                id, stateMap.get(initialStateName), stateMap, transitionMap,
                resumePolicy, snapshotRepository, coordinator, bus);
        ref.set(def);
        return def;
    }

    private static final class DefinitionRef<C> implements StateMachineDefinition<C> {
        private StateMachineDefinition<C> d;

        void set(StateMachineDefinition<C> def) {
            d = def;
        }

        @Override
        public String id() {
            return d.id();
        }

        @Override
        public StateDefinition<C> initialState() {
            return d.initialState();
        }

        @Override
        public SnapshotRepository repository() {
            return d.repository();
        }

        @Override
        public StateDefinition<C> stateByName(String n) {
            return d.stateByName(n);
        }

        @Override
        public List<TransitionDefinition<C>> transitionsFrom(String n) {
            return d.transitionsFrom(n);
        }

        @Override
        public ResumePolicy<C> resumePolicy() {
            return d.resumePolicy();
        }

        @Override
        public RetryCoordinator<C> retryCoordinator() {
            return d.retryCoordinator();
        }

        @Override
        public StateMachineInstance<C> newInstance(C c) {
            return d.newInstance(c);
        }

        @Override
        public StateMachineInstance<C> newInstance(C context, String executionId) {
            return d.newInstance(context, executionId);
        }

        @Override
        public StateMachineManager<C> newManager(Function<String, C> contextLoader) {
            return d.newManager(contextLoader);
        }

        @Override
        public StateMachineManager<C> newManager(SnapshotRepository repository, Function<String, C> contextLoader) {
            return d.newManager(repository, contextLoader);
        }

        @Override
        public StateMachineInstance<C> reconstitute(C context, ExecutionSnapshot snapshot) {
            return d.reconstitute(context, snapshot);
        }

        @Override
        public StateMachineInstance<C> reconstitute(C context, ExecutionSnapshot snapshot, SnapshotRepository repository) {
            return d.reconstitute(context, snapshot, repository);
        }

        @Override
        public StateMachineInstance<C> resume(C context, ExecutionSnapshot snapshot) {
            return d.resume(context, snapshot);
        }

        @Override
        public StateMachineInstance<C> resume(C c, ExecutionSnapshot s, SnapshotRepository r) {
            return d.resume(c, s, r);
        }
    }

    static final class SimpleStateDef<C> implements StateDefinition<C> {
        private final String n;
        private final boolean t;
        private final List<SubStepDefinition<C>> s;
        private final StateHook<C> h;

        SimpleStateDef(String n, boolean t, List<SubStepDefinition<C>> s, StateHook<C> h) {
            this.n = n;
            this.t = t;
            this.s = Collections.unmodifiableList(s);
            this.h = h;
        }

        @Override
        public String name() {
            return n;
        }

        @Override
        public boolean isTerminal() {
            return t;
        }

        @Override
        public List<SubStepDefinition<C>> subSteps() {
            return s;
        }

        @Override
        public Optional<StateHook<C>> hook() {
            return Optional.ofNullable(h);
        }
    }

    static final class SimpleTransDef<C> implements TransitionDefinition<C> {
        private final String e;
        private final String src;
        private final String tgt;
        private final Guard<C> g;
        private final Action<C> a;

        SimpleTransDef(String e, String s, String t, Guard<C> g, Action<C> a) {
            this.e = e;
            src = s;
            tgt = t;
            this.g = g;
            this.a = a;
        }

        @Override
        public String event() {
            return e;
        }

        @Override
        public String sourceState() {
            return src;
        }

        @Override
        public String targetState() {
            return tgt;
        }

        @Override
        public Optional<Guard<C>> guard() {
            return Optional.ofNullable(g);
        }

        @Override
        public Optional<Action<C>> action() {
            return Optional.ofNullable(a);
        }
    }

    private static final class InlineScheduler implements RetryScheduler {
        private final ScheduledExecutorService ex = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "fsm-retry");
            t.setDaemon(true);
            return t;
        });
        private final Map<String, ScheduledFuture<?>> p = new ConcurrentHashMap<>();

        @Override
        public void schedule(String id, Duration d, Runnable a) {
            p.put(id, ex.schedule(() -> {
                p.remove(id);
                a.run();
            }, d.toMillis(), TimeUnit.MILLISECONDS));
        }

        @Override
        public void cancel(String id) {
            ScheduledFuture<?> f = p.remove(id);
            if (f != null) f.cancel(false);
        }

        @Override
        public void shutdown() {
            ex.shutdown();
        }
    }
}
