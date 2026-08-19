package com.x8ing.mtl.server.mtlserver.db.repository.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaCorrelationRepositoryTest {

    @Test
    void expandsTrackWorkThroughIndexedRawAndCorrectedTimeBranches() {
        assertThat(MediaCorrelationRepository.EXPAND_TRACK_WORK_SQL)
                .contains("media.exif_gps_date BETWEEN track.start_date AND track.end_date")
                .contains("media.exif_date_image_taken BETWEEN track.start_date AND track.end_date")
                .contains("correction.media_id IS NULL")
                .contains("JOIN media_time_correction correction ON TRUE")
                .contains("track.start_date - make_interval(secs => correction.offset_seconds)")
                .contains("FROM media_track_correlation correlation")
                .contains("FOR UPDATE SKIP LOCKED")
                .doesNotContain("CROSS JOIN media_file");
    }

    @Test
    void rebuildsAllCandidatesAndSelectsOneDeterministically() {
        assertThat(MediaCorrelationRepository.INSERT_CORRELATIONS_SQL)
                .contains("WHERE media.id IN (:mediaIds)")
                .contains("track.load_status = 'SUCCESS'")
                .contains("track.duplicate_status = 'UNIQUE'")
                .contains("track.track_source = 'IMPORTED'")
                .contains("candidate_tracks AS MATERIALIZED")
                .contains("AS exif_route_distance_meters")
                .contains("JOIN LATERAL")
                .contains("ORDER BY data.id DESC")
                .contains("COUNT(*) OVER (PARTITION BY candidate.media_id)")
                .contains("ROW_NUMBER() OVER")
                .contains("scored.track_id ASC")
                .contains("positioned.candidate_rank = 1")
                .contains("positioned.alternative_count > 1")
                .contains("ST_SetSRID(ST_MakePoint")
                .doesNotContain("AS route_shape")
                .doesNotContain("UPDATE media_file");
    }

    @Test
    void resolvesWithUserThenExifThenTrackPrecedence() {
        assertThat(MediaCorrelationRepository.INSERT_RESOLVED_LOCATIONS_SQL)
                .contains("WHEN manual.location IS NOT NULL THEN manual.location")
                .contains("WHEN media.exif_gps_location IS NOT NULL THEN media.exif_gps_location")
                .contains("ELSE correlation.route_location")
                .contains("'USER_ASSIGNED'")
                .contains("'EXIF_EMBEDDED'")
                .contains("'TRACK_INTERPOLATED'")
                .contains("correlation.selected");
    }

    @Test
    void queueSupportsFailureDeferralAndDeterministicRebuildLocks() {
        assertThat(MediaCorrelationRepository.EXPAND_TRACK_WORK_SQL)
                .contains("attempt_count = 0")
                .contains("retry_after = EXCLUDED.requested_at");
    }
}
