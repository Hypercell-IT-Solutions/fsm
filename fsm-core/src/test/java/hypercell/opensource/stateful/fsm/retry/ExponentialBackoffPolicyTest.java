package hypercell.opensource.stateful.fsm.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExponentialBackoffPolicyTest {

    @Test
    void backoffDoubles_cappedAtMaxDelay() {
        ExponentialBackoffPolicy policy = new ExponentialBackoffPolicy(
                5, Duration.ofSeconds(2), Duration.ofSeconds(10));

        assertThat(policy.backoffFor(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.backoffFor(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy.backoffFor(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(policy.backoffFor(4)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.backoffFor(5)).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void shouldRetry_trueWithinMaxAttempts_falseAfter() {
        ExponentialBackoffPolicy policy = new ExponentialBackoffPolicy(
                3, Duration.ofSeconds(1), Duration.ofMinutes(1));

        assertThat(policy.shouldRetry(1, null)).isTrue();
        assertThat(policy.shouldRetry(2, null)).isTrue();
        assertThat(policy.shouldRetry(3, null)).isTrue();
        assertThat(policy.shouldRetry(4, null)).isFalse();
    }

    @Test
    void maxAttempts_returnsConfiguredValue() {
        ExponentialBackoffPolicy policy = new ExponentialBackoffPolicy(
                7, Duration.ofMillis(100), Duration.ofSeconds(60));
        assertThat(policy.maxAttempts()).isEqualTo(7);
    }

    @Test
    void constructor_throws_whenMaxAttemptsLessThanOne() {
        assertThatThrownBy(() ->
                new ExponentialBackoffPolicy(0, Duration.ofSeconds(1), Duration.ofSeconds(10))
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
    }
}
