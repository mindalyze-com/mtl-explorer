package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import java.time.Instant;

public record MirrorSummaryRow(
        long recordCount,
        Instant earliestRecord,
        Instant latestRecord
) {
    public static MirrorSummaryRow empty() {
        return new MirrorSummaryRow(0L, null, null);
    }
}
