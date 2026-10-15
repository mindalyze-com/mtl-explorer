package com.x8ing.mtl.server.mtlserver.googlehealth.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRetryPolicyTest {

    @Test
    void retriesRateLimitsAndServerErrorsOnly() {
        assertThat(ApiRetryPolicy.isRetriableStatus(429)).isTrue();
        assertThat(ApiRetryPolicy.isRetriableStatus(500)).isTrue();
        assertThat(ApiRetryPolicy.isRetriableStatus(503)).isTrue();
        assertThat(ApiRetryPolicy.isRetriableStatus(400)).isFalse();
        assertThat(ApiRetryPolicy.isRetriableStatus(403)).isFalse();
    }

    @Test
    void respectsTheFullProviderCooldown() {
        assertThat(ApiRetryPolicy.delayForAttempt(0, Duration.ofMinutes(5)))
                .isEqualTo(Duration.ofMinutes(5));
    }
}
