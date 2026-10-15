package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentHeartRateSummary;

@JsonPropertyOrder({"identity", "heartRate"})
public record SegmentAttemptHeartRate(SegmentAttemptIdentity identity, SegmentHeartRateSummary heartRate) {
}
