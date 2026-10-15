package com.x8ing.mtl.server.mtlserver.jobs;

import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype.ActivityTypeAutoClassifier;
import com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype.ActivityTypeClassifierJob;
import com.x8ing.mtl.server.mtlserver.jobs.duplicate.DuplicateDetectorAtomicWorker;
import com.x8ing.mtl.server.mtlserver.jobs.duplicate.DuplicateDetectorJob;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BackgroundConnectionRetryDatabaseIntegrationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void busyPoolLeavesTracksPendingAndBothJobsResumeWithoutChangingPayloads(boolean classification) throws Exception {
        String url = System.getProperty("spring.datasource.url", "jdbc:postgresql://host.docker.internal:25441/mtl");
        String user = System.getProperty("spring.datasource.username", "mtl_test");
        String password = System.getProperty("spring.datasource.password", "synthetic-test-only");
        var administrative = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        String table = "public.test_background_retry_" + UUID.randomUUID().toString().replace("-", "");
        administrative.execute("CREATE TABLE " + table + " (id bigint PRIMARY KEY,state text NOT NULL,payload jsonb NOT NULL)");
        try {
            administrative.execute("INSERT INTO " + table + " VALUES (101,'PENDING','{\"synthetic\":1}'),(102,'PENDING','{\"synthetic\":2}')");
            var payloads = administrative.queryForList("SELECT id,payload::text FROM " + table + " ORDER BY id");
            var config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(password);
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(250);
            config.setConnectionInitSql("SET jit=off");
            try (var pool = new HikariDataSource(config)) {
                var jdbc = new JdbcTemplate(pool);
                var transaction = new TransactionTemplate(new DataSourceTransactionManager(pool));
                var repository = mock(GpsTrackRepository.class);
                var pending = (java.util.function.Supplier<List<Long>>) () -> administrative.queryForList(
                        "SELECT id FROM " + table + " WHERE state='PENDING' ORDER BY id", Long.class);
                BooleanSupplier job;
                if (classification) {
                    var selector = mock(GpsTrackVariantSelector.class);
                    var worker = mock(ActivityTypeAutoClassifier.class);
                    when(repository.findIdsPendingActivityClassification(any())).thenAnswer(call -> pending.get());
                    when(selector.pointsForMetrics(anyLong())).thenReturn(List.of(new GpsTrackDataPoint()));
                    when(worker.classifyActivity(anyLong(), anyList())).thenAnswer(call -> {
                        transaction.executeWithoutResult(status -> jdbc.update("UPDATE " + table + " SET state='DONE' WHERE id=?", call.getArgument(0, Long.class)));
                        return new ActivityTypeAutoClassifier.ClassificationResult(null, GpsTrack.ACTIVITY_TYPE.WALKING, true);
                    });
                    doAnswer(call -> administrative.update("UPDATE " + table + " SET state='FAILED' WHERE id=?", call.getArgument(0, Long.class)))
                            .when(worker).markFailed(anyLong(), anyString());
                    job = new ActivityTypeClassifierJob(repository, selector, worker, mock(SystemLogService.class),
                            mock(IndexerStatusService.class), new ShutdownSignal())::run;
                } else {
                    var worker = mock(DuplicateDetectorAtomicWorker.class);
                    when(repository.findIdsByDuplicateStatusExcludingPlanned(any(), any())).thenAnswer(call -> pending.get());
                    doAnswer(call -> {
                        transaction.executeWithoutResult(status -> jdbc.update("UPDATE " + table + " SET state='DONE' WHERE id=?", call.getArgument(0, Long.class)));
                        return null;
                    }).when(worker).processOne(anyLong(), any(), anyDouble());
                    doAnswer(call -> administrative.update("UPDATE " + table + " SET state='FAILED' WHERE id=?", call.getArgument(0, Long.class)))
                            .when(worker).markFailed(anyLong(), anyString());
                    var duplicateJob = new DuplicateDetectorJob(repository, worker, mock(IndexerStatusService.class), new ShutdownSignal());
                    ReflectionTestUtils.setField(duplicateJob, "timeTolerance", Duration.ofMinutes(5));
                    ReflectionTestUtils.setField(duplicateJob, "distanceTolerance", 100d);
                    job = duplicateJob::run;
                }
                try (Connection occupied = pool.getConnection()) {
                    assertThat(job.getAsBoolean()).isFalse();
                    assertThat(administrative.queryForList("SELECT state FROM " + table + " ORDER BY id", String.class))
                            .containsExactly("PENDING", "PENDING");
                    assertThat(administrative.queryForList("SELECT id,payload::text FROM " + table + " ORDER BY id")).isEqualTo(payloads);
                }
                job.getAsBoolean();
                assertThat(administrative.queryForList("SELECT state FROM " + table + " ORDER BY id", String.class))
                        .containsExactly("DONE", "DONE");
                assertThat(administrative.queryForList("SELECT id,payload::text FROM " + table + " ORDER BY id")).isEqualTo(payloads);
                assertThat(pending.get()).isEmpty();
            }
        } finally {
            administrative.execute("DROP TABLE " + table);
        }
    }
}
