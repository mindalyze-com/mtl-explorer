package com.x8ing.mtl.server.mtlserver.db.repository.gps;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.GpsTrackStatistics;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.duplicate.DuplicateDetectorJob;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.FilterExecutionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "mtl.planner.enabled=false",
        "mtl.media-correlation.initial-delay=PT1H",
        "mtl.media-correlation.run-schedule=PT1H",
        "mtl.indexer.gps.live-watch-enabled=false",
        "mtl.indexer.media.live-watch-enabled=false"
})
class PendingTrackVisibilityDatabaseIntegrationTest {

    private static final String TRACK_NAME_PREFIX = "Synthetic pending visibility ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GpsTrackRepository repository;

    @Autowired
    private FilterExecutionService filterExecutionService;

    @MockitoBean
    private GPXDirectoryWatcherService gpxDirectoryWatcherService;

    @MockitoBean
    private MediaIndexerService mediaIndexerService;

    @MockitoBean
    private DuplicateDetectorJob duplicateDetectorJob;

    @BeforeEach
    @AfterEach
    void removeSyntheticTracks() {
        jdbcTemplate.update("DELETE FROM gps_track WHERE track_name LIKE ?", TRACK_NAME_PREFIX + "%");
    }

    @Test
    void exposesOnlySuccessfulImportedUniqueAndPendingTracksUntilDuplicateDetectionSettles() {
        long unique = insertTrack("unique", 1, "SUCCESS", "UNIQUE", "IMPORTED", 171.0, 81.0);
        long pending = insertTrack("pending", 2, "SUCCESS", "NOT_CHECKED_YET", "IMPORTED", 173.0, 83.0);
        long duplicate = insertTrack("duplicate", 3, "SUCCESS", "DUPLICATE", "IMPORTED", 174.0, 84.0);
        long excluded = insertTrack("excluded", 4, "SUCCESS", "EXCLUDED", "IMPORTED", 174.0, 84.0);
        long failed = insertTrack("failed", 5, "FAILED", "NOT_CHECKED_YET", "IMPORTED", 174.0, 84.0);
        long incomplete = insertTrack("incomplete", 6, null, "NOT_CHECKED_YET", "IMPORTED", 174.0, 84.0);
        long planned = insertTrack("planned", 7, "SUCCESS", "NOT_CHECKED_YET", "PLANNED", 174.0, 84.0);
        Long[] allIds = {unique, pending, duplicate, excluded, failed, incomplete, planned};

        assertVisibleFilterIds(unique, pending, duplicate, excluded, failed, incomplete, planned);
        assertVisibleRepositoryResults(unique, pending, allIds);

        jdbcTemplate.update(
                "UPDATE gps_track SET duplicate_status = 'DUPLICATE', duplicate_of = ? WHERE id = ?",
                unique,
                pending);

        assertThat(filterExecutionService.executeStandardFilter().asIdList())
                .contains(unique)
                .doesNotContain(pending);
        assertThat(filterExecutionService.executeOptionalFilterName("TracksByYear", null).asIdList())
                .contains(unique)
                .doesNotContain(pending);
        assertThat(statisticTrackCount(allIds)).isEqualTo(1L);
        assertThat(repository.getRelatedTrackIdsNext(unique, allIds)).doesNotContain(pending);

        var settledBounds = repository.findImportedTrackBounds(80.0, 85.0, 170.0, 175.0);
        assertThat(settledBounds.getMinLng()).isEqualTo(171.0);
        assertThat(settledBounds.getMaxLng()).isEqualTo(172.0);
    }

    private void assertVisibleFilterIds(
            long unique,
            long pending,
            long duplicate,
            long excluded,
            long failed,
            long incomplete,
            long planned
    ) {
        List<Long> hidden = List.of(duplicate, excluded, failed, incomplete, planned);

        assertThat(filterExecutionService.executeStandardFilter().asIdList())
                .contains(unique, pending)
                .doesNotContainAnyElementsOf(hidden);
        assertThat(filterExecutionService.executeOptionalFilterName("TracksByYear", null).asIdList())
                .contains(unique, pending)
                .doesNotContainAnyElementsOf(hidden);
    }

    private void assertVisibleRepositoryResults(long unique, long pending, Long[] allIds) {
        var bounds = repository.findImportedTrackBounds(80.0, 85.0, 170.0, 175.0);
        assertThat(bounds.getMinLng()).isEqualTo(171.0);
        assertThat(bounds.getMinLat()).isEqualTo(81.0);
        assertThat(bounds.getMaxLng()).isEqualTo(174.0);
        assertThat(bounds.getMaxLat()).isEqualTo(84.0);

        assertThat(statisticTrackCount(allIds)).isEqualTo(2L);
        assertThat(repository.getRelatedTrackIdsNext(unique, allIds)).containsExactly(pending);
        assertThat(repository.getRelatedTrackIdsPrevious(pending, allIds)).containsExactly(unique);
    }

    private long insertTrack(
            String suffix,
            int day,
            String loadStatus,
            String duplicateStatus,
            String trackSource,
            double minLng,
            double minLat
    ) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO gps_track(
                    track_name, start_date, end_date,
                    track_duration_in_motion_secs, track_length_in_meter,
                    load_status, duplicate_status, track_source, activity_type,
                    bbox_min_lng, bbox_max_lng, bbox_min_lat, bbox_max_lat
                )
                VALUES (
                    ?,
                    TIMESTAMP '2198-01-01 10:00:00' + (? * INTERVAL '1 day'),
                    TIMESTAMP '2198-01-01 11:00:00' + (? * INTERVAL '1 day'),
                    3600, 1000,
                    ?, ?, ?, 'WALKING',
                    ?, ?, ?, ?
                )
                RETURNING id
                """,
                Long.class,
                TRACK_NAME_PREFIX + suffix,
                day,
                day,
                loadStatus,
                duplicateStatus,
                trackSource,
                minLng,
                minLng + 1.0,
                minLat,
                minLat + 1.0);
        if (id == null) throw new IllegalStateException("Expected generated track id");
        return id;
    }

    private long statisticTrackCount(Long[] trackIds) {
        return repository.getTrackStatistics("YYYY", null, trackIds, 250.0).stream()
                .mapToLong(GpsTrackStatistics::getNumberOfTracks)
                .sum();
    }
}
