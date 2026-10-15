package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder({"available", "unavailableReason", "beatsPerKj", "wattsPerBpm", "estimatedHeartbeats", "mechanicalEnergyKj",
        "coveredMovingSeconds", "movingSeconds", "coveragePercent", "minimumEnergyKj"})
@Schema(description = "Estimated heartbeats per kJ of mechanical work over identical, covered moving intervals. "
                      + "Includes moving coasting intervals; not a measure of metabolic efficiency.")
public record SegmentCardiacCostSummary(
        boolean available,
        UnavailableReason unavailableReason,
        Double beatsPerKj,
        @Schema(description = "Average estimated power in watts divided by time-weighted average BPM over the same covered moving intervals. Equals 1000 / (60 * beatsPerKj); higher W/bpm corresponds to lower beats/kJ. Unavailable under the same coverage and minimum-work rules.")
        Double wattsPerBpm,
        double estimatedHeartbeats,
        double mechanicalEnergyKj,
        double coveredMovingSeconds,
        double movingSeconds,
        @Schema(description = "Percentage of observed moving time with both usable energy and heart-rate coverage.")
        double coveragePercent,
        double minimumEnergyKj
) {
    public enum UnavailableReason {
        NO_MOVING_DATA, NO_MATCHED_DATA, INSUFFICIENT_ENERGY, MISSING_TIMESTAMPS, MOTORIZED_ACTIVITY
    }
}
