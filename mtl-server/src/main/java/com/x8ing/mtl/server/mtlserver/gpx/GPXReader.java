package com.x8ing.mtl.server.mtlserver.gpx;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackStopDetector;
import com.x8ing.mtl.server.mtlserver.utils.TimingCollector;
import io.jenetics.jpx.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicMask;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.springframework.stereotype.Service;

import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
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

    private static final double WGS84_SEMI_MAJOR_AXIS_M = 6_378_137.0;
    private static final double WGS84_FLATTENING = 1.0 / 298.257_223_563;
    private static final double WGS84_SEMI_MINOR_AXIS_M =
            WGS84_SEMI_MAJOR_AXIS_M * (1.0 - WGS84_FLATTENING);
    private static final double VINCENTY_CONVERGENCE_RADIANS = 1e-12;
    private static final int VINCENTY_MAX_ITERATIONS = 100;
    private static final double WEB_MERCATOR_MAX_LATITUDE = 85.051_128_779_806_6;
    private static final int WEB_MERCATOR_SRID = 3857;
    private static final GeometryFactory WEB_MERCATOR_GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), WEB_MERCATOR_SRID);
    private static final String XML_STYLESHEET_PREFIX = "?xml-stylesheet";

    static final double MAX_PLAUSIBLE_SPEED_MS = 416.0;
    static final double PROBATION_TRUST_TIME_S = 15.0;
    static final int GPS_STARTUP_WINDOW_SIZE = 10;
    private static final String DISTANCE_PROBATION_CORRECTOR_NAME = "GPXReader distance/probation filter";
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
     * 12 hours — non-stop gaps longer than this are treated as separate activities. Known
     * synthetic stop anchor pairs remain one long stop inside the activity.
     */
    static final double SEGMENT_GAP_THRESHOLD_S = 12 * 3600.0;
    private static final double STOP_ANCHOR_TIME_TOLERANCE_S = 1.1;

    // Review Fix #10: LoadResult should be static
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @JsonPropertyOrder({
            "gpsTrack",
            "trackRAW",
            "trackCleaned",
            "stopRanges",
            "processingTime"
    })
    public static class LoadResult {
        public GpsTrack gpsTrack;
        public LineString trackRAW;
        public LineString trackCleaned;
        public List<TrackStopDetector.StopRange> stopRanges = new ArrayList<>();
        public long processingTime;
    }

    @JsonPropertyOrder({
            "cleanedCoordinates",
            "rawCoordinates",
            "distancesBetweenPoints",
            "stopRanges",
            "outlierCount"
    })
    private static class OutlierFilterResult {
        final List<Coordinate> cleanedCoordinates;
        final List<Coordinate> rawCoordinates;
        final List<Double> distancesBetweenPoints;
        final List<TrackStopDetector.StopRange> stopRanges;
        int outlierCount;

        OutlierFilterResult() {
            this.cleanedCoordinates = new ArrayList<>();
            this.rawCoordinates = new ArrayList<>();
            this.distancesBetweenPoints = new ArrayList<>();
            this.stopRanges = new ArrayList<>();
            this.outlierCount = 0;
        }
    }

    /**
     * Mutable state carried across segments within the same track.
     */
    @JsonPropertyOrder({
            "lastAcceptedPoint",
            "probationBuffer",
            "lastElevation"
    })
    private static class SegmentFilterState {
        Coordinate lastAcceptedPoint;
        final List<Coordinate> probationBuffer = new ArrayList<>();
        double lastElevation = 0.0;
    }

    @SneakyThrows
    public List<LoadResult> importGpxFile(IndexedFile indexedFile) {
        return importGpxFile(indexedFile, new TimingCollector());
    }

    @SneakyThrows
    List<LoadResult> importGpxFile(IndexedFile indexedFile, TimingCollector timing) {
        TimingCollector activeTiming = timing != null ? timing : new TimingCollector();
        Path path = Paths.get(indexedFile.getFullPath());
        return importGpx(indexedFile, activeTiming,
                () -> readGpxFile(path));
    }

    /**
     * Import from pre-converted GPX XML (e.g. produced by GPSBabel from a FIT/TCX/KML file).
     * The original file on disk is never read — only the provided XML string is parsed.
     */
    @SneakyThrows
    public List<LoadResult> importGpxXml(IndexedFile indexedFile, String gpxXml) {
        return importGpxXml(indexedFile, gpxXml, new TimingCollector());
    }

    @SneakyThrows
    List<LoadResult> importGpxXml(IndexedFile indexedFile, String gpxXml, TimingCollector timing) {
        TimingCollector activeTiming = timing != null ? timing : new TimingCollector();
        return importGpx(indexedFile, activeTiming,
                () -> GPX.Reader.of(GPX.Reader.Mode.LENIENT).fromString(gpxXml));
    }

    private List<LoadResult> importGpx(IndexedFile indexedFile,
                                       TimingCollector activeTiming,
                                       TimingCollector.TimedSupplier<GPX> parser) {
        long startTimeMs = System.currentTimeMillis();
        log.debug("About to read GPX file {}", indexedFile);

        GpsTrack gpsTrack = new GpsTrack();

        try {
            gpsTrack.setIndexedFile(indexedFile);
            gpsTrack.setDuplicateStatus(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET);
            gpsTrack.addLoadMessage("Starting import of GPX file.");

            OutlierFilterResult filterResult = parseAndFilterGpx(
                    indexedFile, gpsTrack, activeTiming, parser);
            if (filterResult == null) {
                log.info("No waypoints for file={}", indexedFile);
                gpsTrack.setLoadStatus(GpsTrack.LOAD_STATUS.EMPTY_FILE);
                gpsTrack.addLoadMessage("GPS did not contain any track data (was empty).");
                LoadResult emptyResult = new LoadResult();
                emptyResult.gpsTrack = gpsTrack;
                emptyResult.processingTime = System.currentTimeMillis() - startTimeMs;
                return List.of(emptyResult);
            }

            // Second-pass cleaner: remove A-B-C isolated spikes and collapse
            // stationary GPS-drift clusters (e.g. restaurant scribble) into two
            // anchor coordinates at the cluster medoid. Runs unconditionally:
            // every downstream variant (RAW_OUTLIER_CLEANED -> SIMPLIFIED_SHAPE
            // -> SIMPLIFIED_FIXED_POINTS) is built from the cleaned stream and
            // therefore inherits the cleanup. RAW is left untouched.
            activeTiming.time("break-stop filter", () -> applyBreakStopTreatment(filterResult, gpsTrack));
            activeTiming.time("time-order filter", () -> applyTimeOrderTreatment(filterResult, gpsTrack));

            // Fix A: extract time bounds from CLEANED coordinates (after outlier removal)
            activeTiming.time("time bounds", () -> extractTimeBoundsFromCleaned(filterResult.cleanedCoordinates, gpsTrack));
            applyFallbackTimeBounds(gpsTrack, indexedFile);

            // Split by temporal gaps (>12h between consecutive cleaned points), except known stop anchors.
            List<List<Coordinate>> segments = activeTiming.time("split segments",
                    () -> splitByTemporalGaps(filterResult.cleanedCoordinates, filterResult.stopRanges));

            if (segments.size() <= 1) {
                // No gap found — single track, no segment metadata
                activeTiming.time("distance stats", () -> computeDistanceStats(gpsTrack, filterResult));
                LoadResult loadResult = new LoadResult();
                loadResult.gpsTrack = gpsTrack;
                loadResult.stopRanges = new ArrayList<>(filterResult.stopRanges);
                activeTiming.time("build geometry", () -> buildGeometries(loadResult, gpsTrack, filterResult));
                gpsTrack.setLoadStatus(GpsTrack.LOAD_STATUS.SUCCESS);
                loadResult.processingTime = System.currentTimeMillis() - startTimeMs;
                return List.of(loadResult);
            }

            // Multiple segments detected — create one LoadResult per segment
            log.info("Temporal gap split: {} segments detected for file={}", segments.size(), indexedFile);
            gpsTrack.addLoadMessage(String.format("Track split into %d segments due to >12h non-stop inactivity gap.", segments.size()));

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
                activeTiming.time("segment time bounds", () -> extractTimeBoundsFromCleaned(segCoords, segTrack));
                applyFallbackTimeBounds(segTrack, indexedFile);

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

                activeTiming.time("segment stats", () -> computeDistanceStats(segTrack, segFilter));

                LoadResult segResult = new LoadResult();
                segResult.gpsTrack = segTrack;
                segResult.stopRanges = stopRangesForSegment(filterResult.stopRanges, segCoords);
                activeTiming.time("segment geometry", () -> buildGeometries(segResult, segTrack, segFilter));
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

    private OutlierFilterResult parseAndFilterGpx(IndexedFile indexedFile,
                                                   GpsTrack gpsTrack,
                                                   TimingCollector activeTiming,
                                                   TimingCollector.TimedSupplier<GPX> parser) throws Exception {
        GPX gpx = activeTiming.time("parse XML", parser);
        activeTiming.time("metadata", () -> parseGpxMetadata(gpx, gpsTrack, indexedFile));

        WayPoint firstWayPoint = activeTiming.time("first point", () -> findFirstTrackPoint(gpx));
        if (firstWayPoint == null) {
            return null;
        }

        gpsTrack.setUtmZone(getUTMCode(
                firstWayPoint.getLongitude().doubleValue(),
                firstWayPoint.getLatitude().doubleValue()));
        return activeTiming.time("outlier filter", () -> filterOutliers(gpx, gpsTrack, indexedFile));
    }

    private static WayPoint findFirstTrackPoint(GPX gpx) {
        for (Track track : gpx.getTracks()) {
            for (TrackSegment segment : track.getSegments()) {
                if (!segment.getPoints().isEmpty()) {
                    return segment.getPoints().getFirst();
                }
            }
        }
        return null;
    }

    private static GPX readGpxFile(Path path) throws IOException {
        try (Reader fileReader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             Reader sanitizedReader = new GpxSanitizingReader(fileReader)) {
            return GPX.Reader.of(GPX.Reader.Mode.LENIENT).read(new StreamSource(sanitizedReader));
        }
    }

    /**
     * Removes the optional UTF-8 BOM and xml-stylesheet processing instruction
     * while the file is read. JPX expects the GPX root immediately after the
     * XML declaration and otherwise rejects this common GPX prolog.
     */
    private static final class GpxSanitizingReader extends Reader {

        private final PushbackReader source;
        private final char[] lookAhead = new char[XML_STYLESHEET_PREFIX.length()];
        private boolean firstCharacter = true;

        private GpxSanitizingReader(Reader source) {
            this.source = new PushbackReader(source, XML_STYLESHEET_PREFIX.length());
        }

        @Override
        public int read() throws IOException {
            while (true) {
                int character = source.read();
                if (firstCharacter) {
                    firstCharacter = false;
                    if (character == '\uFEFF') {
                        continue;
                    }
                }
                if (character != '<') {
                    return character;
                }

                int count = readLookAhead();
                if (count == lookAhead.length && matchesStylesheetPrefix()) {
                    skipProcessingInstruction();
                    continue;
                }
                source.unread(lookAhead, 0, count);
                return character;
            }
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            int count = 0;
            while (count < length) {
                int character = read();
                if (character < 0) {
                    break;
                }
                buffer[offset + count] = (char) character;
                count++;
            }
            return count == 0 ? -1 : count;
        }

        private int readLookAhead() throws IOException {
            int count = 0;
            while (count < lookAhead.length) {
                int character = source.read();
                if (character < 0) {
                    break;
                }
                lookAhead[count++] = (char) character;
            }
            return count;
        }

        private boolean matchesStylesheetPrefix() {
            for (int i = 0; i < lookAhead.length; i++) {
                if (lookAhead[i] != XML_STYLESHEET_PREFIX.charAt(i)) {
                    return false;
                }
            }
            return true;
        }

        private void skipProcessingInstruction() throws IOException {
            int previous = -1;
            int character;
            while ((character = source.read()) >= 0) {
                if (previous == '?' && character == '>') {
                    return;
                }
                previous = character;
            }
        }

        @Override
        public void close() throws IOException {
            source.close();
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

    private void applyFallbackTimeBounds(GpsTrack gpsTrack, IndexedFile indexedFile) {
        if (gpsTrack.getStartDate() != null) {
            if (gpsTrack.getEndDate() == null) {
                gpsTrack.setEndDate(gpsTrack.getStartDate());
            }
            return;
        }

        Date fallbackDate = firstNonNull(
                gpsTrack.getMetaTime(),
                indexedFile == null ? null : indexedFile.getLastModifiedDate(),
                indexedFile == null ? null : indexedFile.getCreateDate(),
                indexedFile == null ? null : indexedFile.getIndexAddedDate());
        if (fallbackDate == null) return;

        gpsTrack.setStartDate(fallbackDate);
        gpsTrack.setEndDate(fallbackDate);
        gpsTrack.addLoadMessage("No per-point timestamps found; using source metadata/file date as track date.");
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) return value;
        }
        return null;
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
        return splitByTemporalGaps(cleanedCoords, List.of());
    }

    /**
     * Split cleaned coordinates into sub-lists wherever consecutive points have a forward temporal gap > threshold,
     * except synthetic stop anchor pairs backed by detected stop ranges.
     * <p>
     * GPX/TCX conversion tools can emit physically ordered points with stale timestamps mixed in.
     * Those backward jumps are handled by the time-order treatment before this split; splitting
     * them here would create many one-point pseudo-tracks from corrupt interleaved samples.
     */
    static List<List<Coordinate>> splitByTemporalGaps(List<Coordinate> cleanedCoords,
                                                      List<TrackStopDetector.StopRange> stopRanges) {
        if (cleanedCoords.size() < 2) {
            return List.of(cleanedCoords);
        }
        List<List<Coordinate>> segments = new ArrayList<>();
        List<Coordinate> current = new ArrayList<>();
        current.add(cleanedCoords.get(0));

        for (int i = 1; i < cleanedCoords.size(); i++) {
            Coordinate previous = cleanedCoords.get(i - 1);
            Coordinate currentPoint = cleanedCoords.get(i);
            double prevT = previous.getM();
            double currT = currentPoint.getM();
            if (!Double.isNaN(prevT) && !Double.isNaN(currT)
                && currT - prevT > SEGMENT_GAP_THRESHOLD_S
                && !isKnownStopAnchorPair(previous, currentPoint, stopRanges)) {
                segments.add(current);
                current = new ArrayList<>();
            }
            current.add(currentPoint);
        }
        segments.add(current);
        return segments;
    }

    private static boolean isKnownStopAnchorPair(Coordinate previous,
                                                 Coordinate currentPoint,
                                                 List<TrackStopDetector.StopRange> stopRanges) {
        if (stopRanges == null || stopRanges.isEmpty()
            || !TrackStopDetector.isStopAnchorPair(previous, currentPoint)) {
            return false;
        }
        double prevT = previous.getM();
        double currT = currentPoint.getM();
        for (TrackStopDetector.StopRange stop : stopRanges) {
            if (Math.abs(prevT - stop.startTimeS()) <= STOP_ANCHOR_TIME_TOLERANCE_S
                && Math.abs(currT - stop.endTimeS()) <= STOP_ANCHOR_TIME_TOLERANCE_S) {
                return true;
            }
        }
        return false;
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

        for (Track track : gpx.getTracks()) {
            // State is carried across segments within the same track.
            // Segment breaks (trkseg) represent brief GPS signal loss, not separate journeys.
            SegmentFilterState state = new SegmentFilterState();
            for (TrackSegment segment : track.getSegments()) {
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

    /**
     * Replaces {@code result.cleanedCoordinates} with the output of
     * {@link BreakStopTreatment#apply(List)} and rebuilds the matching
     * inter-point distance list so {@link #computeDistanceStats} reflects
     * the post-treatment series.
     */
    private void applyBreakStopTreatment(OutlierFilterResult result, GpsTrack gpsTrack) {
        if (result.cleanedCoordinates.size() < 3) {
            return;
        }
        BreakStopTreatment.Result treated = BreakStopTreatment.apply(result.cleanedCoordinates);
        if (treated.spikesRemoved() == 0 && treated.stops().isEmpty()) {
            return;
        }
        result.cleanedCoordinates.clear();
        result.cleanedCoordinates.addAll(treated.cleanedCoordinates());
        result.stopRanges.clear();
        result.stopRanges.addAll(treated.stops());
        rebuildDistancesBetweenCleanedPoints(result);
        gpsTrack.addLoadMessage(String.format(
                "Outlier corrector %s: removed %d isolated spike point(s); collapsed %d stationary-drift point(s) into %d stop anchor pair(s).",
                BreakStopTreatment.CORRECTOR_NAME,
                treated.spikesRemoved(),
                treated.collapsedPoints(),
                treated.stops().size()));
        log.info("Break-stop treatment applied: spikes={} stops={} collapsedPoints={} file={}",
                treated.spikesRemoved(), treated.stops().size(), treated.collapsedPoints(),
                gpsTrack.getIndexedFile() != null ? gpsTrack.getIndexedFile().getName() : "?");
    }

    private void applyTimeOrderTreatment(OutlierFilterResult result, GpsTrack gpsTrack) {
        if (result.cleanedCoordinates.size() < 2) {
            return;
        }
        List<Coordinate> filtered = withoutStaleBackwardTimeJumpPoints(result.cleanedCoordinates);
        int removed = result.cleanedCoordinates.size() - filtered.size();
        if (removed <= 0) {
            return;
        }

        result.cleanedCoordinates.clear();
        result.cleanedCoordinates.addAll(filtered);
        result.outlierCount += removed;
        rebuildDistancesBetweenCleanedPoints(result);
        gpsTrack.addLoadMessage(String.format(
                "Outlier corrector GPXReader time-order filter: removed %d stale point(s) with timestamp more than %.0f hours earlier than the previous accepted point.",
                removed,
                SEGMENT_GAP_THRESHOLD_S / 3600.0));
        log.info("Time-order treatment removed {} stale backward timestamp point(s) file={}",
                removed,
                gpsTrack.getIndexedFile() != null ? gpsTrack.getIndexedFile().getName() : "?");
    }

    static List<Coordinate> withoutStaleBackwardTimeJumpPoints(List<Coordinate> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            return coordinates == null ? List.of() : new ArrayList<>(coordinates);
        }

        List<Coordinate> filtered = new ArrayList<>(coordinates.size());
        double lastKeptTime = Double.NaN;
        for (Coordinate coordinate : coordinates) {
            double currentTime = coordinate.getM();
            if (!Double.isNaN(lastKeptTime)
                && !Double.isNaN(currentTime)
                && lastKeptTime - currentTime > SEGMENT_GAP_THRESHOLD_S) {
                continue;
            }

            filtered.add(coordinate);
            if (!Double.isNaN(currentTime)) {
                lastKeptTime = currentTime;
            }
        }
        return filtered;
    }

    private void rebuildDistancesBetweenCleanedPoints(OutlierFilterResult result) {
        result.distancesBetweenPoints.clear();
        for (int i = 1; i < result.cleanedCoordinates.size(); i++) {
            result.distancesBetweenPoints.add(getDistanceBetweenTwoWGS84(
                    result.cleanedCoordinates.get(i),
                    result.cleanedCoordinates.get(i - 1)));
        }
    }

    private List<TrackStopDetector.StopRange> stopRangesForSegment(List<TrackStopDetector.StopRange> stopRanges,
                                                                   List<Coordinate> segmentCoordinates) {
        if (stopRanges == null || stopRanges.isEmpty() || segmentCoordinates == null || segmentCoordinates.isEmpty()) {
            return List.of();
        }
        double segmentStart = segmentCoordinates.get(0).getM();
        double segmentEnd = segmentCoordinates.get(segmentCoordinates.size() - 1).getM();
        if (Double.isNaN(segmentStart) || Double.isNaN(segmentEnd)) {
            return List.of();
        }
        return stopRanges.stream()
                .filter(stop -> stop.startTimeS() >= segmentStart && stop.endTimeS() <= segmentEnd)
                .toList();
    }

    private void processSegment(TrackSegment segment, SegmentFilterState state, GpsTrack gpsTrack,
                                IndexedFile indexedFile, OutlierFilterResult result) {

        for (WayPoint wayPoint : segment.getPoints()) {
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
                gpsTrack.addLoadMessage("Outlier corrector " + DISTANCE_PROBATION_CORRECTOR_NAME
                                        + ": detected candidate at " + cleanPointStr + " — entering probation.");
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
                                       "\n  - Outlier Corrector: %s" +
                                       "\n  - Outliers Found: %b" +
                                       "\n  - Outliers Cleared By Corrector: %d point(s)" +
                                       "\n  - Distances (m):" +
                                       "\n      • Max:    %.1f" +
                                       "\n      • Median: %.1f" +
                                       "\n      • Avg:    %.1f",
                DISTANCE_PROBATION_CORRECTOR_NAME,
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
            gpsTrack.setNumberOfTrackPoints(cleaned.getNumPoints());
            loadResult.trackCleaned = cleaned;
        } else {
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

    public static double getDistanceOfWGS84(LineString lineString) {
        double length = 0.0;

        for (int i = 0; i < lineString.getNumPoints() - 1; i++) {
            length += getDistanceBetweenTwoWGS84(lineString.getCoordinateN(i), lineString.getCoordinateN(i + 1));
        }

        return length;
    }

    /**
     * Calculates the WGS84 ellipsoidal distance with Vincenty's inverse formula.
     * The allocation-free common path is checked against the former GeoTools 35.1
     * implementation by {@code GPXReaderGeoToolsCompatibilityTest}. GeographicLib
     * handles the rare non-converging cases.
     */
    public static double getDistanceBetweenTwoWGS84(Coordinate c1, Coordinate c2) {
        if (c1.x == c2.x && c1.y == c2.y) {
            return 0.0;
        }
        validateWgs84Coordinate(c1);
        validateWgs84Coordinate(c2);

        double phi1 = Math.toRadians(c1.y);
        double phi2 = Math.toRadians(c2.y);
        double reducedLatitude1 = Math.atan((1.0 - WGS84_FLATTENING) * Math.tan(phi1));
        double reducedLatitude2 = Math.atan((1.0 - WGS84_FLATTENING) * Math.tan(phi2));
        double sinReducedLatitude1 = Math.sin(reducedLatitude1);
        double cosReducedLatitude1 = Math.cos(reducedLatitude1);
        double sinReducedLatitude2 = Math.sin(reducedLatitude2);
        double cosReducedLatitude2 = Math.cos(reducedLatitude2);

        double longitudeDifference = Math.toRadians(normalizedLongitudeDifference(c1.x, c2.x));
        double lambda = longitudeDifference;
        double sinSigma = 0.0;
        double cosSigma = 0.0;
        double sigma = 0.0;
        double sinAlpha = 0.0;
        double cosSquaredAlpha = 0.0;
        double cosTwoSigmaMidpoint = 0.0;
        boolean converged = false;

        for (int iteration = 0; iteration < VINCENTY_MAX_ITERATIONS; iteration++) {
            double sinLambda = Math.sin(lambda);
            double cosLambda = Math.cos(lambda);
            double firstTerm = cosReducedLatitude2 * sinLambda;
            double secondTerm = cosReducedLatitude1 * sinReducedLatitude2
                                - sinReducedLatitude1 * cosReducedLatitude2 * cosLambda;
            sinSigma = Math.hypot(firstTerm, secondTerm);
            if (sinSigma == 0.0) {
                return 0.0;
            }

            cosSigma = sinReducedLatitude1 * sinReducedLatitude2
                       + cosReducedLatitude1 * cosReducedLatitude2 * cosLambda;
            sigma = Math.atan2(sinSigma, cosSigma);
            sinAlpha = cosReducedLatitude1 * cosReducedLatitude2 * sinLambda / sinSigma;
            cosSquaredAlpha = 1.0 - sinAlpha * sinAlpha;
            cosTwoSigmaMidpoint = cosSquaredAlpha == 0.0
                    ? 0.0
                    : cosSigma - 2.0 * sinReducedLatitude1 * sinReducedLatitude2 / cosSquaredAlpha;

            double correction = WGS84_FLATTENING / 16.0 * cosSquaredAlpha
                                * (4.0 + WGS84_FLATTENING * (4.0 - 3.0 * cosSquaredAlpha));
            double nextLambda = longitudeDifference
                                + (1.0 - correction) * WGS84_FLATTENING * sinAlpha
                                  * (sigma + correction * sinSigma
                                     * (cosTwoSigmaMidpoint + correction * cosSigma
                                        * (-1.0 + 2.0 * cosTwoSigmaMidpoint * cosTwoSigmaMidpoint)));
            if (Math.abs(nextLambda - lambda) <= VINCENTY_CONVERGENCE_RADIANS) {
                converged = true;
                break;
            }
            lambda = nextLambda;
        }

        if (!converged) {
            return GeographicLibFallback.distanceMeters(c1, c2);
        }

        double squaredAxisRatio = cosSquaredAlpha
                                  * (WGS84_SEMI_MAJOR_AXIS_M * WGS84_SEMI_MAJOR_AXIS_M
                                     - WGS84_SEMI_MINOR_AXIS_M * WGS84_SEMI_MINOR_AXIS_M)
                                  / (WGS84_SEMI_MINOR_AXIS_M * WGS84_SEMI_MINOR_AXIS_M);
        double coefficientA = 1.0 + squaredAxisRatio / 16_384.0
                              * (4_096.0 + squaredAxisRatio
                                 * (-768.0 + squaredAxisRatio * (320.0 - 175.0 * squaredAxisRatio)));
        double coefficientB = squaredAxisRatio / 1_024.0
                              * (256.0 + squaredAxisRatio
                                 * (-128.0 + squaredAxisRatio * (74.0 - 47.0 * squaredAxisRatio)));
        double deltaSigma = coefficientB * sinSigma
                            * (cosTwoSigmaMidpoint + coefficientB / 4.0
                               * (cosSigma * (-1.0 + 2.0 * cosTwoSigmaMidpoint * cosTwoSigmaMidpoint)
                                  - coefficientB / 6.0 * cosTwoSigmaMidpoint
                                    * (-3.0 + 4.0 * sinSigma * sinSigma)
                                    * (-3.0 + 4.0 * cosTwoSigmaMidpoint * cosTwoSigmaMidpoint)));
        return WGS84_SEMI_MINOR_AXIS_M * coefficientA * (sigma - deltaSigma);
    }

    private static void validateWgs84Coordinate(Coordinate coordinate) {
        if (!Double.isFinite(coordinate.x)) {
            throw new IllegalArgumentException("Longitude must be finite");
        }
        if (!Double.isFinite(coordinate.y) || coordinate.y < -90.0 || coordinate.y > 90.0) {
            throw new IllegalArgumentException("Latitude must be finite and between -90 and 90 degrees");
        }
    }

    private static double normalizedLongitudeDifference(double longitude1, double longitude2) {
        double normalizedLongitude1 = Math.IEEEremainder(longitude1, 360.0);
        double normalizedLongitude2 = Math.IEEEremainder(longitude2, 360.0);
        return Math.IEEEremainder(normalizedLongitude2 - normalizedLongitude1, 360.0);
    }

    /** Keeps the exact ellipsoidal solution for the rare cases where Vincenty cannot converge. */
    private static final class GeographicLibFallback {

        private static double distanceMeters(Coordinate c1, Coordinate c2) {
            return Geodesic.WGS84.Inverse(
                    c1.y, c1.x, c2.y, c2.x, GeodesicMask.DISTANCE).s12;
        }
    }

    public static Double getEuclideanDistanceBetweenMercatorPoints(Coordinate c1, Coordinate c2) {
        if (c1 != null && c2 != null) {
            return Math.sqrt(Math.pow(c1.getX() - c2.getX(), 2) + Math.pow(c1.getY() - c2.getY(), 2));
        }
        return null;
    }


    /** Standard EPSG:3857 projection, checked against GeoTools across its supported area. */
    public Point convertLongLatWgs84ToPlanarWebMercator(double longitude, double latitude) {
        double boundedLatitude = Math.max(
                -WEB_MERCATOR_MAX_LATITUDE,
                Math.min(WEB_MERCATOR_MAX_LATITUDE, latitude));
        double x = WGS84_SEMI_MAJOR_AXIS_M * Math.toRadians(longitude);
        double latitudeRadians = Math.toRadians(boundedLatitude);
        double y = WGS84_SEMI_MAJOR_AXIS_M
                   * Math.log(Math.tan(Math.PI / 4.0 + latitudeRadians / 2.0));
        return WEB_MERCATOR_GEOMETRY_FACTORY.createPoint(new Coordinate(x, y));
    }

}
