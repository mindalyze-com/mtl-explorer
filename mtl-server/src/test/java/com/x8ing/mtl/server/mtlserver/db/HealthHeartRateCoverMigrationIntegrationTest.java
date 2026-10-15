package com.x8ing.mtl.server.mtlserver.db;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the concurrent migration and rollback only in a fresh, disposable database. */
@EnabledIfSystemProperty(named = "mtl.test.health-cover.jdbc-url", matches = "jdbc:postgresql:.*")
class HealthHeartRateCoverMigrationIntegrationTest {
    private static final String CHANGELOG = "db/changelog/changes/110.xml";
    private static final String ORIGINAL_INDEX = "google_health.idx_google_health_heart_rate_time";
    private static final String COVER_INDEX = "google_health.idx_google_health_heart_rate_time_cover";
    private static final String ORIGINAL_CHECKSUM = "9:e84813617a4c04abaa600f1313ddbe94";
    private static final int SYNTHETIC_SAMPLES = 20_000;

    @Test
    void recoversInterruptedBuildsWithoutChangingAlreadyAppliedIndexesOrSamples() throws Exception {
        var source = new DriverManagerDataSource(System.getProperty("mtl.test.health-cover.jdbc-url"),
                System.getProperty("mtl.test.health-cover.username", "health_simulation"),
                System.getProperty("mtl.test.health-cover.password", "health_simulation"));
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
                .startsWith("mtl_health_simulation_");
        assertThat(jdbc.queryForObject("SELECT to_regclass('google_health.heart_rate_sample')::text", String.class))
                .isNull();
        jdbc.execute("CREATE SCHEMA google_health");
        jdbc.execute("""
                CREATE TABLE google_health.heart_rate_sample (
                    id bigint PRIMARY KEY,
                    connection_id smallint NOT NULL,
                    sample_time timestamptz NOT NULL,
                    beats_per_minute numeric NOT NULL
                )
                """);
        jdbc.execute("CREATE INDEX idx_google_health_heart_rate_time "
                     + "ON google_health.heart_rate_sample (sample_time DESC, id DESC)");
        jdbc.update("""
                INSERT INTO google_health.heart_rate_sample
                SELECT id, 1, TIMESTAMPTZ '2026-01-01 00:00:00+00' + id * INTERVAL '3 seconds',
                       60 + id % 80
                  FROM generate_series(1, ?) id
                """, SYNTHETIC_SAMPLES);
        String originalDefinition = definition(jdbc, ORIGINAL_INDEX);
        Long originalOid = oid(jdbc, ORIGINAL_INDEX);
        String samples = sampleChecksum(jdbc);

        for (boolean leftoverInvalidIndex : new boolean[]{false, true}) {
            if (leftoverInvalidIndex) {
                // Fail during the concurrent scan, after PostgreSQL commits the index catalogue entry.
                assertThatThrownBy(() -> jdbc.execute("CREATE INDEX CONCURRENTLY idx_google_health_heart_rate_time_cover "
                        + "ON google_health.heart_rate_sample ((1 / (beats_per_minute - 60)))"))
                        .hasMessageContaining("division by zero");
                assertThat(jdbc.queryForObject("SELECT indisvalid FROM pg_index WHERE indexrelid = ?::regclass",
                        Boolean.class, COVER_INDEX)).isFalse();
            }
            verifyMigrationAndRollback(source, jdbc, originalDefinition, originalOid, samples);
        }
    }

    private void verifyMigrationAndRollback(DriverManagerDataSource source, JdbcTemplate jdbc,
                                            String originalDefinition, Long originalOid, String samples) throws Exception {
        Long appliedCoverOid = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try (var connection = source.getConnection(); var accessor = new ClassLoaderResourceAccessor();
                 var liquibase = new Liquibase(CHANGELOG, accessor,
                         DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
            assertThat(oid(jdbc, ORIGINAL_INDEX)).isEqualTo(originalOid);
            assertThat(definition(jdbc, ORIGINAL_INDEX)).isEqualTo(originalDefinition);
            assertThat(definition(jdbc, COVER_INDEX))
                    .contains("(sample_time DESC, id DESC) INCLUDE (connection_id, beats_per_minute)");
            assertThat(jdbc.queryForObject("""
                    SELECT indisvalid AND indisready AND NOT indisunique
                       AND indnkeyatts = 2 AND indnatts = 4
                      FROM pg_index WHERE indexrelid = ?::regclass
                    """, Boolean.class, COVER_INDEX)).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample", Integer.class))
                    .isEqualTo(SYNTHETIC_SAMPLES);
            assertThat(sampleChecksum(jdbc)).isEqualTo(samples);
            if (attempt == 0) {
                appliedCoverOid = oid(jdbc, COVER_INDEX);
                // Databases which applied the original changeset must validate without rebuilding it.
                assertThat(jdbc.update("UPDATE databasechangelog SET md5sum = ? WHERE id = ?",
                        ORIGINAL_CHECKSUM, "20261014_google_health_heart_rate_time_cover")).isEqualTo(1);
            } else assertThat(oid(jdbc, COVER_INDEX)).isEqualTo(appliedCoverOid);
        }

        try (var connection = source.getConnection(); var accessor = new ClassLoaderResourceAccessor();
             var liquibase = new Liquibase(CHANGELOG, accessor,
                     DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            liquibase.rollback(1, "");
        }
        assertThat(oid(jdbc, COVER_INDEX)).isNull();
        assertThat(oid(jdbc, ORIGINAL_INDEX)).isEqualTo(originalOid);
        assertThat(definition(jdbc, ORIGINAL_INDEX)).isEqualTo(originalDefinition);
        assertThat(sampleChecksum(jdbc)).isEqualTo(samples);
    }

    private static Long oid(JdbcTemplate jdbc, String index) {
        return jdbc.queryForObject("SELECT to_regclass(?)::oid::bigint", Long.class, index);
    }

    private static String definition(JdbcTemplate jdbc, String index) {
        return jdbc.queryForObject("SELECT pg_get_indexdef(?::regclass)", String.class, index);
    }

    private static String sampleChecksum(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT md5(string_agg(ROW(id, connection_id, sample_time, beats_per_minute)::text, ',' ORDER BY id))
                  FROM google_health.heart_rate_sample
                """, String.class);
    }
}
