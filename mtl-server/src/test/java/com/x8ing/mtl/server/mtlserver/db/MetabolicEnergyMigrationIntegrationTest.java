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
 * Requires a fresh disposable PostGIS database; never points at application data by default.
 */
@EnabledIfSystemProperty(named = "mtl.test.metabolic-migration.jdbc-url", matches = "jdbc:postgresql:.*")
class MetabolicEnergyMigrationIntegrationTest {
    private static final String MASTER = "db/changelog/db.changelog-master.xml";
    @TempDir
    Path resources;

    @Test
    void addsNullableModelAndQueuesReadyTracksExactlyOnceWithoutDeletingData() throws Exception {
        var source = new DriverManagerDataSource(System.getProperty("mtl.test.metabolic-migration.jdbc-url"), "review", "review");
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.gps_track')::text", String.class)).isNull();
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS postgis");
        try (var input = getClass().getClassLoader().getResourceAsStream(MASTER)) {
            String original = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Files.writeString(resources.resolve("before-metabolic.xml"), original.replace(
                    "<include file=\"/db/changelog/changes/089.xml\"/>", "").replace("<include file=\"/db/changelog/changes/090.xml\"/>", ""));
        }
        migrate(source, "before-metabolic.xml");
        long ready = track(jdbc, "IMPORTED", "RUNNING", false, true);
        track(jdbc, "PLANNED", "RUNNING", false, true);
        track(jdbc, "IMPORTED", null, false, true);
        track(jdbc, "IMPORTED", "RUNNING", true, true);
        track(jdbc, "IMPORTED", "RUNNING", false, false);
        jdbc.update("INSERT INTO track_energy_recalculation VALUES (?, 4, now(), now(), 'PreviousFailure')", ready);
        migrate(source, MASTER);
        assertThat(jdbc.queryForList("SELECT track_id FROM track_energy_recalculation", Long.class)).containsExactly(ready);
        assertThat(jdbc.queryForObject("SELECT generation FROM track_energy_recalculation WHERE track_id = ?", Long.class, ready)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT last_error FROM track_energy_recalculation WHERE track_id = ?", String.class, ready)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gps_track", Long.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT metabolic_active_kcal FROM gps_track WHERE id = ?", Double.class, ready)).isNull();
        jdbc.update("DELETE FROM track_energy_recalculation");
        migrate(source, MASTER);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM track_energy_recalculation", Long.class)).isZero();
    }

    private static long track(JdbcTemplate jdbc, String source, String activity, boolean unsupported, boolean withPoints) {
        long id = jdbc.queryForObject("INSERT INTO gps_track(track_name, track_source, activity_type, timing_unreliable) VALUES ('Synthetic metabolic test', ?, ?, ?) RETURNING id",
                Long.class, source, activity, unsupported);
        if (withPoints) {
            long data = jdbc.queryForObject("INSERT INTO gps_track_data(gps_track_id, track_type, precision_in_meter) VALUES (?, 'RAW_OUTLIER_CLEANED', 0) RETURNING id", Long.class, id);
            jdbc.update("INSERT INTO gps_track_data_points(id, gps_track_data_id, moving_window_in_sec, point_index, point_index_max, point_long_lat) VALUES (nextval('gps_track_data_points_id_seq'), ?, 30, 0, 0, ST_SetSRID(ST_Point(8, 47), 4326))", data);
        }
        return id;
    }

    private void migrate(DriverManagerDataSource source, String changelog) throws Exception {
        try (var connection = source.getConnection();
             var accessor = new CompositeResourceAccessor(new DirectoryResourceAccessor(resources), new ClassLoaderResourceAccessor());
             var liquibase = new Liquibase(changelog, accessor,
                     DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
            liquibase.update(new Contexts(), new LabelExpression());
        }
    }
}
