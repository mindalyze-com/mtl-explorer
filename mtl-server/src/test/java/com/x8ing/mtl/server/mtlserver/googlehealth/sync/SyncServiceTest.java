package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiClient;
import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiException;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.exercise.ExerciseRouteService;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.AccountConnection;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.StoredPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SyncServiceTest {
    private static final String RESPIRATORY_DATA_TYPE = "respiratory-rate-sleep-summary";
    MirrorRepository repository = mock(MirrorRepository.class);
    ApiClient client = mock(ApiClient.class);
    ExerciseRouteService routes = mock(ExerciseRouteService.class);
    ConnectorProperties properties = new ConnectorProperties();
    ObjectMapper json = new ObjectMapper();
    SyncCoordinator.Progress progress = mock(SyncCoordinator.Progress.class);
    SyncService service;
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);

    @BeforeEach
    void setup() {
        properties.setClientId("synthetic");
        properties.setClientSecret("synthetic");
        properties.setHistoryStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(7));
        var connection = new AccountConnection("synthetic", Set.of(OAuthScopes.SLEEP), "cipher", AccountConnection.ConnectionState.CONNECTED, null, Instant.now(), null, Instant.now());
        when(repository.findConnection()).thenReturn(Optional.of(connection));
        when(repository.recentPass(anyString(), any())).thenReturn(Optional.of(new MirrorRepository.RecentPass(UUID.randomUUID(), Instant.now(), Instant.now().minusSeconds(1), Instant.now(), Instant.now(), "COMPLETED", Instant.now().plusSeconds(3600), 1, 0, 0)));
        when(client.listRaw(any(), any(), any(), any())).thenReturn(new ApiClient.Page(List.of(), null));
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new SyncService(properties, repository, client, new PointMapper(new DataSourceResolver(repository)), routes, manager);
    }

    @Test
    void validEmptyAnswersReplaceOnlyTheirNativeScopeAndNeverDeleteRoutes() {
        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isTrue();
        verify(repository, atLeastOnce()).replaceRecent(any(), any(), any(), eq(Set.of()));
        verify(routes, never()).removeManagedRoutes(any());
        verify(routes, never()).removeOrphanedManagedRoutes();
        verify(repository, never()).startWindow(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void invalidItemsWriteNeitherSourcesNorRecordsNorReplacement() {
        when(client.listRaw(any(), any(), any(), any())).thenReturn(new ApiClient.Page(List.of(json.readTree("{\"sleep\":{}}")), null));
        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isFalse();
        verify(repository, never()).upsertDataSource(any(), any(), any(), any(), any(), any());
        verify(repository, never()).upsertDataPoints(any(), any());
        verify(repository, never()).replaceRecent(any(), any(), any(), any());
        verify(repository, atLeastOnce()).recordRecentFailure(any(), any(), eq("FAST"), any(), any(), any(), any(), eq(false), any(), any());
    }

    @Test
    void quotaPauseDoesNotConsumeWorkFailureAttempts() {
        when(client.listRaw(any(), any(), any(), any())).thenThrow(new ApiClient.QuotaPause());
        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isFalse();
        verify(repository, never()).recordRecentFailure(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void temporaryProviderFailureIsSavedForAutomaticRetry() {
        when(client.listRaw(any(), any(), any(), any())).thenThrow(new ApiException("Synthetic unavailable", 503, true, ApiException.Reason.SERVER));
        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isFalse();
        verify(repository, atLeastOnce()).recordRecentFailure(any(), any(), eq("FAST"), any(), any(), any(), any(), eq(true), any(), any());
        verify(repository, never()).replaceRecent(any(), any(), any(), any());
    }

    @Test
    void interruptedRequestAfterCancellationDoesNotBecomeFailedWork() {
        when(client.listRaw(any(), any(), any(), any())).thenAnswer(call -> {
            when(progress.isCancelled()).thenReturn(true);
            throw new ApiException("Synthetic interrupted retry", 0, false, new java.io.InterruptedIOException());
        });
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.run(SyncCoordinator.JobKind.REGULAR, progress))
                .isInstanceOf(java.util.concurrent.CancellationException.class);
        verify(repository, never()).recordRecentFailure(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void finalDaysAreExcludedEvenWhenTheRecentWindowWidens() {
        properties.setRecentWindow(Period.ofDays(14));
        when(repository.isFinal(any(), any(), any())).thenReturn(true);
        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isTrue();
        verify(client, never()).listRaw(any(), any(), any(), any());
    }

    @Test
    void zeroRemainingDispatchBudgetMakesNoOutboundAttempts() {
        when(progress.cycleDeadline()).thenReturn(Instant.now().minusSeconds(1));
        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isTrue();
        verify(client, never()).listRaw(any(), any(), any(), any());
        verify(repository, never()).guardSynchronization(any(), anyLong());
        verify(repository, never()).handOverFailures(any(), any());
    }

    @Test
    void fetchedAnswerCommitsWithoutReservingTimeForAnotherHttpRequest() {
        properties.setRequestTimeout(Duration.ofSeconds(10));
        when(progress.cycleDeadline()).thenReturn(Instant.now().plusSeconds(12));
        when(client.listRaw(any(), any(), any(), any())).thenAnswer(call -> {
            Thread.sleep(2100);
            return new ApiClient.Page(List.of(), null);
        });

        var result = service.run(SyncCoordinator.JobKind.REGULAR, progress);

        assertThat(result.success()).isTrue();
        assertThat(result.statistics().budgetLimited()).isTrue();
        verify(repository, atLeastOnce()).replaceRecent(any(), any(), any(), eq(Set.of()));
        verify(repository, never()).recordRecentFailure(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void slowCycleSetupCannotConsumeTheReservedHistoryShare() {
        properties.setRequestTimeout(Duration.ofMillis(10));
        properties.setHistoryStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(14));
        var connection = repository.findConnection().orElseThrow();
        when(progress.cycleDeadline()).thenReturn(Instant.now().plusSeconds(12));
        when(repository.findConnection()).thenAnswer(call -> {
            Thread.sleep(2500);
            return Optional.of(connection);
        });
        doAnswer(call -> {
            Thread.sleep(2500);
            return null;
        }).when(repository).recentCycle(any(), any());
        when(repository.newestUnmarked(any(), any(), any(), any()))
                .thenReturn(Optional.of(LocalDate.now(ZoneOffset.UTC).minusDays(8)), Optional.empty());
        var policy = DataTypePolicy.allowedBy(connection.grantedScopes()).getFirst();
        LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(8);
        var parent = new MirrorRepository.HistoryWork(UUID.randomUUID(), day, "HISTORY", 0, "ACTIVE", 0, null);
        var bounds = new SyncWindowPolicy.Bounds(policy.windowTimeDomain(), SyncWindowPolicy.midnight(day), SyncWindowPolicy.midnight(day.plusDays(1)));
        var children = SyncWindowPolicy.cells(policy, bounds).stream().map(cell -> new MirrorRepository.HistoryChild(
                UUID.randomUUID(), UUID.randomUUID(), cell.start(), cell.end(), "ACTIVE", null, 0, 0)).toList();
        when(repository.emptyBatchStart(any(), any(), any(), any())).thenReturn(day);
        when(repository.createWork(any(), eq(policy), eq(day), eq("HISTORY"), any())).thenReturn(parent);
        when(repository.children(parent.id())).thenReturn(children);
        when(repository.queryFingerprints(any())).thenAnswer(call -> new HashMap<>());
        var firstRequestBudget = new AtomicLong(-1);
        when(client.listRaw(any(), any(), any(), any())).thenAnswer(call -> {
            firstRequestBudget.compareAndSet(-1, SyncBudget.remainingNanos());
            return new ApiClient.Page(List.of(), null);
        });

        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isTrue();
        assertThat(Duration.ofNanos(firstRequestBudget.get())).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(4));
    }

    @Test
    void completedBatchesUseTheStorageLimitWhileSetupStillUsesTheRemainingCycle() {
        properties.setDatabaseTransactionTimeout(Duration.ofMinutes(10));
        when(progress.cycleDeadline()).thenReturn(Instant.now().plusSeconds(90));

        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isTrue();

        var definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(manager, atLeastOnce()).getTransaction(definitions.capture());
        assertThat(definitions.getAllValues()).anySatisfy(definition ->
                assertThat(definition.getTimeout()).isEqualTo(600));
        assertThat(definitions.getAllValues()).anySatisfy(definition ->
                assertThat(definition.getTimeout()).isBetween(2, 90));
        assertThat(definitions.getAllValues()).allSatisfy(definition ->
                assertThat(definition.getTimeout()).isBetween(2, 600));
    }

    @Test
    void hardDatabaseTimeoutIsRecordedEvenAfterTheSchedulingBudgetExpires() {
        properties.setRequestTimeout(Duration.ofMillis(10));
        when(progress.cycleDeadline()).thenReturn(Instant.now().plusMillis(2500));
        when(repository.replaceRecent(any(), any(), any(), any())).thenAnswer(call -> {
            Thread.sleep(2700);
            throw new TransactionTimedOutException("Synthetic budget exhaustion");
        });

        var result = service.run(SyncCoordinator.JobKind.REGULAR, progress);

        assertThat(result.success()).isFalse();
        assertThat(result.statistics().budgetLimited()).isTrue();
        verify(repository).recordRecentFailure(any(), any(), eq("FAST"), any(), any(), any(), any(), eq(true), any(), any());
        assertThat(result.errors()).allSatisfy(error -> assertThat(error).contains("Local database transaction timed out"));
    }

    @Test
    void aCompletedAnswerStillCommitsAfterItsSchedulingDeadline() {
        properties.setRequestTimeout(Duration.ofMillis(10));
        when(progress.cycleDeadline()).thenReturn(Instant.now().plusMillis(2000));
        when(client.listRaw(any(), any(), any(), any())).thenAnswer(call -> {
            Thread.sleep(2200);
            return new ApiClient.Page(List.of(), null);
        });

        var result = service.run(SyncCoordinator.JobKind.REGULAR, progress);

        assertThat(result.success()).isTrue();
        assertThat(result.statistics().budgetLimited()).isTrue();
        verify(client, times(1)).listRaw(any(), any(), any(), any());
        verify(repository).replaceRecent(any(), any(), any(), eq(Set.of()));
        verify(repository, never()).recordRecentFailure(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void subdividedReloadSavesItsCompletedCheckpointAfterTheFinalChunkOverruns() {
        properties.setRequestTimeout(Duration.ofMillis(10));
        when(progress.cycleDeadline()).thenReturn(Instant.now().plusMillis(2000));
        var policy = DataTypePolicy.allowedBy(Set.of(OAuthScopes.SLEEP)).getFirst();
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        Instant start = day.atStartOfDay().toInstant(ZoneOffset.UTC);
        var parent = new MirrorRepository.HistoryWork(UUID.randomUUID(), day, "RELOAD", 0, "ACTIVE", 0, null);
        var child = new MirrorRepository.HistoryChild(UUID.randomUUID(), UUID.randomUUID(), start,
                start.plus(Duration.ofMinutes(2)), "ACTIVE", null, 0, 0);
        when(repository.dueWork(anyString(), eq(policy), eq("RELOAD"), any())).thenReturn(Optional.of(parent));
        when(repository.children(parent.id())).thenReturn(List.of(child));
        var purpose = new AtomicReference<>("");
        doAnswer(call -> {
            purpose.set(call.getArgument(0));
            return null;
        }).when(client).purpose(anyString());
        when(repository.isFinal(any(), any(), any())).thenAnswer(call -> !purpose.get().equals("RECENT_RELOAD"));
        var requests = new AtomicInteger();
        when(client.listRaw(any(), any(), any(), any())).thenAnswer(call -> {
            int number = requests.incrementAndGet();
            if (number == 3) Thread.sleep(2200);
            return new ApiClient.Page(List.of(), number == 1 ? "synthetic-probe-token" : null);
        });

        var result = service.run(SyncCoordinator.JobKind.REGULAR, progress);

        assertThat(result.success()).isTrue();
        assertThat(result.statistics().budgetLimited()).isTrue();
        assertThat(requests).hasValue(3);
        verify(repository, times(2)).replaceRecent(any(), any(), any(), eq(Set.of()));
        verify(repository).checkpointChild(child, null, 0, 0, 1);
        verify(repository, never()).recordWorkFailure(any(), anyBoolean(), any(), any());
    }

    @Test
    void localDatabaseTimeoutDoesNotBlameGoogleOrExposeDatabaseDetails() {
        when(repository.replaceRecent(any(), any(), any(), any()))
                .thenThrow(new TransactionTimedOutException("sensitive database details"));

        var result = service.run(SyncCoordinator.JobKind.REGULAR, progress);

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).allSatisfy(error -> assertThat(error)
                .contains("Local database transaction timed out")
                .doesNotContain("Google Health did not return", "sensitive database details"));
    }

    @Test
    void recentRespiratoryImportPersistsDistinctRecordsAndDeduplicatesExactRepeats() {
        useHealthMetricsScope();
        var batches = captureRespiratoryBatches();
        when(client.listRaw(any(), any(), any(), any())).thenAnswer(call -> {
            DataTypePolicy.Policy policy = call.getArgument(0);
            if (!policy.dataType().equals(RESPIRATORY_DATA_TYPE)) return new ApiClient.Page(List.of(), null);
            Instant start = call.getArgument(1);
            Instant end = call.getArgument(2);
            Duration spacing = Duration.between(start, end).dividedBy(3);
            var first = respiratoryPoint(start.plus(spacing));
            var second = respiratoryPoint(start.plus(spacing.multipliedBy(2)));
            return new ApiClient.Page(List.of(first, second, first.deepCopy()), null);
        });

        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isTrue();

        assertThat(batches).isNotEmpty().allSatisfy(batch -> {
            assertThat(batch).hasSize(2);
            assertThat(batch).extracting(StoredPoint::identityKey).doesNotHaveDuplicates();
        });
        assertThat(batches.stream().flatMap(List::stream).map(StoredPoint::identityKey).toList())
                .doesNotHaveDuplicates();
        verify(repository, never()).recordRecentFailure(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void historicalRespiratoryImportKeepsSeparateKeysAcrossPagesAndFinalizes() {
        useHealthMetricsScope();
        properties.setHistoryStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(14));
        when(repository.isFinal(any(), any(), any())).thenReturn(true);
        LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(10);
        when(repository.isFinal(any(), any(), eq(day))).thenReturn(false);
        var parent = new MirrorRepository.HistoryWork(UUID.randomUUID(), day, "HISTORY", 0, "ACTIVE", 0, null);
        Instant start = day.atStartOfDay().toInstant(ZoneOffset.UTC);
        var child = new MirrorRepository.HistoryChild(UUID.randomUUID(), UUID.randomUUID(), start,
                start.plus(Duration.ofDays(1)), "ACTIVE", null, 0, 0);
        var pending = new AtomicBoolean(true);
        var completedChild = new AtomicBoolean(false);
        when(repository.dueWork(any(), any(), eq("HISTORY"), any())).thenAnswer(call -> {
            DataTypePolicy.Policy policy = call.getArgument(1);
            return pending.get() && policy.dataType().equals(RESPIRATORY_DATA_TYPE)
                    ? Optional.of(parent) : Optional.empty();
        });
        when(repository.children(parent.id())).thenAnswer(call -> List.of(completedChild.get()
                ? new MirrorRepository.HistoryChild(child.id(), child.traversal(), child.start(), child.end(),
                "COMPLETED", null, 2, 2) : child));
        when(client.listRaw(any(), any(), any(), any())).thenReturn(
                new ApiClient.Page(List.of(respiratoryPoint(start.plus(Duration.ofHours(7)))), "synthetic-next-page"),
                new ApiClient.Page(List.of(respiratoryPoint(start.plus(Duration.ofHours(15)))), null));
        doAnswer(call -> {
            if (call.getArgument(1) == null) completedChild.set(true);
            return null;
        }).when(repository).checkpointHistoricalChild(eq(child), any(), anyLong(), anyLong(), anyLong(), anyInt(), any());
        doAnswer(call -> {
            pending.set(false);
            return null;
        }).when(repository).completeHistoricalImport(eq(parent));
        doAnswer(call -> {
            pending.set(false);
            return null;
        }).when(repository).recordWorkFailure(eq(parent), anyBoolean(), any(), any());
        when(repository.queryFingerprints(child)).thenReturn(new HashMap<>());
        var batches = new ArrayList<List<StoredPoint>>();
        when(repository.importHistoricalPoints(any())).thenAnswer(call -> {
            List<StoredPoint> rows = call.getArgument(0);
            if (!rows.isEmpty()) batches.add(rows);
            return rows.size();
        });

        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isTrue();

        assertThat(batches).hasSize(2).allSatisfy(batch -> assertThat(batch).hasSize(1));
        assertThat(batches.stream().flatMap(List::stream).map(StoredPoint::identityKey).toList())
                .doesNotHaveDuplicates();
        verify(repository, never()).checkQueryConflicts(any(), any(), any());
        verify(repository, never()).countContentChanges(any(), any());
        verify(repository, never()).missingCountsByDay(any(), any());
        verify(repository, never()).deleteMissingDataPoints(any(), any(), any(), any(), any(), any(), any());
        verify(repository).completeHistoricalImport(parent);
        verify(repository, never()).recordWorkFailure(any(), anyBoolean(), any(), any());
    }

    private void useHealthMetricsScope() {
        var connection = new AccountConnection("synthetic", Set.of(OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS),
                "cipher", AccountConnection.ConnectionState.CONNECTED, null, Instant.now(), null, Instant.now());
        when(repository.findConnection()).thenReturn(Optional.of(connection));
        when(client.dailyRollUp(any(), any(), any(), any())).thenReturn(new ApiClient.Page(List.of(), null));
    }

    private List<List<StoredPoint>> captureRespiratoryBatches() {
        var batches = new ArrayList<List<StoredPoint>>();
        when(repository.upsertDataPoints(any(), any())).thenAnswer(call -> {
            List<StoredPoint> rows = call.getArgument(0);
            if (!rows.isEmpty() && rows.getFirst().dataType().equals(RESPIRATORY_DATA_TYPE)) batches.add(rows);
            return rows.size();
        });
        return batches;
    }

    private ObjectNode respiratoryPoint(Instant time) {
        var point = json.createObjectNode();
        point.put("name", "users/synthetic/dataTypes/" + RESPIRATORY_DATA_TYPE + "/dataPoints/");
        var payload = point.putObject("respiratoryRateSleepSummary");
        payload.putObject("sampleTime").put("physicalTime", time.toString()).put("utcOffset", "0s");
        payload.putObject("fullSleepStats").put("breathsPerMinute", 14.0);
        return point;
    }
}
