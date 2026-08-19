package com.x8ing.mtl.server.mtlserver.jobs.status;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaCorrelationRepository;
import com.x8ing.mtl.server.mtlserver.jobs.media.correlation.MediaCorrelationJob;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@JsonPropertyOrder({
        "gpsTrackRepository"
})
public class JobStatusService {

    private final GpsTrackRepository gpsTrackRepository;
    private final MediaCorrelationRepository mediaCorrelationRepository;

    public JobStatusService(
            GpsTrackRepository gpsTrackRepository,
            MediaCorrelationRepository mediaCorrelationRepository) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.mediaCorrelationRepository = mediaCorrelationRepository;
    }

    @JsonPropertyOrder({
            "job",
            "label",
            "total",
            "pending",
            "done",
            "progressPercent"
    })
    public record JobSummaryDto(
            String job,
            String label,
            long total,
            long pending,
            long done,
            int progressPercent
    ) {
    }

    public List<JobSummaryDto> getJobSummaries() {
        List<JobSummaryDto> result = new ArrayList<>();
        result.add(buildDuplicateSummary());
        result.add(buildActivityTypeSummary());
        result.add(buildExplorationSummary());
        result.add(buildMediaCorrelationSummary());
        return result;
    }

    private JobSummaryDto buildDuplicateSummary() {
        List<Object[]> rows = gpsTrackRepository.countGroupedByDuplicateStatus();
        Map<GpsTrack.DUPLICATE_CHECK_STATUS, Long> counts = new EnumMap<>(GpsTrack.DUPLICATE_CHECK_STATUS.class);
        for (Object[] row : rows) {
            counts.put((GpsTrack.DUPLICATE_CHECK_STATUS) row[0], (Long) row[1]);
        }
        long pending = counts.getOrDefault(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET, 0L);
        long done = counts.getOrDefault(GpsTrack.DUPLICATE_CHECK_STATUS.UNIQUE, 0L)
                    + counts.getOrDefault(GpsTrack.DUPLICATE_CHECK_STATUS.DUPLICATE, 0L);
        return summary("duplicate", "Duplicate Finder", pending, done);
    }

    private JobSummaryDto buildActivityTypeSummary() {
        long pending = gpsTrackRepository.countActivityTypePending();
        long done = gpsTrackRepository.countActivityTypeDone();
        return summary("activityType", "Activity Classifier", pending, done);
    }

    private JobSummaryDto buildExplorationSummary() {
        List<Object[]> rows = gpsTrackRepository.countGroupedByExplorationStatus();
        Map<GpsTrack.EXPLORATION_STATUS, Long> counts = new EnumMap<>(GpsTrack.EXPLORATION_STATUS.class);
        for (Object[] row : rows) {
            counts.put((GpsTrack.EXPLORATION_STATUS) row[0], (Long) row[1]);
        }
        long pending = counts.getOrDefault(GpsTrack.EXPLORATION_STATUS.SCHEDULED, 0L)
                       + counts.getOrDefault(GpsTrack.EXPLORATION_STATUS.IN_PROGRESS, 0L)
                       + counts.getOrDefault(GpsTrack.EXPLORATION_STATUS.NEEDS_RECALCULATION, 0L);
        // CALCULATED: successfully computed exploration score.
        // Explicitly-skipped NOT_SCHEDULED (explorationCalcDate set): job evaluated the track and found
        // no geometry — treated as done so that pending→0 reflects actual completion, not silent vanishing.
        long done = counts.getOrDefault(GpsTrack.EXPLORATION_STATUS.CALCULATED, 0L)
                    + gpsTrackRepository.countExplorationExplicitlySkipped();
        return summary("exploration", "Exploration Score", pending, done);
    }

    private JobSummaryDto buildMediaCorrelationSummary() {
        long pending = mediaCorrelationRepository.countPendingWork();
        long done = mediaCorrelationRepository.countCompletedMedia(MediaCorrelationJob.ALGORITHM_VERSION);
        return summary("mediaCorrelation", "Photo Position Correlation", pending, done);
    }

    private static JobSummaryDto summary(String job, String label, long pending, long done) {
        long total = pending + done;
        int progress = total > 0 ? (int) (done * 100L / total) : 100;
        return new JobSummaryDto(job, label, total, pending, done, progress);
    }
}
