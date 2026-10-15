package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import org.apache.commons.math3.filter.DefaultMeasurementModel;
import org.apache.commons.math3.filter.DefaultProcessModel;
import org.apache.commons.math3.filter.KalmanFilter;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fully synthetic correctness checks through the production importer and canonical calculations.
 */
class GpsPipelineReliabilityTest {
    static final double T = 1_700_000_000;

    static Coordinate c(double x, double y, double z, double t) {
        return new CoordinateXYZM(8 + x / 76000.0, 47 + y / 111000.0, z, Double.isNaN(t) ? t : T + t);
    }

    static List<Coordinate> straight(int n, double step, double dt) {
        List<Coordinate> a = new ArrayList<>();
        for (int i = 0; i < n; i++) a.add(c(i * step, 0, 500, i * dt));
        return a;
    }

    static String segment(List<Coordinate> a) {
        StringBuilder b = new StringBuilder("<trkseg>");
        for (Coordinate c : a) {
            b.append(String.format(Locale.ROOT, "<trkpt lat=\"%.10f\" lon=\"%.10f\">", c.y, c.x));
            if (!Double.isNaN(c.getZ())) b.append("<ele>").append(c.getZ()).append("</ele>");
            if (!Double.isNaN(c.getM())) b.append("<time>").append(Instant.ofEpochMilli(Math.round(c.getM() * 1000))).append("</time>");
            b.append("</trkpt>");
        }
        return b.append("</trkseg>").toString();
    }

    static List<GPXReader.LoadResult> read(String tracks) {
        IndexedFile f = new IndexedFile();
        f.setName("synthetic-audit.gpx");
        f.setPath("/synthetic-audit");
        return new GPXReader().importGpxXml(f, "<gpx version=\"1.1\" creator=\"MTL Explorer synthetic audit\" xmlns=\"http://www.topografix.com/GPX/1/1\">" + tracks + "</gpx>");
    }

    static GPXReader.LoadResult read(List<Coordinate> a) {
        return read("<trk>" + segment(a) + "</trk>").getFirst();
    }

    static LineString line(List<Coordinate> a) {
        return new LineString(new CoordinateArraySequence(a.toArray(Coordinate[]::new)), new GeometryFactory(new PrecisionModel(), 4326));
    }

    @SuppressWarnings("unchecked")
    static List<GpsTrackDataPoint> metrics(LineString l) throws Exception {
        return metrics(l, List.of());
    }

    @SuppressWarnings("unchecked")
    static List<GpsTrackDataPoint> metrics(LineString l, List<Integer> breaks) throws Exception {
        Method m = GPXStoreService.class.getDeclaredMethod("calculateBetweenPointsMetrics", GpsTrackData.class, List.class, int.class);
        m.setAccessible(true);
        List<GpsTrackDataPoint> p = (List<GpsTrackDataPoint>) m.invoke(null, GpsTrackData.builder().id(1L).track(l).recordingBreakBeforePointIndexes(breaks).build(), new ArrayList<GpsTrackDataPoint>(), 90);
        GPXStoreService.calculateMovingWindowStats(90, p);
        return p;
    }

    static LineString smooth(GPXReader.LoadResult r) {
        return GPXStoreService.smoothRecordingSpans(
                r.trackCleaned, r.recordingBreakBeforePointIndexes, new KalmanElevationSmoother(6, 10, 2, .5));
    }

    @Test
    void geometryJsonPreservesMissingAltitudeAndTimeAsNull() {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var data = GpsTrackData.builder().track(line(List.of(c(0, 0, Double.NaN, Double.NaN), c(10, 0, 500, 1)))).build();
        var json = mapper.valueToTree(data);
        assertTrue(json.get("track").get(0).get(2).isNull());
        assertTrue(json.get("track").get(0).get(3).isNull());
    }

    @Test
    void untimedNearbyGeometryIsRetainedWithoutRatesOrInventedDuration() throws Exception {
        var input = straight(20, 20, 1);
        input.forEach(point -> point.setM(Double.NaN));
        var loaded = read(input);
        var points = metrics(smooth(loaded), loaded.recordingBreakBeforePointIndexes);
        assertTrue(points.getLast().getDistanceInMeterSinceStart() > 370);
        assertEquals(GpsTrackDataPoint.IntervalStatus.GEOMETRY_ONLY, points.get(1).getIntervalStatus());
        assertTrue(points.stream().allMatch(point -> point.getSpeedInKmhMovingWindow() == null && point.getDurationBetweenPointsInSec() == null));
    }

    @Test
    void rejectedPositionCannotSupplyLaterAltitude() {
        var input = straight(30, 20, 1);
        input.set(12, c(100000, 0, 4000, 12));
        for (int i = 13; i < 30; i++) input.get(i).setZ(Double.NaN);
        var r = read(input);
        assertTrue(Arrays.stream(r.trackCleaned.getCoordinates()).noneMatch(p -> p.x > 9));
        assertTrue(Double.isNaN(smooth(r).getCoordinateN(r.trackCleaned.getNumPoints() - 1).getZ()));
        assertEquals(4000, r.trackRAW.getCoordinateN(12).getZ());
    }

    @Test
    void missingAltitudeStaysMissingThroughFullPipeline() throws Exception {
        var input = straight(30, 20, 1);
        input.forEach(p -> p.setZ(Double.NaN));
        var r = read(input);
        assertTrue(Arrays.stream(r.trackRAW.getCoordinates()).allMatch(p -> Double.isNaN(p.getZ())));
        assertTrue(metrics(smooth(r)).stream().allMatch(p -> p.getPointAltitude() == null));
    }

    @Test
    void untimedDistantFixCannotPoisonGeometry() throws Exception {
        var input = straight(30, 20, 1);
        input.set(12, c(100000, 0, 500, Double.NaN));
        var r = read(input);
        assertTrue(Arrays.stream(r.trackCleaned.getCoordinates()).noneMatch(p -> p.x > 9));
        assertTrue(metrics(smooth(r)).getLast().getDistanceInMeterSinceStart() < 650);
    }

    @Test
    void probationCannotSpendAccumulatedTimeCredit() throws Exception {
        var input = straight(55, 10, 1);
        for (int i = 15; i <= 25; i++) input.set(i, c(3150 + (i - 15) * 10, 0, 500, i));
        var r = read(input);
        assertTrue(Arrays.stream(r.trackCleaned.getCoordinates()).noneMatch(p -> p.x > 8.03));
        assertTrue(metrics(smooth(r)).getLast().getDistanceInMeterSinceStart() < 600);
    }

    @Test
    void recordingSectionsKeepBothPathsWithoutInventingBridge() throws Exception {
        var first = straight(15, 20, 1);
        List<Coordinate> second = new ArrayList<>();
        for (int i = 0; i < 25; i++) second.add(c(20000 + i * 20, 0, 500, 3600 + i));
        var r = read("<trk>" + segment(first) + segment(second) + "</trk>").getFirst();
        assertEquals(40, r.trackCleaned.getNumPoints());
        assertEquals(List.of(15), r.recordingBreakBeforePointIndexes);
        var points = metrics(smooth(r), r.recordingBreakBeforePointIndexes);
        assertEquals(GpsTrackDataPoint.IntervalStatus.UNKNOWN, points.get(15).getIntervalStatus());
        assertNull(points.get(15).getDistanceInMeterBetweenPoints());
        assertTrue(points.getLast().getDistanceInMeterSinceStart() < 800);
        Geometry observed = GpsTrackData.buildObservedPath(r.trackCleaned, r.recordingBreakBeforePointIndexes);
        assertEquals(2, observed.getNumGeometries());
    }

    @Test
    void oneClockResetKeepsRecordedTimesAndMarksItsTransitionUnsupported() throws Exception {
        var input = straight(15, 20, 1);
        for (int i = 15; i < 45; i++) input.add(c(i * 20, 0, 500, i - 120));
        var r = read(input);
        var points = metrics(smooth(r), r.recordingBreakBeforePointIndexes);
        assertEquals(45, points.size());
        assertFalse(r.gpsTrack.isTimingUnreliable());
        assertEquals(GpsTrackDataPoint.IntervalStatus.UNKNOWN, points.get(15).getIntervalStatus());
        assertTrue(points.stream().allMatch(p -> p.getDurationBetweenPointsInSec() == null || p.getDurationBetweenPointsInSec() > 0));
        assertEquals(GpsTrackDataPoint.IntervalStatus.OBSERVED, points.get(16).getIntervalStatus());
        assertTrue(points.getLast().getDurationSinceStart() > 0);
        assertTrue(points.stream().allMatch(p -> p.getPointTimestamp() != null));
    }

    @Test
    void isolatedAltitudeSpikeIsUnknownAndAddsNoAscent() throws Exception {
        var input = straight(31, 70, 5);
        input.get(15).setZ(5000);
        var r = read(input);
        var points = metrics(smooth(r));
        assertTrue(Double.isNaN(r.trackCleaned.getCoordinateN(15).getZ()));
        assertEquals(0, points.getLast().getAscentInMeterSinceStart(), 0.01);
        assertTrue(points.stream().filter(p -> p.getPointAltitude() != null).allMatch(p -> Math.abs(p.getPointAltitude() - 500) < 1));
    }

    @Test
    void alternatingHorizontalNoiseCannotTurnContinuousWalkingIntoStops() {
        List<Coordinate> input = new ArrayList<>();
        for (int i = 0; i <= 120; i++) input.add(c(i * 5, i % 2 == 0 ? 5 : -5, 500, i * 5));
        var r = read(input);
        assertEquals(0, r.stopRanges.size());
        assertEquals(121, r.trackCleaned.getNumPoints());
    }

    @Test
    void sparseSamplingHasMotionSupportAtEdgesAndInside() throws Exception {
        var points = metrics(line(straight(12, 600, 60)));
        assertTrue(points.stream().allMatch(p -> p.getSpeedInKmhMovingWindow() != null));
        assertTrue(points.stream().allMatch(p -> p.getSpeedInKmhMovingWindow() > 35 && p.getSpeedInKmhMovingWindow() < 37));
    }

    @Test
    void fractionalTimestampsRemainDistinct() throws Exception {
        var points = metrics(line(straight(31, 1, .1)));
        for (int i = 1; i < points.size(); i++) assertEquals(.1, points.get(i).getDurationBetweenPointsInSec(), 1e-8);
    }

    @Test
    void energyAndAscentShareNoisePolicy() throws Exception {
        var input = straight(101, 70, 5);
        for (int i = 0; i < input.size(); i++) input.get(i).setZ(500 + (i % 2) * 1.5);
        var points = metrics(line(input));
        var calculator = new com.x8ing.mtl.server.mtlserver.energy.impl.BicycleEnergyCalculator();
        var parameters = com.x8ing.mtl.server.mtlserver.energy.EnergyParameters.builder().build();
        double gravity = 0;
        for (int i = 1; i < points.size(); i++)
            gravity += Math.max(0, calculator.calculateBetweenPoints(points.get(i), points.get(i - 1), parameters).getGravitationalJoules());
        assertEquals(0, points.getLast().getAscentInMeterSinceStart());
        assertEquals(0, gravity);
    }

    @Test
    void smootherDoesNotCrossExplicitRecordingBoundary() {
        var first = straight(10, 10, 1);
        var all = new ArrayList<>(first);
        for (int i = 10; i < 20; i++) all.add(c(100000 + i * 10, 0, 4000, i));
        var algorithm = new KalmanElevationSmoother(6, 10, 2, .5);
        var separate = algorithm.denoise(line(first));
        var joined = GPXStoreService.smoothRecordingSpans(line(all), List.of(10), algorithm);
        for (int i = 0; i < first.size(); i++) assertEquals(separate.getCoordinateN(i).getX(), joined.getCoordinateN(i).getX(), 1e-12);
    }

    @Test
    void mixedCadenceAndMissingMeasurementsMatchIndependentKalmanReference() {
        double[] values = {0, 1, Double.NaN, 4, 7, 7, 15};
        double[] times = {0, .25, 1, 2, 7, 7.2, 20};
        assertArrayEquals(reference(values, times, 6, 2), KalmanElevationSmoother.smoothAxis(values, times, 6, 2), 1e-8);
    }

    static double[] reference(double[] z, double[] times, double sigma, double acceleration) {
        int n = z.length;
        RealVector[] x = new RealVector[n], prior = new RealVector[n];
        RealMatrix[] p = new RealMatrix[n], pp = new RealMatrix[n], a = new RealMatrix[n];
        RealVector state = new ArrayRealVector(new double[]{z[0], 0});
        RealMatrix covariance = MatrixUtils.createRealDiagonalMatrix(new double[]{500, 25});
        for (int i = 0; i < n; i++) {
            double dt = i == 0 ? 0 : times[i] - times[i - 1], q = acceleration * acceleration;
            a[i] = MatrixUtils.createRealMatrix(new double[][]{{1, dt}, {0, 1}});
            RealMatrix noise = MatrixUtils.createRealMatrix(new double[][]{{q * Math.pow(dt, 4) / 4, q * Math.pow(dt, 3) / 2}, {q * Math.pow(dt, 3) / 2, q * dt * dt}});
            KalmanFilter filter = new KalmanFilter(new DefaultProcessModel(a[i], null, noise, state, covariance),
                    new DefaultMeasurementModel(MatrixUtils.createRealMatrix(new double[][]{{1, 0}}), MatrixUtils.createRealMatrix(new double[][]{{sigma * sigma}})));
            filter.predict();
            prior[i] = new ArrayRealVector(filter.getStateEstimation());
            pp[i] = MatrixUtils.createRealMatrix(filter.getErrorCovariance());
            if (Double.isFinite(z[i])) filter.correct(new double[]{z[i]});
            state = new ArrayRealVector(filter.getStateEstimation());
            covariance = MatrixUtils.createRealMatrix(filter.getErrorCovariance());
            x[i] = state;
            p[i] = covariance;
        }
        double[] result = z.clone();
        RealVector smoothed = x[n - 1];
        result[n - 1] = smoothed.getEntry(0);
        for (int i = n - 2; i >= 0; i--) {
            var gain = p[i].multiply(a[i + 1].transpose()).multiply(MatrixUtils.inverse(pp[i + 1]));
            smoothed = x[i].add(gain.operate(smoothed.subtract(prior[i + 1])));
            result[i] = Double.isFinite(z[i]) ? smoothed.getEntry(0) : Double.NaN;
        }
        return result;
    }
}
