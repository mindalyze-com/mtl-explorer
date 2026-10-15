package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentHeartRateGroup;

import java.util.List;

@JsonPropertyOrder({"trackId", "revision", "healthRevision", "attempts", "groups"})
public record SegmentTrackHeartRate(long trackId, String revision, String healthRevision,
                                    List<SegmentAttemptHeartRate> attempts, List<SegmentHeartRateGroup> groups) {
}
