package com.x8ing.mtl.server.mtlserver.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MediaTechnicalMetadataChangelogTest {

    private static final String CHANGELOG = "db/changelog/changes/068.xml";
    private static final String MASTER_CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void addsAndAuditsEveryTechnicalMetadataColumn() throws IOException {
        String changelog = readResource(CHANGELOG);

        List.of(
                "gps_altitude_meters",
                "lens_model",
                "width_pixels",
                "height_pixels",
                "aperture_f_number",
                "exposure_time_seconds",
                "iso_speed",
                "focal_length_mm",
                "focal_length_35mm",
                "duration_seconds",
                "frame_rate",
                "video_codec",
                "audio_codec"
        ).forEach(column -> assertThat(changelog)
                .contains("name=\"" + column + "\"")
                .contains("OLD." + column)
                .contains("NEW." + column));
    }

    @Test
    void includesTechnicalMetadataChangelogInMaster() throws IOException {
        assertThat(readResource(MASTER_CHANGELOG)).contains("/db/changelog/changes/068.xml");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = MediaTechnicalMetadataChangelogTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
