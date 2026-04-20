package com.x8ing.mtl.server.mtlserver.jobs.status;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class JobStatusService {

    private final GpsTrackRepository gpsTrackRepository;

    public JobStatusService(GpsTrackRepository gpsTrackRepository) {
        this.gpsTrackRepository = gpsTrackRepository;
    }

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
        long total = pending + done;
        int progress = total > 0 ? (int) (done * 100L / total) : 100;
        return new JobSummaryDto("duplicate", "Duplicate Finder", total, pending, done, progress);
    }

    private JobSummaryDto buildActivityTypeSummary() {
        long pending = gpsTrackRepository.countActivityTypePending();
        long done = gpsTrackRepository.countActivityTypeDone();
        long total = pending + done;
        int progress = total > 0 ? (int) (done * 100L / total) : 100;
        return new JobSummaryDto("activityType", "Activity Classifier", total, pending, done, progress);
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
        long total = pending + done;
        int progress = total > 0 ? (int) (done * 100L / total) : 100;
        return new JobSummaryDto("exploration", "Exploration Score", total, pending, done, progress);
    }
}
