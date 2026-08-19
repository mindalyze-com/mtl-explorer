package com.x8ing.mtl.server.mtlserver.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MediaPositionPersistenceChangelogTest {

    private static final String CHANGELOG = "db/changelog/changes/064.xml";
    private static final String TRACK_DATA_CHANGELOG = "db/changelog/changes/065.xml";
    private static final String TRACK_POINT_CHANGELOG = "db/changelog/changes/066.xml";
    private static final String MASTER_CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void keepsEvidenceCorrelationsOverridesAndResolvedProjectionSeparate() throws IOException {
        String changelog = readResource(CHANGELOG);

        assertThat(changelog)
                .contains("CREATE TABLE media_time_correction")
                .contains("CREATE TABLE media_manual_location")
                .contains("CREATE TABLE media_track_correlation")
                .contains("CREATE TABLE media_resolved_location")
                .contains("EXIF_EMBEDDED", "TRACK_INTERPOLATED", "USER_ASSIGNED")
                .contains("media_resolved_location_ix_location")
                .contains("media_track_correlation_ix_track_timeline")
                .contains("media_correlation_media_work")
                .contains("media_correlation_track_work")
                .contains("attempt_count INTEGER NOT NULL DEFAULT 0")
                .contains("last_error VARCHAR(1000)")
                .contains("retry_after TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
                .contains("queue_media_correlation_before_track_delete")
                .contains("initial_projection");
    }

    @Test
    void includesPersistenceChangelogInMaster() throws IOException {
        assertThat(readResource(MASTER_CHANGELOG))
                .contains("/db/changelog/changes/064.xml")
                .contains("/db/changelog/changes/065.xml")
                .contains("/db/changelog/changes/066.xml");
    }

    @Test
    void queuesCorrelationWhenCanonicalTrackDataChanges() throws IOException {
        assertThat(readResource(TRACK_DATA_CHANGELOG))
                .contains("queue_media_correlation_for_track_data")
                .contains("RAW_OUTLIER_CLEANED")
                .contains("AFTER INSERT OR UPDATE OF gps_track_id, track_type, track OR DELETE ON gps_track_data");
    }

    @Test
    void queuesCanonicalPointChangesOncePerStatement() throws IOException {
        assertThat(readResource(TRACK_POINT_CHANGELOG))
                .contains("REFERENCING NEW TABLE AS inserted_points")
                .contains("REFERENCING OLD TABLE AS old_points NEW TABLE AS new_points")
                .contains("REFERENCING OLD TABLE AS deleted_points")
                .contains("FOR EACH STATEMENT")
                .contains("RAW_OUTLIER_CLEANED");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = MediaPositionPersistenceChangelogTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
