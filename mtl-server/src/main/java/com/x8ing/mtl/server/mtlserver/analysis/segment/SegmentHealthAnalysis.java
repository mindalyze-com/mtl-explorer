package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostSummary;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentHeartRateSummary;

import java.util.List;

@JsonPropertyOrder({"identity", "healthRevision", "heartRate", "cardiacCost", "points", "availability"})
public record SegmentHealthAnalysis(SegmentAttemptIdentity identity, String healthRevision, SegmentHeartRateSummary heartRate,
                                    SegmentCardiacCostSummary cardiacCost, List<SegmentHealthPoint> points, SegmentMetricAvailability availability) {
}
