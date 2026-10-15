package com.x8ing.mtl.server.mtlserver.googlehealth.demo;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack.ACTIVITY_TYPE;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DemoPhysiologyTest {
    private static final Instant START = Instant.parse("2026-10-01T09:00:00Z");
    private static final long SEED = 20261003;

    @Test
    void sharedTimelineHasLagAndRecoveryAndIsIndependentOfInputOrder() {
        List<DemoPhysiology.Point> points = new ArrayList<>();
        for (int second = 0; second <= 900; second += 15) {
            boolean stopped = second <= 120 || second > 600;
            points.add(new DemoPhysiology.Point(START.plusSeconds(second), (double) second * 3, null, stopped ? 0.0 : 12.0, null, stopped ? "STOPPED" : "MOVING"));
        }
        var loads = DemoPhysiology.workload(SEED, "run", ACTIVITY_TYPE.RUNNING, points, 15);
        Map<Instant, DemoPhysiology.Workload> map = new HashMap<>();
        loads.forEach(load -> map.put(load.time(), load));
        var timeline = DemoTimeline.generate(SEED, LocalDate.of(2026, 10, 1), 15, true, map, List.of());
        int first = 9 * 60 * 60 / 15;
        assertThat(timeline.samples().get(first + 9).bpm()).isLessThan(timeline.samples().get(first + 20).bpm());
        assertThat(timeline.samples().get(first + 40).bpm()).isGreaterThan(timeline.samples().get(first + 60).bpm() + 20);
        Collections.reverse(points);
        Map<Instant, DemoPhysiology.Workload> reverse = new LinkedHashMap<>();
        loads.reversed().forEach(load -> reverse.put(load.time(), load));
        assertThat(DemoTimeline.generate(SEED, LocalDate.of(2026, 10, 1), 15, true, reverse, List.of())).isEqualTo(timeline);
        assertThat(timeline.samples()).allSatisfy(sample -> assertThat(sample.bpm()).isBetween(45, 182));
    }

    @Test
    void drivingHasNoExerciseStepsDistanceOrActiveCalories() {
        var loads = DemoPhysiology.workload(SEED, "car", ACTIVITY_TYPE.CAR, points(100, null), 15);
        assertThat(loads).allSatisfy(load -> {
            assertThat(load.steps()).isZero();
            assertThat(load.activeKcal()).isZero();
            assertThat(load.distanceMeters()).isZero();
            assertThat(load.heartRateOffset()).isLessThan(35);
        });
    }

    @Test
    void missingElevationIsFlatAndPowerSeparatesCyclingWorkFromCoasting() {
        var flat = DemoPhysiology.workload(SEED, "bike", ACTIVITY_TYPE.BICYCLE, points(25, null), 15);
        var zero = points(25, null).stream().map(p -> new DemoPhysiology.Point(p.time(), p.distanceMeters(), 0.0, p.speedKmh(), null, p.motionState())).toList();
        assertThat(DemoPhysiology.workload(SEED, "bike", ACTIVITY_TYPE.BICYCLE, zero, 15)).isEqualTo(flat);
        var coast = DemoPhysiology.workload(SEED, "bike", ACTIVITY_TYPE.BICYCLE, points(25, 0.0), 15);
        var work = DemoPhysiology.workload(SEED, "bike", ACTIVITY_TYPE.BICYCLE, points(25, 280.0), 15);
        assertThat(work.getLast().heartRateOffset()).isGreaterThan(coast.getLast().heartRateOffset() + 40);
        assertThat(work.stream().mapToDouble(DemoPhysiology.Workload::activeKcal).sum()).isGreaterThan(100);
    }

    @Test
    void recordingGapsHaveNoSamplesEvenInDetailedDailyData() {
        var input = List.of(new DemoPhysiology.Point(START, 0.0, null, 10.0, null, "MOVING"), new DemoPhysiology.Point(START.plusSeconds(30), 80.0, null, 10.0, null, "MOVING"),
                new DemoPhysiology.Point(START.plusSeconds(600), 90.0, null, 10.0, null, "MOVING"), new DemoPhysiology.Point(START.plusSeconds(630), 170.0, null, 10.0, null, "MOVING"));
        Map<Instant, DemoPhysiology.Workload> loads = new HashMap<>();
        DemoPhysiology.workload(SEED, "gaps", ACTIVITY_TYPE.RUNNING, input, 15).forEach(load -> loads.put(load.time(), load));
        var result = DemoTimeline.generate(SEED, LocalDate.of(2026, 10, 1), 15, true, loads, List.of(new DemoTimeline.Window(START, START.plusSeconds(630))));
        assertThat(result.samples().stream().filter(s -> !s.time().isBefore(START) && !s.time().isAfter(START.plusSeconds(630))))
                .extracting(DemoPhysiology.Sample::time).containsExactly(START, START.plusSeconds(15), START.plusSeconds(600), START.plusSeconds(615), START.plusSeconds(630));
    }

    @Test
    void workloadAndSelectionAreReproducibleAndRunningCaloriesArePlausible() {
        var loads = DemoPhysiology.workload(SEED, "a", ACTIVITY_TYPE.RUNNING, points(11, null), 15);
        assertThat(DemoPhysiology.workload(SEED, "a", ACTIVITY_TYPE.RUNNING, points(11, null), 15)).isEqualTo(loads);
        assertThat(DemoPhysiology.workload(SEED, "b", ACTIVITY_TYPE.RUNNING, points(11, null), 15)).isNotEqualTo(loads);
        assertThat(loads.stream().mapToDouble(DemoPhysiology.Workload::activeKcal).sum()).isBetween(110.0, 135.0);
        assertThat(DemoPhysiology.day(SEED, LocalDate.of(2026, 10, 1))).isNotEqualTo(DemoPhysiology.day(SEED, LocalDate.of(2026, 10, 2)));
        assertThat(DemoPhysiology.selected(SEED, "a", 0)).isFalse();
        assertThat(DemoPhysiology.selected(SEED, "a", 100)).isTrue();
    }

    @Test
    void denseGpsIntervalsKeepAllActivityAndNonDivisorSampleSpacingUsesTheSharedGrid() {
        List<DemoPhysiology.Point> points = new ArrayList<>();
        for (int second = 0; second <= 600; second++)
            points.add(new DemoPhysiology.Point(START.plusSeconds(second), second * 10 / 3.6, null, 10.0, null, "MOVING"));
        Map<Instant, DemoPhysiology.Workload> loads = new HashMap<>();
        DemoPhysiology.workload(SEED, "dense", ACTIVITY_TYPE.RUNNING, points, 7).forEach(load -> loads.put(load.time(), load));
        var result = DemoTimeline.generate(SEED, LocalDate.of(2026, 10, 1), 7, false, loads, List.of());
        assertThat(result.steps()).isCloseTo(1750.0, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(result.distanceMeters()).isCloseTo(600 * 10 / 3.6, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(result.samples()).extracting(DemoPhysiology.Sample::time).containsExactlyElementsOf(loads.keySet().stream().sorted().toList());
        assertThat(result.samples().stream().mapToInt(DemoPhysiology.Sample::bpm).max().orElseThrow()).isGreaterThan(100);
    }

    private static List<DemoPhysiology.Point> points(double speed, Double watts) {
        List<DemoPhysiology.Point> points = new ArrayList<>();
        for (int second = 0; second <= 600; second += 15)
            points.add(new DemoPhysiology.Point(START.plusSeconds(second), second * speed / 3.6, null, speed, watts, "MOVING"));
        return points;
    }
}
