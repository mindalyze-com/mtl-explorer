package com.x8ing.mtl.server.mtlserver.sourceissues;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SourceIssueModels {
    private SourceIssueModels() {
    }

    public enum Severity {INFORMATION, REVIEW}
    public enum State {PENDING, ACKNOWLEDGED, INFORMATION, RESOLVED}
    public enum Reason {
        EMPTY_FILE, MEDIA_METADATA_UNREADABLE, SOURCE_UNREADABLE, CONVERSION_FAILED, PARSE_FAILED,
        ASSESSMENT_LIMIT, INVALID_VALUE, NO_OBSERVATIONS, TIME_MISSING, TIME_DUPLICATE, TIME_BACKWARD,
        TIME_EXCURSION, TIME_ESTIMATED, TIMING_UNRELIABLE, POSITION_INVALID, POSITION_JUMP,
        POSITION_CORRECTION, WEAK_SOURCE_FIX, RECORDING_GAP, RECORDING_BREAK, ELEVATION_MISSING,
        ELEVATION_SPIKE, ELEVATION_CORRECTION, EXCESSIVE_FRAGMENTATION, INCONSISTENT_TIME_DOMAIN,
        INCONSISTENT_METRIC_TOTALS, PROCESSING_EVIDENCE_UNAVAILABLE, PROCESSED_SOURCE_CHANGED
    }

    public record Finding(String stage, Reason reason, Severity severity, int sectionIndex,
                          String findingHash, String message, Map<String, Object> details) {
    }

    public record SourceIssueTarget(long issueId, String sourceRevision) {
    }

    public record SourceIssueMutationRequest(List<SourceIssueTarget> targets) {
    }

    @JsonPropertyOrder({"index", "pendingFileCount", "acknowledgedFileCount"})
    public record SourceIssueIndexSummary(String index, long pendingFileCount, long acknowledgedFileCount) {
    }

    public record SourceIssueSummary(long pendingFileCount, long acknowledgedFileCount, List<SourceIssueIndexSummary> indexes) {
    }

    @JsonPropertyOrder({"pendingFileCount", "targets"})
    public record SourceIssueTargetsSnapshot(long pendingFileCount, List<SourceIssueTarget> targets) {
    }

    @JsonPropertyOrder({"newlyAcknowledged", "alreadyAcknowledged", "staleOrResolved", "reopened", "changedTargets", "summary"})
    public record SourceIssueMutationResult(int newlyAcknowledged, int alreadyAcknowledged, int staleOrResolved,
                                           int reopened, List<SourceIssueTarget> changedTargets, SourceIssueSummary summary) {
    }

    @JsonPropertyOrder({"issueId", "sourceId", "index", "fileName", "sourcePath", "sourceRevision", "stage",
            "reason", "severity", "sectionIndex", "message", "details", "state", "firstSeenAt", "lastSeenAt",
            "resolvedAt", "acknowledgedAt"})
    public record SourceIssue(long issueId, long sourceId, String index, String fileName, String sourcePath,
                        String sourceRevision, String stage, Reason reason, Severity severity, int sectionIndex,
                        String message, Map<String, Object> details, State state, Instant firstSeenAt,
                        Instant lastSeenAt, Instant resolvedAt, Instant acknowledgedAt) {
    }

    public record SourceIssuePage(long total, List<SourceIssue> issues, SourceIssueSummary summary) {
    }
}
