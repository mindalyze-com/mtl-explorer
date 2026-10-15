package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository.RecentPass;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record GHStreamSyncStatus(String dataType, String representation, String calendarPolicy, String keyPolicy, String timeDomain,
                                 Instant fastStart, Instant fastEnd, Instant lastFastCompletedAt, boolean budgetLimited, Instant newestStoredRecord,
                                 RecentPass fullPass, LocalDate finalizedBackTo, long pendingDays, long parkedDays,
                                 Instant activeHistoryStart, Instant activeHistoryEnd, List<GHFailedRange> failures) {
}
