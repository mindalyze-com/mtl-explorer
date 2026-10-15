package com.x8ing.mtl.server.mtlserver.energy;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Estimated human energy expenditure, separate from mechanical work.
 */
@Data
@Embeddable
@JsonPropertyOrder({"activeKcal", "totalKcal", "coverage", "coveredSeconds", "uncoveredSeconds",
        "recordingGapSeconds", "sparseSamplingSeconds", "unknownDuration", "exclusionReasons",
        "method", "methodDescription", "modelVersion", "bodyWeightKg"})
public class MetabolicEnergy {
    public enum Coverage {COMPLETE, PARTIAL, UNAVAILABLE}

    public enum ExclusionReason {
        ACTIVITY_REQUIRED, INVALID_BODY_WEIGHT, INSUFFICIENT_DATA, MISSING_TIMING,
        RECORDING_GAP, SPARSE_SAMPLING, UNSUPPORTED_MOTION, MISSING_ELEVATION,
        INSUFFICIENT_DISTANCE, GRADE_OUTSIDE_MODEL, UNSUPPORTED_MECHANICAL_POWER, INCONSISTENT_ENERGY_ESTIMATES
    }

    public enum Method {
        MINETTI_WALKING, MINETTI_RUNNING, ACSM_CYCLING,
        COMPENDIUM_MOUNTAIN_BIKING, COMPENDIUM_ROWING, COMPENDIUM_KAYAKING,
        COMPENDIUM_PADDLE_BOARDING, COMPENDIUM_SKIING, COMPENDIUM_CAR_PASSENGER,
        COMPENDIUM_MOTORCYCLING, COMPENDIUM_SEATED_AIR_TRAVEL
    }

    @Column(name = "metabolic_active_kcal")
    @Schema(description = "Estimated human energy above standard resting expenditure, for covered intervals only; null when unavailable.")
    private Double activeKcal;
    @Column(name = "metabolic_total_kcal")
    @Schema(description = "Active energy plus standard resting expenditure for the same covered intervals; not a full-track total when coverage is partial.")
    private Double totalKcal;
    @Enumerated(EnumType.STRING)
    @Column(name = "metabolic_coverage")
    private Coverage coverage;
    @Column(name = "metabolic_covered_seconds")
    private Double coveredSeconds = 0.0;
    @Column(name = "metabolic_uncovered_seconds")
    @Schema(description = "Known positive duration omitted from the estimate. Missing or invalid timestamps may leave additional unquantifiable gaps.")
    private Double uncoveredSeconds = 0.0;
    @Column(name = "metabolic_recording_gap_seconds")
    @Schema(description = "Known excluded time across explicit recording breaks; included in uncoveredSeconds.")
    private Double recordingGapSeconds = 0.0;
    @Column(name = "metabolic_sparse_sampling_seconds")
    @Schema(description = "Known excluded time between observations too far apart to support motion; included in uncoveredSeconds.")
    private Double sparseSamplingSeconds = 0.0;
    @Column(name = "metabolic_unknown_duration")
    @Schema(description = "Additional intervals were excluded whose duration cannot be quantified.")
    private Boolean unknownDuration = false;
    @Convert(converter = ExclusionReasonsConverter.class)
    @Column(name = "metabolic_exclusion_reasons", length = 512)
    private List<ExclusionReason> exclusionReasons = List.of();
    @Enumerated(EnumType.STRING)
    @Column(name = "metabolic_method")
    private Method method;
    @Column(name = "metabolic_model_version")
    private String modelVersion;
    @Column(name = "metabolic_body_weight_kg")
    private Double bodyWeightKg;

    @Converter
    public static class ExclusionReasonsConverter implements AttributeConverter<List<ExclusionReason>, String> {
        @Override
        public String convertToDatabaseColumn(List<ExclusionReason> reasons) {
            return reasons == null ? null : reasons.stream().map(Enum::name).collect(Collectors.joining(","));
        }

        @Override
        public List<ExclusionReason> convertToEntityAttribute(String stored) {
            return stored == null ? null : stored.isBlank() ? List.of()
                    : Arrays.stream(stored.split(",")).map(ExclusionReason::valueOf).toList();
        }
    }

    @Transient
    public String getMethodDescription() {
        if (method == null) return "An activity type is required.";
        return switch (method) {
            case MINETTI_WALKING ->
                    "Minetti walking model using distance, slope and body weight. Assumes economical walking on firm ground; backpack load and difficult terrain are not modeled.";
            case MINETTI_RUNNING ->
                    "Minetti running model using distance, slope and body weight. Surface, wind and individual running economy are not modeled.";
            case ACSM_CYCLING ->
                    "ACSM cycling equation using GPS-estimated mechanical power. Requires supported elevation. Assumes unassisted cycling and a light movement cost while coasting; cadence, wind and individual efficiency are not measured.";
            case COMPENDIUM_MOUNTAIN_BIKING ->
                    "2024 Compendium: general mountain biking, 8.5 MET during moving time. A coarse activity estimate; terrain difficulty and pedaling effort are not measured.";
            case COMPENDIUM_ROWING ->
                    "2024 Compendium: recreational rowing, 3.5 MET during moving time. Intervals conflicting with mechanical work are excluded. Intensity, wind and water current are not measured.";
            case COMPENDIUM_KAYAKING ->
                    "2024 Compendium: moderate kayaking, 5 MET during moving time. Intervals conflicting with mechanical work are excluded. Intensity, wind and water current are not measured.";
            case COMPENDIUM_PADDLE_BOARDING ->
                    "2024 Compendium: general stand-up paddling, 6.5 MET during moving time. Intervals conflicting with mechanical work are excluded. Stroke rate, wind and water current are not measured.";
            case COMPENDIUM_SKIING ->
                    "2024 Compendium: general skiing, 7 MET during moving time. The activity type does not distinguish alpine skiing, touring or lift rides.";
            case COMPENDIUM_CAR_PASSENGER ->
                    "2024 Compendium: car passenger, 1.3 MET during moving time. Passenger is assumed because the track does not identify the driver. Vehicle energy is excluded.";
            case COMPENDIUM_MOTORCYCLING ->
                    "2024 Compendium: motorcycling, 2.8 MET during moving time. Estimates the rider's expenditure, excluding vehicle energy.";
            case COMPENDIUM_SEATED_AIR_TRAVEL ->
                    "Seated travel estimate, 1.3 MET during moving time, using the 2024 Compendium passenger category as a proxy. Aircraft energy is excluded.";
        };
    }
}
