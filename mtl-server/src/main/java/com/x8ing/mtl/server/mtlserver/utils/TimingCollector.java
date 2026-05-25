package com.x8ing.mtl.server.mtlserver.utils;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

@JsonPropertyOrder({
        "startedNanos",
        "baseElapsedMs",
        "steps"
})
public final class TimingCollector {

    private static final int DEFAULT_TOP_STEP_COUNT = 8;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long MILLIS_PER_MINUTE = 60_000L;

    private final long startedNanos;
    private final long baseElapsedMs;
    private final List<Step> steps;

    public TimingCollector() {
        this(System.nanoTime(), 0, new ArrayList<>());
    }

    private TimingCollector(long startedNanos, long baseElapsedMs, List<Step> steps) {
        this.startedNanos = startedNanos;
        this.baseElapsedMs = baseElapsedMs;
        this.steps = steps;
    }

    public TimingCollector copy() {
        return new TimingCollector(System.nanoTime(), baseElapsedMs, copySteps());
    }

    public TimingCollector snapshot() {
        return new TimingCollector(System.nanoTime(), totalElapsedMs(), copySteps());
    }

    public <T> T time(String label, TimedSupplier<T> supplier) throws Exception {
        long started = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            record(label, elapsedMs(started));
        }
    }

    public void time(String label, TimedRunnable runnable) throws Exception {
        long started = System.nanoTime();
        try {
            runnable.run();
        } finally {
            record(label, elapsedMs(started));
        }
    }

    public <T> T timeUnchecked(String label, Supplier<T> supplier) {
        long started = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            record(label, elapsedMs(started));
        }
    }

    public void timeUnchecked(String label, Runnable runnable) {
        long started = System.nanoTime();
        try {
            runnable.run();
        } finally {
            record(label, elapsedMs(started));
        }
    }

    public void record(String label, long elapsedMs) {
        if (label == null || label.isBlank()) {
            return;
        }
        synchronized (steps) {
            steps.add(new Step(label, Math.max(0, elapsedMs)));
        }
    }

    public long totalElapsedMs() {
        return baseElapsedMs + elapsedMs(startedNanos);
    }

    public String formatSummary() {
        return formatSummary(DEFAULT_TOP_STEP_COUNT);
    }

    public String formatSummary(int maxSteps) {
        List<Step> sorted = copySteps().stream()
                .filter(step -> step.elapsedMs() > 0)
                .sorted(Comparator.comparingLong(Step::elapsedMs).reversed())
                .limit(Math.max(1, maxSteps))
                .toList();

        if (sorted.isEmpty()) {
            return "Timing total " + formatDuration(totalElapsedMs()) + ".";
        }

        StringBuilder builder = new StringBuilder("Timing total ")
                .append(formatDuration(totalElapsedMs()))
                .append("; slowest ");
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            Step step = sorted.get(i);
            builder.append(step.label()).append(" ").append(formatDuration(step.elapsedMs()));
        }
        builder.append('.');
        return builder.toString();
    }

    private List<Step> copySteps() {
        synchronized (steps) {
            return new ArrayList<>(steps);
        }
    }

    public static String formatDuration(long elapsedMs) {
        long ms = Math.max(0, elapsedMs);
        if (ms < MILLIS_PER_SECOND) {
            return ms + "ms";
        }
        if (ms < 10 * MILLIS_PER_SECOND) {
            return String.format(Locale.ROOT, "%.2fs", ms / 1000.0);
        }
        if (ms < MILLIS_PER_MINUTE) {
            return String.format(Locale.ROOT, "%.1fs", ms / 1000.0);
        }
        long minutes = ms / MILLIS_PER_MINUTE;
        long seconds = (ms % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND;
        return minutes + "m" + String.format(Locale.ROOT, "%02ds", seconds);
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / NANOS_PER_MILLI;
    }

    @FunctionalInterface
    public interface TimedSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface TimedRunnable {
        void run() throws Exception;
    }

    @JsonPropertyOrder({
            "label",
            "elapsedMs"
    })
    private record Step(String label, long elapsedMs) {
    }
}
