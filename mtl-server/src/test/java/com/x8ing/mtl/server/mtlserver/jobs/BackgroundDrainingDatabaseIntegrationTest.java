package com.x8ing.mtl.server.mtlserver.jobs;

import com.x8ing.mtl.server.mtlserver.MtlServerApplication;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.energy.EnergyRecalculationQueue;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.testsupport.NoScheduledJobs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic records in the explicitly selected disposable database only. */
@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.google-health.enabled=false", "mtl.garmin-sync.enabled=false"})
@Import({NoScheduledJobs.class, BackgroundDrainingDatabaseIntegrationTest.Scheduler.class})
@EnabledIfSystemProperty(named = "mtl.test.energy.jdbc-url", matches = "jdbc:postgresql:.*")
class BackgroundDrainingDatabaseIntegrationTest {
    private static final String TRACK_PREFIX = "Synthetic draining test ";
    private static final int DUE_TRACKS = 270;
    private static final int DELAYED_TRACKS = 50;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EnergyRecalculationQueue energy;
    @Autowired
    GpsTrackRepository tracks;
    @Autowired
    MtlServerApplication application;
    @Autowired
    ThreadPoolTaskScheduler scheduler;
    @MockitoBean
    GPXDirectoryWatcherService watcher;
    @MockitoBean
    MediaIndexerService mediaIndexer;

    @TestConfiguration(proxyBeanMethods = false)
    static class Scheduler {
        @Bean
        ThreadPoolTaskScheduler taskScheduler() {
            var scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(2);
            return scheduler;
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getProperty("mtl.test.energy.jdbc-url"));
        properties.add("spring.datasource.username", () -> "review");
        properties.add("spring.datasource.password", () -> "review");
        properties.add("mtl.db-readonly.username", () -> "review");
        properties.add("mtl.db-readonly.password", () -> "review");
    }

    @AfterEach
    void cleanup() throws Exception {
        await(() -> scheduler.getActiveCount() == 0);
        jdbc.update("DELETE FROM gps_track WHERE track_name LIKE ?", TRACK_PREFIX + "%");
    }

    @Test
    void energyDrainsMultipleBatchesAndLeavesFutureRetriesPending() throws Exception {
        seed(DUE_TRACKS + DELAYED_TRACKS, "USER_SET", "UNIQUE");
        jdbc.update("""
                INSERT INTO track_energy_recalculation(track_id,generation,requested_at,next_attempt_at)
                SELECT id,1,now(),CASE WHEN row_number() OVER (ORDER BY id) <= ?
                         THEN now() ELSE now()+interval '1 hour' END
                  FROM gps_track WHERE track_name LIKE ?
                """, DUE_TRACKS, TRACK_PREFIX + "%");
        long started = System.nanoTime();
        energy.processPending();
        await(() -> jdbc.queryForObject("""
                SELECT count(*) FROM track_energy_recalculation request JOIN gps_track track ON track.id=request.track_id
                 WHERE track.track_name LIKE ? AND request.next_attempt_at<=now()
                """, Long.class, TRACK_PREFIX + "%") == 0);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM track_energy_recalculation request JOIN gps_track track ON track.id=request.track_id
                 WHERE track.track_name LIKE ?
                """, Long.class, TRACK_PREFIX + "%")).isEqualTo(DELAYED_TRACKS);
        energy.processPending();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM track_energy_recalculation", Long.class)).isEqualTo(DELAYED_TRACKS);
    }

    @Test
    void duplicateAndClassifierSelectionsAreBoundedAndClassificationContinuesAfterOneTrigger() throws Exception {
        seed(230, null, "NOT_CHECKED_YET");
        assertThat(tracks.findIdsByDuplicateStatusExcludingPlanned(
                GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET, PageRequest.of(0, 100))).hasSize(100);
        jdbc.update("UPDATE gps_track SET duplicate_status='UNIQUE' WHERE track_name LIKE ?", TRACK_PREFIX + "%");
        assertThat(tracks.findIdsPendingActivityClassification(PageRequest.of(0, 100))).hasSize(100);
        application.scheduleJobClassifyActivityType();
        await(() -> jdbc.queryForObject("""
                SELECT count(*) FROM gps_track WHERE track_name LIKE ? AND activity_type_source IS NULL
                """, Long.class, TRACK_PREFIX + "%") == 0);
        // Missing observations are a terminal source-data outcome, not an endless software retry.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gps_track WHERE track_name LIKE ? AND activity_type_source='FAILED'",
                Long.class, TRACK_PREFIX + "%")).isEqualTo(230);
    }

    private void seed(int count, String activitySource, String duplicateStatus) {
        jdbc.update("""
                INSERT INTO gps_track(load_status,track_source,activity_type_source,activity_type,timing_unreliable,
                                      duplicate_status,exploration_status,track_name)
                SELECT 'SUCCESS','IMPORTED',?,'WALKING',true,?,'NOT_SCHEDULED',? || i FROM generate_series(1,?) i
                """, activitySource, duplicateStatus, TRACK_PREFIX, count);
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
