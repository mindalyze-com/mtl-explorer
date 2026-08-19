package com.x8ing.mtl.server.mtlserver.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PendingTrackVisibilityChangelogTest {

    private static final String CHANGELOG = "db/changelog/changes/069.xml";
    private static final String MASTER_CHANGELOG = "db/changelog/db.changelog-master.xml";
    private static final String VISIBLE_DUPLICATE_STATUSES =
            "duplicate_status IN ('UNIQUE', 'NOT_CHECKED_YET')";

    @Test
    void smartBaseFilterKeepsTheLatestParametersAndShowsSuccessfulPendingImports() throws IOException {
        String smartBaseUpdate = updateEndingWith(
                readResource(CHANGELOG),
                "<where>filter_domain = 'GPS_TRACK' AND filter_name = 'SmartBaseFilter'</where>");

        assertThat(smartBaseUpdate)
                .contains(VISIBLE_DUPLICATE_STATUSES)
                .contains("gt.load_status = 'SUCCESS'")
                .contains("gt.track_source = 'IMPORTED'")
                .contains(":DATE_TIME_FROM")
                .contains(":DATE_TIME_TO")
                .contains(":TRACK_IDS")
                .contains(":GEO_CIRCLE_1_POINT")
                .contains(":GEO_RECTANGLE_1_MIN_LAT")
                .contains(":GEO_POLYGON_1")
                .contains("ST_DWithin")
                .contains("ST_MakeEnvelope")
                .contains("ST_GeomFromText")
                .doesNotContain("duplicate_status = 'UNIQUE'")
                .doesNotContain("'DUPLICATE'")
                .doesNotContain("'EXCLUDED'");
    }

    @Test
    void tracksByYearUsesTheSameSuccessfulImportedTrackVisibilityRule() throws IOException {
        String tracksByYearUpdate = updateEndingWith(
                readResource(CHANGELOG),
                "<where>filter_domain = 'GPS_TRACK' AND filter_name = 'TracksByYear'</where>");

        assertThat(tracksByYearUpdate)
                .contains(VISIBLE_DUPLICATE_STATUSES)
                .contains("gt.load_status = 'SUCCESS'")
                .contains("gt.track_source = 'IMPORTED'")
                .contains(":YEAR_FROM")
                .contains(":YEAR_TO")
                .doesNotContain("duplicate_status = 'UNIQUE'")
                .doesNotContain("'DUPLICATE'")
                .doesNotContain("'EXCLUDED'");
    }

    @Test
    void includesPendingTrackVisibilityChangelogInMaster() throws IOException {
        assertThat(readResource(MASTER_CHANGELOG)).contains("/db/changelog/changes/069.xml");
    }

    private static String updateEndingWith(String changelog, String whereClause) {
        int end = changelog.indexOf(whereClause);
        assertThat(end).isGreaterThanOrEqualTo(0);
        int start = changelog.lastIndexOf("<update tableName=\"filter_config\">", end);
        assertThat(start).isGreaterThanOrEqualTo(0);
        return changelog.substring(start, end + whereClause.length());
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = PendingTrackVisibilityChangelogTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
