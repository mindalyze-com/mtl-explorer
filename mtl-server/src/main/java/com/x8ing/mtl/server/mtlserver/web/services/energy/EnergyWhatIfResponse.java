package com.x8ing.mtl.server.mtlserver.web.services.energy;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.energy.TrackEnergySummary;

@JsonPropertyOrder({
        "gpsTrackId",
        "baselineRiderWeightKg",
        "requestedRiderWeightKg",
        "baselineWeightKgUsed",
        "adjustedWeightKgUsed",
        "baselineSummary",
        "adjustedSummary",
        "deltaSummary"
})
public record EnergyWhatIfResponse(
        Long gpsTrackId,
        double baselineRiderWeightKg,
        double requestedRiderWeightKg,
        double baselineWeightKgUsed,
        double adjustedWeightKgUsed,
        TrackEnergySummary baselineSummary,
        TrackEnergySummary adjustedSummary,
        TrackEnergySummary deltaSummary
) {
}
