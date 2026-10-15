package com.x8ing.mtl.server.mtlserver.googlehealth.provider;

import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Paged Google Health data and the synchronization lifecycle, independent of transport.
 */
public interface GoogleHealthProvider {
    String ALL_SOURCES = "users/me/dataSourceFamilies/all-sources";

    Page listRaw(DataTypePolicy.Policy policy, Instant start, Instant end, String pageToken);

    Page reconcile(DataTypePolicy.Policy policy, Instant start, Instant end, String pageToken);

    Page dailyRollUp(DataTypePolicy.Policy policy, LocalDate start, LocalDate end, String pageToken);

    void beginCycle();

    boolean coolingDown();

    void purpose(String purpose);

    void countProbe();

    Counters counters();

    record Page(List<JsonNode> points, String nextPageToken) {
        public boolean hasNextPage() {
            return nextPageToken != null && !nextPageToken.isBlank();
        }
    }

    record Counters(long attempts, long retries, long rateLimits, long pages,
                    @Schema(description = "Decoded response bytes consumed, including failed responses and route exports; not wire traffic.") long bytes,
                    long probes, long routeAttempts) {
    }

    final class QuotaPause extends RuntimeException {
    }
}
