package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

@JsonPropertyOrder({"pendingCount", "failedCount", "lastError", "nextRetryAt", "retainedCount"})
public record RouteSummary(long pendingCount, long failedCount, String lastError, Instant nextRetryAt, long retainedCount) {
    public RouteSummary(long pending, long failed, String error, Instant retry) {
        this(pending, failed, error, retry, 0);
    }

    public static RouteSummary empty() {
        return new RouteSummary(0, 0, null, null);
    }
}
