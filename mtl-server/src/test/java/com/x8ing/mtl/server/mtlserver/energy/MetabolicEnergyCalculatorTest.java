package com.x8ing.mtl.server.mtlserver.energy;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.ACTIVITY_TYPE;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MetabolicEnergyCalculatorTest {
    @ParameterizedTest
    @EnumSource(ACTIVITY_TYPE.class)
    void everyActivityHasAnExplicitHumanModel(ACTIVITY_TYPE activity) {
        var result = calculate(activity, 0);
        assertThat(result.getMethod()).isNotNull();
        assertThat(result.getActiveKcal()).isPositive();
        assertThat(result.getTotalKcal()).isGreaterThan(result.getActiveKcal());
        assertThat(result.getCoverage()).isEqualTo(MetabolicEnergy.Coverage.COMPLETE);
        assertThat(result.getMethodDescription()).isNotBlank();
    }

    @Test
    void flatLocomotionMatchesPublishedNetCostAndAddsRestExactlyOnce() {
        // Minetti net cost: walking 2.5 and running 3.6 J/(kg m); 75 kg, 1 km.
        var walking = calculate(ACTIVITY_TYPE.WALKING, 0);
        var running = calculate(ACTIVITY_TYPE.RUNNING, 0);
        assertThat(walking.getActiveKcal()).isCloseTo(44.8135755, within(0.00001));
        assertThat(running.getActiveKcal()).isCloseTo(64.5315488, within(0.00001));
        // Ten minutes at standard 1 MET: 13.125 kcal.
        assertThat(walking.getTotalKcal() - walking.getActiveKcal()).isCloseTo(13.125, within(1e-9));
    }

    @ParameterizedTest
    @EnumSource(value = ACTIVITY_TYPE.class, names = {"WALKING", "HIKING", "RUNNING"})
    void descentsRemainPositiveAndSteepDescentCostsMoreThanModerateDescent(ACTIVITY_TYPE activity) {
        double level = calculate(activity, 0).getActiveKcal();
        double gentle = calculate(activity, -0.1).getActiveKcal();
        double steep = calculate(activity, -0.4).getActiveKcal();
        assertThat(gentle).isPositive().isLessThan(level);
        assertThat(steep).isGreaterThan(gentle);
        assertThat(calculate(activity, 0.2).getActiveKcal()).isGreaterThan(level);
    }

    @Test
    void cyclingMatchesAcsmAt100WattsAndCoastingStillHasAMetabolicCost() {
        var end = point(1000, 600, 0);
        end.setEnergyTotalWh(100.0 * 600 / 3600);
        var result = MetabolicEnergyCalculator.calculate(List.of(start(), end), ACTIVITY_TYPE.BICYCLE, 75);
        // (7 + 10.8*100/75) ml/kg/min * 75 kg * .005 kcal/ml * 10 min = 80.25 kcal gross.
        assertThat(result.getTotalKcal()).isCloseTo(80.25, within(1e-9));
        assertThat(result.getActiveKcal()).isCloseTo(67.125, within(1e-9));
        end.setEnergyTotalWh(0.0);
        assertThat(MetabolicEnergyCalculator.calculate(List.of(start(), end), ACTIVITY_TYPE.BICYCLE, 75).getActiveKcal())
                .isCloseTo(13.125, within(1e-9));
        assertThat(end.getEnergyTotalWh()).isZero();
    }

    @Test
    void vehicleEnergyNeverBecomesHumanCalories() {
        var end = point(1000, 600, 0);
        end.setEnergyTotalWh(1_000_000.0);
        for (var activity : List.of(ACTIVITY_TYPE.CAR, ACTIVITY_TYPE.AIRPLANE, ACTIVITY_TYPE.SUPER_SONIC)) {
            var result = MetabolicEnergyCalculator.calculate(List.of(start(), end), activity, 75);
            assertThat(result.getTotalKcal()).isCloseTo(17.0625, within(1e-9));
        }
    }

    @Test
    void stoppedIntervalsAddOnlyRestEvenWithPositionJitter() {
        var end = point(8, 600, 0.2);
        end.setMotionState(State.STOPPED);
        var result = MetabolicEnergyCalculator.calculate(List.of(start(), end), ACTIVITY_TYPE.HIKING, 75);
        assertThat(result.getActiveKcal()).isZero();
        assertThat(result.getTotalKcal()).isCloseTo(13.125, within(1e-9));
    }

    @Test
    void gapsAreExcludedRatherThanFilledAndUnknownDurationStillMarksPartial() {
        var valid = point(1000, 600, 0);
        var gap = point(1000, 300, 0);
        gap.setIntervalStatus(GpsTrackDataPoint.IntervalStatus.UNKNOWN);
        var noTime = point(1000, 600, 0);
        noTime.setDurationBetweenPointsInSec(null);
        var result = MetabolicEnergyCalculator.calculate(List.of(start(), valid, gap, noTime), ACTIVITY_TYPE.RUNNING, 75);
        assertThat(result.getCoverage()).isEqualTo(MetabolicEnergy.Coverage.PARTIAL);
        assertThat(result.getCoveredSeconds()).isEqualTo(600);
        assertThat(result.getUncoveredSeconds()).isEqualTo(300);
        assertThat(result.getActiveKcal()).isEqualTo(calculate(ACTIVITY_TYPE.RUNNING, 0).getActiveKcal());
    }

    @Test
    void missingAltitudeOrOutOfDomainGradientIsUnavailableNotZero() {
        var end = point(1000, 600, 0);
        end.setPointAltitude(null);
        end.setSlopePercentageInMovingWindow(null);
        var result = MetabolicEnergyCalculator.calculate(List.of(start(), end), ACTIVITY_TYPE.HIKING, 75);
        assertThat(result.getActiveKcal()).isNull();
        assertThat(result.getCoverage()).isEqualTo(MetabolicEnergy.Coverage.UNAVAILABLE);
        assertThat(calculate(ACTIVITY_TYPE.HIKING, 0.6).getActiveKcal()).isNull();
    }

    @Test
    void invalidWeightAndUnclassifiedTracksAreUnavailable() {
        assertThat(MetabolicEnergyCalculator.calculate(List.of(start(), point(1000, 600, 0)), ACTIVITY_TYPE.WALKING, Double.NaN)
                .getActiveKcal()).isNull();
        assertThat(calculate(null, 0).getActiveKcal()).isNull();
    }

    @Test
    void bodyWeightScalesLocomotionAndActivityEstimates() {
        var points = List.of(start(), point(1000, 600, 0));
        points.get(1).setEnergyTotalWh(0.1);
        for (var activity : List.of(ACTIVITY_TYPE.RUNNING, ACTIVITY_TYPE.KAYAKING)) {
            var lighter = MetabolicEnergyCalculator.calculate(points, activity, 60);
            var heavier = MetabolicEnergyCalculator.calculate(points, activity, 90);
            assertThat(heavier.getActiveKcal()).isCloseTo(lighter.getActiveKcal() * 1.5, within(1e-9));
        }
    }

    @Test
    void implausibleMechanicalPowerIsExcludedFromCyclingEstimate() {
        var end = point(1000, 600, 0);
        end.setEnergyLimited(true);
        assertThat(MetabolicEnergyCalculator.calculate(List.of(start(), end), ACTIVITY_TYPE.BICYCLE, 75).getActiveKcal()).isNull();
    }

    @Test
    void validSmoothedSlopeDoesNotRequireEndpointAltitude() {
        var end = point(1000, 600, .1);
        end.setPointAltitude(null);
        var first = start();
        first.setPointAltitude(null);
        assertThat(MetabolicEnergyCalculator.calculate(List.of(first, end), ACTIVITY_TYPE.HIKING, 75).getActiveKcal())
                .isEqualTo(calculate(ACTIVITY_TYPE.HIKING, .1).getActiveKcal());
    }

    @Test
    void fallbackNeedsUsableDistanceAndElevationAndKeepsPublishedBoundaries() {
        for (double grade : List.of(-.45, .45)) {
            var end = point(1000, 600, grade);
            end.setSlopePercentageInMovingWindow(null);
            assertThat(MetabolicEnergyCalculator.calculate(List.of(start(), end), ACTIVITY_TYPE.HIKING, 75).getActiveKcal())
                    .isEqualTo(calculate(ACTIVITY_TYPE.HIKING, grade).getActiveKcal());
        }
        for (double grade : List.of(-.45001, .45001)) {
            assertThat(calculate(ACTIVITY_TYPE.HIKING, grade).getExclusionReasons())
                    .containsExactly(MetabolicEnergy.ExclusionReason.GRADE_OUTSIDE_MODEL);
        }
        var end = point(.01, 10, 0);
        end.setSlopePercentageInMovingWindow(null);
        assertThat(MetabolicEnergyCalculator.calculate(List.of(start(), end), ACTIVITY_TYPE.HIKING, 75).getExclusionReasons())
                .containsExactly(MetabolicEnergy.ExclusionReason.INSUFFICIENT_DISTANCE);
        end.setDistanceInMeterBetweenPoints(0.0);
        assertThat(MetabolicEnergyCalculator.calculate(List.of(start(), end), ACTIVITY_TYPE.HIKING, 75).getActiveKcal()).isZero();
        end.setPointAltitude(101.0);
        assertThat(MetabolicEnergyCalculator.calculate(List.of(start(), end), ACTIVITY_TYPE.HIKING, 75).getActiveKcal()).isNull();
    }

    @Test
    void exactDuplicateIsHarmlessButNearbyOrVerticalUntimedMovementIsPartial() {
        var first = start();
        first.setPointTimestamp(new java.util.Date(0));
        var duplicate = point(0, 0, 0);
        duplicate.setPointTimestamp(new java.util.Date(0));
        duplicate.setIntervalStatus(GpsTrackDataPoint.IntervalStatus.GEOMETRY_ONLY);
        var moving = point(1000, 600, 0);
        var points = List.of(first, duplicate, moving);
        assertThat(MetabolicEnergyCalculator.calculate(points, ACTIVITY_TYPE.RUNNING, 75).getCoverage())
                .isEqualTo(MetabolicEnergy.Coverage.COMPLETE);
        for (double displacement : List.of(.01, 4.0)) {
            duplicate.setDistanceInMeterBetweenPoints(displacement);
            var result = MetabolicEnergyCalculator.calculate(points, ACTIVITY_TYPE.RUNNING, 75);
            assertThat(result.getCoverage()).isEqualTo(MetabolicEnergy.Coverage.PARTIAL);
            assertThat(result.getUnknownDuration()).isTrue();
            assertThat(result.getUncoveredSeconds()).isZero();
        }
        duplicate.setDistanceInMeterBetweenPoints(0.0);
        duplicate.setPointAltitude(100.01);
        assertThat(MetabolicEnergyCalculator.calculate(points, ACTIVITY_TYPE.RUNNING, 75).getCoverage())
                .isEqualTo(MetabolicEnergy.Coverage.PARTIAL);
    }

    @Test
    void recordingBreaksAndSparseSamplingAreReportedSeparately() {
        var moving = point(100, 30, 0);
        var gap = point(100, 120, 0);
        gap.setMotionState(State.UNKNOWN);
        gap.setMotionReason(com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.Reason.RECORDING_GAP);
        var sparse = point(100, 75, 0);
        sparse.setMotionState(State.UNKNOWN);
        sparse.setMotionReason(com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer.Reason.SPARSE_SUPPORT);
        var result = MetabolicEnergyCalculator.calculate(List.of(start(), moving, gap, sparse), ACTIVITY_TYPE.RUNNING, 75);
        assertThat(result.getCoveredSeconds()).isEqualTo(30);
        assertThat(result.getUncoveredSeconds()).isEqualTo(195);
        assertThat(result.getRecordingGapSeconds()).isEqualTo(120);
        assertThat(result.getSparseSamplingSeconds()).isEqualTo(75);
        assertThat(result.getUnknownDuration()).isFalse();
        assertThat(result.getExclusionReasons()).containsExactly(MetabolicEnergy.ExclusionReason.RECORDING_GAP,
                MetabolicEnergy.ExclusionReason.SPARSE_SAMPLING);
    }

    @Test
    void nullPersistedReasonsDoNotTurnAnAbsentEstimateIntoAnEmbeddable() {
        var converter = new MetabolicEnergy.ExclusionReasonsConverter();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        var reasons = List.of(MetabolicEnergy.ExclusionReason.RECORDING_GAP, MetabolicEnergy.ExclusionReason.MISSING_TIMING);
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(reasons))).isEqualTo(reasons);
    }

    @Test
    void cyclingRequiresAcceptedElevationSupportAndKeepsStoppedRest() {
        var supported = point(100, 30, 0);
        var missing = point(100, 30, 0);
        missing.setAscentInMeterBetweenPoints(null);
        var stopped = point(0, 30, 0);
        stopped.setMotionState(State.STOPPED);
        stopped.setAscentInMeterBetweenPoints(null);
        var result = MetabolicEnergyCalculator.calculate(List.of(start(), supported, missing, stopped), ACTIVITY_TYPE.BICYCLE, 75);
        assertThat(result.getCoverage()).isEqualTo(MetabolicEnergy.Coverage.PARTIAL);
        assertThat(result.getCoveredSeconds()).isEqualTo(60);
        assertThat(result.getUncoveredSeconds()).isEqualTo(30);
        assertThat(result.getExclusionReasons()).containsExactly(MetabolicEnergy.ExclusionReason.MISSING_ELEVATION);
        assertThat(MetabolicEnergyCalculator.calculate(List.of(start(), missing), ACTIVITY_TYPE.BICYCLE, 75).getActiveKcal()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = ACTIVITY_TYPE.class, names = {"ROWING", "KAYAKING", "STAND_UP_PADDLE"})
    void waterEstimatesCannotReportMoreMechanicalWorkThanGrossHumanEnergy(ACTIVITY_TYPE activity) {
        var supported = point(100, 30, 0);
        supported.setEnergyTotalWh(0.1);
        var impossible = point(100, 30, 0);
        impossible.setEnergyTotalWh(100.0);
        var result = MetabolicEnergyCalculator.calculate(List.of(start(), supported, impossible), activity, 75);
        assertThat(result.getCoverage()).isEqualTo(MetabolicEnergy.Coverage.PARTIAL);
        assertThat(result.getCoveredSeconds()).isEqualTo(30);
        assertThat(result.getUncoveredSeconds()).isEqualTo(30);
        assertThat(result.getExclusionReasons()).containsExactly(MetabolicEnergy.ExclusionReason.INCONSISTENT_ENERGY_ESTIMATES);
        assertThat(result.getTotalKcal() * 4184).isGreaterThanOrEqualTo(supported.getEnergyTotalWh() * 3600);
        assertThat(MetabolicEnergyCalculator.calculate(List.of(start(), impossible), activity, 75).getTotalKcal()).isNull();
        impossible.setEnergyLimited(true);
        assertThat(MetabolicEnergyCalculator.calculate(List.of(start(), impossible), activity, 75).getExclusionReasons())
                .containsExactly(MetabolicEnergy.ExclusionReason.UNSUPPORTED_MECHANICAL_POWER);
    }

    private static MetabolicEnergy calculate(ACTIVITY_TYPE activity, double grade) {
        return MetabolicEnergyCalculator.calculate(List.of(start(), point(1000, 600, grade)), activity, 75);
    }

    private static GpsTrackDataPoint start() {
        return point(0, 0, 0);
    }

    private static GpsTrackDataPoint point(double distance, double duration, double grade) {
        var point = new GpsTrackDataPoint();
        point.setPointAltitude(100.0 + distance * grade);
        point.setAscentInMeterBetweenPoints(distance * grade);
        point.setSlopePercentageInMovingWindow(grade * 100);
        point.setDistanceInMeterBetweenPoints(distance);
        point.setDurationBetweenPointsInSec(duration);
        point.setIntervalStatus(GpsTrackDataPoint.IntervalStatus.OBSERVED);
        point.setMotionState(State.MOVING);
        point.setEnergyTotalWh(0.1);
        return point;
    }
}
