package com.x8ing.mtl.server.mtlserver.jobs.status;

import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype.ActivityTypeAutoClassifier;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.testsupport.NoScheduledJobs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.google-health.enabled=false", "mtl.garmin-sync.enabled=false"})
@Import(NoScheduledJobs.class)
@EnabledIfSystemProperty(named = "mtl.test.energy.jdbc-url", matches = "jdbc:postgresql:.*")
@Transactional
class ActivityTypeSummaryDatabaseIntegrationTest {
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    GpsTrackRepository tracks;
    @MockitoBean
    GPXDirectoryWatcherService watcher;
    @MockitoBean
    MediaIndexerService mediaIndexer;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getProperty("mtl.test.energy.jdbc-url"));
        properties.add("spring.datasource.username", () -> "review");
        properties.add("spring.datasource.password", () -> "review");
        properties.add("mtl.db-readonly.username", () -> "review");
        properties.add("mtl.db-readonly.password", () -> "review");
    }

    @Test
    void summaryPartitionsImportedTracksAndKeepsTheTotalStableAcrossClassification() {
        // The dedicated test database contains only synthetic records.
        jdbc.update("DELETE FROM gps_track");
        long pendingId = insert("SUCCESS", "IMPORTED", null, null);
        insert("SUCCESS", "IMPORTED", "AUTO_GUESS", null);
        insert("SUCCESS", "IMPORTED", "FAILED", "Synthetic software failure");
        insert("SUCCESS", "IMPORTED", "FAILED", ActivityTypeAutoClassifier.NO_DATA_POINTS_MESSAGE);
        insert("SUCCESS", "PLANNED", null, null);
        insert("FAILURE", "IMPORTED", null, null);
        var counts = counts();
        assertThat(counts.getPending()).isEqualTo(1);
        assertThat(counts.getDone()).isEqualTo(1);
        assertThat(counts.getFailed()).isEqualTo(2);
        assertThat(counts.getUnavailable()).isEqualTo(1);
        assertThat(total(counts)).isEqualTo(4);
        jdbc.update("UPDATE gps_track SET activity_type_source='AUTO_GUESS' WHERE id=?", pendingId);
        counts = counts();
        assertThat(counts.getPending()).isZero();
        assertThat(counts.getDone()).isEqualTo(2);
        assertThat(total(counts)).isEqualTo(4);
    }

    private GpsTrackRepository.ActivityTypeSummaryCounts counts() {
        return tracks.countActivityTypeSummary(ActivityTypeAutoClassifier.NO_DATA_POINTS_MESSAGE + "%");
    }

    private long total(GpsTrackRepository.ActivityTypeSummaryCounts counts) {
        return counts.getPending() + counts.getDone() + counts.getFailed();
    }

    private long insert(String loadStatus, String source, String activitySource, String detail) {
        return jdbc.queryForObject("""
                INSERT INTO gps_track(load_status,track_source,activity_type_source,activity_type_source_details)
                VALUES (?,?,?,?) RETURNING id
                """, Long.class, loadStatus, source, activitySource, detail);
    }
}
