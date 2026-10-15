package com.x8ing.mtl.server.mtlserver.googlehealth.simulation;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.exercise.ExerciseRouteWork;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataSourceResolver;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.PointMapper;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncCoordinator;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real PostgreSQL mirror and importer, assembled directly without an application context or HTTP.
 */
public final class HistoricalHeartRateImportSimulation implements AutoCloseable {
    static final String ACCOUNT = "synthetic-history-simulation";
    private final HikariDataSource dataSource;
    final JdbcTemplate jdbc;
    final MirrorRepository repository;
    final DataSourceTransactionManager transactions;
    private final TransactionTiming timing = new TransactionTiming();

    public HistoricalHeartRateImportSimulation() throws Exception {
        String url = System.getenv("MTL_HEALTH_TEST_DB_URL");
        if (url == null || url.isBlank()) throw new IllegalArgumentException("MTL_HEALTH_TEST_DB_URL is required");
        HikariConfig pool = new HikariConfig();
        pool.setJdbcUrl(url);
        pool.setUsername(System.getenv().getOrDefault("MTL_HEALTH_TEST_DB_USER", "health_simulation"));
        pool.setPassword(System.getenv().getOrDefault("MTL_HEALTH_TEST_DB_PASSWORD", "health_simulation"));
        pool.setConnectionInitSql("SET jit = off");
        pool.setMaximumPoolSize(3);
        dataSource = new HikariDataSource(pool);
        jdbc = new JdbcTemplate(dataSource);
        try {
            String database = jdbc.queryForObject("SELECT current_database()", String.class);
            if (database == null || !database.startsWith("mtl_health_simulation")) {
                throw new IllegalArgumentException("Simulation requires a disposable database named mtl_health_simulation...");
            }
            SpringLiquibase migrations = new SpringLiquibase();
            migrations.setDataSource(dataSource);
            migrations.setChangeLog("classpath:db/changelog/db.changelog-master.xml");
            migrations.afterPropertiesSet();
        } catch (Exception exception) {
            dataSource.close();
            throw exception;
        }
        repository = new MirrorRepository(jdbc, new ObjectMapper());
        transactions = new DataSourceTransactionManager(dataSource);
        transactions.setTransactionExecutionListeners(List.of(timing));
    }

    public void reset() {
        transaction(() -> {
            clearMirror();
            repository.saveConnected(ACCOUNT, Set.of(OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS), "synthetic-unused-token", null);
        });
    }

    public void clear() {
        transaction(this::clearMirror);
    }

    /**
     * Remove old heap/index contents so each timed import starts with the same empty schema.
     */
    public void clearForBenchmark() {
        transaction(() -> jdbc.execute("TRUNCATE google_health.connection, google_health.history_audit, "
                                       + "google_health.history_work, google_health.recent_failure, "
                                       + "google_health.recent_pass, google_health.sync_control RESTART IDENTITY CASCADE"));
    }

    public void resetForBenchmark() {
        clearForBenchmark();
        transaction(() -> repository.saveConnected(ACCOUNT, Set.of(OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS), "synthetic-unused-token", null));
    }

    private void clearMirror() {
        repository.disconnect();
        repository.deleteMirrorRows();
        jdbc.update("DELETE FROM google_health.connection WHERE id=1");
    }

    public void transaction(Runnable action) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> action.run());
    }

    public Cycle runCycle(SyntheticGoogleHealthProvider provider, LocalDate historyStart) {
        return runCycle(provider, historyStart, Duration.ofSeconds(Long.getLong("mtl.health.simulation.cycle-seconds", 60)));
    }

    public Cycle runCycle(SyntheticGoogleHealthProvider provider, LocalDate historyStart, Duration budget) {
        ConnectorProperties settings = new ConnectorProperties();
        settings.setClientId("synthetic-unused-client");
        settings.setClientSecret("synthetic-unused-secret");
        settings.setHistoryStartDate(historyStart);
        settings.setSyncInterval(budget);
        settings.setCycleDeadlineMargin(Duration.ofSeconds(1));
        settings.setHistoryStreamQuantum(Duration.ofSeconds(10));
        settings.setRequestTimeout(Duration.ofMillis(1));
        Clock clock = Clock.fixed(provider.dataset().end().plus(Duration.ofDays(settings.getRecentWindow().getDays())), ZoneOffset.UTC);
        PointMapper mapper = new PointMapper(new DataSourceResolver(repository));
        ExerciseRouteWork routes = progress -> {
            throw new IllegalStateException("Heart-rate simulation must not run exercise route work");
        };
        SimulationProgress progress = new SimulationProgress();
        // Construct a new worker each cycle to exercise durable resumption.
        SyncService importer = new SyncService(settings, repository, provider, mapper, routes, transactions, clock);
        timing.reset();
        long started = System.nanoTime();
        SyncService.SyncResult result = importer.run(SyncCoordinator.JobKind.REGULAR, progress);
        long rows = jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample", Long.class);
        long days = jdbc.queryForObject("SELECT count(*) FROM google_health.history_audit WHERE account_id=? "
                                        + "AND data_type='heart-rate' AND representation='HEART_RATE_SAMPLE' AND outcome IN ('DATA','EMPTY')", Long.class, ACCOUNT);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        return new Cycle(result, elapsed, provider.readTime(), Duration.ofNanos(timing.nanos), timing.commits,
                rows, days, provider.readsByPurpose());
    }

    public record Cycle(SyncService.SyncResult result, Duration elapsed, Duration providerTime,
                        Duration transactionTime, long commits, long rows, long finalizedDays, Map<String, Long> readsByPurpose) {
    }

    /**
     * Measures complete transactions, including mapping and SQL, rather than claiming pure database execution time.
     */
    private static final class TransactionTiming implements TransactionExecutionListener {
        private long started, nanos, commits;

        void reset() {
            nanos = commits = 0;
        }

        @Override
        public void beforeBegin(TransactionExecution execution) {
            started = System.nanoTime();
        }

        @Override
        public void afterCommit(TransactionExecution execution, Throwable failure) {
            nanos += System.nanoTime() - started;
            if (failure == null) commits++;
        }

        @Override
        public void afterRollback(TransactionExecution execution, Throwable failure) {
            nanos += System.nanoTime() - started;
        }
    }

    private static final class SimulationProgress implements SyncCoordinator.Progress {
        @Override
        public void setTotalUnits(long units) {
        }

        @Override
        public void addTotalUnits(long units) {
        }

        @Override
        public void startUnit(String dataType, String representation, Instant start, Instant end) {
        }

        @Override
        public void pageCompleted(long imported) {
        }

        @Override
        public void completeUnit() {
        }

        @Override
        public void failUnit(String error) {
        }

        @Override
        public void unitSkipped(String dataType, String reason) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
