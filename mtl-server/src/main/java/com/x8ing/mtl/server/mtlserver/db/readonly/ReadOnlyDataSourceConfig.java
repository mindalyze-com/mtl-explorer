package com.x8ing.mtl.server.mtlserver.db.readonly;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * For the dynamic grouping dynamic SQL is used.
 * <p>
 * For security reasons, we must avoid that such SQL can modify (delete, change) data in the DB.
 * We could either try to parse (very difficult against advanced attacks) or we create a read only user to the DB
 * and use that user for these queries. We follow that later approach.
 */
@Configuration
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

    @Primary
    @Bean(name = "primaryDataSource")
    public DataSource primaryDataSource() {
        HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setJdbcUrl(dbURL);
        hikariConfig.setUsername(primaryUsername);
        hikariConfig.setPassword(primaryPassword);
        hikariConfig.setMaximumPoolSize(maximumPoolSize);

        return new HikariDataSource(hikariConfig);
    }

    // Configure the secondary data source (read-only)
    @Bean(name = "readOnlyDataSource")
    @DependsOn("liquibase") // this needs to initialized, after liquibase did run, as liquibase just creates the user
    public DataSource readOnlyDataSource() {
        return DataSourceBuilder.create()
                .url(dbURL)
                .username(readonlyUsername)
                .password(readonlyPassword)
                .build();
    }

    @Bean(name = "readOnlyJdbcTemplate")
    public NamedParameterJdbcTemplate readOnlyJdbcTemplate(DataSource readOnlyDataSource) {
        return new NamedParameterJdbcTemplate(readOnlyDataSource);
    }
}
