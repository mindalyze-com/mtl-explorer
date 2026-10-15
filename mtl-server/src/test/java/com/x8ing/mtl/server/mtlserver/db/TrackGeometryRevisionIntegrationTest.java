package com.x8ing.mtl.server.mtlserver.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import liquibase.Liquibase;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs all production triggers in a fully migrated, disposable PostGIS cluster.
 * Historical grants also require an empty database named mtl in that cluster.
 */
@EnabledIfSystemProperty(named = "mtl.test.track-cache-version.jdbc-url", matches = "jdbc:postgresql:.*")
class TrackGeometryRevisionIntegrationTest {
    private Connection connection;
    private JdbcTemplate jdbc;

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getProperty("mtl.test.track-cache-version.jdbc-url"),
                System.getProperty("mtl.test.track-cache-version.username", "review"),
                System.getProperty("mtl.test.track-cache-version.password", "review"));
    }

    @BeforeAll
    static void migrateDisposableDatabase() throws Exception {
        try (var connection = connect()) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
                    .startsWith("mtl_cache_revision_");
            try (var accessor = new ClassLoaderResourceAccessor();
                 var liquibase = new Liquibase("db/changelog/db.changelog-master.xml", accessor,
                         DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)))) {
                liquibase.update(new Contexts(), new LabelExpression());
                // Also works after 111/112 have already been applied.
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
    }

    @BeforeEach
    void insertSyntheticTracks() throws Exception {
        connection = connect();
        connection.setAutoCommit(false);
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        jdbc.update("INSERT INTO gps_track(id) VALUES (1),(2),(3)");
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
    void revisesEachAffectedTrackOncePerStatementIncludingBothParentsAfterReassignment() {
        jdbc.update("INSERT INTO gps_track_data(id,gps_track_id,track_type) VALUES (11,1,'overview'),(12,1,'detail'),(21,2,'overview')");
        assertThat(versions()).containsExactly(1L, 1L, 0L);

        jdbc.update("UPDATE gps_track_data SET track_type='changed' WHERE gps_track_id=1");
        assertThat(versions()).containsExactly(2L, 1L, 0L);

        jdbc.update("UPDATE gps_track_data SET gps_track_id=2 WHERE id=12");
        assertThat(versions()).containsExactly(3L, 2L, 0L);

        jdbc.update("DELETE FROM gps_track_data");
        assertThat(versions()).containsExactly(4L, 3L, 0L);

        jdbc.update("UPDATE gps_track_data SET track_type='empty statement'");
        assertThat(versions()).containsExactly(4L, 3L, 0L);
    }

    @Test
    void batchesVariantRevisionsAndRollsThemBackWithGeometry() throws SQLException {
        jdbc.update("INSERT INTO gps_track_data(id,gps_track_id,track_type) SELECT i,1,'synthetic variant' FROM generate_series(1,1000) i");
        assertThat(versions()).containsExactly(1L, 0L, 0L);
        var savepoint = connection.setSavepoint();

        jdbc.update("UPDATE gps_track_data SET track_type='replacement'");
        assertThat(versions()).containsExactly(2L, 0L, 0L);
        connection.rollback(savepoint);

        assertThat(versions()).containsExactly(1L, 0L, 0L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gps_track_data WHERE track_type='synthetic variant'", Long.class))
                .isEqualTo(1000);
    }

    @Test
    void deletingGeometryThenItsParentDoesNotReviseOtherTracksOrRecreateTheDeletedParent() {
        jdbc.update("INSERT INTO gps_track_data(id,gps_track_id,track_type) VALUES (11,1,'overview'),(12,1,'detail'),(21,2,'overview')");

        // GPXStoreService removes dependent geometry before deleting the parent.
        jdbc.update("DELETE FROM gps_track_data WHERE gps_track_id=1");
        jdbc.update("DELETE FROM gps_track WHERE id=1");

        assertThat(versions()).containsExactly(1L, 0L);
        assertThat(jdbc.queryForList("SELECT gps_track_id FROM gps_track_data", Long.class)).containsExactly(2L);
    }

    private List<Long> versions() {
        return jdbc.queryForList("SELECT version FROM gps_track WHERE id IN (1,2,3) ORDER BY id", Long.class);
    }

    @Test
    void separateGeometryVariantInsertsReviseFreshnessWithoutDuplicatingMetadataAudit() {
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        jdbc.execute("SET CONSTRAINTS ALL DEFERRED");
        long tracksBefore = freshness("tracks");
        long geometryBefore = freshness("track_geometry");
        assertThat(auditRows()).isEqualTo(1);
        for (int variant = 0; variant < 6; variant++) {
            jdbc.update("INSERT INTO gps_track_data(gps_track_id,track_type,precision_in_meter,track) "
                    + "VALUES (1, ?, ?, ST_GeomFromText('LINESTRING(8 47,8.01 47.01)',4326))",
                    "synthetic-" + variant, variant);
        }
        assertThat(versions()).containsExactly(6L, 0L, 0L);
        assertThat(auditRows()).isEqualTo(1);
        assertThat(freshness("tracks")).isEqualTo(tracksBefore);
        assertThat(freshness("track_geometry")).isEqualTo(geometryBefore);
        // Execute commit-time freshness triggers without committing the fixture.
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        assertThat(freshness("tracks")).isEqualTo(tracksBefore + 6);
        assertThat(freshness("track_geometry")).isEqualTo(geometryBefore + 6);
        jdbc.update("UPDATE gps_track SET track_name='Edited metadata' WHERE id=1");
        assertThat(versions()).containsExactly(7L, 0L, 0L);
        assertThat(auditRows()).isEqualTo(2);
    }

    private long auditRows() {
        return jdbc.queryForObject("SELECT count(*) FROM gps_track_audit WHERE id=1", Long.class);
    }

    private long freshness(String domain) {
        return jdbc.queryForObject("SELECT revision FROM data_freshness WHERE domain_key=?", Long.class, domain);
    }
}
