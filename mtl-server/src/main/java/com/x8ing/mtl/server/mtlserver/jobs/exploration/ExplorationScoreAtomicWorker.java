package com.x8ing.mtl.server.mtlserver.jobs.exploration;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Service
@JsonPropertyOrder({
        "gpsTrackRepository",
        "queryRepository",
        "corridorWidthM",
        "useTrackPrecision"
})
public class ExplorationScoreAtomicWorker {

    private final GpsTrackRepository gpsTrackRepository;
    private final ExplorationScoreQueryRepository queryRepository;

    /**
     * Radius in meters around each prior track that counts as "known territory".
     * A segment of the new track is considered "known" if any prior track passes within this distance.
     * Also used as the max segment length for ST_Segmentize — ensures no segment is longer than the
     * corridor width, preventing diagonal crossings from being missed between segment endpoints.
     * Typical value: 25m (covers road width + GPS error of ~5–10m).
     */
    @Value("${mtl.exploration.corridor-width-m:25.0}")
    private double corridorWidthM;

    /**
     * Which pre-computed simplified track variant to use for the PostGIS query (in meters).
     * Each track is stored at multiple precision levels (1m, 10m, 100m, 1000m).
     * Lower = more accurate but slower. 10m preserves switchbacks while being fast enough.
     * Maps to {@code gps_track_data.precision_in_meter} and {@code track_type = 'SIMPLIFIED_SHAPE'}.
     */
    @Value("${mtl.exploration.use-track-precision:10}")
    private int useTrackPrecision;

    public ExplorationScoreAtomicWorker(GpsTrackRepository gpsTrackRepository,
                                        ExplorationScoreQueryRepository queryRepository) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.queryRepository = queryRepository;
    }

    /**
     * Process a single track in its own transaction. Runs the PostGIS query and writes only
     * exploration fields. On failure, marks the track as NEEDS_RECALCULATION so it can be retried.
     * Each track is independent — a failure here does not roll back other tracks in the batch.
     * <p>
     * The loaded track is an input only and is never mutated or saved. Defer the targeted write
     * until the calculation finishes so unrelated jobs can commit while the query runs.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processOne(long trackId) {
        long t0 = System.currentTimeMillis();

        GpsTrack track = gpsTrackRepository.findById(trackId).orElse(null);
        if (track == null) {
            log.warn("Exploration score: trackId={} not found, skipping.", trackId);
            return true;
        }

        try {
            ExplorationScoreQueryRepository.ExplorationResult result =
                    queryRepository.calculateExplorationScore(
                            track.getId(),
                            track.getStartDate(),
                            corridorWidthM,
                            useTrackPrecision
                    );

            if (result == null) {
                gpsTrackRepository.updateExplorationResult(trackId, GpsTrack.EXPLORATION_STATUS.NOT_SCHEDULED,
                        null, new Date());
                log.debug("Exploration score unavailable: trackId={} source is ineligible or has no observed geometry; marked NOT_SCHEDULED.", track.getId());
                return true;
            }

            // Round to 3 decimal places (e.g. 0.847 = 84.7% novel territory)
            double score = Math.round(result.getExplorationScore() * 1000.0) / 1000.0;
            gpsTrackRepository.updateExplorationResult(trackId, GpsTrack.EXPLORATION_STATUS.CALCULATED,
                    score, new Date());

            long durationMs = System.currentTimeMillis() - t0;
            log.debug("Exploration score: trackId={} score={} totalM={} novelM={} corridorM={} precisionM={} durationMs={}",
                    track.getId(),
                    String.format("%.1f%%", result.getExplorationScore() * 100),
                    String.format("%.0f", result.getTotalM()),
                    String.format("%.0f", result.getNovelM()),
                    corridorWidthM,
                    useTrackPrecision,
                    durationMs);
            return true;

        } catch (Exception e) {
            log.error("Exploration score failed for trackId={}. e={}", track.getId(), e.toString(), e);
            gpsTrackRepository.markExplorationForRetry(trackId, new Date());
            return false;
        }
    }

}
