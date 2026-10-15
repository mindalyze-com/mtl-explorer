package com.x8ing.mtl.server.mtlserver.jobs.status;

import com.x8ing.mtl.server.mtlserver.energy.EnergyRecalculationQueue;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaCorrelationRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.testsupport.NoScheduledJobs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.google-health.enabled=false", "mtl.garmin-sync.enabled=false"})
@Import(NoScheduledJobs.class)
@EnabledIfSystemProperty(named = "mtl.test.processing.jdbc-url", matches = "jdbc:postgresql:.*")
class ProcessingStatusDatabaseIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired JobStatusService status;
    @Autowired EnergyRecalculationQueue energy;
    @Autowired MediaCorrelationRepository media;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean GPXDirectoryWatcherService watcher;
    @MockitoBean MediaIndexerService mediaIndexer;
    @MockitoBean IndexerStatusService indexers;
    private final List<Long> ids = new ArrayList<>();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getProperty("mtl.test.processing.jdbc-url"));
        properties.add("spring.datasource.username", () -> "review");
        properties.add("spring.datasource.password", () -> "review");
        properties.add("mtl.db-readonly.username", () -> "review");
        properties.add("mtl.db-readonly.password", () -> "review");
    }

    @AfterEach
    void cleanup() {
        ids.forEach(id -> jdbc.update("DELETE FROM gps_track WHERE id=?", id));
        jdbc.update("DELETE FROM media_correlation_track_work WHERE NOT EXISTS (SELECT 1 FROM gps_track WHERE id=track_id)");
    }

    @Test
    void reportsPartialReadinessUserActionsRetriesAndCurrentWholeJobGates() {
        assertThat(media.hasReadyWork()).isFalse();
        long unchecked = seed("NOT_CHECKED_YET", null, null, null);
        long checked = seed("UNIQUE", null, null, null);
        long complete = seed("UNIQUE", "RUNNING", "USER_SET", 0.0);
        assertThat(media.hasReadyWork()).isTrue();
        var activity = job("activityType");
        assertThat(activity.work()).isEqualTo(new ProcessingDetails.Work(1, 1, 0));
        assertThat(activity.state()).isEqualTo(ProcessingDetails.State.QUEUED);
        var calculation = job("energy");
        assertThat(calculation.work()).isEqualTo(new ProcessingDetails.Work(0, 2, 0));
        assertThat(calculation.attentionRequired()).isZero();
        assertThat(calculation.unavailableReasons().missingActivity()).isZero();
        assertThat(job("exploration").work().waiting()).isEqualTo(3);
        assertThat(job("mediaCorrelation").blockedBy()).isEmpty();

        jdbc.update("UPDATE gps_track SET activity_type_source='FAILED',activity_type_source_details='Unknown activity' WHERE id=?", checked);
        calculation = job("energy");
        assertThat(calculation.attentionRequired()).isEqualTo(1);
        assertThat(calculation.blockedBy()).anySatisfy(blocker -> {
            assertThat(blocker.reason()).isEqualTo("MISSING_ACTIVITY");
            assertThat(blocker.resolution()).isEqualTo(ProcessingDetails.Resolution.USER_ACTION);
        });
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> energy.request(complete));
        jdbc.update("UPDATE track_energy_recalculation SET next_attempt_at=now()+interval '1 minute',last_error='SyntheticRetry' WHERE track_id=?", complete);
        calculation = job("energy");
        assertThat(calculation.state()).isEqualTo(ProcessingDetails.State.RETRYING);
        // The track that needs a user-selected activity is attention, not waiting work.
        assertThat(calculation.work()).isEqualTo(new ProcessingDetails.Work(0, 1, 1));
        assertThat(calculation.nextRetryAt()).isNotNull();
        jdbc.update("DELETE FROM track_energy_recalculation WHERE track_id=?", complete);
        jdbc.update("UPDATE gps_track SET activity_type='RUNNING',activity_type_source='USER_SET',energy_net_total_wh=0.0 WHERE id=?", unchecked);
        calculation = job("energy");
        assertThat(calculation.state()).isEqualTo(ProcessingDetails.State.COMPLETE);
        assertThat(calculation.progressPercent()).isEqualTo(100);
        assertThat(calculation.attentionRequired()).isEqualTo(1);
        assertThat(calculation.unavailable()).isEqualTo(1);
        jdbc.update("UPDATE gps_track SET duplicate_status='UNIQUE' WHERE id=?", unchecked);
        assertThat(job("exploration").state()).isEqualTo(ProcessingDetails.State.QUEUED);
        when(indexers.hasIndexPendingWork("GPS")).thenReturn(true);
        assertThat(job("exploration").state()).isEqualTo(ProcessingDetails.State.WAITING);
        assertThat(job("mediaCorrelation").state()).isEqualTo(ProcessingDetails.State.WAITING);
        for (var report : status.getJobSummaries()) {
            assertThat(report.work().ready() + report.work().waiting() + report.work().retrying()).isEqualTo(report.pending());
        }
    }

    private JobStatusService.JobSummaryDto job(String id) {
        return status.getJobSummaries().stream().filter(job -> id.equals(job.job())).findFirst().orElseThrow();
    }

    private long seed(String duplicate, String activity, String source, Double energy) {
        long id = jdbc.queryForObject("""
                INSERT INTO gps_track(load_status,track_source,duplicate_status,activity_type,activity_type_source,
                                      energy_net_total_wh,exploration_status,timing_unreliable)
                VALUES('SUCCESS','IMPORTED',?,?,?,?,'SCHEDULED',false) RETURNING id
                """, Long.class, duplicate, activity, source, energy);
        ids.add(id);
        return id;
    }
}
