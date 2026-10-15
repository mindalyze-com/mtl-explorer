package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.StoredPoint;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "mtl.planner.enabled=false",
        "mtl.media-correlation.initial-delay=PT1H",
        "mtl.media-correlation.run-schedule=PT1H",
        "mtl.indexer.gps.live-watch-enabled=false",
        "mtl.indexer.media.live-watch-enabled=false"
})
@Transactional
class HealthSleepGroupingDatabaseIntegrationTest {
    @Autowired
    private MirrorRepository repository;

    @Autowired
    private HealthGroupingService service;

    @MockitoBean
    private GPXDirectoryWatcherService gpxDirectoryWatcherService;

    @MockitoBean
    private MediaIndexerService mediaIndexerService;

    @BeforeEach
    void emptySyntheticMirror() {
        repository.disconnect();
        repository.deleteMirrorRows();
    }

    @Test
    void crossMidnightSleepAndAnAfternoonNapBelongToTheDateTheyStarted() {
        sleep("2026-09-04T21:00:00Z", "2026-09-05T05:00:00Z", 420);
        sleep("2026-09-04T12:00:00Z", "2026-09-04T13:30:00Z", 90);

        GHMetricBucket bucket = day("2026-09-04", "Europe/Zurich");

        assertThat(bucket.total()).isEqualTo(8.5);
        assertThat(bucket.sessions()).hasSize(2);
        assertThat(bucket.sessions()).extracting(GHMetricPoint::time).containsExactly(
                Instant.parse("2026-09-04T12:00:00Z"), Instant.parse("2026-09-04T21:00:00Z"));
        assertThat(query("2026-09-05", "Europe/Zurich").series()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"2026-03-28T22:00:00Z,2026-03-29T05:00:00Z,2026-03-28,7",
            "2026-10-24T21:00:00Z,2026-10-25T06:00:00Z,2026-10-24,9"})
    void elapsedDurationFallbackKeepsTheRealHoursAcrossDst(String start, String end, String startDate, double hours) {
        sleep(start, end, null);

        GHMetricBucket bucket = day(startDate, "Europe/Zurich");

        assertThat(bucket.total()).isEqualTo(hours);
        assertThat(bucket.sessions()).singleElement().satisfies(session -> assertThat(session.value()).isEqualTo(hours));
        assertThat(query(LocalDate.parse(startDate).plusDays(1).toString(), "Europe/Zurich").series()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"America/Los_Angeles,2026-09-04", "Europe/Zurich,2026-09-05", "Pacific/Kiritimati,2026-09-05"})
    void changingViewingZonesRegroupsTheSameSessionWithoutChangingItsDuration(String zone, String date) {
        Instant start = Instant.parse("2026-09-05T01:00:00Z");
        Instant end = Instant.parse("2026-09-05T08:00:00Z");
        // The provider captured this session in Hawaii; the requested viewing zone owns chart dates.
        repository.upsertDataPoints(List.of(new StoredPoint("sleep", StoredPoint.Representation.RAW,
                "a".repeat(64), "b".repeat(64), "users/me/dataTypes/sleep/dataPoints/synthetic-travel", null,
                start, end, start.toString(), end.toString(),
                LocalDateTime.ofInstant(start, ZoneOffset.ofHours(-10)),
                LocalDateTime.ofInstant(end, ZoneOffset.ofHours(-10)), "-36000s", "-36000s", null, null,
                "{\"sleep\":{\"summary\":{\"minutesAsleep\":360}}}")), UUID.randomUUID());

        GHMetricBucket bucket = day(date, zone);

        assertThat(bucket.total()).isEqualTo(6);
        assertThat(bucket.sessions()).singleElement().satisfies(session -> assertThat(session.time()).isEqualTo(start));
    }

    private GHMetricBucket day(String date, String zone) {
        return query(date, zone).series().stream().filter(series -> series.dataType().equals("sleep"))
                .findFirst().orElseThrow().buckets().getFirst();
    }

    private GHGroupedMetrics query(String date, String zone) {
        return service.grouped(date, LocalDate.parse(date).plusDays(1).toString(), GHGrouping.DAY, zone);
    }

    private void sleep(String startValue, String endValue, Integer minutesAsleep) {
        Instant start = Instant.parse(startValue);
        Instant end = Instant.parse(endValue);
        String identity = UUID.randomUUID().toString().replace("-", "").repeat(2);
        String json = minutesAsleep == null ? "{\"sleep\":{}}"
                : "{\"sleep\":{\"summary\":{\"minutesAsleep\":" + minutesAsleep + "}}}";
        repository.upsertDataPoints(List.of(new StoredPoint("sleep", StoredPoint.Representation.RAW,
                identity, identity, "users/me/dataTypes/sleep/dataPoints/" + identity, null,
                start, end, startValue, endValue, null, null, null, null, null, null, json)), UUID.randomUUID());
    }
}
