package io.hypercell.fsm.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPoliciesTest {

    @Test
    void fixedDelayPolicy_sameDelayEveryAttempt() {
        FixedDelayPolicy policy = new FixedDelayPolicy(3, Duration.ofSeconds(5));

        assertThat(policy.backoffFor(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.backoffFor(2)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.backoffFor(3)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.shouldRetry(3, null)).isTrue();
        assertThat(policy.shouldRetry(4, null)).isFalse();
        assertThat(policy.maxAttempts()).isEqualTo(3);
    }

    @Test
    void noAutoRetryPolicy_neverRetries() {
        RetryPolicy policy = NoAutoRetryPolicy.INSTANCE;

        assertThat(policy.shouldRetry(1, null)).isFalse();
        assertThat(policy.shouldRetry(100, null)).isFalse();
        assertThat(policy.backoffFor(1)).isEqualTo(Duration.ZERO);
        assertThat(policy.maxAttempts()).isEqualTo(0);
    }
}
