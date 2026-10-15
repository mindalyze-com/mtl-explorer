package com.x8ing.mtl.server.mtlserver.googlehealth.demo;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * One fictional wearable timeline, independent of import order and overlapping-track count.
 */
public final class DemoTimeline {
    private static final int SECONDS_PER_DAY = 24 * 60 * 60;
    private static final double EFFORT_RESPONSE_SECONDS = 30;
    private static final double HEART_RATE_RISE_SECONDS = 65;
    private static final double HEART_RATE_RECOVERY_SECONDS = 85;
    private static final double NOISE_RESPONSE_SECONDS = 25;
    private static final double NOISE_BPM = 1.8;
    private static final int MINIMUM_BPM = 45;
    private static final int MAXIMUM_BPM = 182;

    public record Window(Instant start, Instant end) {
    }

    public record Result(List<DemoPhysiology.Sample> samples, double averageBpm, int minimumBpm, int maximumBpm,
                         double steps, double distanceMeters, double activeKcal) {
    }

    public static Result generate(long seed, LocalDate date, int intervalSeconds, boolean detailed,
                                  Map<Instant, DemoPhysiology.Workload> loads, List<Window> windows) {
        var day = DemoPhysiology.day(seed, date);
        var random = new SplittableRandom(DemoPhysiology.seed(seed, "timeline:" + date));
        Instant start = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        List<DemoPhysiology.Sample> samples = new ArrayList<>();
        var statistics = new IntSummaryStatistics();
        double heartRate = DemoPhysiology.backgroundBpm(day, 0), target = heartRate, noise = 0;
        double steps = 0, distance = 0, kcal = 0;
        var ranges = merge(windows);
        int windowIndex = 0;
        long gridMillis = intervalSeconds * 1000L;
        Instant first = Instant.ofEpochMilli(-Math.floorDiv(-start.toEpochMilli(), gridMillis) * gridMillis);
        for (Instant time = first; time.isBefore(start.plusSeconds(SECONDS_PER_DAY)); time = time.plusSeconds(intervalSeconds)) {
            int second = (int) Duration.between(start, time).toSeconds();
            var load = loads.get(time);
            double desired = load == null ? DemoPhysiology.backgroundBpm(day, second / 60)
                    : day.restingBpm() + load.heartRateOffset();
            target += (desired - target) * (1 - Math.exp(-intervalSeconds / EFFORT_RESPONSE_SECONDS));
            double response = target > heartRate ? HEART_RATE_RISE_SECONDS : HEART_RATE_RECOVERY_SECONDS;
            heartRate += (target - heartRate) * (1 - Math.exp(-intervalSeconds / response));
            double persistence = Math.exp(-intervalSeconds / NOISE_RESPONSE_SECONDS);
            noise = noise * persistence + random.nextDouble(-NOISE_BPM, NOISE_BPM) * Math.sqrt(1 - persistence * persistence);
            int bpm = (int) Math.round(Math.max(MINIMUM_BPM, Math.min(MAXIMUM_BPM, heartRate + noise)));
            while (windowIndex < ranges.size() && ranges.get(windowIndex).end().isBefore(time)) windowIndex++;
            boolean gap = load == null && windowIndex < ranges.size() && !ranges.get(windowIndex).start().isAfter(time);
            if (!gap) {
                statistics.accept(bpm);
                if (detailed || load != null) samples.add(new DemoPhysiology.Sample(time, bpm));
            }
            if (load != null) {
                steps += load.steps();
                distance += load.distanceMeters();
                kcal += load.activeKcal();
            }
        }
        return new Result(List.copyOf(samples), statistics.getAverage(), statistics.getCount() == 0 ? day.restingBpm() : statistics.getMin(),
                statistics.getCount() == 0 ? day.restingBpm() : statistics.getMax(), steps, distance, kcal);
    }

    private static List<Window> merge(List<Window> windows) {
        List<Window> ranges = new ArrayList<>();
        for (Window range : windows.stream().sorted(Comparator.comparing(Window::start)).toList()) {
            if (ranges.isEmpty() || range.start().isAfter(ranges.getLast().end())) ranges.add(range);
            else {
                Window previous = ranges.removeLast();
                ranges.add(new Window(previous.start(), previous.end().isAfter(range.end()) ? previous.end() : range.end()));
            }
        }
        return ranges;
    }

    private DemoTimeline() {
    }
}
