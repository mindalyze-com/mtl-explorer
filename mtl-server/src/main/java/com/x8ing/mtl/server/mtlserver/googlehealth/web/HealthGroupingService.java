package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.HeartRateDay;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.GroupedMetricRecordRow;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.HeartRateTimeRange;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@Slf4j
public class HealthGroupingService {
    private static final int AUTO_BUCKET_LIMIT = 40;
    private static final int MAX_CALENDAR_BUCKETS = 2_000;
    private static final int DETAILED_HEART_RATE_DAYS = 31;
    private static final int SHORT_HEART_RATE_DAYS = 7;
    private static final int QUARTER_HOUR_SECONDS = 15 * 60;
    private static final int HOUR_SECONDS = 60 * 60;
    private static final String HEART_RATE = "heart-rate";
    private static final int SECOND_BUCKET_SECONDS = 1;
    private final MirrorRepository repository;
    private final ObjectMapper objectMapper;

    public HealthGroupingService(MirrorRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public GHGroupedMetrics grouped(String startDate, String endDate, GHGrouping requested, String timeZone) {
        return grouped(startDate, endDate, requested, timeZone, Clock.systemUTC());
    }

    GHGroupedMetrics grouped(String startDate, String endDate, GHGrouping requested, String timeZone, Clock clock) {
        LocalDate start = parseDate(startDate, "startDate");
        LocalDate end = parseDate(endDate, "endDate");
        if (!start.isBefore(end)) throw invalid("endDate must be after startDate (exclusive).");
        if (requested == null) throw invalid("groupBy must be AUTO, SECOND, DAY, WEEK, MONTH or YEAR.");
        if (timeZone == null || !ZoneId.getAvailableZoneIds().contains(timeZone)) {
            throw invalid("timeZone must be a valid IANA timezone.");
        }
        ZoneId zone = ZoneId.of(timeZone);
        GHGrouping grouping = resolve(start, end, requested);
        requireBucketBudget(start, end, grouping);
        long started = System.nanoTime();
        log.info("Starting Google Health aggregation: start={}, end={}, grouping={}, resolved={}, timeZone={}",
                start, end, requested, grouping, timeZone);
        try {
            GHGroupedMetrics result = aggregate(start, end, requested, grouping, zone, LocalDate.now(clock.withZone(zone)));
            log.info("Completed Google Health aggregation: series={}, buckets={}, availableDays={}, durationMs={}",
                    result.series().size(), result.series().stream().mapToInt(s -> s.buckets().size()).sum(),
                    result.series().stream().flatMap(s -> s.buckets().stream()).mapToInt(GHMetricBucket::availableDays).sum(),
                    (System.nanoTime() - started) / 1_000_000);
            return result;
        } catch (RuntimeException exception) {
            log.error("Google Health aggregation failed: start={}, end={}, grouping={}, errorType={}",
                    start, end, grouping, exception.getClass().getSimpleName(), exception);
            throw exception;
        }
    }

    private GHGroupedMetrics aggregate(LocalDate start, LocalDate end, GHGrouping requested,
                                       GHGrouping grouping, ZoneId zone, LocalDate today) {
        Map<String, TreeMap<LocalDate, DayValues>> values = new HashMap<>();
        int malformed = 0;
        boolean secondGrouping = grouping == GHGrouping.SECOND;
        Instant instantStart = start.atStartOfDay(zone).toInstant();
        Instant instantEnd = end.atStartOfDay(zone).toInstant();
        var records = secondGrouping
                ? repository.findIntradayMetricRecords(instantStart, instantEnd).stream()
                .map(row -> new GroupedMetricRecordRow(
                        row.recordTime().atZone(zone).toLocalDate(), row)).toList()
                : repository.findGroupedMetricRecords(start, end, zone.getId());
        for (var row : records) {
            try {
                var point = HealthQueryService.metricPoint(row.record(), objectMapper, true);
                if (point.isEmpty()) continue;
                values.computeIfAbsent(row.record().dataType(), ignored -> new TreeMap<>())
                        .computeIfAbsent(row.date(), ignored -> new DayValues()).add(point.get());
            } catch (RuntimeException exception) {
                malformed++;
                log.warn("Unreadable Google Health grouped metric: dataType={}, date={}, errorType={}, reason=invalid stored metric payload",
                        row.record().dataType(), row.date(), exception.getClass().getSimpleName());
                log.debug("Google Health grouped metric parse failure", exception);
            }
        }
        if (malformed > 0) log.info("Google Health aggregation skipped malformed records: count={}", malformed);
        if (secondGrouping) {
            for (var point : repository.findHeartRateBuckets(instantStart, instantEnd, SECOND_BUCKET_SECONDS)) {
                values.computeIfAbsent(HEART_RATE, ignored -> new TreeMap<>())
                        .computeIfAbsent(point.time().atZone(zone).toLocalDate(), ignored -> new DayValues())
                        .add(new GHMetricPoint(point.time(), point.average(),
                                (double) point.minimum(), (double) point.maximum()));
            }
        }
        var rollupDays = values.getOrDefault(HEART_RATE, new TreeMap<>());
        var missingRanges = secondGrouping ? List.<HeartRateTimeRange>of()
                : missingRollupRanges(start, end, zone, rollupDays.navigableKeySet());
        log.info("Google Health aggregation heart-rate source: rollupDays={}, fallbackRanges={}, detailed={}",
                rollupDays.size(), missingRanges.size(), requested == GHGrouping.AUTO
                                                         && ChronoUnit.DAYS.between(start, end) <= DETAILED_HEART_RATE_DAYS);
        var fallbackDays = missingRanges.isEmpty() ? List.<HeartRateDay>of()
                : repository.findHeartRateDaysForRanges(missingRanges, zone.getId());
        for (var day : fallbackDays) {
            // A valid daily rollup owns its date. Samples only supply missing rollup dates.
            values.computeIfAbsent(HEART_RATE, ignored -> new TreeMap<>()).computeIfAbsent(day.date(), ignored -> {
                DayValues result = new DayValues();
                result.add(new GHMetricPoint(day.date().atStartOfDay(zone).toInstant(),
                        day.average(), day.minimum(), day.maximum()));
                return result;
            });
        }
        boolean detailed = requested == GHGrouping.AUTO && ChronoUnit.DAYS.between(start, end) <= DETAILED_HEART_RATE_DAYS;
        List<GHMetricPoint> detailedPoints = new ArrayList<>();
        if (detailed && values.containsKey(HEART_RATE)) {
            int seconds = ChronoUnit.DAYS.between(start, end) <= SHORT_HEART_RATE_DAYS ? QUARTER_HOUR_SECONDS : HOUR_SECONDS;
            var sampledDates = new java.util.HashSet<LocalDate>();
            for (var point : repository.findHeartRateBuckets(instantStart, instantEnd, seconds)) {
                // date_bin can begin before the requested boundary in fractional-offset zones.
                Instant time = point.time().isBefore(instantStart) ? instantStart : point.time();
                sampledDates.add(time.atZone(zone).toLocalDate());
                detailedPoints.add(new GHMetricPoint(time, point.average(), (double) point.minimum(), (double) point.maximum()));
            }
            values.get(HEART_RATE).forEach((date, day) -> {
                if (!sampledDates.contains(date)) detailedPoints.add(new GHMetricPoint(
                        date.atStartOfDay(zone).toInstant(), day.mean(), day.minimum, day.maximum));
            });
            detailedPoints.sort(Comparator.comparing(GHMetricPoint::time));
        }
        List<GHGroupedSeries> series = new ArrayList<>();
        for (var definition : HealthQueryService.METRIC_DEFINITIONS) {
            var days = values.get(definition.dataType());
            if (days == null || days.isEmpty()) continue;
            var family = definition.category() == GHMetricSeries.Category.ACTIVITY ? GHGroupedSeries.Family.ACTIVITY
                    : definition.category() == GHMetricSeries.Category.SLEEP ? GHGroupedSeries.Family.SLEEP
                    : GHGroupedSeries.Family.MEASUREMENT;
            List<GHMetricBucket> buckets = new ArrayList<>();
            for (LocalDate periodStart = floor(start, grouping); periodStart.isBefore(end); periodStart = next(periodStart, grouping)) {
                LocalDate periodEnd = next(periodStart, grouping);
                LocalDate bucketStart = periodStart.isBefore(start) ? start : periodStart;
                LocalDate bucketEnd = periodEnd.isAfter(end) ? end : periodEnd;
                buckets.add(bucket(bucketStart, bucketEnd, periodStart, periodEnd, today,
                        days.subMap(bucketStart, true, bucketEnd, false), family, grouping));
            }
            boolean isDetailed = (detailed || secondGrouping) && definition.dataType().equals(HEART_RATE);
            series.add(new GHGroupedSeries(definition.dataType(), definition.title(), definition.category(),
                    definition.unit(), definition.decimals(), family, grouping, isDetailed, buckets,
                    secondGrouping ? secondPoints(days) : isDetailed ? detailedPoints : List.of()));
        }
        return new GHGroupedMetrics(start.toString(), end.toString(), zone.getId(), requested, grouping, series);
    }

    static List<HeartRateTimeRange> missingRollupRanges(LocalDate start, LocalDate end, ZoneId zone,
                                                        java.util.SortedSet<LocalDate> rollupDays) {
        List<HeartRateTimeRange> ranges = new ArrayList<>();
        LocalDate cursor = start;
        for (LocalDate date : rollupDays) {
            if (date.isBefore(start) || !date.isBefore(end)) continue;
            if (cursor.isBefore(date)) ranges.add(new HeartRateTimeRange(
                    cursor.atStartOfDay(zone).toInstant(), date.atStartOfDay(zone).toInstant()));
            cursor = date.plusDays(1);
        }
        if (cursor.isBefore(end)) ranges.add(new HeartRateTimeRange(
                cursor.atStartOfDay(zone).toInstant(), end.atStartOfDay(zone).toInstant()));
        return ranges;
    }

    private static GHMetricBucket bucket(LocalDate start, LocalDate end, LocalDate periodStart, LocalDate periodEnd,
                                         LocalDate today, Map<LocalDate, DayValues> days,
                                         GHGroupedSeries.Family family, GHGrouping grouping) {
        boolean cumulative = family != GHGroupedSeries.Family.MEASUREMENT;
        double sum = 0;
        Double minimum = null, maximum = null;
        LocalDate minimumDate = null, maximumDate = null;
        List<GHMetricPoint> sessions = new ArrayList<>();
        for (var entry : days.entrySet()) {
            DayValues day = entry.getValue();
            double daily = cumulative ? day.sum : day.mean();
            sum += daily;
            double low = cumulative ? daily : day.minimum;
            double high = cumulative ? daily : day.maximum;
            // Sorted dates plus strict comparison break ties at the earliest date.
            if (minimum == null || low < minimum) {
                minimum = low;
                minimumDate = entry.getKey();
            }
            if (maximum == null || high > maximum) {
                maximum = high;
                maximumDate = entry.getKey();
            }
            // The repository assigns each complete session to its start date in the requested viewing zone.
            // Cross-midnight sessions are not split; naps on the same date add to that day's sleep.
            if (family == GHGroupedSeries.Family.SLEEP && grouping == GHGrouping.DAY) sessions.addAll(day.points);
        }
        return new GHMetricBucket(start.toString(), end.toString(), periodStart.toString(), periodEnd.toString(),
                cumulative && !days.isEmpty() ? sum : null, days.isEmpty() ? null : sum / days.size(),
                minimum, maximum, minimumDate == null ? null : minimumDate.toString(),
                maximumDate == null ? null : maximumDate.toString(), days.size(), (int) ChronoUnit.DAYS.between(start, end),
                !today.isBefore(start) && today.isBefore(end), !start.equals(periodStart) || !end.equals(periodEnd), sessions);
    }

    private static void requireBucketBudget(LocalDate start, LocalDate end, GHGrouping grouping) {
        if (grouping == GHGrouping.SECOND) {
            if (!end.equals(start.plusDays(1))) throw invalid("Choose one day for second grouping.");
            return;
        }
        LocalDate first = floor(start, grouping);
        LocalDate last = floor(end.minusDays(1), grouping);
        long periods = switch (grouping) {
            case SECOND, DAY -> ChronoUnit.DAYS.between(first, last) + 1;
            case WEEK -> ChronoUnit.WEEKS.between(first, last) + 1;
            case MONTH -> ChronoUnit.MONTHS.between(first, last) + 1;
            case YEAR -> ChronoUnit.YEARS.between(first, last) + 1;
            case AUTO -> throw new IllegalArgumentException("Resolve Auto before counting buckets");
        };
        if (periods > MAX_CALENDAR_BUCKETS) {
            log.warn("Rejected Google Health aggregation exceeding its bucket budget: grouping={}, buckets={}, maximum={}",
                    grouping, periods, MAX_CALENDAR_BUCKETS);
            throw invalid("At most " + MAX_CALENDAR_BUCKETS
                          + " calendar buckets can be requested. Select a shorter range or a coarser grouping.");
        }
    }

    static GHGrouping resolve(LocalDate start, LocalDate end, GHGrouping requested) {
        if (requested != GHGrouping.AUTO) return requested;
        for (var grouping : List.of(GHGrouping.DAY, GHGrouping.WEEK, GHGrouping.MONTH)) {
            LocalDate boundary = floor(start, grouping);
            for (int count = 0; count < AUTO_BUCKET_LIMIT && boundary.isBefore(end); count++) boundary = next(boundary, grouping);
            if (!boundary.isBefore(end)) return grouping;
        }
        return GHGrouping.YEAR;
    }

    static LocalDate floor(LocalDate date, GHGrouping grouping) {
        return switch (grouping) {
            case SECOND, DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
            case YEAR -> date.withDayOfYear(1);
            case AUTO -> throw new IllegalArgumentException("Resolve Auto before grouping");
        };
    }

    private static LocalDate next(LocalDate date, GHGrouping grouping) {
        return switch (grouping) {
            case SECOND, DAY -> date.plusDays(1);
            case WEEK -> date.plusWeeks(1);
            case MONTH -> date.plusMonths(1);
            case YEAR -> date.plusYears(1);
            case AUTO -> throw new IllegalArgumentException("Resolve Auto before grouping");
        };
    }

    private static List<GHMetricPoint> secondPoints(Map<LocalDate, DayValues> days) {
        Map<Instant, DayValues> seconds = new TreeMap<>();
        for (var day : days.values()) for (var point : day.points) {
            seconds.computeIfAbsent(point.time().truncatedTo(ChronoUnit.SECONDS), ignored -> new DayValues()).add(point);
        }
        return seconds.entrySet().stream().map(entry -> new GHMetricPoint(
                entry.getKey(), entry.getValue().mean(), entry.getValue().minimum, entry.getValue().maximum)).toList();
    }

    private static LocalDate parseDate(String value, String name) {
        try {
            if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new DateTimeException("Expected ISO date");
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1 || date.getYear() > 9998) throw new DateTimeException("Date outside supported calendar");
            return date;
        } catch (DateTimeException exception) {
            log.warn("Rejected Google Health grouping date: field={}, errorType={}, reason=invalid ISO calendar date",
                    name, exception.getClass().getSimpleName());
            throw invalid(name + " must be a valid YYYY-MM-DD calendar date (years 0001–9998).");
        }
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static final class DayValues {
        double sum;
        int count;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        final List<GHMetricPoint> points = new ArrayList<>();

        void add(GHMetricPoint point) {
            sum += point.value();
            count++;
            minimum = Math.min(minimum, point.minimum() != null && Double.isFinite(point.minimum()) ? point.minimum() : point.value());
            maximum = Math.max(maximum, point.maximum() != null && Double.isFinite(point.maximum()) ? point.maximum() : point.value());
            points.add(point);
        }

        double mean() {
            return sum / count;
        }
    }
}
