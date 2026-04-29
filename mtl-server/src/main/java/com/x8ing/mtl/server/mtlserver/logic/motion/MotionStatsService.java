package com.x8ing.mtl.server.mtlserver.logic.motion;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentNotes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persists per-track motion/stop totals on {@link GpsTrack}.
 *
 * <p>Mirrors the ingest-time computation in {@code GPXStoreService} but operates
 * off the already-stored {@code RAW_OUTLIER_CLEANED} points — used both for
 * one-shot recalcs and for backfilling existing rows after rolling out the new
 * stop-stats columns. The underlying algorithm lives in
 * {@link TrackMotionAnalyzer} so there is exactly one source of truth.
 */
@Service
@Slf4j
public class MotionStatsService {

    private final GpsTrackRepository gpsTrackRepository;
    private final GpsTrackVariantSelector variantSelector;
    private final GpsTrackEventService gpsTrackEventService;

    public MotionStatsService(GpsTrackRepository gpsTrackRepository,
                              GpsTrackVariantSelector variantSelector,
                              GpsTrackEventService gpsTrackEventService) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.variantSelector = variantSelector;
        this.gpsTrackEventService = gpsTrackEventService;
    }

    /**
     * Recompute and persist {@code trackDurationStoppedSecs},
     * {@code trackStopCount} and {@code trackLongestStopSecs} for the given
     * track, using the {@code RAW_OUTLIER_CLEANED} point stream.
     *
     * @return true if totals were updated; false if the track or points are missing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recalculateMotionStatsForTrack(Long gpsTrackId) {
        if (gpsTrackId == null) return false;
        GpsTrack track = gpsTrackRepository.findById(gpsTrackId).orElse(null);
        if (track == null) {
            log.warn("recalculateMotionStatsForTrack: track id={} not found", gpsTrackId);
            return false;
        }
        List<GpsTrackDataPoint> points = variantSelector.pointsForMetrics(gpsTrackId);
        if (points == null || points.isEmpty()) {
            log.debug("recalculateMotionStatsForTrack: track id={} has no RAW_OUTLIER_CLEANED points — skipping", gpsTrackId);
            return false;
        }
        Long gpsTrackDataId = points.get(0).getGpsTrackDataId();
        List<TrackMotionAnalyzer.StopRange> stopRanges = TrackMotionAnalyzer.detectStopRangesInTrack(points);
        SegmentNotes notes = TrackMotionAnalyzer.summarizeStopRanges(stopRanges);
        track.setTrackDurationStoppedSecs(notes.totalStoppedSec);
        track.setTrackStopCount(notes.stopCount);
        track.setTrackLongestStopSecs(notes.longestStopSec);
        gpsTrackEventService.replaceDetectedStopEvents(gpsTrackId, gpsTrackDataId, stopRanges);
        gpsTrackRepository.save(track);
        return true;
    }

    /**
     * Backfill every track where stop-stats have not been computed yet
     * (i.e. {@code trackDurationStoppedSecs IS NULL}). Runs in its own
     * transactions per-track so a failure on one track does not roll back
     * the others.
     *
     * @return number of tracks successfully updated
     */
    public int backfillMissing() {
        List<Long> ids = gpsTrackRepository.findIdsWithMissingStopStats();
        log.info("backfillMissing: {} tracks need stop-stats backfill", ids.size());
        int updated = 0;
        for (Long id : ids) {
            try {
                if (recalculateMotionStatsForTrack(id)) {
                    updated++;
                }
            } catch (Exception e) {
                log.warn("backfillMissing: failed for trackId={}: {}", id, e.getMessage(), e);
            }
        }
        log.info("backfillMissing: updated {} / {} tracks", updated, ids.size());
        return updated;
    }
}
