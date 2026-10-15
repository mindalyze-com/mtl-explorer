package com.x8ing.mtl.server.mtlserver.jobs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;

import static org.assertj.core.api.Assertions.assertThat;

class BackgroundConnectionFailureTest {
    @Test
    void recognizesWrappedPoolAcquisitionAndRecoverableConnectionFailures() {
        assertThat(BackgroundJobRunner.isTemporaryConnectionFailure(new CannotCreateTransactionException(
                "Synthetic wait", new SQLTransientConnectionException("Pool busy")))).isTrue();
        assertThat(BackgroundJobRunner.isTemporaryConnectionFailure(new CannotGetJdbcConnectionException(
                "Synthetic wait", new SQLRecoverableException("Connection lost")))).isTrue();
        assertThat(BackgroundJobRunner.isTemporaryConnectionFailure(new TransientDataAccessResourceException("Temporary resource"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"08006", "53300", "57P01"})
    void recognizesTemporarySqlStates(String state) {
        assertThat(BackgroundJobRunner.isTemporaryConnectionFailure(new SQLException("Synthetic connection problem", state))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"22003", "23505", "28000", "28P01", "3D000", "42501", "42601"})
    void aPoolTimeoutCannotHideInvalidDataCredentialsOrConfiguration(String state) {
        var timeout = new SQLTransientConnectionException("Synthetic pool wait");
        timeout.initCause(new SQLException("Synthetic non-retryable cause", state));
        assertThat(BackgroundJobRunner.isTemporaryConnectionFailure(new CannotCreateTransactionException("Wrapped", timeout))).isFalse();
    }

    @Test
    void doesNotReclassifyValidationOrNonTransientConnections() {
        assertThat(BackgroundJobRunner.isTemporaryConnectionFailure(new IllegalArgumentException("Invalid source"))).isFalse();
        assertThat(BackgroundJobRunner.isTemporaryConnectionFailure(new DataIntegrityViolationException("Invalid rows"))).isFalse();
        assertThat(BackgroundJobRunner.isTemporaryConnectionFailure(new SQLNonTransientConnectionException("Configuration", "08001"))).isFalse();
        assertThat(BackgroundJobRunner.isTemporaryConnectionFailure(null)).isFalse();
    }

    @Test
    void checksChainedSqlErrorsAndHandlesCycles() {
        var first = new SQLTransientConnectionException("Pool busy");
        first.setNextException(new SQLException("Invalid credentials", "28000"));
        assertThat(BackgroundJobRunner.isTemporaryConnectionFailure(first)).isFalse();
        var outer = new RuntimeException("Synthetic outer");
        var inner = new RuntimeException("Synthetic inner", outer);
        outer.initCause(inner);
        assertThat(BackgroundJobRunner.isTemporaryConnectionFailure(outer)).isFalse();
    }
}
