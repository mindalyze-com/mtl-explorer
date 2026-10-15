package com.x8ing.mtl.server.mtlserver.web.services.info;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeVersionInfoServiceTest {
    private final JdbcOperations jdbc = mock(JdbcOperations.class);
    private final RuntimeVersionInfoService service = new RuntimeVersionInfoService(new NamedParameterJdbcTemplate(jdbc));

    @Test
    void reportsTheRunningDatabaseAndJavaVersions() {
        when(jdbc.queryForMap(anyString())).thenReturn(Map.of("postgresql", "18.3", "postgis", "3.6.2"));
        var versions = service.getVersions();
        assertThat(versions.java()).isEqualTo(System.getProperty("java.version"));
        assertThat(versions.postgresql()).isEqualTo("18.3");
        assertThat(versions.postgis()).isEqualTo("3.6.2");
    }

    @Test
    void keepsJavaVersionWhenTheDatabaseCannotBeQueried() {
        when(jdbc.queryForMap(anyString())).thenThrow(new DataAccessResourceFailureException("Test database unavailable"));
        var versions = service.getVersions();
        assertThat(versions.java()).isEqualTo(System.getProperty("java.version"));
        assertThat(versions.postgresql()).isNull();
        assertThat(versions.postgis()).isNull();
    }
}
