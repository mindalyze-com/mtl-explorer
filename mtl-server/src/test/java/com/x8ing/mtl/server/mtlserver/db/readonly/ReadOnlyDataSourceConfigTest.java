package com.x8ing.mtl.server.mtlserver.db.readonly;

import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;

class ReadOnlyDataSourceConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ReadOnlyDataSourceConfig.class)
            .withBean("liquibase", Object.class, Object::new)
            .withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost/test",
                    "spring.datasource.username=test", "spring.datasource.password=test",
                    "spring.datasource.hikari.maximum-pool-size=5",
                    "mtl.db-readonly.username=readonly", "mtl.db-readonly.password=test");

    @Test
    void disablesJitOnBothCustomPoolsByDefault() {
        assertSessionInitialization(contextRunner, "SET jit = off");
    }

    @Test
    void honorsExplicitSessionInitializationOnBothPools() {
        assertSessionInitialization(contextRunner.withPropertyValues(
                "spring.datasource.hikari.connection-init-sql=SET jit = on"), "SET jit = on");
    }

    @Test
    void routesDynamicSqlThroughTheReadOnlyPool() {
        try (var ignored = mockConstruction(HikariDataSource.class, (mock, context) -> configurePrimaryConnection(mock))) {
            contextRunner.run(context -> {
                assertThat(context).hasNotFailed();
                var template = context.getBean("readOnlyJdbcTemplate", NamedParameterJdbcTemplate.class);
                assertThat(template.getJdbcTemplate().getDataSource())
                        .isSameAs(context.getBean("readOnlyDataSource"))
                        .isNotSameAs(context.getBean("primaryDataSource"));
            });
        }
    }

    @Test
    void retainsThePrimaryPoolForDefaultNamedSqlQueries() {
        try (var ignored = mockConstruction(HikariDataSource.class, (mock, context) -> configurePrimaryConnection(mock))) {
            contextRunner.run(context -> {
                assertThat(context).hasNotFailed();
                var template = context.getBean(NamedParameterJdbcTemplate.class);
                assertThat(template).isSameAs(context.getBean("namedParameterJdbcTemplate"));
                assertThat(template.getJdbcTemplate().getDataSource())
                        .isSameAs(context.getBean("primaryDataSource"));
            });
        }
    }

    @Test
    void injectsTheReadOnlyTemplateIntoDynamicSqlWithBothTemplatesPresent() {
        try (var ignored = mockConstruction(HikariDataSource.class, (mock, context) -> configurePrimaryConnection(mock))) {
            contextRunner.withBean(GpsTrackRepository.class, () -> mock(GpsTrackRepository.class))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var service = context.getAutowireCapableBeanFactory().createBean(DynamicSqlService.class);
                        assertThat(ReflectionTestUtils.getField(service, "readOnlyJdbcTemplate"))
                                .isSameAs(context.getBean("readOnlyJdbcTemplate"))
                                .isNotSameAs(context.getBean("namedParameterJdbcTemplate"));
                    });
        }
    }

    @Test
    void appliesConfiguredCredentialBeforeReadOnlyPoolCreation() {
        List<PreparedStatement> formats = new ArrayList<>();
        try (var ignored = mockConstruction(HikariDataSource.class, (mock, context) ->
                formats.add(configurePrimaryConnection(mock)))) {
            contextRunner.withPropertyValues("mtl.db-readonly.password=custom'credential")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        verify(formats.getFirst()).setString(1, "readonly");
                        verify(formats.getFirst()).setString(2, "custom'credential");
                    });
        }
    }

    @Test
    void blankReadOnlyPasswordUsesConfiguredPrimaryCredential() {
        List<PreparedStatement> formats = new ArrayList<>();
        try (var ignored = mockConstruction(HikariDataSource.class, (mock, context) ->
                formats.add(configurePrimaryConnection(mock)))) {
            contextRunner.withPropertyValues("mtl.db-readonly.password=")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        verify(formats.getFirst()).setString(2, "test");
                    });
        }
    }

    @Test
    void failsStartupWithoutIncludingTheCredentialInTheException() {
        try (var ignored = mockConstruction(HikariDataSource.class, (mock, context) -> {
            configurePrimaryConnection(mock);
            when(mock.getConnection()).thenThrow(new SQLException("private-test-credential", "42501"));
        })) {
            contextRunner.run(context -> {
                assertThat(context).hasFailed();
                StringWriter trace = new StringWriter();
                context.getStartupFailure().printStackTrace(new PrintWriter(trace));
                assertThat(trace.toString()).contains("Cannot configure the read-only database credential")
                        .doesNotContain("private-test-credential");
            });
        }
    }

    private PreparedStatement configurePrimaryConnection(HikariDataSource source) throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement format = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        Statement statement = mock(Statement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(format);
        when(format.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getString(1)).thenReturn("ALTER ROLE readonly PASSWORD 'test'");
        when(connection.createStatement()).thenReturn(statement);
        return format;
    }

    private void assertSessionInitialization(ApplicationContextRunner runner, String expected) {
        List<HikariConfig> configs = new ArrayList<>();
        try (var ignored = mockConstruction(HikariDataSource.class,
                (mock, context) -> {
                    configs.add((HikariConfig) context.arguments().getFirst());
                    configurePrimaryConnection(mock);
                })) {
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(configs).hasSize(2).allSatisfy(config ->
                        assertThat(config.getConnectionInitSql()).isEqualTo(expected));
                assertThat(configs).extracting(HikariConfig::isReadOnly).containsExactlyInAnyOrder(false, true);
            });
        }
    }
}
