package com.x8ing.mtl.server.mtlserver.metrics.window;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.metrics.MetricConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Computes the trailing-window {@link WindowedRateSample} (speed, ascent rate,
 * descent rate) for a stream of canonical GPS points.
 * <p>
 * Single-pass O(N) over the input, deterministic, free of any persistence
 * concerns. Used in two distinct call sites:
 * <ul>
 *   <li><b>Ingest:</b> {@code GPXStoreService} runs this once over the
 *       canonical {@code RAW_OUTLIER_CLEANED} point stream to compute the
 *       track-level peak values stored on {@code GpsTrack}.</li>
 *   <li><b>On-the-fly chart series:</b> the chart-series endpoint runs this
 *       (with the activity-specific window length) and folds the result into
 *       the bucket aggregators — never persisting the per-point values.</li>
 * </ul>
 * <p>
 * The calculator is intentionally driven through the {@link CanonicalPointView}
 * adapter so it never imports any JPA entity directly.
 */
@JsonPropertyOrder({
        "windowSec",
        "gapPolicy"
})
public final class PointWindowedRateCalculator {

    private static final int IDX_DISTANCE = 0;
    private static final int IDX_ASCENT = 1;
    private static final int IDX_DESCENT = 2;
    private static final int NUM_SLOTS = 3;

    /**
     * Controls how the gap-reset threshold is derived from the track's sampling
     * cadence.
     *
     * <p>The effective gap is computed as:
     * <pre>
     *   effectiveGapSec = min(windowSec,
     *                     min(maxGapSec,
     *                     max(fixedMinGapSec,
     *                         medianCadenceSec * missedBeatMultiplier)))
     * </pre>
     *
     * <p>Rule: accept normal sparse-sampling cadence; reset the window when
     * roughly one full sample beat is missed.
     *
     * @param fixedMinGapSec       floor in seconds; protects dense-cadence tracks
     *                             from over-adapting (e.g. default 10 s gap keeps
     *                             1-second GPS tracks strict).
     * @param missedBeatMultiplier factor applied to the median sample interval.
     *                             1.75 means: accept a sample that is up to 1.75x
     *                             the normal interval late; reset if more is missed.
     *                             Set to {@code 0} to disable cadence adaptation
     *                             (fixed-gap mode).
     * @param maxGapSec            absolute ceiling for the effective gap,
     *                             independent of cadence. Use
     *                             {@link Double#MAX_VALUE} to let the window size
     *                             be the only upper bound.
     */
    @JsonPropertyOrder({
            "fixedMinGapSec",
            "missedBeatMultiplier",
            "maxGapSec"
    })
    public record GapPolicy(
            double fixedMinGapSec,
            double missedBeatMultiplier,
            double maxGapSec) {
        public GapPolicy {
            if (!Double.isFinite(fixedMinGapSec) || fixedMinGapSec <= 0) {
                throw new IllegalArgumentException("fixedMinGapSec must be finite and > 0");
            }
            if (!Double.isFinite(missedBeatMultiplier) || missedBeatMultiplier < 0) {
                throw new IllegalArgumentException("missedBeatMultiplier must be finite and >= 0");
            }
            if (!Double.isFinite(maxGapSec) || maxGapSec <= 0) {
                throw new IllegalArgumentException("maxGapSec must be finite and > 0");
            }
        }

        /**
         * Default adaptive policy: 10 s floor protects dense tracks; 1.75x median
         * cadence accepts normal sparse sampling while resetting on a missed beat.
         */
        public static final GapPolicy DEFAULT = new GapPolicy(
                TrailingWindow.DEFAULT_MAX_GAP_SEC,
                1.75,
                Double.MAX_VALUE);
    }

    private final double windowSec;
    private final GapPolicy gapPolicy;

    /**
     * Uses {@link GapPolicy#DEFAULT}: cadence-adaptive gap, anchored at a 10 s
     * floor. Suitable for all production call sites.
     */
    public PointWindowedRateCalculator(double windowSec) {
        this(windowSec, GapPolicy.DEFAULT);
    }

    /**
     * Fixed gap, no cadence adaptation. Suitable when the caller knows the exact
     * gap threshold to apply (e.g., tests or explicit overrides).
     */
    public PointWindowedRateCalculator(double windowSec, double maxGapSec) {
        this(windowSec, new GapPolicy(maxGapSec, 0.0, maxGapSec));
    }

    public PointWindowedRateCalculator(double windowSec, GapPolicy gapPolicy) {
        this.windowSec = windowSec;
        this.gapPolicy = java.util.Objects.requireNonNull(gapPolicy, "gapPolicy");
    }

    /**
     * Compute the per-point trailing-window samples for the given canonical
     * point stream. The result has the same length and order as the input.
     */
    public <P> List<WindowedRateSample> compute(List<P> points, CanonicalPointView<P> view) {
        if (points == null || points.isEmpty()) {
            return new ArrayList<>();
        }
        int n = points.size();
        List<WindowedRateSample> out = new ArrayList<>(n);

        TrailingWindow window = new TrailingWindow(
                windowSec,
                effectiveMaxGapSec(points, view),
                NUM_SLOTS);

        double prevAscentSinceStart = view.ascentInMeterSinceStart(points.get(0));
        double prevDescentSinceStart = view.descentInMeterSinceStart(points.get(0));

        for (int i = 0; i < n; i++) {
            P current = points.get(i);
            double duration = positive(view.durationBetweenPointsInSec(current));
            double distance = positive(view.distanceInMeterBetweenPoints(current));

            double curAscentSinceStart = view.ascentInMeterSinceStart(current);
            double curDescentSinceStart = view.descentInMeterSinceStart(current);
            double ascentSegment = i == 0 ? 0 : positiveDelta(prevAscentSinceStart, curAscentSinceStart);
            double descentSegment = i == 0 ? 0 : positiveDelta(prevDescentSinceStart, curDescentSinceStart);
            prevAscentSinceStart = curAscentSinceStart;
            prevDescentSinceStart = curDescentSinceStart;

            window.step(duration, false, distance, ascentSegment, descentSegment);

            if (window.ready() && duration > 0) {
                out.add(WindowedRateSample.fromWindow(
                        window.windowDurationSec(),
                        window.sum(IDX_DISTANCE),
                        window.sum(IDX_ASCENT),
                        window.sum(IDX_DESCENT)));
            } else {
                out.add(WindowedRateSample.empty());
            }
        }
        return out;
    }

    /**
     * Track-level peak summary derived from the per-point window output.
     */
    @JsonPropertyOrder({
            "speedInKmhMax",
            "elevationGainPerHourMax",
            "elevationLossPerHourMax"
    })
    public record PeakSummary(double speedInKmhMax, double elevationGainPerHourMax, double elevationLossPerHourMax) {
        public static final PeakSummary ZERO = new PeakSummary(0d, 0d, 0d);
    }

    public static PeakSummary peakOf(List<WindowedRateSample> samples) {
        if (samples == null || samples.isEmpty()) return PeakSummary.ZERO;
        double speedMax = 0, gainMax = 0, lossMax = 0;
        for (WindowedRateSample s : samples) {
            if (s == null) continue;
            speedMax = max(speedMax, s.speedInKmh());
            gainMax = max(gainMax, s.elevationGainPerHour());
            lossMax = max(lossMax, s.elevationLossPerHour());
        }
        return new PeakSummary(speedMax, gainMax, lossMax);
    }

    /**
     * Convenience: run calculator and reduce to peak summary in one call.
     */
    public <P> PeakSummary computePeaks(List<P> points, CanonicalPointView<P> view) {
        return peakOf(compute(points, view));
    }

    /**
     * Convenience for code that wants the raw extractors without the
     * intermediate {@link CanonicalPointView} (mostly for direct JTS-free
     * tests).
     */
    public static <P> CanonicalPointView<P> viewOf(
            Function<P, Double> durationBetweenPointsInSec,
            Function<P, Double> distanceInMeterBetweenPoints,
            Function<P, Double> ascentInMeterSinceStart,
            Function<P, Double> descentInMeterSinceStart) {
        return new CanonicalPointView<>() {
            @Override
            public double durationBetweenPointsInSec(P p) {
                return orZero(durationBetweenPointsInSec.apply(p));
            }

            @Override
            public double distanceInMeterBetweenPoints(P p) {
                return orZero(distanceInMeterBetweenPoints.apply(p));
            }

            @Override
            public double ascentInMeterSinceStart(P p) {
                return orZero(ascentInMeterSinceStart.apply(p));
            }

            @Override
            public double descentInMeterSinceStart(P p) {
                return orZero(descentInMeterSinceStart.apply(p));
            }
        };
    }

    public double windowSec() {
        return windowSec;
    }

    public GapPolicy gapPolicy() {
        return gapPolicy;
    }

    private <P> double effectiveMaxGapSec(List<P> points, CanonicalPointView<P> view) {
        // Fixed-gap mode: multiplier 0 disables cadence adaptation entirely.
        if (gapPolicy.missedBeatMultiplier() == 0) {
            return Math.min(gapPolicy.fixedMinGapSec(), windowSec);
        }
        List<Double> durations = new ArrayList<>(points.size());
        for (P point : points) {
            double duration = view.durationBetweenPointsInSec(point);
            if (Double.isFinite(duration) && duration > 0) {
                durations.add(duration);
            }
        }
        if (durations.isEmpty()) {
            // No positive durations to measure cadence; fall back to the floor.
            return Math.min(gapPolicy.fixedMinGapSec(), windowSec);
        }
        Collections.sort(durations);
        double medianCadence = durations.get(durations.size() / 2);
        // Rule: accept normal sparse sampling cadence; reset when roughly one
        // full sample beat is missed.
        double missedBeatGap = medianCadence * gapPolicy.missedBeatMultiplier();
        double effective = Math.max(gapPolicy.fixedMinGapSec(), missedBeatGap);
        effective = Math.min(effective, gapPolicy.maxGapSec());
        effective = Math.min(effective, windowSec);
        return effective;
    }

    private static double positive(double v) {
        return Double.isFinite(v) && v > 0 ? v : 0;
    }

    private static double positiveDelta(double prev, double cur) {
        double d = cur - prev;
        return Double.isFinite(d) && d > 0 ? d : 0;
    }

    private static double orZero(Double v) {
        return v == null || !Double.isFinite(v) ? 0 : v;
    }

    private static double max(double a, Double b) {
        if (b == null || !Double.isFinite(b)) return a;
        return Math.max(a, b);
    }

    /**
     * Silences unused import warning for {@link MetricConstants} — kept so
     * downstream callers can {@code import static} from here later if needed.
     */
    @SuppressWarnings("unused")
    private static double untouchedConstantsReference() {
        return MetricConstants.MPS_TO_KMH;
    }
}
