package com.x8ing.mtl.server.mtlserver.web.services.energy;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.energy.EnergyParameters;
import com.x8ing.mtl.server.mtlserver.energy.EnergyService;
import com.x8ing.mtl.server.mtlserver.energy.TrackEnergySummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST endpoint for ad-hoc energy calculation.
 * Recalculates energy for an existing track with custom parameters without persisting.
 */
@RestController
@RequestMapping("/api/energy")
@JsonPropertyOrder({
        "gpsTrackRepository",
        "gpsTrackDataPointRepository",
        "energyService"
})
public class EnergyController {

    private static final BigDecimal RAW_ENERGY_PRECISION = BigDecimal.ZERO;

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
    @GetMapping("/calculate/{gpsTrackId}")
    public ResponseEntity<TrackEnergySummary> calculateEnergy(
            @PathVariable Long gpsTrackId,
            @RequestParam(name = "weightKg", required = false) Double weightKg,
            @RequestParam(name = "equipmentKg", required = false) Double equipmentKg,
            @RequestParam(name = "precisionInMeter", defaultValue = "0") BigDecimal precisionInMeter,
            @RequestParam(name = "trackType", defaultValue = "RAW_OUTLIER_CLEANED") String trackType
    ) {
        GpsTrack track = gpsTrackRepository.findById(gpsTrackId).orElseThrow();

        EnergyParameters params = buildAdHocParameters(weightKg, equipmentKg);

        List<GpsTrackDataPoint> points = gpsTrackDataPointRepository
                .getTrackDetailsByGpsTrackIdAndPrecisionAndType(gpsTrackId, precisionInMeter, trackType);

        TrackEnergySummary summary = energyService.calculateSummaryWithoutPersisting(points, track.getActivityType(), params);

        return ResponseEntity.ok(summary);
    }

    /**
     * Server-owned rider-weight what-if calculation for the track details UI.
     * The baseline comes from the persisted track summary so the dialog compares
     * against the values currently visible in the overview.
     */
    @GetMapping("/what-if/{gpsTrackId}")
    public ResponseEntity<EnergyWhatIfResponse> calculateEnergyWhatIf(
            @PathVariable Long gpsTrackId,
            @RequestParam(name = "riderWeightKg", required = false) Double riderWeightKg
    ) {
        validateOptionalRiderWeight(riderWeightKg);

        GpsTrack track = gpsTrackRepository.findById(gpsTrackId).orElseThrow();
        TrackEnergySummary baselineSummary = energyService.summaryFromTrack(track);
        double baselineRiderWeightKg = inferBaselineRiderWeightKg(track);
        double targetRiderWeightKg = riderWeightKg != null ? riderWeightKg : baselineRiderWeightKg;

        List<GpsTrackDataPoint> points = gpsTrackDataPointRepository.getTrackDetailsByGpsTrackIdAndPrecisionAndType(
                gpsTrackId,
                RAW_ENERGY_PRECISION,
                GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name()
        );

        EnergyParameters adjustedParams = energyService.getDefaultParameters().toBuilder()
                .riderWeightKg(targetRiderWeightKg)
                .build();
        TrackEnergySummary adjustedSummary = energyService.calculateSummaryWithoutPersisting(
                points,
                track.getActivityType(),
                adjustedParams
        );
        TrackEnergySummary deltaSummary = energyService.subtractSummaries(adjustedSummary, baselineSummary);

        return ResponseEntity.ok(new EnergyWhatIfResponse(
                gpsTrackId,
                baselineRiderWeightKg,
                targetRiderWeightKg,
                baselineSummary.getWeightKgUsed(),
                adjustedSummary.getWeightKgUsed(),
                baselineSummary,
                adjustedSummary,
                deltaSummary
        ));
    }

    /**
     * Persist a rider-weight-specific energy recalculation for this track only.
     * This does not update the default rider weight config.
     */
    @PostMapping("/rider-weight/{gpsTrackId}")
    public ResponseEntity<GpsTrack> saveTrackRiderWeight(
            @PathVariable Long gpsTrackId,
            @RequestParam(name = "riderWeightKg") Double riderWeightKg
    ) {
        validateRiderWeight(riderWeightKg);

        EnergyParameters params = energyService.getDefaultParameters().toBuilder()
                .riderWeightKg(riderWeightKg)
                .build();

        boolean recalculated = energyService.recalculateEnergyForTrack(gpsTrackId, params);
        if (!recalculated) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Energy could not be recalculated for this track"
            );
        }

        return ResponseEntity.ok(gpsTrackRepository.findById(gpsTrackId).orElseThrow());
    }

    private EnergyParameters buildAdHocParameters(Double weightKg, Double equipmentKg) {
        validateOptionalRiderWeight(weightKg);
        EnergyParameters baseParams = energyService.getDefaultParameters();
        return baseParams.toBuilder()
                .riderWeightKg(weightKg != null ? weightKg : baseParams.getRiderWeightKg())
                .equipmentWeightKgOverride(equipmentKg)
                .build();
    }

    private double inferBaselineRiderWeightKg(GpsTrack track) {
        double defaultRiderWeightKg = energyService.getDefaultParameters().getRiderWeightKg();
        if (track.getEnergyWeightKgUsed() == null) return defaultRiderWeightKg;

        double equipmentWeightKg = energyService.getDefaultEquipmentWeightKg(track.getActivityType());
        double inferredRiderWeightKg = track.getEnergyWeightKgUsed() - equipmentWeightKg;
        if (!Double.isFinite(inferredRiderWeightKg)) return defaultRiderWeightKg;
        return Math.min(
                EnergyParameters.MAX_RIDER_WEIGHT_KG,
                Math.max(EnergyParameters.MIN_RIDER_WEIGHT_KG, inferredRiderWeightKg)
        );
    }

    private void validateOptionalRiderWeight(Double riderWeightKg) {
        if (riderWeightKg != null) {
            validateRiderWeight(riderWeightKg);
        }
    }

    private void validateRiderWeight(Double riderWeightKg) {
        if (riderWeightKg == null
            || !Double.isFinite(riderWeightKg)
            || riderWeightKg < EnergyParameters.MIN_RIDER_WEIGHT_KG
            || riderWeightKg > EnergyParameters.MAX_RIDER_WEIGHT_KG) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "riderWeightKg must be between %.1f and %.1f kg".formatted(
                            EnergyParameters.MIN_RIDER_WEIGHT_KG,
                            EnergyParameters.MAX_RIDER_WEIGHT_KG
                    )
            );
        }
    }
}
