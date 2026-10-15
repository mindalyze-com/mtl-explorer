package com.x8ing.mtl.server.mtlserver.db.repository.gps;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.jpa.repository.Query;

import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "mtl.test.simplification.jdbc-url", matches = "jdbc:postgresql:.*")
class GpsTrackDuplicateMissingElevationIntegrationTest {
    private static final long ORIGINAL_TRACK_ID = 101;
    private static final long DUPLICATE_TRACK_ID = 102;
    private static final long DIFFERENT_TRACK_ID = 103;
    private static final long FAILED_TRACK_ID = 104;

    @ParameterizedTest
    @CsvSource({
            "NaN, 110, 120", "Infinity, 110, 120", "-Infinity, 110, 120", "100, 110, 120",
            "NaN, NaN, NaN", "Infinity, Infinity, Infinity", "-Infinity, -Infinity, -Infinity"
    })
    void identicalHorizontalTracksRemainDuplicatesRegardlessOfElevation(
            double firstElevation, double secondElevation, double thirdElevation) throws Exception {
        String repositoryQuery = GpsTrackRepository.class
                .getMethod("findSimilarTracksWithinTimeRangeForTrack", Date.class, Date.class, Double.class, Long.class)
                .getAnnotation(Query.class).value();
        // Read-only synthetic CTEs shadow the real tables for this statement.
        String sources = """
                WITH input AS (
                    SELECT ST_SetSRID(ST_MakeLine(ARRAY[
                        ST_MakePoint(10.1, 20, ?::double precision, 0),
                        ST_MakePoint(10.0001, 20, ?::double precision, 10),
                        ST_MakePoint(10.0002, 20, ?::double precision, 20)]), 4326) AS track),
                gps_track(id, start_date, duplicate_status) AS (
                    VALUES (%d::bigint, NULL::timestamp, 'NOT_CHECKED_YET'), (%d::bigint, NULL::timestamp, 'NOT_CHECKED_YET'),
                           (%d::bigint, NULL::timestamp, 'NOT_CHECKED_YET'),
                           (%d::bigint, NULL::timestamp, 'FAILED')),
                gps_track_data(id, gps_track_id, precision_in_meter, recording_break_before_point_indexes, track) AS (
                    SELECT id, id, 100, '[]'::jsonb,
                           CASE WHEN id = %d THEN ST_SetPoint(input.track, 0, ST_MakePoint(10.0001, 20, 100, 0))
                                ELSE input.track END
                    FROM gps_track CROSS JOIN input),
                orig_track AS
                """.formatted(ORIGINAL_TRACK_ID, DUPLICATE_TRACK_ID, DIFFERENT_TRACK_ID, FAILED_TRACK_ID, DIFFERENT_TRACK_ID);
        String sql = repositoryQuery.replaceFirst("WITH orig_track AS", sources)
                .replace(":gps_track_id", "?").replace(":start_date", "?")
                .replace(":end_date", "?").replace(":similarity", "?");
        var matches = new ArrayList<Long>();
        try (var connection = DriverManager.getConnection(
                System.getProperty("mtl.test.simplification.jdbc-url"),
                System.getProperty("mtl.test.simplification.jdbc-user"),
                System.getProperty("mtl.test.simplification.jdbc-password"))) {
            connection.setReadOnly(true);
            try (var statement = connection.prepareStatement(sql)) {
                statement.setDouble(1, firstElevation);
                statement.setDouble(2, secondElevation);
                statement.setDouble(3, thirdElevation);
                statement.setLong(4, ORIGINAL_TRACK_ID);
                statement.setTimestamp(5, Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
                statement.setTimestamp(6, Timestamp.from(Instant.parse("2027-01-01T00:00:00Z")));
                statement.setDouble(7, 1);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) matches.add(rows.getLong(1));
                }
            }
        }
        assertThat(matches).containsExactlyInAnyOrder(ORIGINAL_TRACK_ID, DUPLICATE_TRACK_ID);
    }
}
