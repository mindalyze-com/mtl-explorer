package com.x8ing.mtl.server.mtlserver.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleHealthChangelogTest {

    private static final String CHANGELOG = "db/changelog/changes/070.xml";
    private static final String RECORD_BROWSER_CHANGELOG = "db/changelog/changes/073.xml";
    private static final String SUMMARY_CHANGELOG = "db/changelog/changes/074.xml";
    private static final String MASTER_CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void createsIsolatedMirrorWithSafeCascadeBoundariesAndRequiredIndexes() throws IOException {
        String changelog = read(CHANGELOG);

        assertThat(changelog)
                .contains("CREATE SCHEMA google_health")
                .contains("CREATE TABLE google_health.connection")
                .contains("CREATE TABLE google_health.sync_state")
                .contains("CREATE TABLE google_health.data_source")
                .contains("CREATE TABLE google_health.data_point")
                .contains("CREATE TABLE google_health.heart_rate_sample")
                .contains("REFERENCES google_health.connection(id) ON DELETE CASCADE")
                .contains("REFERENCES public.indexed_file(id) ON DELETE SET NULL")
                .contains("REFERENCES public.gps_track(id) ON DELETE SET NULL")
                .contains("uq_google_health_data_point_identity")
                .contains("uq_google_health_heart_rate_sample")
                .contains("USING brin (sample_time)")
                .contains("route_fingerprint char(64)")
                .contains("active_window_run_id uuid")
                .contains("active_imported_count bigint")
                .contains("active_pages_complete boolean")
                .contains("active_backfill_complete boolean")
                .doesNotContain("REFERENCES public.gps_track(id) ON DELETE CASCADE");

        String heartRateTable = changelog.substring(
                changelog.indexOf("CREATE TABLE google_health.heart_rate_sample"),
                changelog.indexOf("CREATE INDEX idx_google_health_sync_state_updated"));
        assertThat(heartRateTable).doesNotContain("raw_json");
    }

    @Test
    void addsAnIndexForTheRecordBrowserOrderingExpression() throws IOException {
        assertThat(read(RECORD_BROWSER_CHANGELOG))
                .contains("idx_google_health_data_point_record_time")
                .contains("connection_id")
                .contains("COALESCE(physical_start_time")
                .contains("civil_date::timestamp AT TIME ZONE 'UTC'")
                .contains("id DESC");
    }

    @Test
    void addsCoveringIndexesForExactRecordSummaries() throws IOException {
        assertThat(read(SUMMARY_CHANGELOG))
                .contains("idx_google_health_heart_rate_summary")
                .contains("heart_rate_sample (connection_id)")
                .contains("idx_google_health_data_point_type_summary")
                .contains("data_point (connection_id, data_type, representation)")
                .contains("INCLUDE (physical_start_time, civil_date)");
    }

    @Test
    void masterIncludesGoogleHealthChangelogs() throws IOException {
        assertThat(read(MASTER_CHANGELOG))
                .contains("/db/changelog/changes/070.xml")
                .contains("/db/changelog/changes/071.xml")
                .contains("/db/changelog/changes/072.xml")
                .contains("/db/changelog/changes/073.xml")
                .contains("/db/changelog/changes/074.xml")
                .contains("/db/changelog/changes/110.xml");
    }

    private static String read(String path) throws IOException {
        try (InputStream input = GoogleHealthChangelogTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
