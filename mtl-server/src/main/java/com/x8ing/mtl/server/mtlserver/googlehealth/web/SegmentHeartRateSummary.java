package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder({"averageBpm", "coveredSeconds", "elapsedSeconds", "coveragePercent"})
@Schema(description = "Time-weighted heart rate over the exact segment, including stops but excluding recording gaps. Null average means no covered time. Repeated visits are weighted by covered seconds.")
public record SegmentHeartRateSummary(Double averageBpm, double coveredSeconds, double elapsedSeconds, double coveragePercent) {
}
