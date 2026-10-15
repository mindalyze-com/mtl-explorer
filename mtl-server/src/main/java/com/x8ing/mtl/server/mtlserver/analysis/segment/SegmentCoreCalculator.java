package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentTimingStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Function;

import static com.x8ing.mtl.server.mtlserver.analysis.segment.SegmentMetricAvailability.State.*;

/**
 * Pure full-density calculation. Point reduction must happen only after this result exists.
 */
@Slf4j
public final class SegmentCoreCalculator {
    private static final double DISTANCE_RECONCILIATION_TOLERANCE_METRES = 0.01;

    private SegmentCoreCalculator() {
    }

    public static SegmentCoreAnalysis calculate(SegmentAttemptResolver.ResolvedAttempt attempt) {
        var identity = attempt.identity();
        var slice = SegmentInterval.clip(attempt);
        var nodes = slice.nodes();
        boolean continuous = slice.pieces().stream().allMatch(SegmentInterval.Piece::supported);
        double geometryDistance = slice.pieces().stream().filter(SegmentInterval.Piece::supported)
                .mapToDouble(p -> SegmentInterval.distance(p.from(), p.to())).sum();
        // Both discovery and progress use canonical geometry. Calibration also removes floating summation drift at the endpoint.
        double scale = 1;
        Double discoveredDistance = attempt.summary().distanceMeters();
        var previousDistanceAvailability = attempt.summary().availability().get("distance");
        boolean distanceMismatch = previousDistanceAvailability != null
                                   && SegmentMetricAvailability.DISTANCE_MISMATCH.equals(previousDistanceAvailability.reason());
        distanceMismatch |= continuous && discoveredDistance != null
                            && (!Double.isFinite(discoveredDistance) || discoveredDistance < 0
                                || Math.abs(discoveredDistance - geometryDistance) > DISTANCE_RECONCILIATION_TOLERANCE_METRES);
        if (distanceMismatch && discoveredDistance != null) {
            log.warn("Segment distance unavailable: trackId={} startPosition={} endPosition={} discoveryMetres={} canonicalMetres={}",
                    identity.trackId(), identity.startPosition(), identity.endPosition(), discoveredDistance, geometryDistance);
        } else if (continuous && discoveredDistance != null && geometryDistance > 0) {
            scale = discoveredDistance / geometryDistance;
        }
        boolean distanceAvailable = continuous && !distanceMismatch && discoveredDistance != null;
        String distanceReason = distanceMismatch ? SegmentMetricAvailability.DISTANCE_MISMATCH : continuous ? "DISTANCE_UNAVAILABLE" : "RECORDING_GAP";
        double distance = 0, energy = 0, energyCovered = 0;
        boolean energyComplete = true;
        boolean distanceComplete = !distanceMismatch;
        List<SegmentAnalysisPoint> points = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i);
            boolean breakBefore = false;
            if (i > 0) {
                var piece = slice.pieces().get(i - 1);
                breakBefore = !piece.supported();
                distanceComplete &= piece.supported();
                if (piece.supported()) distance += SegmentInterval.distance(piece.from(), piece.to()) * scale;
                if (piece.energyWh() == null) energyComplete = false;
                else {
                    energy += piece.energyWh();
                    Double seconds = SegmentInterval.seconds(piece.from().time(), piece.to().time());
                    if (seconds != null && seconds > 0) energyCovered += seconds;
                }
            }
            Double elapsed = identity.timingStatus() == SegmentTimingStatus.AVAILABLE
                    ? SegmentInterval.seconds(identity.start(), node.time()) : null;
            if (elapsed != null && elapsed < 0) elapsed = null;
            points.add(new SegmentAnalysisPoint(distanceComplete ? Double.valueOf(distance) : null, elapsed, node.time(), node.longitude(), node.latitude(),
                    distanceMismatch ? null : node.speed(), node.altitude(), node.power(), distanceMismatch ? null : node.slope(),
                    energyComplete ? energy : null, breakBefore));
        }
        if (!energyComplete && energyCovered == 0) {
            points.replaceAll(p -> new SegmentAnalysisPoint(p.distanceMeters(), p.elapsedSeconds(), p.timestamp(),
                    p.longitude(), p.latitude(), p.speedKmh(), p.altitudeMeters(), p.powerWatts(), p.slopePercent(), null, p.breakBefore()));
        }
        Double ascent = continuous ? cumulativeDelta(nodes, SegmentInterval.Node::ascent) : null;
        Double descent = continuous ? cumulativeDelta(nodes, SegmentInterval.Node::descent) : null;
        var powers = nodes.stream().map(SegmentInterval.Node::power).filter(Objects::nonNull).toList();
        Double maximumPower = powers.isEmpty() ? null : Collections.max(powers);
        Map<String, SegmentMetricAvailability> availability = new LinkedHashMap<>();
        availability.put("distance", availability(distanceAvailable ? discoveredDistance : null, distanceReason));
        availability.put("elapsed", availability(attempt.summary().elapsedSeconds(), String.valueOf(identity.timingStatus())));
        availability.put("speed", availability(distanceAvailable ? attempt.summary().averageSpeedKmh() : null,
                distanceAvailable ? String.valueOf(identity.timingStatus()) : distanceReason));
        availability.put(SegmentMetricAvailability.PERFORMANCE_METRIC, new SegmentMetricAvailability(distanceAvailable ? AVAILABLE : UNAVAILABLE,
                distanceAvailable ? null : distanceReason, null));
        availability.put("ascent", availability(ascent, "MISSING_BOUNDARY_OR_INTERVAL"));
        availability.put("descent", availability(descent, "MISSING_BOUNDARY_OR_INTERVAL"));
        availability.put("energy", new SegmentMetricAvailability(energyComplete ? AVAILABLE : energyCovered > 0 ? PARTIAL : UNAVAILABLE,
                energyComplete ? null : "UNSUPPORTED_ENERGY_INTERVAL", energyCovered));
        availability.put("power", new SegmentMetricAvailability(powers.isEmpty() ? UNAVAILABLE : powers.size() == nodes.size() ? AVAILABLE : PARTIAL,
                powers.size() == nodes.size() ? null : "MISSING_POWER_SAMPLES", null));
        var summary = new SegmentAttemptSummary(identity, distanceAvailable ? discoveredDistance : null, attempt.summary().elapsedSeconds(),
                distanceAvailable ? attempt.summary().averageSpeedKmh() : null, ascent, descent, energyComplete ? energy : null, maximumPower, Map.copyOf(availability));
        var geometry = points.stream().map(p -> new SegmentGeometryPoint(p.distanceMeters(), p.elapsedSeconds(), p.timestamp(),
                p.longitude(), p.latitude(), p.breakBefore())).toList();
        return new SegmentCoreAnalysis(identity, summary, List.copyOf(points), geometry, nodes.size());
    }

    private static Double cumulativeDelta(List<SegmentInterval.Node> nodes, Function<SegmentInterval.Node, Double> metric) {
        if (nodes.stream().anyMatch(n -> metric.apply(n) == null)) return null;
        double value = metric.apply(nodes.getLast()) - metric.apply(nodes.getFirst());
        return value < 0 ? null : value;
    }

    private static SegmentMetricAvailability availability(Double value, String reason) {
        return new SegmentMetricAvailability(value == null ? UNAVAILABLE : AVAILABLE,
                value == null ? reason == null ? "UNAVAILABLE" : reason : null, null);
    }
}
