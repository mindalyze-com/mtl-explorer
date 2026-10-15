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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Run only against a disposable database prepared through changeset 088 or 089.
 */
@EnabledIfSystemProperty(named = "mtl.test.coverage-migration.jdbc-url", matches = "jdbc:postgresql:.*")
class MetabolicCoverageMigrationIntegrationTest {
    @TempDir
    Path resources;

    @Test
    void addsNullableExplanationsWithoutBackfillingOrQueuingTracks() throws Exception {
        var source = new DriverManagerDataSource(System.getProperty("mtl.test.coverage-migration.jdbc-url"),
                System.getProperty("mtl.test.coverage-migration.username", "review"),
                System.getProperty("mtl.test.coverage-migration.password", "review"));
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name = 'gps_track' AND column_name = 'metabolic_exclusion_reasons'", Integer.class)).isZero();
        try (var input = getClass().getClassLoader().getResourceAsStream("db/changelog/db.changelog-master.xml")) {
            Files.writeString(resources.resolve("before-coverage.xml"), new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("<include file=\"/db/changelog/changes/090.xml\"/>", ""));
        }
        try (var connection = source.getConnection();
             var accessor = new CompositeResourceAccessor(new DirectoryResourceAccessor(resources), new ClassLoaderResourceAccessor());
             var liquibase = new Liquibase("before-coverage.xml", accessor,
                     DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            liquibase.update(new Contexts(), new LabelExpression());
        }
        long tracks = jdbc.queryForObject("SELECT count(*) FROM gps_track", Long.class);
        var queueBefore = jdbc.queryForList("SELECT * FROM track_energy_recalculation ORDER BY track_id");
        for (int attempt = 0; attempt < 2; attempt++) {
            try (var connection = source.getConnection(); var accessor = new ClassLoaderResourceAccessor();
                 var liquibase = new Liquibase("db/changelog/db.changelog-master.xml", accessor,
                         DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM gps_track", Long.class)).isEqualTo(tracks);
            assertThat(jdbc.queryForList("SELECT * FROM track_energy_recalculation ORDER BY track_id")).isEqualTo(queueBefore);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM gps_track WHERE metabolic_exclusion_reasons IS NOT NULL
                    OR metabolic_unknown_duration IS NOT NULL OR metabolic_recording_gap_seconds IS NOT NULL
                    OR metabolic_sparse_sampling_seconds IS NOT NULL
                    """, Long.class)).isZero();
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns WHERE table_name = 'gps_track'
                AND column_name IN ('metabolic_exclusion_reasons', 'metabolic_unknown_duration',
                'metabolic_recording_gap_seconds', 'metabolic_sparse_sampling_seconds') AND is_nullable = 'YES'
                """, Integer.class)).isEqualTo(4);
    }
}
