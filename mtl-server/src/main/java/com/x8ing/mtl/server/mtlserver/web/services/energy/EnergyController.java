package com.x8ing.mtl.server.mtlserver.web.services.energy;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.energy.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
    private final EnergyRecalculationQueue energyQueue;

    public EnergyController(GpsTrackRepository gpsTrackRepository,
                            GpsTrackDataPointRepository gpsTrackDataPointRepository,
                            EnergyService energyService, EnergyRecalculationQueue energyQueue) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.gpsTrackDataPointRepository = gpsTrackDataPointRepository;
        this.energyService = energyService;
        this.energyQueue = energyQueue;
    }

    @GetMapping("/status/{gpsTrackId}")
    public ResponseEntity<EnergyCalculationStatus> getEnergyCalculationStatus(@PathVariable Long gpsTrackId) {
        var track = gpsTrackRepository.findById(gpsTrackId).orElseThrow();
        return ResponseEntity.ok(energyQueue.statusForTrack(track));
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
        requireSupportedTiming(track);
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
        requireSupportedTiming(track);
        TrackEnergySummary baselineSummary = energyService.summaryFromTrack(track);
        EnergyParameters trackParams = energyService.parametersForTrack(track);
        double baselineRiderWeightKg = trackParams.getRiderWeightKg();
        double targetRiderWeightKg = riderWeightKg != null ? riderWeightKg : baselineRiderWeightKg;

        List<GpsTrackDataPoint> points = gpsTrackDataPointRepository.getTrackDetailsByGpsTrackIdAndPrecisionAndType(
                gpsTrackId,
                RAW_ENERGY_PRECISION,
                GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name()
        );

        EnergyParameters adjustedParams = trackParams.toBuilder()
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
                deltaSummary,
                baselineSummary.getMetabolicEnergy() != null && baselineSummary.getMetabolicEnergy().getActiveKcal() != null
                && adjustedSummary.getMetabolicEnergy() != null && adjustedSummary.getMetabolicEnergy().getActiveKcal() != null
                        ? adjustedSummary.getMetabolicEnergy().getActiveKcal() - baselineSummary.getMetabolicEnergy().getActiveKcal() : null
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

    private static void requireSupportedTiming(GpsTrack track) {
        if (track.isTimingUnreliable()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Energy calculation is unavailable because source timing is unreliable or estimated. See Track review.");
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
