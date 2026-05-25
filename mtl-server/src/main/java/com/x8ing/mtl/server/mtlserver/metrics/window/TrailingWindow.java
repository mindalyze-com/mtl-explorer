package com.x8ing.mtl.server.mtlserver.metrics.window;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Generic O(N) trailing time-window accumulator over a per-point stream.
 * <p>
 * The window tracks the trailing {@code windowSec} of "time" along with a
 * configurable set of value sums (sum of raw per-segment values inside the
 * window). The caller decides what those raw values represent (e.g.
 * distance-in-meters, ascent-in-meters, power×duration); the engine itself is
 * value-agnostic.
 * <p>
 * Two well-known derived metrics fall out of the sums:
 * <ul>
 *   <li><b>rate</b> = {@code windowSum(i) / windowDurationSec()} — useful for
 *       sums that are per-segment quantities, e.g. m/s = Σdistance/Σtime, or
 *       m/h ascent rate = Σascent/Σtime.</li>
 *   <li><b>time-weighted average</b> = same formula when the caller passes
 *       {@code value × durationToPrev} as the raw input (so the window sum
 *       becomes the integral of value over time).</li>
 * </ul>
 * <p>
 * Gap and stop policies — both explicit and testable per the architecture
 * doc {@code canonical_metric_lod_architecture.md}:
 * <ul>
 *   <li><b>Gap-reset:</b> when a single {@code durationToPrev > maxGapSec},
 *       the window is reset and the gap-crossing sample is skipped. This
 *       prevents a long GPS dropout from being averaged into the next live
 *       point or reported as a standalone speed spike.</li>
 *   <li><b>Stop-exclusion:</b> when {@code exclude} is true the sample is
 *       still consumed for trimming purposes but its values do not contribute
 *       to the window sums. This is how "moving-only" series are produced.</li>
 * </ul>
 * <p>
 * Stateless and reusable: instantiate, drive forward with {@link #step}, read
 * derived values at each step, optionally {@link #reset()} between independent
 * streams. Not thread-safe.
 */
@JsonPropertyOrder({
        "windowSec",
        "maxGapSec",
        "nValues",
        "buffer",
        "sums",
        "windowDurationSec"
})
public final class TrailingWindow {

    /**
     * Default maximum time gap (seconds) between two consecutive canonical
     * points before the window is reset. Matches the threshold described in
     * the architecture doc.
     */
    public static final double DEFAULT_MAX_GAP_SEC = 10.0;

    private final double windowSec;
    private final double maxGapSec;
    private final int nValues;

    /**
     * Each entry holds {@code [durationToPrev, value_0, value_1, ...]} for one
     * in-window sample. ArrayDeque gives O(1) add-last / poll-first.
     */
    private final ArrayDeque<double[]> buffer;
    private final double[] sums;
    private double windowDurationSec;

    public TrailingWindow(double windowSec, double maxGapSec, int nValues) {
        if (windowSec <= 0) throw new IllegalArgumentException("windowSec must be > 0");
        if (maxGapSec <= 0) throw new IllegalArgumentException("maxGapSec must be > 0");
        if (nValues < 0) throw new IllegalArgumentException("nValues must be >= 0");
        this.windowSec = windowSec;
        this.maxGapSec = maxGapSec;
        this.nValues = nValues;
        this.sums = new double[nValues];
        this.buffer = new ArrayDeque<>();
    }

    /**
     * Feed one sample into the window.
     *
     * @param durationToPrev seconds since the previous sample (0 for the first
     *                       sample of a stream; negative or NaN values are
     *                       treated as 0).
     * @param exclude        when true the sample is not added to the window
     *                       sums (used for stop/idle samples) but the window
     *                       is still trimmed from the front as time advances.
     * @param rawValues      one raw value per registered slot. Length must
     *                       equal the {@code nValues} passed at construction
     *                       time. May be {@code null} if {@code nValues == 0}.
     */
    public void step(double durationToPrev, boolean exclude, double... rawValues) {
        double dt = Double.isFinite(durationToPrev) && durationToPrev > 0 ? durationToPrev : 0;

        // Strict greater-than so a regular sample interval that equals
        // maxGapSec (e.g. a 10 s-sample track with the default 10 s gap
        // threshold) does not reset the window on every step. The
        // architecture doc specifies the boundary as "> maxGapSec".
        if (dt > maxGapSec) {
            reset();
            return;
        }

        if (!exclude && dt > 0) {
            if (rawValues == null || rawValues.length != nValues) {
                throw new IllegalArgumentException(
                        "rawValues length=" + (rawValues == null ? "null" : rawValues.length)
                        + " does not match nValues=" + nValues);
            }
            double[] entry = new double[nValues + 1];
            entry[0] = dt;
            System.arraycopy(rawValues, 0, entry, 1, nValues);
            buffer.addLast(entry);
            windowDurationSec += dt;
            for (int k = 0; k < nValues; k++) {
                sums[k] += rawValues[k];
            }
        }

        // Trim from the front while keeping at least one in-window sample.
        while (!buffer.isEmpty() && windowDurationSec - buffer.peekFirst()[0] >= windowSec) {
            double[] head = buffer.pollFirst();
            windowDurationSec -= head[0];
            for (int k = 0; k < nValues; k++) {
                sums[k] -= head[k + 1];
            }
        }
    }

    /**
     * Total in-window time, in seconds.
     */
    public double windowDurationSec() {
        return windowDurationSec;
    }

    /**
     * True when the window covers at least {@code windowSec} seconds of data.
     */
    public boolean ready() {
        return windowDurationSec >= windowSec;
    }

    /**
     * Sum of the raw values for slot {@code idx} currently inside the window.
     */
    public double sum(int idx) {
        return sums[idx];
    }

    /**
     * Convenience accessor: {@code sum(idx) / windowDurationSec()} when the
     * window has any data, else 0.
     */
    public double ratePerSecond(int idx) {
        return windowDurationSec > 0 ? sums[idx] / windowDurationSec : 0;
    }

    public double windowSec() {
        return windowSec;
    }

    public double maxGapSec() {
        return maxGapSec;
    }

    public void reset() {
        buffer.clear();
        windowDurationSec = 0;
        Arrays.fill(sums, 0);
    }
}
