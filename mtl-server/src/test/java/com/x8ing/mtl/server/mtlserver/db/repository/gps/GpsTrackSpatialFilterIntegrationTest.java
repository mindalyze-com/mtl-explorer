package com.x8ing.mtl.server.mtlserver.db.repository.gps;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs both production spatial queries against isolated synthetic PostGIS rows.
 */
@EnabledIfSystemProperty(named = "mtl.test.spatial.jdbc-url", matches = "jdbc:postgresql:.*")
class GpsTrackSpatialFilterIntegrationTest {
    private Connection connection;
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void createSyntheticTracks() throws SQLException {
        connection = DriverManager.getConnection(System.getProperty("mtl.test.spatial.jdbc-url"),
                System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
        connection.setAutoCommit(false);
        jdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true));
        jdbc.getJdbcTemplate().execute("""
                CREATE TEMP TABLE gps_track (
                    id bigint, duplicate_status text, review_map_excluded boolean, track_source text
                ) ON COMMIT DROP
                """);
        jdbc.getJdbcTemplate().execute("""
                CREATE TEMP TABLE gps_track_data (
                    gps_track_id bigint, observed_path geometry,
                    track_type text DEFAULT 'SIMPLIFIED_SHAPE', precision_in_meter integer DEFAULT 10
                ) ON COMMIT DROP
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO gps_track VALUES
                    (1, 'UNIQUE', false, 'IMPORTED'), (2, 'DUPLICATE', false, 'IMPORTED'),
                    (3, 'DUPLICATE', true, 'IMPORTED'), (4, 'UNIQUE', false, 'PLANNED')
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO gps_track_data (gps_track_id, observed_path)
                SELECT id, ST_GeomFromText('LINESTRING(8 47, 8.001 47)', 4326) FROM gps_track
                """);
    }

    @AfterEach
    void discardFixture() throws SQLException {
        if (connection != null) {
            try {
                connection.rollback();
            } finally {
                connection.close();
            }
        }
    }

    @Test
    void spatialDiscoveryRespectsTheSelectedTrackIdsIncludingDuplicates() throws Exception {
        for (String method : List.of("getTracksWithinDistanceToPoint", "getTracksWithDistanceToPoint")) {
            assertThat(query(method, 2L)).as(method + " duplicate selection").containsExactly(2L);
            assertThat(query(method, 1L)).as(method + " unique selection").containsExactly(1L);
            assertThat(query(method, 1L, 2L, 3L, 4L)).as(method + " map eligibility").containsExactly(1L, 2L);
            assertThat(query(method)).as(method + " empty selection").isEmpty();
        }
    }

    private List<Long> query(String method, Long... trackIds) throws Exception {
        String sql = GpsTrackRepository.class.getMethod(method, double.class, double.class, double.class, Long[].class)
                .getAnnotation(Query.class).value();
        var ids = connection.createArrayOf("bigint", trackIds);
        try {
            var params = new MapSqlParameterSource().addValue("longitude", 8.0005)
                    .addValue("latitude", 47).addValue("distanceInMeter", 5).addValue("filterIds", ids);
            return jdbc.query(sql, params, (row, index) -> row.getLong(1));
        } finally {
            ids.free();
        }
    }
}
