package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"identity", "summary", "points", "geometry", "sourcePointCount"})
public record SegmentCoreAnalysis(SegmentAttemptIdentity identity, SegmentAttemptSummary summary, List<SegmentAnalysisPoint> points,
                                  List<SegmentGeometryPoint> geometry, int sourcePointCount) {
}
