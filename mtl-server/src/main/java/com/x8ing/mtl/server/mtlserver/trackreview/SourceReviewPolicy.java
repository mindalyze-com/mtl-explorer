package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.TrackDiagnosis;

import java.util.List;

import static com.x8ing.mtl.server.mtlserver.trackreview.SourceReviewRepository.combinedState;
import static com.x8ing.mtl.server.mtlserver.trackreview.TrackReviewModels.*;

/**
 * One eligibility policy for newly imported tracks and stored-track review decisions.
 */
final class SourceReviewPolicy {
    private SourceReviewPolicy() {
    }

    record UseSettings(ReviewState state, boolean statisticsExcluded, boolean mapExcluded,
                       boolean estimated, boolean unreliable) {
    }

    static UseSettings forTrack(TrackDiagnosis diagnosis, List<SourceSectionReview> sections, Integer section,
                                boolean stale, boolean previouslyEstimated, boolean previouslyUnreliable) {
        var applicable = sections.stream().filter(s -> s.sectionIndex() < 0 || section == null || s.sectionIndex() == section).toList();
        boolean limited = SourceReviewService.assessmentLimited(diagnosis);
        boolean estimated = (limited && previouslyEstimated) || applicable.stream().anyMatch(SourceSectionReview::estimatedTiming);
        boolean unreliable = stale || estimated || (limited && previouslyUnreliable)
                             || applicable.stream().anyMatch(s -> SourceImportPlan.unreliableTiming(diagnosis, s.sectionIndex()));
        return new UseSettings(stale ? ReviewState.NOT_ASSESSED : combinedState(applicable),
                applicable.stream().anyMatch(s -> s.useDecision() == Decision.EXCLUDE_STATISTICS || s.useDecision() == Decision.EXCLUDE),
                applicable.stream().anyMatch(s -> s.useDecision() == Decision.EXCLUDE_MAP || s.useDecision() == Decision.EXCLUDE),
                estimated, unreliable);
    }
}
