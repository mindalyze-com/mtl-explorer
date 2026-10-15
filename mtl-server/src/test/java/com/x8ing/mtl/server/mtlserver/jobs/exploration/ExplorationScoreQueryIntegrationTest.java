package com.x8ing.mtl.server.mtlserver.jobs.exploration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Synthetic, transaction-local PostGIS fixtures; never reads archive geometry.
 */
@EnabledIfSystemProperty(named = "mtl.test.exploration.jdbc-url", matches = "jdbc:postgresql:.*")
class ExplorationScoreQueryIntegrationTest {
    private static final String EARLIER = "2030-01-01 10:00:00";
    private static final String TARGET_DATE = "2030-01-02 10:00:00";
    private static final String LATER = "2030-01-03 10:00:00";
    private static final double CORRIDOR_M = 25;
    private static final int PRECISION_M = 10;
    private static final double LENGTH_TOLERANCE_M = 1e-6;
    private static final String STRAIGHT_PATH = "LINESTRING(0 0,100 0)";

    private Connection connection;
    private NamedParameterJdbcTemplate jdbc;
    private ExplorationScoreQueryRepository repository;
    private String originalQuery;

    @BeforeEach
    void createFixture() throws Exception {
        connection = DriverManager.getConnection(System.getProperty("mtl.test.exploration.jdbc-url"),
                System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
        connection.setAutoCommit(false);
        jdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true));
        repository = new ExplorationScoreQueryRepository(jdbc);
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/exploration/original-score-query.sql"))) {
            originalQuery = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        jdbc.getJdbcTemplate().execute("SET LOCAL jit=off");
        jdbc.getJdbcTemplate().execute("SET LOCAL work_mem='1MB'");
        jdbc.getJdbcTemplate().execute("""
                CREATE TEMP TABLE gps_track (
                    id bigint PRIMARY KEY, start_date timestamp,
                    duplicate_status text DEFAULT 'UNIQUE', load_status text DEFAULT 'SUCCESS',
                    review_excluded boolean DEFAULT false, timing_unreliable boolean DEFAULT false,
                    bbox_min_lng double precision, bbox_max_lng double precision,
                    bbox_min_lat double precision, bbox_max_lat double precision
                ) ON COMMIT DROP
                """);
        jdbc.getJdbcTemplate().execute("""
                CREATE TEMP TABLE gps_track_data (
                    gps_track_id bigint, track geometry, observed_path geometry,
                    track_type text DEFAULT 'SIMPLIFIED_SHAPE', precision_in_meter integer DEFAULT 10
                ) ON COMMIT DROP
                """);
        // Exercise the shipped index definitions against transaction-local synthetic tables.
        for (String migration : List.of("092", "093")) {
            try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/db/changelog/changes/" + migration + ".xml"))) {
                var statements = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                        .parse(input).getElementsByTagName("changeSet").item(0).getChildNodes();
                for (int index = 0; index < statements.getLength(); index++) {
                    if (statements.item(index).getNodeName().equals("sql")) {
                        jdbc.getJdbcTemplate().execute(statements.item(index).getTextContent());
                    }
                }
            }
        }
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
    void firstTrackAndExcludedTargetsAreStillScored() {
        track(1, TARGET_DATE, STRAIGHT_PATH, STRAIGHT_PATH);
        assertScore(1, 1);
        jdbc.getJdbcTemplate().update("UPDATE gps_track SET duplicate_status='EXCLUDED' WHERE id=1");
        assertScore(1, 1);
        track(2, EARLIER, STRAIGHT_PATH, STRAIGHT_PATH);
        assertScore(1, 0);
        jdbc.getJdbcTemplate().update("UPDATE gps_track SET duplicate_status='DUPLICATE' WHERE id=1");
        assertScore(1, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate_status='EXCLUDED'", "duplicate_status='DUPLICATE'",
            "duplicate_status='NOT_CHECKED_YET'", "load_status='FAILED'",
            "review_excluded=true", "timing_unreliable=true", "start_date=NULL"})
    void ineligiblePriorTracksDoNotEstablishCoverage(String assignment) {
        track(1, TARGET_DATE, STRAIGHT_PATH, STRAIGHT_PATH);
        track(2, EARLIER, STRAIGHT_PATH, STRAIGHT_PATH);
        assertScore(1, 0);
        jdbc.getJdbcTemplate().update("UPDATE gps_track SET " + assignment + " WHERE id=2");
        assertScore(1, 1);
    }

    @Test
    void equalAndLaterDatesAreNotPriorHistory() {
        track(1, TARGET_DATE, STRAIGHT_PATH, STRAIGHT_PATH);
        track(2, TARGET_DATE, STRAIGHT_PATH, STRAIGHT_PATH);
        track(3, LATER, STRAIGHT_PATH, STRAIGHT_PATH);
        assertScore(1, 1);
        jdbc.getJdbcTemplate().update("UPDATE gps_track SET start_date=timestamp '2030-01-02 09:59:59.999' WHERE id=2");
        assertScore(1, 0);
    }

    @Test
    void priorRecordingGapsDoNotEstablishCoverage() {
        track(1, TARGET_DATE, "LINESTRING(400 0,600 0)", "LINESTRING(400 0,600 0)");
        track(2, EARLIER, "LINESTRING(0 0,1000 0)", "MULTILINESTRING((0 0,100 0),(900 0,1000 0))");
        assertScore(1, 1);
        jdbc.getJdbcTemplate().update("UPDATE gps_track_data SET observed_path=track WHERE gps_track_id=2");
        assertScore(1, 0);
    }

    @Test
    void targetRecordingGapsAreExcludedFromTotalAndNovelDistance() {
        track(1, TARGET_DATE, "LINESTRING(0 0,1000 0)", "MULTILINESTRING((0 0,100 0),(900 0,1000 0))");
        track(2, EARLIER, "LINESTRING(400 0,600 0)", "LINESTRING(400 0,600 0)");
        var result = assertEquivalent(1, CORRIDOR_M, PRECISION_M);
        assertEquals(200, result.getTotalM(), LENGTH_TOLERANCE_M);
        assertEquals(200, result.getNovelM(), LENGTH_TOLERANCE_M);
    }

    @Test
    void selfOverlapKeepsDistanceWeightingAndWholeSegmentClassification() {
        String repeated = "LINESTRING(0 0,100 0,0 0,100 0)";
        track(1, TARGET_DATE, repeated, repeated);
        track(2, EARLIER, "LINESTRING(0 0,49 0)", "LINESTRING(0 0,49 0)");
        var result = assertEquivalent(1, CORRIDOR_M, PRECISION_M);
        assertEquals(300, result.getTotalM(), LENGTH_TOLERANCE_M);
        assertEquals(75, result.getNovelM(), LENGTH_TOLERANCE_M);
    }

    @ParameterizedTest
    @ValueSource(doubles = {24.999, 25, 25.001})
    void expandedTargetPrefilterPreservesCorridorBoundary(double offset) {
        track(1, TARGET_DATE, STRAIGHT_PATH, STRAIGHT_PATH);
        String parallel = "LINESTRING(0 " + offset + ",100 " + offset + ")";
        track(2, EARLIER, parallel, parallel);
        var result = assertEquivalent(1, CORRIDOR_M, PRECISION_M);
        if (offset < CORRIDOR_M) assertEquals(0, result.getExplorationScore());
        if (offset > CORRIDOR_M) assertEquals(1, result.getExplorationScore());
    }

    @ParameterizedTest
    @ValueSource(doubles = {179.9995, -179.9999, 8})
    void searchBoundsRemainConservativeNearTheAntimeridian(double longitude) {
        String path = "LINESTRING(" + longitude + " 0," + (longitude + 0.0004) + " 0)";
        track(1, TARGET_DATE, STRAIGHT_PATH, STRAIGHT_PATH);
        track(2, EARLIER, STRAIGHT_PATH, STRAIGHT_PATH);
        jdbc.getJdbcTemplate().update("UPDATE gps_track_data SET track=ST_GeomFromText(?,4326),observed_path=ST_GeomFromText(?,4326)", path, path);
        refreshBounds();
        assertEquivalent(1, CORRIDOR_M, PRECISION_M);
    }

    @ParameterizedTest
    @ValueSource(strings = {"north", "south", "east", "west"})
    void snappedShapesOutsideRoundedSourceBoundsStillEstablishCoverage(String direction) {
        boolean horizontal = direction.equals("north") || direction.equals("south");
        double offset = direction.equals("south") || direction.equals("west") ? -0.0002249 : 0.0002249;
        String target = horizontal ? "LINESTRING(0 0,0.001 0)" : "LINESTRING(0 0,0 0.001)";
        String source = horizontal ? "LINESTRING(0 " + offset + ",0.001 " + offset + ")"
                : "LINESTRING(" + offset + " 0," + offset + " 0.001)";
        track(1, TARGET_DATE, STRAIGHT_PATH, STRAIGHT_PATH);
        track(2, EARLIER, STRAIGHT_PATH, STRAIGHT_PATH);
        jdbc.getJdbcTemplate().update("UPDATE gps_track_data SET track=ST_GeomFromText(?,4326) WHERE gps_track_id=1", target);
        jdbc.getJdbcTemplate().update("UPDATE gps_track_data SET track=ST_GeomFromText(?,4326) WHERE gps_track_id=2", source);
        refreshBounds();
        // Ingest rounds the cleaned-source bounds to six decimals, while shape
        // simplification snaps to a five-decimal grid after the projection round trip.
        jdbc.getJdbcTemplate().update("""
                UPDATE gps_track SET bbox_min_lng=round(bbox_min_lng::numeric,6),
                  bbox_max_lng=round(bbox_max_lng::numeric,6),
                  bbox_min_lat=round(bbox_min_lat::numeric,6),bbox_max_lat=round(bbox_max_lat::numeric,6)
                """);
        jdbc.getJdbcTemplate().update("""
                UPDATE gps_track_data SET track=ST_SnapToGrid(
                  ST_Transform(ST_Simplify(ST_Transform(track,3857),10),4326),0.00001)
                """);
        jdbc.getJdbcTemplate().update("UPDATE gps_track_data SET observed_path=track");
        assertScore(1, 0);
    }

    @Test
    void conservativeBoundsMarginDoesNotWidenTheCoverageCorridor() {
        track(1, TARGET_DATE, STRAIGHT_PATH, STRAIGHT_PATH);
        track(2, EARLIER, "LINESTRING(0 50,100 50)", "LINESTRING(0 50,100 50)");
        assertScore(1, 1);
    }

    @Test
    void missingEmptyOrWithheldTargetPathsHaveNoScore() {
        assertNull(assertEquivalent(999, CORRIDOR_M, PRECISION_M));
        for (String path : List.of("LINESTRING EMPTY", "MULTILINESTRING EMPTY")) {
            track(1, TARGET_DATE, path, path);
            assertNull(assertEquivalent(1, CORRIDOR_M, PRECISION_M));
            jdbc.getJdbcTemplate().update("DELETE FROM gps_track_data");
            jdbc.getJdbcTemplate().update("DELETE FROM gps_track");
        }
        track(1, TARGET_DATE, STRAIGHT_PATH, STRAIGHT_PATH);
        jdbc.getJdbcTemplate().update("UPDATE gps_track_data SET observed_path=NULL");
        assertNull(assertEquivalent(1, CORRIDOR_M, PRECISION_M));
        jdbc.getJdbcTemplate().update("UPDATE gps_track_data SET observed_path=track");
        jdbc.getJdbcTemplate().update("UPDATE gps_track SET review_excluded=true");
        assertNull(assertEquivalent(1, CORRIDOR_M, PRECISION_M));
        jdbc.getJdbcTemplate().update("UPDATE gps_track SET review_excluded=false,timing_unreliable=true");
        assertNull(assertEquivalent(1, CORRIDOR_M, PRECISION_M));
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "force_custom_plan", "force_generic_plan"})
    void jdbcPreparedPlansPreserveConfiguredPrecisionAndCorridor(String mode) {
        jdbc.getJdbcTemplate().execute("SET LOCAL plan_cache_mode=" + mode);
        track(1, TARGET_DATE, STRAIGHT_PATH, STRAIGHT_PATH);
        track(2, EARLIER, STRAIGHT_PATH, STRAIGHT_PATH);
        jdbc.getJdbcTemplate().update("UPDATE gps_track_data SET precision_in_meter=100");
        String displaced = "LINESTRING(0 40,100 40)";
        jdbc.getJdbcTemplate().update("""
                INSERT INTO gps_track_data (gps_track_id,track,observed_path)
                SELECT gps_track_id,ST_Transform(ST_GeomFromText(?,3857),4326),
                       ST_Transform(ST_GeomFromText(?,3857),4326)
                FROM gps_track_data WHERE gps_track_id=2
                """, displaced, displaced);
        jdbc.getJdbcTemplate().update("INSERT INTO gps_track_data SELECT gps_track_id,track,observed_path,'SIMPLIFIED_SHAPE',10 FROM gps_track_data WHERE gps_track_id=1");
        jdbc.getJdbcTemplate().update("INSERT INTO gps_track_data SELECT gps_track_id,track,observed_path,'RAW',10 FROM gps_track_data WHERE gps_track_id=2 AND precision_in_meter=100");
        refreshBounds();
        // Cross the driver's prepare threshold on the same connection and SQL.
        for (int execution = 0; execution < 8; execution++) {
            assertEquals(1, assertEquivalent(1, 25, 10).getExplorationScore());
            assertEquals(0, assertEquivalent(1, 50, 10).getExplorationScore());
            assertEquals(0, assertEquivalent(1, 25, 100).getExplorationScore());
        }
        if (!mode.equals("auto")) {
            String countColumn = mode.equals("force_generic_plan") ? "generic_plans" : "custom_plans";
            Long preparedExecutions = jdbc.getJdbcTemplate().queryForObject(
                    "SELECT COALESCE(SUM(" + countColumn + "),0) FROM pg_prepared_statements WHERE statement LIKE '%prior_track_ids%'", Long.class);
            assertTrue(preparedExecutions > 0, "Production SQL must use the requested prepared-plan mode");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "force_custom_plan", "force_generic_plan"})
    void defaultPrecisionIndexesRemainUsableAfterJdbcPreparation(String mode) {
        jdbc.getJdbcTemplate().execute("SET LOCAL plan_cache_mode=" + mode);
        // Tiny fixtures normally prefer sequential scans. This checks index eligibility,
        // while the public-archive benchmark checks the planner's unrestricted choices.
        jdbc.getJdbcTemplate().execute("SET LOCAL enable_seqscan=off");
        track(1, TARGET_DATE, STRAIGHT_PATH, STRAIGHT_PATH);
        track(2, EARLIER, STRAIGHT_PATH, STRAIGHT_PATH);
        for (int execution = 0; execution < 8; execution++) {
            assertEquals(0, repository.calculateExplorationScore(1, null, CORRIDOR_M, PRECISION_M)
                    .getExplorationScore());
        }
        String preparedName = jdbc.getJdbcTemplate().queryForObject("""
                SELECT quote_ident(name) FROM pg_prepared_statements
                WHERE statement LIKE '%WITH target_track AS MATERIALIZED%'
                  AND statement LIKE '%gtd.precision_in_meter = 10%'
                """, String.class);
        assertNotNull(preparedName, "The production default query must reach JDBC preparation");
        String plan = jdbc.getJdbcTemplate().queryForObject(
                "EXPLAIN (FORMAT JSON) EXECUTE " + preparedName + "(1,25,25,0.001,0.001,0.001,0.001,25)", String.class);
        assertNotNull(plan);
        assertTrue(plan.contains("gps_track_data_ix_exploration_track"), plan);
        assertTrue(plan.contains("gps_track_ix_exploration_prior_bbox"), plan);
        assertFalse(plan.contains("gps_track_data_ix_exploration_shape"), plan);
    }

    private void track(long id, String start, String track, String observed) {
        jdbc.getJdbcTemplate().update("INSERT INTO gps_track(id,start_date) VALUES (?,CAST(? AS timestamp))", id, start);
        jdbc.getJdbcTemplate().update("""
                INSERT INTO gps_track_data(gps_track_id,track,observed_path)
                VALUES (?,ST_Transform(ST_GeomFromText(?,3857),4326),ST_Transform(ST_GeomFromText(?,3857),4326))
                """, id, track, observed);
        refreshBounds();
    }

    private void refreshBounds() {
        jdbc.getJdbcTemplate().update("""
                UPDATE gps_track gt SET bbox_min_lng=b.min_lng,bbox_max_lng=b.max_lng,
                    bbox_min_lat=b.min_lat,bbox_max_lat=b.max_lat
                FROM (SELECT gps_track_id,min(ST_XMin(track)) AS min_lng,max(ST_XMax(track)) AS max_lng,
                      min(ST_YMin(track)) AS min_lat,max(ST_YMax(track)) AS max_lat
                      FROM gps_track_data GROUP BY gps_track_id) b
                WHERE gt.id=b.gps_track_id
                """);
    }

    private void assertScore(long id, double expected) {
        var result = assertEquivalent(id, CORRIDOR_M, PRECISION_M);
        assertNotNull(result);
        assertEquals(expected, result.getExplorationScore(), LENGTH_TOLERANCE_M);
    }

    private ExplorationScoreQueryRepository.ExplorationResult assertEquivalent(long id, double corridor, int precision) {
        var actual = repository.calculateExplorationScore(id, null, corridor, precision);
        var params = new MapSqlParameterSource().addValue("trackId", id).addValue("trackType", "SIMPLIFIED_SHAPE")
                .addValue("precisionInMeter", precision).addValue("corridorWidthM", corridor);
        var reference = jdbc.query(originalQuery, params, (row, index) -> new double[]{row.getDouble("total_m"), row.getDouble("novel_m")});
        if (reference.isEmpty()) {
            assertNull(actual);
        } else {
            assertNotNull(actual);
            assertEquals(reference.getFirst()[0], actual.getTotalM(), LENGTH_TOLERANCE_M);
            assertEquals(reference.getFirst()[1], actual.getNovelM(), LENGTH_TOLERANCE_M);
        }
        return actual;
    }
}
