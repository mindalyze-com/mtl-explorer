package com.x8ing.mtl.server.mtlserver.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MediaFileAuditChangelogTest {

    private static final String CHANGELOG = "db/changelog/changes/062.xml";
    private static final String MASTER_CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void auditsEveryMediaFileOperationAndMetadataColumn() throws IOException {
        String changelog = readResource(CHANGELOG);

        assertThat(changelog)
                .contains("tableName=\"media_file_audit\"")
                .contains("MEDIA_FILE_AUDIT_IX_INDEX")
                .contains("MEDIA_FILE_AUDIT_IX_FILE_ID")
                .contains("CREATE OR REPLACE FUNCTION media_file_audit_trigger_function()")
                .contains("CREATE TRIGGER media_file_audit_trigger")
                .contains("AFTER INSERT OR UPDATE OR DELETE ON media_file");

        List.of(
                "id",
                "file_id",
                "cre_date",
                "exif_gps_location_long",
                "exif_gps_location_lat",
                "exif_gps_location",
                "exif_gps_date",
                "exif_date_image_taken",
                "camera_make",
                "camera_model"
        ).forEach(column -> assertThat(changelog)
                .contains("OLD." + column)
                .contains("NEW." + column));
    }

    @Test
    void includesMediaFileAuditChangelogInMaster() throws IOException {
        assertThat(readResource(MASTER_CHANGELOG)).contains("/db/changelog/changes/062.xml");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = MediaFileAuditChangelogTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
