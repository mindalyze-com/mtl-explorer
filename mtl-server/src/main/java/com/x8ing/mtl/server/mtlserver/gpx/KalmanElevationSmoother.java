package com.x8ing.mtl.server.mtlserver.gpx;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.filter.KalmanFilter;
import org.apache.commons.math3.filter.MeasurementModel;
import org.apache.commons.math3.filter.ProcessModel;
import org.apache.commons.math3.linear.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Full 3-D GPS smoother using {@link org.apache.commons.math3.filter.KalmanFilter}
 * (Apache Commons Math) with a Rauch–Tung–Striebel (RTS) backward smoother pass.
 *
 * <p>Unlike an elevation-only filter, this implementation smooths every spatial
 * dimension — latitude, longitude, and altitude — producing a consistent,
 * physically plausible trajectory.
 *
 * <h2>State-space model (constant-velocity, 6-D)</h2>
 * <pre>
 *   State:       x = [ north_m, v_north,  east_m, v_east,  alt_m, v_alt ]ᵀ
 *   Measurement: z = [ north_m, east_m,   alt_m ]ᵀ   (GPS fix in ENU metres)
 *
 *   Transition A(dt) = block-diag( [1 dt; 0 1],   ← north / v_north
 *                                   [1 dt; 0 1],   ← east  / v_east
 *                                   [1 dt; 0 1] )  ← alt   / v_alt
 *
 *   Process noise Q(dt) = σ_a² · block-diag( Q₁d, Q₁d, Q₁d )
 *       where Q₁d = [ dt⁴/4  dt³/2 ]     (discrete white-noise acceleration)
 *                   [ dt³/2  dt²   ]
 *
 *   Measurement noise R = diag( σ_h², σ_h², σ_v² )
 * </pre>
 *
 * <h2>Time-varying dt</h2>
 * <p>The inner {@code GpsDynamicProcessModel} exposes a {@code setDt()} mutator.
 * Because the {@link ProcessModel} API documents that
 * {@code getStateTransitionMatrix()} and {@code getProcessNoise()} are called by
 * {@link KalmanFilter} on <em>every</em> {@code predict()} invocation, updating
 * {@code dt} before the call is all that is needed for a truly time-varying filter.
 *
 * <h2>Two-pass algorithm</h2>
 * <ol>
 *   <li><b>Forward pass</b> — {@link KalmanFilter#predict()} /
 *       {@link KalmanFilter#correct(double[])} for every point.  The prior
 *       (captured between predict and correct) and posterior are stored for the
 *       backward pass.</li>
 *   <li><b>RTS backward smoother</b> — runs backwards from the last point using
 *       the smoother gain {@code G = Pf · Aᵀ · Pp⁻¹} (inversion via
 *       {@link MatrixUtils#inverse}).  On singularity the forward estimate is kept.</li>
 * </ol>
 *
 * <p>ENU conversion: coordinates are mapped to approximate local-tangent metres
 * (1° lat ≈ 111 320 m; 1° lon ≈ cos(lat₀) × 111 320 m) and converted back after
 * filtering.
 *
 * <h2>application.yml</h2>
 * <pre>
 * mtl:
 *   denoise:
 *     algorithm: kalman
 *     kalman:
 *       measurement-noise-sigma-horizontal: 6.0   # GPS horizontal std-dev (m); default 6 m
 *       measurement-noise-sigma-vertical:  10.0   # GPS vertical  std-dev (m); default 10 m
 *       process-noise-sigma-a:              2.0   # horizontal acceleration uncertainty (m/s²); lower = smoother
 *       process-noise-sigma-a-vertical:     0.5   # vertical   acceleration uncertainty (m/s²); lower = smoother
 * </pre>
 *
 * <p>Higher {@code process-noise-sigma-a} → filter trusts measurements more →
 * less smoothing (generous/responsive).  Default 2 m/s² is generous enough for
 * bicycle dynamics without over-smoothing real turns or climbs.
 * The vertical sigma defaults to 0.5 m/s² — much calmer than horizontal dynamics
 * — which aggressively smooths noisy barometric/GPS altitude while preserving
 * real climbs.
 */
@Component("kalman")
@Slf4j
public class KalmanElevationSmoother implements GpsSmoothingAlgorithm {

    /**
     * Metres per degree of latitude (WGS-84 mean sphere).
     */
    private static final double METERS_PER_DEG_LAT = 111_320.0;

    /**
     * Upper cap on dt; prevents matrix blow-up during long pauses or stops.
     */
    private static final double MAX_DT_SEC = 60.0;

    private final double sigmaH;   // horizontal GPS noise std-dev (m)
    private final double sigmaV;   // vertical   GPS noise std-dev (m)
    private final double sigmaA;   // process-noise: horizontal acceleration std-dev (m/s²)
    private final double sigmaAV;  // process-noise: vertical   acceleration std-dev (m/s²)

    public KalmanElevationSmoother(
            @Value("${mtl.denoise.kalman.measurement-noise-sigma-horizontal:6.0}") double sigmaH,
            @Value("${mtl.denoise.kalman.measurement-noise-sigma-vertical:10.0}") double sigmaV,
            @Value("${mtl.denoise.kalman.process-noise-sigma-a:2.0}") double sigmaA,
            @Value("${mtl.denoise.kalman.process-noise-sigma-a-vertical:0.5}") double sigmaAV) {
        this.sigmaH = sigmaH;
        this.sigmaV = sigmaV;
        this.sigmaA = sigmaA;
        this.sigmaAV = sigmaAV;
        log.info("KalmanElevationSmoother: sigmaH={} m, sigmaV={} m, sigmaA={} m/s², sigmaAV={} m/s²",
                sigmaH, sigmaV, sigmaA, sigmaAV);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public entry point
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public LineString denoise(LineString lineString) {
        int n = lineString.getNumPoints();
        if (n <= 2) return lineString;

        Coordinate[] coords = lineString.getCoordinates();

        // Reference point for ENU conversion
        double refLat = coords[0].getY();
        double refLon = coords[0].getX();
        double mpdLon = METERS_PER_DEG_LAT * Math.cos(Math.toRadians(refLat));

        // ── Convert to approximate ENU metres ────────────────────────────────
        double[] northM = new double[n];
        double[] eastM = new double[n];
        double[] altM = new double[n];
        boolean[] hasAlt = new boolean[n];
        double seedAlt = 0.0;
        boolean seedAltFound = false;

        for (int i = 0; i < n; i++) {
            northM[i] = (coords[i].getY() - refLat) * METERS_PER_DEG_LAT;
            eastM[i] = (coords[i].getX() - refLon) * mpdLon;
            hasAlt[i] = !Double.isNaN(coords[i].getZ());
            if (hasAlt[i]) {
                altM[i] = coords[i].getZ();
                if (!seedAltFound) {
                    seedAlt = altM[i];
                    seedAltFound = true;
                }
            } else {
                altM[i] = seedAlt; // placeholder; filter will override via prediction
            }
        }

        // ── Build models and KalmanFilter ─────────────────────────────────────
        GpsDynamicProcessModel procModel =
                new GpsDynamicProcessModel(sigmaA, sigmaAV, northM[0], eastM[0], altM[0]);
        GpsMeasurementModel measModel =
                new GpsMeasurementModel(sigmaH, sigmaV);

        KalmanFilter kf = new KalmanFilter(procModel, measModel);

        // ── Storage for the RTS backward pass ─────────────────────────────────
        double[][] xFilt = new double[n][];    // forward posterior states
        double[][][] pFilt = new double[n][][];  // forward posterior covariances
        double[][] xPrior = new double[n][];    // forward prior  states
        double[][][] pPrior = new double[n][][];  // forward prior  covariances
        RealMatrix[] aK = new RealMatrix[n];  // A(dt) used at each step

        // ── k = 0: first measurement — correct only, no predict ───────────────
        aK[0] = MatrixUtils.createRealIdentityMatrix(6);
        xPrior[0] = procModel.getInitialStateEstimate().toArray().clone();
        pPrior[0] = procModel.getInitialErrorCovariance().getData();

        kf.correct(new double[]{northM[0], eastM[0], altM[0]});
        xFilt[0] = kf.getStateEstimation().clone();
        pFilt[0] = deepCopy(kf.getErrorCovariance());

        // ── k = 1 … n-1: predict → capture prior → correct → capture posterior ─
        for (int k = 1; k < n; k++) {
            double dt = computeDt(coords, k);
            procModel.setDt(dt);
            // Capture A(dt) before predict() consumes it inside the library
            aK[k] = procModel.getStateTransitionMatrix();

            kf.predict();
            // getStateEstimation() now returns the PRIOR (before this step's correction)
            xPrior[k] = kf.getStateEstimation().clone();
            pPrior[k] = deepCopy(kf.getErrorCovariance());

            // For NaN altitude: feed the predicted altitude as the measurement
            // → zero innovation for the altitude component → no altitude update this step
            double zAlt = hasAlt[k] ? altM[k] : xPrior[k][4];
            kf.correct(new double[]{northM[k], eastM[k], zAlt});

            xFilt[k] = kf.getStateEstimation().clone();
            pFilt[k] = deepCopy(kf.getErrorCovariance());

            // When altitude is missing, the zero-innovation trick still causes
            // the Kalman gain to shrink the altitude covariance as if a real
            // measurement arrived.  Restore the altitude sub-block (rows/cols
            // 4-5) of pFilt from pPrior so the RTS smoother is not misled.
            if (!hasAlt[k]) {
                for (int r = 0; r < 6; r++) {
                    pFilt[k][r][4] = pPrior[k][r][4];
                    pFilt[k][r][5] = pPrior[k][r][5];
                    pFilt[k][4][r] = pPrior[k][4][r];
                    pFilt[k][5][r] = pPrior[k][5][r];
                }
                // Also revert the altitude state to the prior (no information gained)
                xFilt[k][4] = xPrior[k][4];
                xFilt[k][5] = xPrior[k][5];
            }
        }

        // ── RTS backward smoother ─────────────────────────────────────────────
        // x_smooth[k] = x_filt[k] + G · (x_smooth[k+1] − x_prior[k+1])
        // P_smooth[k] = P_filt[k] + G · (P_smooth[k+1] − P_prior[k+1]) · Gᵀ
        // G           = P_filt[k] · A[k+1]ᵀ · P_prior[k+1]⁻¹
        double[][] xSmooth = new double[n][];
        double[][][] pSmooth = new double[n][][];
        xSmooth[n - 1] = xFilt[n - 1].clone();
        pSmooth[n - 1] = deepCopy(pFilt[n - 1]);

        for (int k = n - 2; k >= 0; k--) {
            RealMatrix Pf = MatrixUtils.createRealMatrix(pFilt[k]);
            RealMatrix Pp = MatrixUtils.createRealMatrix(pPrior[k + 1]);

            RealMatrix G;
            try {
                G = Pf.multiply(aK[k + 1].transpose())
                        .multiply(MatrixUtils.inverse(Pp));
            } catch (SingularMatrixException e) {
                log.debug("RTS: singular prior covariance at k={}, keeping forward estimate", k);
                xSmooth[k] = xFilt[k].clone();
                pSmooth[k] = deepCopy(pFilt[k]);
                continue;
            }

            RealVector dx = new ArrayRealVector(xSmooth[k + 1])
                    .subtract(new ArrayRealVector(xPrior[k + 1]));
            xSmooth[k] = new ArrayRealVector(xFilt[k]).add(G.operate(dx)).toArray();

            RealMatrix dP = MatrixUtils.createRealMatrix(pSmooth[k + 1]).subtract(Pp);
            pSmooth[k] = Pf.add(G.multiply(dP).multiply(G.transpose())).getData();
        }

        // ── Convert smoothed ENU metres back to WGS-84 ───────────────────────
        Coordinate[] out = new Coordinate[n];
        for (int k = 0; k < n; k++) {
            double lat = refLat + xSmooth[k][0] / METERS_PER_DEG_LAT;
            double lon = refLon + xSmooth[k][2] / mpdLon;
            double z = hasAlt[k] ? xSmooth[k][4] : Double.NaN;
            out[k] = new CoordinateXYZM(lon, lat, z, coords[k].getM());
        }

        return new LineString(new CoordinateArraySequence(out), lineString.getFactory());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static double computeDt(Coordinate[] coords, int i) {
        double prev = coords[i - 1].getM();
        double curr = coords[i].getM();
        return (prev > 0 && curr > prev) ? Math.min(curr - prev, MAX_DT_SEC) : 1.0;
    }

    private static double[][] deepCopy(double[][] src) {
        double[][] dst = new double[src.length][];
        for (int i = 0; i < src.length; i++) dst[i] = src[i].clone();
        return dst;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner: Dynamic ProcessModel
    //
    // getStateTransitionMatrix() and getProcessNoise() are called by KalmanFilter
    // on every predict() invocation (documented in the ProcessModel Javadoc), so
    // mutating dt before predict() is the officially supported way to build a
    // time-varying model with the library.
    // ─────────────────────────────────────────────────────────────────────────

    private static class GpsDynamicProcessModel implements ProcessModel {

        private double dt;
        private final double sigmaAH2;  // horizontal acceleration variance
        private final double sigmaAV2;  // vertical   acceleration variance
        private final RealVector initialState;
        private final RealMatrix initialP;

        GpsDynamicProcessModel(double sigmaAH, double sigmaAV, double north0, double east0, double alt0) {
            this.sigmaAH2 = sigmaAH * sigmaAH;
            this.sigmaAV2 = sigmaAV * sigmaAV;
            this.dt = 1.0;
            // State: [north_m, v_north, east_m, v_east, alt_m, v_alt]
            this.initialState = new ArrayRealVector(
                    new double[]{north0, 0.0, east0, 0.0, alt0, 0.0});
            // Large initial uncertainty: 500 m² positional, 25 (m/s)² velocity
            this.initialP = MatrixUtils.createRealDiagonalMatrix(
                    new double[]{500.0, 25.0, 500.0, 25.0, 500.0, 25.0});
        }

        void setDt(double dt) {
            this.dt = dt;
        }

        /**
         * A(dt) = block-diag( [1 dt; 0 1], [1 dt; 0 1], [1 dt; 0 1] )
         * Called by KalmanFilter.predict() on every step.
         */
        @Override
        public RealMatrix getStateTransitionMatrix() {
            double[][] a = new double[6][6];
            a[0][0] = 1.0;
            a[0][1] = dt;
            a[1][1] = 1.0;
            a[2][2] = 1.0;
            a[2][3] = dt;
            a[3][3] = 1.0;
            a[4][4] = 1.0;
            a[4][5] = dt;
            a[5][5] = 1.0;
            return MatrixUtils.createRealMatrix(a);
        }

        /**
         * No external control input.
         */
        @Override
        public RealMatrix getControlMatrix() {
            return null;
        }

        /**
         * Q(dt) = σ_a² · block-diag( Q₁d, Q₁d, Q₁d )
         * Q₁d = [ dt⁴/4  dt³/2 ]
         * [ dt³/2  dt²   ]
         * Called by KalmanFilter.predict() on every step.
         */
        @Override
        public RealMatrix getProcessNoise() {
            double dt2 = dt * dt;
            double dt3 = dt2 * dt;
            double dt4 = dt3 * dt;
            double qH11 = sigmaAH2 * dt4 / 4.0;
            double qH12 = sigmaAH2 * dt3 / 2.0;
            double qH22 = sigmaAH2 * dt2;
            double qV11 = sigmaAV2 * dt4 / 4.0;
            double qV12 = sigmaAV2 * dt3 / 2.0;
            double qV22 = sigmaAV2 * dt2;
            double[][] q = new double[6][6];
            // north (horizontal)
            q[0][0] = qH11;
            q[0][1] = qH12;
            q[1][0] = qH12;
            q[1][1] = qH22;
            // east  (horizontal)
            q[2][2] = qH11;
            q[2][3] = qH12;
            q[3][2] = qH12;
            q[3][3] = qH22;
            // alt   (vertical — separate, calmer dynamics)
            q[4][4] = qV11;
            q[4][5] = qV12;
            q[5][4] = qV12;
            q[5][5] = qV22;
            return MatrixUtils.createRealMatrix(q);
        }

        @Override
        public RealVector getInitialStateEstimate() {
            return initialState;
        }

        @Override
        public RealMatrix getInitialErrorCovariance() {
            return initialP;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner: Fixed MeasurementModel
    //   H maps state → [north, east, alt]
    //   R = diag(σ_h², σ_h², σ_v²)
    // ─────────────────────────────────────────────────────────────────────────

    private static class GpsMeasurementModel implements MeasurementModel {

        private final RealMatrix H;
        private final RealMatrix R;

        GpsMeasurementModel(double sigmaH, double sigmaV) {
            double[][] h = new double[3][6];
            h[0][0] = 1.0; // north (state index 0)
            h[1][2] = 1.0; // east  (state index 2)
            h[2][4] = 1.0; // alt   (state index 4)
            this.H = MatrixUtils.createRealMatrix(h);
            this.R = MatrixUtils.createRealDiagonalMatrix(
                    new double[]{sigmaH * sigmaH, sigmaH * sigmaH, sigmaV * sigmaV});
        }

        @Override
        public RealMatrix getMeasurementMatrix() {
            return H;
        }

        @Override
        public RealMatrix getMeasurementNoise() {
            return R;
        }
    }
}

