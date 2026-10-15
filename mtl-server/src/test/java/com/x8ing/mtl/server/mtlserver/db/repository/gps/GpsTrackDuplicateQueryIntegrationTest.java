package com.x8ing.mtl.server.mtlserver.db.repository.gps;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes the repository SQL on PostgreSQL; a temporary table isolates all fixture rows.
 */
@EnabledIfSystemProperty(named = "mtl.test.duplicates.jdbc-url", matches = "jdbc:postgresql:.*")
class GpsTrackDuplicateQueryIntegrationTest {
    private Connection connection;
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void createSyntheticDuplicateGroups() throws SQLException {
        connection = DriverManager.getConnection(System.getProperty("mtl.test.duplicates.jdbc-url"),
                System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
        connection.setAutoCommit(false);
        jdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true));
        jdbc.getJdbcTemplate().execute("""
                CREATE TEMP TABLE gps_track (
                    id bigint PRIMARY KEY,
                    duplicate_of bigint,
                    duplicate_status text
                ) ON COMMIT DROP
                """);
        jdbc.getJdbcTemplate().execute("""
                INSERT INTO gps_track (id, duplicate_of, duplicate_status) VALUES
                    (101, NULL, 'UNIQUE'), (102, 101, 'DUPLICATE'), (103, 101, 'DUPLICATE'),
                    (104, NULL, 'UNIQUE'), (105, NULL, 'NOT_CHECKED_YET'), (106, NULL, 'EXCLUDED'),
                    (107, NULL, 'UNIQUE'), (108, 107, 'DUPLICATE')
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
    void primaryListsItsDuplicatesWithoutIncludingItselfOrAnotherGroup() throws ReflectiveOperationException {
        assertThat(duplicates(101)).containsExactly(102L, 103L);
    }

    @Test
    void duplicateListsItsPrimaryAndSiblingWithoutIncludingItself() throws ReflectiveOperationException {
        assertThat(duplicates(102)).containsExactly(101L, 103L);
        assertThat(duplicates(103)).containsExactly(101L, 102L);
        assertThat(duplicates(108)).containsExactly(107L);
    }

    @Test
    void tracksWithoutDuplicatesNeverListThemselves() throws ReflectiveOperationException {
        for (long id : List.of(104L, 105L, 106L)) {
            assertThat(duplicates(id)).as("track %s", id).isEmpty();
        }
    }

    @Test
    void missingTrackHasNoDuplicateGroup() throws ReflectiveOperationException {
        assertThat(duplicates(999)).isEmpty();
    }

    @Test
    void deletionLookupReturnsOnlyTracksReferencingTheDeletedTrack() throws ReflectiveOperationException {
        assertThat(query("getDuplicateReferenceIdsForGpsTrackId", 101)).containsExactly(102L, 103L);
        assertThat(query("getDuplicateReferenceIdsForGpsTrackId", 102)).isEmpty();
        assertThat(query("getDuplicateReferenceIdsForGpsTrackId", 999)).isEmpty();
    }

    private List<Long> duplicates(long trackId) throws ReflectiveOperationException {
        return query("getDuplicatesForGpsTrackId", trackId);
    }

    private List<Long> query(String method, long trackId) throws ReflectiveOperationException {
        String sql = GpsTrackRepository.class.getMethod(method, Long.class)
                .getAnnotation(Query.class).value();
        return jdbc.query(sql, Map.of("gps_track_id", trackId), (row, index) -> row.getLong("id"));
    }
}
