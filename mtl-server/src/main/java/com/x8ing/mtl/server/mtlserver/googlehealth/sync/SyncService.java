package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiException;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.exercise.ExerciseRouteWork;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.AccountConnection;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.HeartRateSample;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.StoredPoint;
import com.x8ing.mtl.server.mtlserver.googlehealth.provider.GoogleHealthProvider;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.GHCycleStatistics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncWindowPolicy.*;

/**
 * One serialized worker. Recent answers replace complete chunks; history imports each day once.
 */
@Service
@Slf4j
public class SyncService {
    private static final double FULL_RESERVATION = 0.25;
    private static final double ROUTE_RESERVATION = 0.10;
    // Minimum time needed to start work; this is not the transaction's execution limit.
    private static final Duration COMMIT_HEADROOM = Duration.ofSeconds(1);
    private static final int SAFE_ERROR_LIMIT = 500;
    private static final int QUERY_FINGERPRINT_ENTRY_BYTES = 134;
    private static final String HISTORY_LANE = "HISTORY";
    private final ConnectorProperties properties;
    private final MirrorRepository repository;
    private final GoogleHealthProvider apiClient;
    private final PointMapper mapper;
    private final ExerciseRouteWork routes;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;
    private AccountConnection connection;
    private long generation;
    private Instant now;
    private LocalDate cutoff;
    private long fetched, changed, stamped, deleted;
    private long historyGapSearches;
    private int completeFastStreams;
    private int streamCount;
    private boolean budgetLimited;
    private SyncCoordinator.Progress progress;
    private final Set<String> errors = new LinkedHashSet<>();
    private final Map<DataTypePolicy.Policy, HistoryCursor> historyCursors = new HashMap<>();

    @Autowired
    public SyncService(ConnectorProperties properties, MirrorRepository repository, GoogleHealthProvider apiClient,
                       PointMapper mapper, ExerciseRouteWork routes, PlatformTransactionManager manager) {
        this(properties, repository, apiClient, mapper, routes, manager, Clock.systemUTC());
    }

    public SyncService(ConnectorProperties properties, MirrorRepository repository, GoogleHealthProvider apiClient,
                       PointMapper mapper, ExerciseRouteWork routes, PlatformTransactionManager manager, Clock clock) {
        this.properties = properties;
        this.repository = repository;
        this.apiClient = apiClient;
        this.mapper = mapper;
        this.routes = routes;
        transactionManager = manager;
        this.clock = Objects.requireNonNull(clock);
    }

    public SyncResult run(SyncCoordinator.JobKind kind, SyncCoordinator.Progress progress) {
        if (!properties.isConfigured()) throw new IllegalStateException("Google Health is not configured");
        properties.validateSynchronization();
        this.progress = progress;
        now = Instant.now(clock);
        long cycleStarted = System.nanoTime();
        cutoff = cutoff(properties, now);
        errors.clear();
        historyCursors.clear();
        fetched = 0;
        changed = 0;
        stamped = 0;
        deleted = 0;
        historyGapSearches = 0;
        completeFastStreams = 0;
        budgetLimited = false;
        connection = repository.findConnection().filter(c -> c.state() == AccountConnection.ConnectionState.CONNECTED)
                .orElseThrow(() -> new IllegalStateException("Google Health is not connected"));
        generation = repository.synchronizationGeneration(connection.googleUserId());
        mapper.useAccount(connection.googleUserId());
        List<DataTypePolicy.Policy> policies = DataTypePolicy.allowedBy(connection.grantedScopes());
        if (policies.isEmpty()) throw new IllegalStateException("No Google Health categories were granted");
        streamCount = policies.size();
        long cycleEnd = cycleStarted + properties.getSyncInterval().minus(properties.getCycleDeadlineMargin()).toNanos();
        Instant dispatchedDeadline = progress.cycleDeadline();
        if (dispatchedDeadline != null) {
            long remaining = Math.max(0, Duration.between(Instant.now(clock), dispatchedDeadline).toNanos());
            cycleEnd = Math.min(cycleEnd, System.nanoTime() + remaining);
        }
        long usable = Math.max(0, cycleEnd - System.nanoTime());
        apiClient.beginCycle();
        if (apiClient.coolingDown()) return new SyncResult(false, List.of("Google Health quota cooldown is active"));
        try (SyncBudget cycle = new SyncBudget(cycleEnd)) {
            guarded(() -> repository.handOverFailures(account(), midnight(cutoff)));
            initializeFullPass(policies);
            for (var policy : policies) {
                guarded(() -> repository.recentCycle(account(), policy));
            }
            int rotation = Math.floorMod(repository.rotation(account(), false), policies.size());
            var ordered = rotate(policies, rotation);
            boolean historicalRunnable = policies.stream().anyMatch(this::historyRunnable);
            long remaining = SyncBudget.remainingNanos();
            long recentShare = historicalRunnable ? (long) (remaining * (1 - properties.getHistoryBudgetFraction())) : remaining;
            long recentEnd = Math.min(cycleEnd, System.nanoTime() + recentShare);
            boolean fullRunnable = policies.stream().anyMatch(this::recentRunnable);
            boolean routeRunnable = connection.grantedScopes().contains(OAuthScopes.LOCATION)
                                    && !repository.findDueExerciseRouteRetries(1).isEmpty();
            long routeShare = routeRunnable ? Math.min(properties.getHistoryStreamQuantum().toNanos(), (long) (recentShare * ROUTE_RESERVATION)) : 0;
            long fullEnd = recentEnd - routeShare;
            long fastEnd = Math.min(fullEnd, System.nanoTime() + (long) (recentShare * (fullRunnable ? 1 - FULL_RESERVATION : 1)));
            log.info("Google Health cycle started: streams={}, cutoff={}, usableMs={}, recentMs={}, historyRunnable={}",
                    policies.size(), cutoff, usable / 1_000_000, recentShare / 1_000_000, historicalRunnable);
            progress.setTotalUnits(0);
            int nextRotation = rotation;
            boolean allFast = true;
            for (int i = 0; i < ordered.size(); i++) {
                var policy = ordered.get(i);
                boolean complete = false;
                boolean limited = false;
                Bounds bounds = recent(policy, properties, now, true);
                try (SyncBudget fast = new SyncBudget(fastEnd)) {
                    complete = runFast(policy, bounds);
                } catch (SyncBudget.Yield yield) {
                    limited = true;
                    log.info("Google Health fast pass yielded: stream={}, reason=budget", policy.dataType());
                }
                boolean finished = complete;
                boolean capacityLimited = limited;
                guarded(() -> repository.fastStatus(account(), policy, bounds.start(), bounds.end(), finished, capacityLimited));
                if (complete) completeFastStreams++;
                budgetLimited |= limited;
                publishStatistics();
                if (!complete && allFast) nextRotation = Math.floorMod(rotation + i, policies.size());
                allFast &= complete;
            }
            int recentRotation = allFast ? (rotation + 1) % policies.size() : nextRotation;
            guarded(() -> repository.rotate(account(), false, recentRotation));
            try (SyncBudget recent = new SyncBudget(fullEnd)) {
                runRecent(ordered);
            } catch (SyncBudget.Yield yield) {
                log.info("Google Health recent work yielded: reason=budget");
            }
            // Route work stays inside the recent reservation; history keeps its guaranteed share.
            if (routeRunnable) try (SyncBudget routeBudget = new SyncBudget(recentEnd)) {
                apiClient.purpose("ROUTE_EXPORT");
                if (SyncBudget.has(properties.getRequestTimeout().plus(COMMIT_HEADROOM))) {
                    var result = routes.retryDueRoutes(progress);
                    if (result != null && result.failed() > 0) errors.add("Some exercise route exports need retry.");
                }
            } catch (SyncBudget.Yield yield) {
                log.info("Google Health route turn yielded: reason=budget");
            }
            runHistory(policies, cycleEnd);
            // Borrow history's unused share without repeating fast work or starting a pass early.
            runRecent(ordered);
        } catch (SyncBudget.Yield yield) {
            budgetLimited = true;
            log.info("Google Health cycle yielded: reason=budget");
        } catch (GoogleHealthProvider.QuotaPause pause) {
            errors.add("Google Health quota cooldown is active");
            log.info("Google Health cycle paused: reason=quota");
        } catch (ApiException exception) {
            if (exception.getReason() == ApiException.Reason.AUTHORIZATION || exception.getReason() == ApiException.Reason.MISSING_SCOPE) {
                repository.markReconnectRequired(connection.authorizationGeneration(), "Google Health authorization needs attention");
            }
            log.warn("Google Health cycle paused: type={}, reason={}", exception.getClass().getSimpleName(), exception.getReason());
            errors.add(safeError(exception));
        }
        log.info("Google Health cycle finished: durationMs={}, errors={}, requests={}, historyGapSearches={}",
                (System.nanoTime() - cycleStarted) / 1_000_000, errors.size(), apiClient.counters(), historyGapSearches);
        return new SyncResult(errors.isEmpty(), List.copyOf(errors), false,
                new GHCycleStatistics(fetched, changed, stamped, deleted,
                        completeFastStreams, policies.size(), budgetLimited, apiClient.counters()));
    }

    private String account() {
        return connection.googleUserId();
    }

    private static <T> List<T> rotate(List<T> list, int index) {
        List<T> result = new ArrayList<>(list);
        Collections.rotate(result, -Math.floorMod(index, list.size()));
        return result;
    }

    private void initializeFullPass(List<DataTypePolicy.Policy> policies) {
        var passes = policies.stream().map(policy -> repository.recentPass(account(), policy).orElse(null)).filter(Objects::nonNull).toList();
        var active = passes.stream().filter(pass -> pass.state().equals("ACTIVE")).findFirst().orElse(null);
        boolean start = passes.size() < policies.size() || (active == null && passes.stream().anyMatch(pass -> pass.nextDueAt() == null || !pass.nextDueAt().isAfter(now)));
        if (!start) return;
        UUID id = active == null ? UUID.randomUUID() : active.id();
        Instant targetNow = active == null ? now : active.startedAt();
        guarded(() -> {
            for (var policy : policies) {
                if (active != null && repository.recentPass(account(), policy).isPresent()) continue;
                Bounds target = recent(policy, properties, targetNow, false);
                repository.startRecentPass(account(), policy, id, targetNow, target.start(), target.end());
            }
        });
    }

    private void sealFullPass(List<DataTypePolicy.Policy> policies) {
        var passes = policies.stream().map(policy -> repository.recentPass(account(), policy).orElse(null)).filter(Objects::nonNull).toList();
        if (passes.size() == policies.size() && passes.stream().noneMatch(pass -> pass.state().equals("ACTIVE"))) {
            var pending = passes.stream().filter(pass -> pass.nextDueAt() == null).findFirst().orElse(null);
            if (pending != null)
                guarded(() -> repository.sealRecentEpoch(account(), pending.id(), Instant.now(clock), Instant.now(clock).plus(properties.getRecentFullInterval())));
        }
    }

    private boolean recentRunnable(DataTypePolicy.Policy policy) {
        return repository.dueWork(account(), policy, "RELOAD", now).isPresent()
               || repository.recentPass(account(), policy).map(p -> p.state().equals("ACTIVE")).orElse(false);
    }

    private boolean historyRunnable(DataTypePolicy.Policy policy) {
        return selectHistory(policy).isPresent();
    }

    private Optional<HistorySelection> selectHistory(DataTypePolicy.Policy policy) {
        requireNotCancelled();
        // Saved retries remain runnable even after the search for unallocated days is exhausted.
        var due = repository.dueWork(account(), policy, HISTORY_LANE, now);
        if (due.isPresent()) return Optional.of(new HistorySelection(due.get(), due.get().day()));
        var cursor = historyCursor(policy);
        if (cursor.exhausted) return Optional.empty();
        if (cursor.candidateDay == null) {
            historyGapSearches++;
            cursor.candidateDay = repository.newestUnmarked(account(), policy, properties.getHistoryStartDate(), cursor.before).orElse(null);
            if (cursor.candidateDay == null || completion(policy, cursor.candidateDay).isAfter(now)) {
                cursor.exhausted = true;
                return Optional.empty();
            }
        }
        return Optional.of(new HistorySelection(null, cursor.candidateDay));
    }

    private HistoryCursor historyCursor(DataTypePolicy.Policy policy) {
        return historyCursors.computeIfAbsent(policy, ignored -> new HistoryCursor(cutoff));
    }

    private void advanceHistoryCursor(DataTypePolicy.Policy policy, MirrorRepository.HistoryWork work,
                                      List<MirrorRepository.HistoryChild> children) {
        var cursor = historyCursor(policy);
        // An older resumed work item does not prove that newer unallocated gaps are complete.
        if (!work.id().equals(cursor.selectedWorkId)) return;
        LocalDate start = children.stream().map(child -> child.start().atOffset(ZoneOffset.UTC).toLocalDate())
                .min(LocalDate::compareTo).orElse(work.day());
        if (start.isBefore(cursor.before)) cursor.before = start;
        cursor.selectedWorkId = null;
    }

    private boolean runFast(DataTypePolicy.Policy policy, Bounds bounds) {
        boolean complete = true;
        for (Bounds cell : cells(policy, bounds)) {
            requireWorkTime();
            if (repository.isFinal(account(), policy, cell.day())) continue;
            Bounds gridCell = gridCell(policy, cell.start());
            Outcome outcome = recentChunk(policy, cell, gridCell, "FAST", null);
            complete &= outcome == Outcome.SUCCESS;
        }
        return complete;
    }

    private Bounds gridCell(DataTypePolicy.Policy policy, Instant point) {
        Instant day = midnight(point.atOffset(ZoneOffset.UTC).toLocalDate());
        Duration step = grid(policy);
        long index = Duration.between(day, point).toSeconds() / step.toSeconds();
        Instant start = day.plus(step.multipliedBy(index));
        return new Bounds(policy.windowTimeDomain(), start, start.plus(step));
    }

    private void runRecent(List<DataTypePolicy.Policy> policies) {
        boolean madeProgress;
        do {
            madeProgress = false;
            for (var policy : policies) {
                if (!recentRunnable(policy)) continue;
                requireWorkTime();
                var reload = repository.dueWork(account(), policy, "RELOAD", now).orElse(null);
                if (reload != null) {
                    if (reload.day().isBefore(cutoff) || reload.day().isBefore(properties.getHistoryStartDate())) {
                        // Crossing the cutoff transfers ownership to the ordinary historical query.
                        guarded(() -> repository.revokeWork(reload));
                    } else runRecentReload(policy, reload);
                    madeProgress = true;
                    continue;
                }
                var pass = repository.recentPass(account(), policy).orElseThrow();
                if (!pass.state().equals("ACTIVE")) continue;
                if (!pass.nextEnd().isAfter(pass.start())) {
                    guarded(() -> repository.finishRecentPass(account(), policy, pass.id(), Instant.now(clock), Instant.now(clock).plus(properties.getRecentFullInterval())));
                    madeProgress = true;
                    continue;
                }
                Instant last = pass.nextEnd().minusNanos(1000);
                Bounds grid = gridCell(policy, last);
                Instant start = grid.start().isBefore(pass.start()) ? pass.start() : grid.start();
                Bounds cell = new Bounds(policy.windowTimeDomain(), start, pass.nextEnd());
                boolean handed = cell.day().isBefore(cutoff);
                boolean outsideFloor = cell.day().isBefore(properties.getHistoryStartDate());
                if (handed || outsideFloor || repository.isFinal(account(), policy, cell.day())) {
                    guarded(() -> repository.advanceRecentPass(account(), policy, pass.id(), start, 0, 0, 0, outsideFloor, handed));
                    madeProgress = true;
                    continue;
                }
                Outcome outcome = recentChunk(policy, cell, grid, "FULL", position -> repository.advanceRecentPass(account(), policy, pass.id(), position, 0, 0, 0, false, false));
                boolean skip = outcome == Outcome.SKIPPED || (outcome == Outcome.WAITING
                        && repository.recentFailureSlowRetry(account(), policy, start, cell.end(), Instant.now(clock)));
                if (skip) guarded(() -> repository.advanceRecentPass(account(), policy, pass.id(), start, 0, 0, 0, true, false));
                madeProgress |= skip || outcome != Outcome.WAITING;
            }
        } while (madeProgress);
        sealFullPass(policies);
    }

    private void runRecentReload(DataTypePolicy.Policy policy, MirrorRepository.HistoryWork work) {
        guarded(() -> repository.activateWork(work));
        for (var child : repository.children(work.id())) {
            if (child.state().equals("COMPLETED")) continue;
            Bounds bounds = new Bounds(policy.windowTimeDomain(), child.start(), child.end());
            Outcome outcome = recentChunk(policy, bounds, bounds, "RELOAD", position -> {
                repository.guardWork(work, child);
                repository.checkpointChild(child, null, 0, 0, 1);
            });
            if (outcome != Outcome.SUCCESS) {
                guarded(() -> repository.deferRecentReload(work, account(), policy, bounds.start(), bounds.end()));
                return;
            }
        }
        guarded(() -> repository.completeWork(work, false, 0));
    }

    private Outcome recentChunk(DataTypePolicy.Policy policy, Bounds bounds, Bounds logical, String purpose, Consumer<Instant> checkpoint) {
        String failure = repository.recentFailureState(account(), policy, logical.start(), logical.end(), Instant.now(clock));
        if (failure != null) return failure.equals("SKIPPED") ? Outcome.SKIPPED : Outcome.WAITING;
        progress.addTotalUnits(1);
        progress.startUnit(policy.dataType(), policy.representation(), bounds.start(), bounds.end());
        apiClient.purpose("RECENT_" + purpose);
        try {
            requireWorkTime();
            GoogleHealthProvider.Page first = fetch(policy, bounds, null);
            if (first.hasNextPage()) {
                Instant middle = split(logical);
                if (middle != null) {
                    apiClient.countProbe();
                    List<Bounds> subdivisions = List.of(new Bounds(logical.domain(), middle, logical.end()), new Bounds(logical.domain(), logical.start(), middle));
                    for (var part : subdivisions) {
                        Instant start = part.start().isBefore(bounds.start()) ? bounds.start() : part.start();
                        Instant end = part.end().isAfter(bounds.end()) ? bounds.end() : part.end();
                        if (!start.isBefore(end)) continue;
                        Outcome outcome = recentChunk(policy, new Bounds(bounds.domain(), start, end), part, purpose, purpose.equals("FULL") ? checkpoint : null);
                        if (outcome != Outcome.SUCCESS) return outcome;
                    }
                    // A former parent failure is covered only after every subdivision succeeds.
                    completeBatch(() -> {
                        repository.clearCoveredFailures(account(), policy, bounds.start(), bounds.end());
                        if (checkpoint != null && !purpose.equals("FULL")) checkpoint.accept(bounds.start());
                    });
                    return Outcome.SUCCESS;
                }
            }
            LinkedHashMap<String, JsonNode> answer = new LinkedHashMap<>();
            Set<String> tokens = new HashSet<>();
            long bytes = 0;
            int pageCount = 0;
            GoogleHealthProvider.Page page = first;
            while (true) {
                for (JsonNode point : page.points()) {
                    bytes += point.toString().getBytes(StandardCharsets.UTF_8).length;
                    if (bytes > properties.getMaxChunkBytes()) throw new SyncValidationException(SyncValidationException.Reason.CHUNK_BYTE_LIMIT);
                    addValidated(policy, bounds, answer, point);
                    if (answer.size() > properties.getMaxChunkRows()) throw new SyncValidationException(SyncValidationException.Reason.CHUNK_ROW_LIMIT);
                }
                if (++pageCount > properties.getMaxPagesPerWindow()) throw new SyncValidationException(SyncValidationException.Reason.CHUNK_PAGE_LIMIT);
                if (!page.hasNextPage()) break;
                if (!tokens.add(StableFingerprint.of(page.nextPageToken())))
                    throw new SyncValidationException(SyncValidationException.Reason.REPEATED_PAGE_TOKEN);
                requireWorkTime();
                page = fetch(policy, bounds, page.nextPageToken());
            }
            final long[] counts = new long[2];
            completeBatch(() -> {
                verifyRecent(policy, bounds);
                counts[0] = persist(policy, answer, null);
                counts[1] = repository.replaceRecent(policy, bounds.start(), bounds.end(), answer.keySet());
                repository.clearCoveredFailures(account(), policy, bounds.start(), bounds.end());
                if (purpose.equals("FULL")) repository.addRecentPassCounts(account(), policy, answer.size(), counts[0], counts[1]);
                if (checkpoint != null) checkpoint.accept(bounds.start());
            });
            changed += counts[0];
            deleted += counts[1];
            publishStatistics();
            progress.completeUnit(answer.size(), counts[1]);
            log.info("Google Health recent chunk committed: stream={}, representation={}, purpose={}, start={}, end={}, fetched={}, changed={}, deleted={}",
                    policy.dataType(), policy.representation(), purpose, bounds.start(), bounds.end(), answer.size(), counts[0], counts[1]);
            return Outcome.SUCCESS;
        } catch (SyncBudget.Yield | GoogleHealthProvider.QuotaPause | CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            requireNotCancelled();
            if (exception instanceof ApiException api && (api.getReason() == ApiException.Reason.AUTHORIZATION || api.getReason() == ApiException.Reason.MISSING_SCOPE))
                throw api;
            String safe = safeError(exception);
            var diagnostic = FailureDiagnostic.from(exception);
            log.warn("Google Health recent chunk failed: stream={}, representation={}, purpose={}, queryDomain={}, type={}, reason={}, detail={}, error={}",
                    policy.dataType(), policy.representation(), purpose, policy.windowTimeDomain(),
                    exception.getClass().getSimpleName(), diagnostic.reason(), diagnostic.detail(), safe);
            if (log.isDebugEnabled()) log.debug("Recent chunk failure: type={}, reason={}",
                    exception.getClass().getSimpleName(), diagnostic.reason(), diagnostic.safeTrace(exception));
            completeBatch(() -> repository.recordRecentFailure(account(), policy, purpose, logical.start(), logical.end(), bounds.start(), bounds.end(),
                    WorkRetryPolicy.isRecoverable(exception), Instant.now(clock), safe));
            errors.add(policy.dataType() + ": " + safe);
            progress.failUnit(safe);
            return Outcome.WAITING;
        }
    }

    private void runHistory(List<DataTypePolicy.Policy> policies, long cycleEnd) {
        int rotation = Math.floorMod(repository.rotation(account(), true), policies.size());
        boolean runnable = true;
        while (runnable) {
            runnable = false;
            for (var policy : rotate(policies, rotation)) {
                var selection = selectHistory(policy).orElse(null);
                if (selection == null) continue;
                requireWorkTime();
                runnable = true;
                int next = (policies.indexOf(policy) + 1) % policies.size();
                long turn = Math.min(cycleEnd, System.nanoTime() + properties.getHistoryStreamQuantum().toNanos());
                try (SyncBudget slice = new SyncBudget(turn)) {
                    historicalTurn(policy, selection, next);
                } catch (SyncBudget.Yield yield) {
                    log.info("Google Health historical stream yielded: stream={}", policy.dataType());
                }
                rotation = next;
                guarded(() -> repository.rotate(account(), true, next));
            }
        }
    }

    private void historicalTurn(DataTypePolicy.Policy policy, HistorySelection selection, int nextRotation) {
        var work = selection.work();
        if (work == null) {
            var day = selection.day();
            LocalDate start = repository.emptyBatchStart(account(), policy, day, properties.getHistoryStartDate());
            Bounds bounds = new Bounds(policy.windowTimeDomain(), midnight(start), midnight(day.plusDays(1)));
            work = transact(() -> repository.createWork(account(), policy, day, HISTORY_LANE,
                    start.equals(day) ? cells(policy, bounds) : List.of(bounds)));
            var cursor = historyCursor(policy);
            cursor.candidateDay = null;
            cursor.selectedWorkId = work.id();
        }
        final var parent = work;
        if (repository.isFinal(account(), policy, parent.day())) {
            guarded(() -> repository.revokeWork(parent));
            return;
        }
        if (!parent.day().isBefore(cutoff) || parent.day().isBefore(properties.getHistoryStartDate())) {
            guarded(() -> repository.revokeWork(parent));
            return;
        }
        guarded(() -> repository.activateWork(parent));
        apiClient.purpose("HISTORICAL_FINAL_CHECK");
        try {
            var children = repository.children(parent.id());
            for (var child : children) {
                if (child.start().atOffset(ZoneOffset.UTC).toLocalDate().isBefore(properties.getHistoryStartDate())) {
                    guarded(() -> repository.reduceHistoryToDay(account(), policy, parent));
                    return;
                }
                if (child.state().equals("COMPLETED")) continue;
                Map<String, String> queryFingerprints = parent.replaceMissing() ? new HashMap<>() : repository.queryFingerprints(child);
                if (queryFingerprints == null) {
                    // A checkpoint written before query-local validation existed restarts without deleting its records.
                    if (child.pages() > 0) {
                        completeBatch(() -> repository.restartHistoricalChild(parent, child));
                        return;
                    }
                    queryFingerprints = new HashMap<>();
                }
                final Map<String, String> validation = queryFingerprints;
                String token = child.token();
                int pageCount = child.pages();
                long imported = 0;
                progress.addTotalUnits(1);
                progress.startUnit(policy.dataType(), policy.representation(), child.start(), child.end());
                progress.restoreWindowProgress(0);
                while (true) {
                    requireWorkTime();
                    if (pageCount >= properties.getMaxPagesPerWindow()) throw new SyncValidationException(SyncValidationException.Reason.HISTORY_PAGE_LIMIT);
                    Bounds bounds = new Bounds(policy.windowTimeDomain(), child.start(), child.end());
                    GoogleHealthProvider.Page page;
                    try {
                        page = fetch(policy, bounds, token);
                    } catch (ApiException exception) {
                        if (token != null && exception.getReason() == ApiException.Reason.INVALID_PAGE_TOKEN) {
                            Instant middle = split(bounds);
                            if (middle != null) {
                                completeBatch(() -> {
                                    repository.splitChild(parent, child, middle);
                                    repository.rotate(account(), true, nextRotation);
                                });
                                return;
                            }
                        }
                        throw exception;
                    }
                    if (Duration.between(child.start(), child.end()).compareTo(Duration.ofDays(1)) > 0
                        && (!page.points().isEmpty() || page.hasNextPage())) {
                        apiClient.countProbe();
                        completeBatch(() -> {
                            repository.reduceHistoryToDay(account(), policy, parent);
                            repository.rotate(account(), true, nextRotation);
                        });
                        log.info("Google Health empty-history batch reduced after data appeared: stream={}, workId={}", policy.dataType(), parent.id());
                        return;
                    }
                    LinkedHashMap<String, JsonNode> answer = new LinkedHashMap<>();
                    HistoricalPage prepared;
                    if (parent.replaceMissing()) {
                        for (var point : page.points()) addValidated(policy, bounds, answer, point);
                        prepared = null;
                    } else {
                        prepared = prepareHistoricalPage(policy, bounds, page.points());
                        prepared.fingerprints().forEach((key, fingerprint) -> {
                            String previous = validation.putIfAbsent(key, fingerprint);
                            if (previous != null && !previous.equals(fingerprint))
                                throw new SyncValidationException(SyncValidationException.Reason.DUPLICATE_TRAVERSAL_IDENTITY);
                        });
                        if (validation.size() > properties.getMaxChunkRows()
                            || (long) validation.size() * QUERY_FINGERPRINT_ENTRY_BYTES + 2 > properties.getMaxChunkBytes())
                            throw new SyncValidationException(SyncValidationException.Reason.HISTORY_VALIDATION_LIMIT);
                    }
                    int returned = prepared == null ? answer.size() : prepared.size();
                    int committedPages = ++pageCount;
                    long[] pageChanged = {0};
                    completeBatch(() -> {
                        verifyHistory(parent);
                        repository.guardWork(parent, child);
                        repository.guardUnmarkedHistory(parent, account(), policy);
                        String next = page.hasNextPage() ? page.nextPageToken() : null;
                        if (parent.replaceMissing()) {
                            pageChanged[0] = persist(policy, answer, child.traversal());
                            repository.checkpointChild(child, next, page.points().size(), pageChanged[0], returned, committedPages);
                        } else {
                            pageChanged[0] = persistHistoricalPage(prepared);
                            repository.checkpointHistoricalChild(child, next, page.points().size(), pageChanged[0], returned, committedPages, validation);
                        }
                        repository.rotate(account(), true, nextRotation);
                    });
                    changed += pageChanged[0];
                    if (parent.replaceMissing()) stamped += returned;
                    publishStatistics();
                    imported += returned;
                    progress.pageCompleted(imported);
                    if (!page.hasNextPage()) break;
                    token = page.nextPageToken();
                }
                progress.completeUnit(imported, 0);
            }
            requireCommitTime();
            long[] swept = {0};
            completeBatch(() -> {
                verifyHistory(parent);
                repository.guardWork(parent, null);
                repository.guardUnmarkedHistory(parent, account(), policy);
                if (!parent.day().isBefore(cutoff) || completion(policy, parent.day()).isAfter(now))
                    throw new CancellationException("Historical day is not eligible");
                if (!parent.replaceMissing()) {
                    repository.completeHistoricalImport(parent);
                    repository.rotate(account(), true, nextRotation);
                    return;
                }
                int deleted = 0;
                Map<LocalDate, Long> deletedDays = new HashMap<>();
                for (var child : repository.children(parent.id())) {
                    if (!child.state().equals("COMPLETED")) throw new IllegalStateException("Incomplete child");
                    repository.missingCountsByDay(policy, child).forEach((day, count) -> deletedDays.merge(day, count, Long::sum));
                    if (policy.dataType().equals("exercise")) repository.retireMissingHistoricalExercises(child.start(), child.end(), child.traversal());
                    deleted += policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE
                            ? repository.deleteMissingHeartRateSamples(child.start(), child.end(), child.traversal())
                            : repository.deleteMissingDataPoints(policy.dataType(), StoredRepresentationMapper.from(policy.mode()), policy.recordType(), policy.windowTimeDomain(), child.start(), child.end(), child.traversal());
                }
                repository.completeWork(parent, true, deleted, deletedDays);
                repository.rotate(account(), true, nextRotation);
                swept[0] = deleted;
            });
            advanceHistoryCursor(policy, parent, children);
            deleted += swept[0];
            log.info("Google Health historical day finalized: stream={}, day={}, workId={}, replacement={}",
                    policy.dataType(), parent.day(), parent.id(), parent.replaceMissing());
        } catch (SyncBudget.Yield | GoogleHealthProvider.QuotaPause | CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            requireNotCancelled();
            if (exception instanceof ApiException api && (api.getReason() == ApiException.Reason.AUTHORIZATION || api.getReason() == ApiException.Reason.MISSING_SCOPE))
                throw api;
            String safe = safeError(exception);
            var diagnostic = FailureDiagnostic.from(exception);
            log.warn("Google Health historical work failed: stream={}, representation={}, queryDomain={}, type={}, reason={}, detail={}, error={}",
                    policy.dataType(), policy.representation(), policy.windowTimeDomain(),
                    exception.getClass().getSimpleName(), diagnostic.reason(), diagnostic.detail(), safe);
            if (log.isDebugEnabled()) log.debug("Historical work failure: type={}, reason={}",
                    exception.getClass().getSimpleName(), diagnostic.reason(), diagnostic.safeTrace(exception));
            completeBatch(() -> {
                if (exception instanceof ApiException api && api.getReason() == ApiException.Reason.INVALID_PAGE_TOKEN)
                    repository.failWork(parent, 1, null, safe);
                else repository.recordWorkFailure(parent, WorkRetryPolicy.isRecoverable(exception), Instant.now(clock), safe);
                repository.rotate(account(), true, nextRotation);
            });
            errors.add(policy.dataType() + ": " + safe);
            progress.failUnit(safe);
        }
    }

    private void addValidated(DataTypePolicy.Policy policy, Bounds bounds, Map<String, JsonNode> answer, JsonNode json) {
        if (!shouldPersistPoint(policy, json)) return;
        String key, fingerprint;
        Instant queryTime;
        if (policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE) {
            var row = mapper.validateHeartRate(json);
            key = row.identityKey();
            fingerprint = row.fingerprint();
            queryTime = row.sampleTime();
        } else {
            var row = mapper.validatePoint(policy, json, policy.mode() == DataTypePolicy.Mode.DAILY_ROLLUP ? GoogleHealthProvider.ALL_SOURCES : null);
            key = row.identityKey();
            fingerprint = row.fingerprint();
            queryTime = switch (policy.windowTimeDomain()) {
                case DATE -> midnight(row.civilDate());
                case CIVIL -> row.civilStartTime().toInstant(ZoneOffset.UTC);
                case PHYSICAL -> policy.recordType() == DataTypePolicy.RecordType.SESSION_END ? row.physicalEndTime() : row.physicalStartTime();
            };
        }
        queryTime = queryTime.truncatedTo(ChronoUnit.MICROS);
        if (queryTime.isBefore(bounds.start()) || !queryTime.isBefore(bounds.end()))
            throw new SyncValidationException(SyncValidationException.Reason.QUERY_RANGE_MISMATCH);
        JsonNode previous = answer.putIfAbsent(key, json);
        if (previous != null && !StableFingerprint.ofCanonicalJson(previous).equals(fingerprint))
            throw new SyncValidationException(SyncValidationException.Reason.DUPLICATE_QUERY_IDENTITY);
    }

    private HistoricalPage prepareHistoricalPage(DataTypePolicy.Policy policy, Bounds bounds, List<JsonNode> payloads) {
        Map<String, HeartRateSample> samples = new LinkedHashMap<>();
        Map<String, StoredPoint> points = new LinkedHashMap<>();
        Map<String, JsonNode> sources = new HashMap<>();
        Map<String, String> fingerprints = new HashMap<>();
        long bytes = 0;
        for (JsonNode payload : payloads) {
            if (!shouldPersistPoint(policy, payload)) continue;
            String key, fingerprint, resource, raw;
            Instant queryTime;
            if (policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE) {
                HeartRateSample row = mapper.validateHeartRate(payload);
                key = row.identityKey();
                fingerprint = row.fingerprint();
                resource = row.googleResourceName();
                raw = row.rawJson();
                queryTime = row.sampleTime();
                HeartRateSample previous = samples.putIfAbsent(key, row);
                if (previous != null && !previous.fingerprint().equals(fingerprint))
                    throw new SyncValidationException(SyncValidationException.Reason.DUPLICATE_QUERY_IDENTITY);
            } else {
                StoredPoint row = mapper.validatePoint(policy, payload,
                        policy.mode() == DataTypePolicy.Mode.DAILY_ROLLUP ? GoogleHealthProvider.ALL_SOURCES : null);
                key = row.identityKey();
                fingerprint = row.fingerprint();
                resource = row.googleResourceName();
                raw = row.rawJson();
                queryTime = switch (policy.windowTimeDomain()) {
                    case DATE -> midnight(row.civilDate());
                    case CIVIL -> row.civilStartTime().toInstant(ZoneOffset.UTC);
                    case PHYSICAL -> policy.recordType() == DataTypePolicy.RecordType.SESSION_END ? row.physicalEndTime() : row.physicalStartTime();
                };
                StoredPoint previous = points.putIfAbsent(key, row);
                if (previous != null && !previous.fingerprint().equals(fingerprint))
                    throw new SyncValidationException(SyncValidationException.Reason.DUPLICATE_QUERY_IDENTITY);
            }
            queryTime = queryTime.truncatedTo(ChronoUnit.MICROS);
            if (queryTime.isBefore(bounds.start()) || !queryTime.isBefore(bounds.end()))
                throw new SyncValidationException(SyncValidationException.Reason.QUERY_RANGE_MISMATCH);
            sources.put(key, payload.path("dataSource"));
            if (PointMapper.needsQueryFingerprint(policy, resource)) fingerprints.put(key, fingerprint);
            bytes += raw.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > properties.getMaxChunkBytes()) throw new SyncValidationException(SyncValidationException.Reason.HISTORY_BYTE_LIMIT);
            if (samples.size() + points.size() > properties.getMaxChunkRows())
                throw new SyncValidationException(SyncValidationException.Reason.HISTORY_ROW_LIMIT);
        }
        return new HistoricalPage(List.copyOf(samples.values()), List.copyOf(points.values()), sources, fingerprints);
    }

    private long persistHistoricalPage(HistoricalPage page) {
        List<HeartRateSample> samples = page.samples().stream()
                .map(row -> mapper.resolveSource(row, page.sources().get(row.identityKey()))).toList();
        List<StoredPoint> points = page.points().stream()
                .map(row -> mapper.resolveSource(row, page.sources().get(row.identityKey()))).toList();
        return (long) repository.importHistoricalHeartRate(samples) + repository.importHistoricalPoints(points);
    }

    private record HistoricalPage(List<HeartRateSample> samples, List<StoredPoint> points,
                                  Map<String, JsonNode> sources, Map<String, String> fingerprints) {
        int size() {
            return samples.size() + points.size();
        }
    }

    private long persist(DataTypePolicy.Policy policy, Map<String, JsonNode> answer, UUID stamp) {
        if (policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE) {
            List<HeartRateSample> rows = answer.values().stream().map(mapper::mapHeartRate).toList();
            repository.checkHeartRateQueryConflicts(rows, stamp);
            long contentChanges = repository.countHeartRateContentChanges(rows);
            repository.upsertHeartRateSamples(rows, stamp);
            return contentChanges;
        }
        List<StoredPoint> rows = answer.values().stream().map(json -> mapper.mapPoint(policy, json,
                policy.mode() == DataTypePolicy.Mode.DAILY_ROLLUP ? GoogleHealthProvider.ALL_SOURCES : null)).toList();
        var fingerprints = rows.stream().collect(Collectors.toMap(StoredPoint::identityKey, StoredPoint::fingerprint));
        repository.checkQueryConflicts(policy, fingerprints, stamp);
        long contentChanges = repository.countContentChanges(policy, fingerprints);
        repository.upsertDataPoints(rows, stamp);
        return contentChanges;
    }

    private GoogleHealthProvider.Page fetch(DataTypePolicy.Policy policy, Bounds bounds, String token) {
        GoogleHealthProvider.Page page = switch (policy.mode()) {
            case RAW, HEART_RATE_SAMPLE -> apiClient.listRaw(policy, bounds.start(), bounds.end(), token);
            case RECONCILED_INTERVAL -> apiClient.reconcile(policy, bounds.start(), bounds.end(), token);
            case DAILY_ROLLUP -> apiClient.dailyRollUp(policy, bounds.civilStart().toLocalDate(), bounds.civilEnd().toLocalDate(), token);
        };
        fetched += page.points().size();
        return page;
    }

    private void verifyRecent(DataTypePolicy.Policy policy, Bounds bounds) {
        LocalDate liveCutoff = cutoff(properties, Instant.now(clock));
        if (bounds.day().isBefore(liveCutoff) || bounds.day().isBefore(properties.getHistoryStartDate()) || repository.isFinal(account(), policy, bounds.day()))
            throw new CancellationException("Recent ownership ended");
    }

    private void verifyHistory(MirrorRepository.HistoryWork work) {
        if (!work.day().isBefore(cutoff(properties, Instant.now(clock))) || work.day().isBefore(properties.getHistoryStartDate()))
            throw new CancellationException("Historical ownership ended");
    }

    private void publishStatistics() {
        progress.observeStatistics(new GHCycleStatistics(fetched, changed, stamped, deleted,
                completeFastStreams, streamCount, budgetLimited, apiClient.counters()));
    }

    private void requireWorkTime() {
        if (progress.isCancelled()) throw new CancellationException("Sync cancelled");
        SyncBudget.require(properties.getRequestTimeout().plus(COMMIT_HEADROOM));
    }

    private void guard() {
        requireNotCancelled();
        repository.guardSynchronization(connection, generation);
    }

    private void requireNotCancelled() {
        if (progress.isCancelled() || Thread.currentThread().isInterrupted()) throw new CancellationException("Sync cancelled");
    }

    private void requireCommitTime() {
        requireNotCancelled();
        SyncBudget.require(COMMIT_HEADROOM);
    }

    /**
     * Complete an admitted answer or its failure checkpoint without extending request admission.
     */
    private void completeBatch(Runnable action) {
        requireNotCancelled();
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout((int) properties.getDatabaseTransactionTimeout().toSeconds());
        long started = System.nanoTime();
        transaction.executeWithoutResult(status -> {
            guard();
            action.run();
            requireNotCancelled();
        });
        if (!SyncBudget.has(COMMIT_HEADROOM)) {
            budgetLimited = true;
            log.info("Google Health batch committed at scheduling boundary: durationMs={}, databaseTimeoutSeconds={}",
                    (System.nanoTime() - started) / 1_000_000, properties.getDatabaseTransactionTimeout().toSeconds());
        }
    }

    private TransactionTemplate transaction() {
        requireCommitTime();
        long remainingSeconds = SyncBudget.remainingNanos() / Duration.ofSeconds(1).toNanos();
        if (remainingSeconds < 1) throw new SyncBudget.Yield();
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout((int) Math.min(properties.getDatabaseTransactionTimeout().toSeconds(), remainingSeconds));
        return transaction;
    }

    private void guarded(Runnable action) {
        transact(() -> {
            action.run();
            return null;
        });
    }

    private <T> T transact(Supplier<T> action) {
        try {
            return transaction().execute(status -> {
                guard();
                return action.get();
            });
        } catch (TransactionTimedOutException | QueryTimeoutException exception) {
            // The transaction has already rolled back. An exhausted lane is a pause,
            // not a bad answer or a failed range that should use up retry attempts.
            if (!SyncBudget.has(COMMIT_HEADROOM)) throw new SyncBudget.Yield();
            throw exception;
        }
    }

    static boolean shouldPersistPoint(DataTypePolicy.Policy policy, JsonNode point) {
        return policy.mode() != DataTypePolicy.Mode.DAILY_ROLLUP || (!point.path(policy.jsonFieldName()).isMissingNode() && !point.path(policy.jsonFieldName()).isNull());
    }

    private static String safeError(RuntimeException exception) {
        var validation = validationFailure(exception);
        if (validation != null) return "Health data validation failed. Reason: " + validation.reason().name() + ".";
        if (exception instanceof IllegalArgumentException)
            return "Health data validation failed.";
        if (exception instanceof TransactionTimedOutException)
            return "Local database transaction timed out.";
        if (exception instanceof QueryTimeoutException)
            return "Local database work timed out.";
        if (exception instanceof DataAccessException || exception instanceof TransactionException)
            return "Local database work failed.";
        String message = exception instanceof ApiException ? exception.getMessage() : "Health synchronization failed locally.";
        return message == null ? "Google Health work failed" : message.substring(0, Math.min(message.length(), SAFE_ERROR_LIMIT));
    }

    private static SyncValidationException validationFailure(Throwable exception) {
        // Repository exception translation can wrap validation failures in a DataAccessException.
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = exception; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof SyncValidationException validation) return validation;
        }
        return null;
    }

    private record FailureDiagnostic(String reason, String detail) {
        static FailureDiagnostic from(RuntimeException exception) {
            var validation = validationFailure(exception);
            if (validation != null)
                return new FailureDiagnostic(validation.reason().name(), validation.reason().message());
            if (exception instanceof ApiException api)
                return new FailureDiagnostic("API_" + api.getReason().name(), "Google Health API request failed.");
            return new FailureDiagnostic(exception instanceof IllegalArgumentException
                    ? "UNCLASSIFIED_VALIDATION" : "LOCAL_FAILURE", safeError(exception));
        }

        RuntimeException safeTrace(RuntimeException original) {
            // Keep the failure location without raw messages, causes or suppressed provider data.
            var sanitized = new RuntimeException("type=" + original.getClass().getSimpleName()
                                                 + ", reason=" + reason + ", detail=" + detail);
            sanitized.setStackTrace(original.getStackTrace());
            return sanitized;
        }
    }

    private enum Outcome {SUCCESS, WAITING, SKIPPED}

    private record HistorySelection(MirrorRepository.HistoryWork work, LocalDate day) {
    }

    /** Disposable search state for one account, ownership generation and fixed cycle cutoff. */
    private static final class HistoryCursor {
        private LocalDate before;
        private LocalDate candidateDay;
        private UUID selectedWorkId;
        private boolean exhausted;

        private HistoryCursor(LocalDate before) {
            this.before = before;
        }
    }

    public record SyncResult(boolean success, List<String> errors, boolean continueImmediately,
                             GHCycleStatistics statistics) {
        public SyncResult(boolean success, List<String> errors) {
            this(success, errors, false, null);
        }

        public SyncResult(boolean success, List<String> errors, boolean ignoredContinuation) {
            this(success, errors, false, null);
        }
    }
}
