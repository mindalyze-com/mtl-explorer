package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiClient;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.exercise.ExerciseRouteService;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataSourceResolver;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.PointMapper;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncCoordinator;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncService;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Synthetic checks for contiguous coverage and independent temporal domains.
 */
@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.media-correlation.initial-delay=PT1H",
        "mtl.media-correlation.run-schedule=PT1H", "mtl.indexer.gps.live-watch-enabled=false",
        "mtl.indexer.media.live-watch-enabled=false"})
@Transactional
class SyncBoundaryDatabaseIntegrationTest {
    @Autowired
    MirrorRepository repository;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    GpsTrackRepository tracks;
    @MockitoBean
    GPXDirectoryWatcherService watcher;
    @MockitoBean
    MediaIndexerService media;

    @Test
    void emptyHistoryFinalsSurviveAWorkerRestartWithoutCursorConversion() {
        repository.disconnect();
        repository.deleteMirrorRows();
        repository.saveConnected("synthetic-review", Set.of(OAuthScopes.SLEEP), "synthetic", null);
        ConnectorProperties properties = new ConnectorProperties();
        properties.setClientId("synthetic");
        properties.setClientSecret("synthetic");
        properties.setHistoryStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(10));
        ApiClient client = mock(ApiClient.class);
        when(client.listRaw(any(), any(), any(), any())).thenReturn(new ApiClient.Page(List.of(), null));
        var service = new SyncService(properties, repository, client, new PointMapper(new DataSourceResolver(repository)), mock(ExerciseRouteService.class), transactionManager);
        var progress = mock(SyncCoordinator.Progress.class);
        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.history_audit WHERE outcome='EMPTY'", Integer.class)).isEqualTo(3);
        clearInvocations(client);
        service = new SyncService(properties, repository, client, new PointMapper(new DataSourceResolver(repository)), mock(ExerciseRouteService.class), transactionManager);
        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isTrue();
        verify(client, never()).listRaw(any(), argThat(start -> start.isBefore(LocalDate.now(ZoneOffset.UTC).minusDays(7).atStartOfDay().toInstant(ZoneOffset.UTC))), any(), any());
    }

    @Test
    void adjacentWindowMustNotDeleteASampleWhoseNanosecondsRoundUp() {
        repository.disconnect();
        repository.deleteMirrorRows();
        Instant boundary = Instant.parse("2042-08-02T12:00:00Z");
        Instant original = boundary.minusNanos(100);
        long source = repository.upsertDataSource("a".repeat(64), "TEST", "AUTOMATIC", "Synthetic", "Synthetic", "{}");
        repository.upsertHeartRateSamples(List.of(new HeartRateSample(source, original, original.toString(),
                null, null, 60, null, null, "b".repeat(64))), UUID.randomUUID());
        int deleted = repository.deleteMissingHeartRateSamples(boundary, boundary.plusSeconds(60), UUID.randomUUID());
        assertThat(deleted).as("The sample belongs to the preceding provider window").isZero();
    }

    @Test
    void rawBrowserLocalDayMustSelectTheMatchingCivilDate() {
        repository.disconnect();
        repository.deleteMirrorRows();
        LocalDate requested = LocalDate.parse("2042-08-02");
        for (int i = 0; i < 2; i++) {
            LocalDate day = requested.plusDays(i);
            LocalDateTime start = day.atStartOfDay();
            repository.upsertDataPoints(List.of(new StoredPoint("steps", StoredPoint.Representation.DAILY_ROLLUP,
                    String.valueOf(i).repeat(64), String.valueOf(i + 2).repeat(64), null, null,
                    null, null, null, null, start, start.plusDays(1), null, null, day, null,
                    "{\"steps\":{\"countSum\":42}}")), UUID.randomUUID());
        }
        ZoneId browserZone = ZoneId.of("America/Los_Angeles");
        var rows = repository.findRecords("steps", "DAILY_ROLLUP",
                requested.atStartOfDay(browserZone).toInstant(), requested.plusDays(1).atStartOfDay(browserZone).toInstant(),
                requested, requested.plusDays(1), null, null, null, null, 50);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.temporalKind()).isEqualTo(RecordRow.TemporalKind.CALENDAR_DATE);
            assertThat(row.civilDate()).isEqualTo(requested);
            assertThat(repository.findRecord(row.kind(), row.id()).orElseThrow().civilDate()).isEqualTo(requested);
        });
    }

    @Test
    void disjointCompletedWindowDoesNotMoveContinuousHistoryBoundary() {
        repository.disconnect();
        repository.deleteMirrorRows();
        Instant now = Instant.parse("2042-08-02T12:00:00Z");
        Instant recent = now.minus(14, ChronoUnit.DAYS);
        repository.startWindow("sleep", "RAW", recent, now, UUID.randomUUID(), false);
        repository.markWindowComplete("sleep", "RAW", recent, now, 0, 0, false);
        Instant floor = now.minus(270, ChronoUnit.DAYS);
        repository.startWindow("sleep", "RAW", floor, floor.plus(90, ChronoUnit.DAYS), UUID.randomUUID(), true);
        repository.markWindowCompleteWithoutAdvancingCursor("sleep", "RAW", floor,
                floor.plus(90, ChronoUnit.DAYS), 0, 0, true);
        GHSyncState state = repository.findSyncState("sleep", "RAW").orElseThrow();
        assertThat(state.oldestSyncedTime()).isEqualTo(recent);
        assertThat(state.backfillComplete()).isFalse();
        assertThat(state.cursorTime()).isEqualTo(now);
    }

    @Test
    void calendarFilterUsesLocalMidnightForPhysicalRecordsAcrossDst() {
        repository.disconnect();
        repository.deleteMirrorRows();
        LocalDate day = LocalDate.parse("2042-03-09");
        ZoneId zone = ZoneId.of("America/Los_Angeles");
        Instant start = day.atStartOfDay(zone).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(zone).toInstant();
        long source = repository.upsertDataSource("a".repeat(64), "TEST", "AUTOMATIC", "Synthetic", "Synthetic", "{}");
        repository.upsertHeartRateSamples(List.of(
                new HeartRateSample(source, start.minusNanos(100), start.minusNanos(100).toString(), null, null, 60, null, null, "b".repeat(64)),
                new HeartRateSample(source, start, start.toString(), null, null, 61, null, null, "c".repeat(64)),
                new HeartRateSample(source, end.minusNanos(100), end.minusNanos(100).toString(), null, null, 62, null, null, "d".repeat(64)),
                new HeartRateSample(source, end, end.toString(), null, null, 63, null, null, "e".repeat(64))), UUID.randomUUID());
        var rows = repository.findRecords(null, null, start, end, day, day.plusDays(1), null, null, null, null, 50);
        assertThat(rows).hasSize(2).allSatisfy(row -> assertThat(row.temporalKind()).isEqualTo(RecordRow.TemporalKind.PHYSICAL));
    }

    @Test
    void dailySchedulingPreservesAnOlderFullReconciliationCursor() {
        repository.disconnect();
        repository.deleteMirrorRows();
        Instant now = Instant.parse("2042-08-02T12:00:00Z");
        Instant floor = now.minus(720, ChronoUnit.DAYS);
        repository.planReconciliation("sleep", "RAW", floor, now, true);
        Instant completedStart = now.minus(90, ChronoUnit.DAYS);
        repository.startWindow("sleep", "RAW", completedStart, now, UUID.randomUUID(), false);
        repository.markWindowComplete("sleep", "RAW", completedStart, now, 0, 0, false);
        Instant tomorrow = now.plus(1, ChronoUnit.DAYS);
        repository.planReconciliation("sleep", "RAW", tomorrow.minus(90, ChronoUnit.DAYS), tomorrow, false);
        repository.planReconciliation("sleep", "RAW", floor, tomorrow, true);
        assertThat(repository.findReconciliation("sleep", "RAW", true)).get().satisfies(range -> {
            assertThat(range.start()).isEqualTo(floor);
            assertThat(range.end()).isEqualTo(completedStart);
        });
        assertThat(repository.findReconciliation("sleep", "RAW", false)).get()
                .satisfies(range -> assertThat(range.end()).isEqualTo(tomorrow));
    }


}
