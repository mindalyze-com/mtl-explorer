package com.x8ing.mtl.server.mtlserver.energy;

import com.x8ing.mtl.server.mtlserver.db.entity.config.ConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.config.ConfigRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Central service for physical/mechanical energy calculations on GPS tracks.
 * <p>
 * Energy is never computed at ingest time, because the activity type is not yet known then.
 * The triggers for (re-)calculation are:
 * <ol>
 *   <li><b>ActivityTypeClassifierJob</b> — runs shortly after ingest, determines the real
 *       activity type, then calls {@link #recalculateEnergyForTrack} once the classification
 *       transaction has committed.</li>
 *   <li><b>User activity-type change</b> — {@code TracksController.saveTrack} detects a type
 *       change, saves the track, then calls {@link #recalculateEnergyForTrack}.</li>
 *   <li><b>What-if endpoint</b> — {@code EnergyController} calls {@link #calculateAndPopulatePoints}
 *       directly against an ad-hoc parameter set and returns the {@link TrackEnergySummary}
 *       without saving. Note: {@code calculateAndPopulatePoints} mutates the passed-in points
 *       in place; the controller is intentionally non-transactional so those JPA entities are
 *       detached and the mutations are not flushed. Do NOT wrap that controller method in a
 *       transaction without loading points in a fresh, detached form.</li>
 * </ol>
 * For the persisting paths (1, 2) the recalc runs in its own {@code REQUIRES_NEW} transaction
 * so it always reads the already-committed activity type from the database.
 */
@Service
@Slf4j
public class EnergyService {

    /**
     * Minimum speed (km/h) for a point to be considered "moving". Matches GPXStoreService.MOVING_SPEED_THRESHOLD_KMH.
     */
    private static final double MOVING_SPEED_THRESHOLD_KMH = 0.5;

    /**
     * Minimum segment duration (seconds) to compute power. Shorter segments are treated as 0 W to avoid GPS-glitch spikes.
     */
    private static final double MIN_SEGMENT_DURATION_SEC = 1.0;

    /**
     * Maximum plausible instantaneous mechanical power (W). Clamps GPS-artifact outliers.
     */
    private static final double MAX_POWER_WATTS = 2500.0;

    /**
     * Conversion factor: 1 Wh = 3600 J.
     */
    private static final double JOULES_PER_WH = 3600.0;

    /**
     * Round to n decimal places.
     */
    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    /**
     * Compute Normalized Power (NP) from a series of per-segment power values (W) and
     * matching segment durations (s). NP is the 4th root of the time-weighted mean of
     * the 30-second trailing rolling-average power raised to the 4th power. Because
     * hard bursts contribute disproportionately (p^4), NP is always ≥ simple average
     * power on variable-effort rides, and reflects physiological cost better.
     * <p>
     * The algorithm walks a trailing 30-second window along the point stream, tracking
     * a time-weighted mean of power within the window, and accumulates
     * {@code rollingAvg^4 * segmentDuration} into the NP integral. Points before the
     * window is fully warmed up (first 30 s of data) are skipped to match Coggan's
     * definition.
     * <p>
     * Industry aliases: Weighted Average Power (Strava), Normalized Power / NP
     * (Garmin, TrainingPeaks), xPower / IsoPower (GoldenCheetah).
     *
     * @param powersWatts  instantaneous power per point (W); same length as durationsSec
     * @param durationsSec duration of the segment ending at each point (s)
     * @return NP in watts, or 0 if the track is shorter than the warm-up window
     */
    static double computeNormalizedPower(double[] powersWatts, double[] durationsSec) {
        if (powersWatts == null || durationsSec == null
            || powersWatts.length == 0 || powersWatts.length != durationsSec.length) {
            return 0;
        }
        final double WINDOW_SEC = 30.0;
        final int n = powersWatts.length;

        double winPowerDuration = 0; // Σ power_k × duration_k over the window
        double winDuration = 0;      // Σ duration_k over the window
        int windowStart = 0;

        double weightedFourthPowerSum = 0;
        double totalDuration = 0;

        for (int i = 0; i < n; i++) {
            double d = durationsSec[i] > 0 ? durationsSec[i] : 0;
            double p = powersWatts[i] > 0 ? powersWatts[i] : 0;
            winPowerDuration += p * d;
            winDuration += d;

            // Shrink the window from the front so it spans ~30 s of trailing data.
            while (windowStart < i) {
                double headD = durationsSec[windowStart] > 0 ? durationsSec[windowStart] : 0;
                if (winDuration - headD >= WINDOW_SEC) {
                    double headP = powersWatts[windowStart] > 0 ? powersWatts[windowStart] : 0;
                    winPowerDuration -= headP * headD;
                    winDuration -= headD;
                    windowStart++;
                } else {
                    break;
                }
            }

            // Skip warm-up: require the trailing window to be fully populated before
            // contributing to the NP integral.
            if (winDuration >= WINDOW_SEC && d > 0) {
                double rollingAvg = winPowerDuration / winDuration;
                double avg4 = rollingAvg * rollingAvg * rollingAvg * rollingAvg;
                weightedFourthPowerSum += avg4 * d;
                totalDuration += d;
            }
        }

        if (totalDuration <= 0) return 0;
        double mean4 = weightedFourthPowerSum / totalDuration;
        return Math.pow(mean4, 0.25);
    }

    private final EnergyCalculatorFactory calculatorFactory;
    private final ConfigRepository configRepository;
    private final GpsTrackRepository gpsTrackRepository;
    private final GpsTrackDataRepository gpsTrackDataRepository;
    private final GpsTrackDataPointRepository gpsTrackDataPointRepository;

    public EnergyService(EnergyCalculatorFactory calculatorFactory,
                         ConfigRepository configRepository,
                         GpsTrackRepository gpsTrackRepository,
                         GpsTrackDataRepository gpsTrackDataRepository,
                         GpsTrackDataPointRepository gpsTrackDataPointRepository) {
        this.calculatorFactory = calculatorFactory;
        this.configRepository = configRepository;
        this.gpsTrackRepository = gpsTrackRepository;
        this.gpsTrackDataRepository = gpsTrackDataRepository;
        this.gpsTrackDataPointRepository = gpsTrackDataPointRepository;
    }

    /**
     * Build a copy of {@code params} with a track-level average-speed fallback filled in
     * (total distance / total duration across the point list, in m/s). This is used by
     * {@code EnergyCalculator.smoothedSpeedMps()} as a last-resort value when a single
     * point has neither a moving-window speed nor a usable per-segment distance/duration
     * pair. Without it, GPX files that lack per-point timestamps produce 0 aero and 0
     * kinetic energy across the board. If the caller already supplied a fallback, or if
     * no meaningful duration can be inferred, {@code params} is returned unchanged.
     */
    EnergyParameters withTrackAverageSpeedFallback(List<GpsTrackDataPoint> points, EnergyParameters params) {
        if (params != null && params.getTrackAverageSpeedMpsFallback() != null
            && params.getTrackAverageSpeedMpsFallback() > 0) {
            return params;
        }

        double totalDistance = 0;
        double totalDuration = 0;
        for (GpsTrackDataPoint p : points) {
            Double d = p.getDistanceInMeterBetweenPoints();
            Double dt = p.getDurationBetweenPointsInSec();
            if (d != null && d > 0 && dt != null && dt > 0) {
                totalDistance += d;
                totalDuration += dt;
            }
        }

        // Fallback to first/last timestamp when per-segment durations are missing entirely —
        // which is exactly the case for GPX exports without per-point <time> tags.
        if (totalDuration <= 0 && points.size() >= 2) {
            GpsTrackDataPoint first = points.getFirst();
            GpsTrackDataPoint last = points.getLast();
            if (first.getPointTimestamp() != null && last.getPointTimestamp() != null) {
                double seconds = (last.getPointTimestamp().getTime() - first.getPointTimestamp().getTime()) / 1000.0;
                if (seconds > 0) {
                    // Use the cumulative distance at the last point (already computed during ingest).
                    Double distSinceStart = last.getDistanceInMeterSinceStart();
                    if (distSinceStart != null && distSinceStart > 0) {
                        totalDistance = distSinceStart;
                        totalDuration = seconds;
                    }
                }
            }
        }

        if (totalDistance <= 0 || totalDuration <= 0) {
            return params != null ? params : EnergyParameters.builder().build();
        }

        double avgMps = totalDistance / totalDuration;
        EnergyParameters base = params != null ? params : EnergyParameters.builder().build();
        return base.toBuilder().trackAverageSpeedMpsFallback(avgMps).build();
    }

    /**
     * Calculate energy for each point in the list and populate the energy fields on each point.
     * Physics formulas produce Joules internally; values are converted to Wh before storing.
     * Also computes energyCumulativeWh and powerWatts per point.
     * It returns a high-precision `TrackEnergySummary` that avoids rounding errors.
     *
     * @param points       ordered list of track data points (must have distance/elevation/speed already calculated)
     * @param activityType the activity type for selecting the appropriate physics model
     * @param params       user/system parameters (weight, optional overrides)
     * @return track-level energy summary accumulated using raw Joules.
     */
    public TrackEnergySummary calculateAndPopulatePoints(List<GpsTrackDataPoint> points, GpsTrack.ACTIVITY_TYPE activityType, EnergyParameters params) {
        if (points == null || points.isEmpty()) {
            return TrackEnergySummary.builder().build();
        }

        EnergyCalculator calculator = calculatorFactory.getCalculator(activityType);

        // Inject a track-level average speed fallback so aero/kinetic still work on GPX files
        // without per-point timestamps (or when moving-window speed is absent). See
        // EnergyCalculator.smoothedSpeedMps() for the full fallback chain.
        EnergyParameters effectiveParams = withTrackAverageSpeedFallback(points, params);

        // High-precision accumulators in Joules
        double cumulativeTotalJoules = 0;
        double cumulativeGravAscentJoules = 0;
        double cumulativeGravDescentJoules = 0;
        double cumulativeAeroDragJoules = 0;
        double cumulativeRollingResistanceJoules = 0;
        double cumulativeKineticPosJoules = 0;
        double cumulativeKineticNegJoules = 0;

        double powerMax = 0;
        double powerSum = 0;
        int powerCount = 0;

        // Per-point series captured for Normalized Power computation after the loop.
        double[] npPowers = new double[points.size()];
        double[] npDurations = new double[points.size()];
        int npIdx = 0;

        // Moving-time accumulation for "Avg Moving Power" (net energy / moving time).
        // Uses chunk-based wall-clock approach (same as GPXStoreService) to avoid
        // floating-point drift from summing thousands of small durations.
        java.util.Date movingSectionStart = null;
        java.util.Date movingSectionEnd = null;
        double movingTimeSec = 0;

        GpsTrackDataPoint prev = null;
        for (GpsTrackDataPoint current : points) {
            EnergyComponents ec = calculator.calculateBetweenPoints(current, prev, effectiveParams);

            // Accumulate raw Joules
            if (ec.getGravitationalJoules() > 0) cumulativeGravAscentJoules += ec.getGravitationalJoules();
            else cumulativeGravDescentJoules += Math.abs(ec.getGravitationalJoules());

            if (ec.getAeroDragJoules() > 0) cumulativeAeroDragJoules += ec.getAeroDragJoules();
            if (ec.getRollingResistanceJoules() > 0) cumulativeRollingResistanceJoules += ec.getRollingResistanceJoules();

            if (ec.getKineticJoules() > 0) cumulativeKineticPosJoules += ec.getKineticJoules();
            else cumulativeKineticNegJoules += Math.abs(ec.getKineticJoules());

            double segmentTotalJoules = ec.totalPositiveJoules();
            cumulativeTotalJoules += segmentTotalJoules;

            // Store per-point energy at full double precision — the DB columns are `double precision`
            // with no scale limit. Any rounding happens at aggregation / display time only; otherwise
            // small per-segment values (e.g. 0.003 Wh) collapse to 0 and the track-level totals drift.
            current.setEnergyGravitationalWh(ec.getGravitationalJoules() / JOULES_PER_WH);
            current.setEnergyAeroDragWh(ec.getAeroDragJoules() / JOULES_PER_WH);
            current.setEnergyRollingResistanceWh(ec.getRollingResistanceJoules() / JOULES_PER_WH);
            current.setEnergyKineticWh(ec.getKineticJoules() / JOULES_PER_WH);
            current.setEnergyTotalWh(segmentTotalJoules / JOULES_PER_WH);
            current.setEnergyCumulativeWh(cumulativeTotalJoules / JOULES_PER_WH);

            // Power = Energy / Time (Joules / seconds = Watts), with guards against GPS glitches.
            // Power is stored at integer-W resolution because that is already far finer than
            // the underlying mechanical accuracy.
            Double duration = current.getDurationBetweenPointsInSec();
            double pointPowerW = 0;
            double pointDurationS = 0;
            if (duration != null && duration >= MIN_SEGMENT_DURATION_SEC && segmentTotalJoules > 0) {
                double power = segmentTotalJoules / duration;
                double clampedPower = Math.min(power, MAX_POWER_WATTS);
                current.setPowerWatts(round(clampedPower, 0));
                pointPowerW = clampedPower;
                pointDurationS = duration;

                if (clampedPower > 0) {
                    powerSum += clampedPower;
                    powerCount++;
                    if (clampedPower > powerMax) powerMax = clampedPower;
                }
            } else {
                current.setPowerWatts(0.0);
                if (duration != null && duration > 0) pointDurationS = duration;
            }
            npPowers[npIdx] = pointPowerW;
            npDurations[npIdx] = pointDurationS;
            npIdx++;

            // Track moving sections for Avg Moving Power
            boolean isMoving = current.getSpeedInKmhMovingWindow() != null
                               && current.getSpeedInKmhMovingWindow() >= MOVING_SPEED_THRESHOLD_KMH
                               && current.getPointTimestamp() != null;
            if (isMoving) {
                if (movingSectionStart == null) {
                    movingSectionStart = current.getPointTimestamp();
                }
                movingSectionEnd = current.getPointTimestamp();
            } else {
                if (movingSectionStart != null && movingSectionEnd != null) {
                    movingTimeSec += (movingSectionEnd.getTime() - movingSectionStart.getTime()) / 1000.0;
                }
                movingSectionStart = null;
                movingSectionEnd = null;
            }

            prev = current;
        }

        // Flush last open moving section
        if (movingSectionStart != null && movingSectionEnd != null) {
            movingTimeSec += (movingSectionEnd.getTime() - movingSectionStart.getTime()) / 1000.0;
        }

        // Avg Moving Power = total net energy / moving time
        double movingPowerAvg = 0;
        if (movingTimeSec > 0 && cumulativeTotalJoules > 0) {
            movingPowerAvg = Math.min(cumulativeTotalJoules / movingTimeSec, MAX_POWER_WATTS);
        }

        double normalizedPower = Math.min(computeNormalizedPower(npPowers, npDurations), MAX_POWER_WATTS);

        return TrackEnergySummary.builder()
                .gravitationalAscentTotalWh(cumulativeGravAscentJoules / JOULES_PER_WH)
                .gravitationalDescentTotalWh(cumulativeGravDescentJoules / JOULES_PER_WH)
                .aeroDragTotalWh(cumulativeAeroDragJoules / JOULES_PER_WH)
                .rollingResistanceTotalWh(cumulativeRollingResistanceJoules / JOULES_PER_WH)
                .kineticPositiveTotalWh(cumulativeKineticPosJoules / JOULES_PER_WH)
                .kineticNegativeTotalWh(cumulativeKineticNegJoules / JOULES_PER_WH)
                .netEnergyTotalWh(cumulativeTotalJoules / JOULES_PER_WH)
                .powerWattsAvg(powerCount > 0 ? powerSum / powerCount : 0)
                .powerWattsMovingAvg(movingPowerAvg)
                .powerWattsMax(powerMax)
                .normalizedPowerWatts(normalizedPower)
                .weightKgUsed(params.getRiderWeightKg()) // Store the actual weight used
                .build();
    }

    /**
     * Aggregate per-point energy data into a track-level summary.
     * Call this after {@link #calculateAndPopulatePoints} has populated the energy fields.
     * All energy values are in Wh (already converted from Joules at point level).
     *
     * @param points       the points with energy fields already populated (in Wh)
     * @param weightKgUsed the rider weight that was used (for audit)
     * @return aggregated summary in Wh
     */
    public TrackEnergySummary calculateTrackEnergySummary(List<GpsTrackDataPoint> points, double weightKgUsed) {
        double gravAscent = 0, gravDescent = 0;
        double aeroDrag = 0, rolling = 0;
        double kineticPos = 0, kineticNeg = 0;
        double netTotal = 0;
        double powerMax = 0;
        double powerSum = 0;
        int powerCount = 0;

        double[] npPowers = new double[points.size()];
        double[] npDurations = new double[points.size()];
        int npIdx = 0;

        java.util.Date movingSectionStart = null;
        java.util.Date movingSectionEnd = null;
        double movingTimeSec = 0;

        for (GpsTrackDataPoint p : points) {
            Double grav = p.getEnergyGravitationalWh();
            if (grav != null) {
                if (grav > 0) gravAscent += grav;
                else gravDescent += Math.abs(grav);
            }

            Double drag = p.getEnergyAeroDragWh();
            if (drag != null && drag > 0) aeroDrag += drag;

            Double roll = p.getEnergyRollingResistanceWh();
            if (roll != null && roll > 0) rolling += roll;

            Double kin = p.getEnergyKineticWh();
            if (kin != null) {
                if (kin > 0) kineticPos += kin;
                else kineticNeg += Math.abs(kin);
            }

            Double total = p.getEnergyTotalWh();
            if (total != null) netTotal += total;

            Double power = p.getPowerWatts();
            if (power != null && power > 0) {
                powerSum += power;
                powerCount++;
                if (power > powerMax) powerMax = power;
            }
            npPowers[npIdx] = power != null && power > 0 ? power : 0;
            Double dpDur = p.getDurationBetweenPointsInSec();
            npDurations[npIdx] = dpDur != null && dpDur > 0 ? dpDur : 0;
            npIdx++;

            // Track moving sections for Avg Moving Power
            boolean isMoving = p.getSpeedInKmhMovingWindow() != null
                               && p.getSpeedInKmhMovingWindow() >= MOVING_SPEED_THRESHOLD_KMH
                               && p.getPointTimestamp() != null;
            if (isMoving) {
                if (movingSectionStart == null) {
                    movingSectionStart = p.getPointTimestamp();
                }
                movingSectionEnd = p.getPointTimestamp();
            } else {
                if (movingSectionStart != null && movingSectionEnd != null) {
                    movingTimeSec += (movingSectionEnd.getTime() - movingSectionStart.getTime()) / 1000.0;
                }
                movingSectionStart = null;
                movingSectionEnd = null;
            }
        }

        // Flush last open moving section
        if (movingSectionStart != null && movingSectionEnd != null) {
            movingTimeSec += (movingSectionEnd.getTime() - movingSectionStart.getTime()) / 1000.0;
        }

        double netTotalWh = netTotal;
        double movingPowerAvg = 0;
        if (movingTimeSec > 0 && netTotalWh > 0) {
            movingPowerAvg = Math.min(netTotalWh * JOULES_PER_WH / movingTimeSec, MAX_POWER_WATTS);
        }

        double normalizedPower = Math.min(computeNormalizedPower(npPowers, npDurations), MAX_POWER_WATTS);

        return TrackEnergySummary.builder()
                .gravitationalAscentTotalWh(gravAscent)
                .gravitationalDescentTotalWh(gravDescent)
                .aeroDragTotalWh(aeroDrag)
                .rollingResistanceTotalWh(rolling)
                .kineticPositiveTotalWh(kineticPos)
                .kineticNegativeTotalWh(kineticNeg)
                .netEnergyTotalWh(netTotal)
                .powerWattsAvg(powerCount > 0 ? powerSum / powerCount : 0)
                .powerWattsMovingAvg(movingPowerAvg)
                .powerWattsMax(powerMax)
                .normalizedPowerWatts(normalizedPower)
                .weightKgUsed(weightKgUsed)
                .build();
    }

    /**
     * Apply a TrackEnergySummary to a GpsTrack entity (sets all energy total fields in Wh).
     */
    public void applyEnergyToTrack(GpsTrack track, TrackEnergySummary summary) {
        track.setEnergyGravitationalTotalWh(round(summary.getGravitationalAscentTotalWh(), 1));
        track.setEnergyGravitationalDescentWh(round(summary.getGravitationalDescentTotalWh(), 1));
        track.setEnergyAeroDragTotalWh(round(summary.getAeroDragTotalWh(), 1));
        track.setEnergyRollingResistanceTotalWh(round(summary.getRollingResistanceTotalWh(), 1));
        track.setEnergyKineticPositiveTotalWh(round(summary.getKineticPositiveTotalWh(), 1));
        track.setEnergyNetTotalWh(round(summary.getNetEnergyTotalWh(), 1));
        track.setEnergyWeightKgUsed(round(summary.getWeightKgUsed(), 1));
        track.setPowerWattsAvg(round(summary.getPowerWattsAvg(), 0));
        track.setPowerWattsMovingAvg(round(summary.getPowerWattsMovingAvg(), 0));
        track.setPowerWattsMax(round(summary.getPowerWattsMax(), 0));
        track.setNormalizedPowerWatts(round(summary.getNormalizedPowerWatts(), 0));
    }

    /**
     * Recalculate and persist energy for an existing track using its *current*
     * {@code activityType}. Needed because the activity-type classifier runs as a
     * post-ingest job — at ingest time {@code activityType} is still null and the
     * pipeline falls back to {@link com.x8ing.mtl.server.mtlserver.energy.impl.DefaultEnergyCalculator}
     * (gravity + kinetic only, no aero, no rolling). Once the classifier sets the
     * real activity type, this method re-runs the per-segment physics on every stored
     * track-data variant (RAW, RAW_OUTLIER_CLEANED, SIMPLIFIED@*), persists the new
     * per-point energy fields, and updates the track-level summary from the
     * {@code RAW_OUTLIER_CLEANED} variant (same variant used at ingest).
     *
     * @return true if energy was recomputed; false if the track has no activity type yet,
     * no data points, or doesn't exist.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recalculateEnergyForTrack(Long gpsTrackId, EnergyParameters params) {
        if (gpsTrackId == null) return false;
        GpsTrack track = gpsTrackRepository.findById(gpsTrackId).orElse(null);
        if (track == null) {
            log.warn("recalculateEnergyForTrack: track id={} not found", gpsTrackId);
            return false;
        }
        if (track.getActivityType() == null) {
            log.debug("recalculateEnergyForTrack: track id={} has no activityType yet — skipping", gpsTrackId);
            return false;
        }

        EnergyParameters effectiveParams = params != null ? params : getDefaultParameters();
        List<GpsTrackData> variants = gpsTrackDataRepository.findAllByGpsTrackId(gpsTrackId);
        if (variants.isEmpty()) return false;

        TrackEnergySummary summaryForTrack = null;
        int variantCount = 0;

        for (GpsTrackData variant : variants) {
            List<GpsTrackDataPoint> points = gpsTrackDataPointRepository.findAllByGpsTrackDataIdOrderByPointIndexAsc(variant.getId());
            if (points.isEmpty()) continue;

            TrackEnergySummary summary = calculateAndPopulatePoints(points, track.getActivityType(), effectiveParams);
            gpsTrackDataPointRepository.saveAll(points);
            variantCount++;

            // Use the same variant as the ingest path (RAW_OUTLIER_CLEANED) for the track-level totals.
            if (GpsTrackData.TRACK_TYPE.RAW_OUTLIER_CLEANED.equals(variant.getTrackType())) {
                summaryForTrack = summary;
            }
        }

        if (summaryForTrack == null) {
            log.warn("recalculateEnergyForTrack: track id={} has no RAW_OUTLIER_CLEANED variant — skipping track-level aggregation", gpsTrackId);
            return false;
        }

        applyEnergyToTrack(track, summaryForTrack);
        track.addLoadMessage("Energy recalculated for activityType=" + track.getActivityType()
                             + " across " + variantCount + " variants (Net Total: "
                             + String.format("%.1f", summaryForTrack.getNetEnergyTotalWh()) + " Wh).");
        gpsTrackRepository.save(track);
        return true;
    }

    /**
     * Build default EnergyParameters by reading user weight from the config table.
     * Falls back to {@link EnergyParameters#DEFAULT_RIDER_WEIGHT_KG} if not configured.
     */
    public EnergyParameters getDefaultParameters() {
        double weight = EnergyParameters.DEFAULT_RIDER_WEIGHT_KG;
        try {
            List<ConfigEntity> configs = configRepository.findConfigEntitiesByDomain1AndDomain2AndDomain3("energy", "user", "riderWeightKg");
            if (configs != null && !configs.isEmpty()) {
                weight = Double.parseDouble(configs.getFirst().getValue());
            }
        } catch (Exception e) {
            log.warn("Could not read energy weight config, using default {}kg: {}", weight, e.getMessage());
        }
        return EnergyParameters.builder().riderWeightKg(weight).build();
    }

    /**
     * Default threshold power (W) used to normalize fitness metrics (Intensity
     * Index, Training Load) when no user-configured value is present. 150 W is a
     * pragmatic average across mixed activities. Cycling-only users typically
     * configure a higher value (their FTP).
     */
    public static final double DEFAULT_THRESHOLD_POWER_WATTS = 150.0;

    /**
     * Read the configured threshold power (W) used to normalize fitness metrics
     * (Intensity Index = NP / threshold; Training Load = (NP/threshold)² × hours × 100).
     * Falls back to {@link #DEFAULT_THRESHOLD_POWER_WATTS} if not configured.
     * Config key: domain1='fitness', domain2='user', domain3='thresholdPowerWatts'.
     */
    public double getThresholdPowerWatts() {
        double threshold = DEFAULT_THRESHOLD_POWER_WATTS;
        try {
            List<ConfigEntity> configs = configRepository.findConfigEntitiesByDomain1AndDomain2AndDomain3("fitness", "user", "thresholdPowerWatts");
            if (configs != null && !configs.isEmpty()) {
                double v = Double.parseDouble(configs.getFirst().getValue());
                if (v > 0) threshold = v;
            }
        } catch (Exception e) {
            log.warn("Could not read threshold power config, using default {}W: {}", threshold, e.getMessage());
        }
        return threshold;
    }
}
