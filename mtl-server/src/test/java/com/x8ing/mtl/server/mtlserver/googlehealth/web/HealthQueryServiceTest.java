package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.googlehealth.application.GoogleHealthMirrorCache;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.exercise.TrackScopeService;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.*;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncCoordinator;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncScheduler;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class HealthQueryServiceTest {

    private MirrorRepository repository;
    private TrackScopeService trackScopeService;
    private ConnectorProperties properties;
    private SyncScheduler syncScheduler;
    private SyncCoordinator syncCoordinator;
    private GoogleHealthMirrorCache mirrorCache;
    private HealthQueryService webService;

    @BeforeEach
    void setUp() {
        repository = mock(MirrorRepository.class);
        trackScopeService = mock(TrackScopeService.class);
        properties = new ConnectorProperties();
        syncScheduler = mock(SyncScheduler.class);
        syncCoordinator = mock(SyncCoordinator.class);
        when(syncCoordinator.status()).thenReturn(SyncCoordinator.JobSnapshot.idle());
        when(repository.findMirrorSummary()).thenReturn(MirrorSummaryRow.empty());
        mirrorCache = new GoogleHealthMirrorCache(repository, syncCoordinator);
        webService = new HealthQueryService(
                properties,
                repository,
                syncCoordinator,
                syncScheduler,
                trackScopeService,
                new ObjectMapper(),
                mirrorCache);
    }

    @Test
    void typedRecordMeasurementsPreserveZeroUnitsAndProviderNumericStrings() {
        record Case(String type, String representation, String payload, double value, String unit, String summary) {}
        var cases = List.of(
                new Case("steps", "DAILY_ROLLUP", "{\"steps\":{\"countSum\":\"0\"}}", 0, "steps", "0 steps"),
                new Case("distance", "DAILY_ROLLUP", "{\"distance\":{\"millimetersSum\":2500000}}", 2.5, "km", "2.5 km"),
                new Case("active-energy-burned", "DAILY_ROLLUP", "{\"activeEnergyBurned\":{\"kcalSum\":0}}", 0, "kcal", "0 kcal"),
                new Case("daily-resting-heart-rate", "RAW", "{\"dailyRestingHeartRate\":{\"beatsPerMinute\":\"57\"}}", 57, "bpm", "57 bpm"),
                new Case("weight", "RAW", "{\"weight\":{\"weightGrams\":\"71500.25\"}}", 71.50025, "kg", "71.5 kg"),
                new Case("sleep", "RAW", "{\"sleep\":{\"summary\":{\"minutesAsleep\":456},\"stages\":{\"providerExtension\":true}}}", 7.6, "h", "7.6 h"),
                new Case("daily-heart-rate-variability", "RAW", "{\"dailyHeartRateVariability\":{\"averageHeartRateVariabilityMilliseconds\":12.34}}", 12.34, "ms", "12.3 ms"),
                new Case("daily-oxygen-saturation", "RAW", "{\"dailyOxygenSaturation\":{\"averagePercentage\":98.4}}", 98.4, "%", "98.4 %"),
                new Case("daily-respiratory-rate", "RAW", "{\"dailyRespiratoryRate\":{\"breathsPerMinute\":16.5}}", 16.5, "breaths/min", "16.5 breaths/min"),
                new Case("daily-vo2-max", "RAW", "{\"dailyVo2Max\":{\"vo2Max\":42.5}}", 42.5, "ml/kg/min", "42.5 ml/kg/min"),
                new Case("body-fat", "RAW", "{\"bodyFat\":{\"percentage\":20.5}}", 20.5, "%", "20.5 %"),
                new Case("daily-sleep-temperature-derivations", "RAW", "{\"dailySleepTemperatureDerivations\":{\"nightlyTemperatureCelsius\":36.123}}", 36.123, "°C", "36.1 °C"),
                new Case("core-body-temperature", "RAW", "{\"coreBodyTemperature\":{\"temperatureCelsius\":36.8}}", 36.8, "°C", "36.8 °C"),
                new Case("blood-glucose", "RAW", "{\"bloodGlucose\":{\"bloodGlucoseMilligramsPerDeciliter\":100.4}}", 100.4, "mg/dL", "100.4 mg/dL"),
                new Case("heart-rate", "DAILY_ROLLUP", "{\"heartRate\":{\"beatsPerMinuteAvg\":64,\"beatsPerMinuteMin\":50,\"beatsPerMinuteMax\":120}}", 64, "bpm", "64 bpm"),
                new Case("heart-rate", "HEART_RATE_SAMPLE", "{\"heartRate\":{\"beatsPerMinute\":\"55\"}}", 55, "bpm", "55 bpm"));
        for (var example : cases) {
            var row = new RecordRow(42, RecordRow.RecordKind.DATA_POINT, example.type(), example.representation(),
                    Instant.parse("2026-10-14T00:00:00Z"), null, null, 1L, "Synthetic", "SYNTHETIC_DEMO", null, null, example.payload());
            when(repository.findRecords(any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of(row));
            when(repository.findRecord(RecordRow.RecordKind.DATA_POINT, 42L)).thenReturn(Optional.of(row));
            var listed = webService.records(example.type(), example.representation(), null, null, null, null, 50).records().getFirst();
            var detail = webService.record("point:42");
            assertThat(listed.measurement()).as(example.type()).isEqualTo(detail.measurement());
            assertThat(listed.measurement().value()).as(example.type()).isEqualTo(example.value());
            assertThat(listed.measurement().unit()).isEqualTo(example.unit());
            assertThat(listed.valueSummary()).isEqualTo(example.summary());
            assertThat(listed.payload()).isNull();
            assertThat(detail.payload()).isNotEmpty();
            if (example.representation().equals("DAILY_ROLLUP") && example.type().equals("heart-rate")) {
                assertThat(detail.measurement().minimum()).isEqualTo(50d);
                assertThat(detail.measurement().maximum()).isEqualTo(120d);
            }
        }
    }

    @Test
    void flatMetricPayloadIsReportedAndDoesNotHideValidRecords(CapturedOutput output) {
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        Instant end = Instant.parse("2026-10-03T00:00:00Z");
        when(repository.findMetricRecords(start, end)).thenReturn(List.of(
                new MetricRecordRow("steps", "DAILY_ROLLUP", start, null, "{\"countSum\":4432}"),
                new MetricRecordRow("steps", "DAILY_ROLLUP", start.plusSeconds(86400), null, "{\"steps\":{\"countSum\":0}}")));
        var series = webService.metrics(start, end);
        assertThat(series).hasSize(1);
        assertThat(series.getFirst().points()).singleElement().satisfies(point -> assertThat(point.value()).isZero());
        assertThat(output).contains("Skipped unreadable Google Health metric values: count=1");
    }

    @Test
    void nonFiniteProviderStringsKeepValidAlternativesAndNeverReachChartRanges() {
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        Instant end = start.plusSeconds(86400);
        when(repository.findMetricRecords(start, end)).thenReturn(List.of(
                new MetricRecordRow("steps", "DAILY_ROLLUP", start, null,
                        "{\"steps\":{\"countSum\":\"NaN\",\"count\":\"12\"}}"),
                new MetricRecordRow("sleep", "RAW", start, start.plusSeconds(7200),
                        "{\"sleep\":{\"summary\":{\"minutesAsleep\":\"NaN\"}}}"),
                new MetricRecordRow("heart-rate", "DAILY_ROLLUP", start, null,
                        "{\"heartRate\":{\"beatsPerMinuteAvg\":64,\"beatsPerMinuteMin\":\"NaN\",\"beatsPerMinuteMax\":\"Infinity\"}}")));
        var series = webService.metrics(start, end);
        assertThat(series).extracting(GHMetricSeries::dataType).containsExactly("steps", "sleep", "heart-rate");
        assertThat(series.get(0).points().getFirst().value()).isEqualTo(12);
        assertThat(series.get(1).points().getFirst().value()).isEqualTo(2);
        assertThat(series.get(2).points().getFirst()).satisfies(point -> {
            assertThat(point.value()).isEqualTo(64);
            assertThat(point.minimum()).isNull();
            assertThat(point.maximum()).isNull();
        });
    }

    @Test
    void typedRecordMeasurementRejectsNonFiniteValues() {
        Assertions.assertThatThrownBy(() -> new GHRecordValue(Double.NaN, "steps", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> new GHRecordValue(0, "", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordCalendarBoundsUseTheViewingZoneAndIncludeTheEndDate() {
        var day = LocalDate.parse("2026-03-29");
        var zone = ZoneId.of("Europe/Zurich");
        webService.records(null, null, null, null, day, day, zone.getId(), null, null, 50);
        verify(repository).findRecords(null, null, day.atStartOfDay(zone).toInstant(),
                day.plusDays(1).atStartOfDay(zone).toInstant(), day, day.plusDays(1), null, null, null, null, 51);
        assertThat(Duration.between(day.atStartOfDay(zone), day.plusDays(1).atStartOfDay(zone))).isEqualTo(Duration.ofHours(23));
    }

    @Test
    void invalidOrMixedRecordCalendarBoundsAreRejectedBeforeQuerying() {
        var day = LocalDate.parse("2026-03-29");
        Assertions.assertThatThrownBy(() ->
                        webService.records(null, null, Instant.now(), null, day, day, "UTC", null, null, 50))
                .isInstanceOf(ResponseStatusException.class);
        Assertions.assertThatThrownBy(() ->
                        webService.records(null, null, null, null, day, day.minusDays(1), "UTC", null, null, 50))
                .isInstanceOf(ResponseStatusException.class);
        Assertions.assertThatThrownBy(() ->
                        webService.records(null, null, null, null, day, day, "Unknown/Place", null, null, 50))
                .isInstanceOf(ResponseStatusException.class);
        Mockito.verifyNoInteractions(repository);
    }

    @Test
    void nextRunIsShownOnlyForAConfiguredConnectedMirror() {
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRedirectUri("http://localhost/mtl/api/google-health/oauth/callback");
        Instant nextRun = Instant.parse("2026-08-28T11:00:00Z");
        when(syncScheduler.nextRegularSync()).thenReturn(nextRun);
        when(repository.findConnection())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new AccountConnection(
                        "me", Set.of(), "encrypted",
                        AccountConnection.ConnectionState.CONNECTED,
                        null, Instant.now(), null, Instant.now())));
        when(repository.findAllSyncStates()).thenReturn(List.of());

        assertThat(webService.status().nextRegularSync()).isNull();
        assertThat(webService.status().nextRegularSync()).isEqualTo(nextRun);
    }

    @Test
    void disabledStatusIsInertButStillReportsRetainedMirrorData() {
        properties.setEnabled(false);
        Instant earliest = Instant.parse("2025-08-01T00:00:00Z");
        Instant latest = Instant.parse("2026-08-01T00:00:00Z");
        GHSyncState syncState = new GHSyncState(
                "steps", "DAILY_ROLLUP", null, earliest, null, null, null,
                true, latest, latest, null, 365L, 0L);
        when(repository.findMirrorSummary()).thenReturn(new MirrorSummaryRow(365L, earliest, latest));
        when(repository.findAllSyncStates()).thenReturn(List.of(syncState));

        GHStatusResponse status = webService.status();

        assertThat(status.configurationState()).isEqualTo(ConnectorProperties.ConfigurationState.DISABLED);
        assertThat(status.connectionState()).isEqualTo(AccountConnection.ConnectionState.DISCONNECTED);
        assertThat(status.job().state()).isEqualTo(SyncCoordinator.JobState.IDLE);
        assertThat(status.mirrorSummary()).isEqualTo(new GHMirrorSummary(365L, earliest, latest));
        assertThat(status.syncStates()).containsExactly(syncState);
        verify(repository, Mockito.times(2)).findConnection();
        verify(syncScheduler, never()).nextRegularSync();
    }

    @Test
    void disabledConfigurationPreservesSavedConnectionForLocalCleanup() {
        properties.setEnabled(false);
        Instant connectedAt = Instant.parse("2026-09-01T10:00:00Z");
        Instant expiresAt = connectedAt.plus(Duration.ofDays(7));
        for (AccountConnection.ConnectionState state : List.of(
                AccountConnection.ConnectionState.CONNECTED,
                AccountConnection.ConnectionState.RECONNECT_REQUIRED)) {
            when(repository.findConnection()).thenReturn(Optional.of(new AccountConnection(
                    "health-user", Set.of("https://www.googleapis.com/auth/sleep.readonly"), "encrypted",
                    state, "Saved error", connectedAt, expiresAt, null, connectedAt)));

            GHStatusResponse status = webService.status();

            assertThat(status.configurationState()).isEqualTo(ConnectorProperties.ConfigurationState.DISABLED);
            assertThat(status.connectionState()).isEqualTo(state);
            assertThat(status.authorizationAction()).isEqualTo(GHStatusResponse.AuthorizationAction.NONE);
            assertThat(status.connectedAt()).isEqualTo(connectedAt);
            assertThat(status.refreshTokenExpiresAt()).isEqualTo(expiresAt);
            assertThat(status.lastError()).isEqualTo("Saved error");
            assertThat(status.nextRegularSync()).isNull();
        }
        verify(syncScheduler, never()).nextRegularSync();
        verify(syncCoordinator, never()).status();
    }

    @Test
    void reportsExpiryDrivenAuthorizationActions() {
        Instant now = Instant.parse("2026-09-01T10:00:00Z");
        AccountConnection current = new AccountConnection(
                "health-user-1", Set.of(), "encrypted",
                AccountConnection.ConnectionState.CONNECTED, null,
                now.minus(Duration.ofDays(6)), now.plus(Duration.ofHours(12)), null, now);
        AccountConnection expired = new AccountConnection(
                "health-user-1", Set.of(), "encrypted",
                AccountConnection.ConnectionState.CONNECTED, null,
                now.minus(Duration.ofDays(7)), now.minusSeconds(1), null, now);

        assertThat(HealthQueryService.authorizationAction(current, now))
                .isEqualTo(GHStatusResponse.AuthorizationAction.EXPIRING_SOON);
        assertThat(HealthQueryService.authorizationAction(expired, now))
                .isEqualTo(GHStatusResponse.AuthorizationAction.REAUTHORIZE);
    }

    @Test
    void statusCachesTheMirrorSummaryAndRefreshesItAfterAJobCompletes() {
        Instant firstRecord = Instant.parse("2025-08-25T19:27:03Z");
        Instant latestRecord = Instant.parse("2026-09-03T13:08:51Z");
        MirrorSummaryRow firstRow = new MirrorSummaryRow(2_000_000L, firstRecord, latestRecord);
        MirrorSummaryRow refreshedRow = new MirrorSummaryRow(2_700_000L, firstRecord, latestRecord);
        UUID jobId = UUID.randomUUID();
        SyncCoordinator.JobSnapshot completed = new SyncCoordinator.JobSnapshot(
                jobId,
                jobId,
                SyncCoordinator.JobKind.REGULAR,
                SyncCoordinator.JobState.COMPLETED,
                latestRecord.minusSeconds(60),
                latestRecord.minusSeconds(60),
                latestRecord,
                140L,
                140L,
                0L,
                0L,
                null,
                null,
                null,
                null,
                null);
        when(repository.findMirrorSummary()).thenReturn(firstRow, refreshedRow);
        when(syncCoordinator.status())
                .thenReturn(SyncCoordinator.JobSnapshot.idle(), SyncCoordinator.JobSnapshot.idle(), completed);

        GHMirrorSummary firstResponse = new GHMirrorSummary(2_000_000L, firstRecord, latestRecord);
        GHMirrorSummary refreshedResponse = new GHMirrorSummary(2_700_000L, firstRecord, latestRecord);
        assertThat(webService.status().mirrorSummary()).isEqualTo(firstResponse);
        assertThat(webService.status().mirrorSummary()).isEqualTo(firstResponse);
        assertThat(webService.status().mirrorSummary()).isEqualTo(refreshedResponse);
        verify(repository, times(2)).findMirrorSummary();
    }

    @Test
    void aggregateListsAreCachedAndRefreshAfterAJobCompletes() {
        GHTypeSummary firstType = new GHTypeSummary(
                "steps", "DAILY_ROLLUP", 10L, null, null, null, null);
        GHTypeSummary refreshedType = new GHTypeSummary(
                "steps", "DAILY_ROLLUP", 20L, null, null, null, null);
        GHDataSourceSummary firstSource = new GHDataSourceSummary(1L, "Fitbit", "Watch", "FITBIT", 10L);
        GHDataSourceSummary refreshedSource = new GHDataSourceSummary(1L, "Fitbit", "Watch", "FITBIT", 20L);
        SyncCoordinator.JobSnapshot completed = completedJob();
        when(repository.findTypeSummaries()).thenReturn(List.of(firstType), List.of(refreshedType));
        when(repository.findDataSourceSummaries()).thenReturn(List.of(firstSource), List.of(refreshedSource));
        when(syncCoordinator.status())
                .thenReturn(SyncCoordinator.JobSnapshot.idle(), SyncCoordinator.JobSnapshot.idle(),
                        SyncCoordinator.JobSnapshot.idle(), SyncCoordinator.JobSnapshot.idle(),
                        completed, completed);

        assertThat(webService.summaries()).containsExactly(firstType);
        assertThat(webService.sources()).containsExactly(firstSource);
        assertThat(webService.summaries()).containsExactly(firstType);
        assertThat(webService.sources()).containsExactly(firstSource);
        assertThat(webService.summaries()).containsExactly(refreshedType);
        assertThat(webService.sources()).containsExactly(refreshedSource);
        verify(repository, times(2)).findTypeSummaries();
        verify(repository, times(2)).findDataSourceSummaries();
    }

    @Test
    void clearingCachesInvalidatesEveryAggregate() {
        when(repository.findTypeSummaries()).thenReturn(List.of());
        when(repository.findDataSourceSummaries()).thenReturn(List.of());

        webService.status();
        webService.summaries();
        webService.sources();
        mirrorCache.clear();
        webService.status();
        webService.summaries();
        webService.sources();

        verify(repository, times(2)).findMirrorSummary();
        verify(repository, times(2)).findTypeSummaries();
        verify(repository, times(2)).findDataSourceSummaries();
    }

    @Test
    void activeContinuationsDoNotInvalidateAggregatesOnEveryPass() {
        webService.status();
        webService.summaries();
        webService.sources();
        UUID completedId = UUID.randomUUID();
        when(syncCoordinator.lastCompletedJobId()).thenReturn(completedId);
        UUID activeId = UUID.randomUUID();
        when(syncCoordinator.status()).thenReturn(new SyncCoordinator.JobSnapshot(
                activeId, activeId, SyncCoordinator.JobKind.REGULAR, SyncCoordinator.JobState.RUNNING,
                Instant.now(), Instant.now(), null, 140, 0, 0, 0, null, null, null, null, null));

        webService.status();
        webService.summaries();
        webService.sources();
        webService.status();
        webService.summaries();
        webService.sources();

        verify(repository, times(1)).findMirrorSummary();
        verify(repository, times(1)).findTypeSummaries();
        verify(repository, times(1)).findDataSourceSummaries();
    }

    @Test
    void listPaginationRedactsRawPayloadAndUsesAnOpaqueCursor() {
        RecordRow first = row(3L, Instant.parse("2026-08-28T10:00:00Z"));
        RecordRow second = row(2L, Instant.parse("2026-08-28T09:00:00Z"));
        RecordRow lookahead = row(1L, Instant.parse("2026-08-28T08:00:00Z"));
        when(repository.findRecords(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), eq(3)))
                .thenReturn(List.of(first, second, lookahead));

        GHRecordsResponse response = webService.records(
                null, null, null, null, null, null, 2);

        assertThat(response.records()).extracting(GHRecordResponse::id)
                .containsExactly("heart:3", "heart:2");
        assertThat(response.records()).allSatisfy(record -> assertThat(record.payload()).isNull());
        assertThat(response.nextCursor()).isNotBlank().matches("[A-Za-z0-9_-]+");
    }

    @Test
    void detailReturnsTheStoredPayloadAndCanonicalTrack() {
        RecordRow row = new RecordRow(
                9L,
                RecordRow.RecordKind.DATA_POINT,
                "exercise",
                "RAW",
                Instant.parse("2026-08-28T10:00:00Z"),
                "2026-08-28T10:00:00.123456789Z",
                null,
                4L,
                "Pixel Watch",
                "ANDROID",
                "IMPORTED",
                30L,
                "{\"exercise\":{\"exerciseType\":\"RUNNING\"}}");
        when(repository.findRecord(RecordRow.RecordKind.DATA_POINT, 9L))
                .thenReturn(Optional.of(row));
        when(trackScopeService.canonicalTrackId(30L)).thenReturn(12L);

        GHRecordResponse response = webService.record("point:9");

        assertThat(response.originalTime()).isEqualTo("2026-08-28T10:00:00.123456789Z");
        assertThat(response.associatedTrackId()).isEqualTo(30L);
        assertThat(response.canonicalTrackId()).isEqualTo(12L);
        assertThat(response.payload()).containsKey("exercise");
    }

    @Test
    void metricSeriesExposeDifferentHealthKindsWithoutRawPayloads() {
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        Instant end = Instant.parse("2026-08-29T00:00:00Z");
        when(repository.findMetricRecords(start, end)).thenReturn(List.of(
                new MetricRecordRow(
                        "steps", "DAILY_ROLLUP", Instant.parse("2026-08-27T00:00:00Z"), null,
                        "{\"steps\":{\"countSum\":\"12345\"}}"),
                new MetricRecordRow(
                        "sleep", "RAW", Instant.parse("2026-08-27T22:00:00Z"),
                        Instant.parse("2026-08-28T06:00:00Z"),
                        "{\"sleep\":{\"summary\":{\"minutesAsleep\":\"438\"}}}"),
                new MetricRecordRow(
                        "weight", "RAW", Instant.parse("2026-08-28T07:00:00Z"), null,
                        "{\"weight\":{\"weightGrams\":71500}}")));
        when(repository.findHeartRateBuckets(start, end, 3_600)).thenReturn(List.of(
                new HeartRateBucket(Instant.parse("2026-08-28T08:00:00Z"), 62.5, 55, 81)));

        List<GHMetricSeries> series = webService.metrics(start, end);

        assertThat(series).extracting(GHMetricSeries::dataType)
                .containsExactly("steps", "sleep", "heart-rate", "weight");
        assertThat(series).filteredOn(item -> item.dataType().equals("steps"))
                .singleElement().satisfies(item -> {
                    assertThat(item.chartType()).isEqualTo(GHMetricSeries.ChartType.COLUMN);
                    assertThat(item.points()).singleElement()
                            .extracting(GHMetricPoint::value)
                            .isEqualTo(12_345d);
                });
        assertThat(series).filteredOn(item -> item.dataType().equals("sleep"))
                .singleElement().satisfies(item -> assertThat(item.points()).singleElement()
                        .extracting(GHMetricPoint::value)
                        .isEqualTo(7.3d));
        assertThat(series).filteredOn(item -> item.dataType().equals("heart-rate"))
                .singleElement().satisfies(item -> assertThat(item.points()).singleElement()
                        .satisfies(point -> {
                            assertThat(point.value()).isEqualTo(62.5d);
                            assertThat(point.minimum()).isEqualTo(55d);
                            assertThat(point.maximum()).isEqualTo(81d);
                        }));
        assertThat(series).filteredOn(item -> item.dataType().equals("weight"))
                .singleElement().satisfies(item -> assertThat(item.points()).singleElement()
                        .extracting(GHMetricPoint::value)
                        .isEqualTo(71.5d));
    }

    @Test
    void metricSeriesAllowsCustomRangesLongerThanOneYear() {
        Instant start = Instant.parse("2021-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-08-01T00:00:00Z");
        when(repository.findMetricRecords(start, end)).thenReturn(List.of());

        assertThat(webService.metrics(start, end)).isEmpty();

        verify(repository).findMetricRecords(start, end);
    }

    @Test
    void invalidMetricPayloadsAreLoggedAsOneSummary(CapturedOutput output) {
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        Instant end = Instant.parse("2026-08-02T00:00:00Z");
        when(repository.findMetricRecords(start, end)).thenReturn(List.of(
                new MetricRecordRow("steps", "DAILY_ROLLUP", start, null, "{"),
                new MetricRecordRow("steps", "DAILY_ROLLUP", end.minusSeconds(1), null, "{")));

        assertThat(webService.metrics(start, end)).isEmpty();
        assertThat(output).contains("Skipped unreadable Google Health metric values: count=2");
    }

    @Test
    void metricSeriesRejectsReversedRanges() {
        Assertions.assertThatThrownBy(() -> webService.metrics(
                        Instant.parse("2026-08-02T00:00:00Z"),
                        Instant.parse("2026-08-01T00:00:00Z")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void longMetricRangesUseDailyHeartRateSummaries() {
        Instant start = Instant.parse("2026-02-01T00:00:00Z");
        Instant end = Instant.parse("2026-08-01T00:00:00Z");
        when(repository.findMetricRecords(start, end)).thenReturn(List.of(new MetricRecordRow(
                "heart-rate", "DAILY_ROLLUP", Instant.parse("2026-07-31T00:00:00Z"), null,
                "{\"heartRate\":{\"beatsPerMinuteAvg\":\"68\","
                + "\"beatsPerMinuteMin\":\"51\",\"beatsPerMinuteMax\":\"126\"}}")));

        assertThat(webService.metrics(start, end))
                .singleElement()
                .satisfies(series -> assertThat(series.points())
                        .singleElement()
                        .satisfies(point -> {
                            assertThat(point.value()).isEqualTo(68d);
                            assertThat(point.minimum()).isEqualTo(51d);
                            assertThat(point.maximum()).isEqualTo(126d);
                        }));
        verify(repository, never()).findHeartRateBuckets(any(Instant.class), any(Instant.class), anyInt());
    }

    @Test
    void invalidOpaqueCursorAlwaysReturnsBadRequest() {
        String outOfRangeTime = cursor(Long.MAX_VALUE + ":0:0:1");
        String invalidKind = cursor("0:0:7:1");
        String invalidId = cursor("0:0:0:0");

        for (String cursor : List.of(outOfRangeTime, invalidKind, invalidId)) {
            Assertions.assertThatThrownBy(() ->
                            webService.records(null, null, null, null, null, cursor, 50))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    private static SyncCoordinator.JobSnapshot completedJob() {
        Instant completedAt = Instant.parse("2026-09-03T13:08:51Z");
        UUID jobId = UUID.randomUUID();
        return new SyncCoordinator.JobSnapshot(
                jobId,
                jobId,
                SyncCoordinator.JobKind.REGULAR,
                SyncCoordinator.JobState.COMPLETED,
                completedAt.minusSeconds(60),
                completedAt.minusSeconds(60),
                completedAt,
                140L,
                140L,
                0L,
                0L,
                null,
                null,
                null,
                null,
                null);
    }

    private static String cursor(String decoded) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(decoded.getBytes(StandardCharsets.UTF_8));
    }

    private static RecordRow row(long id, Instant time) {
        return new RecordRow(
                id,
                RecordRow.RecordKind.HEART_RATE_SAMPLE,
                "heart-rate",
                "HEART_RATE_SAMPLE",
                time,
                time.toString(),
                "61 bpm",
                null,
                null,
                null,
                null,
                null,
                "{\"heartRate\":{\"beatsPerMinute\":\"61\"}}");
    }
}
