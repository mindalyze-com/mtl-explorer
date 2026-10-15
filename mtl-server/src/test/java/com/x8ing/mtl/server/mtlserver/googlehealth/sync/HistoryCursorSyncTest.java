package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiClient;
import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiException;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.exercise.ExerciseRouteService;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.AccountConnection;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistoryCursorSyncTest {
    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");
    private static final int HISTORY_DAYS = 6_123;
    private static final int UNFINISHED_DAYS = 100;
    private static final String NEXT_PAGE = "synthetic-next-page";

    @Test
    void completedStreamsAreSearchedOnceWhileEveryRemainingDayIsFinalized() {
        var f = new Fixture();
        var policy = f.policy("daily-heart-rate-zones");
        for (int i = 1; i <= UNFINISHED_DAYS; i++) f.gap(policy, f.cutoff().minusDays(i));

        assertThat(f.run().success()).isTrue();

        assertThat(f.policies).hasSize(35);
        assertThat(f.gaps.get(policy)).isEmpty();
        assertThat(f.completed).hasSize(UNFINISHED_DAYS);
        assertThat(f.searches.get(policy)).hasSize(UNFINISHED_DAYS + 1);
        f.policies.stream().filter(p -> !p.equals(policy)).forEach(p -> assertThat(f.searches.get(p)).hasSize(1));
        int searches = f.searches.values().stream().mapToInt(List::size).sum();
        int repeatedSearches = (f.policies.size() + 1) * UNFINISHED_DAYS;
        assertThat(searches).isLessThan(repeatedSearches / 20);
        System.out.printf("Synthetic history selection: %d searches versus at least %d repeated searches for %d days.%n",
                searches, repeatedSearches, UNFINISHED_DAYS);
    }

    @Test
    void completingAnOlderResumedWorkItemDoesNotSkipANewerGap() {
        var f = new Fixture();
        var policy = f.policy("daily-heart-rate-zones");
        LocalDate newer = f.cutoff().minusDays(1), older = newer.minusDays(5);
        f.gap(policy, newer);
        f.gap(policy, older);
        f.saved(policy, older, "ACTIVE", null);

        assertThat(f.run().success()).isTrue();

        assertThat(f.completedDays()).containsExactly(older, newer);
        assertThat(f.gaps.get(policy)).isEmpty();
        assertThat(f.searches.get(policy).getFirst()).isEqualTo(f.cutoff());
    }

    @Test
    void waitingAndParkedRangesArePreservedWhileOtherGapsFinish() {
        var f = new Fixture();
        var policy = f.policy("daily-heart-rate-zones");
        LocalDate waiting = f.cutoff().minusDays(1), available = waiting.minusDays(1), parked = available.minusDays(1);
        f.gap(policy, waiting);
        f.gap(policy, available);
        f.gap(policy, parked);
        var retry = f.saved(policy, waiting, "WAITING", NOW.plusSeconds(3600));
        var stopped = f.saved(policy, parked, "PARKED", null);

        assertThat(f.run().success()).isTrue();

        assertThat(f.completedDays()).containsExactly(available);
        assertThat(f.gaps.get(policy)).containsExactly(parked, waiting);
        assertThat(retry.work.state()).isEqualTo("WAITING");
        assertThat(stopped.work.state()).isEqualTo("PARKED");
    }

    @Test
    void exhaustedStreamStillResumesSavedWorkOnTheNextTurn() {
        var f = new Fixture();
        var retryPolicy = f.policy("daily-resting-heart-rate");
        var activePolicy = f.policy("daily-heart-rate-zones");
        LocalDate day = f.cutoff().minusDays(1);
        f.gap(activePolicy, day);
        var admitted = new AtomicBoolean();
        f.afterSearch = (policy, result) -> {
            if (policy.equals(retryPolicy) && result.isEmpty() && admitted.compareAndSet(false, true)) {
                f.gap(policy, day);
                f.saved(policy, day, "ACTIVE", null);
            }
        };

        assertThat(f.run().success()).isTrue();

        assertThat(f.completed).extracting(SavedWork::policy).contains(retryPolicy, activePolicy);
        assertThat(f.searches.get(retryPolicy)).hasSize(1);
        assertThat(f.gaps.get(retryPolicy)).isEmpty();
    }

    @Test
    void successfulEmptyBatchAdvancesBelowItsOldestFinalizedDay() {
        var f = new Fixture();
        var policy = f.policy("daily-heart-rate-zones");
        LocalDate newest = f.cutoff().minusDays(1), oldest = newest.minusDays(2);
        for (int i = 0; i < 4; i++) f.gap(policy, newest.minusDays(i));
        f.batchStart = day -> day.equals(newest) ? oldest : day;

        assertThat(f.run().success()).isTrue();

        assertThat(f.gaps.get(policy)).isEmpty();
        assertThat(f.completedDays()).containsExactly(newest, oldest.minusDays(1));
        assertThat(f.searches.get(policy)).containsExactly(f.cutoff(), oldest, oldest.minusDays(1));
    }

    @Test
    void reducingAWideProbeDoesNotAdvancePastItsUnimportedDays() {
        var f = new Fixture();
        var policy = f.policy("daily-heart-rate-zones");
        LocalDate newest = f.cutoff().minusDays(1);
        for (int i = 0; i < 3; i++) f.gap(policy, newest.minusDays(i));
        f.batchStart = day -> day.equals(newest) ? newest.minusDays(2) : day;
        var first = new AtomicBoolean(true);
        f.answer = request -> new ApiClient.Page(List.of(), first.getAndSet(false) ? NEXT_PAGE : null);

        assertThat(f.run().success()).isTrue();

        assertThat(f.gaps.get(policy)).isEmpty();
        assertThat(f.completedDays()).containsExactly(newest, newest.minusDays(1), newest.minusDays(2));
        assertThat(f.searches.get(policy).subList(0, 2)).containsExactly(f.cutoff(), f.cutoff());
    }

    @Test
    void failedWorkDoesNotBecomeCompletedAndCanResumeInAnotherCycle() {
        var f = new Fixture();
        var policy = f.policy("daily-heart-rate-zones");
        LocalDate failed = f.cutoff().minusDays(1), other = failed.minusDays(1);
        f.gap(policy, failed);
        f.gap(policy, other);
        f.answer = request -> {
            if (request.start().equals(SyncWindowPolicy.midnight(failed)))
                throw new ApiException("Synthetic unavailable", 503, true, ApiException.Reason.SERVER);
            return new ApiClient.Page(List.of(), null);
        };

        assertThat(f.run().success()).isFalse();
        assertThat(f.completedDays()).containsExactly(other);
        assertThat(f.gaps.get(policy)).containsExactly(failed);

        f.now.set(NOW.plusSeconds(7200));
        f.answer = request -> new ApiClient.Page(List.of(), null);
        assertThat(f.run().success()).isTrue();
        assertThat(f.gaps.get(policy)).isEmpty();
        assertThat(f.completedDays()).containsExactly(other, failed);
    }

    @Test
    void cancellationAfterAPageCommitResumesItsTokenWithoutSkippingAnotherDay() {
        var f = new Fixture();
        var policy = f.policy("daily-heart-rate-zones");
        LocalDate newest = f.cutoff().minusDays(1);
        f.gap(policy, newest);
        f.gap(policy, newest.minusDays(1));
        f.answer = request -> new ApiClient.Page(List.of(), NEXT_PAGE);
        f.afterPage = () -> f.cancelled.set(true);

        assertThatThrownBy(f::run).isInstanceOf(CancellationException.class);
        assertThat(f.completed).isEmpty();
        assertThat(f.gaps.get(policy)).hasSize(2);
        verify(f.repository, never()).recordWorkFailure(any(), anyBoolean(), any(), any());

        f.cancelled.set(false);
        f.afterPage = () -> {};
        f.answer = request -> new ApiClient.Page(List.of(), null);
        assertThat(f.run().success()).isTrue();
        assertThat(f.requests).extracting(Request::token).contains(NEXT_PAGE);
        assertThat(f.gaps.get(policy)).isEmpty();
    }

    @Test
    void reloadGenerationCancelsTheOldCursorAndTheNextCycleFindsTheReopenedGap() {
        var f = new Fixture();
        var original = f.policy("daily-heart-rate-zones");
        var reloaded = f.policy("daily-resting-heart-rate");
        LocalDate day = f.cutoff().minusDays(1);
        f.gap(original, day);
        f.answer = request -> {
            f.gap(reloaded, day);
            f.generation.incrementAndGet();
            return new ApiClient.Page(List.of(), null);
        };

        assertThatThrownBy(f::run).isInstanceOf(CancellationException.class);
        assertThat(f.completed).isEmpty();

        f.answer = request -> new ApiClient.Page(List.of(), null);
        assertThat(f.run().success()).isTrue();
        assertThat(f.gaps.get(original)).isEmpty();
        assertThat(f.gaps.get(reloaded)).isEmpty();
        assertThat(f.searches.get(reloaded)).contains(f.cutoff());
    }

    @Test
    void aNewCutoffRebuildsThePreviouslyExhaustedCursor() {
        var f = new Fixture();
        var policy = f.policy("daily-heart-rate-zones");
        LocalDate newlyEligible = f.cutoff();
        f.gap(policy, newlyEligible);
        assertThat(f.run().success()).isTrue();
        assertThat(f.completed).isEmpty();

        f.now.set(NOW.plus(Duration.ofDays(1)));
        assertThat(f.run().success()).isTrue();
        assertThat(f.completedDays()).containsExactly(newlyEligible);
        assertThat(f.gaps.get(policy)).isEmpty();
    }

    private record Request(DataTypePolicy.Policy policy, Instant start, Instant end, String token) {}

    private static final class SavedWork {
        private final DataTypePolicy.Policy policy;
        private MirrorRepository.HistoryWork work;
        private final List<MirrorRepository.HistoryChild> children;

        private SavedWork(DataTypePolicy.Policy policy, MirrorRepository.HistoryWork work,
                          List<MirrorRepository.HistoryChild> children) {
            this.policy = policy;
            this.work = work;
            this.children = new ArrayList<>(children);
        }

        private DataTypePolicy.Policy policy() { return policy; }

        private void state(String state, Instant retry) {
            work = new MirrorRepository.HistoryWork(work.id(), work.day(), work.lane(), work.generation(),
                    state, work.attempts(), retry, work.replaceMissing());
        }
    }

    /** Synthetic durable work/audit behavior; real SQL coverage is tested separately. */
    private static final class Fixture {
        private final MirrorRepository repository = mock(MirrorRepository.class);
        private final ApiClient client = mock(ApiClient.class);
        private final SyncCoordinator.Progress progress = mock(SyncCoordinator.Progress.class);
        private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
        private final AtomicLong generation = new AtomicLong();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final ConnectorProperties properties = new ConnectorProperties();
        private final List<DataTypePolicy.Policy> policies;
        private final Map<DataTypePolicy.Policy, NavigableSet<LocalDate>> gaps = new HashMap<>();
        private final Map<DataTypePolicy.Policy, List<LocalDate>> searches = new HashMap<>();
        private final Map<UUID, SavedWork> work = new LinkedHashMap<>();
        private final List<SavedWork> completed = new ArrayList<>();
        private final List<Request> requests = new ArrayList<>();
        private Function<Request, ApiClient.Page> answer = request -> new ApiClient.Page(List.of(), null);
        private Function<LocalDate, LocalDate> batchStart = Function.identity();
        private BiConsumer<DataTypePolicy.Policy, Optional<LocalDate>> afterSearch = (policy, result) -> {};
        private Runnable afterPage = () -> {};
        private final SyncService service;

        private Fixture() {
            properties.setClientId("synthetic");
            properties.setClientSecret("synthetic");
            properties.setRecentWindow(Period.ofDays(7));
            properties.setSyncInterval(Duration.ofMinutes(1));
            properties.setCycleDeadlineMargin(Duration.ofSeconds(1));
            properties.setRecentFastWindow(Duration.ofMinutes(1));
            properties.setRequestTimeout(Duration.ofMillis(10));
            properties.setHistoryStartDate(cutoff().minusDays(HISTORY_DAYS));
            var scopes = Set.of(OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS, OAuthScopes.ACTIVITY_AND_FITNESS, OAuthScopes.SLEEP);
            policies = DataTypePolicy.allowedBy(scopes);
            policies.forEach(policy -> {
                gaps.put(policy, new TreeSet<>());
                searches.put(policy, new ArrayList<>());
            });
            var connection = new AccountConnection("synthetic-history-cursor", scopes, "cipher",
                    AccountConnection.ConnectionState.CONNECTED, null, NOW, null, NOW);
            when(repository.findConnection()).thenReturn(Optional.of(connection));
            when(repository.synchronizationGeneration(any())).thenAnswer(call -> generation.get());
            doAnswer(call -> {
                if (call.getArgument(1, Long.class) != generation.get()) throw new CancellationException("Synthetic reload");
                return null;
            }).when(repository).guardSynchronization(any(), anyLong());
            when(repository.recentPass(any(), any())).thenReturn(Optional.of(new MirrorRepository.RecentPass(
                    UUID.randomUUID(), NOW, NOW, NOW, NOW, "COMPLETED", NOW.plus(Duration.ofDays(30)), 1, 0, 0)));
            when(repository.isFinal(any(), any(), any())).thenAnswer(call -> !gaps.get(call.getArgument(1)).contains(call.getArgument(2)));
            when(repository.dueWork(any(), any(), eq("HISTORY"), any())).thenAnswer(call -> work.values().stream()
                    .filter(saved -> saved.policy.equals(call.getArgument(1)))
                    .map(saved -> saved.work)
                    .filter(saved -> saved.state().equals("ACTIVE") || saved.state().equals("WAITING")
                            && (saved.retryAt() == null || !saved.retryAt().isAfter(call.getArgument(3))))
                    .max(Comparator.comparing(MirrorRepository.HistoryWork::day)));
            when(repository.newestUnmarked(any(), any(), any(), any())).thenAnswer(call -> {
                DataTypePolicy.Policy policy = call.getArgument(1);
                LocalDate floor = call.getArgument(2), before = call.getArgument(3);
                searches.get(policy).add(before);
                Optional<LocalDate> result = gaps.get(policy).headSet(before, false).descendingSet().stream()
                        .filter(day -> !day.isBefore(floor) && !owned(policy, day)).findFirst();
                afterSearch.accept(policy, result);
                return result;
            });
            when(repository.emptyBatchStart(any(), any(), any(), any())).thenAnswer(call -> batchStart.apply(call.getArgument(2)));
            when(repository.createWork(any(), any(), any(), eq("HISTORY"), any())).thenAnswer(call ->
                    allocate(call.getArgument(1), call.getArgument(2), call.getArgument(4)).work);
            when(repository.children(any())).thenAnswer(call -> List.copyOf(work.get(call.getArgument(0)).children));
            when(repository.queryFingerprints(any())).thenAnswer(call -> new HashMap<>());
            doAnswer(call -> {
                MirrorRepository.HistoryWork parent = call.getArgument(0);
                work.get(parent.id()).state("ACTIVE", null);
                return null;
            }).when(repository).activateWork(any());
            doAnswer(call -> {
                MirrorRepository.HistoryChild child = call.getArgument(0);
                String token = call.getArgument(1);
                var saved = work.values().stream().filter(value -> value.children.contains(child)).findFirst().orElseThrow();
                saved.children.set(saved.children.indexOf(child), new MirrorRepository.HistoryChild(child.id(), child.traversal(),
                        child.start(), child.end(), token == null ? "COMPLETED" : "ACTIVE", token, call.getArgument(5), call.getArgument(2)));
                return null;
            }).when(repository).checkpointHistoricalChild(any(), any(), anyLong(), anyLong(), anyLong(), anyInt(), any());
            doAnswer(call -> {
                MirrorRepository.HistoryWork parent = call.getArgument(0);
                var saved = work.get(parent.id());
                assertThat(saved.children).allMatch(child -> child.state().equals("COMPLETED"));
                LocalDate start = saved.children.stream().map(child -> date(child.start())).min(LocalDate::compareTo).orElseThrow();
                LocalDate end = saved.children.stream().map(child -> date(child.end())).max(LocalDate::compareTo).orElseThrow();
                gaps.get(saved.policy).removeIf(day -> !day.isBefore(start) && day.isBefore(end));
                saved.state("COMPLETED", null);
                completed.add(saved);
                return null;
            }).when(repository).completeHistoricalImport(any());
            doAnswer(call -> {
                MirrorRepository.HistoryWork parent = call.getArgument(0);
                boolean retry = call.getArgument(1);
                work.get(parent.id()).state(retry ? "WAITING" : "PARKED", retry ? now.get().plusSeconds(3600) : null);
                return null;
            }).when(repository).recordWorkFailure(any(), anyBoolean(), any(), any());
            when(repository.reduceHistoryToDay(any(), any(), any())).thenAnswer(call -> {
                MirrorRepository.HistoryWork parent = call.getArgument(2);
                work.remove(parent.id());
                var policy = call.getArgument(1, DataTypePolicy.Policy.class);
                var bounds = new SyncWindowPolicy.Bounds(policy.windowTimeDomain(), SyncWindowPolicy.midnight(parent.day()),
                        SyncWindowPolicy.midnight(parent.day().plusDays(1)));
                return allocate(policy, parent.day(), SyncWindowPolicy.cells(policy, bounds)).work;
            });
            when(client.listRaw(any(), any(), any(), any())).thenAnswer(call -> fetch(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
            when(client.reconcile(any(), any(), any(), any())).thenAnswer(call -> fetch(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));
            when(client.dailyRollUp(any(), any(), any(), any())).thenReturn(new ApiClient.Page(List.of(), null));
            when(progress.isCancelled()).thenAnswer(call -> cancelled.get());
            doAnswer(call -> { afterPage.run(); return null; }).when(progress).pageCompleted(anyLong());
            var manager = mock(PlatformTransactionManager.class);
            when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            var clock = new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zone) { return this; }
                @Override public Instant instant() { return now.get(); }
            };
            service = new SyncService(properties, repository, client, new PointMapper(new DataSourceResolver(repository)),
                    mock(ExerciseRouteService.class), manager, clock);
        }

        private DataTypePolicy.Policy policy(String type) {
            return policies.stream().filter(policy -> policy.dataType().equals(type) && policy.mode() == DataTypePolicy.Mode.RAW).findFirst().orElseThrow();
        }

        private LocalDate cutoff() { return SyncWindowPolicy.cutoff(properties, now.get()); }
        private void gap(DataTypePolicy.Policy policy, LocalDate day) { gaps.get(policy).add(day); }
        private List<LocalDate> completedDays() { return completed.stream().map(saved -> saved.work.day()).toList(); }
        private SyncService.SyncResult run() { return service.run(SyncCoordinator.JobKind.REGULAR, progress); }
        private static LocalDate date(Instant instant) { return instant.atOffset(ZoneOffset.UTC).toLocalDate(); }

        private SavedWork saved(DataTypePolicy.Policy policy, LocalDate day, String state, Instant retry) {
            var bounds = new SyncWindowPolicy.Bounds(policy.windowTimeDomain(), SyncWindowPolicy.midnight(day), SyncWindowPolicy.midnight(day.plusDays(1)));
            var saved = allocate(policy, day, SyncWindowPolicy.cells(policy, bounds));
            saved.state(state, retry);
            return saved;
        }

        private SavedWork allocate(DataTypePolicy.Policy policy, LocalDate day, List<SyncWindowPolicy.Bounds> cells) {
            var parent = new MirrorRepository.HistoryWork(UUID.randomUUID(), day, "HISTORY", 0, "ACTIVE", 0, null);
            var children = cells.stream().map(cell -> new MirrorRepository.HistoryChild(UUID.randomUUID(), UUID.randomUUID(),
                    cell.start(), cell.end(), "ACTIVE", null, 0, 0)).toList();
            var saved = new SavedWork(policy, parent, children);
            work.put(parent.id(), saved);
            return saved;
        }

        private boolean owned(DataTypePolicy.Policy policy, LocalDate day) {
            return work.values().stream().filter(saved -> saved.policy.equals(policy) && !saved.work.state().equals("COMPLETED"))
                    .flatMap(saved -> saved.children.stream()).anyMatch(child -> !day.isBefore(date(child.start())) && day.isBefore(date(child.end())));
        }

        private ApiClient.Page fetch(DataTypePolicy.Policy policy, Instant start, Instant end, String token) {
            var request = new Request(policy, start, end, token);
            requests.add(request);
            return answer.apply(request);
        }
    }
}
