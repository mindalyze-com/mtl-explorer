package com.x8ing.mtl.server.mtlserver.db.repository.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackMediaQueryRepositoryTest {

    @Test
    void queryNarrowsTheMediaLibraryByTargetTrackBeforeResolvingOverlaps() {
        assertThat(TrackMediaQueryRepository.TRACK_MEDIA_SQL)
                .contains("WITH target_track AS MATERIALIZED")
                .contains("gt.id = :trackId")
                .contains("target_candidates AS MATERIALIZED")
                .contains("m.exif_gps_date BETWEEN tt.start_date AND tt.end_date")
                .contains("m.exif_date_image_taken BETWEEN")
                .contains("tt.start_date - make_interval(secs => :cameraOffsetSeconds)")
                .contains("m.exif_date_image_taken + make_interval(secs => :cameraOffsetSeconds)")
                .contains("UNION ALL")
                .contains("JOIN LATERAL")
                .contains("gt.load_status = 'SUCCESS'")
                .contains("gt.duplicate_status = 'UNIQUE'")
                .contains("gt.track_source = 'IMPORTED'")
                .contains("gtd.track_type = 'RAW_OUTLIER_CLEANED'")
                .contains("candidate_shape.track_type = 'SIMPLIFIED_SHAPE'")
                .contains("candidate_shape.precision_in_meter = 10")
                .contains("candidate_canonical.track_type = 'RAW_OUTLIER_CLEANED'")
                .contains("COALESCE(candidate_shape.track, candidate_canonical.track)")
                .contains("COUNT(*) OVER () AS overlap_count")
                .contains("COUNT(*) OVER () AS total_elements")
                .contains("LIMIT :pageSize")
                .contains("OFFSET :offset")
                .contains("ranked_track.overlap_count = 1")
                .contains("best_match.id = :trackId");

        assertThat(TrackMediaQueryRepository.DISABLE_JIT_FOR_TRANSACTION_SQL)
                .isEqualTo("SET LOCAL jit = off");

        assertThat(TrackMediaQueryRepository.TRACK_MEDIA_SQL)
                .doesNotContain("m.exif_date_image_taken + make_interval(secs => :cameraOffsetSeconds) BETWEEN")
                .doesNotContain("FROM media_file m\n                JOIN eligible_tracks");
    }

    @Test
    void queryKeepsExifAndCanonicalRouteCoordinatesSeparate() {
        assertThat(TrackMediaQueryRepository.TRACK_MEDIA_SQL)
                .contains("ST_Y(sm.exif_gps_location) AS exif_lat")
                .contains("ST_X(sm.exif_gps_location) AS exif_lng")
                .contains("LEFT JOIN media_manual_location manual ON manual.media_id = sm.id")
                .contains("ST_Y(manual.location) AS manual_lat")
                .contains("ST_Y(before_point.point_long_lat) AS before_lat")
                .contains("ST_X(after_point.point_long_lat) AS after_lng")
                .contains("point_timestamp <= sm.adjusted_captured_at")
                .contains("point_timestamp >= sm.adjusted_captured_at")
                .doesNotContain("UPDATE media_file");
    }

    @Test
    void normalTimelineReadsOnlySelectedPersistedCorrelations() {
        assertThat(TrackMediaQueryRepository.PERSISTED_TRACK_MEDIA_SQL)
                .contains("FROM media_track_correlation correlation")
                .contains("correlation.track_id = :trackId")
                .contains("correlation.selected")
                .contains("LEFT JOIN media_resolved_location resolved")
                .contains("LEFT JOIN media_manual_location manual")
                .contains("media.exif_gps_location")
                .contains("correlation.route_location")
                .contains("resolved.location")
                .contains("COUNT(*) OVER () AS total_elements")
                .contains("LIMIT :pageSize")
                .contains("OFFSET :offset")
                .doesNotContain("gps_track_data_points");
    }

    @Test
    void nearbyTrackCountsUseOnlySelectedCorrelations() {
        assertThat(TrackMediaQueryRepository.SELECTED_MEDIA_COUNTS_BY_TRACK_SQL)
                .contains("FROM media_track_correlation correlation")
                .contains("correlation.selected")
                .contains("correlation.track_id IN (:trackIds)")
                .contains("GROUP BY correlation.track_id");
    }
}
