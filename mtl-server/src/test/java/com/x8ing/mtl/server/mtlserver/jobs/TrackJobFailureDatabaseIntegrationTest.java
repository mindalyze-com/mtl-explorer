package com.x8ing.mtl.server.mtlserver.jobs;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype.ActivityTypeAutoClassifier;
import com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype.ActivityTypeClassifierJob;
import com.x8ing.mtl.server.mtlserver.jobs.duplicate.DuplicateDetectorAtomicWorker;
import com.x8ing.mtl.server.mtlserver.jobs.duplicate.DuplicateDetectorJob;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Real rollback and independent failure commits, using synthetic rows in a dedicated local database.
 */
@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.google-health.enabled=false", "mtl.garmin-sync.enabled=false"})
@Import(NoScheduledJobs.class)
@EnabledIfSystemProperty(named = "mtl.test.jobs.jdbc-url", matches = "jdbc:postgresql:.*")
class TrackJobFailureDatabaseIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getProperty("mtl.test.jobs.jdbc-url"));
        properties.add("spring.datasource.username", () -> System.getProperty("mtl.test.jobs.jdbc-user"));
        properties.add("spring.datasource.password", () -> System.getProperty("mtl.test.jobs.jdbc-password"));
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DuplicateDetectorJob duplicates;
    @Autowired
    DuplicateDetectorAtomicWorker duplicateWorker;
    @Autowired
    ActivityTypeClassifierJob activities;
    @Autowired
    ActivityTypeAutoClassifier classifier;
    @MockitoSpyBean
    GpsTrackRepository tracks;
    @MockitoBean
    GpsTrackVariantSelector selector;
    @MockitoBean
    ResourceIntensiveJobGuard scheduledJobs;
    @MockitoBean
    IndexerStatusService indexers;
    @MockitoBean
    GPXDirectoryWatcherService gpsWatcher;
    @MockitoBean
    MediaIndexerService mediaIndexer;
    private final List<Long> fixtures = new ArrayList<>();

    @AfterEach
    void removeSyntheticTracks() {
        for (Long id : fixtures) jdbc.update("DELETE FROM gps_track WHERE id = ?", id);
    }

    @Test
    void duplicateSqlFailureRollsBackAndCommitsFailedWhileLaterTracksFinish() {
        long poison = track("NOT_CHECKED_YET");
        long healthy = track("NOT_CHECKED_YET");
        doAnswer(call -> {
            jdbc.update("UPDATE gps_track SET activity_type = 'CAR' WHERE id = ?", poison);
            jdbc.execute("SELECT 1 / 0");
            return null;
        }).when(tracks).findSimilarTracksWithinTimeRangeForTrack(any(), any(), anyDouble(), eq(poison));

        duplicates.run();
        duplicates.run();

        var failed = tracks.findById(poison).orElseThrow();
        assertThat(failed.getDuplicateStatus()).isEqualTo(GpsTrack.DUPLICATE_CHECK_STATUS.FAILED);
        assertThat(failed.getDuplicateDetails()).contains("failed", "See server logs");
        assertThat(failed.getActivityType()).isNull();
        assertThat(tracks.findById(healthy).orElseThrow().getDuplicateStatus()).isEqualTo(GpsTrack.DUPLICATE_CHECK_STATUS.UNIQUE);
        assertThat(tracks.findByDuplicateStatusExcludingPlanned(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET)).isEmpty();
        verify(tracks, times(1)).findSimilarTracksWithinTimeRangeForTrack(any(), any(), anyDouble(), eq(poison));
    }

    @Test
    void activitySqlFailureRollsBackAndCommitsFailedWhileLaterTracksFinish() {
        long poison = track("UNIQUE");
        long healthy = track("UNIQUE");
        GpsTrackDataPoint point = new GpsTrackDataPoint();
        point.setSpeedInKmhMovingWindow(5d);
        when(selector.pointsForMetrics(anyLong())).thenReturn(List.of(point));
        doAnswer(call -> {
            jdbc.update("UPDATE gps_track SET duplicate_status = 'FAILED' WHERE id = ?", poison);
            jdbc.execute("SELECT 1 / 0");
            return 0;
        }).when(tracks).updateActivityClassificationIfPending(eq(poison), any(), eq(GpsTrack.ACTIVITY_TYPE_SOURCE.AUTO_GUESS), anyString());

        activities.run();
        activities.run();

        var failed = tracks.findById(poison).orElseThrow();
        assertThat(failed.getActivityTypeSource()).isEqualTo(GpsTrack.ACTIVITY_TYPE_SOURCE.FAILED);
        assertThat(failed.getActivityType()).isNull();
        assertThat(failed.getActivityTypeSourceDetails()).contains("failed", "See server logs");
        assertThat(failed.getDuplicateStatus()).isEqualTo(GpsTrack.DUPLICATE_CHECK_STATUS.UNIQUE);
        assertThat(tracks.findById(healthy).orElseThrow().getActivityTypeSource()).isEqualTo(GpsTrack.ACTIVITY_TYPE_SOURCE.AUTO_GUESS);
        assertThat(tracks.findIdsPendingActivityClassification()).isEmpty();
        assertThat(tracks.countActivityTypeDone()).isEqualTo(1);
        assertThat(tracks.countActivityTypeFailed()).isEqualTo(1);
    }

    @Test
    void failureMarkersDoNotOverwriteCompletedOrUserSelectedResults() {
        long id = track("UNIQUE");
        jdbc.update("UPDATE gps_track SET activity_type = 'RUNNING', activity_type_source = 'USER_SET' WHERE id = ?", id);
        duplicateWorker.markFailed(id, "Synthetic failure");
        classifier.markFailed(id, "Synthetic failure");
        var track = tracks.findById(id).orElseThrow();
        assertThat(track.getDuplicateStatus()).isEqualTo(GpsTrack.DUPLICATE_CHECK_STATUS.UNIQUE);
        assertThat(track.getActivityType()).isEqualTo(GpsTrack.ACTIVITY_TYPE.RUNNING);
        assertThat(track.getActivityTypeSource()).isEqualTo(GpsTrack.ACTIVITY_TYPE_SOURCE.USER_SET);
    }

    private long track(String duplicateStatus) {
        long id = jdbc.queryForObject("""
                INSERT INTO gps_track(load_status, duplicate_status, track_source)
                VALUES('SUCCESS', ?, 'IMPORTED') RETURNING id
                """, Long.class, duplicateStatus);
        fixtures.add(id);
        return id;
    }
}
