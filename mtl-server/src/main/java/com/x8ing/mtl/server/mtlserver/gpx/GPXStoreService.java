package com.x8ing.mtl.server.mtlserver.gpx;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.simplified.SimplifiedTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.simplified.SimplifiedTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentNotes;
import com.x8ing.mtl.server.mtlserver.logic.motion.GpsTrackEventService;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackStopDetector;
import com.x8ing.mtl.server.mtlserver.metrics.MetricConstants;
import com.x8ing.mtl.server.mtlserver.metrics.window.GpsTrackDataPointWindowAdapter;
import com.x8ing.mtl.server.mtlserver.metrics.window.PointWindowedRateCalculator;
import com.x8ing.mtl.server.mtlserver.utils.TimingCollector;
import com.x8ing.mtl.server.mtlserver.web.global.LineStringSerializer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.locationtech.jts.geom.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@JsonPropertyOrder({
        "gpsRepository",
        "gpsDataRepository",
        "simplifiedTrackRepository",
        "gpsTrackDataPointRepository",
        "gpsTrackEventRepository",
        "gpsTrackEventService",
        "gpsSmoother",
        "entityManager",
        "movingWindowInSecs"
})
public class GPXStoreService {

    private final GpsTrackRepository gpsRepository;
    private final GpsTrackDataRepository gpsDataRepository;
    private final SimplifiedTrackRepository simplifiedTrackRepository;
    private final GpsTrackDataPointRepository gpsTrackDataPointRepository;
    private final GpsTrackEventRepository gpsTrackEventRepository;
    private final GpsTrackEventService gpsTrackEventService;
    private final GpsSmoothingAlgorithm gpsSmoother;

    @PersistenceContext
    private EntityManager entityManager;

    private final int movingWindowInSecs = 90;

    private static final double MILLIS_PER_SECOND = 1000.0;
    private static final int MIN_LINESTRING_POINTS = 2;
    private static final double MAX_ABSOLUTE_LATITUDE_DEGREES = 90.0;
    private static final List<BigDecimal> SIMPLIFIED_PRECISIONS = List.of(
            GpsTrackData.PRECISION_1M,
            GpsTrackData.PRECISION_10M,
            GpsTrackData.PRECISION_100M,
            GpsTrackData.PRECISION_1000M);

    private static final int SAVE_CHUNK_SIZE = 500; // flush+clear after every N points to cap Hibernate 1st-level cache memory
    private static final double SECONDS_PER_HOUR = MetricConstants.SECONDS_PER_HOUR;
    private static final double MPS_TO_KMH = MetricConstants.MPS_TO_KMH;
    private static final double ALTITUDE_NOISE_THRESHOLD_M = 2.0;
    private static final double MIN_WINDOW_SECONDS = 1.0;
    private static final double MAX_ELEVATION_RATE_PER_HOUR = MetricConstants.MAX_ELEVATION_RATE_PER_HOUR;
    private static final double MAX_SPEED_KMH = MetricConstants.MAX_SPEED_KMH;
    private static final double MAX_SLOPE_PERCENTAGE = 500.0;
    private static final int UNKNOWN_SOURCE_COORDINATE_INDEX = Integer.MAX_VALUE;

    public GPXStoreService(
            GpsTrackRepository gpsRepository,
            GpsTrackDataRepository gpsDataRepository,
            SimplifiedTrackRepository simplifiedTrackRepository,
            GpsTrackDataPointRepository gpsTrackDataPointRepository,
            GpsTrackEventRepository gpsTrackEventRepository,
            GpsTrackEventService gpsTrackEventService,
            Map<String, GpsSmoothingAlgorithm> smoothers,
            @Value("${mtl.denoise.algorithm:kalman}") String smoothingAlgorithm) {
        this.gpsRepository = gpsRepository;
        this.gpsDataRepository = gpsDataRepository;
        this.simplifiedTrackRepository = simplifiedTrackRepository;
        this.gpsTrackDataPointRepository = gpsTrackDataPointRepository;
        this.gpsTrackEventRepository = gpsTrackEventRepository;
        this.gpsTrackEventService = gpsTrackEventService;
        GpsSmoothingAlgorithm chosen = smoothers.get(smoothingAlgorithm);
        if (chosen == null) {
            log.warn("Unknown smoothing algorithm '{}', falling back to 'kalman'", smoothingAlgorithm);
            chosen = smoothers.get("kalman");
        }
        this.gpsSmoother = Objects.requireNonNull(chosen, "No GPS smoother bean found");
        log.info("Elevation smoothing algorithm: {}", smoothingAlgorithm);
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public List<GPXReader.LoadResult> readAndSave(IndexedFile indexedFile) {
        return readAndSave(indexedFile, null, new TimingCollector());
    }

    /**
     * Reads (or uses pre-converted) GPX XML and saves the resulting tracks.
     *
     * @param indexedFile     the indexed file metadata
     * @param preConvertedXml if non-null, this GPX XML is used instead of reading from disk
     *                        (used when GPSBabel converted a non-GPX file in-memory)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<GPXReader.LoadResult> readAndSave(IndexedFile indexedFile, String preConvertedXml) {
        return readAndSave(indexedFile, preConvertedXml, new TimingCollector());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<GPXReader.LoadResult> readAndSave(IndexedFile indexedFile, String preConvertedXml, TimingCollector timing) {
        TimingCollector fileTiming = timing != null ? timing : new TimingCollector();

        try {
            GPXReader reader = new GPXReader();
            List<GPXReader.LoadResult> loadResults = (preConvertedXml != null)
                    ? reader.importGpxXml(indexedFile, preConvertedXml, fileTiming)
                    : reader.importGpxFile(indexedFile, fileTiming);
            return saveLoadResults(indexedFile, loadResults, fileTiming);

        } catch (Exception e) {
            log.error("Failed read for " + indexedFile + ". e=" + e, e);
        }

        return List.of();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<GPXReader.LoadResult> saveLoadResults(IndexedFile indexedFile, List<GPXReader.LoadResult> loadResults, TimingCollector fileTiming) {
        if (loadResults == null || loadResults.isEmpty()) {
            return List.of();
        }

        List<GPXReader.LoadResult> savedResults = new ArrayList<>();
        Long masterTrackId = null;
        TimingCollector fileLoadTiming = fileTiming.snapshot();

        for (int segIdx = 0; segIdx < loadResults.size(); segIdx++) {
            GPXReader.LoadResult gpsTrackLoadResult = loadResults.get(segIdx);

            // For segments 2..N, set the parent FK to the master (segment 1)
            if (segIdx > 0 && masterTrackId != null) {
                gpsTrackLoadResult.gpsTrack.setSourceParentTrackId(masterTrackId);
            }

            GPXReader.LoadResult saved = saveOneLoadResult(gpsTrackLoadResult, fileLoadTiming, indexedFile);
            savedResults.add(saved);

            // After saving segment 1, capture its ID as the master
            if (segIdx == 0 && saved.gpsTrack.getId() != null) {
                masterTrackId = saved.gpsTrack.getId();
            }
        }

        return savedResults;

    }

    private GPXReader.LoadResult saveOneLoadResult(GPXReader.LoadResult gpsTrackLoadResult, TimingCollector fileTiming, IndexedFile indexedFile) {
        TimingCollector timing = fileTiming != null ? fileTiming.copy() : new TimingCollector();

        timing.timeUnchecked("denoise", () -> {
            // Denoise elevation on the outlier-cleaned track before any further processing.
            // RAW track is never modified.
            if (gpsTrackLoadResult.trackCleaned != null && !gpsTrackLoadResult.trackCleaned.isEmpty()) {
                LineString denoised = smoothRecordingSpans(gpsTrackLoadResult.trackCleaned,
                        gpsTrackLoadResult.recordingBreakBeforePointIndexes, gpsSmoother, gpsTrackLoadResult.observationEvidence);
                gpsTrackLoadResult.trackCleaned = denoised;
                gpsTrackLoadResult.gpsTrack.addLoadMessage("Elevation denoised via smoothing algorithm.");
            }
        });

        gpsTrackLoadResult.processingIssues = timing.timeUnchecked("processing evidence", () -> GpsProcessingAssessment.record(gpsTrackLoadResult));
        log.info("GPS processing evidence recorded: sourceId={} observations={} issues={} version={}",
                indexedFile.getId(), gpsTrackLoadResult.observationEvidence.size(), gpsTrackLoadResult.processingIssues.size(), GpsProcessingAssessment.VERSION);

        Map<String, String> processingSettings = GpsProcessingAssessment.processingSettings(gpsSmoother.processingSettings());

        GpsTrack savedTrack = timing.timeUnchecked("track row", () -> {
            // Compute bounding box and centroid from the outlier-cleaned geometry (SRID 4326: x=lng, y=lat)
            if (gpsTrackLoadResult.trackCleaned != null && !gpsTrackLoadResult.trackCleaned.isEmpty()) {
                org.locationtech.jts.geom.Envelope env = gpsTrackLoadResult.trackCleaned.getEnvelopeInternal();
                gpsTrackLoadResult.gpsTrack.setBboxMinLat(LineStringSerializer.roundToDecimalPlaces(env.getMinY(), LineStringSerializer.DECIMAL_PLACES).doubleValue());
                gpsTrackLoadResult.gpsTrack.setBboxMaxLat(LineStringSerializer.roundToDecimalPlaces(env.getMaxY(), LineStringSerializer.DECIMAL_PLACES).doubleValue());
                gpsTrackLoadResult.gpsTrack.setBboxMinLng(LineStringSerializer.roundToDecimalPlaces(env.getMinX(), LineStringSerializer.DECIMAL_PLACES).doubleValue());
                gpsTrackLoadResult.gpsTrack.setBboxMaxLng(LineStringSerializer.roundToDecimalPlaces(env.getMaxX(), LineStringSerializer.DECIMAL_PLACES).doubleValue());
                org.locationtech.jts.geom.Point centroid = gpsTrackLoadResult.trackCleaned.getCentroid();
                gpsTrackLoadResult.gpsTrack.setCenterLat(LineStringSerializer.roundToDecimalPlaces(centroid.getY(), LineStringSerializer.DECIMAL_PLACES).doubleValue());
                gpsTrackLoadResult.gpsTrack.setCenterLng(LineStringSerializer.roundToDecimalPlaces(centroid.getX(), LineStringSerializer.DECIMAL_PLACES).doubleValue());
            }

            return gpsRepository.save(gpsTrackLoadResult.gpsTrack);
        });

        if (!GpsTrack.LOAD_STATUS.EMPTY_FILE.equals(savedTrack.getLoadStatus())) {

            GpsTrackData raw = GpsTrackData.builder().track(gpsTrackLoadResult.trackRAW).recordingBreakBeforePointIndexes(gpsTrackLoadResult.rawRecordingBreakBeforePointIndexes).observationEvidence(gpsTrackLoadResult.observationEvidence).processingIssues(gpsTrackLoadResult.processingIssues).processingSettings(processingSettings).gpsTrackId(savedTrack.getId()).trackType(GpsTrackData.TRACK_TYPE.RAW).precisionInMeter(GpsTrackData.PRECISION_RAW).createDate(new Date()).build();
            timing.timeUnchecked("raw points", () -> {
                gpsDataRepository.save(raw);
                populatePointData(raw, movingWindowInSecs);
            });

            // save outlier cleaned version — used as the canonical variant for
            // downstream metric calculations (motion duration, energy once
            // activity type is known, etc.)
            GpsTrackData outlierCleaned = GpsTrackData.builder().track(gpsTrackLoadResult.trackCleaned).processingSettings(processingSettings).recordingBreakBeforePointIndexes(gpsTrackLoadResult.recordingBreakBeforePointIndexes).gpsTrackId(savedTrack.getId()).trackType(GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED).precisionInMeter(GpsTrackData.PRECISION_RAW).createDate(new Date()).build();
            PopulatedPoints populated = timing.timeUnchecked("cleaned points", () -> {
                gpsDataRepository.save(outlierCleaned);
                return populatePointData(outlierCleaned, movingWindowInSecs);
            });

            List<GpsTrackDataPoint> cleanedPoints = populated.points();
            timing.timeUnchecked("motion/stops/events", () -> {
                if (gpsTrackLoadResult.trackCleaned != null) {
                    applyCanonicalTrackStats(savedTrack, cleanedPoints);
                } else {
                    savedTrack.setTrackLengthInMeter(0d);
                }

                TrackMotionAnalyzer.Analysis analysis = populated.analysis();
                gpsTrackLoadResult.stopRanges = TrackStopDetector.eventRanges(analysis);
                List<TrackStopDetector.PointStopRange> stopRanges = TrackStopDetector.mapStopRangesToTrackPoints(
                        cleanedPoints, gpsTrackLoadResult.stopRanges);
                applyMotionStats(savedTrack, analysis);
                SegmentNotes stopNotes = TrackStopDetector.summarizeStopRanges(stopRanges);

                savedTrack.setTrackStopCount(stopNotes.stopCount);
                savedTrack.setTrackLongestStopSecs(stopNotes.longestStopSec);
                gpsTrackEventService.replaceDetectedStopEvents(savedTrack.getId(), outlierCleaned.getId(), stopRanges);
                gpsTrackEventService.replaceImportedRecordingGaps(savedTrack.getId(), outlierCleaned.getId(),
                        cleanedPoints, gpsTrackLoadResult.recordingBreakBeforePointIndexes);

                // Energy is intentionally NOT computed here. At ingest time the activity type
                // is still null, which would force the DefaultEnergyCalculator (gravity + kinetic
                // only, no aero, no rolling). The ActivityTypeClassifierJob runs shortly after
                // ingest, determines the real activity type, and triggers energy calculation
                // via EnergyService.recalculateEnergyForTrack.
                savedTrack.addLoadMessage("Energy calculation deferred until activity type is classified.");
            });

            log.debug("Did save entity " + savedTrack.getId());

            // now use the DB to apply the simplified versions of it
            List<SimplifiedTiming> simplifiedTimings = new ArrayList<>();
            long simplifiedStarted = System.currentTimeMillis();
            for (BigDecimal precision : SIMPLIFIED_PRECISIONS) {
                simplifiedTimings.add(calculateSimplified(savedTrack.getId(), precision));
            }
            long simplifiedMs = elapsedMs(simplifiedStarted);
            timing.record("simplified shapes", simplifiedMs);
            savedTrack.addLoadMessage("Simplified track variants created (1m to 1000m).");
            savedTrack.addLoadMessage("Timing simplified: " + formatSimplifiedTimings(simplifiedTimings) + ".");
            log.info("GPS simplified timing trackId={} file={}/{} total={} details={}",
                    savedTrack.getId(), indexedFile.getPath(), indexedFile.getName(),
                    TimingCollector.formatDuration(simplifiedMs), formatSimplifiedTimings(simplifiedTimings));
        }

        // Schedule exploration score calculation for the new track.
        // Note: Overlapping tracks that need recalculation are detected and invalidated
        // by ExplorationScoreJob itself before processing, to avoid lock contention during import.
        timing.timeUnchecked("schedule exploration", () -> {
            if (savedTrack.getStartDate() != null
                && GpsTrack.LOAD_STATUS.SUCCESS.equals(savedTrack.getLoadStatus())) {
                savedTrack.setExplorationStatus(GpsTrack.EXPLORATION_STATUS.SCHEDULED);
            }
        });

        appendTimingSummary(savedTrack, indexedFile, timing);
        gpsRepository.save(savedTrack);

        gpsTrackLoadResult.setProcessingTime(timing.totalElapsedMs());
        log.info("Reading of track id={} and path={} file={} did complete with status={} and took processingTime={}",
                gpsTrackLoadResult.gpsTrack.getId(), indexedFile.getPath(), indexedFile.getName(),
                savedTrack.getLoadStatus(), TimingCollector.formatDuration(gpsTrackLoadResult.getProcessingTime()));

        return gpsTrackLoadResult;
    }

    private void appendTimingSummary(GpsTrack savedTrack, IndexedFile indexedFile, TimingCollector timing) {
        String summary = timing.formatSummary();
        savedTrack.addLoadMessage(summary);
        log.info("GPS ingest timing trackId={} file={}/{} status={} {}",
                savedTrack.getId(), indexedFile.getPath(), indexedFile.getName(),
                savedTrack.getLoadStatus(), summary);
    }

    private static String formatSimplifiedTimings(List<SimplifiedTiming> timings) {
        List<String> parts = new ArrayList<>();
        for (SimplifiedTiming timing : timings) {
            String status = timing.created() ? "" : " skip";
            parts.add(formatPrecision(timing.precision()) + " "
                      + TimingCollector.formatDuration(timing.elapsedMs())
                      + " (" + timing.pointCount() + " pts" + status + ")");
        }
        return String.join(", ", parts);
    }

    private static String formatPrecision(BigDecimal precision) {
        return precision.stripTrailingZeros().toPlainString() + "m";
    }

    private static long elapsedMs(long startedMs) {
        return Math.max(0, System.currentTimeMillis() - startedMs);
    }

    @JsonPropertyOrder({
            "precision",
            "elapsedMs",
            "pointCount",
            "created"
    })
    private record SimplifiedTiming(BigDecimal precision, long elapsedMs, int pointCount, boolean created) {
    }

    private record PopulatedPoints(List<GpsTrackDataPoint> points, TrackMotionAnalyzer.Analysis analysis) {
    }

    static void applyMotionStats(GpsTrack track, TrackMotionAnalyzer.Analysis analysis) {
        boolean complete = analysis.status() == TrackMotionAnalyzer.Status.COMPLETE;
        track.setMotionAnalysisVersion(TrackMotionAnalyzer.VERSION);
        track.setMotionAnalysisStatus(analysis.status());
        track.setTrackDurationInMotionSecs(complete ? analysis.movingSeconds() : null);
        track.setTrackDurationStoppedSecs(complete ? analysis.stoppedSeconds() : null);
        track.setTrackDurationUnknownSecs(complete ? analysis.unknownSeconds() : null);
        track.setTrackDurationShortStoppedSecs(complete ? analysis.shortStoppedSeconds() : null);
    }

    private PopulatedPoints populatePointData(final GpsTrackData gpsTrackData, final int movingWindowInSeconds) {

        if (gpsTrackData == null || gpsTrackData.getTrack() == null) {
            log.info("No work to do for gps track data points data for gpsTrackData");
            return new PopulatedPoints(List.of(), TrackMotionAnalyzer.analyze(List.of(), List.of()));
        }

        log.debug("Create data-points for gpsTrackId=%s, gpsTrackDataId=%s".formatted(gpsTrackData.getGpsTrackId(), gpsTrackData.getId()));

        List<GpsTrackDataPoint> gpsTrackDataPoints = new ArrayList<>();
        calculateBetweenPointsMetrics(gpsTrackData, gpsTrackDataPoints, movingWindowInSeconds);
        calculateMovingWindowStats(movingWindowInSeconds, gpsTrackDataPoints);
        TrackMotionAnalyzer.Analysis analysis = null;
        if (gpsTrackData.getTrackType() == GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED) {
            analysis = TrackMotionAnalyzer.analyze(Arrays.asList(gpsTrackData.getTrack().getCoordinates()),
                    gpsTrackData.getRecordingBreakBeforePointIndexes());
            for (GpsTrackDataPoint point : gpsTrackDataPoints) {
                TrackMotionAnalyzer.Interval interval = analysis.intervals().get(point.getPointIndex());
                point.setMotionState(interval.state());
                point.setMotionReason(interval.reason());
            }
        }

        // Energy fields are left null here on purpose. See readAndSave() — energy is
        // calculated only after the ActivityTypeClassifierJob has set a real activity type.

        for (int i = 0; i < gpsTrackDataPoints.size(); i += SAVE_CHUNK_SIZE) {
            List<GpsTrackDataPoint> chunk = gpsTrackDataPoints.subList(i, Math.min(i + SAVE_CHUNK_SIZE, gpsTrackDataPoints.size()));
            gpsTrackDataPointRepository.saveAll(chunk);
            entityManager.flush();
            entityManager.clear();
        }
        return new PopulatedPoints(gpsTrackDataPoints, analysis);
    }

    static void applyCanonicalTrackStats(GpsTrack gpsTrack, List<GpsTrackDataPoint> canonicalPoints) {
        if (gpsTrack == null) {
            return;
        }

        int pointCount = canonicalPoints == null ? 0 : canonicalPoints.size();
        gpsTrack.setNumberOfTrackPoints(pointCount);

        double[] distances = canonicalPoints == null
                ? new double[0]
                : canonicalPoints.stream()
                .map(GpsTrackDataPoint::getDistanceInMeterBetweenPoints)
                .filter(distance -> distance != null && Double.isFinite(distance))
                .mapToDouble(Double::doubleValue)
                .toArray();

        if (distances.length == 0) {
            gpsTrack.setTrackLengthInMeter(0d);
            gpsTrack.setMaxDistanceBetweenPoints(0d);
            gpsTrack.setMedianDistanceBetweenPoints(0d);
            gpsTrack.setAvgDistanceBetweenPoints(0d);
        } else {
            gpsTrack.setTrackLengthInMeter(Arrays.stream(distances).sum());
            gpsTrack.setMaxDistanceBetweenPoints(StatUtils.max(distances));
            gpsTrack.setMedianDistanceBetweenPoints(new Percentile(50).evaluate(distances));
            gpsTrack.setAvgDistanceBetweenPoints(StatUtils.mean(distances));
        }

        applyCanonicalElevationAndMotionStats(gpsTrack, canonicalPoints);
    }

    static void applyCanonicalDistanceStats(GpsTrack gpsTrack, List<GpsTrackDataPoint> canonicalPoints) {
        applyCanonicalTrackStats(gpsTrack, canonicalPoints);
    }

    private static void applyCanonicalElevationAndMotionStats(GpsTrack gpsTrack, List<GpsTrackDataPoint> canonicalPoints) {
        if (canonicalPoints == null || canonicalPoints.isEmpty()) {
            gpsTrack.setAscentInMeter(0d);
            gpsTrack.setDescentInMeter(0d);
            gpsTrack.setMinAltitude(null);
            gpsTrack.setMaxAltitude(null);
            gpsTrack.setSpeedInKmh30sMax(null);
            gpsTrack.setElevationGainPerHour30sMax(null);
            gpsTrack.setElevationLossPerHour30sMax(null);
            gpsTrack.setSlopePercentageMax(null);
            gpsTrack.setSlopePercentageMin(null);
            return;
        }

        double ascent = 0;
        double descent = 0;
        double minAltitude = Double.POSITIVE_INFINITY;
        double maxAltitude = Double.NEGATIVE_INFINITY;
        double slopeMax = Double.NEGATIVE_INFINITY;
        double slopeMin = Double.POSITIVE_INFINITY;

        for (GpsTrackDataPoint point : canonicalPoints) {
            if (point.getAscentInMeterSinceStart() != null) {
                ascent = point.getAscentInMeterSinceStart();
            }
            if (point.getDescentInMeterSinceStart() != null) {
                descent = point.getDescentInMeterSinceStart();
            }
            if (point.getPointAltitude() != null) {
                minAltitude = Math.min(minAltitude, point.getPointAltitude());
                maxAltitude = Math.max(maxAltitude, point.getPointAltitude());
            }
            if (point.getSlopePercentageInMovingWindow() != null) {
                slopeMax = Math.max(slopeMax, point.getSlopePercentageInMovingWindow());
                slopeMin = Math.min(slopeMin, point.getSlopePercentageInMovingWindow());
            }
        }

        // Track-level 30-second peaks are now derived on-the-fly via the
        // factored {@link PointWindowedRateCalculator} instead of being read
        // back from persisted per-point columns. The per-point window values
        // themselves are NEVER stored — the chart-series endpoint recomputes
        // them on demand from the same canonical stream.
        PointWindowedRateCalculator.PeakSummary peaks = new PointWindowedRateCalculator(
                MetricConstants.DEFAULT_DISPLAY_WINDOW_SEC)
                .computePeaks(canonicalPoints, GpsTrackDataPointWindowAdapter.view());

        gpsTrack.setAscentInMeter(ascent);
        gpsTrack.setDescentInMeter(descent);
        gpsTrack.setMinAltitude(Double.isFinite(minAltitude) ? minAltitude : null);
        gpsTrack.setMaxAltitude(Double.isFinite(maxAltitude) ? maxAltitude : null);
        gpsTrack.setSpeedInKmh30sMax(peaks.speedInKmhMax());
        gpsTrack.setElevationGainPerHour30sMax(peaks.elevationGainPerHourMax());
        gpsTrack.setElevationLossPerHour30sMax(peaks.elevationLossPerHourMax());
        gpsTrack.setSlopePercentageMax(Double.isFinite(slopeMax) ? slopeMax : null);
        gpsTrack.setSlopePercentageMin(Double.isFinite(slopeMin) ? slopeMin : null);
    }

    /**
     * calculate data between points
     */
    private static List<GpsTrackDataPoint> calculateBetweenPointsMetrics(GpsTrackData gpsTrackData, List<GpsTrackDataPoint> gpsTrackDataPoints, int movingWindowInSeconds) {

        GeometryFactory factory = new GeometryFactory();
        GPXReader reader = new GPXReader();
        LineString line = gpsTrackData.getTrack();
        Set<Integer> breaks = new HashSet<>(gpsTrackData.getRecordingBreakBeforePointIndexes() == null
                ? List.of() : gpsTrackData.getRecordingBreakBeforePointIndexes());
        breaks.addAll(RecordingContinuity.timeBreakIndexes(Arrays.asList(line.getCoordinates()), breaks));
        double distance = 0, ascent = 0, descent = 0, elapsed = 0;
        Double elevationBaseline = null;
        Coordinate previous = null;
        for (int i = 0; i < line.getNumPoints(); i++) {
            Coordinate coordinate = line.getCoordinateN(i);
            if (hasInvalidHorizontalPosition(coordinate)) continue;
            GpsTrackDataPoint point = createGpsTrackDataPoint(gpsTrackData.getId(), movingWindowInSeconds,
                    i, line.getNumPoints() - 1, coordinate, factory, reader);
            point.setIntervalStatus(GpsTrackDataPoint.IntervalStatus.START);
            Double altitude = point.getPointAltitude();
            if (previous != null) {
                double dt = Double.isFinite(coordinate.getM()) && Double.isFinite(previous.getM())
                        ? (Math.round(coordinate.getM() * MILLIS_PER_SECOND) - Math.round(previous.getM() * MILLIS_PER_SECOND)) / MILLIS_PER_SECOND : Double.NaN;
                if (Double.isFinite(dt) && dt > 0) {
                    point.setDurationBetweenPointsInSec(dt);
                    elapsed += dt;
                }
                point.setIntervalStatus(breaks.contains(i) ? GpsTrackDataPoint.IntervalStatus.UNKNOWN
                        : RecordingContinuity.intervalStatus(previous, coordinate));
                boolean continuous = point.getIntervalStatus() != GpsTrackDataPoint.IntervalStatus.UNKNOWN;
                if (continuous) {
                    double d = GPXReader.getDistanceBetweenTwoWGS84(previous, coordinate);
                    point.setDistanceInMeterBetweenPoints(d);
                    distance += d;
                    if (altitude != null && elevationBaseline != null && Double.isFinite(previous.getZ())) {
                        double change = altitude - elevationBaseline;
                        double acceptedChange = 0;
                        if (Math.abs(change) > ALTITUDE_NOISE_THRESHOLD_M) {
                            if (change > 0) ascent += change;
                            else descent -= change;
                            elevationBaseline = altitude;
                            acceptedChange = change;
                        }
                        point.setAscentInMeterBetweenPoints(acceptedChange);
                    } else elevationBaseline = altitude;
                } else elevationBaseline = altitude;
            } else elevationBaseline = altitude;
            if (altitude == null) elevationBaseline = null;
            point.setDistanceInMeterSinceStart(distance);
            point.setDurationSinceStart(elapsed);
            point.setAscentInMeterSinceStart(ascent);
            point.setDescentInMeterSinceStart(descent);
            gpsTrackDataPoints.add(point);
            previous = coordinate;
        }
        return gpsTrackDataPoints;
    }

    /**
     * Calculate data based on a moving window using an O(N) two-pointer approach.
     * For each point, the window spans [left..right] where left and right are the
     * furthest points within half the window duration from the center point.
     * Metrics are aggregated over segments [left+1..right] to avoid including
     * segments that cross outside the window boundary.
     */
    static void calculateMovingWindowStats(int movingWindowInSeconds, List<GpsTrackDataPoint> gpsTrackDataPoints) {
        int n = gpsTrackDataPoints.size();
        if (n == 0) return;

        long halfWindowMs = movingWindowInSeconds * 500L;

        for (int i = 0; i < n; i++) {
            GpsTrackDataPoint center = gpsTrackDataPoints.get(i);
            if (center.getPointTimestamp() == null || isUnknownInterval(center)) continue;
            Instant centerTime = center.getPointTimestamp().toInstant();

            // find left boundary: earliest point within half-window before center
            int left = i;
            while (left > 0) {
                GpsTrackDataPoint candidate = gpsTrackDataPoints.get(left - 1);
                if (candidate.getPointTimestamp() == null || isUnknownInterval(gpsTrackDataPoints.get(left))) break;
                long diffMs = Duration.between(candidate.getPointTimestamp().toInstant(), centerTime).toMillis();
                if (diffMs < 0 || (diffMs > halfWindowMs && left != i)) break;
                left--;
            }

            // find right boundary: latest point within half-window after center
            int right = i;
            while (right < n - 1) {
                GpsTrackDataPoint candidate = gpsTrackDataPoints.get(right + 1);
                if (candidate.getPointTimestamp() == null || isUnknownInterval(candidate)) break;
                long diffMs = Duration.between(centerTime, candidate.getPointTimestamp().toInstant()).toMillis();
                if (diffMs < 0 || (diffMs > halfWindowMs && right != i)) break;
                right++;
            }

            // need at least one point on each side for a meaningful window
            if (left == right) continue;

            GpsTrackDataPoint windowStart = gpsTrackDataPoints.get(left);
            GpsTrackDataPoint windowEnd = gpsTrackDataPoints.get(right);
            if (windowStart.getPointTimestamp() == null || windowEnd.getPointTimestamp() == null) continue;

            double diffSeconds = Duration.between(
                    windowStart.getPointTimestamp().toInstant(),
                    windowEnd.getPointTimestamp().toInstant()).toMillis() / 1000.0;
            if (diffSeconds < MIN_WINDOW_SECONDS) continue;

            double ascent = 0;
            double descent = 0;
            double distance = 0;
            boolean altitudeSupported = true;

            // each point's "between" metric belongs to the segment ending at that point,
            // so start from left+1 to avoid including a segment that crosses outside the window
            for (int j = left + 1; j <= right; j++) {
                GpsTrackDataPoint p = gpsTrackDataPoints.get(j);
                if (p.getDistanceInMeterBetweenPoints() != null) {
                    distance += p.getDistanceInMeterBetweenPoints();
                }
                Double a = p.getAscentInMeterBetweenPoints();
                if (a == null) altitudeSupported = false;
                if (a != null) {
                    if (a > 0) ascent += a;
                    else if (a < 0) descent += -a;
                }
            }

            if (altitudeSupported)
                center.setElevationGainPerHourMovingWindow(ascent > 0 ? Math.min(ascent / diffSeconds * SECONDS_PER_HOUR, MAX_ELEVATION_RATE_PER_HOUR) : 0.0);
            if (altitudeSupported && descent > 0) {
                center.setElevationLossPerHourMovingWindow(Math.min(descent / diffSeconds * SECONDS_PER_HOUR, MAX_ELEVATION_RATE_PER_HOUR));
            }
            center.setSpeedInKmhMovingWindow(distance > 0 ? Math.min(distance / diffSeconds * MPS_TO_KMH, MAX_SPEED_KMH) : 0.0);
            if (distance > 0) {
                // Require at least 1 m total window distance before computing slope:
                // the Kalman smoother can collapse adjacent XY positions to near-zero
                // distances, making heightDiff / distance blow up to beyond numeric(12,2).
                if (altitudeSupported && distance >= 1.0 && windowStart.getPointAltitude() != null && windowEnd.getPointAltitude() != null) {
                    double heightDiff = windowEnd.getPointAltitude() - windowStart.getPointAltitude();
                    double slope = heightDiff / distance * 100;
                    center.setSlopePercentageInMovingWindow(Math.max(-MAX_SLOPE_PERCENTAGE, Math.min(slope, MAX_SLOPE_PERCENTAGE)));
                }
            }
        }
    }

    private static boolean isUnknownInterval(GpsTrackDataPoint point) {
        return point.getIntervalStatus() == GpsTrackDataPoint.IntervalStatus.UNKNOWN
               || point.getIntervalStatus() == GpsTrackDataPoint.IntervalStatus.GEOMETRY_ONLY;
    }

    static LineString smoothRecordingSpans(LineString line, List<Integer> breakIndexes, GpsSmoothingAlgorithm smoother) {
        return smoothRecordingSpans(line, breakIndexes, smoother, List.of());
    }

    static LineString smoothRecordingSpans(LineString line, List<Integer> breakIndexes, GpsSmoothingAlgorithm smoother,
                                           List<GpsObservationEvidence> evidence) {
        Set<Integer> breaks = new HashSet<>(breakIndexes == null ? List.of() : breakIndexes);
        List<GpsObservationEvidence> aligned = new ArrayList<>(Collections.nCopies(line.getNumPoints(), null));
        for (GpsObservationEvidence observation : evidence) {
            Integer index = observation.canonicalPointIndex();
            if (index != null && index >= 0 && index < aligned.size()) aligned.set(index, observation);
        }
        List<Coordinate> output = new ArrayList<>(line.getNumPoints());
        for (int from = 0; from < line.getNumPoints(); ) {
            int to = from + 1;
            while (to < line.getNumPoints() && !breaks.contains(to)
                   && !RecordingContinuity.hasTimeBreak(line.getCoordinateN(to - 1).getM(), line.getCoordinateN(to).getM())) to++;
            if (to - from < 2) output.add(copyCoordinate(line.getCoordinateN(from)));
            else {
                Coordinate[] span = Arrays.copyOfRange(line.getCoordinates(), from, to);
                LineString section = line.getFactory().createLineString(span);
                section = StartupElevationStabilizer.stabilize(section).lineString();
                output.addAll(Arrays.asList(smoother.denoise(section, aligned.subList(from, to)).getCoordinates()));
            }
            from = to;
        }
        return line.getFactory().createLineString(output.toArray(Coordinate[]::new));
    }

    private SimplifiedTiming calculateSimplified(Long trackId, BigDecimal tolerance) {
        long started = System.currentTimeMillis();
        int pointCount = 0;
        boolean created = false;
        SimplifiedTrack gpsTrackSimplified = simplifiedTrackRepository.getTrackSimplified(tolerance, trackId);

        if (gpsTrackSimplified != null && gpsTrackSimplified.getLineString() != null && !gpsTrackSimplified.getLineString().isEmpty()) {
            LineString simplifiedLine = gpsTrackSimplified.getLineString();
            GpsTrackData cleaned = gpsDataRepository.findFirstByGpsTrackIdAndTrackType(
                    trackId,
                    GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.name());
            int invalidVertices = countInvalidWgs84Positions(simplifiedLine);
            if (invalidVertices > 0) {
                LineString canonical = cleaned != null ? cleaned.getTrack() : null;
                if (canonical == null || canonical.getNumPoints() < MIN_LINESTRING_POINTS
                    || countInvalidWgs84Positions(canonical) > 0) {
                    log.warn("Skipping simplified shape: trackId={} tolerance={} invalidVertices={} no valid canonical geometry",
                            trackId, tolerance, invalidVertices);
                    return new SimplifiedTiming(tolerance, elapsedMs(started), 0, false);
                }
                // A display projection must not roll back an otherwise valid import.
                // Keep all canonical vertices so timestamps and recording gaps remain intact.
                log.warn("Using canonical geometry for simplified shape: trackId={} tolerance={} invalidVertices={} canonicalPoints={}",
                        trackId, tolerance, invalidVertices, canonical.getNumPoints());
                simplifiedLine = (LineString) canonical.copy();
            }
            if (cleaned != null && cleaned.getTrack() != null) {
                // The display query projects XYM so missing elevations cannot
                // contaminate horizontal coordinates. Restore Z from its source.
                simplifiedLine = restoreCanonicalElevations(simplifiedLine, cleaned.getTrack());
                simplifiedLine = preserveStopAnchors(simplifiedLine, cleaned.getTrack());
            }

            List<Integer> simplifiedBreaks = new ArrayList<>();
            if (cleaned != null && cleaned.getRecordingBreakBeforePointIndexes() != null
                && !cleaned.getRecordingBreakBeforePointIndexes().isEmpty()) {
                // Restore both sides of every source break before mapping it to the LOD.
                List<Coordinate> vertices = new ArrayList<>(Arrays.asList(simplifiedLine.getCoordinates()));
                for (int index : cleaned.getRecordingBreakBeforePointIndexes()) {
                    for (int endpoint : new int[]{index - 1, index}) {
                        if (endpoint >= 0 && endpoint < cleaned.getTrack().getNumPoints()) {
                            Coordinate anchor = cleaned.getTrack().getCoordinateN(endpoint);
                            if (!containsCoordinateAtTime(vertices, anchor)) vertices.add(copyCoordinate(anchor));
                        }
                    }
                }
                SourceCoordinateOrder order = SourceCoordinateOrder.from(cleaned.getTrack());
                vertices.sort(Comparator.comparingInt(order::indexOf));
                simplifiedLine = simplifiedLine.getFactory().createLineString(vertices.toArray(Coordinate[]::new));
                for (int i = 1; i < vertices.size(); i++) {
                    int current = order.indexOf(vertices.get(i));
                    int previous = order.indexOf(vertices.get(i - 1));
                    if (cleaned.getRecordingBreakBeforePointIndexes().stream().anyMatch(b -> b > previous && b <= current)) simplifiedBreaks.add(i);
                }
            }
            GpsTrackData simplified = GpsTrackData.builder().recordingBreakBeforePointIndexes(simplifiedBreaks).track(simplifiedLine).gpsTrackId(trackId).trackType(GpsTrackData.TRACK_TYPE.SIMPLIFIED_SHAPE).precisionInMeter(tolerance).createDate(new Date()).build();
            gpsDataRepository.save(simplified);
            pointCount = simplifiedLine.getNumPoints();
            // SIMPLIFIED_SHAPE is a display-only LOD: geometry + canonical back-pointer
            // only. Derived per-point metrics (speed, slope, energy, …) live on the
            // canonical RAW_OUTLIER_CLEANED variant; the map popup resolves them via
            // canonicalPointIndex. See canonical_metric_lod_architecture.md (Phase 7).
            populateSimplifiedGeometryPoints(simplified, cleaned != null ? cleaned.getTrack() : null);
            created = true;

        } else {
            log.info("Did not create a simplified track, as the linestring was empty.");
        }
        return new SimplifiedTiming(tolerance, elapsedMs(started), pointCount, created);
    }

    /**
     * Build and persist {@code SIMPLIFIED_SHAPE} point rows as a pure
     * geometry/timestamp/canonical-back-pointer projection — NO derived metric
     * fields (speed, slope, distance deltas, energy, …) are computed or stored
     * here. Per the canonical-metric-LOD architecture (Phase 7), the simplified
     * variant is a display-only LOD; all per-point metrics are read from the
     * canonical {@code RAW_OUTLIER_CLEANED} stream via {@code canonicalPointIndex}.
     * <p>
     * Each simplified vertex carries an exact M (timestamp) inherited from
     * {@code ST_SimplifyPreserveTopology}; we resolve {@code canonicalPointIndex}
     * by matching that timestamp against the canonical LineString's M values.
     */
    private void populateSimplifiedGeometryPoints(GpsTrackData simplified, LineString canonicalTrack) {
        if (simplified == null || simplified.getTrack() == null) {
            return;
        }
        LineString lineString = simplified.getTrack();

        SourceCoordinateOrder canonicalOrder = canonicalTrack != null && !canonicalTrack.isEmpty()
                ? SourceCoordinateOrder.from(canonicalTrack)
                : SourceCoordinateOrder.empty();

        GeometryFactory geometryFactory = new GeometryFactory();
        GPXReader gpxReader = new GPXReader();
        List<GpsTrackDataPoint> points = new ArrayList<>(lineString.getNumPoints());
        int pointIndexMax = lineString.getNumPoints() - 1;

        for (int i = 0; i < lineString.getNumPoints(); i++) {
            Coordinate currentPoint = lineString.getCoordinateN(i);
            if (hasInvalidHorizontalPosition(currentPoint)) {
                log.warn("Skipping simplified point index={} in gpsTrackDataId={}: invalid coordinate (NaN lon/lat)", i, simplified.getId());
                continue;
            }

            GpsTrackDataPoint p = createGpsTrackDataPoint(
                    simplified.getId(), movingWindowInSecs, i, pointIndexMax,
                    currentPoint, geometryFactory, gpxReader);
            if (p.getPointTimestamp() != null) {
                Integer canonicalIndex = canonicalOrder.indexOfOrNull(currentPoint);
                if (canonicalIndex != null) {
                    p.setCanonicalPointIndex(canonicalIndex);
                }
            }

            points.add(p);
        }

        for (int i = 0; i < points.size(); i += SAVE_CHUNK_SIZE) {
            List<GpsTrackDataPoint> chunk = points.subList(i, Math.min(i + SAVE_CHUNK_SIZE, points.size()));
            gpsTrackDataPointRepository.saveAll(chunk);
            entityManager.flush();
            entityManager.clear();
        }
    }

    private static GpsTrackDataPoint createGpsTrackDataPoint(Long gpsTrackDataId,
                                                             int movingWindowInSeconds,
                                                             int pointIndex,
                                                             int pointIndexMax,
                                                             Coordinate coordinate,
                                                             GeometryFactory geometryFactory,
                                                             GPXReader gpxReader) {
        GpsTrackDataPoint point = new GpsTrackDataPoint();
        point.setGpsTrackDataId(gpsTrackDataId);
        point.setMovingWindowInSec(movingWindowInSeconds);
        point.setPointIndex(pointIndex);
        point.setPointIndexMax(pointIndexMax);

        Point mercator = gpxReader.convertLongLatWgs84ToPlanarWebMercator(coordinate.x, coordinate.y);
        if (mercator != null) {
            point.setPointXY(geometryFactory.createPoint(new CoordinateXY(mercator.getX(), mercator.getY())));
        }
        point.setPointLongLat(geometryFactory.createPoint(new CoordinateXY(coordinate.getX(), coordinate.getY())));

        double altitude = coordinate.getZ();
        point.setPointAltitude(Double.isFinite(altitude) ? altitude : null);
        double epochSeconds = coordinate.getM();
        if (Double.isFinite(epochSeconds)) {
            point.setPointTimestamp(Timestamp.from(Instant.ofEpochMilli(Math.round(epochSeconds * MILLIS_PER_SECOND))));
        }
        return point;
    }

    private static boolean hasInvalidHorizontalPosition(Coordinate coordinate) {
        return !Double.isFinite(coordinate.getX()) || !Double.isFinite(coordinate.getY());
    }

    private static int countInvalidWgs84Positions(LineString line) {
        int invalid = 0;
        for (Coordinate coordinate : line.getCoordinates()) {
            if (hasInvalidHorizontalPosition(coordinate)
                || Math.abs(coordinate.getY()) > MAX_ABSOLUTE_LATITUDE_DEGREES) {
                invalid++;
            }
        }
        return invalid;
    }

    private static LineString restoreCanonicalElevations(LineString simplified, LineString canonical) {
        SourceCoordinateOrder order = SourceCoordinateOrder.from(canonical);
        Coordinate[] coordinates = Arrays.copyOf(simplified.getCoordinates(), simplified.getNumPoints());
        for (int i = 0; i < coordinates.length; i++) {
            Coordinate coordinate = coordinates[i];
            Integer sourceIndex = order.indexOfOrNull(coordinate);
            double elevation = sourceIndex == null ? coordinate.getZ() : canonical.getCoordinateN(sourceIndex).getZ();
            coordinates[i] = new CoordinateXYZM(
                    coordinate.getX(), coordinate.getY(), elevation, coordinate.getM());
        }
        // Database coordinate factories can infer dimensions from the first
        // vertex and drop every elevation when that vertex has an unknown Z.
        GeometryFactory factory = new GeometryFactory(simplified.getPrecisionModel(), simplified.getSRID());
        return factory.createLineString(coordinates);
    }

    static LineString preserveStopAnchors(LineString simplified, LineString source) {
        if (simplified == null || source == null || simplified.isEmpty() || source.getNumPoints() < MIN_LINESTRING_POINTS) {
            return simplified;
        }

        List<Coordinate> anchors = new ArrayList<>();
        for (int i = 1; i < source.getNumPoints(); i++) {
            Coordinate previous = source.getCoordinateN(i - 1);
            Coordinate current = source.getCoordinateN(i);
            boolean previousSame = i > 1 && TrackStopDetector.isStopAnchorPair(source.getCoordinateN(i - 2), previous);
            boolean followingSame = i + 1 < source.getNumPoints() && TrackStopDetector.isStopAnchorPair(current, source.getCoordinateN(i + 1));
            if (!previousSame && !followingSame && TrackStopDetector.isStopAnchorPair(previous, current)) {
                anchors.add(copyCoordinate(previous));
                anchors.add(copyCoordinate(current));
            }
        }
        if (anchors.isEmpty()) {
            return simplified;
        }

        List<Coordinate> coordinates = new ArrayList<>();
        for (Coordinate coordinate : simplified.getCoordinates()) {
            coordinates.add(copyCoordinate(coordinate));
        }
        if (!allHaveM(coordinates)) {
            log.warn("Could not preserve stop anchors in simplified shape: simplified geometry has no M timestamps");
            return simplified;
        }

        boolean changed = false;
        for (Coordinate anchor : anchors) {
            if (!containsCoordinateAtTime(coordinates, anchor)) {
                coordinates.add(anchor);
                changed = true;
            }
        }
        if (!changed) {
            return simplified;
        }

        SourceCoordinateOrder sourceOrder = SourceCoordinateOrder.from(source);
        coordinates.sort(Comparator
                .comparingInt(sourceOrder::indexOf)
                .thenComparingDouble(Coordinate::getM));
        LineString line = simplified.getFactory().createLineString(coordinates.toArray(Coordinate[]::new));
        line.setSRID(simplified.getSRID());
        return line;
    }

    private static long epochSecond(Coordinate coordinate) {
        double m = coordinate.getM();
        if (Double.isNaN(m)) {
            return Long.MIN_VALUE;
        }
        return Math.round(m * MILLIS_PER_SECOND);
    }

    static Integer canonicalPointIndexFor(LineString canonicalTrack, Coordinate coordinate) {
        if (canonicalTrack == null || coordinate == null || canonicalTrack.isEmpty()) {
            return null;
        }
        return SourceCoordinateOrder.from(canonicalTrack).indexOfOrNull(coordinate);
    }

    private record SourceCoordinateOrder(Map<Long, List<SourceCoordinate>> byEpochSec) {

        static SourceCoordinateOrder empty() {
            return new SourceCoordinateOrder(Collections.emptyMap());
        }

        static SourceCoordinateOrder from(LineString source) {
            Map<Long, List<SourceCoordinate>> orderByEpochSec = new HashMap<>(source.getNumPoints());
            for (int i = 0; i < source.getNumPoints(); i++) {
                Coordinate coordinate = source.getCoordinateN(i);
                long epochSec = epochSecond(coordinate);
                orderByEpochSec.computeIfAbsent(epochSec, ignored -> new ArrayList<>())
                        .add(new SourceCoordinate(i, coordinate.getX(), coordinate.getY()));
            }
            return new SourceCoordinateOrder(orderByEpochSec);
        }

        Integer indexOfOrNull(Coordinate coordinate) {
            int index = indexOf(coordinate);
            return index == UNKNOWN_SOURCE_COORDINATE_INDEX ? null : index;
        }

        int indexOf(Coordinate coordinate) {
            List<SourceCoordinate> candidates = byEpochSec.get(epochSecond(coordinate));
            if (candidates == null || candidates.isEmpty()) {
                return UNKNOWN_SOURCE_COORDINATE_INDEX;
            }
            SourceCoordinate best = candidates.getFirst();
            double bestDistanceSq = best.distanceSq(coordinate);
            for (int i = 1; i < candidates.size(); i++) {
                SourceCoordinate candidate = candidates.get(i);
                double distanceSq = candidate.distanceSq(coordinate);
                if (distanceSq < bestDistanceSq) {
                    best = candidate;
                    bestDistanceSq = distanceSq;
                }
            }
            return best.index();
        }
    }

    private record SourceCoordinate(int index, double x, double y) {
        double distanceSq(Coordinate coordinate) {
            double dx = x - coordinate.getX();
            double dy = y - coordinate.getY();
            return dx * dx + dy * dy;
        }
    }


    private static Coordinate copyCoordinate(Coordinate coordinate) {
        return new CoordinateXYZM(
                coordinate.getX(),
                coordinate.getY(),
                coordinate.getZ(),
                coordinate.getM());
    }

    private static boolean allHaveM(List<Coordinate> coordinates) {
        for (Coordinate coordinate : coordinates) {
            if (Double.isNaN(coordinate.getM())) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsCoordinateAtTime(List<Coordinate> coordinates, Coordinate target) {
        for (Coordinate coordinate : coordinates) {
            if (Double.compare(coordinate.getM(), target.getM()) == 0
                && GPXReader.getDistanceBetweenTwoWGS84(coordinate, target) <= 1.0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Delete all GPS tracks (and their dependencies) associated with the given file,
     * without changing the IndexedFile's status. Use this when re-importing a changed file.
     */
    @Transactional
    public void deleteTracksForFile(IndexedFile indexedFile) {
        if (indexedFile == null) {
            log.info("Nothing to delete, as null was given as indexedFile.");
            return;
        }

        List<GpsTrack> gpsTracksToDelete = gpsRepository.findByIndexedFile(indexedFile);
        gpsTracksToDelete.forEach(this::deleteWithAllDependencies);
    }

    /**
     * Delete all GPS tracks for the given file and mark it as REMOVED.
     * Use this when the file has actually been deleted from disk.
     */
    @Transactional
    public void deleteWithAllDependencies(IndexedFile indexedFile) {
        if (indexedFile == null) {
            log.info("Nothing to delete, as null was given as indexedFile.");
            return;
        }

        deleteTracksForFile(indexedFile);
        indexedFile.setIndexerStatus(IndexedFile.IndexerStatus.REMOVED);
    }

    @Transactional
    public void deleteWithAllDependencies(GpsTrack gpsTrack) {

        if (gpsTrack == null) {
            log.info("Nothing to delete, as null was given as gpsTrack");
            return;
        }

        // Background updates lock the track before its points and correlation queue.
        // Dependency deletion fires queue triggers too, so take the same lock first.
        if (gpsRepository.findByIdForUpdate(gpsTrack.getId()).isEmpty()) {
            log.info("GPS track deletion skipped: trackId={} was already removed", gpsTrack.getId());
            return;
        }

        // Source deletion owns reference cleanup, not the surviving tracks' metadata or results.
        int duplicatesReleased = gpsRepository.releaseDuplicateReferences(gpsTrack.getId());
        int segmentParentsCleared = gpsRepository.clearSegmentParentReferences(gpsTrack.getId());
        log.info("GPS track deletion references cleared: trackId={} duplicatesReleased={} segmentParentsCleared={}",
                gpsTrack.getId(), duplicatesReleased, segmentParentsCleared);

        log.debug("About to delete gpsTrackDataPoints for gpsTrack with id=%s".formatted(gpsTrack.getId()));
        gpsTrackEventRepository.deleteByGpsTrackId(gpsTrack.getId());
        gpsTrackDataPointRepository.deleteAllByGpsTrackId(gpsTrack.getId());

        log.debug("About to delete gpsTrack and it's track data for given id=%s".formatted(gpsTrack.getId()));
        gpsDataRepository.deleteByGpsTrackId(gpsTrack.getId());
        gpsRepository.deleteById(gpsTrack.getId());

        log.info("Did delete gpsTrack and it's track data for given id=%s file=%s/%s".formatted(gpsTrack.getId(), gpsTrack.getIndexedFile().getPath(), gpsTrack.getIndexedFile().getName()));

    }


}
