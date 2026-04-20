package com.x8ing.mtl.server.mtlserver.db.repository.gps;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Single decision point for "which {@link GpsTrackData} variant should this caller use".
 *
 * <p>Background: every track is stored at multiple precisions and types
 * (RAW, RAW_OUTLIER_CLEANED, SIMPLIFIED@1m..1000m). For metric/calculation
 * consumers (measure, energy, slope, classification, ...) we want a single
 * canonical full-density, error-cleaned variant — {@code RAW_OUTLIER_CLEANED}
 * — rather than reaching for an ad-hoc precision constant. Rendering paths
 * keep using {@code SIMPLIFIED@*} variants chosen by zoom/use case.
 *
 * <p>Centralising this here prevents "someone passes PRECISION_1M for
 * measurement" regressions and gives us one place to change routing later.
 */
@Component
public class GpsTrackVariantSelector {

    private final GpsTrackDataRepository gpsTrackDataRepository;
    private final GpsTrackDataPointRepository gpsTrackDataPointRepository;

    public GpsTrackVariantSelector(GpsTrackDataRepository gpsTrackDataRepository,
                                   GpsTrackDataPointRepository gpsTrackDataPointRepository) {
        this.gpsTrackDataRepository = gpsTrackDataRepository;
        this.gpsTrackDataPointRepository = gpsTrackDataPointRepository;
    }

    /**
     * Canonical variant for any consumer that computes physical quantities
     * (time, distance, speed, slope, energy, ...). Returns the
     * {@code RAW_OUTLIER_CLEANED} variant: full GPS density with outliers
     * (jumps, speed spikes) and altitude jitter already removed by the
     * import pipeline.
     */
    public GpsTrackData forMetrics(Long gpsTrackId) {
        return gpsTrackDataRepository.findFirstByGpsTrackIdAndTrackType(
                gpsTrackId,
                GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name());
    }

    /**
     * Id-only flavour of {@link #forMetrics(Long)} — useful when callers
     * only need the {@code GpsTrackData} id to look up its points.
     */
    public Long forMetricsId(Long gpsTrackId) {
        return gpsTrackDataRepository.findFirstByGpsTrackIdAndTrackTypeOnlyIds(
                gpsTrackId,
                GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name());
    }

    /**
     * Loads all points of the canonical metrics variant in track order.
     */
    public List<GpsTrackDataPoint> pointsForMetrics(Long gpsTrackId) {
        return gpsTrackDataPointRepository.getTrackDetailsByGpsTrackIdAndType(
                gpsTrackId,
                GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name());
    }
}
