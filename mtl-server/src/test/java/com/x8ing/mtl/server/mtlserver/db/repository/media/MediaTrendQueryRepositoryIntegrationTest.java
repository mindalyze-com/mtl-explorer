package com.x8ing.mtl.server.mtlserver.db.repository.media;

import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "mtl.media-correlation.initial-delay=PT1H",
        "mtl.media-correlation.run-schedule=PT1H",
        "mtl.indexer.gps.live-watch-enabled=false"
})
@Transactional
@Rollback
class MediaTrendQueryRepositoryIntegrationTest {

    private static final String GPS_TIME = "2199-08-17 10:05:00";
    private static final String CAMERA_TIME = "2199-08-17 10:06:00";
    private static final int CAMERA_OFFSET_SECONDS = 86_400;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MediaTrendQueryRepository repository;

    @MockitoBean
    private GPXDirectoryWatcherService gpxDirectoryWatcherService;

    @MockitoBean
    private MediaIndexerService mediaIndexerService;

    @Test
    void groupsOwnCaptureTimesWithinMatchingTracksAndUsesSelectedCorrelations() {
        long eligibleTrack = insertTrack("Synthetic media trend", null);
        long alternativeTrack = insertTrack("Synthetic media alternative", null);
        long excludedTrack = insertTrack("Synthetic media excluded", "OTHER");
        long gpsMedia = insertMedia("synthetic-gps-photo.jpg", GPS_TIME, "2100-01-01 01:00:00", "2199-08-20 00:00:00");
        long correctedVideo = insertMedia("synthetic-corrected-video.MOV", null, CAMERA_TIME, "2199-08-19 00:00:00");
        long undatedMedia = insertMedia("synthetic-undated-photo.webp", null, null, "2199-08-18 00:00:00");
        long excludedMedia = insertMedia("synthetic-excluded-photo.jpg", GPS_TIME, null, "2199-08-17 00:00:00");
        insertCorrection(correctedVideo, CAMERA_OFFSET_SECONDS);

        insertCorrelation(gpsMedia, eligibleTrack, GPS_TIME, true, true, 2);
        insertCorrelation(gpsMedia, alternativeTrack, GPS_TIME, false, true, 2);
        insertCorrelation(correctedVideo, eligibleTrack, "2199-08-18 10:06:00", true, false, 1);
        insertCorrelation(excludedMedia, excludedTrack, GPS_TIME, true, false, 1);

        var matched = repository.findTrendBuckets(
                MediaTrendGrouping.DAY,
                MediaTrendScope.MATCHED_ACTIVITIES,
                List.of(eligibleTrack, alternativeTrack, excludedTrack));

        assertThat(matched)
                .extracting(bucket -> List.of(bucket.bucketKey(), bucket.imageCount(), bucket.videoCount()))
                .containsExactly(
                        List.of("2199-08-17", 2L, 0L),
                        List.of("2199-08-18", 0L, 1L));

        var allIndexed = repository.findTrendBuckets(
                MediaTrendGrouping.DAY,
                MediaTrendScope.ALL_INDEXED,
                List.of(eligibleTrack));
        assertThat(allIndexed)
                .filteredOn(bucket -> bucket.bucketKey().startsWith("2199-") || bucket.undated())
                .anySatisfy(bucket -> {
                    assertThat(bucket.bucketKey()).isEqualTo("2199-08-17");
                    assertThat(bucket.imageCount()).isGreaterThanOrEqualTo(2);
                })
                .anySatisfy(bucket -> {
                    assertThat(bucket.bucketKey()).isEqualTo("2199-08-18");
                    assertThat(bucket.videoCount()).isGreaterThanOrEqualTo(1);
                });
        assertThat(allIndexed).noneMatch(bucket -> bucket.undated());

        for (MediaTrendGrouping grouping : MediaTrendGrouping.values()) {
            var buckets = repository.findTrendBuckets(grouping, MediaTrendScope.MATCHED_ACTIVITIES, List.of(eligibleTrack));
            assertThat(buckets).isNotEmpty();
            assertThat(buckets).allSatisfy(bucket -> assertThat(grouping.isValidBucketKey(bucket.bucketKey())).isTrue());
        }

        var photosOnly = repository.findItems(
                MediaTrendGrouping.DAY,
                MediaTrendScope.MATCHED_ACTIVITIES,
                "2199-08-17",
                MediaTrendKindFilter.IMAGE,
                List.of(eligibleTrack, alternativeTrack),
                60,
                0);
        assertThat(photosOnly.totalElements()).isEqualTo(1);
        assertThat(photosOnly.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(gpsMedia);
            assertThat(item.effectiveCapturedAt()).isNotNull();
            assertThat(item.ambiguousMatch()).isTrue();
        });

        var sameTimeItems = repository.findItems(
                MediaTrendGrouping.DAY,
                MediaTrendScope.ALL_INDEXED,
                "2199-08-17",
                MediaTrendKindFilter.IMAGE,
                List.of(eligibleTrack),
                60,
                0);
        assertThat(sameTimeItems.items())
                .extracting(item -> item.id())
                .startsWith(excludedMedia, gpsMedia);

        var correctedItems = repository.findItems(
                MediaTrendGrouping.DAY,
                MediaTrendScope.ALL_INDEXED,
                "2199-08-18",
                MediaTrendKindFilter.VIDEO,
                List.of(eligibleTrack),
                60,
                0);
        assertThat(correctedItems.items())
                .filteredOn(item -> item.id() == correctedVideo)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.appliedCameraOffsetSeconds()).isEqualTo(CAMERA_OFFSET_SECONDS);
                    assertThat(item.timeSource()).isEqualTo(TrackMediaDto.TIME_SOURCE.EXIF_DATE_TAKEN);
                });
        assertThat(sameTimeItems.items())
                .filteredOn(item -> item.id() == gpsMedia)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.appliedCameraOffsetSeconds()).isZero();
                    assertThat(item.timeSource()).isEqualTo(TrackMediaDto.TIME_SOURCE.EXIF_GPS);
                });

        var undated = repository.findItems(
                MediaTrendGrouping.TOTAL,
                MediaTrendScope.ALL_INDEXED,
                MediaTrendGrouping.UNDATED_BUCKET_KEY,
                MediaTrendKindFilter.ALL,
                List.of(eligibleTrack),
                1,
                0);
        assertThat(undated.items()).isEmpty();
    }

    @Test
    void mapTotalsAndBrowsingShareContinuousInclusiveRangeInBothScopes() {
        long firstTrack = insertTrack("Synthetic first range", null);
        long lastTrack = insertTrack("Synthetic last range", null);
        jdbcTemplate.update("UPDATE gps_track SET end_date = '2199-08-17 01:00:00' WHERE id = ?", firstTrack);
        jdbcTemplate.update("UPDATE gps_track SET start_date = '2199-08-18 23:00:00' WHERE id = ?", lastTrack);
        List<Long> tracks = List.of(firstTrack, lastTrack);
        long first = insertMedia("range-first.jpg", "2199-08-17 00:00:00", null, GPS_TIME);
        long gap = insertMedia("range-gap.jpg", GPS_TIME, null, GPS_TIME);
        long last = insertMedia("range-last.mp4", "2199-08-19 00:00:00", null, GPS_TIME);
        long old = insertMedia("range-old.jpg", "2189-08-17 00:00:00", null, GPS_TIME);
        long future = insertMedia("range-future.jpg", "2199-08-19 00:00:01", null, GPS_TIME);
        long unknown = insertMedia("range-unknown.jpg", null, null, GPS_TIME);
        for (long id : List.of(first, gap, last, old, future, unknown)) {
            jdbcTemplate.update("UPDATE media_file SET exif_gps_location = ST_SetSRID(ST_MakePoint(8,47),4326) WHERE id = ?", id);
        }
        insertCorrelation(first, firstTrack, "2199-08-17 00:00:00", true, false, 1);
        // Even a link to a selected track must not admit old or unknown media.
        insertCorrelation(old, firstTrack, GPS_TIME, true, false, 1);
        insertCorrelation(unknown, firstTrack, GPS_TIME, true, false, 1);
        for (MediaTrendScope scope : MediaTrendScope.values()) {
            List<Long> expected = scope == MediaTrendScope.ALL_INDEXED ? List.of(first, gap, last) : List.of(first);
            var buckets = repository.findTrendBuckets(MediaTrendGrouping.TOTAL, scope, tracks);
            assertThat(buckets).singleElement().satisfies(bucket ->
                    assertThat(bucket.imageCount() + bucket.videoCount()).isEqualTo(expected.size()));
            var items = repository.findItems(MediaTrendGrouping.TOTAL, scope, "TOTAL", MediaTrendKindFilter.ALL, tracks, 60, 0);
            assertThat(items.items()).extracting(item -> item.id()).containsExactlyInAnyOrderElementsOf(expected);
            var bounds = new MediaBoundsRequest(46, 7, 48, 9, scope, tracks);
            assertThat(repository.findInBounds(bounds, tracks)).extracting(point -> point.getId())
                    .containsExactlyInAnyOrderElementsOf(expected);
            assertThat(repository.findTrendBuckets(MediaTrendGrouping.TOTAL, scope, List.of())).isEmpty();
            assertThat(repository.findInBounds(bounds, List.of())).isEmpty();
            assertThat(repository.findItems(MediaTrendGrouping.TOTAL, scope, "TOTAL", MediaTrendKindFilter.ALL, List.of(), 60, 0).items()).isEmpty();
            jdbcTemplate.update("UPDATE gps_track SET end_date = NULL WHERE id IN (?, ?)", firstTrack, lastTrack);
            assertThat(repository.findTrendBuckets(MediaTrendGrouping.TOTAL, scope, tracks)).isEmpty();
            assertThat(repository.findInBounds(bounds, tracks)).isEmpty();
            jdbcTemplate.update("UPDATE gps_track SET end_date = '2199-08-19 00:00:00' WHERE id IN (?, ?)", firstTrack, lastTrack);
        }
    }

    private long insertTrack(String name, String exclusionReason) {
        long indexedFileId = insertIndexedFile(name + ".gpx", "2199-08-17 00:00:00");
        return requiredLong(jdbcTemplate.queryForObject("""
                INSERT INTO gps_track(
                    file_id, track_name, start_date, end_date, load_status,
                    duplicate_status, track_source, statistics_exclusion_reason
                )
                VALUES (?, ?, '2199-08-17 00:00:00'::timestamp, '2199-08-19 00:00:00'::timestamp,
                        'SUCCESS', 'UNIQUE', 'IMPORTED', ?)
                RETURNING id
                """, Long.class, indexedFileId, name, exclusionReason));
    }

    private long insertMedia(String name, String gpsTime, String cameraTime, String indexedCreateTime) {
        long indexedFileId = insertIndexedFile(name, indexedCreateTime);
        return requiredLong(jdbcTemplate.queryForObject("""
                INSERT INTO media_file(file_id, exif_gps_date, exif_date_image_taken)
                VALUES (?, ?::timestamp, ?::timestamp)
                RETURNING id
                """, Long.class, indexedFileId, gpsTime, cameraTime));
    }

    private long insertIndexedFile(String name, String createTime) {
        return requiredLong(jdbcTemplate.queryForObject("""
                INSERT INTO indexed_file("index", name, path, full_path, indexer_status, create_date)
                VALUES ('MEDIA', ?, '/tmp/mtl-synthetic-media-trend',
                        '/tmp/mtl-synthetic-media-trend/' || ?, 'COMPLETED_WITH_SUCCESS', ?::timestamp)
                RETURNING id
                """, Long.class, name, name, createTime));
    }

    private void insertCorrection(long mediaId, int offsetSeconds) {
        jdbcTemplate.update(
                "INSERT INTO media_time_correction(media_id, offset_seconds) VALUES (?, ?)",
                mediaId,
                offsetSeconds);
    }

    private void insertCorrelation(
            long mediaId,
            long trackId,
            String effectiveTime,
            boolean selected,
            boolean ambiguous,
            int alternativeCount) {
        jdbcTemplate.update("""
                INSERT INTO media_track_correlation(
                    media_id, track_id, track_version, algorithm_version, captured_at,
                    adjusted_capture_time, applied_camera_offset_seconds, time_source,
                    route_location, selected, alternative_count, ambiguous
                )
                VALUES (?, ?, 0, 1, ?::timestamp, ?::timestamp, 0, 'EXIF_GPS',
                        ST_SetSRID(ST_MakePoint(8.0, 47.0), 4326), ?, ?, ?)
                """, mediaId, trackId, effectiveTime, effectiveTime, selected, alternativeCount, ambiguous);
    }

    private static long requiredLong(Long value) {
        if (value == null) throw new IllegalStateException("Expected generated database id");
        return value;
    }
}
