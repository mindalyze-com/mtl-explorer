package com.x8ing.mtl.server.mtlserver.googlehealth.demo;

import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.energy.EnergyRecalculationQueue;
import com.x8ing.mtl.server.mtlserver.googlehealth.application.GoogleHealthMirrorCache;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.ResourceIntensiveJobGuard;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingContext;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails;
import com.x8ing.mtl.server.mtlserver.testsupport.NoScheduledJobs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.testsupport.BackgroundJobTestSupport.directRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(properties = {"mtl.demo-mode=true", "mtl.demo-health.enabled=true", "mtl.demo-health.recent-days=7",
        "mtl.planner.enabled=false", "mtl.garmin-sync.enabled=false", "mtl.google-health.enabled=false"})
@Import(NoScheduledJobs.class)
@EnabledIfSystemProperty(named = "mtl.test.energy.jdbc-url", matches = "jdbc:postgresql:.*")
@Transactional
class DemoHealthGenerationPrerequisitesDatabaseIntegrationTest {
    @Autowired DemoHealthAccess access;
    @Autowired DemoHealthProperties properties;
    @Autowired DemoHealthRepository repository;
    @Autowired DemoHealthWorker worker;
    @Autowired GpsTrackRepository tracks;
    @Autowired EnergyRecalculationQueue energy;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean GPXDirectoryWatcherService gps;
    @MockitoBean MediaIndexerService media;

    @Test
    void emptyEnergyQueueDoesNotAllowGenerationBeforeFinalActivitiesAndExclusions() {
        LocalDate date = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        long included = fixture(date), excluded = fixture(date), duplicate = fixture(date);
        var indexers = mock(IndexerStatusService.class);
        var context = ProcessingContext.indexes(indexers);
        var job = newJob(indexers);

        assertThat(repository.trackCalculationPending()).isFalse();
        job.runBatch();
        assertThat(blockers(job, context)).contains("duplicate", "activityType");
        assertNoHealthRecords();

        jdbc.update("UPDATE gps_track SET duplicate_status='UNIQUE' WHERE id=?", included);
        jdbc.update("UPDATE gps_track SET duplicate_status='EXCLUDED' WHERE id=?", excluded);
        jdbc.update("UPDATE gps_track SET duplicate_status='DUPLICATE' WHERE id=?", duplicate);
        job.runBatch();
        assertThat(blockers(job, context)).containsExactly("activityType");
        assertNoHealthRecords();

        jdbc.update("UPDATE gps_track SET activity_type='BICYCLE', activity_type_source='AUTO_GUESS'");
        energy.request(included);
        job.runBatch();
        assertThat(blockers(job, context)).containsExactly("energy");
        assertNoHealthRecords();

        jdbc.update("DELETE FROM track_energy_recalculation WHERE track_id=?", included);
        job.runBatch();
        assertThat(job.summary().progressPercent()).isEqualTo(100);
        assertThat(jdbc.queryForList("SELECT gps_track_id FROM google_health.route_ownership WHERE present", Long.class))
                .containsExactly(included);
        assertThat(jdbc.queryForObject("SELECT raw_json::text FROM google_health.data_point WHERE data_type='exercise'", String.class))
                .contains("\"BICYCLE\"").doesNotContain("\"DRIVING\"");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample", Long.class)).isPositive();

        var records = jdbc.queryForList("SELECT id, raw_json::text, updated_at, last_seen_run FROM google_health.data_point ORDER BY id");
        newJob(indexers).runBatch();
        assertThat(jdbc.queryForList("SELECT id, raw_json::text, updated_at, last_seen_run FROM google_health.data_point ORDER BY id"))
                .isEqualTo(records);
    }

    private DemoHealthJob newJob(IndexerStatusService indexers) {
        return new DemoHealthJob(access, properties, indexers, new ResourceIntensiveJobGuard(), repository, worker,
                mock(GoogleHealthMirrorCache.class), mock(DemoHealthShowcase.class), tracks,
                new ShutdownSignal(), directRunner());
    }

    private List<String> blockers(DemoHealthJob job, ProcessingContext context) {
        return job.prerequisites(context).stream().map(ProcessingDetails.Blocker::id).toList();
    }

    private void assertNoHealthRecords() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.data_point", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample", Long.class)).isZero();
    }

    private long fixture(LocalDate date) {
        var start = date.atTime(9, 0).toInstant(ZoneOffset.UTC);
        long id = jdbc.queryForObject("""
                INSERT INTO gps_track (load_status, track_source, duplicate_status, start_date, end_date)
                VALUES ('SUCCESS', 'IMPORTED', 'NOT_CHECKED_YET', ?, ?) RETURNING id
                """, Long.class, Timestamp.from(start), Timestamp.from(start.plusSeconds(600)));
        long data = jdbc.queryForObject("INSERT INTO gps_track_data (gps_track_id, track_type) VALUES (?, 'RAW_OUTLIER_CLEANED') RETURNING id", Long.class, id);
        for (int second = 0; second <= 600; second += 15) {
            jdbc.update("""
                    INSERT INTO gps_track_data_points (gps_track_data_id, moving_window_in_sec, point_index, point_index_max,
                        point_timestamp, distance_in_meter_since_start, speed_in_kmh_moving_window, motion_state, point_long_lat)
                    VALUES (?, 30, ?, 40, ?, ?, 20, 'MOVING', ST_SetSRID(ST_MakePoint(0, 0), 4326))
                    """, data, second / 15, Timestamp.from(start.plusSeconds(second)), second * 20 / 3.6);
        }
        return id;
    }
}
