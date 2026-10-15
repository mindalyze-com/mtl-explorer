package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import java.time.Instant;

public record GHSyncState(
        String dataType,
        String representation,
        Instant cursorTime,
        Instant oldestSyncedTime,
        Instant activeWindowStart,
        Instant activeWindowEnd,
        String activePageToken,
        boolean backfillComplete,
        Instant lastAttemptAt,
        Instant lastSuccessAt,
        String lastError,
        long importedCount,
        long deletedCount
) {
}
