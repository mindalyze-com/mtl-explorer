package com.x8ing.mtl.server.mtlserver.trackreview;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosis;
import com.x8ing.mtl.server.gpsquality.diagnostics.TrackObservation;

import java.time.Instant;
import java.util.List;

/**
 * API contracts; core algorithms depend on neither these records nor the hosting application.
 */
public final class TrackReviewModels {
    private TrackReviewModels() {
    }

    public enum Decision {INCLUDE, EXCLUDE_MAP, EXCLUDE_STATISTICS, EXCLUDE, LATER}

    public enum ReviewState {NOT_ASSESSED, INCLUDED, NEEDS_REVIEW, EXCLUDED_MAP, EXCLUDED_STATISTICS, EXCLUDED, MIXED}

    public record ReviewDecisionRequest(String sourceHash, Integer sectionIndex, Decision decision) {
    }

    @JsonPropertyOrder({"sectionIndex", "name", "state", "useDecision", "needsReview", "estimatedTiming", "firstPointIndex", "pointCount"})
    public record SourceSectionReview(int sectionIndex, String name, ReviewState state, Decision useDecision, boolean needsReview,
                                      boolean estimatedTiming, int firstPointIndex, int pointCount) {
    }

    @JsonPropertyOrder({"sourceId", "fileName", "sourceHash", "state", "affectedTrackCount", "pointCount", "findingCount",
            "reviewFindingCount", "informationFindingCount", "assessedAt", "reasons", "reviewReasons", "informationReasons"})
    public record SourceReviewItem(long sourceId, String fileName, String sourceHash, ReviewState state,
                                   int affectedTrackCount, int pointCount, int findingCount, int reviewFindingCount,
                                   int informationFindingCount, Instant assessedAt, List<String> reasons,
                                   List<String> reviewReasons, List<String> informationReasons) {
    }

    public record TrackReviewQueue(int needsReviewCount, int excludedCount, int unassessedCount,
                                   int totalSourceCount, List<SourceReviewItem> sources) {
    }

    public record ReviewedTrack(long trackId, String name, Integer sectionIndex, String reviewStatus) {
    }

    public record ReviewDecisionHistory(String sourceHash, int sectionIndex, Decision decision, Instant decidedAt) {
    }

    @JsonPropertyOrder({"source", "diagnosis", "sections", "sourcePath", "previewUnavailableReason", "canDownloadGpx",
            "originalPoints", "totalPointCount", "tracks", "decisions"})
    public record SourceReviewDetail(SourceReviewItem source, TrackDiagnosis diagnosis, List<SourceSectionReview> sections,
                                     String sourcePath, String previewUnavailableReason, boolean canDownloadGpx,
                                     List<TrackObservation> originalPoints, int totalPointCount, List<ReviewedTrack> tracks,
                                     List<ReviewDecisionHistory> decisions) {
    }

    public record ReviewPointPage(int totalCount, int fromPointIndex, List<TrackObservation> points) {
    }
}
