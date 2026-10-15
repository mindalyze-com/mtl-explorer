package com.x8ing.mtl.server.mtlserver.googlehealth.demo;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.StableFingerprint;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Fictional demo parameters, not a model for estimating a real person's health.
 */
public final class DemoPhysiology {
    public static final int MAX_RECORDED_GAP_SECONDS = 60;
    private static final double RESTING_BPM = 58;
    private static final double MAXIMUM_BPM = 184;
    private static final double CYCLING_CAPACITY_WATTS = 280;
    private static final double FLAT_CYCLING_SPEED_KMH = 32;
    private static final double RUNNING_REFERENCE_SPEED_KMH = 20;
    private static final double WALKING_REFERENCE_SPEED_KMH = 13;
    private static final double WATER_REFERENCE_SPEED_KMH = 13;
    private static final double DRIVING_REFERENCE_SPEED_KMH = 100;
    private static final double MAX_GRADE = 0.25;
    private static final double MIN_GRADE_DISTANCE_METERS = 5;
    private static final double DRIFT_START_SECONDS = 20 * 60;
    private static final double DRIFT_MAX_BPM = 5;
    private static final double DRIFT_SECONDS = 60 * 60;
    private static final double SECONDS_PER_HOUR = 3600;
    private static final double SECONDS_PER_DAY = 86400;
    private static final double DEMO_WEIGHT_KG = 74;
    private static final double RUNNING_STEPS_PER_METER = 1.05;
    private static final double WALKING_STEPS_PER_METER = 1.35;
    static final int SELECTION_HEX_DIGITS = 8;
    static final int FULL_PERCENT = 100;
    static final long SELECTION_BUCKET_COUNT = 1L << 32;

    public record Point(Instant time, Double distanceMeters, Double altitudeMeters,
                        Double speedKmh, Double powerWatts, String motionState) {
    }

    public record Sample(Instant time, int bpm) {
    }

    public record Workload(Instant time, double heartRateOffset, double steps, double distanceMeters, double activeKcal) {
    }

    public record Day(double sleepHours, int restingBpm, long steps, double distanceKm,
                      double activeKcal, double weightKg, double readiness) {
    }

    public static long seed(long seed, String identity) {
        return Long.parseUnsignedLong(StableFingerprint.of(seed + ":" + identity).substring(0, 16), 16);
    }

    public static boolean selected(long seed, String identity, int percentage) {
        long bucket = Long.parseUnsignedLong(StableFingerprint.of(seed + ":selection:" + identity).substring(0, SELECTION_HEX_DIGITS), 16);
        return bucket * FULL_PERCENT < percentage * SELECTION_BUCKET_COUNT;
    }

    public static Day day(long seed, LocalDate date) {
        var random = new SplittableRandom(seed(seed, "day:" + date));
        double weekly = Math.sin(date.toEpochDay() * 2 * Math.PI / 7);
        double sleep = 7.4 + 0.45 * weekly + random.nextDouble(-0.7, 0.7);
        double readiness = clamp((sleep - 6) / 2.5 + random.nextDouble(-0.1, 0.1), 0.25, 0.95);
        int resting = (int) Math.round(RESTING_BPM + (0.65 - readiness) * 8 + random.nextDouble(-1, 1));
        long steps = Math.round(7000 + 2200 * weekly + random.nextDouble(-1800, 1800));
        double distance = steps / 1350.0;
        double active = 170 + steps * 0.035 + 100 * readiness;
        double weight = DEMO_WEIGHT_KG + 0.65 * Math.sin(date.toEpochDay() / 45.0) + random.nextDouble(-0.12, 0.12);
        return new Day(sleep, resting, steps, distance, active, weight, readiness);
    }

    /**
     * Track inputs only: the shared daily timeline owns the physiological response.
     */
    public static List<Workload> workload(long seed, String identity, GpsTrack.ACTIVITY_TYPE activity,
                                          List<Point> points, int intervalSeconds) {
        var loads = new java.util.TreeMap<Instant, Workload>();
        double scale = new SplittableRandom(seed(seed, "session:" + identity)).nextDouble(0.92, 1.08);
        double activeSeconds = 0;
        for (int i = 1; i < points.size(); i++) {
            Point from = points.get(i - 1), to = points.get(i);
            if (from.time() == null || to.time() == null) continue;
            double seconds = Duration.between(from.time(), to.time()).toMillis() / 1000.0;
            if (seconds <= 0 || seconds > MAX_RECORDED_GAP_SECONDS || "UNKNOWN".equals(to.motionState())) continue;
            boolean stopped = "STOPPED".equals(to.motionState());
            double speed = stopped ? 0 : usable(to.speedKmh()) ? Math.max(0, to.speedKmh())
                    : usable(from.distanceMeters()) && usable(to.distanceMeters())
                    ? Math.max(0, to.distanceMeters() - from.distanceMeters()) / seconds * 3.6 : 0;
            double distance = speed / 3.6 * seconds;
            double grade = usable(from.altitudeMeters()) && usable(to.altitudeMeters()) && distance > MIN_GRADE_DISTANCE_METERS
                    ? clamp((to.altitudeMeters() - from.altitudeMeters()) / distance, -MAX_GRADE, MAX_GRADE) : 0;
            double load = stopped ? 0 : effort(activity, speed, grade, to.powerWatts()) * scale;
            if (!stopped) activeSeconds += seconds;
            double drift = motorized(activity) ? 0 : clamp((activeSeconds - DRIFT_START_SECONDS) / DRIFT_SECONDS, 0, 1) * DRIFT_MAX_BPM;
            double offset = (motorized(activity) ? 14 + load * 18 : 9 + (MAXIMUM_BPM - RESTING_BPM - 9) * clamp(load, 0, 0.94)) + drift;
            long fromMillis = from.time().toEpochMilli();
            long gridMillis = intervalSeconds * 1000L;
            long firstMillis = Math.floorDiv(fromMillis, gridMillis) * gridMillis;
            for (Instant time = Instant.ofEpochMilli(firstMillis); time.isBefore(to.time()); time = time.plusSeconds(intervalSeconds)) {
                Instant coveredStart = time.isBefore(from.time()) ? from.time() : time;
                Instant coveredEnd = time.plusSeconds(intervalSeconds).isAfter(to.time()) ? to.time() : time.plusSeconds(intervalSeconds);
                double covered = Duration.between(coveredStart, coveredEnd).toMillis() / 1000.0;
                double meters = speed / 3.6 * covered;
                loads.merge(time, new Workload(time, offset, meters * stepsPerMeter(activity), motorized(activity) ? 0 : meters,
                                activeKcalPerHour(activity, speed, grade, load) * covered / SECONDS_PER_HOUR),
                        (previous, current) -> new Workload(current.time(), Math.max(previous.heartRateOffset(), current.heartRateOffset()),
                                previous.steps() + current.steps(), previous.distanceMeters() + current.distanceMeters(),
                                previous.activeKcal() + current.activeKcal()));
            }
            if (i == points.size() - 1 && to.time().toEpochMilli() % gridMillis == 0)
                loads.put(to.time(), new Workload(to.time(), offset, 0, 0, 0));
        }
        return List.copyOf(loads.values());
    }

    private static double effort(GpsTrack.ACTIVITY_TYPE activity, double speed, double grade, Double watts) {
        if (motorized(activity)) return clamp(speed / DRIVING_REFERENCE_SPEED_KMH, 0, 1);
        if (activity == GpsTrack.ACTIVITY_TYPE.BICYCLE || activity == GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING) {
            double load = usable(watts) ? Math.max(0, watts) / CYCLING_CAPACITY_WATTS
                    : Math.pow(speed / FLAT_CYCLING_SPEED_KMH, 3) + Math.max(grade, 0) * speed / 4;
            if (grade < -0.02 && !usable(watts)) load *= 0.3;
            return 1 - Math.exp(-load);
        }
        double reference = activity == GpsTrack.ACTIVITY_TYPE.RUNNING ? RUNNING_REFERENCE_SPEED_KMH
                : activity == GpsTrack.ACTIVITY_TYPE.WALKING || activity == GpsTrack.ACTIVITY_TYPE.HIKING
                ? WALKING_REFERENCE_SPEED_KMH : WATER_REFERENCE_SPEED_KMH;
        return clamp(speed / reference * Math.max(0.75, 1 + grade * 6), 0, 0.94);
    }

    public static boolean motorized(GpsTrack.ACTIVITY_TYPE type) {
        return type == null || type == GpsTrack.ACTIVITY_TYPE.CAR || type == GpsTrack.ACTIVITY_TYPE.MOTORBIKING
               || type == GpsTrack.ACTIVITY_TYPE.AIRPLANE || type == GpsTrack.ACTIVITY_TYPE.SUPER_SONIC;
    }

    private static double stepsPerMeter(GpsTrack.ACTIVITY_TYPE type) {
        return type == GpsTrack.ACTIVITY_TYPE.RUNNING ? RUNNING_STEPS_PER_METER
                : type == GpsTrack.ACTIVITY_TYPE.WALKING || type == GpsTrack.ACTIVITY_TYPE.HIKING ? WALKING_STEPS_PER_METER : 0;
    }

    private static final double RUNNING_NET_KCAL_PER_KG_KM = 0.9;
    private static final double WALKING_NET_KCAL_PER_KG_KM = 0.5;
    private static final double UPHILL_ENERGY_FACTOR = 6;
    private static final double CYCLING_EFFICIENCY = 0.24;
    private static final double WATTS_TO_KCAL_PER_HOUR = 0.8604;

    private static double activeKcalPerHour(GpsTrack.ACTIVITY_TYPE type, double speed, double grade, double effort) {
        if (motorized(type) || speed <= 0) return 0;
        if (type == GpsTrack.ACTIVITY_TYPE.RUNNING)
            return DEMO_WEIGHT_KG * speed * RUNNING_NET_KCAL_PER_KG_KM * Math.max(0.75, 1 + grade * UPHILL_ENERGY_FACTOR);
        if (type == GpsTrack.ACTIVITY_TYPE.WALKING || type == GpsTrack.ACTIVITY_TYPE.HIKING)
            return DEMO_WEIGHT_KG * speed * (WALKING_NET_KCAL_PER_KG_KM + Math.max(0, grade) * UPHILL_ENERGY_FACTOR);
        if (type == GpsTrack.ACTIVITY_TYPE.BICYCLE || type == GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING)
            return -Math.log(1 - clamp(effort, 0, 0.94)) * CYCLING_CAPACITY_WATTS * WATTS_TO_KCAL_PER_HOUR / CYCLING_EFFICIENCY;
        return DEMO_WEIGHT_KG * Math.max(0, effort) * 9;
    }

    public static int backgroundBpm(long seed, LocalDate date, int minuteOfDay) {
        return backgroundBpm(day(seed, date), minuteOfDay);
    }

    public static int backgroundBpm(Day day, int minuteOfDay) {
        double phase = minuteOfDay / (SECONDS_PER_DAY / 60.0);
        boolean sleeping = minuteOfDay < Math.round((day.sleepHours() - 1) * 60) || minuteOfDay >= 23 * 60;
        double exercise = minuteOfDay >= 17 * 60 && minuteOfDay < 18 * 60 ? 55 * Math.sin((minuteOfDay - 17 * 60) * Math.PI / 60) : 0;
        double variation = 2 * Math.sin(minuteOfDay / 9.0) + Math.sin(minuteOfDay / 3.0);
        return (int) Math.round(day.restingBpm() + (sleeping ? -3 : 15 + 7 * Math.sin(phase * Math.PI)) + exercise + variation);
    }

    private static boolean usable(Double value) {
        return value != null && Double.isFinite(value);
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private DemoPhysiology() {
    }
}
