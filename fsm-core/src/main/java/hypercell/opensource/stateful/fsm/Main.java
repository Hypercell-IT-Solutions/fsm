package hypercell.opensource.stateful.fsm;

import hypercell.opensource.stateful.fsm.core.ActionResult;
import hypercell.opensource.stateful.fsm.core.StateMachineDefinition;
import hypercell.opensource.stateful.fsm.core.StateMachineInstance;

public class Main {
    public static void main(String[] args) {
        StateMachineDefinition<String> machineDefinition = StateMachine.<String>define("1")
                .initial("STATE_A")
                .listener(StateMachine.loggingListener("[TAG]"))
                .snapshotRepository(StateMachine.inMemoryRepository())
                .state("STATE_A")
                .on("e1").to("STATE_B").end()
                .on("e2").to("STATE_C").end()
                .and()
                .state("STATE_B").terminal().and()
                .state("STATE_C")
                .on("e3").to("STATE_B").end()
                .subStep("step-1", ctx -> {
                    System.out.println("state-a::step-1");
                    return ActionResult.success();
                })
                .and()
                .build();

        StateMachineInstance<String> machineInstance = machineDefinition.newInstance("input 1");
        machineInstance.trigger("e2");
        machineInstance.trigger("e3");
    }
}
