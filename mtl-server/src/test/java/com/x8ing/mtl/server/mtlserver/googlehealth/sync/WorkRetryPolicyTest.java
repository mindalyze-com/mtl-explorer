package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiException;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.TransactionTimedOutException;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkRetryPolicyTest {
    private final ConnectorProperties properties = new ConnectorProperties();
    private final WorkRetryPolicy policy = new WorkRetryPolicy(properties);
    private final Instant now = Instant.parse("2026-10-11T12:00:00Z");

    @Test
    void fourthTemporaryFailureSlowsRetriesInsteadOfExhaustingThem() {
        properties.setSyncInterval(Duration.ofMinutes(20));
        assertThat(properties.getWorkFailureLimit()).isEqualTo(4);
        assertThat(properties.getMaxRetryAttempts()).isEqualTo(5);
        for (int attempts = 1; attempts < 4; attempts++)
            assertThat(policy.afterFailure(attempts, true, now).retryAt()).isEqualTo(now.plus(Duration.ofMinutes(20)));
        for (int sample = 0; sample < 30; sample++) {
            assertDelay(4, Duration.ofMinutes(40), Duration.ofMinutes(48));
            assertDelay(5, Duration.ofMinutes(80), Duration.ofMinutes(96));
            assertDelay(6, Duration.ofMinutes(160), Duration.ofMinutes(192));
        }
        assertThat(policy.afterFailure(Integer.MAX_VALUE, true, now))
                .isEqualTo(new WorkRetryPolicy.Decision(true, now.plus(Duration.ofHours(6))));
    }

    @Test
    void validationFailuresRemainBoundedAndDoNotAdvertiseAnotherAttempt() {
        assertThat(policy.afterFailure(3, false, now).automaticRetry()).isTrue();
        assertThat(policy.afterFailure(4, false, now)).isEqualTo(new WorkRetryPolicy.Decision(false, null));
    }

    @Test
    void providerAndDatabaseTemporaryErrorsAreDistinctFromInvalidDataAndAuthorization() {
        assertThat(WorkRetryPolicy.isRecoverable(new ApiException("synthetic", 503, true, ApiException.Reason.SERVER))).isTrue();
        assertThat(WorkRetryPolicy.isRecoverable(new ApiException("synthetic", 0, true, ApiException.Reason.TRANSPORT))).isTrue();
        assertThat(WorkRetryPolicy.isRecoverable(new ApiException("synthetic", 0, true, ApiException.Reason.INVALID_RESPONSE))).isFalse();
        assertThat(WorkRetryPolicy.isRecoverable(new IllegalArgumentException("synthetic TCX size limit"))).isFalse();
        assertThat(WorkRetryPolicy.isRecoverable(new java.io.IOException("synthetic storage failure"))).isTrue();
        assertThat(WorkRetryPolicy.isRecoverable(new QueryTimeoutException("synthetic"))).isTrue();
        assertThat(WorkRetryPolicy.isRecoverable(new TransactionTimedOutException("synthetic"))).isTrue();
        assertThat(WorkRetryPolicy.isRecoverable(new java.sql.SQLException("synthetic connection lost", "08006"))).isTrue();
        assertThat(WorkRetryPolicy.isRecoverable(new java.sql.SQLException("synthetic invalid authorization", "28000"))).isFalse();
        assertThat(WorkRetryPolicy.isRecoverable(new ApiException("synthetic", 401, false, ApiException.Reason.AUTHORIZATION))).isFalse();
        assertThat(WorkRetryPolicy.isRecoverable(new ApiException("synthetic", 429, true, ApiException.Reason.RATE_LIMIT))).isFalse();
        assertThat(WorkRetryPolicy.isRecoverable(new SyncValidationException(SyncValidationException.Reason.DUPLICATE_QUERY_IDENTITY))).isFalse();
        assertThat(WorkRetryPolicy.isRecoverable(new DataIntegrityViolationException("synthetic",
                new SyncValidationException(SyncValidationException.Reason.DUPLICATE_TRAVERSAL_IDENTITY)))).isFalse();
    }

    @Test
    void configuredCapMustAllowTheNormalRetryInterval() {
        properties.setWorkRetryMaxDelay(Duration.ZERO);
        assertThatThrownBy(properties::validateSynchronization).hasMessageContaining("work-retry-max-delay");
        properties.setWorkRetryMaxDelay(Duration.ofMinutes(1));
        assertThatThrownBy(properties::validateSynchronization).hasMessageContaining("synchronization limits");
    }

    private void assertDelay(int attempts, Duration minimum, Duration maximum) {
        var decision = policy.afterFailure(attempts, true, now);
        assertThat(decision.automaticRetry()).isTrue();
        assertThat(Duration.between(now, decision.retryAt())).isBetween(minimum, maximum);
    }
}
