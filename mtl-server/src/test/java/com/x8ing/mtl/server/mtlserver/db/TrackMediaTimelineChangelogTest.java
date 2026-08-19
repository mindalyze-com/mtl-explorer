package com.x8ing.mtl.server.mtlserver.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TrackMediaTimelineChangelogTest {

    private static final String CHANGELOG = "db/changelog/changes/063.xml";
    private static final String MASTER_CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void addsTrackWindowAndCanonicalPointTimeIndexes() throws IOException {
        String changelog = readResource(CHANGELOG);

        assertThat(changelog)
                .contains("GPS_TRACK_IX_MEDIA_TIME_MATCH")
                .contains("media_file_ix_track_match_gps_time")
                .contains("media_file_ix_track_match_local_time")
                .contains("WHERE exif_gps_date IS NULL AND exif_date_image_taken IS NOT NULL")
                .contains("gps_track_data_points_ix_media_time_match")
                .contains("gps_track_data_id, point_timestamp, point_index")
                .contains("WHERE point_timestamp IS NOT NULL");
    }

    @Test
    void includesTimelineChangelogInMaster() throws IOException {
        assertThat(readResource(MASTER_CHANGELOG)).contains("/db/changelog/changes/063.xml");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = TrackMediaTimelineChangelogTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
