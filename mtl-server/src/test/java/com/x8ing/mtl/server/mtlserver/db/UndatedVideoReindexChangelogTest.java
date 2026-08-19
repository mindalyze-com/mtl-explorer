package com.x8ing.mtl.server.mtlserver.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class UndatedVideoReindexChangelogTest {

    private static final String CHANGELOG = "db/changelog/changes/067.xml";
    private static final String MASTER_CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void requeuesOnlySuccessfullyIndexedUndatedSupportedVideos() throws IOException {
        String changelog = readResource(CHANGELOG);

        assertThat(changelog)
                .contains("indexed.index = 'MEDIA'")
                .contains("indexed.indexer_status = 'COMPLETED_WITH_SUCCESS'")
                .contains("LOWER(indexed.name) ~ '[.](3gp|avi|m4v|mov|mp4)$'")
                .contains("media.exif_gps_date IS NULL")
                .contains("media.exif_date_image_taken IS NULL")
                .contains("indexer_status = 'SCHEDULED'")
                .contains("version = COALESCE(indexed.version, 0) + 1");
    }

    @Test
    void includesOneOffReindexChangelogInMaster() throws IOException {
        assertThat(readResource(MASTER_CHANGELOG)).contains("/db/changelog/changes/067.xml");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = UndatedVideoReindexChangelogTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
