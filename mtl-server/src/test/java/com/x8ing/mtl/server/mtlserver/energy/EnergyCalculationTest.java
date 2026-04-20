package com.x8ing.mtl.server.mtlserver.energy;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.energy.impl.BicycleEnergyCalculator;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the energy calculation framework.
 * Focuses on the three review-identified fixes:
 * 1. Net work calculation (gravity offsets drag on descents)
 * 2. Power division-by-zero and clamping protection
 * 3. Speed capping for GPS artifacts in aero drag
 */
class EnergyCalculationTest {

    private final BicycleEnergyCalculator bicycleCalculator = new BicycleEnergyCalculator();
    private final EnergyParameters defaultParams = EnergyParameters.builder().riderWeightKg(75).build();

    // ─── Fix 1: Net work / totalPositiveJoules ───────────────────────────

    @Test
    void totalPositiveJoules_descentCoasting_shouldBeZero() {
        // Steep downhill: gravity provides -500 J, drag needs 100 J, rolling 50 J, kinetic 20 J
        // Net = -500 + 100 + 50 + 20 = -330 → should clamp to 0
        EnergyComponents ec = EnergyComponents.builder()
                .gravitationalJoules(-500)
                .aeroDragJoules(100)
                .rollingResistanceJoules(50)
                .kineticJoules(20)
                .build();

        assertEquals(0, ec.totalPositiveJoules(), "Downhill coasting should require 0 pedaling work");
    }

    @Test
    void totalPositiveJoules_uphill_shouldSumAll() {
        // Uphill: gravity 300 J, drag 80 J, rolling 40 J, kinetic 10 J
        // Net = 300 + 80 + 40 + 10 = 430
        EnergyComponents ec = EnergyComponents.builder()
                .gravitationalJoules(300)
                .aeroDragJoules(80)
                .rollingResistanceJoules(40)
                .kineticJoules(10)
                .build();

        assertEquals(430, ec.totalPositiveJoules(), 0.01);
    }

    @Test
    void totalPositiveJoules_flatWithDeceleration_netWorkCorrect() {
        // Flat + deceleration: gravity 0, drag 50 J, rolling 30 J, kinetic -100 J (braking)
        // Net = 0 + 50 + 30 + (-100) = -20 → clamp to 0
        EnergyComponents ec = EnergyComponents.builder()
                .gravitationalJoules(0)
                .aeroDragJoules(50)
                .rollingResistanceJoules(30)
                .kineticJoules(-100)
                .build();

        assertEquals(0, ec.totalPositiveJoules(), "Braking segment should be 0 work");
    }

    @Test
    void totalPositiveJoules_mildDescentPedalingRequired() {
        // Mild descent: gravity -50 J, but drag 80 J + rolling 30 J + kinetic 10 J
        // Net = -50 + 80 + 30 + 10 = 70 → rider still needs to work
        EnergyComponents ec = EnergyComponents.builder()
                .gravitationalJoules(-50)
                .aeroDragJoules(80)
                .rollingResistanceJoules(30)
                .kineticJoules(10)
                .build();

        assertEquals(70, ec.totalPositiveJoules(), 0.01, "Mild descent should still require some work");
    }

    // ─── Fix 2: Power calculation edge cases ─────────────────────────────

    @Test
    void power_zeroDuration_shouldBeZeroWatts() {
        // Two points at the exact same timestamp (Δt=0)
        GpsTrackDataPoint p1 = makePoint(0, 0, 100.0, 0.0, 0.0);
        GpsTrackDataPoint p2 = makePoint(0, 1, 110.0, 100.0, 0.0); // Δt=0

        List<GpsTrackDataPoint> points = List.of(p1, p2);
        EnergyCalculatorFactory factory = buildFactory();
        EnergyService service = new EnergyService(factory, null, null, null, null);

        service.calculateAndPopulatePoints(points, GpsTrack.ACTIVITY_TYPE.BICYCLE, defaultParams);

        assertEquals(0.0, p2.getPowerWatts(), "Zero-duration segment should produce 0 W");
    }

    @Test
    void power_tinyDuration_shouldBeZeroWatts() {
        // Duration of 0.5 seconds — below MIN_SEGMENT_DURATION_SEC (1.0)
        GpsTrackDataPoint p1 = makePoint(0, 0, 100.0, 0.0, 0.0);
        GpsTrackDataPoint p2 = makePoint(500, 1, 110.0, 100.0, 0.5);

        List<GpsTrackDataPoint> points = List.of(p1, p2);
        EnergyCalculatorFactory factory = buildFactory();
        EnergyService service = new EnergyService(factory, null, null, null, null);

        service.calculateAndPopulatePoints(points, GpsTrack.ACTIVITY_TYPE.BICYCLE, defaultParams);

        assertEquals(0.0, p2.getPowerWatts(), "Sub-second segment should produce 0 W");
    }

    @Test
    void power_normalSegment_shouldBeCapped() {
        // Create a scenario that would produce very high power without capping
        // Steep uphill + short duration → extreme power
        GpsTrackDataPoint p1 = makePoint(0, 0, 0.0, 0.0, 0.0);
        GpsTrackDataPoint p2 = makePoint(1000, 1, 100.0, 50.0, 1.0);

        List<GpsTrackDataPoint> points = List.of(p1, p2);
        EnergyCalculatorFactory factory = buildFactory();
        EnergyService service = new EnergyService(factory, null, null, null, null);

        service.calculateAndPopulatePoints(points, GpsTrack.ACTIVITY_TYPE.BICYCLE, defaultParams);

        assertTrue(p2.getPowerWatts() <= 2500.0, "Power should be capped at 2500 W");
    }

    // ─── Fix 3: Speed capping in aero drag ───────────────────────────────

    @Test
    void aeroDrag_extremeSpeed_shouldBeCapped() {
        // 200 km/h GPS artifact ≈ 55.6 m/s → should be clamped to MAX_SPEED_MPS (42 m/s ≈ 151 km/h)
        double uncappedDrag = 0.5 * 0.9 * 0.5 * 1.225 * 55.6 * 55.6 * 100;
        double cappedDrag = bicycleCalculator.aeroDragEnergy(0.9, 0.5, 1.225, 55.6, 100);
        double expectedCapped = 0.5 * 0.9 * 0.5 * 1.225 * 42.0 * 42.0 * 100;

        assertEquals(expectedCapped, cappedDrag, 0.1, "Drag should use capped speed");
        assertTrue(cappedDrag < uncappedDrag, "Capped drag should be less than uncapped");
    }

    @Test
    void aeroDrag_normalSpeed_shouldNotBeCapped() {
        // 30 km/h = 8.33 m/s — well below cap
        double result = bicycleCalculator.aeroDragEnergy(0.9, 0.5, 1.225, 8.33, 100);
        double expected = 0.5 * 0.9 * 0.5 * 1.225 * 8.33 * 8.33 * 100;

        assertEquals(expected, result, 0.1, "Normal speed should not be capped");
    }

    // ─── Integration: descent should produce 0 cumulative energy ─────────

    @Test
    void descentTrack_cumulativeEnergy_shouldNotIncrease() {
        // 5 points going steeply downhill: -20m altitude per point, 100m distance, 10s each
        List<GpsTrackDataPoint> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            GpsTrackDataPoint p = makePoint(i * 10_000L, i, 500.0 - (i * 20.0), 100.0, 10.0);
            // Set speed for smoothed calculation
            p.setSpeedInKmhMovingWindow(36.0); // 10 m/s
            points.add(p);
        }

        EnergyCalculatorFactory factory = buildFactory();
        EnergyService service = new EnergyService(factory, null, null, null, null);

        service.calculateAndPopulatePoints(points, GpsTrack.ACTIVITY_TYPE.BICYCLE, defaultParams);

        // On a steep descent, the gravity release should more than offset drag+rolling
        // so cumulative energy should remain at 0
        for (int i = 1; i < points.size(); i++) {
            assertEquals(0.0, points.get(i).getEnergyTotalWh(), 0.001,
                    "Steep descent segment " + i + " should require 0 work");
        }
        assertEquals(0.0, points.get(points.size() - 1).getEnergyCumulativeWh(), 0.01,
                "Cumulative energy on pure descent should be 0");
    }

    // ─── Denoising impact: noisy GPS produces extreme power ───────────

    @Test
    void noisyElevation_withoutDenoising_producesExtremePower() {
        // Demonstrate the root cause: a +80m elevation spike over 2 seconds
        // produces unrealistic power that must be clamped
        GpsTrackDataPoint p1 = makePoint(0, 0, 500.0, 0.0, 0.0);
        GpsTrackDataPoint p2 = makePoint(2000, 1, 580.0, 10.0, 2.0); // +80m spike
        p2.setSpeedInKmhMovingWindow(18.0);

        List<GpsTrackDataPoint> points = List.of(p1, p2);
        EnergyCalculatorFactory factory = buildFactory();
        EnergyService service = new EnergyService(factory, null, null, null, null);
        service.calculateAndPopulatePoints(points, GpsTrack.ACTIVITY_TYPE.BICYCLE, defaultParams);

        // Without denoising, power hits the 2500W cap (actual would be ~33kW)
        assertEquals(2500.0, p2.getPowerWatts(), 1.0,
                "Raw +80m spike in 2s should hit the 2500W power cap");
    }

    @Test
    void denoisedElevation_producesRealisticPower() {
        // Same scenario but after median denoising: the spike is gone,
        // altitude delta is ~2m instead of 80m
        GpsTrackDataPoint p1 = makePoint(0, 0, 500.0, 0.0, 0.0);
        GpsTrackDataPoint p2 = makePoint(2000, 1, 502.0, 10.0, 2.0); // denoised: +2m
        p2.setSpeedInKmhMovingWindow(18.0);

        List<GpsTrackDataPoint> points = List.of(p1, p2);
        EnergyCalculatorFactory factory = buildFactory();
        EnergyService service = new EnergyService(factory, null, null, null, null);
        service.calculateAndPopulatePoints(points, GpsTrack.ACTIVITY_TYPE.BICYCLE, defaultParams);

        // With denoised data, power should be far below the 2500W cap.
        // Real power here includes aero drag and rolling resistance over 10m at 18km/h
        // plus a +2m gravity component — realistic for a short uphill MTB segment.
        assertTrue(p2.getPowerWatts() < 2500,
                "Denoised +2m should not hit the 2500W cap, got " + p2.getPowerWatts() + "W");
        // Crucially, power should be MUCH less than the raw spike scenario (which hits cap)
        assertTrue(p2.getPowerWatts() < 1500,
                "Denoised +2m in 2s should produce manageable power, got " + p2.getPowerWatts() + "W");
        assertTrue(p2.getPowerWatts() > 0,
                "Should still produce some positive power on a slight climb");
    }

    // ─── Fix 4: Kinetic energy should use smoothed speed ────────────────

    @Test
    void kineticEnergy_shouldUseSmoothedSpeed_notInstantaneous() {
        // Simulate GPS X/Y jitter: instantaneous speed jumps from 5 m/s to 15 m/s (noise),
        // but moving window reports a steady 18 km/h (= 5 m/s) for both points.
        // Kinetic energy should see NO change (Δv ≈ 0), not a massive spike.
        GpsTrackDataPoint p1 = makePoint(0, 0, 500.0, 0.0, 0.0);
        p1.setSpeedInKmhMovingWindow(18.0);  // smoothed: 5 m/s

        GpsTrackDataPoint p2 = makePoint(2000, 1, 500.0, 30.0, 2.0);
        // Instantaneous: 30m / 2s = 15 m/s (GPS glitch), but smoothed says 18 km/h (5 m/s)
        p2.setSpeedInKmhMovingWindow(18.0);

        List<GpsTrackDataPoint> points = List.of(p1, p2);
        EnergyCalculatorFactory factory = buildFactory();
        EnergyService service = new EnergyService(factory, null, null, null, null);
        service.calculateAndPopulatePoints(points, GpsTrack.ACTIVITY_TYPE.BICYCLE, defaultParams);

        // With smoothed speed (both 5 m/s), kinetic energy change should be ~0 Wh
        // If instantaneous speed were used: ½·85·(15²−0²) = 9562 J = 2.66 Wh spike!
        double kineticWh = p2.getEnergyKineticWh();
        assertEquals(0.0, kineticWh, 0.01,
                "Kinetic energy should be ~0 when smoothed speed is constant, got " + kineticWh + " Wh");
    }

    @Test
    void kineticEnergy_gpsSpike_withoutMovingWindow_trackAverageFallbackPrevents() {
        // Same scenario but WITHOUT moving window data. Before the track-average fallback
        // was added, smoothedSpeedMps() returned 0 for p1 (no moving window, no distance/duration)
        // and 15 m/s for p2 (instantaneous), producing a ~2.66 Wh kinetic spike.
        // Now the track-average fallback fills in p1's speed as well (avg = 30m/2s = 15 m/s),
        // so Δv ≈ 0 and no spike is produced.
        GpsTrackDataPoint p1 = makePoint(0, 0, 500.0, 0.0, 0.0);
        GpsTrackDataPoint p2 = makePoint(2000, 1, 500.0, 30.0, 2.0);

        List<GpsTrackDataPoint> points = List.of(p1, p2);
        EnergyCalculatorFactory factory = buildFactory();
        EnergyService service = new EnergyService(factory, null, null, null, null);
        service.calculateAndPopulatePoints(points, GpsTrack.ACTIVITY_TYPE.BICYCLE, defaultParams);

        double kineticWh = p2.getEnergyKineticWh();
        assertEquals(0.0, kineticWh, 0.01,
                "Track-average fallback should keep kinetic energy ~0 even without moving-window data, got " + kineticWh + " Wh");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private GpsTrackDataPoint makePoint(long timestampOffsetMs, int index, double altitude,
                                        double distanceBetween, double durationBetween) {
        GpsTrackDataPoint p = new GpsTrackDataPoint();
        p.setPointTimestamp(Timestamp.from(Instant.ofEpochMilli(1700000000000L + timestampOffsetMs)));
        p.setPointIndex(index);
        p.setPointAltitude(altitude);
        p.setDistanceInMeterBetweenPoints(distanceBetween);
        p.setDurationBetweenPointsInSec(durationBetween);
        return p;
    }

    private EnergyCalculatorFactory buildFactory() {
        EnergyCalculatorFactory factory = new EnergyCalculatorFactory(
                List.of(new BicycleEnergyCalculator())
        );
        factory.init();
        return factory;
    }
}
