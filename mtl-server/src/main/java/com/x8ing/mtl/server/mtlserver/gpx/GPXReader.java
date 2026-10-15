package com.x8ing.mtl.server.mtlserver.gpx;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.gpsquality.diagnostics.GpxDocument;
import com.x8ing.mtl.server.gpsquality.diagnostics.RecordingTimingAnalysis;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
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
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import static java.util.stream.Collectors.joining;

@SuppressWarnings("StringConcatenationInsideStringBufferAppend")
@Service
@Slf4j
public class GPXReader {

    private static final double MILLIS_PER_SECOND = 1000.0;
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
    private static final int PROBATION_TRUST_POINT_COUNT = 5;
    private static final double SAME_TIMESTAMP_GEOMETRY_METRES = RecordingContinuity.MAX_SAME_TIME_GEOMETRY_METRES;
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
            "recordingBreakBeforePointIndexes",
            "rawRecordingBreakBeforePointIndexes",
            "observationEvidence",
            "sourceRecordingEvidence",
            "processingTime"
    })
    public static class LoadResult {
        public GpsTrack gpsTrack;
        public LineString trackRAW;
        public LineString trackCleaned;
        public List<TrackStopDetector.StopRange> stopRanges = new ArrayList<>();
        public List<Integer> recordingBreakBeforePointIndexes = new ArrayList<>();
        public List<Integer> rawRecordingBreakBeforePointIndexes = new ArrayList<>();
        public List<GpsObservationEvidence> observationEvidence = new ArrayList<>();
        @com.fasterxml.jackson.annotation.JsonIgnore
        public Map<Integer, String> cleaningRules = new HashMap<>();
        @com.fasterxml.jackson.annotation.JsonIgnore
        public List<GpsProcessingIssue> processingIssues = List.of();
        public SourceRecordingEvidence sourceRecordingEvidence;
        public long processingTime;
    }

    /**
     * Import-time provenance remains available when detailed DOM diagnosis exceeds its limits.
     */
    public record SourceRecordingEvidence(int sectionIndex, String name, int firstPointIndex, int pointCount,
                                          int timedPointCount, int estimatedPointCount, Instant firstTime, Instant lastTime,
                                          Integer firstExcursionPointIndex, RecordingTimingAnalysis timing) {
    }

    @JsonPropertyOrder({
            "cleanedCoordinates",
            "rawCoordinates",
            "distancesBetweenPoints",
            "stopRanges",
            "recordingSections",
            "recordingSection",
            "outlierCount"
    })
    private static class OutlierFilterResult {
        final List<Coordinate> cleanedCoordinates;
        final List<Coordinate> rawCoordinates;
        final List<Double> distancesBetweenPoints;
        final List<TrackStopDetector.StopRange> stopRanges;
        final Map<Coordinate, Integer> recordingSections = new IdentityHashMap<>();
        // Original GPX section identity is independent of gaps detected inside that section.
        final Map<Coordinate, Integer> sourceSections = new IdentityHashMap<>();
        final Map<Coordinate, GpsObservationEvidence> evidence = new IdentityHashMap<>();
        final Map<Integer, String> cleaningRules = new HashMap<>();
        int recordingSection;
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
            "probationEntryDt"
    })
    private static class SegmentFilterState {
        Coordinate lastAcceptedPoint;
        final List<Coordinate> probationBuffer = new ArrayList<>();
        double probationEntryDt = Double.NaN;
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
                () -> readIdentifiedGpx(new GpxSanitizingReader(new StringReader(gpxXml))));
    }

    private List<LoadResult> importGpx(IndexedFile indexedFile,
                                       TimingCollector activeTiming,
                                       TimingCollector.TimedSupplier<GPX> parser) {
        return importGpx(indexedFile, activeTiming, parser, true);
    }

    private List<LoadResult> importGpx(IndexedFile indexedFile, TimingCollector activeTiming,
                                       TimingCollector.TimedSupplier<GPX> parser, boolean inspectProvenance) {
        long startTimeMs = System.currentTimeMillis();
        log.debug("About to read GPX file {}", indexedFile);

        GpsTrack gpsTrack = new GpsTrack();

        try {
            gpsTrack.setIndexedFile(indexedFile);
            gpsTrack.setDuplicateStatus(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET);
            gpsTrack.addLoadMessage("Starting import of GPX file.");

            GPX parsed = activeTiming.time("parse XML", parser);
            if (inspectProvenance && requiresSectionImport(parsed))
                return importProvenanceSections(indexedFile, activeTiming, parsed);
            OutlierFilterResult filterResult = parseAndFilterGpx(indexedFile, gpsTrack, activeTiming, parsed);
            if (filterResult == null) {
                log.info("No waypoints for file={}", indexedFile);
                gpsTrack.setLoadStatus(GpsTrack.LOAD_STATUS.EMPTY_FILE);
                gpsTrack.addLoadMessage("GPS did not contain any track data (was empty).");
                LoadResult emptyResult = new LoadResult();
                emptyResult.gpsTrack = gpsTrack;
                emptyResult.processingTime = System.currentTimeMillis() - startTimeMs;
                return List.of(emptyResult);
            }

            // Remove supported isolated position/elevation spikes before denoising.
            // Downstream geometry and metrics use this stream; RAW retains the observations.
            activeTiming.time("break-stop filter", () -> applyBreakStopTreatment(filterResult, gpsTrack));
            activeTiming.time("position continuity", () -> disconnectRetainedRelocations(filterResult, gpsTrack));


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
                loadResult.recordingBreakBeforePointIndexes = recordingBreaks(filterResult.cleanedCoordinates, filterResult.recordingSections);
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
                segFilter.recordingSections.putAll(filterResult.recordingSections);
                segFilter.sourceSections.putAll(filterResult.sourceSections);
                segFilter.evidence.putAll(filterResult.evidence);
                segFilter.cleaningRules.putAll(filterResult.cleaningRules);

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
                segResult.recordingBreakBeforePointIndexes = recordingBreaks(segCoords, filterResult.recordingSections);
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
                                                  GPX gpx) throws Exception {
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

    private static boolean estimatedTiming(WayPoint point) {
        return point.getExtensions().map(extensions ->
                extensions.getElementsByTagNameNS(GpxDocument.REPAIR_NAMESPACE, "time").getLength() > 0).orElse(false);
    }

    private static boolean requiresSectionImport(GPX source) {
        return !source.getRoutes().isEmpty() || source.getTracks().stream().flatMap(track -> track.getSegments().stream())
                .anyMatch(section -> {
                    var clock = analyzeTiming(section);
                    return section.getPoints().stream().anyMatch(GPXReader::estimatedTiming)
                           || clock.needsContainment() || !clock.isolatedForwardSpikeIndexes().isEmpty();
                });
    }

    private static RecordingTimingAnalysis analyzeTiming(TrackSegment section) {
        // Keep missing observations in the sequence; skipping them would invent adjacent time pairs.
        return RecordingTimingAnalysis.analyze(() -> section.getPoints().stream()
                .map(point -> point.getTime().orElse(null)).iterator());
    }

    private List<LoadResult> importProvenanceSections(IndexedFile file, TimingCollector timing, GPX source) {
        List<Track> recordings = new ArrayList<>();
        // GPX schema order places routes before tracks. Each source section stays independently reviewable.
        for (Route route : source.getRoutes())
            recordings.add(Track.builder().name(route.getName().orElse(null))
                    .desc(route.getDescription().orElse(null)).segments(List.of(TrackSegment.builder().points(route.getPoints()).build())).build());
        for (Track track : source.getTracks())
            for (TrackSegment section : track.getSegments())
                recordings.add(track.toBuilder().segments(List.of(section)).build());
        List<LoadResult> results = new ArrayList<>();
        int firstIndex = 0;
        for (int sectionIndex = 0; sectionIndex < recordings.size(); sectionIndex++) {
            Track recording = recordings.get(sectionIndex);
            TrackSegment original = recording.getSegments().getFirst();
            int estimated = (int) original.getPoints().stream().filter(GPXReader::estimatedTiming).count();
            var clock = analyzeTiming(original);
            boolean unreliable = estimated > 0 || clock.needsContainment();
            int originalSection = original.getPoints().isEmpty() ? sectionIndex : sourceIdentity(original.getPoints().getFirst(), "section", sectionIndex);
            int originalFirst = original.getPoints().isEmpty() ? firstIndex : sourceIdentity(original.getPoints().getFirst(), "point", firstIndex);
            var evidence = new SourceRecordingEvidence(originalSection, recording.getName().orElse("Recording " + (sectionIndex + 1)),
                    originalFirst, original.getPoints().size(), clock.timedPointCount(), estimated, clock.earliest(), clock.latest(),
                    clock.firstExcursionIndex() < 0 ? null : sourceIdentity(original.getPoints().get(clock.firstExcursionIndex()),
                            "point", originalFirst + clock.firstExcursionIndex()), clock);
            if (unreliable) log.info("Source clock containment: sourceId={} section={} points={} backwards={} excursions={} estimated={}",
                    file.getId(), sectionIndex, clock.pointCount(), clock.backwardCount(), clock.excursionCount(), estimated);
            Set<Integer> excludedTimes = new HashSet<>(clock.isolatedForwardSpikeIndexes());
            TrackSegment derived = original.toBuilder().points(java.util.stream.IntStream.range(0, original.getPoints().size())
                    .mapToObj(i -> unreliable || excludedTimes.contains(i)
                            ? original.getPoints().get(i).toBuilder().time((Instant) null).build() : original.getPoints().get(i)).toList()).build();
            Map<Integer, Instant> excludedSourceTimes = new HashMap<>();
            for (int i : excludedTimes) excludedSourceTimes.put(sourceIdentity(original.getPoints().get(i), "point", originalFirst + i),
                    original.getPoints().get(i).getTime().orElseThrow());
            GPX section = source.toBuilder().routes(List.of()).tracks(List.of(recording.toBuilder().segments(List.of(derived)).build())).build();
            var loaded = importGpx(file, timing, () -> section, false);
            for (LoadResult result : loaded) {
                result.gpsTrack.setSourceRecordingSection(originalSection);
                result.sourceRecordingEvidence = evidence;
                result.gpsTrack.setTimingEstimated(estimated > 0);
                if (unreliable) {
                    List<Coordinate> coordinates = original.getPoints().stream().map(point -> (Coordinate) new CoordinateXYZM(
                            point.getLongitude().doubleValue(), point.getLatitude().doubleValue(),
                            point.getElevation().map(Length::doubleValue).orElse(Double.NaN),
                            point.getTime().map(time -> time.getEpochSecond() + time.getNano() / 1_000_000_000.0).orElse(Double.NaN))).toList();
                    Date first = original.getPoints().stream().flatMap(point -> point.getTime().stream()).findFirst().map(Date::from).orElse(null);
                    SourceTimingContainment.preserveOriginal(result, coordinates, first, originalSection);
                } else if (!excludedSourceTimes.isEmpty()) {
                    SourceTimingContainment.preserveExcludedTimes(result, excludedSourceTimes);
                }
                results.add(result);
            }
            firstIndex += original.getPoints().size();
        }
        if (results.size() > 1) for (int i = 0; i < results.size(); i++) results.get(i).gpsTrack.setSourceSegmentIndex(i + 1);
        log.info("Imported source recording sections: sourceId={} recordingSections={} derivedTracks={}", file.getId(), recordings.size(), results.size());
        return results;
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

    private static GPX readGpxFile(Path path) throws Exception {
        try (Reader fileReader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             Reader sanitizedReader = new GpxSanitizingReader(fileReader)) {
            return readIdentifiedGpx(sanitizedReader);
        }
    }

    private static GPX readIdentifiedGpx(Reader input) throws Exception {
        try (Reader identified = new GpxImportIdentityReader(input)) {
            return GPX.Reader.of(GPX.Reader.Mode.LENIENT).read(new StreamSource(identified));
        }
    }

    private static int sourceIdentity(WayPoint point, String attribute, int fallback) {
        return point.getExtensions().map(dom -> {
            var identities = dom.getElementsByTagNameNS(GpxImportIdentityReader.NAMESPACE, "observation");
            if (identities.getLength() == 0) return fallback;
            // The streaming reader appends the authoritative identity after any source extension.
            return Integer.parseInt(((org.w3c.dom.Element) identities.item(identities.getLength() - 1)).getAttribute(attribute));
        }).orElse(fallback);
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
            gpsTrack.setStartDate(Date.from(Instant.ofEpochMilli(Math.round(minT * MILLIS_PER_SECOND))));
        }
        if (maxT != -Double.MAX_VALUE) {
            gpsTrack.setEndDate(Date.from(Instant.ofEpochMilli(Math.round(maxT * MILLIS_PER_SECOND))));
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
     * The section clock check contains unreliable timing before this split, retaining original
     * timestamps in RAW. Backward clock jumps must not become separate activities.
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

    private OutlierFilterResult filterOutliers(GPX gpx, GpsTrack gpsTrack, IndexedFile indexedFile) {
        OutlierFilterResult result = new OutlierFilterResult();
        gpsTrack.setMaxDistanceBetweenPoints(0d);
        gpsTrack.setDidFilterOutlierByDistance(false);

        int sourceSectionIndex = 0;
        for (Track track : gpx.getTracks()) {
            for (TrackSegment segment : track.getSegments()) {
                SegmentFilterState state = new SegmentFilterState();
                result.recordingSection++;
                processSegment(segment, state, gpsTrack, indexedFile, result, sourceSectionIndex++);
                if (!state.probationBuffer.isEmpty()) {
                    result.outlierCount += state.probationBuffer.size();
                    gpsTrack.setDidFilterOutlierByDistance(true);
                }
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
        Set<Integer> accepted = result.cleanedCoordinates.stream().map(result.evidence::get)
                .map(GpsObservationEvidence::sourcePointIndex).collect(java.util.stream.Collectors.toSet());
        for (var source : result.evidence.values())
            if (!accepted.contains(source.sourcePointIndex()))
                result.cleaningRules.putIfAbsent(source.sourcePointIndex(), "distance-probation");
        List<Coordinate> cleaned = new ArrayList<>();
        List<TrackStopDetector.StopRange> stops = new ArrayList<>();
        int spikesRemoved = 0;
        int collapsedPoints = 0;
        for (int from = 0; from < result.cleanedCoordinates.size(); ) {
            int section = result.recordingSections.get(result.cleanedCoordinates.get(from));
            int to = from + 1;
            while (to < result.cleanedCoordinates.size()
                   && result.recordingSections.get(result.cleanedCoordinates.get(to)) == section) to++;
            List<Coordinate> verticalCleaned = ElevationObservationFilter.clean(
                    result.cleanedCoordinates.subList(from, to));
            for (int i = from; i < to; i++) {
                Coordinate original = result.cleanedCoordinates.get(i);
                Coordinate vertical = verticalCleaned.get(i - from);
                result.evidence.put(vertical, result.evidence.get(original));
                result.sourceSections.put(vertical, result.sourceSections.get(original));
            }
            var treated = BreakStopTreatment.apply(verticalCleaned);
            Set<Coordinate> retained = Collections.newSetFromMap(new IdentityHashMap<>());
            retained.addAll(treated.cleanedCoordinates());
            for (Coordinate coordinate : verticalCleaned)
                if (!retained.contains(coordinate))
                    result.cleaningRules.put(result.evidence.get(coordinate).sourcePointIndex(), "isolated-position-return");
            for (Coordinate coordinate : treated.cleanedCoordinates()) {
                result.recordingSections.put(coordinate, section);
                cleaned.add(coordinate);
            }
            for (var stop : treated.stops()) {
                stops.add(new TrackStopDetector.StopRange(stop.startIndex() + from, stop.endIndex() + from,
                        stop.startTimeS(), stop.endTimeS(), stop.centerLng(), stop.centerLat(), stop.centerElevation(),
                        stop.category(), stop.radiusR80M(), stop.radiusR90M(), stop.inlierRatio(), stop.rawPoints(),
                        stop.deletedPoints(), stop.evidence()));
            }
            spikesRemoved += treated.spikesRemoved();
            collapsedPoints += treated.collapsedPoints();
            from = to;
        }
        result.cleanedCoordinates.clear();
        result.cleanedCoordinates.addAll(cleaned);
        result.stopRanges.clear();
        result.stopRanges.addAll(stops);
        rebuildDistancesBetweenCleanedPoints(result);
        gpsTrack.addLoadMessage(String.format(
                "Outlier corrector %s: removed %d isolated spike point(s). Motion is analyzed after denoising.",
                BreakStopTreatment.CORRECTOR_NAME, spikesRemoved));
        log.info("Break-stop treatment completed: spikes={} stops={} collapsedPoints={} recordingSections={} file={}",
                spikesRemoved, stops.size(), collapsedPoints, result.recordingSection,
                gpsTrack.getIndexedFile() != null ? gpsTrack.getIndexedFile().getName() : "?");
    }

    private void disconnectRetainedRelocations(OutlierFilterResult result, GpsTrack gpsTrack) {
        NavigableMap<Integer, Integer> sectionStarts = new TreeMap<>();
        int breakCount = 0;
        for (int from = 0; from < result.cleanedCoordinates.size(); ) {
            int section = result.recordingSections.get(result.cleanedCoordinates.get(from));
            int to = from + 1;
            while (to < result.cleanedCoordinates.size() && result.recordingSections.get(result.cleanedCoordinates.get(to)) == section) to++;
            sectionStarts.put(result.evidence.get(result.cleanedCoordinates.get(from)).sourcePointIndex(), section);
            Set<Integer> breaks = new HashSet<>(RetainedPositionContinuity.breaks(result.cleanedCoordinates.subList(from, to)));
            for (int i = from; i < to; i++) {
                Coordinate point = result.cleanedCoordinates.get(i);
                int sourceIndex = result.evidence.get(point).sourcePointIndex();
                if (breaks.contains(i - from)) {
                    section = ++result.recordingSection;
                    sectionStarts.put(sourceIndex, section);
                    result.cleaningRules.put(sourceIndex, RetainedPositionContinuity.RULE);
                    breakCount++;
                }
            }
            from = to;
        }
        Integer previousOriginalSection = null;
        int section = 0;
        for (Coordinate point : result.rawCoordinates) {
            Integer original = result.recordingSections.get(point);
            if (!Objects.equals(previousOriginalSection, original)) section = original;
            section = sectionStarts.getOrDefault(result.evidence.get(point).sourcePointIndex(), section);
            result.recordingSections.put(point, section);
            previousOriginalSection = original;
        }
        for (Coordinate point : result.cleanedCoordinates)
            result.recordingSections.put(point, sectionStarts.floorEntry(result.evidence.get(point).sourcePointIndex()).getValue());
        rebuildDistancesBetweenCleanedPoints(result);
        if (breakCount > 0) gpsTrack.addLoadMessage("Retained " + breakCount
                                                    + " abrupt GPS relocation(s) as disconnected spans; connecting distance and speed are unknown.");
        log.info("Position continuity completed: disconnectedTransitions={} retainedPoints={} file={}",
                breakCount, result.cleanedCoordinates.size(), gpsTrack.getIndexedFile().getName());
    }

    private static List<Integer> recordingBreaks(List<Coordinate> coordinates, Map<Coordinate, Integer> sections) {
        List<Integer> breaks = new ArrayList<>();
        for (int i = 1; i < coordinates.size(); i++) {
            if (!Objects.equals(sections.get(coordinates.get(i - 1)), sections.get(coordinates.get(i)))
                || RecordingContinuity.intervalStatus(coordinates.get(i - 1), coordinates.get(i))
                   == GpsTrackDataPoint.IntervalStatus.UNKNOWN) breaks.add(i);
        }
        var timedBreaks = RecordingContinuity.timeBreakIndexes(coordinates, breaks);
        var allBreaks = new TreeSet<>(breaks);
        allBreaks.addAll(timedBreaks);
        return new ArrayList<>(allBreaks);
    }

    private void rebuildDistancesBetweenCleanedPoints(OutlierFilterResult result) {
        result.distancesBetweenPoints.clear();
        for (int i = 1; i < result.cleanedCoordinates.size(); i++) {
            if (Objects.equals(result.recordingSections.get(result.cleanedCoordinates.get(i)),
                    result.recordingSections.get(result.cleanedCoordinates.get(i - 1)))) {
                result.distancesBetweenPoints.add(getDistanceBetweenTwoWGS84(
                        result.cleanedCoordinates.get(i), result.cleanedCoordinates.get(i - 1)));
            }
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

    private static final String GARMIN_GPX_EXTENSIONS_NAMESPACE = "http://www.garmin.com/xmlschemas/GpxExtensions/v3";

    private static Double sourceSpeed(WayPoint point) {
        if (point.getSpeed().isPresent()) return point.getSpeed().get().doubleValue();
        if (point.getExtensions().isEmpty()) return null;
        var speeds = point.getExtensions().get().getElementsByTagNameNS(GARMIN_GPX_EXTENSIONS_NAMESPACE, "Speed");
        if (speeds.getLength() == 0) return null;
        try {
            double speed = Double.parseDouble(speeds.item(0).getTextContent().trim());
            return Double.isFinite(speed) && speed >= 0 ? speed : null;
        } catch (NumberFormatException e) {
            log.warn("Ignoring invalid Garmin GPX speed extension: type={} message={}", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private void processSegment(TrackSegment segment, SegmentFilterState state, GpsTrack gpsTrack,
                                IndexedFile indexedFile, OutlierFilterResult result, int sourceSectionIndex) {

        for (WayPoint wayPoint : segment.getPoints()) {
            double t = wayPoint.getTime().map(Instant::toEpochMilli).map(m -> m / MILLIS_PER_SECOND).orElse(Double.NaN);

            double currentElevation = wayPoint.getElevation().map(Length::doubleValue).orElse(Double.NaN);
            Coordinate coordinateLongLat = new CoordinateXYZM(
                    wayPoint.getLongitude().doubleValue(),
                    wayPoint.getLatitude().doubleValue(),
                    currentElevation,
                    t);

            if (state.lastAcceptedPoint != null && Double.isFinite(t)
                && Double.isFinite(state.lastAcceptedPoint.getM())
                && (t < state.lastAcceptedPoint.getM()
                    || t - state.lastAcceptedPoint.getM() > RecordingContinuity.MAX_INTERPOLATION_GAP_SECONDS)) {
                result.outlierCount += state.probationBuffer.size();
                state.probationBuffer.clear();
                state.lastAcceptedPoint = null;
                result.recordingSection++;
            }
            result.recordingSections.put(coordinateLongLat, result.recordingSection);
            result.sourceSections.put(coordinateLongLat, sourceIdentity(wayPoint, "section", sourceSectionIndex));
            result.evidence.put(coordinateLongLat, new GpsObservationEvidence(sourceIdentity(wayPoint, "point", result.rawCoordinates.size()),
                    result.recordingSection, sourceIdentity(wayPoint, "section", sourceSectionIndex), wayPoint.getFix().map(io.jenetics.jpx.Fix::getValue).orElse(null),
                    wayPoint.getSat().map(Number::intValue).orElse(null), wayPoint.getHdop().orElse(null),
                    wayPoint.getVdop().orElse(null), sourceSpeed(wayPoint),
                    wayPoint.getSource().orElse(null), null, List.of()));
            result.rawCoordinates.add(coordinateLongLat);
            if (coordinateLongLat.getX() == 0 && coordinateLongLat.getY() == 0) {
                result.outlierCount++;
                result.cleaningRules.put(sourceIdentity(wayPoint, "point", result.rawCoordinates.size() - 1), "invalid-zero-position");
                gpsTrack.setDidFilterOutlierByDistance(true);
                continue;
            }

            if (state.lastAcceptedPoint == null) {
                result.cleanedCoordinates.add(coordinateLongLat);
                state.lastAcceptedPoint = coordinateLongLat;
                continue;
            }

            double distMain = getDistanceBetweenTwoWGS84(coordinateLongLat, state.lastAcceptedPoint);
            double dtMain = Double.isNaN(coordinateLongLat.getM()) || Double.isNaN(state.lastAcceptedPoint.getM())
                    ? Double.NaN : coordinateLongLat.getM() - state.lastAcceptedPoint.getM();

            // Use observed spacing, not accumulated probation time. A near-duplicate fix
            // must not prevent recovery when the logger resumes its normal cadence.
            double acceptanceDt = state.probationBuffer.isEmpty() ? dtMain : probationAcceptanceDt(state, coordinateLongLat);
            if (isPlausible(distMain, acceptanceDt)) {
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
                if (state.probationBuffer.isEmpty())
                    state.probationEntryDt = dtMain;
                state.lastAcceptedPoint = handleImplausiblePoint(coordinateLongLat, distMain, dtMain,
                        state.lastAcceptedPoint, state.probationBuffer, gpsTrack, indexedFile, result);
            }
        }
    }

    private static double probationAcceptanceDt(SegmentFilterState state, Coordinate point) {
        double entry = state.probationEntryDt;
        if (!(entry > 0)) return entry;
        double observed = point.getM() - state.probationBuffer.getLast().getM();
        return observed > entry ? observed : entry;
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
                shouldPromote = probationBuffer.size() >= PROBATION_TRUST_POINT_COUNT;
            }

            if (shouldPromote) {
                result.recordingSection++;
                for (Coordinate accepted : probationBuffer) {
                    result.recordingSections.put(accepted, result.recordingSection);
                }
                gpsTrack.addLoadMessage("GPS relocation retained as a separate recording span; connecting movement is unknown.");
                log.info("GPS probation retained as disconnected span: points={} jumpMetres={}",
                        probationBuffer.size(), getDistanceBetweenTwoWGS84(probationBuffer.getFirst(), lastAcceptedPoint));

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

    private static List<GpsObservationEvidence> assessObservations(OutlierFilterResult result) {
        Map<Integer, Integer> retained = new HashMap<>();
        for (int i = 0; i < result.cleanedCoordinates.size(); i++) {
            var source = result.evidence.get(result.cleanedCoordinates.get(i));
            if (source != null) retained.put(source.sourcePointIndex(), i);
        }
        List<GpsObservationEvidence> evidence = new ArrayList<>();
        for (Coordinate raw : result.rawCoordinates) {
            GpsObservationEvidence source = result.evidence.get(raw);
            if (source == null) continue;
            Integer index = retained.get(source.sourcePointIndex());
            List<String> decisions = new ArrayList<>();
            if (!Double.isFinite(raw.getM())) decisions.add("TIME_MISSING");
            if (!Double.isFinite(raw.getZ())) decisions.add("ELEVATION_MISSING");
            if (index == null) {
                boolean inStop = result.stopRanges.stream().anyMatch(stop -> raw.getM() >= stop.startTimeS() && raw.getM() <= stop.endTimeS());
                decisions.add(inStop ? "STOP_COLLAPSED" : "POSITION_REJECTED");
            } else if (Double.isFinite(raw.getZ()) && !Double.isFinite(result.cleanedCoordinates.get(index).getZ())) {
                decisions.add("ELEVATION_REJECTED");
            }
            if (index != null && index > 0) {
                Coordinate previous = result.cleanedCoordinates.get(index - 1);
                double dt = raw.getM() - previous.getM();
                if (!Objects.equals(result.recordingSections.get(previous), result.recordingSections.get(raw))
                    && result.evidence.get(previous).sourceRecordingSection() == source.sourceRecordingSection()
                    && Double.isFinite(dt) && dt > 0 && dt <= RecordingContinuity.MAX_INTERPOLATION_GAP_SECONDS)
                    decisions.add("POSITION_DISCONTINUITY");
            }
            evidence.add(new GpsObservationEvidence(source.sourcePointIndex(), result.recordingSections.get(raw), source.sourceRecordingSection(), source.fix(),
                    source.satellites(), source.hdop(), source.vdop(), source.speedMetresPerSecond(), source.source(),
                    index, decisions));
        }
        return evidence;
    }

    private void buildGeometries(LoadResult loadResult, GpsTrack gpsTrack, OutlierFilterResult filterResult) {
        var sourceSections = filterResult.rawCoordinates.stream().map(filterResult.sourceSections::get)
                .collect(java.util.stream.Collectors.toSet());
        gpsTrack.setSourceRecordingSection(sourceSections.size() == 1 ? sourceSections.iterator().next() : null);
        GeometryFactory geometryFactoryLongLat = new GeometryFactory(new PrecisionModel(), 4326);
        loadResult.rawRecordingBreakBeforePointIndexes = recordingBreaks(filterResult.rawCoordinates, filterResult.recordingSections);
        loadResult.observationEvidence = assessObservations(filterResult);
        loadResult.cleaningRules = new HashMap<>(filterResult.cleaningRules);


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

    static Map<String, String> cleaningSettings() {
        return Map.of("position.maximumSpeedMetresPerSecond", Double.toString(MAX_PLAUSIBLE_SPEED_MS),
                "position.maximumTimeCreditSeconds", Double.toString(MAX_DT_SPEED_CHECK_S),
                "position.maximumUnsupportedDistanceMetres", Double.toString(PROBATION_MAX_PROMOTION_DISTANCE_M),
                "position.probationTrustSeconds", Double.toString(PROBATION_TRUST_TIME_S),
                "position.probationTrustPoints", Integer.toString(PROBATION_TRUST_POINT_COUNT),
                "position.sameTimestampGeometryMetres", Double.toString(SAME_TIMESTAMP_GEOMETRY_METRES),
                "position.probationTimeCredit", "maximum-entry-or-previous-observation-interval");
    }

    /**
     * Checks whether a point is physically plausible.
     */
    static boolean isPlausible(double distance, double dt) {
        if (!Double.isFinite(dt)) return distance <= PROBATION_MAX_PROMOTION_DISTANCE_M;
        if (dt < 0) return false;                         // backwards timestamp
        // Equal timestamps cannot establish speed. Preserve supported geometry, as with untimed observations.
        if (dt == 0) return distance <= SAME_TIMESTAMP_GEOMETRY_METRES;
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

    /**
     * Keeps the exact ellipsoidal solution for the rare cases where Vincenty cannot converge.
     */
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


    /**
     * Standard EPSG:3857 projection, checked against GeoTools across its supported area.
     */
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
