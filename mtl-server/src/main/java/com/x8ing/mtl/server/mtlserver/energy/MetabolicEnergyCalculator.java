package com.x8ing.mtl.server.mtlserver.energy;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.ACTIVITY_TYPE;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State;

import java.util.EnumSet;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.energy.MetabolicEnergy.ExclusionReason.*;

/**
 * Server-owned estimates. Sources, assumptions and valid domains: documentation/architecture/analysis/energy.md.
 */
public final class MetabolicEnergyCalculator {
    public static final String MODEL_VERSION = "2026-09-30-v3";
    private static final double SECONDS_PER_MINUTE = 60;
    private static final double JOULES_PER_KCAL = 4184;
    private static final double JOULES_PER_WH = 3600;
    private static final double ENERGY_COMPARISON_TOLERANCE_J = 1e-6;
    private static final double ML_OXYGEN_PER_MET_KG_MINUTE = 3.5;
    private static final double KCAL_PER_ML_OXYGEN = 0.005;
    private static final double RESTING_MET = 1;
    private static final double ACSM_UNLOADED_OXYGEN = 7;
    private static final double ACSM_OXYGEN_PER_WATT = 10.8;
    private static final double MAX_MINETTI_GRADE = 0.45;
    private static final double PERCENT = 100;
    private static final double MIN_GRADE_FALLBACK_DISTANCE_M = 1;
    private static final double ZERO_DISPLACEMENT_EPSILON_M = 1e-6;
    private static final double MTB_MET = 8.5;
    private static final double ROWING_MET = 3.5;
    private static final double KAYAKING_MET = 5;
    private static final double PADDLE_BOARDING_MET = 6.5;
    private static final double SKIING_MET = 7;
    private static final double PASSENGER_MET = 1.3;
    private static final double MOTORCYCLING_MET = 2.8;
    // Minetti et al. (2002), net J/(kg m), coefficients in descending polynomial order.
    private static final double[] WALKING_COST = {280.5, -58.7, -76.8, 51.9, 19.6, 2.5};
    private static final double[] RUNNING_COST = {155.4, -30.4, -43.3, 46.3, 19.5, 3.6};

    private MetabolicEnergyCalculator() {
    }

    public static MetabolicEnergy calculate(List<GpsTrackDataPoint> points, ACTIVITY_TYPE activity, double bodyWeightKg) {
        var result = new MetabolicEnergy();
        result.setMethod(methodFor(activity));
        result.setModelVersion(MODEL_VERSION);
        result.setBodyWeightKg(bodyWeightKg);
        result.setCoverage(MetabolicEnergy.Coverage.UNAVAILABLE);
        if (points == null || points.size() < 2 || activity == null || !EnergyParameters.isValidRiderWeight(bodyWeightKg)) {
            result.setExclusionReasons(List.of(activity == null ? ACTIVITY_REQUIRED
                    : !EnergyParameters.isValidRiderWeight(bodyWeightKg) ? INVALID_BODY_WEIGHT : INSUFFICIENT_DATA));
            return result;
        }

        double active = 0, total = 0, covered = 0, uncovered = 0, recordingGaps = 0, sparseSampling = 0;
        boolean unknownDuration = false;
        var reasons = EnumSet.noneOf(MetabolicEnergy.ExclusionReason.class);
        for (int i = 1; i < points.size(); i++) {
            var point = points.get(i);
            var previous = points.get(i - 1);
            if (harmlessDuplicate(point, previous)) continue;
            Double duration = point.getDurationBetweenPointsInSec();
            MetabolicEnergy.ExclusionReason reason = null;
            if (!positive(duration)) reason = MISSING_TIMING;
            else if (point.getMotionReason() == com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.Reason.RECORDING_GAP) reason = RECORDING_GAP;
            else if (point.getMotionReason() == com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.Reason.SPARSE_SUPPORT) reason = SPARSE_SAMPLING;
            else if (point.getIntervalStatus() != GpsTrackDataPoint.IntervalStatus.OBSERVED
                     || (point.getMotionState() != State.MOVING && point.getMotionState() != State.STOPPED)) reason = UNSUPPORTED_MOTION;
            else if (point.getMotionState() != State.STOPPED) {
                reason = switch (activity) {
                    case WALKING, HIKING, RUNNING -> locomotionExclusion(point, previous);
                    case BICYCLE -> !finite(point.getAscentInMeterBetweenPoints()) ? MISSING_ELEVATION
                            : mechanicalExclusion(point);
                    case ROWING, KAYAKING, STAND_UP_PADDLE -> waterExclusion(point, previous, activity, bodyWeightKg, duration);
                    default -> null;
                };
            }
            if (reason != null) {
                reasons.add(reason);
                if (positive(duration)) {
                    uncovered += duration;
                    if (reason == RECORDING_GAP) recordingGaps += duration;
                    if (reason == SPARSE_SAMPLING) sparseSampling += duration;
                } else unknownDuration = true;
                continue;
            }
            double intervalActive = activeKcal(point, previous, activity, bodyWeightKg, duration);
            covered += duration;
            active += intervalActive;
            total += intervalActive + metKcal(RESTING_MET, bodyWeightKg, duration);
        }
        result.setCoveredSeconds(covered);
        result.setUncoveredSeconds(uncovered);
        result.setRecordingGapSeconds(recordingGaps);
        result.setSparseSamplingSeconds(sparseSampling);
        result.setUnknownDuration(unknownDuration);
        result.setExclusionReasons(List.copyOf(reasons));
        if (covered > 0) {
            result.setActiveKcal(active);
            result.setTotalKcal(total);
            result.setCoverage(reasons.isEmpty() ? MetabolicEnergy.Coverage.COMPLETE : MetabolicEnergy.Coverage.PARTIAL);
        } else if (reasons.isEmpty()) result.setExclusionReasons(List.of(INSUFFICIENT_DATA));
        return result;
    }

    public static MetabolicEnergy.Method methodFor(ACTIVITY_TYPE activity) {
        if (activity == null) return null;
        return switch (activity) {
            case WALKING, HIKING -> MetabolicEnergy.Method.MINETTI_WALKING;
            case RUNNING -> MetabolicEnergy.Method.MINETTI_RUNNING;
            case BICYCLE -> MetabolicEnergy.Method.ACSM_CYCLING;
            case MOUNTAIN_BIKING -> MetabolicEnergy.Method.COMPENDIUM_MOUNTAIN_BIKING;
            case ROWING -> MetabolicEnergy.Method.COMPENDIUM_ROWING;
            case KAYAKING -> MetabolicEnergy.Method.COMPENDIUM_KAYAKING;
            case STAND_UP_PADDLE -> MetabolicEnergy.Method.COMPENDIUM_PADDLE_BOARDING;
            case SKIING -> MetabolicEnergy.Method.COMPENDIUM_SKIING;
            case CAR -> MetabolicEnergy.Method.COMPENDIUM_CAR_PASSENGER;
            case MOTORBIKING -> MetabolicEnergy.Method.COMPENDIUM_MOTORCYCLING;
            case AIRPLANE, SUPER_SONIC -> MetabolicEnergy.Method.COMPENDIUM_SEATED_AIR_TRAVEL;
        };
    }

    private static double activeKcal(GpsTrackDataPoint point, GpsTrackDataPoint previous,
                                     ACTIVITY_TYPE activity, double mass, double seconds) {
        if (point.getMotionState() == State.STOPPED) return 0;
        return switch (activity) {
            case WALKING, HIKING, RUNNING -> locomotionKcal(point, previous, activity, mass);
            case BICYCLE -> cyclingKcal(point, mass, seconds);
            case MOUNTAIN_BIKING -> metKcal(MTB_MET - RESTING_MET, mass, seconds);
            case ROWING -> metKcal(ROWING_MET - RESTING_MET, mass, seconds);
            case KAYAKING -> metKcal(KAYAKING_MET - RESTING_MET, mass, seconds);
            case STAND_UP_PADDLE -> metKcal(PADDLE_BOARDING_MET - RESTING_MET, mass, seconds);
            case SKIING -> metKcal(SKIING_MET - RESTING_MET, mass, seconds);
            case CAR, AIRPLANE, SUPER_SONIC -> metKcal(PASSENGER_MET - RESTING_MET, mass, seconds);
            case MOTORBIKING -> metKcal(MOTORCYCLING_MET - RESTING_MET, mass, seconds);
        };
    }

    private static double locomotionKcal(GpsTrackDataPoint point, GpsTrackDataPoint previous,
                                         ACTIVITY_TYPE activity, double mass) {
        Double distance = point.getDistanceInMeterBetweenPoints();
        if (zeroDisplacement(point, previous)) return 0;
        double grade = locomotionGrade(point, previous);
        double cost = 0;
        for (double coefficient : activity == ACTIVITY_TYPE.RUNNING ? RUNNING_COST : WALKING_COST) {
            cost = cost * grade + coefficient;
        }
        // Stored distances are horizontal; the study's treadmill distance follows the inclined surface.
        double surfaceDistance = distance * Math.sqrt(1 + grade * grade);
        return Math.max(0, cost) * mass * surfaceDistance / JOULES_PER_KCAL;
    }

    private static MetabolicEnergy.ExclusionReason locomotionExclusion(GpsTrackDataPoint point, GpsTrackDataPoint previous) {
        if (zeroDisplacement(point, previous)) return null;
        Double distance = point.getDistanceInMeterBetweenPoints();
        if (!positive(distance)) return INSUFFICIENT_DISTANCE;
        if (!finite(point.getSlopePercentageInMovingWindow())) {
            if (distance < MIN_GRADE_FALLBACK_DISTANCE_M) return INSUFFICIENT_DISTANCE;
            if (!finite(point.getPointAltitude()) || !finite(previous.getPointAltitude())) return MISSING_ELEVATION;
        }
        // Keep the published -45% to +45% domain; do not extrapolate or clamp the fit.
        double grade = locomotionGrade(point, previous);
        return !Double.isFinite(grade) || Math.abs(grade) > MAX_MINETTI_GRADE ? GRADE_OUTSIDE_MODEL : null;
    }

    private static double locomotionGrade(GpsTrackDataPoint point, GpsTrackDataPoint previous) {
        Double slope = point.getSlopePercentageInMovingWindow();
        return finite(slope) ? slope / PERCENT
                : (point.getPointAltitude() - previous.getPointAltitude()) / point.getDistanceInMeterBetweenPoints();
    }

    private static boolean zeroDisplacement(GpsTrackDataPoint point, GpsTrackDataPoint previous) {
        return finite(point.getDistanceInMeterBetweenPoints())
               && Math.abs(point.getDistanceInMeterBetweenPoints()) <= ZERO_DISPLACEMENT_EPSILON_M
               && finite(point.getPointAltitude()) && finite(previous.getPointAltitude())
               && Math.abs(point.getPointAltitude() - previous.getPointAltitude()) <= ZERO_DISPLACEMENT_EPSILON_M;
    }

    private static boolean harmlessDuplicate(GpsTrackDataPoint point, GpsTrackDataPoint previous) {
        // A short geometric interval alone does not establish that it contains no movement.
        return point.getIntervalStatus() == GpsTrackDataPoint.IntervalStatus.GEOMETRY_ONLY
               && point.getPointTimestamp() != null && point.getPointTimestamp().equals(previous.getPointTimestamp())
               && (zeroDisplacement(point, previous)
                   || (point.getPointAltitude() == null && previous.getPointAltitude() == null
                       && finite(point.getDistanceInMeterBetweenPoints())
                       && Math.abs(point.getDistanceInMeterBetweenPoints()) <= ZERO_DISPLACEMENT_EPSILON_M));
    }

    private static MetabolicEnergy.ExclusionReason mechanicalExclusion(GpsTrackDataPoint point) {
        return !finite(point.getEnergyTotalWh()) || point.getEnergyTotalWh() < 0
               || Boolean.TRUE.equals(point.getEnergyLimited()) ? UNSUPPORTED_MECHANICAL_POWER : null;
    }

    private static MetabolicEnergy.ExclusionReason waterExclusion(GpsTrackDataPoint point, GpsTrackDataPoint previous,
                                                                  ACTIVITY_TYPE activity, double mass, double seconds) {
        var unsupported = mechanicalExclusion(point);
        if (unsupported != null) return unsupported;
        double grossKcal = activeKcal(point, previous, activity, mass, seconds) + metKcal(RESTING_MET, mass, seconds);
        // Coarse MET and drag models are independently estimated. Do not publish physically inconsistent pairs.
        return point.getEnergyTotalWh() * JOULES_PER_WH > grossKcal * JOULES_PER_KCAL + ENERGY_COMPARISON_TOLERANCE_J
                ? INCONSISTENT_ENERGY_ESTIMATES : null;
    }

    private static double cyclingKcal(GpsTrackDataPoint point, double mass, double seconds) {
        Double mechanicalWh = point.getEnergyTotalWh();
        if (!finite(mechanicalWh) || mechanicalWh < 0 || Boolean.TRUE.equals(point.getEnergyLimited())) return Double.NaN;
        double watts = mechanicalWh * JOULES_PER_WH / seconds;
        // ACSM includes resting + unloaded pedaling. Subtract only the resting term for active calories.
        double oxygenAboveRest = ACSM_UNLOADED_OXYGEN - ML_OXYGEN_PER_MET_KG_MINUTE
                                 + ACSM_OXYGEN_PER_WATT * watts / mass;
        return oxygenAboveRest * mass * KCAL_PER_ML_OXYGEN * seconds / SECONDS_PER_MINUTE;
    }

    private static double metKcal(double met, double mass, double seconds) {
        return met * ML_OXYGEN_PER_MET_KG_MINUTE * mass * KCAL_PER_ML_OXYGEN * seconds / SECONDS_PER_MINUTE;
    }

    private static boolean positive(Double value) {
        return finite(value) && value > 0;
    }

    private static boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }
}
