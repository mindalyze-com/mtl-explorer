package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Native bounds are encoded as UTC components only for transport, never as user local time.
 */
public final class SyncWindowPolicy {
    public static final String KEY_POLICY = "identity-v2";
    public static final String CALENDAR_POLICY = "utc-physical-civil-minus12-plus14-v1";
    public static final Duration CIVIL_LOWER_PADDING = Duration.ofHours(12);
    public static final Duration CIVIL_UPPER_PADDING = Duration.ofHours(14);
    public static final Duration HEART_RATE_GRID = Duration.ofHours(6);
    public static final Duration MIN_CHUNK = Duration.ofMinutes(1);

    public record Bounds(DataTypePolicy.WindowTimeDomain domain, Instant start, Instant end) {
        public Bounds {
            start = start.truncatedTo(ChronoUnit.MICROS);
            end = end.truncatedTo(ChronoUnit.MICROS);
            if (!start.isBefore(end)) throw new IllegalArgumentException("Empty native range");
        }

        public LocalDateTime civilStart() {
            return LocalDateTime.ofInstant(start, ZoneOffset.UTC);
        }

        public LocalDateTime civilEnd() {
            return LocalDateTime.ofInstant(end, ZoneOffset.UTC);
        }

        public LocalDate day() {
            return civilStart().toLocalDate();
        }
    }

    public static LocalDate cutoff(ConnectorProperties p, Instant now) {
        return now.atOffset(ZoneOffset.UTC).toLocalDate().minusDays(p.getRecentWindow().getDays());
    }

    public static Bounds recent(DataTypePolicy.Policy policy, ConnectorProperties p, Instant now, boolean fast) {
        Instant lower = midnight(cutoff(p, now).isBefore(p.getHistoryStartDate())
                ? p.getHistoryStartDate() : cutoff(p, now));
        Instant upper = now;
        if (policy.windowTimeDomain() != DataTypePolicy.WindowTimeDomain.PHYSICAL) {
            upper = now.plus(CIVIL_UPPER_PADDING);
        }
        if (fast) {
            Instant fastLower = now.minus(p.getRecentFastWindow());
            if (policy.windowTimeDomain() != DataTypePolicy.WindowTimeDomain.PHYSICAL) {
                fastLower = fastLower.minus(CIVIL_LOWER_PADDING);
            }
            lower = lower.isAfter(fastLower) ? lower : fastLower;
        }
        if (policy.windowTimeDomain() == DataTypePolicy.WindowTimeDomain.DATE) {
            lower = midnight(lower.atOffset(ZoneOffset.UTC).toLocalDate());
            // Always the day AFTER the date of the civil upper bound, including exact midnight.
            upper = midnight(upper.atOffset(ZoneOffset.UTC).toLocalDate().plusDays(1));
        }
        return new Bounds(policy.windowTimeDomain(), lower, upper);
    }

    public static Instant midnight(LocalDate day) {
        return day.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public static Instant completion(DataTypePolicy.Policy policy, LocalDate day) {
        Instant end = midnight(day.plusDays(1));
        return policy.windowTimeDomain() == DataTypePolicy.WindowTimeDomain.PHYSICAL
                ? end : end.plus(CIVIL_LOWER_PADDING);
    }

    public static Duration grid(DataTypePolicy.Policy policy) {
        return policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE ? HEART_RATE_GRID : Duration.ofDays(1);
    }

    public static List<Bounds> cells(DataTypePolicy.Policy policy, Bounds bounds) {
        List<Bounds> result = new ArrayList<>();
        Duration step = grid(policy);
        Instant day = midnight(bounds.start.atOffset(ZoneOffset.UTC).toLocalDate());
        long index = Duration.between(day, bounds.start).toSeconds() / step.toSeconds();
        Instant cell = day.plus(step.multipliedBy(index));
        while (cell.isBefore(bounds.end)) {
            Instant start = cell.isBefore(bounds.start) ? bounds.start : cell;
            Instant end = cell.plus(step).isAfter(bounds.end) ? bounds.end : cell.plus(step);
            result.add(new Bounds(bounds.domain, start, end));
            cell = cell.plus(step);
        }
        Collections.reverse(result);
        return List.copyOf(result);
    }

    public static Instant split(Bounds bounds) {
        if (bounds.domain == DataTypePolicy.WindowTimeDomain.DATE) return null;
        long micros = ChronoUnit.MICROS.between(bounds.start, bounds.end);
        if (micros < MIN_CHUNK.toNanos() / 1000 * 2) return null;
        return bounds.start.plus(micros / 2, ChronoUnit.MICROS);
    }

    private SyncWindowPolicy() {
    }
}
