package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.*;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HealthGroupingServiceTest {
    private MirrorRepository repository;
    private HealthGroupingService service;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setup() {
        repository = mock(MirrorRepository.class);
        service = new HealthGroupingService(repository, new ObjectMapper());
    }

    @Test
    void secondGroupingReusesHeartRateBucketsAndAggregatesTimestampedMeasurements() {
        var time = Instant.parse("2026-09-03T12:00:12Z");
        when(repository.findIntradayMetricRecords(any(), any())).thenReturn(List.of(
                new MetricRecordRow("weight", "RAW", time.plusMillis(123), null, "{\"weight\":{\"weightGrams\":71500}}"),
                new MetricRecordRow("weight", "RAW", time.plusMillis(456), null, "{\"weight\":{\"weightGrams\":72500}}")));
        when(repository.findHeartRateBuckets(any(), any(), eq(1))).thenReturn(List.of(
                new HeartRateBucket(time, 70, 60, 80), new HeartRateBucket(time.plusSeconds(1), 65, 65, 65)));
        var result = query("2026-09-03", "2026-09-04", GHGrouping.SECOND);
        assertThat(result.resolvedGrouping()).isEqualTo(GHGrouping.SECOND);
        assertThat(result.series()).extracting(GHGroupedSeries::dataType).containsExactly("heart-rate", "weight");
        assertThat(result.series().getFirst().detailedPoints()).containsExactly(
                new GHMetricPoint(time, 70, 60d, 80d), new GHMetricPoint(time.plusSeconds(1), 65, 65d, 65d));
        assertThat(result.series().getLast().detailedPoints()).containsExactly(new GHMetricPoint(time, 72, 71.5, 72.5));
        assertThat(result.series().getLast().buckets()).singleElement().satisfies(bucket -> {
            assertThat(bucket.selectedDays()).isEqualTo(1);
            assertThat(bucket.minimum()).isEqualTo(71.5);
            assertThat(bucket.maximum()).isEqualTo(72.5);
        });
        verify(repository, never()).findGroupedMetricRecords(any(), any(), any());
        verify(repository, never()).findHeartRateDaysForRanges(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"2026-03-29,2026-03-30,2026-03-28T23:00:00Z,2026-03-29T22:00:00Z",
            "2026-10-25,2026-10-26,2026-10-24T22:00:00Z,2026-10-25T23:00:00Z"})
    void secondGroupingUsesLocalDaylightSavingBoundaries(String start, String end, String first, String last) {
        service.grouped(start, end, GHGrouping.SECOND, "Europe/Zurich", CLOCK);
        verify(repository).findHeartRateBuckets(Instant.parse(first), Instant.parse(last), 1);
        verify(repository).findIntradayMetricRecords(Instant.parse(first), Instant.parse(last));
    }

    @Test
    void secondGroupingRejectsMultipleDaysBeforeReadingTheMirror() {
        assertThatThrownBy(() -> query("2026-09-01", "2026-09-03", GHGrouping.SECOND))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Choose one day");
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @CsvSource({"steps,countSum,100,100", "distance,millimetersSum,1500000,1.5",
            "active-energy-burned,kcalSum,125.5,125.5", "daily-resting-heart-rate,beatsPerMinute,60,60",
            "daily-heart-rate-variability,averageHeartRateVariabilityMilliseconds,31.2,31.2",
            "daily-oxygen-saturation,averagePercentage,98.5,98.5", "daily-respiratory-rate,breathsPerMinute,15,15",
            "weight,weightGrams,70250,70.25", "body-fat,percentage,20,20", "daily-vo2-max,vo2Max,48,48",
            "daily-sleep-temperature-derivations,nightlyTemperatureCelsius,35.3,35.3",
            "core-body-temperature,temperatureCelsius,36.7,36.7", "blood-glucose,bloodGlucoseMilligramsPerDeciliter,92,92"})
    void everyMetricUsesItsUnitsAndKeepsEmptyDaysMissing(String type, String field, double input, double expected) {
        rows(row(type, "2026-09-03", field, input));
        var series = query("2026-09-02", "2026-09-05", GHGrouping.DAY).series().getFirst();
        assertThat(series.buckets().getFirst().average()).isNull();
        assertThat(series.buckets().get(1).average()).isEqualTo(expected);
        assertThat(series.buckets().get(1).minimum()).isEqualTo(expected);
        assertThat(series.buckets().getLast().inProgress()).isTrue();
        assertThat(series.buckets().getLast().availableDays()).isZero();
    }

    @Test
    void totalsIncludeTodayAndRecordedZeroButNotMissingDaysAndResolveTiesToFirstDate() {
        rows(row("steps", "2026-09-01", "countSum", 100), row("steps", "2026-09-02", "countSum", 0),
                row("steps", "2026-09-04", "countSum", 100));
        var bucket = query("2026-09-01", "2026-09-05", GHGrouping.WEEK).series().getFirst().buckets().getFirst();
        assertThat(bucket.total()).isEqualTo(200);
        assertThat(bucket.average()).isCloseTo(200d / 3, within(1e-12));
        assertThat(bucket.minimum()).isZero();
        assertThat(bucket.minimumDate()).isEqualTo("2026-09-02");
        assertThat(bucket.maximumDate()).isEqualTo("2026-09-01");
        assertThat(bucket.availableDays()).isEqualTo(3);
        assertThat(bucket.selectedDays()).isEqualTo(4);
        assertThat(bucket.inProgress()).isTrue();
        assertThat(bucket.clipped()).isTrue();
        assertThat(bucket.periodStartDate()).isEqualTo("2026-08-31");
        assertThat(bucket.periodEndDate()).isEqualTo("2026-09-07");
    }

    @Test
    void measurementsAverageDailyMeansAndRetainObservedExtrema() {
        rows(row("weight", "2026-09-01", "weightGrams", 70000), row("weight", "2026-09-01", "weightGrams", 72000),
                row("weight", "2026-09-04", "weightGrams", 74000));
        var bucket = query("2026-09-01", "2026-09-05", GHGrouping.MONTH).series().getFirst().buckets().getFirst();
        assertThat(bucket.total()).isNull();
        assertThat(bucket.average()).isEqualTo(72.5);
        assertThat(bucket.minimum()).isEqualTo(70);
        assertThat(bucket.maximum()).isEqualTo(74);
        assertThat(bucket.minimumDate()).isEqualTo("2026-09-01");
        assertThat(bucket.maximumDate()).isEqualTo("2026-09-04");
    }

    @Test
    void sleepAddsSessionsAndNapsBeforeAveragingDaysAndPreservesDailySessions() {
        rows(sleep("2026-09-02", 360), sleep("2026-09-02", 60), sleep("2026-09-04", 480));
        var daily = query("2026-09-02", "2026-09-05", GHGrouping.DAY).series().getFirst();
        assertThat(daily.buckets().getFirst().total()).isEqualTo(7);
        assertThat(daily.buckets().getFirst().sessions()).hasSize(2);
        var weekly = query("2026-09-02", "2026-09-05", GHGrouping.WEEK).series().getFirst().buckets().getFirst();
        assertThat(weekly.total()).isEqualTo(15);
        assertThat(weekly.average()).isEqualTo(7.5);
        assertThat(weekly.minimum()).isEqualTo(7);
        assertThat(weekly.maximum()).isEqualTo(8);
        assertThat(weekly.sessions()).isEmpty();
    }

    @Test
    void sleepDurationFallbackKeepsFractionalMinutesUnrounded() {
        Instant time = Instant.parse("2026-09-02T01:00:00Z");
        rows(new GroupedMetricRecordRow(LocalDate.parse("2026-09-02"), new MetricRecordRow(
                "sleep", "RAW", time, time.plusSeconds(90).plusMillis(500), "{\"sleep\":{}}")));
        var bucket = query("2026-09-02", "2026-09-03", GHGrouping.DAY).series().getFirst().buckets().getFirst();
        assertThat(bucket.total()).isCloseTo(90.5 / 3600, within(1e-12));
    }

    @Test
    void heartRatePrefersRollupsPerDateAndUsesSampleDailyStatisticsForMissingDates() {
        rows(new GroupedMetricRecordRow(LocalDate.parse("2026-09-02"), new MetricRecordRow("heart-rate", "DAILY_ROLLUP", null, null,
                "{\"heartRate\":{\"beatsPerMinuteAvg\":60,\"beatsPerMinuteMin\":45,\"beatsPerMinuteMax\":90}}")));
        when(repository.findHeartRateDaysForRanges(anyList(), anyString())).thenReturn(List.of(
                new HeartRateDay(LocalDate.parse("2026-09-02"), 100, 80, 140),
                new HeartRateDay(LocalDate.parse("2026-09-03"), 80, 50, 120)));
        var bucket = query("2026-09-01", "2026-09-05", GHGrouping.WEEK).series().getFirst().buckets().getFirst();
        assertThat(bucket.average()).isEqualTo(70);
        assertThat(bucket.minimum()).isEqualTo(45);
        assertThat(bucket.maximum()).isEqualTo(120);
        assertThat(bucket.availableDays()).isEqualTo(2);
        verify(repository, never()).findHeartRateBuckets(any(), any(), anyInt());
    }

    @Test
    void completeDailyRollupsDoNotReadSamplesForAnyExplicitCalendarGrouping() {
        rows(new GroupedMetricRecordRow(LocalDate.parse("2026-09-02"), new MetricRecordRow("heart-rate", "DAILY_ROLLUP", null, null,
                "{\"heartRate\":{\"beatsPerMinuteAvg\":60,\"beatsPerMinuteMin\":45,\"beatsPerMinuteMax\":90}}")));
        for (var grouping : List.of(GHGrouping.DAY, GHGrouping.WEEK, GHGrouping.MONTH, GHGrouping.YEAR)) {
            var bucket = query("2026-09-02", "2026-09-03", grouping).series().getFirst().buckets().getFirst();
            assertThat(bucket.average()).isEqualTo(60);
            assertThat(bucket.minimum()).isEqualTo(45);
            assertThat(bucket.maximum()).isEqualTo(90);
        }
        verify(repository, never()).findHeartRateDaysForRanges(anyList(), anyString());
        verify(repository, never()).findHeartRateBuckets(any(), any(), anyInt());
    }

    @Test
    void onlyContiguousMissingRollupDatesBecomeSampleQueriesIncludingDstBoundaries() {
        var ranges = HealthGroupingService.missingRollupRanges(LocalDate.parse("2026-03-27"), LocalDate.parse("2026-03-31"),
                ZoneId.of("Europe/Zurich"), new java.util.TreeSet<>(List.of(LocalDate.parse("2026-03-27"), LocalDate.parse("2026-03-30"))));
        assertThat(ranges).containsExactly(new HeartRateTimeRange(
                Instant.parse("2026-03-27T23:00:00Z"), Instant.parse("2026-03-29T22:00:00Z")));
    }

    @Test
    void autoPreservesDetailedHeartRateIncludingDatesWithOnlyRollups() {
        rows(new GroupedMetricRecordRow(LocalDate.parse("2026-09-02"), new MetricRecordRow("heart-rate", "DAILY_ROLLUP", null, null,
                "{\"heartRate\":{\"beatsPerMinuteAvg\":60}}")));
        when(repository.findHeartRateBuckets(any(), any(), anyInt())).thenReturn(List.of(
                new HeartRateBucket(Instant.parse("2026-09-03T12:00:00Z"), 70, 50, 100)));
        var series = query("2026-09-01", "2026-09-05", GHGrouping.AUTO).series().getFirst();
        assertThat(series.detailedHeartRate()).isTrue();
        assertThat(series.detailedPoints()).hasSize(2);
        assertThat(series.detailedPoints().getFirst().value()).isEqualTo(60);
        assertThat(series.detailedPoints().getLast().value()).isEqualTo(70);
        assertThat(query("2026-08-01", "2026-09-05", GHGrouping.AUTO).series().getFirst().detailedHeartRate()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"2026-01-01,2026-02-10,DAY", "2026-01-01,2026-02-11,WEEK",
            "2026-01-01,2027-01-01,MONTH", "2020-01-01,2026-01-01,YEAR"})
    void autoChoosesFinestCalendarGroupingWithinFortyBuckets(String start, String end, GHGrouping expected) {
        assertThat(HealthGroupingService.resolve(LocalDate.parse(start), LocalDate.parse(end), GHGrouping.AUTO)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"WEEK,2020-12-28,2021-01-04", "MONTH,2020-12-01,2021-01-01", "YEAR,2020-01-01,2021-01-01"})
    void calendarBoundariesCrossYears(GHGrouping group, String firstStart, String firstEnd) {
        rows(row("steps", "2021-01-01", "countSum", 1));
        var buckets = query("2020-12-31", "2021-01-06", group).series().getFirst().buckets();
        assertThat(buckets.getFirst().periodStartDate()).isEqualTo(firstStart);
        assertThat(buckets.getFirst().periodEndDate()).isEqualTo(firstEnd);
        assertThat(buckets.getFirst().startDate()).isEqualTo("2020-12-31");
        assertThat(buckets.getLast().endDate()).isEqualTo("2021-01-06");
    }

    @Test
    void leapDayRemainsOneCivilDate() {
        rows(row("steps", "2024-02-29", "countSum", 42));
        var bucket = query("2024-02-01", "2024-03-01", GHGrouping.MONTH).series().getFirst().buckets().getFirst();
        assertThat(bucket.selectedDays()).isEqualTo(29);
        assertThat(bucket.clipped()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"Europe/Zurich,2026-03-28T23:00:00Z,2026-03-29T22:00:00Z",
            "America/Los_Angeles,2026-03-29T07:00:00Z,2026-03-30T07:00:00Z",
            "Pacific/Kiritimati,2026-03-28T10:00:00Z,2026-03-29T10:00:00Z"})
    void timezoneBoundariesUseCalendarDatesAcrossDst(String zone, String startInstant, String endInstant) {
        service.grouped("2026-03-29", "2026-03-30", GHGrouping.DAY, zone, CLOCK);
        verify(repository).findGroupedMetricRecords(LocalDate.parse("2026-03-29"), LocalDate.parse("2026-03-30"), zone);
        verify(repository).findHeartRateDaysForRanges(List.of(new HeartRateTimeRange(Instant.parse(startInstant), Instant.parse(endInstant))), zone);
    }

    @Test
    void badPayloadIsSkippedWhileValidZeroSurvives() {
        rows(new GroupedMetricRecordRow(LocalDate.parse("2026-09-01"), new MetricRecordRow("steps", "DAILY_ROLLUP", null, null, "{")),
                row("steps", "2026-09-04", "countSum", 0));
        var bucket = query("2026-09-01", "2026-09-05", GHGrouping.MONTH).series().getFirst().buckets().getFirst();
        assertThat(bucket.average()).isZero();
        assertThat(bucket.availableDays()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"2026-02-30,2026-03-01,UTC", "2026-01-01,2026-01-01,UTC",
            "2026-01-02,2026-01-01,UTC", "2026-01-01,2026-01-02,MadeUp/Zone",
            "2026-1-1,2026-01-02,UTC"})
    void rejectsInvalidRequestBeforeReadingData(String start, String end, String zone) {
        assertThatThrownBy(() -> service.grouped(start, end, GHGrouping.AUTO, zone, CLOCK))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @CsvSource({"DAY,0001-01-01,9998-12-31", "WEEK,1900-01-01,2000-01-01",
            "MONTH,1800-01-01,2000-01-01", "YEAR,0001-01-01,9998-12-31", "AUTO,0001-01-01,9998-12-31"})
    void rejectsTooManyCalendarBucketsBeforeQueryingTheMirror(GHGrouping grouping, String start, String end) {
        assertThatThrownBy(() -> query(start, end, grouping))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400")
                .hasMessageContaining("At most 2000 calendar buckets")
                .hasMessageContaining("shorter range or a coarser grouping");
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @CsvSource({"DAY,2020-01-01,2025-06-23", "WEEK,2020-01-06,2058-05-06",
            "MONTH,1800-01-01,1966-09-01", "YEAR,1000-01-01,3000-01-01"})
    void acceptsTheExactBucketBudgetAndRejectsOneAdditionalPartialPeriod(GHGrouping grouping, String start, String end) {
        rows(row("steps", start, "countSum", 1));
        assertThat(query(start, end, grouping).series().getFirst().buckets()).hasSize(2_000);
        clearInvocations(repository);
        assertThatThrownBy(() -> query(start, LocalDate.parse(end).plusDays(1).toString(), grouping))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("At most 2000");
        verifyNoInteractions(repository);
    }

    @Test
    void yearAndAutoGroupingContinueToSupportNormalLifetimeHistory() {
        rows(row("steps", "2026-09-01", "countSum", 100));
        assertThat(query("1970-01-01", "2027-01-01", GHGrouping.YEAR).series().getFirst().buckets()).hasSize(57);
        assertThat(query("1970-01-01", "2027-01-01", GHGrouping.AUTO).resolvedGrouping()).isEqualTo(GHGrouping.YEAR);
    }

    private GHGroupedMetrics query(String start, String end, GHGrouping grouping) {
        return service.grouped(start, end, grouping, "UTC", CLOCK);
    }

    private void rows(GroupedMetricRecordRow... rows) {
        when(repository.findGroupedMetricRecords(any(), any(), anyString())).thenReturn(List.of(rows));
    }

    private static GroupedMetricRecordRow row(String type, String date, String field, double value) {
        return new GroupedMetricRecordRow(LocalDate.parse(date), new MetricRecordRow(type,
                type.equals("steps") || type.equals("distance") || type.equals("active-energy-burned") ? "DAILY_ROLLUP" : "RAW",
                LocalDate.parse(date).atTime(12, 0).toInstant(ZoneOffset.UTC), null,
                "{\"" + DataTypePolicy.jsonFieldName(type) + "\":{\"" + field + "\":" + value + "}}"));
    }

    private static GroupedMetricRecordRow sleep(String date, int minutes) {
        return new GroupedMetricRecordRow(LocalDate.parse(date), new MetricRecordRow("sleep", "RAW",
                LocalDate.parse(date).atTime(1, 0).toInstant(ZoneOffset.UTC), null,
                "{\"sleep\":{\"summary\":{\"minutesAsleep\":" + minutes + "}}}"));
    }
}
