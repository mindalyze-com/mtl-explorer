package com.x8ing.mtl.server.mtlserver.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ExactActivityTypeFilterChangelogTest {

    private static final String CHANGELOG = "db/changelog/changes/061.xml";
    private static final String MASTER_CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void registersAnActivityViewWithExactActivityTypeGroups() throws IOException {
        String changelog = readResource(CHANGELOG);

        assertThat(changelog)
                .contains("ActivitiesByExactType")
                .contains("Activities by exact type")
                .contains("[[~{/GPS_TRACK/SmartBaseFilter}]]")
                .contains("gt.activity_type::text AS grp")
                .contains("gt.activity_type IS NOT NULL")
                .contains("<column name=\"filter_group\" value=\"Activity\"/>")
                .contains("Activities by main group");
    }

    @Test
    void includesTheExactActivityViewChangelogInTheMaster() throws IOException {
        assertThat(readResource(MASTER_CHANGELOG)).contains("/db/changelog/changes/061.xml");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = ExactActivityTypeFilterChangelogTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
