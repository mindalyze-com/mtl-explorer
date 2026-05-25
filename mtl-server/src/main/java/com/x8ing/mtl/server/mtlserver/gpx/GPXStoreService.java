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

    private static final int MIN_LINESTRING_POINTS = 2;
    private static final List<BigDecimal> SIMPLIFIED_PRECISIONS = List.of(
            GpsTrackData.PRECISION_1M,
            GpsTrackData.PRECISION_5M,
            GpsTrackData.PRECISION_10M,
            GpsTrackData.PRECISION_50M,
            GpsTrackData.PRECISION_100M,
            GpsTrackData.PRECISION_500M,
            GpsTrackData.PRECISION_1000M);

    private static final int SAVE_CHUNK_SIZE = 500; // flush+clear after every N points to cap Hibernate 1st-level cache memory
    private static final double SECONDS_PER_HOUR = MetricConstants.SECONDS_PER_HOUR;
    private static final double MPS_TO_KMH = MetricConstants.MPS_TO_KMH;
    private static final double ALTITUDE_NOISE_THRESHOLD_M = 2.0;
    private static final double MOVING_SPEED_THRESHOLD_KMH = 0.5;
    private static final double MIN_WINDOW_SECONDS = 1.0;
    private static final double MAX_ELEVATION_RATE_PER_HOUR = MetricConstants.MAX_ELEVATION_RATE_PER_HOUR;
    private static final double MAX_SPEED_KMH = MetricConstants.MAX_SPEED_KMH;
    private static final double MAX_SLOPE_PERCENTAGE = 500.0;
    private static final double STOP_ANCHOR_TIME_TOLERANCE_S = 0.001;

    public GPXStoreService(
            GpsTrackRepository gpsRepository,
            GpsTrackDataRepository gpsDataRepository,
            SimplifiedTrackRepository simplifiedTrackRepository,
            GpsTrackDataPointRepository gpsTrackDataPointRepository,
            GpsTrackEventRepository gpsTrackEventRepository,
            GpsTrackEventService gpsTrackEventService,
            Map<String, GpsSmoothingAlgorithm> smoothers,
            @Value("${mtl.denoise.algorithm:median}") String smoothingAlgorithm) {
        this.gpsRepository = gpsRepository;
        this.gpsDataRepository = gpsDataRepository;
        this.simplifiedTrackRepository = simplifiedTrackRepository;
        this.gpsTrackDataPointRepository = gpsTrackDataPointRepository;
        this.gpsTrackEventRepository = gpsTrackEventRepository;
        this.gpsTrackEventService = gpsTrackEventService;
        GpsSmoothingAlgorithm chosen = smoothers.get(smoothingAlgorithm);
        if (chosen == null) {
            log.warn("Unknown smoothing algorithm '{}', falling back to 'median'", smoothingAlgorithm);
            chosen = smoothers.get("median");
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

        } catch (Exception e) {
            log.error("Failed read for " + indexedFile + ". e=" + e, e);
        }

        return List.of();
    }

    private GPXReader.LoadResult saveOneLoadResult(GPXReader.LoadResult gpsTrackLoadResult, TimingCollector fileTiming, IndexedFile indexedFile) {
        TimingCollector timing = fileTiming != null ? fileTiming.copy() : new TimingCollector();

        timing.timeUnchecked("denoise", () -> {
            // Denoise elevation on the outlier-cleaned track before any further processing.
            // RAW track is never modified.
            if (gpsTrackLoadResult.trackCleaned != null && !gpsTrackLoadResult.trackCleaned.isEmpty()) {
                StartupElevationStabilizer.Result stabilization =
                        StartupElevationStabilizer.stabilize(gpsTrackLoadResult.trackCleaned);
                if (stabilization.corrected()) {
                    gpsTrackLoadResult.trackCleaned = stabilization.lineString();
                    gpsTrackLoadResult.gpsTrack.addLoadMessage(String.format(Locale.ROOT,
                            "Outlier corrector %s: corrected %d leading elevation point(s) to %.1f m baseline.",
                            StartupElevationStabilizer.CORRECTOR_NAME,
                            stabilization.correctedPointCount(),
                            stabilization.baselineElevation()));
                }
                LineString denoised = gpsSmoother.denoise(gpsTrackLoadResult.trackCleaned);
                gpsTrackLoadResult.trackCleaned = restoreStopAnchorsAfterSmoothing(
                        denoised,
                        gpsTrackLoadResult.stopRanges);
                gpsTrackLoadResult.gpsTrack.addLoadMessage("Elevation denoised via smoothing algorithm.");
            }
        });

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

            GpsTrackData raw = GpsTrackData.builder().track(gpsTrackLoadResult.trackRAW).gpsTrackId(savedTrack.getId()).trackType(GpsTrackData.TRACK_TYPE.RAW).precisionInMeter(GpsTrackData.PRECISION_RAW).createDate(new Date()).build();
            timing.timeUnchecked("raw points", () -> {
                gpsDataRepository.save(raw);
                populatePointData(raw, movingWindowInSecs);
            });

            // save outlier cleaned version — used as the canonical variant for
            // downstream metric calculations (motion duration, energy once
            // activity type is known, etc.)
            GpsTrackData outlierCleaned = GpsTrackData.builder().track(gpsTrackLoadResult.trackCleaned).gpsTrackId(savedTrack.getId()).trackType(GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED).precisionInMeter(GpsTrackData.PRECISION_RAW).createDate(new Date()).build();
            List<GpsTrackDataPoint> cleanedPoints = timing.timeUnchecked("cleaned points", () -> {
                gpsDataRepository.save(outlierCleaned);
                return populatePointData(outlierCleaned, movingWindowInSecs);
            });

            timing.timeUnchecked("motion/stops/events", () -> {
                if (gpsTrackLoadResult.trackCleaned != null) {
                    applyCanonicalTrackStats(savedTrack, cleanedPoints);
                } else {
                    savedTrack.setTrackLengthInMeter(0d);
                }

                // Compute motion duration using chunk-based approach:
                // detect moving/stopped transitions and measure wall-clock time per moving section
                // to avoid floating-point drift from summing thousands of small durations.
                double motionSecs = 0;
                Date movingSectionStart = null;
                Date movingSectionEnd = null;
                for (GpsTrackDataPoint pt : cleanedPoints) {
                    boolean isMoving = pt.getSpeedInKmhMovingWindow() != null
                                       && pt.getSpeedInKmhMovingWindow() >= MOVING_SPEED_THRESHOLD_KMH
                                       && pt.getPointTimestamp() != null;
                    if (isMoving) {
                        if (movingSectionStart == null) {
                            movingSectionStart = pt.getPointTimestamp();
                        }
                        movingSectionEnd = pt.getPointTimestamp();
                    } else {
                        if (movingSectionStart != null && movingSectionEnd != null) {
                            motionSecs += (movingSectionEnd.getTime() - movingSectionStart.getTime()) / 1000.0;
                        }
                        movingSectionStart = null;
                        movingSectionEnd = null;
                    }
                }
                // flush last open moving section
                if (movingSectionStart != null && movingSectionEnd != null) {
                    motionSecs += (movingSectionEnd.getTime() - movingSectionStart.getTime()) / 1000.0;
                }
                savedTrack.setTrackDurationInMotionSecs(motionSecs);

                // Detected-stop totals/events come from the dense-cluster detector
                // that also produced the synthetic stop anchors during GPX import.
                // There is intentionally no legacy migration path; the database is
                // expected to be recreated after derived-stat changes.
                List<TrackStopDetector.PointStopRange> denseStopRanges = TrackStopDetector.mapStopRangesToTrackPoints(
                        cleanedPoints,
                        gpsTrackLoadResult.stopRanges);
                List<TrackStopDetector.PointStopRange> recordingGapBreaks = TrackStopDetector.detectNearbyRecordingGapBreaks(
                        cleanedPoints,
                        denseStopRanges);
                List<TrackStopDetector.PointStopRange> stopRanges = TrackStopDetector.mergePointStopRanges(
                        denseStopRanges,
                        recordingGapBreaks);
                SegmentNotes stopNotes = TrackStopDetector.summarizeStopRanges(stopRanges);
                savedTrack.setTrackDurationStoppedSecs(stopNotes.totalStoppedSec);
                savedTrack.setTrackStopCount(stopNotes.stopCount);
                savedTrack.setTrackLongestStopSecs(stopNotes.longestStopSec);
                gpsTrackEventService.replaceDetectedStopEvents(savedTrack.getId(), outlierCleaned.getId(), stopRanges);

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

    private List<GpsTrackDataPoint> populatePointData(final GpsTrackData gpsTrackData, final int movingWindowInSeconds) {

        if (gpsTrackData == null || gpsTrackData.getTrack() == null) {
            log.info("No work to do for gps track data points data for gpsTrackData");
            return List.of();
        }

        log.debug("Create data-points for gpsTrackId=%s, gpsTrackDataId=%s".formatted(gpsTrackData.getGpsTrackId(), gpsTrackData.getId()));

        List<GpsTrackDataPoint> gpsTrackDataPoints = new ArrayList<>();
        calculateBetweenPointsMetrics(gpsTrackData, gpsTrackDataPoints, movingWindowInSeconds);
        calculateMovingWindowStats(movingWindowInSeconds, gpsTrackDataPoints);

        // Energy fields are left null here on purpose. See readAndSave() — energy is
        // calculated only after the ActivityTypeClassifierJob has set a real activity type.

        for (int i = 0; i < gpsTrackDataPoints.size(); i += SAVE_CHUNK_SIZE) {
            List<GpsTrackDataPoint> chunk = gpsTrackDataPoints.subList(i, Math.min(i + SAVE_CHUNK_SIZE, gpsTrackDataPoints.size()));
            gpsTrackDataPointRepository.saveAll(chunk);
            entityManager.flush();
            entityManager.clear();
        }
        return gpsTrackDataPoints;
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
            gpsTrack.setSpeedInKmh30sMax(0d);
            gpsTrack.setElevationGainPerHour30sMax(0d);
            gpsTrack.setElevationLossPerHour30sMax(0d);
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

        GeometryFactory geometryFactory = new GeometryFactory();
        Coordinate prevPoint = null;
        GpsTrackDataPoint prevTrackPoint = null;
        LineString lineString = gpsTrackData.getTrack();
        double distanceSinceStart = 0;
        double ascentInMeterSinceStart = 0;
        double descentInMeterSinceStart = 0;
        Double lastValidElevation = null;
        GPXReader gpxReader = new GPXReader();

        // calculate base data
        for (int i = 0; i < lineString.getNumPoints(); i++) {
            Coordinate currentPoint = lineString.getCoordinateN(i);

            // Layer 1: skip points with invalid coordinates entirely — they cannot contribute
            // position, distance, or any derived metric, and would crash GeodeticCalculator.
            if (Double.isNaN(currentPoint.x) || Double.isNaN(currentPoint.y)) {
                log.warn("Skipping point index={} in gpsTrackDataId={}: invalid coordinate (NaN lon/lat)", i, gpsTrackData.getId());
                continue;
            }

            GpsTrackDataPoint currentTrackPoint = new GpsTrackDataPoint();
            currentTrackPoint.setGpsTrackDataId(gpsTrackData.getId());
            currentTrackPoint.setMovingWindowInSec(movingWindowInSeconds);
            currentTrackPoint.setPointIndex(i);
            currentTrackPoint.setPointIndexMax(lineString.getNumPoints() - 1);
            Point mercator = gpxReader.convertLongLatWgs84ToPlanarWebMercator(currentPoint.x, currentPoint.y);
            if (mercator != null) {
                // make sure x/y has only two dimensions
                Coordinate mercatorXY = new CoordinateXY(mercator.getX(), mercator.getY());
                currentTrackPoint.setPointXY(geometryFactory.createPoint(mercatorXY));
            }

            // make sure long/lat has only two dimensions
            Coordinate currentPointLongLat = new CoordinateXY(currentPoint.getX(), currentPoint.getY());
            currentTrackPoint.setPointLongLat(geometryFactory.createPoint(currentPointLongLat));
            double z = currentPoint.getZ();
            currentTrackPoint.setPointAltitude(Double.isNaN(z) ? null : z);
            if (currentPoint.getM() > 0) {
                currentTrackPoint.setPointTimestamp(Timestamp.from(Instant.ofEpochSecond((long) (currentPoint.getM()))));
            }

            gpsTrackDataPoints.add(currentTrackPoint);

            // Seed the elevation baseline from the very first point with valid altitude
            if (lastValidElevation == null && currentTrackPoint.getPointAltitude() != null) {
                lastValidElevation = currentTrackPoint.getPointAltitude();
            }

            GpsTrackDataPoint startPoint = gpsTrackDataPoints.get(0);
            if (startPoint.getPointTimestamp() != null && currentTrackPoint.getPointTimestamp() != null) {
                Duration durationSinceStart = Duration.between(startPoint.getPointTimestamp().toInstant(), currentTrackPoint.getPointTimestamp().toInstant());
                currentTrackPoint.setDurationSinceStart(durationSinceStart.toMillis() / 1000.0);
            }

            if (prevTrackPoint != null) {
                // Layer 2: guard the between-points metric block — a bad-coordinate pair that
                // slipped through (e.g. infinite values) must not abort the whole track.
                try {
                    Double distanceInMeterBetweenLastPoint = GPXReader.getDistanceBetweenTwoWGS84(currentPoint, prevPoint);
                    currentTrackPoint.setDistanceInMeterBetweenPoints(distanceInMeterBetweenLastPoint);

                    distanceSinceStart += distanceInMeterBetweenLastPoint;
                    currentTrackPoint.setDistanceInMeterSinceStart(distanceSinceStart);

                    if (currentTrackPoint.getPointAltitude() != null && prevTrackPoint.getPointAltitude() != null) {
                        double ascentInMeterBetweenPoints = currentTrackPoint.getPointAltitude() - prevTrackPoint.getPointAltitude();
                        currentTrackPoint.setAscentInMeterBetweenPoints(ascentInMeterBetweenPoints);
                    } else {
                        currentTrackPoint.setAscentInMeterBetweenPoints(null);
                    }

                    // Accumulated-delta threshold: only register ascent/descent once
                    // the cumulative change from the last committed elevation exceeds
                    // the noise floor. This correctly handles gradual climbs where
                    // each individual sample delta is small but the aggregate is real.
                    if (currentTrackPoint.getPointAltitude() != null && lastValidElevation != null) {
                        double altDiff = currentTrackPoint.getPointAltitude() - lastValidElevation;
                        if (altDiff > ALTITUDE_NOISE_THRESHOLD_M) {
                            ascentInMeterSinceStart += altDiff;
                            lastValidElevation = currentTrackPoint.getPointAltitude();
                        } else if (altDiff < -ALTITUDE_NOISE_THRESHOLD_M) {
                            descentInMeterSinceStart += Math.abs(altDiff);
                            lastValidElevation = currentTrackPoint.getPointAltitude();
                        }
                    }

                    currentTrackPoint.setAscentInMeterSinceStart(ascentInMeterSinceStart);
                    currentTrackPoint.setDescentInMeterSinceStart(descentInMeterSinceStart);

                    if (prevTrackPoint.getPointTimestamp() != null && currentTrackPoint.getPointTimestamp() != null) {
                        Duration durationBetweenPoints = Duration.between(prevTrackPoint.getPointTimestamp().toInstant(), currentTrackPoint.getPointTimestamp().toInstant());
                        currentTrackPoint.setDurationBetweenPointsInSec(durationBetweenPoints.toMillis() / 1000.0);
                    } else {
                        currentTrackPoint.setDurationBetweenPointsInSec(null);
                    }
                } catch (Exception e) {
                    log.warn("Could not calculate between-points metrics for point index={} in gpsTrackDataId={}: {}", i, gpsTrackData.getId(), e.getMessage());
                }
            }

            prevTrackPoint = currentTrackPoint;
            prevPoint = currentPoint;

        }

        // Layer 3: surface the edge case where the whole track produced no usable points
        if (gpsTrackDataPoints.isEmpty() && lineString.getNumPoints() > 0) {
            log.warn("All {} points were skipped for gpsTrackDataId={} — track has no usable coordinates", lineString.getNumPoints(), gpsTrackData.getId());
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
            if (center.getPointTimestamp() == null) continue;
            Instant centerTime = center.getPointTimestamp().toInstant();

            // find left boundary: earliest point within half-window before center
            int left = i;
            while (left > 0) {
                GpsTrackDataPoint candidate = gpsTrackDataPoints.get(left - 1);
                if (candidate.getPointTimestamp() == null) break;
                long diffMs = Duration.between(candidate.getPointTimestamp().toInstant(), centerTime).toMillis();
                if (diffMs > halfWindowMs) break;
                left--;
            }

            // find right boundary: latest point within half-window after center
            int right = i;
            while (right < n - 1) {
                GpsTrackDataPoint candidate = gpsTrackDataPoints.get(right + 1);
                if (candidate.getPointTimestamp() == null) break;
                long diffMs = Duration.between(centerTime, candidate.getPointTimestamp().toInstant()).toMillis();
                if (diffMs > halfWindowMs) break;
                right++;
            }

            // need at least one point on each side for a meaningful window
            if (left == i || right == i) continue;

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

            // each point's "between" metric belongs to the segment ending at that point,
            // so start from left+1 to avoid including a segment that crosses outside the window
            for (int j = left + 1; j <= right; j++) {
                GpsTrackDataPoint p = gpsTrackDataPoints.get(j);
                if (p.getDistanceInMeterBetweenPoints() != null) {
                    distance += p.getDistanceInMeterBetweenPoints();
                }
                Double a = p.getAscentInMeterBetweenPoints();
                if (a != null) {
                    if (a > 0) ascent += a;
                    else if (a < 0) descent += -a;
                }
            }

            center.setElevationGainPerHourMovingWindow(ascent > 0 ? Math.min(ascent / diffSeconds * SECONDS_PER_HOUR, MAX_ELEVATION_RATE_PER_HOUR) : 0.0);
            if (descent > 0) {
                center.setElevationLossPerHourMovingWindow(Math.min(descent / diffSeconds * SECONDS_PER_HOUR, MAX_ELEVATION_RATE_PER_HOUR));
            }
            center.setSpeedInKmhMovingWindow(distance > 0 ? Math.min(distance / diffSeconds * MPS_TO_KMH, MAX_SPEED_KMH) : 0.0);
            if (distance > 0) {
                // Require at least 1 m total window distance before computing slope:
                // the Kalman smoother can collapse adjacent XY positions to near-zero
                // distances, making heightDiff / distance blow up to beyond numeric(12,2).
                if (distance >= 1.0 && windowStart.getPointAltitude() != null && windowEnd.getPointAltitude() != null) {
                    double heightDiff = windowEnd.getPointAltitude() - windowStart.getPointAltitude();
                    double slope = heightDiff / distance * 100;
                    center.setSlopePercentageInMovingWindow(Math.max(-MAX_SLOPE_PERCENTAGE, Math.min(slope, MAX_SLOPE_PERCENTAGE)));
                }
            }
        }
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
            if (cleaned != null && cleaned.getTrack() != null) {
                simplifiedLine = preserveStopAnchors(simplifiedLine, cleaned.getTrack());
            }

            GpsTrackData simplified = GpsTrackData.builder().track(simplifiedLine).gpsTrackId(trackId).trackType(GpsTrackData.TRACK_TYPE.SIMPLIFIED_SHAPE).precisionInMeter(tolerance).createDate(new Date()).build();
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

        Map<Long, Integer> canonicalIndexByEpochSec = Collections.emptyMap();
        if (canonicalTrack != null && !canonicalTrack.isEmpty()) {
            canonicalIndexByEpochSec = new HashMap<>(canonicalTrack.getNumPoints());
            for (int i = 0; i < canonicalTrack.getNumPoints(); i++) {
                double m = canonicalTrack.getCoordinateN(i).getM();
                if (!Double.isNaN(m) && m > 0) {
                    // First occurrence wins on duplicate timestamps — the canonical
                    // RAW_OUTLIER_CLEANED stream is authoritative; preferring the
                    // lower index keeps cursor sync stable.
                    canonicalIndexByEpochSec.putIfAbsent((long) m, i);
                }
            }
        }

        GeometryFactory geometryFactory = new GeometryFactory();
        GPXReader gpxReader = new GPXReader();
        List<GpsTrackDataPoint> points = new ArrayList<>(lineString.getNumPoints());
        int pointIndexMax = lineString.getNumPoints() - 1;

        for (int i = 0; i < lineString.getNumPoints(); i++) {
            Coordinate currentPoint = lineString.getCoordinateN(i);
            if (Double.isNaN(currentPoint.x) || Double.isNaN(currentPoint.y)) {
                log.warn("Skipping simplified point index={} in gpsTrackDataId={}: invalid coordinate (NaN lon/lat)", i, simplified.getId());
                continue;
            }

            GpsTrackDataPoint p = new GpsTrackDataPoint();
            p.setGpsTrackDataId(simplified.getId());
            p.setMovingWindowInSec(movingWindowInSecs);
            p.setPointIndex(i);
            p.setPointIndexMax(pointIndexMax);

            Point mercator = gpxReader.convertLongLatWgs84ToPlanarWebMercator(currentPoint.x, currentPoint.y);
            if (mercator != null) {
                p.setPointXY(geometryFactory.createPoint(new CoordinateXY(mercator.getX(), mercator.getY())));
            }
            p.setPointLongLat(geometryFactory.createPoint(new CoordinateXY(currentPoint.getX(), currentPoint.getY())));

            double z = currentPoint.getZ();
            p.setPointAltitude(Double.isNaN(z) ? null : z);

            double m = currentPoint.getM();
            if (!Double.isNaN(m) && m > 0) {
                long epochSec = (long) m;
                p.setPointTimestamp(Timestamp.from(Instant.ofEpochSecond(epochSec)));
                Integer canonicalIndex = canonicalIndexByEpochSec.get(epochSec);
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

        coordinates.sort(Comparator.comparingDouble(Coordinate::getM));
        LineString line = simplified.getFactory().createLineString(coordinates.toArray(Coordinate[]::new));
        line.setSRID(simplified.getSRID());
        return line;
    }

    static LineString restoreStopAnchorsAfterSmoothing(LineString lineString,
                                                       List<TrackStopDetector.StopRange> stopRanges) {
        if (lineString == null || lineString.isEmpty() || stopRanges == null || stopRanges.isEmpty()) {
            return lineString;
        }

        Coordinate[] coordinates = new Coordinate[lineString.getNumPoints()];
        for (int i = 0; i < lineString.getNumPoints(); i++) {
            coordinates[i] = copyCoordinate(lineString.getCoordinateN(i));
        }

        boolean changed = false;
        for (TrackStopDetector.StopRange stop : stopRanges) {
            StopAnchorIndexes indexes = findStopAnchorIndexes(coordinates, stop.startTimeS(), stop.endTimeS());
            if (indexes == null) {
                continue;
            }

            double anchorElevation = stopAnchorElevation(
                    stop,
                    coordinates[indexes.startIndex()],
                    coordinates[indexes.endIndex()]);
            coordinates[indexes.startIndex()] = new CoordinateXYZM(
                    stop.centerLng(),
                    stop.centerLat(),
                    anchorElevation,
                    stop.startTimeS());
            coordinates[indexes.endIndex()] = new CoordinateXYZM(
                    stop.centerLng(),
                    stop.centerLat(),
                    anchorElevation,
                    stop.endTimeS());
            changed = true;
        }

        if (!changed) {
            return lineString;
        }

        LineString restored = lineString.getFactory().createLineString(coordinates);
        restored.setSRID(lineString.getSRID());
        return restored;
    }

    @JsonPropertyOrder({
            "startIndex",
            "endIndex"
    })
    private record StopAnchorIndexes(int startIndex, int endIndex) {
    }

    private static StopAnchorIndexes findStopAnchorIndexes(Coordinate[] coordinates,
                                                           double startTimeS,
                                                           double endTimeS) {
        if (Double.isNaN(startTimeS) || Double.isNaN(endTimeS)) {
            return null;
        }

        StopAnchorIndexes best = null;
        int bestGap = Integer.MAX_VALUE;
        for (int startIndex = 0; startIndex < coordinates.length - 1; startIndex++) {
            if (!timeMatches(coordinates[startIndex], startTimeS)) {
                continue;
            }
            for (int endIndex = startIndex + 1; endIndex < coordinates.length; endIndex++) {
                if (!timeMatches(coordinates[endIndex], endTimeS)) {
                    continue;
                }
                int gap = endIndex - startIndex;
                if (gap < bestGap) {
                    bestGap = gap;
                    best = new StopAnchorIndexes(startIndex, endIndex);
                }
                break;
            }
        }
        return best;
    }

    private static boolean timeMatches(Coordinate coordinate, double timeS) {
        double coordinateTimeS = coordinate.getM();
        return !Double.isNaN(coordinateTimeS)
               && Math.abs(coordinateTimeS - timeS) <= STOP_ANCHOR_TIME_TOLERANCE_S;
    }

    private static double stopAnchorElevation(TrackStopDetector.StopRange stop,
                                              Coordinate startAnchor,
                                              Coordinate endAnchor) {
        if (!Double.isNaN(stop.centerElevation())) {
            return stop.centerElevation();
        }

        double startZ = startAnchor.getZ();
        double endZ = endAnchor.getZ();
        if (!Double.isNaN(startZ) && !Double.isNaN(endZ)) {
            return (startZ + endZ) / 2.0;
        }
        if (!Double.isNaN(startZ)) {
            return startZ;
        }
        return endZ;
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
    public void deleteWithAllDependencies(IndexedFile indexedFile) {
        if (indexedFile == null) {
            log.info("Nothing to delete, as null was given as indexedFile.");
            return;
        }

        deleteTracksForFile(indexedFile);
        indexedFile.setIndexerStatus(IndexedFile.IndexerStatus.REMOVED);
    }

    public void deleteWithAllDependencies(GpsTrack gpsTrack) {

        if (gpsTrack == null) {
            log.info("Nothing to delete, as null was given as gpsTrack");
            return;
        }

        // check if we have duplicates, if so, we need to remove that reference
        gpsRepository.getDuplicatesForGpsTrackId(gpsTrack.getId()).forEach(duplicateId -> {

            if (!Objects.equals(duplicateId, gpsTrack.getId())) {
                log.info("To delete gpsTrack %s, need to remove duplicate references for id %s".formatted(gpsTrack.getId(), duplicateId));
                Optional<GpsTrack> duplicate = gpsRepository.findById(duplicateId);
                duplicate.ifPresent(d -> {
                    d.setDuplicateOf(null);
                    d.setDuplicateStatus(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET);
                    gpsRepository.save(d);
                });
            }
        });

        // Clear source_parent_track_id FK on any child segments referencing this track
        gpsRepository.findSegmentSiblingIds(gpsTrack.getId()).forEach(siblingId -> {
            gpsRepository.findById(siblingId).ifPresent(sibling -> {
                if (Objects.equals(sibling.getSourceParentTrackId(), gpsTrack.getId())) {
                    sibling.setSourceParentTrackId(null);
                    gpsRepository.save(sibling);
                }
            });
        });

        log.debug("About to delete gpsTrackDataPoints for gpsTrack with id=%s".formatted(gpsTrack.getId()));
        gpsTrackEventRepository.deleteByGpsTrackId(gpsTrack.getId());
        gpsTrackDataPointRepository.deleteAllByGpsTrackId(gpsTrack.getId());

        log.debug("About to delete gpsTrack and it's track data for given id=%s".formatted(gpsTrack.getId()));
        gpsDataRepository.deleteByGpsTrackId(gpsTrack.getId());
        gpsRepository.deleteById(gpsTrack.getId());

        log.info("Did delete gpsTrack and it's track data for given id=%s file=%s/%s".formatted(gpsTrack.getId(), gpsTrack.getIndexedFile().getPath(), gpsTrack.getIndexedFile().getName()));

    }


}
