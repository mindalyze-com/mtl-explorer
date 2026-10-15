package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiClient;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.exercise.ExerciseRouteService;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataSourceResolver;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.PointMapper;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.StableFingerprint;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.StoredRepresentationMapper;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncCoordinator;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncService;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncValidationException;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncWindowPolicy;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "mtl.planner.enabled=false",
        "mtl.indexer.gps.live-watch-enabled=false",
        "mtl.indexer.media.live-watch-enabled=false",
        "mtl.media-correlation.initial-delay=PT1H"
})
class IdentityLookupDatabaseIntegrationTest {

    private static final int PAGE_SIZE = 5_000;
    private static final Instant SAMPLE_TIME = Instant.parse("2050-01-01T00:00:00Z");

    @Autowired
    private MirrorRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    @MockitoBean
    private GPXDirectoryWatcherService watcher;

    @MockitoBean
    private MediaIndexerService media;

    @BeforeEach
    void setup() {
        repository.disconnect();
        repository.deleteMirrorRows();
        jdbc.update("UPDATE google_health.connection SET google_user_id=NULL WHERE id=1");
        repository.saveConnected("synthetic-identity-lookup", Set.of(OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS), "synthetic", null);
    }

    @AfterEach
    void cleanup() {
        repository.disconnect();
        repository.deleteMirrorRows();
        jdbc.update("UPDATE google_health.connection SET google_user_id=NULL WHERE id=1");
    }

    @Test
    void foreignStreamAndRepresentationCannotCountAsTheRequestedObservation() {
        String key = StableFingerprint.of("synthetic-shared-key");
        String fingerprint = StableFingerprint.of("synthetic-content");
        point("heart-rate-variability", "RAW", key, fingerprint, null);
        point("oxygen-saturation", "DAILY_ROLLUP", key, fingerprint, null);
        assertThat(repository.countContentChanges(oxygen(), Map.of(key, fingerprint))).isEqualTo(1);
        point("oxygen-saturation", "RAW", key, fingerprint, null);
        assertThat(repository.countContentChanges(oxygen(), Map.of(key, fingerprint))).isZero();
    }

    @Test
    void foreignStreamAndRepresentationCannotRejectTheCurrentTraversal() {
        String key = StableFingerprint.of("synthetic-shared-key");
        UUID traversal = UUID.randomUUID();
        point("heart-rate-variability", "RAW", key, StableFingerprint.of("foreign-content"), traversal);
        point("oxygen-saturation", "DAILY_ROLLUP", key, StableFingerprint.of("foreign-content"), traversal);
        assertThatCode(() -> repository.checkQueryConflicts(
                oxygen(), Map.of(key, StableFingerprint.of("requested-content")), traversal)).doesNotThrowAnyException();
    }

    @Test
    void matchingTraversalStillRejectsConflictingContent() {
        String key = StableFingerprint.of("synthetic-current-key");
        UUID traversal = UUID.randomUUID();
        point("oxygen-saturation", "RAW", key, StableFingerprint.of("old-content"), traversal);
        var incoming = Map.of(key, StableFingerprint.of("new-content"));
        assertThat(repository.countContentChanges(oxygen(), incoming)).isEqualTo(1);
        assertConflict(oxygen(), incoming, traversal);
        assertThatCode(() -> repository.checkQueryConflicts(oxygen(), incoming, UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void historicalConflictKeepsItsValidationDiagnosticBeforePersistence() {
        String account = "synthetic-identity-lookup";
        String privateMarker = "synthetic-private-provider-value";
        repository.saveConnected(account, Set.of(OAuthScopes.SLEEP), "synthetic", null);
        var policy = DataTypePolicy.all().stream().filter(p -> p.dataType().equals("sleep")).findFirst().orElseThrow();
        LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(10);
        Instant start = SyncWindowPolicy.midnight(day);
        var work = repository.createWork(account, policy, day, "HISTORY", List.of(
                new SyncWindowPolicy.Bounds(policy.windowTimeDomain(), start, SyncWindowPolicy.midnight(day.plusDays(1)))));
        var properties = new ConnectorProperties();
        properties.setClientId("synthetic");
        properties.setClientSecret("synthetic");
        properties.setHistoryStartDate(day);
        var json = new ObjectMapper();
        var point = json.createObjectNode();
        point.put("name", "users/synthetic/dataTypes/sleep/dataPoints/" + privateMarker);
        point.putObject("sleep").putObject("interval")
                .put("startTime", start.toString()).put("endTime", start.plusSeconds(3600).toString());
        var changedPoint = point.deepCopy();
        changedPoint.withObject("sleep").put("syntheticDifference", true);
        var client = mock(ApiClient.class);
        when(client.listRaw(any(), any(), any(), any())).thenAnswer(call -> {
            if (!start.equals(call.getArgument(1))) return new ApiClient.Page(List.of(), null);
            return call.getArgument(3) == null
                    ? new ApiClient.Page(List.of(point), "synthetic-next-page")
                    : new ApiClient.Page(List.of(changedPoint), null);
        });
        var service = new SyncService(properties, repository, client, new PointMapper(new DataSourceResolver(repository)),
                mock(ExerciseRouteService.class), transactions);
        var logger = (Logger) LoggerFactory.getLogger(SyncService.class);
        var logs = new ListAppender<ILoggingEvent>();
        Level previousLevel = logger.getLevel();
        logs.start();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(logs);
        try {
            var result = service.run(SyncCoordinator.JobKind.REGULAR, mock(SyncCoordinator.Progress.class));

            assertThat(result.success()).isFalse();
            assertThat(result.errors()).containsExactly("sleep: Health data validation failed. Reason: DUPLICATE_TRAVERSAL_IDENTITY.");
            assertThat(logs.list).filteredOn(event -> event.getLevel() == Level.WARN
                                                    && event.getFormattedMessage().contains("historical work failed"))
                    .singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).contains(
                            "type=SyncValidationException", "reason=DUPLICATE_TRAVERSAL_IDENTITY",
                            "detail=Conflicting duplicate identity in one traversal"));
            assertThat(logs.list).filteredOn(event -> event.getLevel() == Level.DEBUG)
                    .singleElement().satisfies(event -> {
                        var trace = event.getThrowableProxy();
                        assertThat(trace).isNotNull();
                        assertThat(trace.getMessage()).contains("reason=DUPLICATE_TRAVERSAL_IDENTITY").doesNotContain(privateMarker);
                        assertThat(trace.getCause()).isNull();
                        assertThat(trace.getSuppressed()).isNullOrEmpty();
                    });
            assertThat(logs.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(privateMarker));
            assertThat(repository.children(work.id())).singleElement().satisfies(child -> assertThat(child.pages()).isEqualTo(1));
            assertThat(jdbc.queryForObject("SELECT safe_error FROM google_health.history_work WHERE id=?", String.class, work.id()))
                    .isEqualTo("Health data validation failed. Reason: DUPLICATE_TRAVERSAL_IDENTITY.");
        } finally {
            logger.detachAppender(logs);
            logger.setLevel(previousLevel);
            logs.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(DataTypePolicy.Mode.class)
    void fullPageMatchesLegacyCountsAndConflictDecisionsWithoutChangingRecords(DataTypePolicy.Mode mode) {
        var policy = DataTypePolicy.all().stream().filter(p -> p.mode() == mode).findFirst().orElseThrow();
        String table = mode == DataTypePolicy.Mode.HEART_RATE_SAMPLE ? "heart_rate_sample" : "data_point";
        UUID traversal = UUID.randomUUID();
        if (mode == DataTypePolicy.Mode.HEART_RATE_SAMPLE) {
            jdbc.update("""
                    INSERT INTO google_health.heart_rate_sample(
                        connection_id,sample_time,original_sample_time,beats_per_minute,identity_key,fingerprint,raw_json,last_seen_run)
                    SELECT 1,?::timestamptz+i*interval '1 second','synthetic-time',65,
                        repeat(md5('key-'||i),2),repeat(md5('fp-'||i),2),'{"synthetic":true}',?
                    FROM generate_series(1,?) i
                    """, Timestamp.from(SAMPLE_TIME), traversal, PAGE_SIZE);
        } else {
            jdbc.update("""
                    INSERT INTO google_health.data_point(
                        connection_id,data_type,representation,identity_key,fingerprint,physical_start_time,raw_json,last_seen_run)
                    SELECT 1,?,?,repeat(md5('key-'||i),2),repeat(md5('fp-'||i),2),
                        ?::timestamptz+i*interval '1 second','{"synthetic":true}',?
                    FROM generate_series(1,?) i
                    """, policy.dataType(), StoredRepresentationMapper.from(mode).name(), Timestamp.from(SAMPLE_TIME), traversal, PAGE_SIZE);
        }
        var before = jdbc.queryForList("SELECT * FROM google_health." + table + " ORDER BY id");
        Map<String, String> incoming = new LinkedHashMap<>();
        for (int i = 0; i < before.size(); i++) {
            String key = (String) before.get(i).get("identity_key");
            String fingerprint = (String) before.get(i).get("fingerprint");
            if (i % 4 == 1) fingerprint = StableFingerprint.of("changed-" + i);
            if (i % 4 == 2) key = StableFingerprint.of("missing-" + i);
            incoming.put(key, fingerprint);
        }
        assertThat(legacyCount(table, incoming)).isEqualTo(PAGE_SIZE / 2);
        assertThat(repository.countContentChanges(policy, incoming)).isEqualTo(legacyCount(table, incoming));
        assertThat(legacyConflict(table, incoming, traversal)).isTrue();
        assertConflict(policy, incoming, traversal);
        UUID otherTraversal = UUID.randomUUID();
        assertThat(legacyConflict(table, incoming, otherTraversal)).isFalse();
        assertThatCode(() -> repository.checkQueryConflicts(policy, incoming, otherTraversal)).doesNotThrowAnyException();
        assertThat(jdbc.queryForList("SELECT * FROM google_health." + table + " ORDER BY id")).isEqualTo(before);
    }

    @Test
    void unchangedContentStillAllowsReplaysInTheSameTraversal() {
        String key = StableFingerprint.of("unchanged-key");
        String fingerprint = StableFingerprint.of("unchanged-content");
        UUID traversal = UUID.randomUUID();
        point("oxygen-saturation", "RAW", key, fingerprint, traversal);
        var incoming = Map.of(key, fingerprint);
        assertThat(repository.countContentChanges(oxygen(), incoming)).isZero();
        assertThat(legacyConflict("data_point", incoming, traversal)).isFalse();
        assertThatCode(() -> repository.checkQueryConflicts(oxygen(), incoming, traversal)).doesNotThrowAnyException();
    }

    @Test
    void emptyPagesAndAbsentTraversalKeepTheirExistingBehavior() {
        for (var policy : DataTypePolicy.all()) {
            assertThat(repository.countContentChanges(policy, Map.of())).isZero();
            assertThatCode(() -> repository.checkQueryConflicts(policy, Map.of(), UUID.randomUUID())).doesNotThrowAnyException();
            assertThatCode(() -> repository.checkQueryConflicts(
                    policy, Map.of(StableFingerprint.of("key"), StableFingerprint.of("content")), null)).doesNotThrowAnyException();
        }
    }

    private DataTypePolicy.Policy oxygen() {
        return DataTypePolicy.all().stream()
                .filter(p -> p.dataType().equals("oxygen-saturation") && p.mode() == DataTypePolicy.Mode.RAW)
                .findFirst().orElseThrow();
    }

    private void point(String type, String representation, String key, String fingerprint, UUID traversal) {
        jdbc.update("""
                INSERT INTO google_health.data_point(
                    connection_id,data_type,representation,identity_key,fingerprint,physical_start_time,raw_json,last_seen_run)
                VALUES(1,?,?,?,?,?,'{}',?)
                """, type, representation, key, fingerprint, Timestamp.from(SAMPLE_TIME), traversal);
    }

    private long legacyCount(String table, Map<String, String> incoming) {
        return jdbc.queryForObject("SELECT count(*) FROM unnest(?::text[],?::text[]) incoming(key,fp)"
                                   + " LEFT JOIN google_health." + table + " stored ON stored.connection_id=1 AND stored.identity_key=incoming.key"
                                   + " WHERE stored.fingerprint IS DISTINCT FROM incoming.fp", Long.class,
                new SqlArrayValue("text", incoming.keySet().toArray(Object[]::new)),
                new SqlArrayValue("text", incoming.values().toArray(Object[]::new)));
    }

    private boolean legacyConflict(String table, Map<String, String> incoming, UUID traversal) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM unnest(?::text[],?::text[]) incoming(key,fp)"
                                                      + " JOIN google_health." + table + " stored ON stored.connection_id=1 AND stored.identity_key=incoming.key"
                                                      + " WHERE stored.last_seen_run=? AND stored.fingerprint IS DISTINCT FROM incoming.fp)", Boolean.class,
                new SqlArrayValue("text", incoming.keySet().toArray(Object[]::new)),
                new SqlArrayValue("text", incoming.values().toArray(Object[]::new)), traversal));
    }

    private void assertConflict(DataTypePolicy.Policy policy, Map<String, String> incoming, UUID traversal) {
        assertThatThrownBy(() -> repository.checkQueryConflicts(policy, incoming, traversal))
                .hasRootCauseInstanceOf(SyncValidationException.class)
                .rootCause().isInstanceOfSatisfying(SyncValidationException.class,
                        failure -> assertThat(failure.reason()).isEqualTo(SyncValidationException.Reason.DUPLICATE_TRAVERSAL_IDENTITY));
    }
}
