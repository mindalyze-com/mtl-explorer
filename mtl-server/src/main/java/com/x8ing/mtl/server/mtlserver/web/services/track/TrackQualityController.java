package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GpsObservationEvidence;
import com.x8ing.mtl.server.mtlserver.gpx.GpsProcessingIssue;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/tracks")
@RequiredArgsConstructor
@Slf4j
public class TrackQualityController {
    private static final int MAX_REVIEW_ISSUES = 250;
    private static final double VERTICAL_ONLY_DISTANCE_METRES = 1;
    private static final double VERTICAL_ONLY_CHANGE_METRES = 2;
    private final GpsTrackDataRepository dataRepository;
    private final GpsTrackDataPointRepository pointRepository;
    private final GpsTrackRepository trackRepository;

    @JsonPropertyOrder({"rawPointIndex", "canonicalPointIndex", "reason", "horizontalMetres", "verticalMetres", "durationSeconds", "reviewScore"})
    public record QualityIssue(Integer rawPointIndex, Integer canonicalPointIndex, String reason,
                               Double horizontalMetres, Double verticalMetres, Double durationSeconds, double reviewScore) {
    }

    @JsonPropertyOrder({"trackId", "sourceHash", "rawCoordinates", "canonicalCoordinates", "rawBreakIndexes", "canonicalBreakIndexes",
            "observationEvidence", "processingSettings", "issues", "totalIssueCount"})
    public record TrackQualityReport(Long trackId, String sourceHash, List<List<Double>> rawCoordinates,
                                     List<List<Double>> canonicalCoordinates, List<Integer> rawBreakIndexes,
                                     List<Integer> canonicalBreakIndexes, List<GpsObservationEvidence> observationEvidence,
                                     Map<String, String> processingSettings, List<QualityIssue> issues, int totalIssueCount) {
    }

    @GetMapping("/{trackId}/quality")
    @Operation(summary = "Review original observations, processing decisions, and unsupported intervals")
    @Transactional(readOnly = true)
    public TrackQualityReport getTrackQuality(@PathVariable Long trackId) {
        long started = System.currentTimeMillis();
        log.info("GPS quality review started: trackId={}", trackId);
        GpsTrackData raw = dataRepository.findFirstByGpsTrackIdAndTrackType(trackId, GpsTrackData.TRACK_TYPE.RAW.name());
        GpsTrackData canonical = dataRepository.findFirstByGpsTrackIdAndTrackType(trackId, GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name());
        if (raw == null || canonical == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Track observations unavailable");
        List<GpsTrackDataPoint> points = pointRepository.getTrackDetailsByGpsTrackIdAndType(trackId, GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name());
        String sourceHash = trackRepository.findById(trackId).orElseThrow().getSourceContentHash();
        TrackQualityReport report = buildReport(trackId, sourceHash, raw, canonical, points);
        log.info("GPS quality review completed: trackId={} rawPoints={} canonicalPoints={} issues={} durationMs={}",
                trackId, report.rawCoordinates().size(), report.canonicalCoordinates().size(), report.totalIssueCount(), System.currentTimeMillis() - started);
        return report;
    }

    static TrackQualityReport buildReport(Long trackId, String sourceHash, GpsTrackData raw, GpsTrackData canonical, List<GpsTrackDataPoint> points) {
        List<QualityIssue> issues = new ArrayList<>();
        List<GpsObservationEvidence> evidence = raw.getObservationEvidence() == null ? List.of() : raw.getObservationEvidence();
        for (var recorded : raw.getProcessingIssues() == null ? List.<GpsProcessingIssue>of() : raw.getProcessingIssues())
            issues.add(issue(recorded.rawPointIndex(), recorded.canonicalPointIndex(), recorded.reason(),
                    recorded.horizontalMetres(), recorded.verticalMetres(), recorded.durationSeconds()));
        for (GpsTrackDataPoint point : points) {
            if (point.getIntervalStatus() == GpsTrackDataPoint.IntervalStatus.GEOMETRY_ONLY)
                issues.add(issue(null, point.getPointIndex(), "GEOMETRY_ONLY", null, null, null));
            if (point.getIntervalStatus() == GpsTrackDataPoint.IntervalStatus.UNKNOWN)
                issues.add(issue(null, point.getPointIndex(), "UNKNOWN_INTERVAL", null, null, point.getDurationBetweenPointsInSec()));
            if (point.getDistanceInMeterBetweenPoints() != null && point.getDistanceInMeterBetweenPoints() < VERTICAL_ONLY_DISTANCE_METRES
                && point.getAscentInMeterBetweenPoints() != null && Math.abs(point.getAscentInMeterBetweenPoints()) > VERTICAL_ONLY_CHANGE_METRES)
                issues.add(issue(null, point.getPointIndex(), "VERTICAL_ONLY_MOVEMENT", null, Math.abs(point.getAscentInMeterBetweenPoints()), point.getDurationBetweenPointsInSec()));
            if (Boolean.TRUE.equals(point.getEnergyLimited()))
                issues.add(issue(null, point.getPointIndex(), "ENERGY_LIMITED", null, null, point.getDurationBetweenPointsInSec()));
        }
        issues.sort(Comparator.comparingDouble(QualityIssue::reviewScore).reversed());
        return new TrackQualityReport(trackId, sourceHash,
                coordinates(raw.getTrack()), coordinates(canonical.getTrack()),
                raw.getRecordingBreakBeforePointIndexes(), canonical.getRecordingBreakBeforePointIndexes(), evidence,
                canonical.getProcessingSettings(), issues.stream().limit(MAX_REVIEW_ISSUES).toList(), issues.size());
    }

    private static QualityIssue issue(Integer raw, Integer canonical, String reason, Double horizontal, Double vertical, Double seconds) {
        double score = Math.max(horizontal == null ? 0 : horizontal,
                Math.max(vertical == null ? 0 : vertical, seconds == null ? 0 : seconds));
        return new QualityIssue(raw, canonical, reason, horizontal, vertical, seconds, score);
    }

    private static List<List<Double>> coordinates(LineString line) {
        if (line == null) return List.of();
        List<List<Double>> result = new ArrayList<>(line.getNumPoints());
        for (Coordinate p : line.getCoordinates())
            result.add(Arrays.asList(p.getX(), p.getY(),
                    Double.isFinite(p.getZ()) ? p.getZ() : null, Double.isFinite(p.getM()) ? p.getM() : null));
        return result;
    }
}
