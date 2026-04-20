package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.web.global.LatentThreadLocal;
import io.jenetics.jpx.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.GeodeticCalculator;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.joining;

@SuppressWarnings("StringConcatenationInsideStringBufferAppend")
@Service
@Slf4j
public class GPXReader {

    private static final LatentThreadLocal<GeodeticCalculator> geodeticCalculatorThreadLocal = new LatentThreadLocal<>(() -> new GeodeticCalculator(DefaultGeographicCRS.WGS84));

    private static final MathTransform WGS84_TO_WEB_MERCATOR = initWgs84ToWebMercator();

    static final double MAX_PLAUSIBLE_SPEED_MS = 416.0;
    static final double PROBATION_TRUST_TIME_S = 15.0;
    static final int GPS_STARTUP_WINDOW_SIZE = 10;
    /**
     * Maximum dt (seconds) credited to the speed check. Caps the "time credit" so a large
     * pause (e.g. 30 h between two Garmin activities merged into one file) cannot make a
     * cross-country jump appear plausible. 120 s × 416 m/s ≈ 50 km maximum allowed jump.
     */
    static final double MAX_DT_SPEED_CHECK_S = 120.0;
    /**
     * Maximum distance (m) from the main track at which a probation cluster may be promoted.
     * Clusters that stabilise further away than this are almost certainly bad GPS fixes.
     */
    static final double PROBATION_MAX_PROMOTION_DISTANCE_M = 5000.0;

    /**
     * Temporal gap (seconds) between consecutive cleaned GPS points that triggers a segment split.
     * 12 hours — points separated by more than this are treated as belonging to separate activities.
     */
    static final double SEGMENT_GAP_THRESHOLD_S = 12 * 3600.0;

    // Review Fix #10: LoadResult should be static
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class LoadResult {
        public GpsTrack gpsTrack;
        public LineString trackRAW;
        public LineString trackCleaned;
        public long processingTime;
    }

    private static class OutlierFilterResult {
        final List<Coordinate> cleanedCoordinates;
        final List<Coordinate> rawCoordinates;
        final List<Double> distancesBetweenPoints;
        int outlierCount;

        OutlierFilterResult() {
            this.cleanedCoordinates = new ArrayList<>();
            this.rawCoordinates = new ArrayList<>();
            this.distancesBetweenPoints = new ArrayList<>();
            this.outlierCount = 0;
        }
    }

    /**
     * Mutable state carried across segments within the same track.
     */
    private static class SegmentFilterState {
        Coordinate lastAcceptedPoint;
        final List<Coordinate> probationBuffer = new ArrayList<>();
        double lastElevation = 0.0;
    }

    @SneakyThrows
    public List<LoadResult> importGpxFile(IndexedFile indexedFile) {
        String xml = readFileContentAndClean(Paths.get(indexedFile.getFullPath()));
        return importGpxXml(indexedFile, xml);
    }

    /**
     * Import from pre-converted GPX XML (e.g. produced by GPSBabel from a FIT/TCX/KML file).
     * The original file on disk is never read — only the provided XML string is parsed.
     */
    @SneakyThrows
    public List<LoadResult> importGpxXml(IndexedFile indexedFile, String gpxXml) {
        long startTimeMs = System.currentTimeMillis();
        log.debug("About to read GPX file {}", indexedFile);

        GpsTrack gpsTrack = new GpsTrack();

        try {
            gpsTrack.setIndexedFile(indexedFile);
            gpsTrack.setDuplicateStatus(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET);
            gpsTrack.addLoadMessage("Starting import of GPX file.");

            GPX gpx = GPX.Reader.of(GPX.Reader.Mode.LENIENT).fromString(gpxXml);

            parseGpxMetadata(gpx, gpsTrack, indexedFile);

            List<WayPoint> allWayPoints = gpx.tracks().flatMap(Track::segments).flatMap(TrackSegment::points).toList();

            if (allWayPoints.isEmpty()) {
                log.info("No waypoints for file={}", indexedFile);
                gpsTrack.setLoadStatus(GpsTrack.LOAD_STATUS.EMPTY_FILE);
                gpsTrack.addLoadMessage("GPS did not contain any track data (was empty).");
                LoadResult emptyResult = new LoadResult();
                emptyResult.gpsTrack = gpsTrack;
                emptyResult.processingTime = System.currentTimeMillis() - startTimeMs;
                return List.of(emptyResult);
            }

            gpsTrack.setUtmZone(getUTMCode(allWayPoints.get(0).getLongitude().doubleValue(), allWayPoints.get(0).getLatitude().doubleValue()));

            OutlierFilterResult filterResult = filterOutliers(gpx, gpsTrack, indexedFile);

            // Fix A: extract time bounds from CLEANED coordinates (after outlier removal)
            extractTimeBoundsFromCleaned(filterResult.cleanedCoordinates, gpsTrack);

            // Split by temporal gaps (>12h between consecutive cleaned points)
            List<List<Coordinate>> segments = splitByTemporalGaps(filterResult.cleanedCoordinates);

            if (segments.size() <= 1) {
                // No gap found — single track, no segment metadata
                computeDistanceStats(gpsTrack, filterResult);
                LoadResult loadResult = new LoadResult();
                loadResult.gpsTrack = gpsTrack;
                buildGeometries(loadResult, gpsTrack, filterResult);
                gpsTrack.setLoadStatus(GpsTrack.LOAD_STATUS.SUCCESS);
                loadResult.processingTime = System.currentTimeMillis() - startTimeMs;
                return List.of(loadResult);
            }

            // Multiple segments detected — create one LoadResult per segment
            log.info("Temporal gap split: {} segments detected for file={}", segments.size(), indexedFile);
            gpsTrack.addLoadMessage(String.format("Track split into %d segments due to >12h inactivity gap.", segments.size()));

            List<LoadResult> results = new ArrayList<>();
            int rawSliceStart = 0;
            for (int i = 0; i < segments.size(); i++) {
                List<Coordinate> segCoords = segments.get(i);

                GpsTrack segTrack;
                if (i == 0) {
                    segTrack = gpsTrack; // Re-use main track instance
                } else {
                    segTrack = copyTrackMetadata(gpsTrack, indexedFile);
                    // Copy existing load messages from master (file-level stuff)
                    segTrack.setLoadMessages(gpsTrack.getLoadMessages());
                }

                segTrack.addLoadMessage(String.format("Processing segment %d of %d.", i + 1, segments.size()));
                segTrack.setSourceSegmentIndex(i + 1);

                // Per-segment time bounds from cleaned coordinates
                extractTimeBoundsFromCleaned(segCoords, segTrack);

                // Per-segment outlier result with just this segment's coordinates
                OutlierFilterResult segFilter = new OutlierFilterResult();
                segFilter.cleanedCoordinates.addAll(segCoords);

                // Accurately capture this segment's raw coordinates from the full list
                int rawSliceEnd;
                if (i < segments.size() - 1) {
                    Coordinate nextSegStart = segments.get(i + 1).get(0);
                    int nextSegStartIndex = indexOfExact(filterResult.rawCoordinates, nextSegStart, rawSliceStart);
                    rawSliceEnd = (nextSegStartIndex != -1) ? nextSegStartIndex - 1 : indexOfExact(filterResult.rawCoordinates, segCoords.get(segCoords.size() - 1), rawSliceStart);
                } else {
                    rawSliceEnd = filterResult.rawCoordinates.size() - 1;
                }

                if (rawSliceStart <= rawSliceEnd) {
                    segFilter.rawCoordinates.addAll(filterResult.rawCoordinates.subList(rawSliceStart, rawSliceEnd + 1));
                } else {
                    segFilter.rawCoordinates.addAll(segCoords); // Fallback
                }
                rawSliceStart = rawSliceEnd + 1;

                for (int j = 1; j < segCoords.size(); j++) {
                    double d = getDistanceBetweenTwoWGS84(segCoords.get(j), segCoords.get(j - 1));
                    segFilter.distancesBetweenPoints.add(d);
                }

                // Outlier detection ran on the whole file before splitting — per-segment counts
                // are not available, so we don't attempt to attribute them here.
                segTrack.setDidFilterOutlierByDistance(filterResult.outlierCount > 0);

                computeDistanceStats(segTrack, segFilter);

                LoadResult segResult = new LoadResult();
                segResult.gpsTrack = segTrack;
                buildGeometries(segResult, segTrack, segFilter);
                segTrack.setLoadStatus(GpsTrack.LOAD_STATUS.SUCCESS);
                segResult.processingTime = System.currentTimeMillis() - startTimeMs;
                results.add(segResult);
            }
            return results;

        } catch (Exception e) {
            gpsTrack.setLoadStatus(GpsTrack.LOAD_STATUS.FAILED);
            log.error("Failed to import GPX file={}", indexedFile, e);
            gpsTrack.addLoadMessage("Failed with an exception: " + e);
            LoadResult failResult = new LoadResult();
            failResult.gpsTrack = gpsTrack;
            failResult.processingTime = System.currentTimeMillis() - startTimeMs;
            return List.of(failResult);
        }
    }

    /**
     * Copy metadata from a master track to create a new segment track.
     */
    private GpsTrack copyTrackMetadata(GpsTrack source, IndexedFile indexedFile) {
        GpsTrack copy = new GpsTrack();
        copy.setIndexedFile(indexedFile);
        copy.setDuplicateStatus(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET);
        copy.setGpxVersion(source.getGpxVersion());
        copy.setCreator(source.getCreator());
        copy.setTrackName(source.getTrackName());
        copy.setTrackDescription(source.getTrackDescription());
        copy.setTrackType(source.getTrackType());
        copy.setMetaAuthor(source.getMetaAuthor());
        copy.setMetaTime(source.getMetaTime());
        copy.setMetaLink(source.getMetaLink());
        copy.setGarminActivityId(source.getGarminActivityId());
        copy.setUtmZone(source.getUtmZone());
        return copy;
    }

    /**
     * Extract time bounds from cleaned coordinates using the M ordinate (epoch seconds).
     */
    private void extractTimeBoundsFromCleaned(List<Coordinate> cleanedCoords, GpsTrack gpsTrack) {
        double minT = Double.MAX_VALUE;
        double maxT = -Double.MAX_VALUE;
        for (Coordinate c : cleanedCoords) {
            double t = c.getM();
            if (!Double.isNaN(t)) {
                if (t < minT) minT = t;
                if (t > maxT) maxT = t;
            }
        }
        if (minT != Double.MAX_VALUE) {
            gpsTrack.setStartDate(Date.from(Instant.ofEpochSecond((long) minT)));
        }
        if (maxT != -Double.MAX_VALUE) {
            gpsTrack.setEndDate(Date.from(Instant.ofEpochSecond((long) maxT)));
        }
    }

    private static int indexOfExact(List<Coordinate> list, Coordinate target, int startIndex) {
        for (int i = startIndex; i < list.size(); i++) {
            Coordinate c = list.get(i);
            if (c == target) return i;
            if (c.getX() == target.getX() && c.getY() == target.getY() &&
                (Double.compare(c.getM(), target.getM()) == 0 || (Double.isNaN(c.getM()) && Double.isNaN(target.getM())))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Split cleaned coordinates into sub-lists wherever consecutive points have a temporal gap > threshold.
     */
    static List<List<Coordinate>> splitByTemporalGaps(List<Coordinate> cleanedCoords) {
        if (cleanedCoords.size() < 2) {
            return List.of(cleanedCoords);
        }
        List<List<Coordinate>> segments = new ArrayList<>();
        List<Coordinate> current = new ArrayList<>();
        current.add(cleanedCoords.get(0));

        for (int i = 1; i < cleanedCoords.size(); i++) {
            double prevT = cleanedCoords.get(i - 1).getM();
            double currT = cleanedCoords.get(i).getM();
            if (!Double.isNaN(prevT) && !Double.isNaN(currT) && (currT - prevT) > SEGMENT_GAP_THRESHOLD_S) {
                segments.add(current);
                current = new ArrayList<>();
            }
            current.add(cleanedCoords.get(i));
        }
        segments.add(current);
        return segments;
    }

    private void parseGpxMetadata(GPX gpx, GpsTrack gpsTrack, IndexedFile indexedFile) {
        gpsTrack.setGpxVersion(gpx.getVersion());
        gpsTrack.setCreator(gpx.getCreator());
        gpsTrack.setTrackName(gpx.tracks().map(Track::getName).filter(Optional::isPresent).map(Optional::get).collect(joining(" ")));
        gpsTrack.setTrackDescription(gpx.tracks().map(Track::getDescription).filter(Optional::isPresent).map(Optional::get).collect(joining(" ")));
        gpsTrack.setTrackType(gpx.tracks().map(Track::getType).filter(Optional::isPresent).map(Optional::get).collect(joining(" ")));

        gpx.getMetadata().flatMap(Metadata::getAuthor).ifPresent(
                person -> gpsTrack.setMetaAuthor(person.getName().orElse("") + " " + (person.getEmail().isPresent() ? person.getEmail().get().getAddress() : "")));
        gpx.getMetadata().flatMap(Metadata::getTime).ifPresent(instant -> gpsTrack.setMetaTime(new Date(instant.toEpochMilli())));

        if (gpx.getMetadata().isPresent() && gpx.getMetadata().get().getLinks() != null) {
            gpsTrack.setMetaLink(StringUtils.substring(gpx.getMetadata().get().getLinks().stream().map(link -> link.getHref() + " " + link.getText().orElse("")).collect(joining(" ; ")), 0, 4000));
        }

        final String garminFilePrefix = "activity_";
        final String fileNameLowerCase = StringUtils.lowerCase(indexedFile.getName());
        if (StringUtils.containsIgnoreCase(fileNameLowerCase, garminFilePrefix)
            && (StringUtils.containsIgnoreCase(gpsTrack.getCreator(), "garmin")
                || StringUtils.containsAnyIgnoreCase(gpsTrack.getMetaLink(), "garmin", "connect"))) {
            String activityId = StringUtils.substringAfter(fileNameLowerCase, garminFilePrefix);
            activityId = StringUtils.substringBefore(activityId, ".gpx");
            gpsTrack.setGarminActivityId(activityId);
        }
    }

    /**
     * @deprecated Replaced by {@link #extractTimeBoundsFromCleaned} — kept for reference only.
     */
    @SuppressWarnings("unused")
    private void extractTimeBounds(List<WayPoint> allWayPoints, GpsTrack gpsTrack) {
        List<Instant> times = allWayPoints.stream().flatMap(wp -> wp.getTime().stream()).sorted().toList();
        if (!times.isEmpty()) {
            gpsTrack.setStartDate(Date.from(times.get(0)));
            gpsTrack.setEndDate(Date.from(times.get(times.size() - 1)));
        }
    }

    private OutlierFilterResult filterOutliers(GPX gpx, GpsTrack gpsTrack, IndexedFile indexedFile) {
        OutlierFilterResult result = new OutlierFilterResult();
        gpsTrack.setMaxDistanceBetweenPoints(0d);
        gpsTrack.setDidFilterOutlierByDistance(false);

        for (Track track : gpx.tracks().toList()) {
            // State is carried across segments within the same track.
            // Segment breaks (trkseg) represent brief GPS signal loss, not separate journeys.
            SegmentFilterState state = new SegmentFilterState();
            for (TrackSegment segment : track.segments().toList()) {
                processSegment(segment, state, gpsTrack, indexedFile, result);
            }
            // Flush any remaining probation at end of track
            if (!state.probationBuffer.isEmpty()) {
                result.outlierCount += state.probationBuffer.size();
                gpsTrack.setDidFilterOutlierByDistance(true);
                state.probationBuffer.clear();
            }
        }
        return result;
    }

    private void processSegment(TrackSegment segment, SegmentFilterState state, GpsTrack gpsTrack,
                                IndexedFile indexedFile, OutlierFilterResult result) {

        for (WayPoint wayPoint : segment.points().toList()) {
            if (wayPoint.getLatitude().doubleValue() == 0.0 && wayPoint.getLongitude().doubleValue() == 0.0) {
                log.debug("Ignoring exact 0.0, 0.0 coordinates (likely missing GPS fix)");
                continue;
            }

            double t = wayPoint.getTime().map(Instant::toEpochMilli).map(m -> m / 1000.0).orElse(Double.NaN);

            double currentElevation = wayPoint.getElevation().map(Length::doubleValue).orElse(Double.NaN);
            if (Double.isNaN(currentElevation)) {
                currentElevation = state.lastElevation;
            } else {
                state.lastElevation = currentElevation;
            }

            Coordinate coordinateLongLat = new CoordinateXYZM(
                    wayPoint.getLongitude().doubleValue(),
                    wayPoint.getLatitude().doubleValue(),
                    currentElevation,
                    t);

            result.rawCoordinates.add(coordinateLongLat);

            if (state.lastAcceptedPoint == null) {
                result.cleanedCoordinates.add(coordinateLongLat);
                state.lastAcceptedPoint = coordinateLongLat;
                continue;
            }

            double distMain = getDistanceBetweenTwoWGS84(coordinateLongLat, state.lastAcceptedPoint);
            double dtMain = Double.isNaN(coordinateLongLat.getM()) || Double.isNaN(state.lastAcceptedPoint.getM())
                    ? Double.NaN : coordinateLongLat.getM() - state.lastAcceptedPoint.getM();

            if (isPlausible(distMain, dtMain)) {
                if (!state.probationBuffer.isEmpty()) {
                    log.debug("Discarding {} probation points; snapped back to main track.", state.probationBuffer.size());
                    result.outlierCount += state.probationBuffer.size();
                    gpsTrack.setDidFilterOutlierByDistance(true);
                    state.probationBuffer.clear();
                }
                result.cleanedCoordinates.add(coordinateLongLat);
                state.lastAcceptedPoint = coordinateLongLat;
                result.distancesBetweenPoints.add(distMain);
                if (distMain > gpsTrack.getMaxDistanceBetweenPoints()) {
                    gpsTrack.setMaxDistanceBetweenPoints(distMain);
                }
            } else {
                state.lastAcceptedPoint = handleImplausiblePoint(coordinateLongLat, distMain, dtMain,
                        state.lastAcceptedPoint, state.probationBuffer, gpsTrack, indexedFile, result);
            }
        }
    }

    /**
     * Handles a point that failed the plausibility check against the main track.
     * Returns the (possibly updated) lastAcceptedPoint.
     */
    private Coordinate handleImplausiblePoint(Coordinate point, double distMain, double dtMain,
                                              Coordinate lastAcceptedPoint, List<Coordinate> probationBuffer,
                                              GpsTrack gpsTrack, IndexedFile indexedFile,
                                              OutlierFilterResult result) {
        if (probationBuffer.isEmpty()) {
            probationBuffer.add(point);
            if (result.outlierCount == 0) {
                // Formatting point coordinates neatly
                String cleanPointStr = String.format("lat:%.4f, lon:%.4f", point.getY(), point.getX());
                log.info("Outlier detected in filepath={} coordinate={} speed={} m/s — entering probation",
                        indexedFile, point,
                        (!Double.isNaN(dtMain) && dtMain > 0) ? String.format("%.0f", distMain / dtMain) : "inf");
                gpsTrack.addLoadMessage("Outlier detected at " + cleanPointStr + " — entering probation.");
            }
            return lastAcceptedPoint;
        }

        Coordinate lastProbation = probationBuffer.get(probationBuffer.size() - 1);
        double dtProb = Double.isNaN(point.getM()) || Double.isNaN(lastProbation.getM())
                ? Double.NaN : point.getM() - lastProbation.getM();
        double distProb = getDistanceBetweenTwoWGS84(point, lastProbation);

        if (isPlausible(distProb, dtProb)) {
            probationBuffer.add(point);

            boolean shouldPromote;
            if (!Double.isNaN(point.getM()) && !Double.isNaN(probationBuffer.get(0).getM())) {
                double probationDuration = point.getM() - probationBuffer.get(0).getM();
                shouldPromote = probationDuration >= PROBATION_TRUST_TIME_S;
            } else {
                shouldPromote = probationBuffer.size() >= 5;
            }

            if (shouldPromote) {
                // Guard: if the probation cluster is still unreasonably far from the main track
                // (e.g. GPS locked onto a satellite set 400 km away for 15 s), discard it.
                // FIX: Unless there is a massive time gap (>12h) indicating a legitimate new activity bridging distance
                double jumpFromMainTrack = getDistanceBetweenTwoWGS84(probationBuffer.get(0), lastAcceptedPoint);
                double dtFromMainTrack = Double.isNaN(probationBuffer.get(0).getM()) || Double.isNaN(lastAcceptedPoint.getM())
                        ? Double.NaN : probationBuffer.get(0).getM() - lastAcceptedPoint.getM();

                boolean isTemporalGapSplit = !Double.isNaN(dtFromMainTrack) && dtFromMainTrack > SEGMENT_GAP_THRESHOLD_S;
                boolean isStartupWindow = result.cleanedCoordinates.size() <= GPS_STARTUP_WINDOW_SIZE;

                if (jumpFromMainTrack > PROBATION_MAX_PROMOTION_DISTANCE_M && !isTemporalGapSplit && !isStartupWindow) {
                    log.info("Probation rejected: first probe point is {}m from main track, exceeds threshold {}m. Discarding {} points.",
                            String.format("%.0f", jumpFromMainTrack), PROBATION_MAX_PROMOTION_DISTANCE_M, probationBuffer.size());
                    result.outlierCount += probationBuffer.size();
                    gpsTrack.setDidFilterOutlierByDistance(true);
                    probationBuffer.clear();
                    return lastAcceptedPoint;
                }

                if (isTemporalGapSplit) {
                    log.info("Probation matched temporal gap threshold (>12h). Bypassing spatial jump guard of {}m.",
                            String.format("%.0f", jumpFromMainTrack));
                } else if (isStartupWindow && jumpFromMainTrack > PROBATION_MAX_PROMOTION_DISTANCE_M) {
                    log.info("Probation jump {}m allowed because current track is still in startup phase ({} points).",
                            String.format("%.0f", jumpFromMainTrack), result.cleanedCoordinates.size());
                } else {
                    log.info("Probation track stabilized ({} points). Trusting new location.", probationBuffer.size());
                }

                if (result.cleanedCoordinates.size() <= GPS_STARTUP_WINDOW_SIZE) {
                    result.cleanedCoordinates.clear();
                    result.distancesBetweenPoints.clear();
                    gpsTrack.setMaxDistanceBetweenPoints(0d);
                }

                result.cleanedCoordinates.addAll(probationBuffer);
                for (int i = 1; i < probationBuffer.size(); i++) {
                    double d = getDistanceBetweenTwoWGS84(probationBuffer.get(i), probationBuffer.get(i - 1));
                    result.distancesBetweenPoints.add(d);
                    if (d > gpsTrack.getMaxDistanceBetweenPoints()) {
                        gpsTrack.setMaxDistanceBetweenPoints(d);
                    }
                }

                lastAcceptedPoint = point;
                probationBuffer.clear();
            }
        } else {
            result.outlierCount += probationBuffer.size();
            gpsTrack.setDidFilterOutlierByDistance(true);
            probationBuffer.clear();
            probationBuffer.add(point);
        }

        return lastAcceptedPoint;
    }

    private void computeDistanceStats(GpsTrack gpsTrack, OutlierFilterResult filterResult) {
        double[] distArray = filterResult.distancesBetweenPoints.stream().mapToDouble(Double::doubleValue).toArray();
        if (distArray.length > 0) {
            gpsTrack.setMaxDistanceBetweenPoints(StatUtils.max(distArray));
            gpsTrack.setMedianDistanceBetweenPoints(new Percentile(50).evaluate(distArray));
            gpsTrack.setAvgDistanceBetweenPoints(StatUtils.mean(distArray));
        } else {
            gpsTrack.setMaxDistanceBetweenPoints(0d);
            gpsTrack.setMedianDistanceBetweenPoints(0d);
            gpsTrack.setAvgDistanceBetweenPoints(0d);
        }

        String summary = String.format("Track Data Summary:" +
                                       "\n  - Outliers Found: %b" +
                                       "\n  - Outliers Count: %d" +
                                       "\n  - Distances (m):" +
                                       "\n      • Max:    %.1f" +
                                       "\n      • Median: %.1f" +
                                       "\n      • Avg:    %.1f",
                gpsTrack.getDidFilterOutlierByDistance(),
                filterResult.outlierCount,
                gpsTrack.getMaxDistanceBetweenPoints(),
                gpsTrack.getMedianDistanceBetweenPoints(),
                gpsTrack.getAvgDistanceBetweenPoints()
        );
        log.info("{} file={}", summary.replace('\n', ' '), gpsTrack.getIndexedFile().getName());
        gpsTrack.addLoadMessage(summary);
    }

    private void buildGeometries(LoadResult loadResult, GpsTrack gpsTrack, OutlierFilterResult filterResult) {
        GeometryFactory geometryFactoryLongLat = new GeometryFactory(new PrecisionModel(), 4326);

        if (filterResult.cleanedCoordinates.size() >= 2) {
            LineString cleaned = new LineString(new CoordinateArraySequence(filterResult.cleanedCoordinates.toArray(new Coordinate[0])), geometryFactoryLongLat);
            gpsTrack.setTrackLengthInMeter(getDistanceOfWGS84(cleaned));
            gpsTrack.setNumberOfTrackPoints(cleaned.getNumPoints());
            loadResult.trackCleaned = cleaned;
        } else {
            gpsTrack.setTrackLengthInMeter(0d);
            gpsTrack.setNumberOfTrackPoints(filterResult.cleanedCoordinates.size());
        }

        if (filterResult.rawCoordinates.size() >= 2) {
            LineString raw = new LineString(new CoordinateArraySequence(filterResult.rawCoordinates.toArray(new Coordinate[0])), geometryFactoryLongLat);
            loadResult.trackRAW = raw;
        }
    }

    /**
     * Checks whether a point is physically plausible.
     */
    static boolean isPlausible(double distance, double dt) {
        if (Double.isNaN(dt)) return true;                // no time info -> don't speed-filter
        if (dt < 0) return false;                         // backwards timestamp
        if (dt == 0) return distance <= 5.0;              // max 5 m drift for identical timestamps
        double effectiveDt = Math.min(dt, MAX_DT_SPEED_CHECK_S); // cap time credit
        if ((distance / effectiveDt) > MAX_PLAUSIBLE_SPEED_MS) return false; // speed check
        // When dt exceeds the cap, the speed check alone cannot reliably detect outliers
        // because the capped dt inflates the allowed distance budget (~50 km).
        // Apply an absolute distance limit to catch far-away points that appear plausible
        // only because of the generous time credit.
        if (dt > MAX_DT_SPEED_CHECK_S && distance > PROBATION_MAX_PROMOTION_DISTANCE_M) return false;
        return true;
    }

    /**
     * Review Fix #4: UTM Code string format
     */
    private static String getUTMCode(double longitude, double latitude) {
        int utmZone = Math.min(60, (int) Math.floor((longitude + 180) / 6) + 1);
        int epsgBase = latitude >= 0 ? 326 : 327;
        return String.format("EPSG:%d%02d", epsgBase, utmZone);
    }

    /**
     * Review Fix #6: Faster, memory-safe regex without JSoup DOM loading.
     */
    private static String readFileContentAndClean(Path filePath) throws IOException {
        String xml = Files.readString(filePath, StandardCharsets.UTF_8);
        // Strip UTF-8 BOM (U+FEFF) if present — causes "Content is not allowed in prolog" in XML parsers
        if (xml.startsWith("\uFEFF")) {
            xml = xml.substring(1);
        }
        return xml.replaceAll("(?s)<\\?xml-stylesheet.*?\\?>", "");
    }

    public static double getDistanceOfWGS84(LineString lineString) {
        double length = 0.0;

        for (int i = 0; i < lineString.getNumPoints() - 1; i++) {
            length += getDistanceBetweenTwoWGS84(lineString.getCoordinateN(i), lineString.getCoordinateN(i + 1));
        }

        return length;
    }

    public static double getDistanceBetweenTwoWGS84(Coordinate c1, Coordinate c2) {
        if (c1.x == c2.x && c1.y == c2.y) {
            return 0.0;
        }
        GeodeticCalculator geodeticCalculator = geodeticCalculatorThreadLocal.get();
        geodeticCalculator.setStartingGeographicPoint(c1.x, c1.y);
        geodeticCalculator.setDestinationGeographicPoint(c2.x, c2.y);
        return geodeticCalculator.getOrthodromicDistance();
    }

    public static Double getEuclideanDistanceBetweenMercatorPoints(Coordinate c1, Coordinate c2) {
        if (c1 != null && c2 != null) {
            return Math.sqrt(Math.pow(c1.getX() - c2.getX(), 2) + Math.pow(c1.getY() - c2.getY(), 2));
        }
        return null;
    }


    @SneakyThrows
    public Point convertLongLatWgs84ToPlanarWebMercator(double longitude, double latitude) {
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        Point transformed = (Point) JTS.transform(point, WGS84_TO_WEB_MERCATOR);
        transformed.setSRID(3857);
        return transformed;
    }

    @SneakyThrows
    private static MathTransform initWgs84ToWebMercator() {
        CoordinateReferenceSystem wgs84 = CRS.decode("EPSG:4326", true);
        CoordinateReferenceSystem webMercator = CRS.decode("EPSG:3857", true);
        return CRS.findMathTransform(wgs84, webMercator, true);
    }

}
