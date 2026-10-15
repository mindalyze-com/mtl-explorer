package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"fromTriggerPoint", "toTriggerPoint", "heartRate"})
public record SegmentHeartRateGroup(String fromTriggerPoint, String toTriggerPoint, SegmentHeartRateSummary heartRate) {
}
