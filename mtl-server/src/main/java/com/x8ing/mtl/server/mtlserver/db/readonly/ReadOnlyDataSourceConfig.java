package com.x8ing.mtl.server.mtlserver.db.readonly;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * For the dynamic grouping dynamic SQL is used.
 * <p>
 * For security reasons, we must avoid that such SQL can modify (delete, change) data in the DB.
 * We could either try to parse (very difficult against advanced attacks) or we create a read only user to the DB
 * and use that user for these queries. We follow that later approach.
 */
@Configuration
@Slf4j
@JsonPropertyOrder({
        "dbURL",
        "readonlyUsername",
        "primaryUsername",
        "readonlyPassword",
        "primaryPassword",
        "maximumPoolSize",
        "minimumIdle",
        "readOnlyMaximumPoolSize",
        "readOnlyMinimumIdle"
})
public class ReadOnlyDataSourceConfig {

    @Value("${spring.datasource.url}")
    private String dbURL;

    @Value("${mtl.db-readonly.username}")
    private String readonlyUsername;

    @Value("${spring.datasource.username}")
    private String primaryUsername;

    @Value("${mtl.db-readonly.password}")
    private String readonlyPassword;

    @Value("${spring.datasource.password}")
    private String primaryPassword;

    @Value("${spring.datasource.hikari.maximum-pool-size}")
    private int maximumPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:1}")
    private int minimumIdle;

    @Value("${mtl.db-readonly.maximum-pool-size:2}")
    private int readOnlyMaximumPoolSize;

    @Value("${mtl.db-readonly.minimum-idle:0}")
    private int readOnlyMinimumIdle;

    @Value("${spring.datasource.hikari.connection-init-sql:SET jit = off}")
    private String connectionInitSql;

    @Primary
    @Bean(name = "primaryDataSource")
    public DataSource primaryDataSource() {
        HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setJdbcUrl(dbURL);
        hikariConfig.setUsername(primaryUsername);
        hikariConfig.setPassword(primaryPassword);
        hikariConfig.setMaximumPoolSize(maximumPoolSize);
        hikariConfig.setMinimumIdle(minimumIdle);
        configureSession(hikariConfig, "primary");

        return new HikariDataSource(hikariConfig);
    }

    // Configure the secondary data source (read-only)
    @Bean(name = "readOnlyDataSource")
    @DependsOn("liquibase") // this needs to initialized, after liquibase did run, as liquibase just creates the user
    public DataSource readOnlyDataSource(@Qualifier("primaryDataSource") DataSource primary) {
        synchronizeReadOnlyPassword(primary);
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(dbURL);
        hikariConfig.setUsername(readonlyUsername);
        hikariConfig.setPassword(readonlyPassword);
        hikariConfig.setMaximumPoolSize(readOnlyMaximumPoolSize);
        hikariConfig.setMinimumIdle(readOnlyMinimumIdle);
        hikariConfig.setReadOnly(true);
        configureSession(hikariConfig, "read-only");
        return new HikariDataSource(hikariConfig);
    }

    private void synchronizeReadOnlyPassword(DataSource primary) {
        String password = readonlyPassword == null || readonlyPassword.isBlank() ? primaryPassword : readonlyPassword;
        try (var connection = primary.getConnection();
             var format = connection.prepareStatement("SELECT format('ALTER ROLE %I PASSWORD %L', ?, ?)")) {
            format.setString(1, readonlyUsername);
            format.setString(2, password);
            try (var result = format.executeQuery()) {
                if (!result.next()) throw new SQLException("Role password statement was not produced");
                try (var statement = connection.createStatement()) {
                    statement.execute(result.getString(1));
                }
            }
            readonlyPassword = password;
            log.info("Read-only database credential configured: role={}", readonlyUsername);
        } catch (SQLException error) {
            // JDBC error messages can include SQL literals; keep the credential out of logs.
            SQLException safeError = new SQLException("Read-only credential update failed", error.getSQLState(),
                    error.getErrorCode());
            safeError.setStackTrace(error.getStackTrace());
            log.error("Read-only database credential configuration failed: role={}, type={}, sqlState={}",
                    readonlyUsername, error.getClass().getSimpleName(), error.getSQLState(), safeError);
            throw new IllegalStateException("Cannot configure the read-only database credential", safeError);
        }
    }

    private void configureSession(HikariConfig config, String pool) {
        // These custom pools do not inherit Spring Boot's Hikari configuration.
        // LLVM compilation is unsuitable for the supported small-memory installs.
        config.setConnectionInitSql(connectionInitSql);
        log.info("Database pool configured: pool={}, maximumConnections={}, minimumIdle={}, sessionInitConfigured={}",
                pool, config.getMaximumPoolSize(), config.getMinimumIdle(),
                connectionInitSql != null && !connectionInitSql.isBlank());
    }

    @Primary
    @Bean(name = "namedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate primaryJdbcTemplate(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        return new NamedParameterJdbcTemplate(primaryDataSource);
    }

    @Bean(name = "readOnlyJdbcTemplate")
    public NamedParameterJdbcTemplate readOnlyJdbcTemplate(@Qualifier("readOnlyDataSource") DataSource readOnlyDataSource) {
        return new NamedParameterJdbcTemplate(readOnlyDataSource);
    }
}
