package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.utils.GeoCoordinateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.linear.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;
import org.locationtech.jts.geom.LineString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Constant-velocity Kalman filter with a time-varying transition and RTS smoothing.
 * Independent horizontal and vertical observations use metre units. Missing observations
 * do not update either state or covariance. Recording breaks start independent models.
 */
@Component("kalman")
@Slf4j
public class KalmanElevationSmoother implements GpsSmoothingAlgorithm {
    private static final double INITIAL_POSITION_VARIANCE = 500;
    private static final double INITIAL_VELOCITY_VARIANCE = 25;
    private static final double INNOVATION_SIGMA_LIMIT = 4;
    private static final double MIN_LONGITUDE_SCALE = 1;
    private static final int MIN_REPEATED_POSITION_POINTS = 3;
    private final double sigmaH;
    private final double sigmaV;
    private final double sigmaA;
    private final double sigmaAV;

    public KalmanElevationSmoother(
            @Value("${mtl.denoise.kalman.measurement-noise-sigma-horizontal:6.0}") double sigmaH,
            @Value("${mtl.denoise.kalman.measurement-noise-sigma-vertical:10.0}") double sigmaV,
            @Value("${mtl.denoise.kalman.process-noise-sigma-a:2.0}") double sigmaA,
            @Value("${mtl.denoise.kalman.process-noise-sigma-a-vertical:0.5}") double sigmaAV) {
        if (!(sigmaH > 0 && sigmaV > 0 && sigmaA > 0 && sigmaAV > 0)
            || !Double.isFinite(sigmaH + sigmaV + sigmaA + sigmaAV)) {
            throw new IllegalArgumentException("Kalman noise sigmas must be finite and positive");
        }
        this.sigmaH = sigmaH;
        this.sigmaV = sigmaV;
        this.sigmaA = sigmaA;
        this.sigmaAV = sigmaAV;
        log.info("Kalman smoothing configured: horizontalSigma={} verticalSigma={} accelerationSigma={} verticalAccelerationSigma={}",
                sigmaH, sigmaV, sigmaA, sigmaAV);
    }

    @Override
    public java.util.Map<String, String> processingSettings() {
        return java.util.Map.of("pipeline", "gps-reliability-v1", "algorithm", "kalman",
                "horizontalSigmaMetres", Double.toString(sigmaH), "verticalSigmaMetres", Double.toString(sigmaV),
                "horizontalAccelerationSigma", Double.toString(sigmaA), "verticalAccelerationSigma", Double.toString(sigmaAV));
    }

    @Override
    public LineString denoise(LineString line) {
        return denoise(line, java.util.List.of());
    }

    @Override
    public LineString denoise(LineString line, java.util.List<GpsObservationEvidence> evidence) {
        if (line == null || line.getNumPoints() < 3) return line;
        Coordinate[] coordinates = line.getCoordinates();
        for (int from = 0; from < coordinates.length; ) {
            int to = from + 1;
            while (to < coordinates.length && !RecordingContinuity.hasTimeBreak(
                    coordinates[to - 1].getM(), coordinates[to].getM())) to++;
            if (to - from >= 3) smoothSpan(coordinates, from, to, evidence);
            from = to;
        }
        LineString result = line.getFactory().createLineString(coordinates);
        result.setSRID(line.getSRID());
        return result;
    }

    private void smoothSpan(Coordinate[] c, int from, int to, java.util.List<GpsObservationEvidence> evidence) {
        int n = to - from;
        boolean[] repeatedPositions = repeatedPositions(c, from, to);
        double refLat = c[from].getY();
        double refLon = c[from].getX();
        double latitudeScale = GeoCoordinateUtils.APPROX_METERS_PER_DEGREE_LATITUDE;
        double longitudeScale = Math.max(MIN_LONGITUDE_SCALE, latitudeScale * Math.cos(Math.toRadians(refLat)));
        double[][] measurements = new double[3][n];
        double[] times = new double[n];
        double[] horizontalScale = new double[n];
        double[] verticalScale = new double[n];
        for (int i = 0; i < n; i++) {
            Coordinate point = c[from + i];
            measurements[0][i] = (point.getY() - refLat) * latitudeScale;
            measurements[1][i] = Math.IEEEremainder(point.getX() - refLon, 360) * longitudeScale;
            measurements[2][i] = point.getZ();
            times[i] = point.getM();
            GpsObservationEvidence source = evidence != null && from + i < evidence.size() ? evidence.get(from + i) : null;
            horizontalScale[i] = source == null ? 1 : source.uncertaintyScale(false);
            verticalScale[i] = source == null ? 1 : source.uncertaintyScale(true);
        }
        double[] north = smoothAxis(measurements[0], times, sigmaH, sigmaA, horizontalScale);
        double[] east = smoothAxis(measurements[1], times, sigmaH, sigmaA, horizontalScale);
        double[] altitude = smoothAxis(measurements[2], times, sigmaV, sigmaAV, verticalScale);
        for (int i = 0; i < n; i++) {
            // The velocity model can ring around a recorded plateau and invent motion.
            // Retain repeated accepted XYZ observations; motion support is classified later.
            if (repeatedPositions[i]) continue;
            c[from + i] = new CoordinateXYZM(Math.IEEEremainder(refLon + east[i] / longitudeScale, 360),
                    refLat + north[i] / latitudeScale,
                    Double.isFinite(measurements[2][i]) ? altitude[i] : Double.NaN, times[i]);
        }
    }

    private static boolean[] repeatedPositions(Coordinate[] coordinates, int from, int to) {
        boolean[] repeated = new boolean[to - from];
        for (int start = from; start < to; ) {
            int end = start + 1;
            while (end < to && coordinates[start].equals3D(coordinates[end])) end++;
            if (end - start >= MIN_REPEATED_POSITION_POINTS) {
                java.util.Arrays.fill(repeated, start - from, end - from, true);
            }
            start = end;
        }
        return repeated;
    }

    // Package visibility permits comparison with an independent numerical reference.
    static double[] smoothAxis(double[] observations, double[] times, double measurementSigma, double accelerationSigma) {
        double[] scales = new double[observations.length];
        java.util.Arrays.fill(scales, 1);
        return smoothAxis(observations, times, measurementSigma, accelerationSigma, scales);
    }

    private static double[] smoothAxis(double[] observations, double[] times, double measurementSigma, double accelerationSigma, double[] scales) {
        int n = observations.length;
        double[] result = observations.clone();
        int seed = 0;
        while (seed < n && !Double.isFinite(observations[seed])) seed++;
        if (seed == n) return result;
        RealVector[] filtered = new RealVector[n];
        RealVector[] predicted = new RealVector[n];
        RealMatrix[] covariance = new RealMatrix[n];
        RealMatrix[] priorCovariance = new RealMatrix[n];
        RealMatrix[] transition = new RealMatrix[n];
        RealVector state = new ArrayRealVector(new double[]{observations[seed], 0});
        RealMatrix p = MatrixUtils.createRealDiagonalMatrix(new double[]{INITIAL_POSITION_VARIANCE, INITIAL_VELOCITY_VARIANCE});
        double variance = measurementSigma * measurementSigma;
        for (int i = seed; i < n; i++) {
            double dt = i == seed ? 0 : times[i] - times[i - 1];
            RealMatrix a = MatrixUtils.createRealMatrix(new double[][]{{1, dt}, {0, 1}});
            transition[i] = a;
            double dt2 = dt * dt;
            double q = accelerationSigma * accelerationSigma;
            RealMatrix noise = MatrixUtils.createRealMatrix(new double[][]{
                    {q * dt2 * dt2 / 4, q * dt2 * dt / 2}, {q * dt2 * dt / 2, q * dt2}});
            state = a.operate(state);
            p = a.multiply(p).multiply(a.transpose()).add(noise);
            predicted[i] = state.copy();
            priorCovariance[i] = p.copy();
            if (Double.isFinite(observations[i])) {
                double residual = observations[i] - state.getEntry(0);
                // Huber-style uncertainty inflation limits isolated innovations without
                // declaring every unexpected manoeuvre an outlier.
                double sourceVariance = variance * scales[i] * scales[i];
                double normalized = Math.abs(residual) / Math.sqrt(p.getEntry(0, 0) + sourceVariance);
                double observationVariance = sourceVariance * Math.max(1, Math.pow(normalized / INNOVATION_SIGMA_LIMIT, 2));
                RealVector gain = p.getColumnVector(0).mapDivide(p.getEntry(0, 0) + observationVariance);
                state = state.add(gain.mapMultiply(residual));
                RealMatrix correction = MatrixUtils.createRealIdentityMatrix(2);
                correction.setEntry(0, 0, 1 - gain.getEntry(0));
                correction.setEntry(1, 0, -gain.getEntry(1));
                // Joseph form maintains positive covariance despite rounding.
                p = correction.multiply(p).multiply(correction.transpose())
                        .add(gain.outerProduct(gain).scalarMultiply(observationVariance));
            }
            filtered[i] = state.copy();
            covariance[i] = p.copy();
        }
        RealVector smoothed = filtered[n - 1];
        result[n - 1] = smoothed.getEntry(0);
        for (int i = n - 2; i >= seed; i--) {
            RealMatrix gain = covariance[i].multiply(transition[i + 1].transpose())
                    .multiply(new LUDecomposition(priorCovariance[i + 1]).getSolver().getInverse());
            smoothed = filtered[i].add(gain.operate(smoothed.subtract(predicted[i + 1])));
            result[i] = Double.isFinite(observations[i]) ? smoothed.getEntry(0) : Double.NaN;
        }
        return result;
    }
}
