package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiClient;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.exercise.ExerciseRouteService;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataSourceResolver;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.PointMapper;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncCoordinator;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncService;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncWindowPolicy;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "mtl.planner.enabled=false",
        "mtl.indexer.gps.live-watch-enabled=false",
        "mtl.indexer.media.live-watch-enabled=false",
        "mtl.media-correlation.initial-delay=PT1H"
})
class BatchCompletionDatabaseIntegrationTest {

    private static final int PAGE_SIZE = 5_000;
    private static final String NEXT_TOKEN = "synthetic-next-page";
    private static final Duration STREAM_ALLOWANCE = Duration.ofSeconds(2);

    @Autowired
    private MirrorRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager manager;

    @MockitoBean
    private GPXDirectoryWatcherService watcher;

    @MockitoBean
    private MediaIndexerService media;

    private final ObjectMapper json = new ObjectMapper();
    private final List<String> historicalRequests = new ArrayList<>();
    private ConnectorProperties properties;
    private MirrorRepository serviceRepository;
    private MirrorRepository.HistoryWork work;
    private MirrorRepository.HistoryChild child;
    private DataTypePolicy.Policy heartPolicy;
    private ApiClient client;
    private SyncCoordinator.Progress progress;
    private SyncService service;
    private String account;
    private List<JsonNode> points;

    @BeforeEach
    void setup() {
        repository.disconnect();
        repository.deleteMirrorRows();
        jdbc.update("UPDATE google_health.connection SET google_user_id=NULL WHERE id=1");
        account = "synthetic-batch-" + UUID.randomUUID();
        repository.saveConnected(account, Set.of(OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS), "synthetic", null);
        repository.synchronizationGeneration(account);
        properties = new ConnectorProperties();
        properties.setClientId("synthetic");
        properties.setClientSecret("synthetic");
        properties.setRequestTimeout(Duration.ofMillis(10));
        properties.setDatabaseTransactionTimeout(Duration.ofSeconds(10));
        properties.setHistoryStreamQuantum(STREAM_ALLOWANCE);
        LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(8);
        properties.setHistoryStartDate(day);
        heartPolicy = DataTypePolicy.all().stream()
                .filter(p -> p.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE).findFirst().orElseThrow();
        Instant start = SyncWindowPolicy.midnight(day);
        var bounds = new SyncWindowPolicy.Bounds(heartPolicy.windowTimeDomain(), start, start.plus(Duration.ofDays(1)));
        work = new TransactionTemplate(manager).execute(status ->
                repository.createWork(account, heartPolicy, day, "HISTORY", List.of(bounds)));
        child = repository.children(work.id()).getFirst();
        points = new ArrayList<>();
        for (int i = 0; i < PAGE_SIZE; i++) {
            var point = json.createObjectNode();
            point.put("name", "users/synthetic/dataTypes/heart-rate/dataPoints/sample-" + i);
            var heart = point.putObject("heartRate");
            heart.put("beatsPerMinute", 60 + i % 40);
            heart.putObject("sampleTime").put("physicalTime", start.plusSeconds(4L * i + 1).toString()).put("utcOffset", "0s");
            var source = point.putObject("dataSource");
            source.put("platform", "ANDROID").put("recordingMethod", "AUTOMATIC");
            source.putObject("device").put("displayName", "Synthetic Watch");
            points.add(point);
        }
        // Recent coverage is already complete in this fixture; exercise the real historical worker.
        serviceRepository = spy(new MirrorRepository(jdbc, json));
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(7);
        doAnswer(call -> call.getArgument(2, LocalDate.class).isBefore(cutoff) ? call.callRealMethod() : true)
                .when(serviceRepository).isFinal(anyString(), any(), any());
        Instant now = Instant.now();
        for (var policy : DataTypePolicy.allowedBy(Set.of(OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS))) {
            repository.startRecentPass(account, policy, UUID.randomUUID(), now, now.minus(Duration.ofDays(7)), now);
        }
        jdbc.update("UPDATE google_health.recent_pass SET state='COMPLETED',completed_at=now(),next_due_at=now()+interval '1 hour' WHERE account_id=?", account);
        client = mock(ApiClient.class);
        when(client.dailyRollUp(any(), any(), any(), any())).thenReturn(new ApiClient.Page(List.of(), null));
        when(client.listRaw(any(), any(), any(), any())).thenAnswer(call -> {
            DataTypePolicy.Policy policy = call.getArgument(0);
            String token = call.getArgument(3);
            historicalRequests.add(policy.dataType() + ":" + token);
            return new ApiClient.Page(policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE && token == null ? points : List.of(),
                    policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE && token == null ? NEXT_TOKEN : null);
        });
        progress = mock(SyncCoordinator.Progress.class);
        service = new SyncService(properties, serviceRepository, client,
                new PointMapper(new DataSourceResolver(serviceRepository)), mock(ExerciseRouteService.class), manager);
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_batch_completion_delay ON google_health.heart_rate_sample");
        jdbc.execute("DROP FUNCTION IF EXISTS google_health.test_batch_completion_delay()");
        repository.disconnect();
        repository.deleteMirrorRows();
        jdbc.update("UPDATE google_health.connection SET google_user_id=NULL WHERE id=1");
    }

    @Test
    void fullPageFinishesPastItsStreamAllowanceBeforeAnotherStreamRuns() {
        delayFirstInsert();
        when(progress.cycleDeadline()).thenReturn(Instant.now().plusSeconds(20));

        var result = service.run(SyncCoordinator.JobKind.REGULAR, progress);

        assertThat(result.success()).isTrue();
        assertCommittedPage();
        var saved = repository.children(work.id()).getFirst();
        assertThat(saved.pages()).isEqualTo(2);
        assertThat(saved.state()).isEqualTo("COMPLETED");
        int resumed = historicalRequests.indexOf("heart-rate:" + NEXT_TOKEN);
        assertThat(resumed).isGreaterThan(1);
        assertThat(historicalRequests.subList(1, resumed)).anyMatch(request -> !request.startsWith("heart-rate:"));
        verify(serviceRepository).importHistoricalHeartRate(argThat(rows -> rows.size() == PAGE_SIZE));
        assertThat(jdbc.queryForObject("SELECT attempts FROM google_health.history_work WHERE id=?", Integer.class, work.id())).isZero();
    }

    @Test
    void fullPageAndRotationCommitPastTheCycleDeadlineAndResumeFromTheSavedToken() {
        delayFirstInsert();
        when(progress.cycleDeadline()).thenReturn(Instant.now().plusMillis(2500));

        var result = service.run(SyncCoordinator.JobKind.REGULAR, progress);

        assertThat(result.success()).isTrue();
        assertThat(result.statistics().budgetLimited()).isTrue();
        assertCommittedPage();
        assertThat(historicalRequests).containsExactly("heart-rate:null");
        var saved = repository.children(work.id()).getFirst();
        assertThat(saved.pages()).isEqualTo(1);
        assertThat(saved.token()).isEqualTo(NEXT_TOKEN);
        assertThat(repository.rotation(account, true)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT attempts FROM google_health.history_work WHERE id=?", Integer.class, work.id())).isZero();

        historicalRequests.clear();
        clearInvocations(serviceRepository);
        when(progress.cycleDeadline()).thenReturn(Instant.now().plusSeconds(20));
        var restarted = new SyncService(properties, serviceRepository, client,
                new PointMapper(new DataSourceResolver(serviceRepository)), mock(ExerciseRouteService.class), manager);
        assertThat(restarted.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isTrue();
        assertThat(historicalRequests).contains("heart-rate:" + NEXT_TOKEN).doesNotContain("heart-rate:null");
        assertThat(repository.children(work.id()).getFirst().pages()).isEqualTo(2);
        verify(serviceRepository, never()).importHistoricalHeartRate(argThat(rows -> !rows.isEmpty()));
        assertCommittedPage();
    }

    @Test
    void hardDatabaseTimeoutRollsBackTheWholePageAndRecordsItsFailure() {
        delayFirstInsert();
        properties.setDatabaseTransactionTimeout(Duration.ofSeconds(1));
        when(progress.cycleDeadline()).thenReturn(Instant.now().plusSeconds(20));

        var result = service.run(SyncCoordinator.JobKind.REGULAR, progress);

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("Local database"));
        assertNoPageCommitted();
        assertThat(jdbc.queryForObject("SELECT attempts FROM google_health.history_work WHERE id=?", Integer.class, work.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT state FROM google_health.history_work WHERE id=?", String.class, work.id())).isEqualTo("WAITING");
    }

    @Test
    void cancellationAfterBatchedWritesRollsBackRecordsCheckpointAndRotation() {
        var cancelled = new AtomicBoolean();
        when(progress.isCancelled()).thenAnswer(call -> cancelled.get());
        doAnswer(call -> {
            Object written = call.callRealMethod();
            cancelled.set(true);
            return written;
        }).when(serviceRepository).importHistoricalHeartRate(any());

        assertThatThrownBy(() -> service.run(SyncCoordinator.JobKind.REGULAR, progress)).isInstanceOf(CancellationException.class);

        assertNoPageCommitted();
        assertThat(repository.rotation(account, true)).isZero();
        assertThat(jdbc.queryForObject("SELECT attempts FROM google_health.history_work WHERE id=?", Integer.class, work.id())).isZero();
    }

    @Test
    void revokedOwnershipStillRejectsAFetchedPage() {
        doAnswer(call -> {
            jdbc.update("UPDATE google_health.sync_control SET generation=generation+1 WHERE account_id=?", account);
            return new ApiClient.Page(points, NEXT_TOKEN);
        }).when(client).listRaw(argThat(p -> p.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE), any(), any(), any());

        assertThatThrownBy(() -> service.run(SyncCoordinator.JobKind.REGULAR, progress)).isInstanceOf(CancellationException.class);

        assertNoPageCommitted();
        assertThat(repository.rotation(account, true)).isZero();
    }

    private void delayFirstInsert() {
        jdbc.execute("""
                CREATE FUNCTION google_health.test_batch_completion_delay() RETURNS trigger LANGUAGE plpgsql AS
                'BEGIN IF NEW.google_resource_name LIKE ''%/sample-0'' THEN PERFORM pg_sleep(3.2); END IF; RETURN NEW; END'
                """);
        jdbc.execute("CREATE TRIGGER test_batch_completion_delay BEFORE INSERT ON google_health.heart_rate_sample FOR EACH ROW EXECUTE FUNCTION google_health.test_batch_completion_delay()");
    }

    private void assertCommittedPage() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample", Long.class)).isEqualTo(PAGE_SIZE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample WHERE last_seen_run IS NOT NULL", Long.class)).isZero();
        assertThat(repository.children(work.id()).getFirst().fetched()).isEqualTo(PAGE_SIZE);
        assertThat(jdbc.queryForObject("SELECT changed FROM google_health.history_child WHERE id=?", Long.class, child.id())).isEqualTo(PAGE_SIZE);
    }

    private void assertNoPageCommitted() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.data_source", Long.class)).isZero();
        var saved = repository.children(work.id()).getFirst();
        assertThat(saved.pages()).isZero();
        assertThat(saved.fetched()).isZero();
        assertThat(saved.token()).isNull();
    }
}
