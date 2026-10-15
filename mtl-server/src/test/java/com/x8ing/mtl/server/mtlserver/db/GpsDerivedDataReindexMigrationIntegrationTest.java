package com.x8ing.mtl.server.mtlserver.db;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requires an explicitly selected empty, disposable PostGIS database named mtl.
 */
@EnabledIfSystemProperty(named = "mtl.test.migration.jdbc-url", matches = "jdbc:postgresql:.*")
class GpsDerivedDataReindexMigrationIntegrationTest {

    private static final String MASTER_CHANGELOG = "db/changelog/db.changelog-master.xml";
    private static final String REINDEX_INCLUDE = "<include file=\"/db/changelog/changes/082.xml\"/>";
    private static final String CHANGESET_ID = "20260905_requeue_gps_derived_data";
    private static final List<String> RETAINED_TABLES = List.of(
            "gps_track", "gps_track_data", "gps_track_event", "media_file",
            "google_health.connection", "google_health.data_point", "config", "filter_config");

    @TempDir
    Path resources;

    @Test
    void schedulesEligibleGpsFilesOnceWithoutDeletingTheirData() throws Exception {
        var dataSource = new DriverManagerDataSource(
                System.getProperty("mtl.test.migration.jdbc-url"),
                System.getProperty("mtl.test.migration.username", "review"),
                System.getProperty("mtl.test.migration.password", "review"));
        var jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.indexed_file')::text", String.class))
                .as("Use an empty disposable database; existing application databases must not be modified")
                .isNull();

        // Populate the actual pre-upgrade schema, retaining the original included changelog paths.
        try (var input = getClass().getClassLoader().getResourceAsStream(MASTER_CHANGELOG)) {
            assertThat(input).isNotNull();
            String master = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(master).containsOnlyOnce(REINDEX_INCLUDE);
            Files.writeString(resources.resolve("before-gps-reindex.xml"), master.replace(REINDEX_INCLUDE, ""));
        }
        migrate(dataSource, "before-gps-reindex.xml");

        List<FileFixture> files = new ArrayList<>();
        for (String index : List.of("GPS", "MEDIA", "OTHER")) {
            for (String status : Arrays.asList("COMPLETED_WITH_SUCCESS", "FAILED", "PROCESSING",
                    "SCHEDULED", "REMOVED", "EXCLUDED", null)) {
                Long version = status == null ? null : 7L;
                long id = jdbc.queryForObject("""
                        INSERT INTO indexed_file ("index", name, path, full_path, size, hash,
                            last_modified_date, index_update_date, indexer_status, version,
                            indexer_invocations, last_message)
                        VALUES (?, ?, '/synthetic', '/synthetic/source.gpx', 123, 'synthetic-hash',
                            timestamp '2020-01-01', timestamp '2020-01-02', ?, ?, 3, 'Previous result')
                        RETURNING id
                        """, Long.class, index, index + "-" + status, status, version);
                files.add(new FileFixture(id, index, status, version,
                        fileSnapshot(jdbc, id, false), fileSnapshot(jdbc, id, true)));
            }
        }
        seedRetainedData(jdbc, files.getFirst().id());
        Map<String, String> retained = retainedSnapshots(jdbc);

        migrate(dataSource, MASTER_CHANGELOG);

        for (FileFixture file : files) {
            boolean eligible = file.index().equals("GPS")
                               && !"REMOVED".equals(file.status()) && !"EXCLUDED".equals(file.status());
            if (eligible) {
                var row = jdbc.queryForMap("SELECT * FROM indexed_file WHERE id = ?", file.id());
                assertThat(row.get("indexer_status")).isEqualTo("SCHEDULED");
                assertThat(row.get("version")).isEqualTo(file.version() == null ? 1L : file.version() + 1);
                assertThat(row.get("last_message")).isEqualTo("Upgrade: GPS derived data rebuild scheduled");
                assertThat(jdbc.queryForObject("""
                        SELECT index_update_date > timestamp '2020-01-02' FROM indexed_file WHERE id = ?
                        """, Boolean.class, file.id())).isTrue();
                assertThat(fileSnapshot(jdbc, file.id(), true)).isEqualTo(file.sourceSnapshot());
            } else {
                assertThat(fileSnapshot(jdbc, file.id(), false)).isEqualTo(file.fullSnapshot());
            }
        }
        assertThat(retainedSnapshots(jdbc)).isEqualTo(retained);

        // Simulate a finished import. A later Liquibase update must not schedule it again.
        jdbc.update("""
                UPDATE indexed_file SET indexer_status = 'COMPLETED_WITH_SUCCESS',
                    last_message = 'Rebuild complete', version = version + 1 WHERE id = ?
                """, files.getFirst().id());
        List<String> afterProcessing = files.stream().map(file -> fileSnapshot(jdbc, file.id(), false)).toList();
        migrate(dataSource, MASTER_CHANGELOG);
        assertThat(files.stream().map(file -> fileSnapshot(jdbc, file.id(), false)).toList())
                .isEqualTo(afterProcessing);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM databasechangelog WHERE id = ?",
                Long.class, CHANGESET_ID)).isEqualTo(1L);
        assertThat(retainedSnapshots(jdbc)).isEqualTo(retained);
    }

    private void migrate(DriverManagerDataSource dataSource, String changelog) throws Exception {
        try (var connection = dataSource.getConnection();
             var accessor = new CompositeResourceAccessor(
                     new DirectoryResourceAccessor(resources), new ClassLoaderResourceAccessor())) {
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase(changelog, accessor, database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
    }

    private static String fileSnapshot(JdbcTemplate jdbc, long id, boolean sourceOnly) {
        String projection = sourceOnly
                ? "to_jsonb(f) - 'indexer_status' - 'index_update_date' - 'last_message' - 'version'"
                : "to_jsonb(f)";
        return jdbc.queryForObject("SELECT (" + projection + ")::text FROM indexed_file f WHERE id = ?",
                String.class, id);
    }

    private static Map<String, String> retainedSnapshots(JdbcTemplate jdbc) {
        Map<String, String> snapshots = new LinkedHashMap<>();
        for (String table : RETAINED_TABLES) {
            snapshots.put(table, jdbc.queryForObject(
                    "SELECT COALESCE(jsonb_agg(to_jsonb(r) ORDER BY r.id), '[]'::jsonb)::text FROM " + table + " r",
                    String.class));
        }
        return snapshots;
    }

    private static void seedRetainedData(JdbcTemplate jdbc, long fileId) {
        long trackId = jdbc.queryForObject("""
                INSERT INTO gps_track (file_id, track_name, load_status, track_source,
                    activity_type, activity_type_source, statistics_exclusion_reason)
                VALUES (?, 'Synthetic user-edited track', 'SUCCESS', 'IMPORTED', 'HIKING', 'USER_SET', 'OTHER')
                RETURNING id
                """, Long.class, fileId);
        jdbc.update("""
                INSERT INTO gps_track (track_name, load_status, track_source, planner_waypoints_json)
                VALUES ('Synthetic saved plan', 'SUCCESS', 'PLANNED', '[{"lat":47,"lng":8}]')
                """);
        jdbc.update("""
                INSERT INTO gps_track_data (gps_track_id, track_type, precision_in_meter, track)
                VALUES (?, 'RAW', 0, ST_GeomFromText('LINESTRING(8 47,8.001 47.001)',4326))
                """, trackId);
        jdbc.update("""
                INSERT INTO gps_track_event (gps_track_id, event_type, source, label)
                VALUES (?, 'MANUAL_NOTE', 'USER', 'Synthetic retained note')
                """, trackId);
        jdbc.update("INSERT INTO google_health.connection(id) VALUES (1)");
        jdbc.update("""
                INSERT INTO google_health.data_point (connection_id, data_type, representation,
                    identity_key, fingerprint, raw_json, last_seen_run, indexed_file_id, gps_track_id, route_state)
                VALUES (1, 'exercise', 'RAW', repeat('a',64), repeat('b',64), '{}',
                    '00000000-0000-0000-0000-000000000001', ?, ?, 'IMPORTED')
                """, fileId, trackId);
    }

    private record FileFixture(long id, String index, String status, Long version,
                               String fullSnapshot, String sourceSnapshot) {
    }
}
