package com.x8ing.mtl.server.mtlserver.web.services.energy;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.energy.EnergyParameters;
import com.x8ing.mtl.server.mtlserver.energy.EnergyService;
import com.x8ing.mtl.server.mtlserver.energy.TrackEnergySummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST endpoint for ad-hoc energy calculation.
 * Recalculates energy for an existing track with custom parameters without persisting.
 */
@RestController
@RequestMapping("/api/energy")
public class EnergyController {

    private final GpsTrackRepository gpsTrackRepository;
    private final GpsTrackDataPointRepository gpsTrackDataPointRepository;
    private final EnergyService energyService;

    public EnergyController(GpsTrackRepository gpsTrackRepository,
                            GpsTrackDataPointRepository gpsTrackDataPointRepository,
                            EnergyService energyService) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.gpsTrackDataPointRepository = gpsTrackDataPointRepository;
        this.energyService = energyService;
    }

    /**
     * Ad-hoc energy calculation for a track. Does NOT persist — returns the summary only.
     * Useful for "what-if" scenarios (different weight, different equipment).
     */
    @RequestMapping("/calculate/{gpsTrackId}")
    public ResponseEntity<TrackEnergySummary> calculateEnergy(
            @PathVariable Long gpsTrackId,
            @RequestParam(name = "weightKg", required = false) Double weightKg,
            @RequestParam(name = "equipmentKg", required = false) Double equipmentKg,
            @RequestParam(name = "precisionInMeter", defaultValue = "0") BigDecimal precisionInMeter,
            @RequestParam(name = "trackType", defaultValue = "RAW_OUTLIER_CLEANED") String trackType
    ) {
        GpsTrack track = gpsTrackRepository.findById(gpsTrackId).orElseThrow();

        // Build parameters: use provided values or fall back to config defaults
        EnergyParameters baseParams = energyService.getDefaultParameters();
        EnergyParameters params = EnergyParameters.builder()
                .riderWeightKg(weightKg != null ? weightKg : baseParams.getRiderWeightKg())
                .equipmentWeightKgOverride(equipmentKg)
                .build();

        // Load the track data points
        List<GpsTrackDataPoint> points = gpsTrackDataPointRepository
                .getTrackDetailsByGpsTrackIdAndPrecisionAndType(gpsTrackId, precisionInMeter, trackType);

        // Calculate energy on a copy of the points (don't modify persisted data)
        TrackEnergySummary summary = energyService.calculateAndPopulatePoints(points, track.getActivityType(), params);

        return ResponseEntity.ok(summary);
    }
}
