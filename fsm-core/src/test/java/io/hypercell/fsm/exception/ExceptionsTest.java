package io.hypercell.fsm.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionsTest {

    @Test
    void subStepExecutionException_capturesContext() {
        RuntimeException cause = new RuntimeException("db error");
        SubStepExecutionException ex = new SubStepExecutionException("PROCESSING", "charge", cause);

        assertThat(ex.getStateName()).isEqualTo("PROCESSING");
        assertThat(ex.getSubStepName()).isEqualTo("charge");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void invalidEventException_withEventAndState_capturesContext() {
        InvalidEventException ex = new InvalidEventException("APPROVE", "SHIPPED");

        assertThat(ex.getMessage()).contains("APPROVE").contains("SHIPPED");
    }

    @Test
    void invalidEventException_withMessage_hasMessage() {
        InvalidEventException ex = new InvalidEventException("cannot proceed in RUNNING state");
        assertThat(ex.getMessage()).contains("cannot proceed");
    }

    @Test
    void concurrentExecutionException_capturesExecutionId() {
        ConcurrentExecutionException ex = new ConcurrentExecutionException("exec-1");
        assertThat(ex.getExecutionId()).isEqualTo("exec-1");
        assertThat(ex.getMessage()).contains("exec-1");
    }

    @Test
    void completedMachineException_capturesIdAndState() {
        CompletedMachineException ex = new CompletedMachineException("exec-1", "SHIPPED");
        assertThat(ex.getExecutionId()).isEqualTo("exec-1");
        assertThat(ex.getFinalState()).isEqualTo("SHIPPED");
    }

    @Test
    void stateMachineConfigurationException_isRuntimeException() {
        StateMachineConfigurationException ex = new StateMachineConfigurationException("bad config");
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("bad config");
    }
}
