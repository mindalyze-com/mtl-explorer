package com.x8ing.mtl.server.mtlserver.gpx;

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
import com.x8ing.mtl.server.mtlserver.web.global.LineStringSerializer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Point budgets for the pre-computed {@link GpsTrackData.TRACK_TYPE#SIMPLIFIED_FIXED_POINTS}
     * variants. The smaller budget is for lightweight consumers (overview,
     * tooltips); the larger one is for detailed chart display. Both are built
     * at ingest time from RAW_OUTLIER_CLEANED.
     */
    static final int FIXED_POINTS_BUDGET_SMALL = 750;
    static final int FIXED_POINTS_BUDGET_LARGE = 1500;
    private static final int MIN_LINESTRING_POINTS = 2;

    private static final int SAVE_CHUNK_SIZE = 500; // flush+clear after every N points to cap Hibernate 1st-level cache memory
    private static final double SECONDS_PER_HOUR = 3600.0;
    private static final double MPS_TO_KMH = 3.6;
    private static final double ALTITUDE_NOISE_THRESHOLD_M = 2.0;
    private static final double MOVING_SPEED_THRESHOLD_KMH = 0.5;
    private static final double MIN_WINDOW_SECONDS = 1.0;
    private static final double MAX_ELEVATION_RATE_PER_HOUR = 50_000.0;
    private static final double MAX_SPEED_KMH = 5_000.0;
    private static final double MAX_SLOPE_PERCENTAGE = 500.0;

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
        return readAndSave(indexedFile, null);
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
        long t0 = System.currentTimeMillis();

        try {
            GPXReader reader = new GPXReader();
            List<GPXReader.LoadResult> loadResults = (preConvertedXml != null)
                    ? reader.importGpxXml(indexedFile, preConvertedXml)
                    : reader.importGpxFile(indexedFile);
            if (loadResults == null || loadResults.isEmpty()) {
                return List.of();
            }

            List<GPXReader.LoadResult> savedResults = new ArrayList<>();
            Long masterTrackId = null;

            for (int segIdx = 0; segIdx < loadResults.size(); segIdx++) {
                GPXReader.LoadResult gpsTrackLoadResult = loadResults.get(segIdx);

                // For segments 2..N, set the parent FK to the master (segment 1)
                if (segIdx > 0 && masterTrackId != null) {
                    gpsTrackLoadResult.gpsTrack.setSourceParentTrackId(masterTrackId);
                }

                GPXReader.LoadResult saved = saveOneLoadResult(gpsTrackLoadResult, t0, indexedFile);
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

    private GPXReader.LoadResult saveOneLoadResult(GPXReader.LoadResult gpsTrackLoadResult, long t0, IndexedFile indexedFile) {

        // Denoise elevation on the outlier-cleaned track before any further processing.
        // RAW track is never modified.
        if (gpsTrackLoadResult.trackCleaned != null && !gpsTrackLoadResult.trackCleaned.isEmpty()) {
            gpsTrackLoadResult.trackCleaned = gpsSmoother.denoise(gpsTrackLoadResult.trackCleaned);
            gpsTrackLoadResult.gpsTrack.addLoadMessage("Elevation denoised via smoothing algorithm.");
        }

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

        GpsTrack savedTrack = gpsRepository.save(gpsTrackLoadResult.gpsTrack);

        if (!GpsTrack.LOAD_STATUS.EMPTY_FILE.equals(savedTrack.getLoadStatus())) {

            GpsTrackData raw = GpsTrackData.builder().track(gpsTrackLoadResult.trackRAW).gpsTrackId(savedTrack.getId()).trackType(GpsTrackData.TRACK_TYPE.RAW).precisionInMeter(GpsTrackData.PRECISION_RAW).createDate(new Date()).build();
            gpsDataRepository.save(raw);
            populatePointData(raw, movingWindowInSecs);

            // save outlier cleaned version — used as the canonical variant for
            // downstream metric calculations (motion duration, energy once
            // activity type is known, etc.)
            GpsTrackData outlierCleaned = GpsTrackData.builder().track(gpsTrackLoadResult.trackCleaned).gpsTrackId(savedTrack.getId()).trackType(GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED).precisionInMeter(GpsTrackData.PRECISION_RAW).createDate(new Date()).build();
            gpsDataRepository.save(outlierCleaned);
            List<GpsTrackDataPoint> cleanedPoints = populatePointData(outlierCleaned, movingWindowInSecs);

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

            // Detected-stop totals (≥ 30 s below 0.5 km/h). Shares the exact same
            // algorithm the measure-tool uses for per-segment stop annotations via
            // TrackMotionAnalyzer, so the client never has to recompute this from
            // simplified variants (which drop the low-speed samples the detector
            // depends on).
            List<TrackMotionAnalyzer.StopRange> stopRanges = TrackMotionAnalyzer.detectStopRangesInTrack(cleanedPoints);
            SegmentNotes stopNotes = TrackMotionAnalyzer.summarizeStopRanges(stopRanges);
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

            log.debug("Did save entity " + savedTrack.getId());

            // now use the DB to apply the simplified versions of it
            calculateSimplified(savedTrack.getId(), GpsTrackData.PRECISION_1M);
            calculateSimplified(savedTrack.getId(), GpsTrackData.PRECISION_5M);
            calculateSimplified(savedTrack.getId(), GpsTrackData.PRECISION_10M);
            calculateSimplified(savedTrack.getId(), GpsTrackData.PRECISION_50M);
            calculateSimplified(savedTrack.getId(), GpsTrackData.PRECISION_100M);
            calculateSimplified(savedTrack.getId(), GpsTrackData.PRECISION_500M);
            calculateSimplified(savedTrack.getId(), GpsTrackData.PRECISION_1000M);

            savedTrack.addLoadMessage("Simplified track variants created (1m to 1000m).");

            // Pre-compute the time-uniform SIMPLIFIED_FIXED_POINTS variants for
            // charts / tooltips. These SELECT from RAW_OUTLIER_CLEANED (built
            // above as `cleanedPoints`) and never recompute window metrics.
            calculateFixedPoints(savedTrack.getId(), cleanedPoints, FIXED_POINTS_BUDGET_SMALL);
            calculateFixedPoints(savedTrack.getId(), cleanedPoints, FIXED_POINTS_BUDGET_LARGE);
            savedTrack.addLoadMessage("Fixed-point variants created (%d, %d)."
                    .formatted(FIXED_POINTS_BUDGET_SMALL, FIXED_POINTS_BUDGET_LARGE));

            // Single save — persists motion secs and load messages together
            gpsRepository.save(savedTrack);
        }

        // Schedule exploration score calculation for the new track.
        // Note: Overlapping tracks that need recalculation are detected and invalidated
        // by ExplorationScoreJob itself before processing, to avoid lock contention during import.
        if (savedTrack.getStartDate() != null
            && GpsTrack.LOAD_STATUS.SUCCESS.equals(savedTrack.getLoadStatus())) {
            savedTrack.setExplorationStatus(GpsTrack.EXPLORATION_STATUS.SCHEDULED);
            gpsRepository.save(savedTrack);
        }

        gpsTrackLoadResult.setProcessingTime(System.currentTimeMillis() - t0);
        log.info("Reading of track id=%s and path=%s file=%s did complete with success=true and took a processingTime=%s".formatted(gpsTrackLoadResult.gpsTrack.getId(), indexedFile.getPath(), indexedFile.getName(), gpsTrackLoadResult.getProcessingTime()));

        return gpsTrackLoadResult;
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

    private void calculateSimplified(Long trackId, BigDecimal tolerance) {
        SimplifiedTrack gpsTrackSimplified = simplifiedTrackRepository.getTrackSimplified(tolerance, trackId);

        if (gpsTrackSimplified != null && gpsTrackSimplified.getLineString() != null && !gpsTrackSimplified.getLineString().isEmpty()) {

            GpsTrackData simplified = GpsTrackData.builder().track(gpsTrackSimplified.getLineString()).gpsTrackId(trackId).trackType(GpsTrackData.TRACK_TYPE.SIMPLIFIED_SHAPE).precisionInMeter(tolerance).createDate(new Date()).build();
            gpsDataRepository.save(simplified);
            populatePointData(simplified, movingWindowInSecs);

        } else {
            log.info("Did not create a simplified track, as the linestring was empty.");
        }
    }

    /**
     * Build a time-uniform {@link GpsTrackData.TRACK_TYPE#SIMPLIFIED_FIXED_POINTS}
     * variant capped at {@code maxPoints} by SELECTING rows from the already-
     * persisted RAW_OUTLIER_CLEANED point series.
     * <p>
     * All per-point metrics (window stats, energy, cumulative totals) are
     * copied verbatim — they were computed on the full-density RAW series
     * and remain correct in isolation. Only between-point deltas
     * ({@code distanceBetweenPoints}, {@code durationBetweenPoints},
     * {@code ascentBetweenPoints}, {@code energyTotalWh}) are recomputed
     * between the surviving point pairs. {@code pointIndex} is reassigned
     * 0..N-1.
     * <p>
     * If the source has ≤ {@code maxPoints} points every point is kept.
     * Otherwise the track's time range is divided into {@code maxPoints}
     * equal buckets and the point whose timestamp is closest to each bucket
     * centre is picked, yielding even temporal spacing.
     */
    private void calculateFixedPoints(Long trackId, List<GpsTrackDataPoint> cleanedPoints, int maxPoints) {
        if (cleanedPoints == null || cleanedPoints.isEmpty()) {
            log.info("Skipping SIMPLIFIED_FIXED_POINTS@{} for trackId={}: RAW_OUTLIER_CLEANED has no points",
                    maxPoints, trackId);
            return;
        }

        List<GpsTrackDataPoint> selected = selectUniformInTime(cleanedPoints, maxPoints);
        if (selected.isEmpty()) {
            log.info("Skipping SIMPLIFIED_FIXED_POINTS@{} for trackId={}: uniform-time selection returned empty",
                    maxPoints, trackId);
            return;
        }
        if (selected.size() < MIN_LINESTRING_POINTS) {
            log.info("Skipping SIMPLIFIED_FIXED_POINTS@{} for trackId={}: uniform-time selection returned only {} point",
                    maxPoints, trackId, selected.size());
            return;
        }

        // Build a WGS84 LineString (SRID 4326) of the surviving points so the
        // gps_track_data.track column is valid — downstream per-point queries
        // read from gps_track_data_points, but a non-null LineString keeps the
        // variant consistent with the other track types.
        GeometryFactory geometryFactory = new GeometryFactory();
        Coordinate[] coords = new Coordinate[selected.size()];
        for (int i = 0; i < selected.size(); i++) {
            GpsTrackDataPoint src = selected.get(i);
            double lng = src.getPointLongLat() != null ? src.getPointLongLat().getX() : Double.NaN;
            double lat = src.getPointLongLat() != null ? src.getPointLongLat().getY() : Double.NaN;
            double alt = src.getPointAltitude() != null ? src.getPointAltitude() : Double.NaN;
            double epochSec = src.getPointTimestamp() != null
                    ? src.getPointTimestamp().getTime() / 1000.0
                    : Double.NaN;
            CoordinateXYZM c = new CoordinateXYZM(lng, lat, alt, epochSec);
            coords[i] = c;
        }
        LineString line = geometryFactory.createLineString(coords);
        line.setSRID(4326);

        GpsTrackData fixedPoints = GpsTrackData.builder()
                .track(line)
                .gpsTrackId(trackId)
                .trackType(GpsTrackData.TRACK_TYPE.SIMPLIFIED_FIXED_POINTS)
                .maxPoints(maxPoints)
                // precisionInMeter stays null — it is not the discriminator for this variant
                .createDate(new Date())
                .build();
        gpsDataRepository.save(fixedPoints);

        Long newDataId = fixedPoints.getId();
        int n = selected.size();
        List<GpsTrackDataPoint> newPoints = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            GpsTrackDataPoint src = selected.get(i);
            GpsTrackDataPoint dst = new GpsTrackDataPoint();

            // ── Identity / ownership ──
            dst.setGpsTrackDataId(newDataId);
            dst.setPointIndex(i);
            dst.setPointIndexMax(n - 1);
            dst.setMovingWindowInSec(src.getMovingWindowInSec());
            dst.setCreateDate(new Date());

            // ── Absolute position & time (copied verbatim) ──
            dst.setPointLongLat(src.getPointLongLat());
            dst.setPointXY(src.getPointXY());
            dst.setPointAltitude(src.getPointAltitude());
            dst.setPointTimestamp(src.getPointTimestamp());

            // ── Cumulative totals since start (copied verbatim) ──
            dst.setDistanceInMeterSinceStart(src.getDistanceInMeterSinceStart());
            dst.setAscentInMeterSinceStart(src.getAscentInMeterSinceStart());
            dst.setDescentInMeterSinceStart(src.getDescentInMeterSinceStart());
            dst.setDurationSinceStart(src.getDurationSinceStart());

            // ── Moving-window metrics (copied verbatim — window was computed
            //     on full-density RAW, the value describes THIS moment in time) ──
            dst.setElevationGainPerHourMovingWindow(src.getElevationGainPerHourMovingWindow());
            dst.setElevationLossPerHourMovingWindow(src.getElevationLossPerHourMovingWindow());
            dst.setSpeedInKmhMovingWindow(src.getSpeedInKmhMovingWindow());
            dst.setSlopePercentageInMovingWindow(src.getSlopePercentageInMovingWindow());

            // ── Energy (cumulative + instantaneous, copied verbatim) ──
            dst.setEnergyCumulativeWh(src.getEnergyCumulativeWh());
            dst.setPowerWatts(src.getPowerWatts());
            dst.setEnergyGravitationalWh(src.getEnergyGravitationalWh());
            dst.setEnergyAeroDragWh(src.getEnergyAeroDragWh());
            dst.setEnergyRollingResistanceWh(src.getEnergyRollingResistanceWh());
            dst.setEnergyKineticWh(src.getEnergyKineticWh());

            // ── Between-point deltas (RECOMPUTED between surviving pairs) ──
            if (i == 0) {
                dst.setDistanceInMeterBetweenPoints(null);
                dst.setDurationBetweenPointsInSec(null);
                dst.setAscentInMeterBetweenPoints(null);
                dst.setEnergyTotalWh(null);
            } else {
                GpsTrackDataPoint prev = selected.get(i - 1);

                if (src.getDistanceInMeterSinceStart() != null && prev.getDistanceInMeterSinceStart() != null) {
                    dst.setDistanceInMeterBetweenPoints(
                            src.getDistanceInMeterSinceStart() - prev.getDistanceInMeterSinceStart());
                }
                if (src.getDurationSinceStart() != null && prev.getDurationSinceStart() != null) {
                    dst.setDurationBetweenPointsInSec(
                            src.getDurationSinceStart() - prev.getDurationSinceStart());
                }
                if (src.getPointAltitude() != null && prev.getPointAltitude() != null) {
                    dst.setAscentInMeterBetweenPoints(
                            src.getPointAltitude() - prev.getPointAltitude());
                }
                if (src.getEnergyCumulativeWh() != null && prev.getEnergyCumulativeWh() != null) {
                    dst.setEnergyTotalWh(
                            src.getEnergyCumulativeWh() - prev.getEnergyCumulativeWh());
                }
            }

            newPoints.add(dst);
        }

        for (int i = 0; i < newPoints.size(); i += SAVE_CHUNK_SIZE) {
            List<GpsTrackDataPoint> chunk = newPoints.subList(i, Math.min(i + SAVE_CHUNK_SIZE, newPoints.size()));
            gpsTrackDataPointRepository.saveAll(chunk);
            entityManager.flush();
            entityManager.clear();
        }

        log.info("Created SIMPLIFIED_FIXED_POINTS variant trackId={} maxPoints={} selectedPoints={} (source={} points)",
                trackId, maxPoints, n, cleanedPoints.size());
    }

    /**
     * Pick at most {@code maxPoints} points from {@code source} with uniform
     * spacing on the timestamp axis. If the source has fewer than
     * {@code maxPoints} points, all are returned in order. Points without
     * a timestamp are skipped (they can't be placed on the time axis).
     */
    static List<GpsTrackDataPoint> selectUniformInTime(List<GpsTrackDataPoint> source, int maxPoints) {
        List<GpsTrackDataPoint> withTs = new ArrayList<>(source.size());
        for (GpsTrackDataPoint p : source) {
            if (p.getPointTimestamp() != null) {
                withTs.add(p);
            }
        }
        if (withTs.isEmpty()) return List.of();
        if (withTs.size() <= maxPoints) return withTs;

        long tStart = withTs.get(0).getPointTimestamp().getTime();
        long tEnd = withTs.get(withTs.size() - 1).getPointTimestamp().getTime();
        long totalSpan = tEnd - tStart;
        if (totalSpan <= 0) {
            // All points share the same timestamp — just take the first maxPoints.
            return new ArrayList<>(withTs.subList(0, maxPoints));
        }

        // Divide [tStart, tEnd] into maxPoints equal buckets; for each bucket
        // centre walk the source with a moving cursor to find the closest
        // point. Source is already ordered by timestamp (point_index), so a
        // single forward pass is enough.
        List<GpsTrackDataPoint> picked = new ArrayList<>(maxPoints);
        Long lastPickedTs = null;
        int cursor = 0;
        for (int bucket = 0; bucket < maxPoints; bucket++) {
            double targetRel = (bucket + 0.5) / maxPoints;
            long targetTs = tStart + Math.round(targetRel * totalSpan);

            // advance cursor while the next point is closer to targetTs
            while (cursor + 1 < withTs.size()) {
                long curDiff = Math.abs(withTs.get(cursor).getPointTimestamp().getTime() - targetTs);
                long nxtDiff = Math.abs(withTs.get(cursor + 1).getPointTimestamp().getTime() - targetTs);
                if (nxtDiff <= curDiff) cursor++;
                else break;
            }

            GpsTrackDataPoint candidate = withTs.get(cursor);
            long ts = candidate.getPointTimestamp().getTime();
            if (lastPickedTs != null && ts == lastPickedTs) {
                // Two consecutive buckets resolved to the same source point
                // (happens at the tail of very dense segments); skip to keep
                // the output strictly increasing in time.
                continue;
            }
            picked.add(candidate);
            lastPickedTs = ts;
        }
        return picked;
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
