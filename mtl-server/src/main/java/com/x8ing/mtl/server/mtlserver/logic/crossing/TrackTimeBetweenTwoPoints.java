package com.x8ing.mtl.server.mtlserver.logic.crossing;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.*;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class TrackTimeBetweenTwoPoints {

    private final GpsTrackRepository gpsTrackRepository;
    private final GpsTrackDataPointRepository gpsTrackDataPointRepository;
    private final GpsTrackVariantSelector gpsTrackVariantSelector;

    public TrackTimeBetweenTwoPoints(GpsTrackRepository gpsTrackRepository, GpsTrackDataPointRepository gpsTrackDataPointRepository, GpsTrackVariantSelector gpsTrackVariantSelector) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.gpsTrackDataPointRepository = gpsTrackDataPointRepository;
        this.gpsTrackVariantSelector = gpsTrackVariantSelector;
    }

    public CrossingPointsResponse getTrackTimeBetweenPoints(CrossingPointsRequest crossingPointsRequest, Long[] tracksIdFilterList) {

        long t0 = System.currentTimeMillis();
        CrossingPointsResponse crossingPointsResponse = new CrossingPointsResponse();

        // check valid params
        if (crossingPointsRequest == null) {
            throw new IllegalArgumentException("crossingPointsRequest can't be null.");
        }
        if (crossingPointsRequest.radius == null || crossingPointsRequest.radius <= 0) {
            throw new IllegalArgumentException("radius must be greater than 0.");
        }
        if (crossingPointsRequest.triggerPoints == null || crossingPointsRequest.triggerPoints.isEmpty()) {
            throw new IllegalArgumentException("triggerPoints can't be empty.");
        }

        if (!checkTriggerPointNamesAreUnique(crossingPointsRequest.triggerPoints)) {
            throw new IllegalArgumentException("All TriggerPoints must have unique names.");
        }

        // load all tracks which cross any trigger points and find the common ones
        List<Long> commonTrackIds = null;
        Map<String, Integer> tracksPerZone = new HashMap<>();
        for (TriggerPoint triggerPoint : crossingPointsRequest.triggerPoints) {
            List<Long> tracks = gpsTrackRepository.getTracksWithinDistanceToPoint(triggerPoint.coordinate.x, triggerPoint.coordinate.y, crossingPointsRequest.radius, tracksIdFilterList);
            tracksPerZone.put(triggerPoint.name, tracks.size());
            if (commonTrackIds == null) {
                commonTrackIds = new ArrayList<>(tracks); // first one... just add the first tracks we get
            } else {
                commonTrackIds.retainAll(tracks); // if it's a subsequent, only keep the tracks present before...
            }
        }

        // filter out if a given filter array is given...
//        if (tracksIdFilterList != null && !tracksIdFilterList.isEmpty() && commonTrackIds != null) {
//            commonTrackIds.retainAll(tracksIdFilterList); // Retain only the IDs that are present in trackIdList
//        }

        long relativeTimeAfterLoadingTracks = System.currentTimeMillis() - t0;

        Map<String, Segment> trackPointCombinations = new HashMap<>();
        Map<Long, CrossingsPerTrack> crossings = new HashMap<>();

        if (commonTrackIds != null) {

            // execute it multithreaded    : here happens the intense computing...
            List<CrossingsPerTrack> tmpList = commonTrackIds.stream().parallel()
                    .map(trackId -> processingCrossingForOneTrack(crossingPointsRequest, trackId))
                    .toList();

            // now update the overall crossing stats
            tmpList.forEach(cpt -> {
                crossings.put(cpt.getGpsTrack().getId(), cpt);
                if (cpt.getCrossings() != null) {
                    Crossing lastCrossing = null;
                    for (final Crossing crossing : cpt.getCrossings()) {
                        if (lastCrossing != null) {
                            String crossingKey = getTriggerPointsComposedKey(lastCrossing.triggerPoint, crossing.triggerPoint);
                            Crossing finalLastCrossing = lastCrossing;
                            trackPointCombinations.computeIfAbsent(crossingKey, s -> new Segment(finalLastCrossing.triggerPoint.name, crossing.triggerPoint.name, crossingKey, 0, 0d));
                            trackPointCombinations.computeIfPresent(crossingKey, (k, v) -> {
                                v.setCount(v.count + 1);
                                v.setTimeDelta(v.timeDelta + crossing.timeInSecSinceLastTriggerPoint);
                                return v;
                            });
                        }
                        lastCrossing = crossing;
                    }
                }
            });
        }

        crossingPointsResponse.crossings = crossings;
        crossingPointsResponse.segmentsStats = trackPointCombinations.values().stream().sorted(Comparator.comparingInt(o -> -o.count)).toList();
        crossingPointsResponse.triggerPoints = crossingPointsRequest.triggerPoints;
        crossingPointsResponse.tracksPerZone = tracksPerZone;

        long relativeTimeFinished = System.currentTimeMillis() - t0;
        int candidateTracks = (commonTrackIds != null) ? commonTrackIds.size() : 0;
        int totalCrossings = crossings.values().stream().mapToInt(cpt -> cpt.getCrossings() != null ? cpt.getCrossings().size() : 0).sum();

        log.info("Processed getTrackTimeBetweenPoints: triggerPoints={}, radius={}m, candidateTracks={}, totalCrossings={}, timeLoadTracks={}ms, timeTotal={}ms",
                crossingPointsRequest.triggerPoints.size(),
                crossingPointsRequest.radius,
                candidateTracks,
                totalCrossings,
                relativeTimeAfterLoadingTracks,
                relativeTimeFinished);

        return crossingPointsResponse;
    }

    private CrossingsPerTrack processingCrossingForOneTrack(CrossingPointsRequest crossingPointsRequest, Long trackId) {
        long t0 = System.currentTimeMillis();
        // find all crossings (assuming track is ordered in time, which should be true for GPS)
        // Use the canonical metric variant (full GPS density, outliers + altitude jitter
        // removed). This preserves 1-Hz sampling on straight sections that the SIMPLIFIED
        // variants would drop, which matters for accurate crossing time/speed.
        List<Crossing> crossingList = new ArrayList<>();
        Long trackDataId = gpsTrackVariantSelector.forMetricsId(trackId);
        List<GpsTrackDataPoint> gpsTrackDataPoints = gpsTrackDataPointRepository.findAllByGpsTrackDataIdOrderByPointIndexAsc(trackDataId);

        findCrossingsForOneTrack(
                crossingPointsRequest.triggerPoints,
                crossingPointsRequest.radius,
                gpsTrackDataPoints,
                crossingList,
                trackId
        );

        // Annotate each segment (previous crossing → this crossing) with notes
        // derived from the raw samples that fall inside the segment — currently
        // just stop detection so the client can flag segments where racer stats
        // are biased by a pause.
        annotateSegmentNotes(crossingList, gpsTrackDataPoints);

        log.info("Calculated crossings for trackId={}  in dT={}", trackId, System.currentTimeMillis() - t0);

        return new CrossingsPerTrack(gpsTrackRepository.findById(trackId).orElse(null), crossingList);

    }

    /**
     * Finds all crossings for a single track using state-machine logic.
     * Each trigger point's state is tracked independently, allowing for precise
     * detection of zone entries and exits, even when zones overlap.
     *
     * <p>For each zone visit (OUTSIDE -> INSIDE -> OUTSIDE), a single crossing is
     * recorded at the track location that is <b>closest to the trigger point's
     * center</b> during that visit. This ensures that both the sector measurement
     * (time/distance between two points) and the race animation use the point on
     * the track closest to A and B rather than the zone-entry edge, which would
     * otherwise offset the result by up to the radius on each side.
     *
     * <p>Within each segment, we evaluate the perpendicular foot of the center on
     * the segment (clamped to the segment endpoints) so that the closest approach
     * is found at sub-sample accuracy even when the GPS cadence is coarse.
     */
    private void findCrossingsForOneTrack(
            List<TriggerPoint> triggerPoints,
            Double radius,
            List<GpsTrackDataPoint> gpsTrackDataPoints,
            List<Crossing> crossings,
            Long gpsTrackId
    ) {
        if (gpsTrackDataPoints == null || gpsTrackDataPoints.size() < 2) {
            return; // Need at least 2 points to detect a crossing
        }

        // Initialize state for each trigger point based on the first point.
        // A track that starts already inside a zone is treated as if we just entered.
        Map<String, TriggerState> triggerStates = new HashMap<>();
        // For each trigger point, holds the best (closest-to-center) candidate seen
        // during the CURRENT zone visit. Null/absent between visits.
        Map<String, ClosestCandidate> bestDuringVisit = new HashMap<>();

        GpsTrackDataPoint firstPoint = gpsTrackDataPoints.get(0);
        for (TriggerPoint tp : triggerPoints) {
            double d0 = GPXReader.getDistanceBetweenTwoWGS84(
                    tp.coordinate, firstPoint.getPointLongLat().getCoordinate());
            if (d0 <= radius) {
                triggerStates.put(tp.name, TriggerState.INSIDE);
                bestDuringVisit.put(tp.name, new ClosestCandidate(firstPoint, firstPoint, 0.0, d0));
            } else {
                triggerStates.put(tp.name, TriggerState.OUTSIDE);
            }
        }

        // Track the single most recent crossing on this track (regardless of which trigger point)
        Crossing lastCrossingOnThisTrack = null;

        // Process consecutive point pairs to detect state transitions
        for (int i = 1; i < gpsTrackDataPoints.size(); i++) {
            GpsTrackDataPoint p1 = gpsTrackDataPoints.get(i - 1);
            GpsTrackDataPoint p2 = gpsTrackDataPoints.get(i);

            // Check each trigger point independently
            for (TriggerPoint triggerPoint : triggerPoints) {
                double dist1 = GPXReader.getDistanceBetweenTwoWGS84(
                        triggerPoint.coordinate, p1.getPointLongLat().getCoordinate());
                double dist2 = GPXReader.getDistanceBetweenTwoWGS84(
                        triggerPoint.coordinate, p2.getPointLongLat().getCoordinate());

                TriggerState currentState = triggerStates.get(triggerPoint.name);
                boolean p2Inside = dist2 <= radius;

                // Closest approach of THIS segment (including endpoints) to the trigger center.
                ClosestApproach ca = computeClosestApproachOnSegment(
                        p1, p2, triggerPoint.coordinate, dist1, dist2);

                if (currentState == TriggerState.OUTSIDE) {
                    if (p2Inside) {
                        // Entering zone on this segment. Seed the per-visit best with this
                        // segment's closest approach (which already accounts for p1, p2 and
                        // the perpendicular foot).
                        bestDuringVisit.put(triggerPoint.name,
                                new ClosestCandidate(p1, p2, ca.factor, ca.distance));
                        triggerStates.put(triggerPoint.name, TriggerState.INSIDE);
                        log.debug("Entered trigger point '{}' zone", triggerPoint.name);
                    } else if (ca.distance <= radius) {
                        // Tangent pass: segment dips into the zone and exits within the
                        // same segment (both endpoints outside). Emit a single crossing at
                        // the closest approach to the center.
                        GpsTrackDataPoint crossingPoint = interpolateCrossingPointByFactor(
                                p1, p2, ca.factor, gpsTrackId);
                        recordCrossing(crossingPoint, triggerPoint, gpsTrackId,
                                lastCrossingOnThisTrack, crossings);
                        lastCrossingOnThisTrack = crossings.get(crossings.size() - 1);
                        log.debug("Tangent crossing detected for trigger point '{}'", triggerPoint.name);
                    }
                    // else: segment never touches the zone; nothing to do.
                } else { // INSIDE
                    // Update per-visit best if this segment has a closer approach.
                    ClosestCandidate best = bestDuringVisit.get(triggerPoint.name);
                    if (best == null || ca.distance < best.distance) {
                        bestDuringVisit.put(triggerPoint.name,
                                new ClosestCandidate(p1, p2, ca.factor, ca.distance));
                    }
                    if (!p2Inside) {
                        // Exiting the zone on this segment: emit the crossing at the
                        // closest-to-center point accumulated during the visit.
                        emitBestCrossing(bestDuringVisit.get(triggerPoint.name),
                                triggerPoint, gpsTrackId, lastCrossingOnThisTrack, crossings);
                        lastCrossingOnThisTrack = crossings.get(crossings.size() - 1);
                        bestDuringVisit.remove(triggerPoint.name);
                        triggerStates.put(triggerPoint.name, TriggerState.OUTSIDE);
                        log.debug("Exited trigger point '{}' zone; crossing recorded at closest point to center",
                                triggerPoint.name);
                    }
                }
            }
        }

        // Flush any zone that the track ended inside: emit the closest-approach crossing.
        for (TriggerPoint triggerPoint : triggerPoints) {
            if (triggerStates.get(triggerPoint.name) == TriggerState.INSIDE) {
                ClosestCandidate best = bestDuringVisit.get(triggerPoint.name);
                if (best != null) {
                    emitBestCrossing(best, triggerPoint, gpsTrackId, lastCrossingOnThisTrack, crossings);
                    lastCrossingOnThisTrack = crossings.get(crossings.size() - 1);
                }
            }
        }

        // Sort crossings by timestamp to ensure chronological order
        // (multiple trigger points could be crossed between the same two GPS points)
        crossings.sort(Comparator.comparing(c -> c.gpsTrackDataPoint.getPointTimestamp()));
    }

    private void emitBestCrossing(
            ClosestCandidate best,
            TriggerPoint triggerPoint,
            Long gpsTrackId,
            Crossing lastCrossingOnThisTrack,
            List<Crossing> crossings
    ) {
        GpsTrackDataPoint crossingPoint = interpolateCrossingPointByFactor(
                best.p1, best.p2, best.factor, gpsTrackId);
        recordCrossing(crossingPoint, triggerPoint, gpsTrackId, lastCrossingOnThisTrack, crossings);
    }

    // ---------------------------------------------------------------------
    // Segment notes (stop detection)
    // ---------------------------------------------------------------------

    /**
     * Populates {@link Crossing#segmentNotesSinceLastTriggerPoint} for every
     * crossing after the first. Delegates to
     * {@link com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer}
     * so the same stop-detection logic powers both per-segment annotations and
     * the per-track totals persisted on {@code GpsTrack} at ingest.
     */
    private void annotateSegmentNotes(List<Crossing> crossings, List<GpsTrackDataPoint> gpsTrackDataPoints) {
        if (crossings == null || crossings.size() < 2 || gpsTrackDataPoints == null || gpsTrackDataPoints.isEmpty()) {
            return;
        }
        for (int i = 1; i < crossings.size(); i++) {
            Crossing prev = crossings.get(i - 1);
            Crossing curr = crossings.get(i);
            if (prev.gpsTrackDataPoint == null || curr.gpsTrackDataPoint == null
                || prev.gpsTrackDataPoint.getPointTimestamp() == null
                || curr.gpsTrackDataPoint.getPointTimestamp() == null) {
                continue;
            }
            long fromMs = prev.gpsTrackDataPoint.getPointTimestamp().getTime();
            long toMs = curr.gpsTrackDataPoint.getPointTimestamp().getTime();
            if (toMs <= fromMs) {
                continue; // Degenerate; skip.
            }
            curr.segmentNotesSinceLastTriggerPoint =
                    TrackMotionAnalyzer.detectStopsInRange(gpsTrackDataPoints, fromMs, toMs);
        }
    }

    /**
     * Closest-approach computation for a single segment against a center point.
     * Uses a local planar projection (equirectangular around the center) to compute
     * the perpendicular foot on the segment, clamped to [0,1]. The returned distance
     * is then recomputed with the haversine (GPXReader) formula for accuracy, and
     * compared against the endpoint distances so we always return a true minimum.
     */
    private ClosestApproach computeClosestApproachOnSegment(
            GpsTrackDataPoint p1Point,
            GpsTrackDataPoint p2Point,
            Coordinate center,
            double dist1,
            double dist2
    ) {
        Coordinate p1 = p1Point.getPointLongLat().getCoordinate();
        Coordinate p2 = p2Point.getPointLongLat().getCoordinate();

        double metersPerDegLon = 111320.0 * Math.cos(Math.toRadians(center.y));
        double metersPerDegLat = 110540.0;

        double x1 = (p1.x - center.x) * metersPerDegLon;
        double y1 = (p1.y - center.y) * metersPerDegLat;
        double x2 = (p2.x - center.x) * metersPerDegLon;
        double y2 = (p2.y - center.y) * metersPerDegLat;

        double dx = x2 - x1;
        double dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;

        double t;
        if (lenSq <= 0.0) {
            t = 0.0;
        } else {
            // Project origin (the center, after translation) onto the segment direction.
            t = -(x1 * dx + y1 * dy) / lenSq;
            t = Math.max(0.0, Math.min(1.0, t));
        }

        double lon = p1.x + (p2.x - p1.x) * t;
        double lat = p1.y + (p2.y - p1.y) * t;
        double distance = GPXReader.getDistanceBetweenTwoWGS84(center, new Coordinate(lon, lat));

        // Safety: endpoint distances are authoritative; pick whichever is smallest.
        if (dist1 < distance) {
            distance = dist1;
            t = 0.0;
        }
        if (dist2 < distance) {
            distance = dist2;
            t = 1.0;
        }
        return new ClosestApproach(t, distance);
    }

    private static final class ClosestApproach {
        final double factor;
        final double distance;

        ClosestApproach(double factor, double distance) {
            this.factor = factor;
            this.distance = distance;
        }
    }

    private static final class ClosestCandidate {
        final GpsTrackDataPoint p1;
        final GpsTrackDataPoint p2;
        final double factor;
        final double distance;

        ClosestCandidate(GpsTrackDataPoint p1, GpsTrackDataPoint p2, double factor, double distance) {
            this.p1 = p1;
            this.p2 = p2;
            this.factor = factor;
            this.distance = distance;
        }
    }

    /**
     * Records a crossing event by calculating metrics and adding it to the crossings list.
     */
    private void recordCrossing(
            GpsTrackDataPoint crossingPoint,
            TriggerPoint triggerPoint,
            Long gpsTrackId,
            Crossing lastCrossingOnThisTrack,
            List<Crossing> crossings
    ) {
        double timeInSecSinceLastTriggerPoint = 0;
        double distanceInMeterSinceLastTriggerPoint = 0;
        double avgSpeedSinceLastTriggerPoint = 0;

        if (lastCrossingOnThisTrack != null
            && crossingPoint.getPointTimestamp() != null
            && lastCrossingOnThisTrack.gpsTrackDataPoint != null
            && lastCrossingOnThisTrack.gpsTrackDataPoint.getPointTimestamp() != null
            && lastCrossingOnThisTrack.gpsTrackDataPoint.getDistanceInMeterSinceStart() != null
            && crossingPoint.getDistanceInMeterSinceStart() != null) {

            timeInSecSinceLastTriggerPoint = (1.0d * crossingPoint.getPointTimestamp().getTime()
                                              - lastCrossingOnThisTrack.gpsTrackDataPoint.getPointTimestamp().getTime()) / 1000;

            distanceInMeterSinceLastTriggerPoint = (crossingPoint.getDistanceInMeterSinceStart()
                                                    - lastCrossingOnThisTrack.gpsTrackDataPoint.getDistanceInMeterSinceStart());

            if (timeInSecSinceLastTriggerPoint > 0) {
                avgSpeedSinceLastTriggerPoint = distanceInMeterSinceLastTriggerPoint
                                                / timeInSecSinceLastTriggerPoint * 3600 / 1000; // km/h
            }
        }

        // Create and store the crossing
        Crossing crossing = new Crossing(
                triggerPoint,
                crossingPoint,
                gpsTrackId,
                0.0, // Distance to trigger point is approximately 0 at the crossing
                timeInSecSinceLastTriggerPoint,
                distanceInMeterSinceLastTriggerPoint,
                avgSpeedSinceLastTriggerPoint,
                null // segment notes — populated later by annotateSegmentNotes()
        );

        crossings.add(crossing);
    }

    /**
     * Interpolates a GPS point at parameter t ∈ [0,1] along the segment from p1 to p2.
     * t=0 corresponds to p1, t=1 corresponds to p2.
     */
    private GpsTrackDataPoint interpolateCrossingPointByFactor(
            GpsTrackDataPoint p1,
            GpsTrackDataPoint p2,
            double t,
            Long gpsTrackId
    ) {
        // Clamp to [0, 1] to ensure we stay within the segment
        t = Math.max(0.0, Math.min(1.0, t));

        // Interpolate coordinates
        Coordinate coord1 = p1.getPointLongLat().getCoordinate();
        Coordinate coord2 = p2.getPointLongLat().getCoordinate();

        double interpolatedLon = coord1.x + (coord2.x - coord1.x) * t;
        double interpolatedLat = coord1.y + (coord2.y - coord1.y) * t;
        double z1 = Double.isNaN(coord1.z) ? 0.0 : coord1.z;
        double z2 = Double.isNaN(coord2.z) ? 0.0 : coord2.z;
        double interpolatedAlt = z1 + (z2 - z1) * t;

        // Interpolate timestamp
        Date interpolatedTimestamp = null;
        if (p1.getPointTimestamp() != null && p2.getPointTimestamp() != null) {
            long time1 = p1.getPointTimestamp().getTime();
            long time2 = p2.getPointTimestamp().getTime();
            long interpolatedTime = time1 + (long) ((time2 - time1) * t);
            interpolatedTimestamp = new Date(interpolatedTime);
        }

        // Interpolate distance since start
        Double interpolatedDistance = null;
        if (p1.getDistanceInMeterSinceStart() != null && p2.getDistanceInMeterSinceStart() != null) {
            interpolatedDistance = p1.getDistanceInMeterSinceStart()
                                   + (p2.getDistanceInMeterSinceStart() - p1.getDistanceInMeterSinceStart()) * t;
        }

        // Create a new GpsTrackDataPoint for the interpolated crossing
        GpsTrackDataPoint crossingPoint = new GpsTrackDataPoint();

        // Set id/gpsTrackDataId to the nearest actual data point so the virtual race
        // sub-track API can resolve this crossing to a real database row.
        GpsTrackDataPoint nearest = t <= 0.5 ? p1 : p2;
        crossingPoint.setId(nearest.getId());
        crossingPoint.setGpsTrackDataId(nearest.getGpsTrackDataId());

        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
        Coordinate interpolatedCoord = new Coordinate(interpolatedLon, interpolatedLat, interpolatedAlt);
        crossingPoint.setPointLongLat((org.locationtech.jts.geom.Point) factory.createPoint(interpolatedCoord));

        crossingPoint.setPointTimestamp(interpolatedTimestamp);
        crossingPoint.setDistanceInMeterSinceStart(interpolatedDistance);

        crossingPoint.setPointIndex(p1.getPointIndex() != null && p2.getPointIndex() != null
                ? (int) (p1.getPointIndex() + (p2.getPointIndex() - p1.getPointIndex()) * t)
                : null);

        return crossingPoint;
    }

    private static boolean checkTriggerPointNamesAreUnique(List<TriggerPoint> triggerPoints) {
        Set<String> uniqueNames = new HashSet<>();

        for (TriggerPoint point : triggerPoints) {
            if (uniqueNames.contains(point.name)) {
                return false;
            } else {
                uniqueNames.add(point.name);
            }
        }
        return true;
    }

    private static String getTriggerPointsComposedKey(TriggerPoint p1, TriggerPoint p2) {
        return p1.name + "-" + p2.name;
    }

}
