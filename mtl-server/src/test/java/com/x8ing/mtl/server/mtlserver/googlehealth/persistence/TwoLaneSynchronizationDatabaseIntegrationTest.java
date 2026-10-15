package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiClient;
import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiException;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.exercise.ExerciseRouteService;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.*;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.indexer.gps.live-watch-enabled=false", "mtl.indexer.media.live-watch-enabled=false", "mtl.media-correlation.initial-delay=PT1H"})
class TwoLaneSynchronizationDatabaseIntegrationTest {
    @Autowired
    MirrorRepository repository;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager manager;
    @Autowired
    ConnectorProperties connectorProperties;
    @MockitoBean
    GPXDirectoryWatcherService watcher;
    @MockitoBean
    MediaIndexerService media;
    ObjectMapper json = new ObjectMapper();
    PointMapper mapper;
    TransactionTemplate transaction;
    LocalDate day = LocalDate.of(2026, 9, 29);
    String account = "synthetic-two-lane";

    @BeforeEach
    void setup() {
        repository.configureWorkLimits(connectorProperties);
        repository.disconnect();
        repository.deleteMirrorRows();
        repository.saveConnected(account, Set.of(OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS, OAuthScopes.ACTIVITY_AND_FITNESS), "synthetic", null);
        repository.synchronizationGeneration(account);
        mapper = new PointMapper(new DataSourceResolver(repository));
        mapper.useAccount(account);
        transaction = new TransactionTemplate(manager);
    }

    @AfterEach
    void clean() {
        repository.disconnect();
        repository.deleteMirrorRows();
        jdbc.update("UPDATE google_health.connection SET google_user_id=NULL WHERE id=1");
    }

    DataTypePolicy.Policy policy(String type, DataTypePolicy.Mode mode) {
        return DataTypePolicy.all().stream().filter(p -> p.dataType().equals(type) && p.mode() == mode).findFirst().orElseThrow();
    }

    HeartRateSample sample(String name, Instant time, int bpm) {
        var node = json.createObjectNode();
        if (name != null) node.put("name", name);
        var heart = node.putObject("heartRate");
        heart.put("beatsPerMinute", bpm);
        heart.putObject("sampleTime").put("physicalTime", time.toString());
        return mapper.mapHeartRate(node);
    }

    MirrorRepository.HistoryWork work(DataTypePolicy.Policy policy) {
        var bounds = new SyncWindowPolicy.Bounds(policy.windowTimeDomain(), SyncWindowPolicy.midnight(day), SyncWindowPolicy.midnight(day.plusDays(1)));
        return transaction.execute(status -> repository.createWork(account, policy, day, "HISTORY", SyncWindowPolicy.cells(policy, bounds)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 300})
    void slowDatabaseCommitRollsBackAtOneSecondAndSucceedsWithDefaultLimit(int timeoutSeconds) {
        repository.saveConnected(account, Set.of(OAuthScopes.SLEEP), "synthetic", null);
        var properties = new ConnectorProperties();
        properties.setClientId("synthetic");
        properties.setClientSecret("synthetic");
        if (timeoutSeconds == 1) properties.setDatabaseTransactionTimeout(Duration.ofSeconds(1));
        properties.setHistoryStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(7));
        Instant end = Instant.now().minusSeconds(60), start = end.minusSeconds(3600);
        var point = json.createObjectNode();
        point.put("name", "users/synthetic/dataTypes/sleep/dataPoints/slow-storage");
        var interval = point.putObject("sleep").putObject("interval");
        interval.put("startTime", start.toString());
        interval.put("endTime", end.toString());
        var client = Mockito.mock(ApiClient.class);
        Mockito.when(client.listRaw(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenAnswer(call -> new ApiClient.Page(!end.isBefore(call.getArgument(1, Instant.class)) && end.isBefore(call.getArgument(2, Instant.class)) ? List.of(point) : List.of(), null));
        jdbc.execute("CREATE FUNCTION google_health.test_slow_commit() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN PERFORM pg_sleep(1.3); RETURN NEW; END'");
        jdbc.execute("CREATE TRIGGER test_slow_commit BEFORE INSERT ON google_health.data_point FOR EACH ROW EXECUTE FUNCTION google_health.test_slow_commit()");
        try {
            var service = new SyncService(properties, repository, client, mapper, Mockito.mock(ExerciseRouteService.class), manager);
            var result = service.run(SyncCoordinator.JobKind.REGULAR, Mockito.mock(SyncCoordinator.Progress.class));
            assertThat(result.success()).isEqualTo(timeoutSeconds > 1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.data_point WHERE data_type='sleep'", Integer.class)).isEqualTo(timeoutSeconds > 1 ? 1 : 0);
            if (timeoutSeconds > 1) {
                assertThat(repository.recentPass(account, policy("sleep", DataTypePolicy.Mode.RAW)).orElseThrow().state()).isEqualTo("COMPLETED");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.recent_failure", Integer.class)).isZero();
            } else {
                assertThat(result.errors()).allSatisfy(error -> assertThat(error).contains("Local database").doesNotContain("Google Health did not return"));
                assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.recent_failure", Integer.class)).isPositive();
            }
        } finally {
            jdbc.execute("DROP TRIGGER test_slow_commit ON google_health.data_point");
            jdbc.execute("DROP FUNCTION google_health.test_slow_commit()");
        }
    }

    @Test
    void timeScopedHeartLookupsPreserveNamedCorrectionsAndCanonicalNamelessIdentity() {
        Instant start = SyncWindowPolicy.midnight(day).plusNanos(123456789);
        var named = sample("users/synthetic/dataTypes/heart-rate/dataPoints/one", start.plusSeconds(1), 61);
        var unnamed = sample(null, start.plusSeconds(2), 61);
        var collection = sample("users/synthetic/dataTypes/heart-rate/dataPoints", start.plusSeconds(3), 61);
        UUID stamp = UUID.randomUUID();
        var original = List.of(named, unnamed, collection);
        repository.upsertHeartRateSamples(original, stamp);
        assertThat(repository.countHeartRateContentChanges(original)).isZero();
        repository.checkHeartRateQueryConflicts(original, stamp);

        var moved = sample(named.googleResourceName(), start.plusSeconds(3601), 62);
        var differentUnnamed = sample(null, unnamed.sampleTime(), 62);
        var differentCollection = sample(collection.googleResourceName(), collection.sampleTime(), 62);
        var changed = List.of(moved, differentUnnamed, differentCollection);
        assertThat(repository.countHeartRateContentChanges(changed)).isEqualTo(3);
        assertThatThrownBy(() -> repository.checkHeartRateQueryConflicts(List.of(moved), stamp))
                .hasMessageContaining("Conflicting duplicate");
        repository.checkHeartRateQueryConflicts(List.of(differentUnnamed, differentCollection), stamp);
        repository.upsertHeartRateSamples(changed, null);
        assertThat(repository.countHeartRateContentChanges(changed)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample WHERE google_resource_name=?",
                Integer.class, named.googleResourceName())).isEqualTo(1);
    }

    @Test
    void namedHeartRateCorrectionsMoveOneRowAndRetainHistoryStamp() {
        var start = SyncWindowPolicy.midnight(day);
        var first = sample("users/synthetic/dataTypes/heart-rate/dataPoints/one", start.plusSeconds(1), 61);
        UUID stamp = UUID.randomUUID();
        repository.upsertHeartRateSamples(List.of(first), stamp);
        long id = jdbc.queryForObject("SELECT id FROM google_health.heart_rate_sample", Long.class);
        var corrected = sample(first.googleResourceName(), start.plusSeconds(3601), 62);
        repository.upsertHeartRateSamples(List.of(corrected), null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT id FROM google_health.heart_rate_sample", Long.class)).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT last_seen_run FROM google_health.heart_rate_sample", UUID.class)).isEqualTo(stamp);
        assertThat(jdbc.queryForObject("SELECT beats_per_minute FROM google_health.heart_rate_sample", Integer.class)).isEqualTo(62);
        repository.upsertHeartRateSamples(List.of(corrected), null);
        assertThat(repository.countContentChanges(policy("heart-rate", DataTypePolicy.Mode.HEART_RATE_SAMPLE), Map.of(corrected.identityKey(), corrected.fingerprint()))).isZero();
    }

    @Test
    void namelessDifferentObservationsAtOneInstantSurviveAndReplacementRemovesOldKeys() {
        var start = SyncWindowPolicy.midnight(day);
        var first = sample(null, start.plusSeconds(1), 61);
        var second = sample(null, start.plusSeconds(1), 62);
        assertThat(first.identityKey()).isNotEqualTo(second.identityKey());
        repository.upsertHeartRateSamples(List.of(first, second), null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample", Integer.class)).isEqualTo(2);
        var policy = policy("heart-rate", DataTypePolicy.Mode.HEART_RATE_SAMPLE);
        repository.replaceRecent(policy, start, start.plusSeconds(3600), Set.of(second.identityKey()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample", Integer.class)).isEqualTo(1);
    }

    @Test
    void conflictingLaterPageIsRejectedWhileAnotherTraversalMayCorrectTheRow() {
        var policy = policy("heart-rate", DataTypePolicy.Mode.HEART_RATE_SAMPLE);
        UUID stamp = UUID.randomUUID();
        var first = sample("users/synthetic/dataTypes/heart-rate/dataPoints/one", SyncWindowPolicy.midnight(day).plusSeconds(1), 61);
        repository.upsertHeartRateSamples(List.of(first), stamp);
        var corrected = sample(first.googleResourceName(), first.sampleTime(), 62);
        assertThatThrownBy(() -> repository.checkQueryConflicts(policy, Map.of(corrected.identityKey(), corrected.fingerprint()), stamp)).hasMessageContaining("Conflicting duplicate");
        repository.checkQueryConflicts(policy, Map.of(corrected.identityKey(), corrected.fingerprint()), UUID.randomUUID());
        repository.checkQueryConflicts(policy, Map.of(first.identityKey(), first.fingerprint()), stamp);
    }

    @Test
    void eachHistoricalChildSweepsOnlyItsOwnLatestStampIncludingNull() {
        var policy = policy("heart-rate", DataTypePolicy.Mode.HEART_RATE_SAMPLE);
        var work = work(policy);
        var children = repository.children(work.id());
        assertThat(children).hasSize(4);
        for (var child : children)
            transaction.executeWithoutResult(status -> {
                repository.guardWork(work, child);
                repository.upsertHeartRateSamples(List.of(sample(null, child.start().plusSeconds(1), 61)), child.traversal());
                repository.checkpointChild(child, null, 1, 1, 1);
            });
        repository.upsertHeartRateSamples(List.of(sample(null, children.getFirst().start().plusSeconds(2), 71)), null);
        transaction.executeWithoutResult(status -> {
            for (var child : repository.children(work.id())) repository.deleteMissingHeartRateSamples(child.start(), child.end(), child.traversal());
            repository.completeWork(work, true, 1);
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample", Integer.class)).isEqualTo(4);
        assertThat(repository.isFinal(account, policy, day)).isTrue();
        assertThat(repository.newestUnmarked(account, policy, day, day.plusDays(1))).isEmpty();
    }

    @Test
    void incompleteOrRevokedChildrenCannotFinalizeOrCommit() {
        var policy = policy("heart-rate", DataTypePolicy.Mode.HEART_RATE_SAMPLE);
        var work = work(policy);
        var child = repository.children(work.id()).getFirst();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> repository.completeWork(work, true, 0))).hasMessageContaining("Incomplete historical children");
        transaction.executeWithoutResult(status -> repository.failWork(work, 1, Instant.now(), "Synthetic failure"));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> repository.guardWork(work, child))).hasMessageContaining("Historical owner was revoked");
        assertThat(repository.isFinal(account, policy, day)).isFalse();
        assertThat(repository.newestUnmarked(account, policy, day.minusDays(1), day.plusDays(1))).contains(day.minusDays(1));
    }

    @Test
    void finalizationRollbackLeavesRowsAndNoFinalMark() {
        var policy = policy("heart-rate", DataTypePolicy.Mode.HEART_RATE_SAMPLE);
        var work = work(policy);
        for (var child : repository.children(work.id())) transaction.executeWithoutResult(status -> repository.checkpointChild(child, null, 0, 0, 1));
        repository.upsertHeartRateSamples(List.of(sample(null, SyncWindowPolicy.midnight(day).plusSeconds(1), 61)), null);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            for (var child : repository.children(work.id())) repository.deleteMissingHeartRateSamples(child.start(), child.end(), child.traversal());
            repository.completeWork(work, true, 1);
            throw new IllegalStateException("Synthetic rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample", Integer.class)).isEqualTo(1);
        assertThat(repository.isFinal(account, policy, day)).isFalse();
    }

    @Test
    void emptyFinalsProtectAccountIdentityAndReloadSupersedesWithoutDeletingRecords() {
        var policy = policy("steps", DataTypePolicy.Mode.DAILY_ROLLUP);
        var work = work(policy);
        for (var child : repository.children(work.id())) transaction.executeWithoutResult(status -> repository.checkpointChild(child, null, 0, 0, 1));
        transaction.executeWithoutResult(status -> repository.completeWork(work, true, 0));
        assertThat(repository.isFinal(account, policy, day)).isTrue();
        assertThatThrownBy(() -> repository.saveConnected("different-synthetic", Set.of(OAuthScopes.ACTIVITY_AND_FITNESS), "synthetic", null)).isInstanceOf(GoogleHealthAccountMismatchException.class);
        repository.reloadDays(account, List.of(policy), day, day.plusDays(1), day.plusDays(2), false);
        assertThat(repository.isFinal(account, policy, day)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.history_audit WHERE outcome='SUPERSEDED_BY_RELOAD'", Integer.class)).isEqualTo(1);
        var replacement = work(policy);
        assertThat(replacement.replaceMissing()).isTrue();
    }

    @Test
    void explicitRecentReloadRetainsReplacementIntentWhenItHandsOverToHistory() {
        var policy = policy("heart-rate", DataTypePolicy.Mode.HEART_RATE_SAMPLE);
        repository.reloadDays(account, List.of(policy), day, day.plusDays(1), day, false);
        assertThat(repository.isFinal(account, policy, day)).isFalse();
        assertThat(work(policy).replaceMissing()).isTrue();
    }

    @Test
    void revokedUnmarkedDayGetsNewOwnershipAndFrozenBounds() {
        var policy = policy("heart-rate", DataTypePolicy.Mode.HEART_RATE_SAMPLE);
        var previous = work(policy);
        var previousChild = repository.children(previous.id()).getFirst();
        transaction.executeWithoutResult(status -> repository.revokeWork(previous));
        var replacement = work(policy);
        assertThat(replacement.id()).isNotEqualTo(previous.id());
        assertThat(repository.children(previous.id())).isEmpty();
        assertThat(replacement.replaceMissing()).isFalse();
        assertThat(repository.children(replacement.id()).getFirst().start()).isEqualTo(previousChild.start());
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> repository.guardWork(previous, previousChild)))
                .hasRootCauseInstanceOf(java.util.concurrent.CancellationException.class);
    }

    @Test
    void removedExerciseKeepsPublishedRouteAndReappearanceReclaimsOwnership() {
        var policy = policy("exercise", DataTypePolicy.Mode.RAW);
        String value = """
                {"name":"users/synthetic/dataTypes/exercise/dataPoints/one","exercise":{"interval":{
                "startTime":"2026-09-29T10:00:00Z","endTime":"2026-09-29T11:00:00Z",
                "civilStartTime":{"date":{"year":2026,"month":9,"day":29},"time":{"hours":10}},
                "civilEndTime":{"date":{"year":2026,"month":9,"day":29},"time":{"hours":11}}}}}
                """;
        var point = mapper.mapPoint(policy, json.readTree(value), null);
        repository.upsertDataPoints(List.of(point), null);
        var owner = repository.findDueExerciseRouteRetries(10).getFirst();
        repository.markExerciseRoutePublished(owner.id(), "google-health/retained.tcx", point.fingerprint(), Instant.now(), 100);
        repository.replaceRecent(policy, SyncWindowPolicy.midnight(day), SyncWindowPolicy.midnight(day.plusDays(1)), Set.of());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.data_point", Integer.class)).isZero();
        assertThat(repository.findAllManagedRoutePaths()).containsExactly("google-health/retained.tcx");
        assertThat(repository.routeSummary().retainedCount()).isEqualTo(1);
        repository.upsertDataPoints(List.of(point), null);
        assertThat(jdbc.queryForObject("SELECT id FROM google_health.route_ownership", Long.class)).isEqualTo(owner.id());
        assertThat(repository.routeSummary().retainedCount()).isZero();
    }

    @Test
    void partialRecentSuccessCannotClearFailureOutsideItsAnswerAndHandoffEndsRecentRetries() {
        var policy = policy("heart-rate", DataTypePolicy.Mode.HEART_RATE_SAMPLE);
        Instant start = SyncWindowPolicy.midnight(day), end = start.plusSeconds(21600);
        repository.failRecent(account, policy, "FAST", start, end, start.plusSeconds(3600), end, 3, Instant.now().plusSeconds(60), "Synthetic failure");
        repository.clearCoveredFailures(account, policy, start.plusSeconds(3600), end);
        assertThat(repository.recentFailureState(account, policy, start, end, Instant.now())).isEqualTo("WAITING");
        repository.handOverFailures(account, end);
        assertThat(repository.recentFailureState(account, policy, start, end, Instant.now())).isNull();
    }

    @Test
    void emptyBatchKeepsDailyAuditsAndExactDailyDeletionCounts() {
        var policy = policy("heart-rate", DataTypePolicy.Mode.HEART_RATE_SAMPLE);
        var bounds = new SyncWindowPolicy.Bounds(policy.windowTimeDomain(), SyncWindowPolicy.midnight(day.minusDays(3)), SyncWindowPolicy.midnight(day.plusDays(1)));
        var work = transaction.execute(status -> repository.createWork(account, policy, day, "HISTORY", List.of(bounds)));
        var child = repository.children(work.id()).getFirst();
        for (int i = 0; i < 3; i++) repository.upsertHeartRateSamples(List.of(sample(null, bounds.start().plus(Duration.ofDays(i)).plusSeconds(1), 61)), null);
        transaction.executeWithoutResult(status -> repository.checkpointChild(child, null, 0, 0, 1));
        transaction.executeWithoutResult(status -> {
            var counts = repository.missingCountsByDay(policy, child);
            int removed = repository.deleteMissingHeartRateSamples(child.start(), child.end(), child.traversal());
            repository.completeWork(work, true, removed, counts);
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.history_audit WHERE outcome='EMPTY'", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT sum(deleted) FROM google_health.history_audit", Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT deleted FROM google_health.history_audit WHERE native_day=?", Long.class, day)).isZero();
    }

    @Test
    void rangeRetryRebuildsOutsideParkedPartsUnderNewOwners() {
        var policy = policy("steps", DataTypePolicy.Mode.DAILY_ROLLUP);
        var bounds = new SyncWindowPolicy.Bounds(policy.windowTimeDomain(), SyncWindowPolicy.midnight(day.minusDays(3)), SyncWindowPolicy.midnight(day.plusDays(1)));
        var work = transaction.execute(status -> repository.createWork(account, policy, day, "HISTORY", List.of(bounds)));
        transaction.executeWithoutResult(status -> repository.failWork(work, 1, Instant.now(), "Synthetic parked batch"));
        repository.reloadDays(account, List.of(policy), day.minusDays(2), day.minusDays(1), day.plusDays(2), true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.history_work WHERE state='PARKED'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT id FROM google_health.history_work", UUID.class)).doesNotContain(work.id());
        assertThat(repository.newestUnmarked(account, policy, day.minusDays(3), day.plusDays(1))).contains(day.minusDays(2));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void recentFailuresKeepTemporaryWorkEligibleAndStopInvalidWork(boolean recoverable) {
        var policy = policy("sleep", DataTypePolicy.Mode.RAW);
        Instant start = SyncWindowPolicy.midnight(day), end = start.plus(Duration.ofDays(1));
        Instant now = Instant.now();
        for (int attempt = 1; attempt <= 4; attempt++) {
            Instant failureAt = now;
            transaction.executeWithoutResult(status -> repository.recordRecentFailure(account, policy, "FAST", start, end,
                    start, end, recoverable, failureAt, "Synthetic failure"));
            assertThat(repository.recentFailureState(account, policy, start, end, now)).isEqualTo(recoverable || attempt < 4 ? "WAITING" : "SKIPPED");
            if (recoverable || attempt < 4) {
                var retry = jdbc.queryForObject("SELECT next_retry_at FROM google_health.recent_failure", java.sql.Timestamp.class).toInstant();
                assertThat(retry).isAfter(now);
                if (attempt == 4) {
                    Duration delay = connectorProperties.getSyncInterval().multipliedBy(2);
                    assertThat(Duration.between(now, retry)).isBetween(delay, delay.plus(delay.dividedBy(5)));
                }
                now = retry;
            }
        }
        if (recoverable) {
            var restarted = new MirrorRepository(jdbc, json);
            assertThat(restarted.recentFailureState(account, policy, start, end, now)).isNull();
            Instant failureAt = now;
            restarted.recordRecentFailure(account, policy, "FAST", start, end, start, end, true, failureAt, "Synthetic fifth failure");
            assertThat(restarted.recentFailureState(account, policy, start, end, now)).isEqualTo("WAITING");
            restarted.clearCoveredFailures(account, policy, start, end);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.recent_failure", Integer.class)).isZero();
        } else {
            assertThat(jdbc.queryForObject("SELECT next_retry_at FROM google_health.recent_failure", java.sql.Timestamp.class)).isNull();
        }
    }

    @Test
    void historicalTemporaryFailuresResumeCommittedPagesAfterRestartAndEventuallyFinalize() {
        var policy = policy("sleep", DataTypePolicy.Mode.RAW);
        var initial = transaction.execute(status -> repository.createWork(account, policy, day, "HISTORY",
                List.of(new SyncWindowPolicy.Bounds(policy.windowTimeDomain(), SyncWindowPolicy.midnight(day), SyncWindowPolicy.midnight(day.plusDays(1))))));
        var child = repository.children(initial.id()).getFirst();
        transaction.executeWithoutResult(status -> repository.checkpointHistoricalChild(child, "synthetic-next", 1, 1, 1, 1, Map.of()));
        var owner = initial;
        Instant now = Instant.now();
        for (int attempt = 1; attempt <= 5; attempt++) {
            var active = owner;
            Instant failureAt = now;
            transaction.executeWithoutResult(status -> repository.recordWorkFailure(active, true, failureAt, "Synthetic provider unavailable"));
            assertThat(repository.dueWork(account, policy, "HISTORY", now)).isEmpty();
            var retry = jdbc.queryForObject("SELECT next_retry_at FROM google_health.history_work WHERE id=?", java.sql.Timestamp.class, initial.id()).toInstant();
            var restarted = new MirrorRepository(jdbc, json);
            owner = restarted.dueWork(account, policy, "HISTORY", retry).orElseThrow();
            assertThat(owner.state()).isEqualTo("WAITING");
            assertThat(owner.attempts()).isEqualTo(attempt);
            assertThat(restarted.children(owner.id()).getFirst().token()).isEqualTo("synthetic-next");
            var waiting = owner;
            transaction.executeWithoutResult(status -> restarted.activateWork(waiting));
            now = retry;
        }
        var finalOwner = owner;
        transaction.executeWithoutResult(status -> {
            repository.checkpointHistoricalChild(child, null, 0, 0, 0, 2, Map.of());
            repository.completeHistoricalImport(finalOwner);
        });
        assertThat(repository.isFinal(account, policy, day)).isTrue();
        assertThat(repository.dueWork(account, policy, "HISTORY", Instant.now().plus(Duration.ofDays(1)))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT safe_error FROM google_health.history_work WHERE id=?", String.class, initial.id())).isNull();
    }

    @Test
    void deferredReloadDoesNotCountAnotherFailureWhileItsCellIsCoolingDown() {
        var policy = policy("sleep", DataTypePolicy.Mode.RAW);
        Instant start = SyncWindowPolicy.midnight(day), end = start.plus(Duration.ofDays(1));
        var work = transaction.execute(status -> repository.createWork(account, policy, day, "RELOAD", List.of(new SyncWindowPolicy.Bounds(policy.windowTimeDomain(), start, end))));
        repository.recordRecentFailure(account, policy, "RELOAD", start, end, start, end, true, Instant.now(), "Synthetic timeout");
        transaction.executeWithoutResult(status -> repository.deferRecentReload(work, account, policy, start, end));
        assertThat(jdbc.queryForObject("SELECT attempts FROM google_health.history_work WHERE id=?", Integer.class, work.id())).isEqualTo(1);
        assertThat(repository.dueWork(account, policy, "RELOAD", Instant.now())).isEmpty();
        var retry = jdbc.queryForObject("SELECT next_retry_at FROM google_health.recent_failure", java.sql.Timestamp.class).toInstant();
        var waiting = repository.dueWork(account, policy, "RELOAD", retry).orElseThrow();
        transaction.executeWithoutResult(status -> {
            repository.activateWork(waiting);
            repository.deferRecentReload(waiting, account, policy, start, end);
        });
        assertThat(jdbc.queryForObject("SELECT attempts FROM google_health.history_work WHERE id=?", Integer.class, work.id())).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void routeFailuresKeepTemporaryExportsEligibleBeyondFourAttempts(boolean recoverable) {
        jdbc.update("INSERT INTO google_health.route_ownership(account_id,identity_key,calendar_policy,native_day,present,google_resource_name,fingerprint,raw_json) VALUES(?,?,?,?,TRUE,?,'synthetic','{}'::jsonb)",
                account, "synthetic-route", SyncWindowPolicy.CALENDAR_POLICY, day, "users/synthetic/dataTypes/exercise/dataPoints/retry");
        long id = jdbc.queryForObject("SELECT id FROM google_health.route_ownership", Long.class);
        for (int attempt = 1; attempt <= 4; attempt++) repository.recordExerciseRouteFailure(id, recoverable, Instant.now(), "Synthetic export failure");
        if (recoverable) {
            assertThat(repository.findDueExerciseRouteRetries(10)).isEmpty();
            jdbc.update("UPDATE google_health.route_ownership SET route_next_retry_at=now()-interval '1 minute'");
            assertThat(new MirrorRepository(jdbc, json).findDueExerciseRouteRetries(10)).hasSize(1);
            repository.recordExerciseRouteFailure(id, true, Instant.now(), "Synthetic fifth failure");
            assertThat(repository.routeSummary().nextRetryAt()).isAfter(Instant.now());
            repository.markExerciseRouteState(id, ExercisePoint.RouteState.NO_ROUTE, null);
            assertThat(repository.routeSummary().failedCount()).isZero();
        } else {
            assertThat(repository.findDueExerciseRouteRetries(10)).isEmpty();
            assertThat(repository.findExerciseRouteCandidates(SyncWindowPolicy.midnight(day), SyncWindowPolicy.midnight(day.plusDays(1)))).isEmpty();
            assertThat(repository.routeSummary().nextRetryAt()).isNull();
            assertThat(repository.routeSummary().failedCount()).isEqualTo(1);
        }
    }

    @Test
    void successfulRoutePublicationStartsTheNextFailureWithAFreshAttemptCount() {
        jdbc.update("INSERT INTO google_health.route_ownership(account_id,identity_key,calendar_policy,native_day,present,google_resource_name,fingerprint,raw_json) VALUES(?,?,?,?,TRUE,?,'synthetic','{}'::jsonb)",
                account, "synthetic-route", SyncWindowPolicy.CALENDAR_POLICY, day, "users/synthetic/dataTypes/exercise/dataPoints/retry");
        long id = jdbc.queryForObject("SELECT id FROM google_health.route_ownership", Long.class);
        for (int attempt = 1; attempt <= 4; attempt++) repository.recordExerciseRouteFailure(id, true, Instant.now(), "Synthetic export failure");
        repository.markExerciseRoutePublished(id, "google-health/synthetic.tcx", "synthetic", Instant.now(), 100);
        assertThat(jdbc.queryForObject("SELECT route_attempts FROM google_health.route_ownership WHERE id=?", Integer.class, id)).isZero();
        repository.recordExerciseRouteFailure(id, false, Instant.now(), "Synthetic validation failure");
        assertThat(jdbc.queryForObject("SELECT route_attempts FROM google_health.route_ownership WHERE id=?", Integer.class, id)).isEqualTo(1);
        assertThat(repository.routeSummary().nextRetryAt()).isAfter(Instant.now());
    }

    @Test
    void removedRouteOwnershipDoesNotAbortFailureRecording() {
        repository.recordExerciseRouteFailure(Long.MAX_VALUE, true, Instant.now(), "Synthetic export failure");
        assertThat(repository.routeSummary().failedCount()).isZero();
    }

    @Test
    void expiredParentFailureDoesNotSkipAFirstFailureInItsNewSubdivision() {
        repository.saveConnected(account, Set.of(OAuthScopes.SLEEP), "synthetic", null);
        Instant now = Instant.parse("2045-09-01T12:00:00Z");
        var properties = new ConnectorProperties();
        properties.setClientId("synthetic");
        properties.setClientSecret("synthetic");
        properties.setHistoryStartDate(SyncWindowPolicy.cutoff(properties, now));
        var failing = policy("sleep", DataTypePolicy.Mode.RAW);
        Instant start = SyncWindowPolicy.midnight(LocalDate.of(2045, 8, 29));
        Instant end = start.plus(Duration.ofDays(1));
        Instant middle = start.plus(Duration.ofHours(12));
        UUID passId = UUID.randomUUID();
        repository.startRecentPass(account, failing, passId, now, start, end);
        for (int attempt = 1; attempt <= properties.getWorkFailureLimit(); attempt++) {
            repository.recordRecentFailure(account, failing, "FULL", start, end, start, end,
                    true, now.minus(Duration.ofDays(1)), "Synthetic previous parent failure");
        }
        Instant parentRetry = jdbc.queryForObject("SELECT next_retry_at FROM google_health.recent_failure", java.sql.Timestamp.class).toInstant();
        assertThat(parentRetry).isBefore(now);
        assertThat(repository.recentFailureSlowRetry(account, failing, start, end, parentRetry.minusNanos(1_000))).isTrue();
        assertThat(repository.recentFailureSlowRetry(account, failing, start, end, parentRetry)).isFalse();
        assertThat(repository.recentFailureState(account, failing, start, end, now)).isNull();

        var childUnavailable = new AtomicBoolean(true);
        var purpose = new AtomicReference<String>();
        var client = Mockito.mock(ApiClient.class);
        Mockito.doAnswer(call -> {
            purpose.set(call.getArgument(0));
            return null;
        }).when(client).purpose(ArgumentMatchers.anyString());
        Mockito.when(client.listRaw(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    if ("RECENT_FULL".equals(purpose.get()) && failing.equals(call.getArgument(0))) {
                        if (start.equals(call.getArgument(1)) && end.equals(call.getArgument(2))) {
                            return new ApiClient.Page(List.of(), "synthetic-split-token");
                        }
                        if (middle.equals(call.getArgument(1)) && end.equals(call.getArgument(2)) && childUnavailable.get()) {
                            throw new ApiException("Synthetic first child failure", 503, true, ApiException.Reason.SERVER);
                        }
                    }
                    return new ApiClient.Page(List.of(), null);
                });
        var routes = Mockito.mock(ExerciseRouteService.class);
        var progress = Mockito.mock(SyncCoordinator.Progress.class);
        var service = new SyncService(properties, repository, client, mapper, routes, manager, Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isFalse();

        var waiting = repository.recentPass(account, failing).orElseThrow();
        assertThat(waiting.id()).isEqualTo(passId);
        assertThat(waiting.state()).isEqualTo("ACTIVE");
        assertThat(waiting.nextEnd()).isEqualTo(end);
        assertThat(waiting.skipped()).isZero();
        assertThat(waiting.nextDueAt()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.recent_failure", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT attempts FROM google_health.recent_failure WHERE cell_start=?", Integer.class,
                java.sql.Timestamp.from(start))).isEqualTo(properties.getWorkFailureLimit());
        assertThat(jdbc.queryForObject("SELECT attempts FROM google_health.recent_failure WHERE cell_start=?", Integer.class,
                java.sql.Timestamp.from(middle))).isEqualTo(1);
        Instant childRetry = jdbc.queryForObject("SELECT next_retry_at FROM google_health.recent_failure WHERE cell_start=?", java.sql.Timestamp.class,
                java.sql.Timestamp.from(middle)).toInstant();
        assertThat(childRetry).isEqualTo(now.plus(properties.getSyncInterval()));
        assertThat(repository.recentFailureSlowRetry(account, failing, start, end, now)).isFalse();

        childUnavailable.set(false);
        assertThat(new SyncService(properties, repository, client, mapper, routes, manager, Clock.fixed(childRetry, ZoneOffset.UTC))
                .run(SyncCoordinator.JobKind.REGULAR, progress).success()).isTrue();
        var recovered = repository.recentPass(account, failing).orElseThrow();
        assertThat(recovered.id()).isEqualTo(passId);
        assertThat(recovered.state()).isEqualTo("COMPLETED");
        assertThat(recovered.skipped()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.recent_failure", Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void slowRecentFailureLetsTheSharedPassFinishAndRecoversInTheNextFullPass(boolean splitRecovery) {
        repository.saveConnected(account, Set.of(OAuthScopes.SLEEP, OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS), "synthetic", null);
        Instant initial = Instant.parse("2026-10-11T12:00:00Z");
        var time = new AtomicReference<>(initial);
        Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return time.get();
            }
        };
        var properties = new ConnectorProperties();
        properties.setClientId("synthetic");
        properties.setClientSecret("synthetic");
        properties.setHistoryStartDate(SyncWindowPolicy.cutoff(properties, initial));
        var failing = policy("sleep", DataTypePolicy.Mode.RAW);
        var healthy = policy("heart-rate", DataTypePolicy.Mode.HEART_RATE_SAMPLE);
        Instant failureStart = SyncWindowPolicy.midnight(LocalDate.of(2026, 10, 8));
        Instant failureEnd = failureStart.plus(Duration.ofDays(1));
        var unavailable = new AtomicBoolean(true);
        var purpose = new AtomicReference<String>();
        var client = Mockito.mock(ApiClient.class);
        Mockito.doAnswer(call -> {
            purpose.set(call.getArgument(0));
            return null;
        }).when(client).purpose(ArgumentMatchers.anyString());
        Mockito.when(client.listRaw(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    if (failing.equals(call.getArgument(0)) && "RECENT_FULL".equals(purpose.get())
                        && failureStart.equals(call.getArgument(1)) && failureEnd.equals(call.getArgument(2))) {
                        if (unavailable.get()) throw new ApiException("Synthetic unavailable range", 503, true, ApiException.Reason.SERVER);
                        if (splitRecovery) return new ApiClient.Page(List.of(), "synthetic-next");
                    }
                    return new ApiClient.Page(List.of(), null);
                });
        Mockito.when(client.dailyRollUp(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new ApiClient.Page(List.of(), null));
        Mockito.when(client.reconcile(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new ApiClient.Page(List.of(), null));
        var routes = Mockito.mock(ExerciseRouteService.class);
        var progress = Mockito.mock(SyncCoordinator.Progress.class);
        UUID firstPass = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            var service = new SyncService(properties, repository, client, mapper, routes, manager, clock);
            assertThat(service.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isFalse();
            var saved = repository.recentPass(account, failing).orElseThrow();
            if (firstPass == null) firstPass = saved.id();
            assertThat(saved.id()).isEqualTo(firstPass);
            assertThat(jdbc.queryForObject("SELECT attempts FROM google_health.recent_failure WHERE data_type='sleep'", Integer.class)).isEqualTo(attempt);
            assertThat(repository.recentFailureState(account, failing, failureStart, failureEnd, time.get())).isEqualTo("WAITING");
            assertThat(repository.recentPass(account, healthy).orElseThrow().state()).isEqualTo("COMPLETED");
            if (attempt < 4) {
                assertThat(saved.state()).isEqualTo("ACTIVE");
                assertThat(saved.nextEnd()).isEqualTo(failureEnd);
                assertThat(repository.recentPass(account, healthy).orElseThrow().nextDueAt()).isNull();
            } else {
                assertThat(saved.state()).isEqualTo("COMPLETED_WITH_ERRORS");
                assertThat(saved.skipped()).isEqualTo(1);
                assertThat(saved.nextDueAt()).isEqualTo(time.get().plus(properties.getRecentFullInterval()));
                assertThat(repository.recentPass(account, healthy).orElseThrow().nextDueAt()).isEqualTo(saved.nextDueAt());
            }
            time.set(jdbc.queryForObject("SELECT next_retry_at FROM google_health.recent_failure WHERE data_type='sleep'", java.sql.Timestamp.class).toInstant());
        }
        // A waiting failure remains durable after completion; an early cycle cannot start a new epoch.
        unavailable.set(false);
        new SyncService(properties, repository, client, mapper, routes, manager, clock).run(SyncCoordinator.JobKind.REGULAR, progress);
        assertThat(repository.recentPass(account, failing).orElseThrow().id()).isEqualTo(firstPass);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.recent_failure", Integer.class)).isEqualTo(1);
        time.set(repository.recentPass(account, failing).orElseThrow().nextDueAt());
        assertThat(new SyncService(properties, repository, client, mapper, routes, manager, clock)
                .run(SyncCoordinator.JobKind.REGULAR, progress).success()).isTrue();
        var recovered = repository.recentPass(account, failing).orElseThrow();
        assertThat(recovered.id()).isNotEqualTo(firstPass);
        assertThat(recovered.state()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.recent_failure", Integer.class)).isZero();
    }

    @Test
    void widerRecentWindowRevokesAnOldHistoricalOwnerBeforePageWrites() {
        repository.saveConnected(account, Set.of(OAuthScopes.SLEEP), "synthetic", null);
        var policy = policy("sleep", DataTypePolicy.Mode.RAW);
        day = LocalDate.now(ZoneOffset.UTC).minusDays(8);
        var old = work(policy);
        var properties = new ConnectorProperties();
        properties.setClientId("synthetic");
        properties.setClientSecret("synthetic");
        properties.setRecentWindow(Period.ofDays(14));
        properties.setHistoryStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(15));
        var client = Mockito.mock(ApiClient.class);
        var purpose = new AtomicReference<String>();
        Mockito.doAnswer(call -> {
            purpose.set(call.getArgument(0));
            return null;
        }).when(client).purpose(ArgumentMatchers.any());
        Mockito.when(client.listRaw(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    if ("HISTORICAL_FINAL_CHECK".equals(purpose.get()))
                        assertThat((Instant) call.getArgument(1)).isBefore(SyncWindowPolicy.midnight(SyncWindowPolicy.cutoff(properties, Instant.now())));
                    return new ApiClient.Page(List.of(), null);
                });
        var service = new SyncService(properties, repository, client, mapper, Mockito.mock(ExerciseRouteService.class), manager);
        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, Mockito.mock(SyncCoordinator.Progress.class)).success()).isTrue();
        assertThat(jdbc.queryForObject("SELECT state FROM google_health.history_work WHERE id=?", String.class, old.id())).isEqualTo("REVOKED");
        assertThat(repository.isFinal(account, policy, day)).isFalse();
    }

    @Test
    void frozenFullPassAdvancesAcrossTwoBudgetedWorkersAndHistoryBorrowsUnusedTime() {
        repository.saveConnected(account, Set.of(OAuthScopes.SLEEP), "synthetic", null);
        var properties = new ConnectorProperties();
        properties.setClientId("synthetic");
        properties.setClientSecret("synthetic");
        properties.setRecentWindow(Period.ofDays(120));
        properties.setHistoryStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(125));
        properties.setSyncInterval(Duration.ofSeconds(11));
        properties.setCycleDeadlineMargin(Duration.ofSeconds(1));
        properties.setRequestTimeout(Duration.ofMillis(10));
        properties.setHistoryStreamQuantum(Duration.ofSeconds(3));
        var client = Mockito.mock(ApiClient.class);
        Mockito.when(client.listRaw(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    Thread.sleep(100);
                    return new ApiClient.Page(List.of(), null);
                });
        var routes = Mockito.mock(ExerciseRouteService.class);
        var progress = Mockito.mock(SyncCoordinator.Progress.class);
        var policy = policy("sleep", DataTypePolicy.Mode.RAW);
        long started = System.nanoTime();
        var first = new SyncService(properties, repository, client, mapper, routes, manager).run(SyncCoordinator.JobKind.REGULAR, progress);
        var saved = repository.recentPass(account, policy).orElseThrow();
        assertThat(first.statistics().completeFastStreams()).isEqualTo(1);
        assertThat(saved.state()).isEqualTo("ACTIVE");
        assertThat(saved.nextEnd()).isBefore(saved.end());
        var second = new SyncService(properties, repository, client, mapper, routes, manager).run(SyncCoordinator.JobKind.REGULAR, progress);
        var completed = repository.recentPass(account, policy).orElseThrow();
        assertThat(second.statistics().completeFastStreams()).isEqualTo(1);
        assertThat(completed.id()).isEqualTo(saved.id());
        assertThat(completed.end()).isEqualTo(saved.end());
        assertThat(completed.state()).isEqualTo("COMPLETED");
        assertThat(completed.cycles()).isEqualTo(2);
        assertThat(completed.nextDueAt()).isAfter(Instant.now().plus(Duration.ofHours(5)));
        assertThat(repository.synchronizationStatus(properties, Instant.now()).streams().getFirst().pendingDays()).isZero();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(22));
    }

    @Test
    void explicit1970FloorAndParkedGapProduceBoundedStatus() {
        var properties = new ConnectorProperties();
        properties.setHistoryStartDate(LocalDate.of(1970, 1, 1));
        var policy = policy("heart-rate", DataTypePolicy.Mode.HEART_RATE_SAMPLE);
        day = SyncWindowPolicy.cutoff(properties, Instant.now()).minusDays(1);
        var finalized = work(policy);
        for (var child : repository.children(finalized.id())) transaction.executeWithoutResult(status -> repository.checkpointChild(child, null, 0, 0, 1));
        transaction.executeWithoutResult(status -> repository.completeWork(finalized, true, 0));
        LocalDate coverage = day;
        day = day.minusDays(1);
        var parked = work(policy);
        transaction.executeWithoutResult(status -> repository.failWork(parked, 1, Instant.now(), "Synthetic parked gap"));
        long started = System.nanoTime();
        var status = repository.synchronizationStatus(properties, Instant.now());
        assertThat(status.streams()).isNotEmpty();
        assertThat(status.streams()).allSatisfy(stream -> assertThat(stream.pendingDays()).isGreaterThan(20000));
        var heart = status.streams().stream().filter(stream -> stream.representation().equals("HEART_RATE_SAMPLE")).findFirst().orElseThrow();
        assertThat(heart.finalizedBackTo()).isEqualTo(coverage);
        assertThat(heart.parkedDays()).isEqualTo(1);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
        assertThat(repository.newestUnmarked(account, policy, properties.getHistoryStartDate(), coverage.plusDays(1))).contains(day.minusDays(1));
    }

    @Test
    void boundedHistorySearchReturnsTheNewestGapWithoutMixingRepresentations() {
        var raw = policy("heart-rate", DataTypePolicy.Mode.HEART_RATE_SAMPLE);
        LocalDate newest = day, older = day.minusDays(3), floor = day.minusDays(5);
        for (LocalDate candidate = floor; !candidate.isAfter(newest); candidate = candidate.plusDays(1)) {
            if (candidate.equals(newest.minusDays(1)) || candidate.equals(older)) continue;
            seedHistoryAudits(raw, candidate, candidate.plusDays(1));
        }
        seedHistoryAudits(policy("heart-rate", DataTypePolicy.Mode.DAILY_ROLLUP), floor, newest.plusDays(1));

        assertThat(repository.newestUnmarked(account, raw, floor, newest.plusDays(1))).contains(newest.minusDays(1));
        assertThat(repository.newestUnmarked(account, raw, floor, newest.minusDays(1))).contains(older);
        assertThat(repository.newestUnmarked(account, raw, floor, older)).isEmpty();
    }

    @Test
    void aRolledBackAuditDoesNotAdvanceTheCursorAndReloadReopensItsGap() {
        var scopes = Set.of(OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS, OAuthScopes.ACTIVITY_AND_FITNESS, OAuthScopes.SLEEP);
        repository.saveConnected(account, scopes, "synthetic", null);
        var policies = DataTypePolicy.allowedBy(scopes);
        var tail = policy("daily-resting-heart-rate", DataTypePolicy.Mode.RAW);
        var now = SyncWindowPolicy.midnight(day.plusDays(8)).plusSeconds(3600);
        var properties = historyCursorProperties(day.minusDays(1));
        LocalDate cutoff = SyncWindowPolicy.cutoff(properties, now);
        for (var policy : policies) if (!policy.equals(tail)) seedHistoryAudits(policy, properties.getHistoryStartDate(), cutoff);
        var observed = historyCursorRepository(properties, now);
        var client = emptyHistoryProvider();
        var service = new SyncService(properties, observed, client, mapper, Mockito.mock(ExerciseRouteService.class), manager, Clock.fixed(now, ZoneOffset.UTC));
        jdbc.execute("CREATE FUNCTION google_health.test_cursor_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                     + "IF NEW.data_type='daily-resting-heart-rate' AND NEW.native_day=DATE '2026-09-29' AND NEW.outcome IN ('DATA','EMPTY') THEN "
                     + "RAISE EXCEPTION 'Synthetic finalization failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER test_cursor_audit_failure BEFORE INSERT ON google_health.history_audit "
                     + "FOR EACH ROW EXECUTE FUNCTION google_health.test_cursor_audit_failure()");
        try {
            assertThat(service.run(SyncCoordinator.JobKind.REGULAR, Mockito.mock(SyncCoordinator.Progress.class)).success()).isFalse();
            assertThat(repository.isFinal(account, tail, day)).isFalse();
            assertThat(repository.isFinal(account, tail, day.minusDays(1))).isTrue();
            assertThat(jdbc.queryForObject("SELECT state FROM google_health.history_work WHERE account_id=? AND data_type=? AND native_day=?",
                    String.class, account, tail.dataType(), day)).isIn("WAITING", "PARKED");
        } finally {
            jdbc.execute("DROP TRIGGER test_cursor_audit_failure ON google_health.history_audit");
            jdbc.execute("DROP FUNCTION google_health.test_cursor_audit_failure()");
        }
        repository.reloadDays(account, List.of(tail), day, day.plusDays(1), cutoff, true);
        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, Mockito.mock(SyncCoordinator.Progress.class)).success()).isTrue();
        assertThat(repository.isFinal(account, tail, day)).isTrue();
    }

    @Test
    void historyCursorReducesActualAuditIndexProbesForTheOneStreamTail() {
        final int historicalDays = 6_123, remainingDays = 100;
        var scopes = Set.of(OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS, OAuthScopes.ACTIVITY_AND_FITNESS, OAuthScopes.SLEEP);
        repository.saveConnected(account, scopes, "synthetic", null);
        var policies = DataTypePolicy.allowedBy(scopes);
        var tail = policy("daily-resting-heart-rate", DataTypePolicy.Mode.RAW);
        Instant now = Instant.parse("2026-10-14T12:00:00Z");
        var properties = historyCursorProperties(LocalDate.of(2010, 1, 1));
        LocalDate cutoff = SyncWindowPolicy.cutoff(properties, now), floor = cutoff.minusDays(historicalDays);
        properties.setHistoryStartDate(floor);
        for (var policy : policies) seedHistoryAudits(policy, policy.equals(tail) ? floor.plusDays(remainingDays) : floor, cutoff);
        jdbc.execute("ANALYZE google_health.history_audit");

        var completedProbes = new AtomicLong();
        for (var policy : policies) if (!policy.equals(tail)) {
            assertThat(measureAuditIndexProbes(completedProbes, () -> legacyHistorySearch(policy, floor, cutoff))).isEmpty();
        }
        var tailProbes = new AtomicLong();
        assertThat(measureAuditIndexProbes(tailProbes, () -> legacyHistorySearch(tail, floor, cutoff))).contains(floor.plusDays(remainingDays - 1));
        long repeatedProbeLowerBound = remainingDays * (completedProbes.get() + 2 * tailProbes.get());
        assertThat(repeatedProbeLowerBound).isPositive();

        var observed = historyCursorRepository(properties, now);
        var optimizedProbes = new AtomicLong();
        Mockito.doAnswer(call -> measureAuditIndexProbes(optimizedProbes, () -> repository.newestUnmarked(
                call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3))))
                .when(observed).newestUnmarked(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        // Keep one-day work to reproduce the populated-day tail without inventing provider records.
        Mockito.doAnswer(call -> call.getArgument(2)).when(observed).emptyBatchStart(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        var service = new SyncService(properties, observed, emptyHistoryProvider(), mapper, Mockito.mock(ExerciseRouteService.class), manager,
                Clock.fixed(now, ZoneOffset.UTC));
        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, Mockito.mock(SyncCoordinator.Progress.class)).success()).isTrue();
        assertThat(repository.newestUnmarked(account, tail, floor, cutoff)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.history_audit WHERE account_id=? AND outcome IN ('DATA','EMPTY')",
                Long.class, account)).isEqualTo((long) historicalDays * policies.size());
        assertThat(optimizedProbes.get()).isLessThan(repeatedProbeLowerBound / 20);
        System.out.printf("Synthetic PostGIS history probes: optimized=%d, repeated lower bound=%d, reduction=%.2f%%%n",
                optimizedProbes.get(), repeatedProbeLowerBound, 100.0 * (1 - (double) optimizedProbes.get() / repeatedProbeLowerBound));
    }

    private ConnectorProperties historyCursorProperties(LocalDate floor) {
        var properties = new ConnectorProperties();
        properties.setClientId("synthetic");
        properties.setClientSecret("synthetic");
        properties.setHistoryStartDate(floor);
        properties.setRecentWindow(Period.ofDays(7));
        properties.setSyncInterval(Duration.ofMinutes(1));
        properties.setCycleDeadlineMargin(Duration.ofSeconds(1));
        properties.setRecentFastWindow(Duration.ofMinutes(1));
        properties.setRequestTimeout(Duration.ofMillis(10));
        return properties;
    }

    private MirrorRepository historyCursorRepository(ConnectorProperties properties, Instant now) {
        var observed = Mockito.mock(MirrorRepository.class, org.mockito.AdditionalAnswers.delegatesTo(repository));
        LocalDate cutoff = SyncWindowPolicy.cutoff(properties, now);
        Mockito.doAnswer(call -> {
            LocalDate selectedDay = call.getArgument(2);
            return !selectedDay.isBefore(cutoff) || repository.isFinal(call.getArgument(0), call.getArgument(1), selectedDay);
        }).when(observed).isFinal(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        Mockito.doReturn(java.util.Optional.of(new MirrorRepository.RecentPass(UUID.randomUUID(), now, now, now, now,
                "COMPLETED", now.plus(Duration.ofDays(1)), 1, 0, 0))).when(observed).recentPass(ArgumentMatchers.any(), ArgumentMatchers.any());
        return observed;
    }

    private ApiClient emptyHistoryProvider() {
        var client = Mockito.mock(ApiClient.class);
        Mockito.when(client.listRaw(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new ApiClient.Page(List.of(), null));
        Mockito.when(client.reconcile(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new ApiClient.Page(List.of(), null));
        Mockito.when(client.dailyRollUp(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new ApiClient.Page(List.of(), null));
        return client;
    }

    private MapSqlParameterSource historyScope(DataTypePolicy.Policy policy) {
        return new MapSqlParameterSource().addValue("account", account).addValue("type", policy.dataType())
                .addValue("representation", policy.representation()).addValue("calendar", SyncWindowPolicy.CALENDAR_POLICY)
                .addValue("key", SyncWindowPolicy.KEY_POLICY);
    }

    private void seedHistoryAudits(DataTypePolicy.Policy policy, LocalDate start, LocalDate end) {
        new NamedParameterJdbcTemplate(jdbc).update("""
                INSERT INTO google_health.history_audit(account_id,data_type,representation,calendar_policy,key_policy,native_day,
                    start_bound,end_bound,reason,outcome,work_id,traversal_ids,fetched,changed,stamped,deleted,started_at)
                SELECT :account,:type,:representation,:calendar,:key,d::date,d AT TIME ZONE 'UTC',(d+interval '1 day') AT TIME ZONE 'UTC',
                    'SYNTHETIC','EMPTY',gen_random_uuid(),ARRAY[]::uuid[],0,0,0,0,now()
                  FROM generate_series(CAST(:start AS date)::timestamp,CAST(:end AS date)::timestamp-interval '1 day',interval '1 day') d
                """, historyScope(policy).addValue("start", start).addValue("end", end));
    }

    private java.util.Optional<LocalDate> legacyHistorySearch(DataTypePolicy.Policy policy, LocalDate floor, LocalDate before) {
        return new NamedParameterJdbcTemplate(jdbc).query("""
                SELECT d::date FROM generate_series(CAST(:before AS date)-1,CAST(:floor AS date),interval '-1 day') d
                 WHERE NOT EXISTS(SELECT 1 FROM google_health.history_audit WHERE account_id=:account AND data_type=:type
                    AND representation=:representation AND calendar_policy=:calendar AND key_policy=:key
                    AND native_day=d::date AND outcome IN ('DATA','EMPTY'))
                   AND NOT EXISTS(SELECT 1 FROM google_health.history_work WHERE account_id=:account AND data_type=:type
                    AND representation=:representation AND calendar_policy=:calendar AND key_policy=:key
                    AND lane='HISTORY' AND d::date>=(start_bound AT TIME ZONE 'UTC')::date
                    AND d::date<(end_bound AT TIME ZONE 'UTC')::date AND state IN ('ACTIVE','WAITING','PARKED')) LIMIT 1
                """, historyScope(policy).addValue("floor", floor).addValue("before", before),
                (rs, n) -> rs.getObject(1, LocalDate.class)).stream().findFirst();
    }

    private <T> T measureAuditIndexProbes(AtomicLong counter, Supplier<T> query) {
        var read = new TransactionTemplate(manager);
        read.setReadOnly(true);
        return read.execute(status -> {
            long before = jdbc.queryForObject("SELECT idx_scan FROM pg_stat_xact_user_tables WHERE schemaname='google_health' AND relname='history_audit'", Long.class);
            T result = query.get();
            long after = jdbc.queryForObject("SELECT idx_scan FROM pg_stat_xact_user_tables WHERE schemaname='google_health' AND relname='history_audit'", Long.class);
            counter.addAndGet(after - before);
            return result;
        });
    }
}
