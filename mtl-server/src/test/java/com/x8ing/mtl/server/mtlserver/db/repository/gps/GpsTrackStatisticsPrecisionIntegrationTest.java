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
import static org.assertj.core.api.Assertions.within;

/**
 * Exercises the production aggregate SQL with isolated fractional statistics.
 */
@EnabledIfSystemProperty(named = "mtl.test.statistics.jdbc-url", matches = "jdbc:postgresql:.*")
class GpsTrackStatisticsPrecisionIntegrationTest {
    private Connection connection;
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void createSyntheticStatistics() throws SQLException {
        connection = DriverManager.getConnection(System.getProperty("mtl.test.statistics.jdbc-url"),
                System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
        connection.setAutoCommit(false);
        jdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true));
        jdbc.getJdbcTemplate().execute("""
                CREATE TEMP TABLE gps_track_v (
                    id bigint, start_date timestamp, end_date timestamp,
                    timing_unreliable boolean DEFAULT false,
                    track_duration_in_motion_secs double precision DEFAULT 60,
                    duplicate_status text DEFAULT 'UNIQUE', load_status text DEFAULT 'SUCCESS',
                    track_source text DEFAULT 'IMPORTED', statistics_exclusion_reason text,
                    review_excluded boolean DEFAULT false, track_length_in_meter double precision,
                    median_distance_between_points double precision DEFAULT 5,
                    energy_net_total_wh double precision, power_watts_avg double precision DEFAULT 10,
                    normalized_power_watts double precision DEFAULT 10,
                    exploration_status text DEFAULT 'CALCULATED', exploration_score double precision DEFAULT 1
                ) ON COMMIT DROP
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO gps_track_v (id, start_date, end_date, track_length_in_meter, energy_net_total_wh)
                VALUES (1, '2026-09-01 08:00', '2026-09-01 08:01', 100.75, 10.24),
                       (2, '2026-09-02 08:00', '2026-09-02 08:01', 200.75, 20.24)
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
    void additiveTotalsDoNotDependOnCalendarGrouping() throws ReflectiveOperationException {
        String sql = GpsTrackRepository.class.getMethod("getTrackStatistics",
                String.class, String.class, Long[].class, Double.class).getAnnotation(Query.class).value();
        for (String grouping : List.of("YYYY-MM-DD", "IYYY-IW", "YYYY-MM", "YYYY")) {
            var parameters = new MapSqlParameterSource()
                    .addValue("group_by_date_format", grouping).addValue("filter_value", null)
                    .addValue("filterIds", null).addValue("threshold_power", 200);
            var periods = jdbc.query(sql, parameters, (row, index) -> new double[]{
                    row.getDouble("trackLengthInMeterSum"), row.getDouble("energyNetTotalWhSum")});
            assertThat(periods.stream().mapToDouble(row -> row[0]).sum())
                    .as("distance across %s", grouping).isCloseTo(301.5, within(1e-9));
            assertThat(periods.stream().mapToDouble(row -> row[1]).sum())
                    .as("energy across %s", grouping).isCloseTo(30.48, within(1e-9));
        }
    }
}
