package com.x8ing.mtl.server.mtlserver.db.repository.media;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the production paging SQL against temporary copies of the migrated PostGIS tables.
 */
@EnabledIfSystemProperty(named = "mtl.test.media-paging.jdbc-url", matches = "jdbc:postgresql:.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrackMediaPagingDatabaseIntegrationTest {
    private static final int MEDIA_COUNT = 100_000;
    private static final Set<String> DETAIL_TABLES = Set.of(
            "media_file", "indexed_file", "media_resolved_location", "media_manual_location");
    private Connection connection;
    private NamedParameterJdbcTemplate jdbc;
    private TrackMediaQueryRepository repository;

    @BeforeAll
    void createSyntheticTimeline() throws SQLException {
        connection = DriverManager.getConnection(System.getProperty("mtl.test.media-paging.jdbc-url"),
                System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
        connection.setAutoCommit(false);
        jdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true));
        repository = new TrackMediaQueryRepository(jdbc);
        for (String table : List.of("indexed_file", "media_file", "media_track_correlation",
                "media_resolved_location", "media_manual_location")) {
            jdbc.getJdbcTemplate().execute("CREATE TEMP TABLE " + table
                                           + " (LIKE public." + table + " INCLUDING ALL) ON COMMIT DROP");
        }
        jdbc.update("""
                INSERT INTO indexed_file(id, "index", name, path, full_path, indexer_status)
                SELECT n, 'MEDIA', 'synthetic-' || n || '.jpg', '/synthetic',
                       '/synthetic/' || n || '.jpg', 'COMPLETED_WITH_SUCCESS'
                FROM generate_series(1, :count) n
                """, Map.of("count", MEDIA_COUNT));
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO media_file(id, file_id, camera_make, camera_model, exif_date_image_taken)
                SELECT id, id, 'Synthetic', 'Paging fixture',
                       timestamp '2026-08-29 10:00:00' + ((id - 1) / 100) * interval '1 second'
                FROM indexed_file;
                INSERT INTO media_track_correlation(
                    id, media_id, track_id, track_version, algorithm_version, captured_at,
                    adjusted_capture_time, applied_camera_offset_seconds, time_source,
                    route_location, selected, alternative_count, ambiguous)
                SELECT id, id, 1, 0, 5, exif_date_image_taken, exif_date_image_taken, 0,
                       'EXIF_DATE_TAKEN', ST_SetSRID(ST_MakePoint(7.45, 46.95), 4326), true, 2, true
                FROM media_file;
                INSERT INTO media_resolved_location(media_id, location, position_origin, correlation_id)
                SELECT media_id, route_location, 'TRACK_INTERPOLATED', id FROM media_track_correlation;
                INSERT INTO media_track_correlation(
                    id, media_id, track_id, track_version, algorithm_version, captured_at,
                    adjusted_capture_time, applied_camera_offset_seconds, time_source,
                    route_location, selected)
                SELECT id + 100000, media_id, 2, track_version, algorithm_version, captured_at,
                       adjusted_capture_time, applied_camera_offset_seconds, time_source,
                       route_location, false
                FROM media_track_correlation WHERE media_id = 1;
                INSERT INTO media_manual_location(media_id, location, note)
                VALUES (1, ST_SetSRID(ST_MakePoint(9, 48), 4326), 'Synthetic manual position');
                UPDATE media_resolved_location SET location=ST_SetSRID(ST_MakePoint(9, 48), 4326),
                    position_origin='USER_ASSIGNED', correlation_id=NULL WHERE media_id=1;
                DELETE FROM media_resolved_location WHERE media_id=100000;
                ANALYZE indexed_file; ANALYZE media_file; ANALYZE media_track_correlation;
                ANALYZE media_resolved_location; ANALYZE media_manual_location;
                """);
    }

    @AfterAll
    void discardFixture() throws SQLException {
        if (connection != null) {
            try {
                connection.rollback();
            } finally {
                connection.close();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"100,0", "200,50000", "200,99800"})
    void boundsDetailLookupsToEachRequestedPage(int pageSize, long offset) {
        var page = repository.findPersistedByTrackId(1, pageSize, offset);
        assertThat(page.totalElements()).isEqualTo(MEDIA_COUNT);
        assertThat(page.items()).hasSize(pageSize);
        assertThat(page.items()).extracting(PersistedTrackMediaRow::id)
                .containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(offset + 1, offset + pageSize)
                        .boxed().toList());
        String planJson = jdbc.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "
                                              + TrackMediaQueryRepository.PERSISTED_TRACK_MEDIA_SQL,
                Map.of("trackId", 1, "pageSize", pageSize, "offset", offset), String.class);
        List<JsonNode> nodes = new ArrayList<>();
        collectNodes(JsonMapper.builder().build().readTree(planJson).get(0).get("Plan"), nodes);
        for (String table : DETAIL_TABLES) {
            List<JsonNode> scans = nodes.stream()
                    .filter(node -> table.equals(node.path("Relation Name").asText())).toList();
            assertThat(scans).as("plan includes %s", table).isNotEmpty();
            double visitedRows = scans.stream().mapToDouble(node ->
                    (node.path("Actual Rows").asDouble() + node.path("Rows Removed by Filter").asDouble())
                    * node.path("Actual Loops").asDouble()).sum();
            assertThat(visitedRows).as("detail rows read from %s for offset %s", table, offset)
                    .isLessThanOrEqualTo(pageSize);
        }
    }

    @Test
    void preservesOptionalDetailsAndTotalsForEmptyPages() {
        var first = repository.findPersistedByTrackId(1, 1, 0).items().getFirst();
        assertThat(first.fileName()).isEqualTo("synthetic-1.jpg");
        assertThat(first.cameraMake()).isEqualTo("Synthetic");
        assertThat(first.ambiguousMatch()).isTrue();
        assertThat(first.alternativeMatchCount()).isEqualTo(2);
        assertThat(first.manualNote()).isEqualTo("Synthetic manual position");
        assertThat(first.resolvedLat()).isEqualTo(48);
        assertThat(first.routeLat()).isEqualTo(46.95);
        var last = repository.findPersistedByTrackId(1, 1, MEDIA_COUNT - 1).items().getFirst();
        assertThat(last.id()).isEqualTo(MEDIA_COUNT);
        assertThat(last.resolvedLat()).isNull();
        assertThat(last.manualNote()).isNull();
        var beyond = repository.findPersistedByTrackId(1, 100, MEDIA_COUNT);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(MEDIA_COUNT);
        for (long trackId : List.of(2L, 999L)) {
            for (long offset : List.of(0L, 100L)) {
                var empty = repository.findPersistedByTrackId(trackId, 100, offset);
                assertThat(empty.items()).isEmpty();
                assertThat(empty.totalElements()).isZero();
            }
        }
    }

    private static void collectNodes(JsonNode node, List<JsonNode> nodes) {
        nodes.add(node);
        for (JsonNode child : node.path("Plans")) collectNodes(child, nodes);
    }
}
