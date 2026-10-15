package com.x8ing.mtl.server.mtlserver.googlehealth.simulation;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.HeartRateSample;
import com.x8ing.mtl.server.mtlserver.googlehealth.provider.GoogleHealthProvider;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncValidationException;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncWindowPolicy;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "MTL_HEALTH_TEST_DB_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistoricalBatchImportSimulationTest {
    private static final Instant START = Instant.parse("2025-12-30T00:00:00Z");
    private static final Instant END = Instant.parse("2026-01-01T00:00:00Z");
    private static final DataTypePolicy.Policy HEART = DataTypePolicy.all().stream()
            .filter(policy -> policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE).findFirst().orElseThrow();
    private HistoricalHeartRateImportSimulation simulation;

    @BeforeAll
    void connect() throws Exception {
        simulation = new HistoricalHeartRateImportSimulation();
    }

    @BeforeEach
    void reset() {
        simulation.reset();
    }

    @AfterAll
    void close() {
        if (simulation == null) return;
        try {
            simulation.clear();
        } finally {
            simulation.close();
        }
    }

    @Test
    void existingIdenticalRecordsAreDataWithoutStampingOrRewritingRows() {
        var provider = provider(true, List.of());
        var first = simulation.runCycle(provider, START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertThat(first.result().success()).isTrue();
        List<String> rowVersions = simulation.jdbc.queryForList(
                "SELECT xmin::text FROM google_health.heart_rate_sample ORDER BY identity_key", String.class);
        simulation.transaction(() -> {
            simulation.jdbc.update("DELETE FROM google_health.history_audit");
            simulation.jdbc.update("DELETE FROM google_health.history_work");
        });
        var resumed = simulation.runCycle(provider, START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertThat(resumed.result().errors()).isEmpty();
        assertThat(resumed.rows()).isEqualTo(provider.expectedSamples());
        assertThat(resumed.result().statistics().changed()).isZero();
        assertThat(resumed.result().statistics().stamped()).isZero();
        assertThat(resumed.result().statistics().deleted()).isZero();
        assertThat(simulation.jdbc.queryForList(
                "SELECT xmin::text FROM google_health.heart_rate_sample ORDER BY identity_key", String.class)).isEqualTo(rowVersions);
        assertThat(simulation.jdbc.queryForObject(
                "SELECT count(*) FROM google_health.heart_rate_sample WHERE last_seen_run IS NOT NULL", Long.class)).isZero();
        assertThat(simulation.jdbc.queryForObject("SELECT count(*) FROM google_health.history_audit "
                + "WHERE data_type='heart-rate' AND representation='HEART_RATE_SAMPLE' AND outcome='DATA'", Long.class)).isEqualTo(2);
        var skipped = simulation.runCycle(provider, START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertThat(skipped.readsByPurpose().getOrDefault("HISTORICAL_FINAL_CHECK", 0L)).isZero();
        assertThat(skipped.rows()).isEqualTo(resumed.rows());
    }

    @Test
    void successfulEmptyDaysRetainPreexistingRecordsAndTheirStamps() {
        UUID stamp = UUID.randomUUID();
        simulation.transaction(() -> simulation.repository.upsertHeartRateSamples(List.of(sample('a', START.plusSeconds(1))), stamp));
        var provider = provider(false, List.of(new SyntheticGoogleHealthProvider.Gap(START, END)));
        var cycle = simulation.runCycle(provider, START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertThat(cycle.result().errors()).isEmpty();
        assertThat(cycle.rows()).isEqualTo(1);
        assertThat(cycle.finalizedDays()).isEqualTo(2);
        assertThat(simulation.jdbc.queryForObject("SELECT last_seen_run FROM google_health.heart_rate_sample", UUID.class)).isEqualTo(stamp);
        assertThat(simulation.jdbc.queryForObject("SELECT count(*) FROM google_health.history_audit "
                + "WHERE data_type='heart-rate' AND representation='HEART_RATE_SAMPLE' AND outcome='EMPTY'", Long.class)).isEqualTo(2);
        assertThat(cycle.result().statistics().stamped()).isZero();
        assertThat(cycle.result().statistics().deleted()).isZero();
    }

    @Test
    void namedDuplicateConflictIsRejectedAfterPersistedPageResumption() {
        var provider = provider(true, List.of());
        pauseAfterFirstPage(provider);
        var paused = simulation.runCycle(provider, START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertThat(paused.rows()).isEqualTo(100);
        UUID parent = simulation.jdbc.queryForObject(
                "SELECT parent_id FROM google_health.history_child WHERE page_token IS NOT NULL", UUID.class);
        var child = simulation.repository.children(parent).stream().filter(row -> row.pages() > 0).findFirst().orElseThrow();
        Map<String, String> committed = simulation.repository.queryFingerprints(child);
        assertThat(committed).hasSize(100);
        String firstName = simulation.jdbc.queryForObject(
                "SELECT google_resource_name FROM google_health.heart_rate_sample ORDER BY sample_time LIMIT 1", String.class);
        Instant duplicateTime = simulation.jdbc.queryForObject(
                "SELECT max(sample_time) FROM google_health.heart_rate_sample", Timestamp.class).toInstant().plusSeconds(60);
        provider.beforeRead(request -> { });
        provider.transformSamples(point -> {
            Instant time = Instant.parse(point.at("/heartRate/sampleTime/physicalTime").asText());
            if (time.equals(duplicateTime)) ((ObjectNode) point).put("name", firstName);
            return point;
        });
        var resumed = simulation.runCycle(provider, START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertThat(resumed.result().success()).isFalse();
        assertThat(simulation.repository.queryFingerprints(child)).isEqualTo(committed);
        assertThat(simulation.repository.isFinal(HistoricalHeartRateImportSimulation.ACCOUNT, HEART,
                END.minus(Duration.ofDays(1)).atOffset(ZoneOffset.UTC).toLocalDate())).isFalse();
        assertThat(simulation.jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample WHERE sample_time=?",
                Long.class, Timestamp.from(duplicateTime))).isZero();
    }

    @Test
    void namelessPagesNeedNoExtraFingerprintState() {
        var provider = provider(false, List.of());
        pauseAfterFirstPage(provider);
        var paused = simulation.runCycle(provider, START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertThat(paused.rows()).isEqualTo(100);
        assertThat(simulation.jdbc.queryForObject("SELECT query_fingerprints::text FROM google_health.history_child "
                + "WHERE page_token IS NOT NULL", String.class)).isEqualTo("{}");
        assertThat(simulation.jdbc.queryForObject(
                "SELECT count(*) FROM google_health.heart_rate_sample WHERE last_seen_run IS NOT NULL", Long.class)).isZero();
        provider.beforeRead(request -> { });
        var resumed = simulation.runCycle(provider, START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertThat(resumed.result().errors()).isEmpty();
        assertThat(resumed.rows()).isEqualTo(provider.expectedSamples());
    }

    @Test
    void differentPageSizesProduceTheSameRecordsAndDayMarks() {
        var dataset = new SyntheticGoogleHealthProvider.Dataset(START, END, Duration.ofSeconds(30), 2, 42,
                ZoneOffset.UTC, false, List.of(new SyntheticGoogleHealthProvider.Gap(
                START.plus(Duration.ofHours(23)), START.plus(Duration.ofHours(25)))));
        var wide = simulation.runCycle(new SyntheticGoogleHealthProvider(dataset, 1_500),
                START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertThat(wide.result().errors()).isEmpty();
        var records = simulation.jdbc.queryForList(
                "SELECT identity_key,fingerprint FROM google_health.heart_rate_sample ORDER BY identity_key");
        var marks = simulation.jdbc.queryForList("SELECT data_type,representation,native_day,outcome "
                + "FROM google_health.history_audit WHERE outcome IN ('DATA','EMPTY') ORDER BY data_type,representation,native_day");
        simulation.reset();
        var narrow = simulation.runCycle(new SyntheticGoogleHealthProvider(dataset, 100),
                START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertThat(narrow.result().errors()).isEmpty();
        assertThat(narrow.rows()).isEqualTo(wide.rows());
        assertThat(narrow.finalizedDays()).isEqualTo(wide.finalizedDays());
        assertThat(simulation.jdbc.queryForList(
                "SELECT identity_key,fingerprint FROM google_health.heart_rate_sample ORDER BY identity_key")).isEqualTo(records);
        assertThat(simulation.jdbc.queryForList("SELECT data_type,representation,native_day,outcome "
                + "FROM google_health.history_audit WHERE outcome IN ('DATA','EMPTY') ORDER BY data_type,representation,native_day")).isEqualTo(marks);
    }

    @Test
    void rejectedCheckpointRollsBackItsBatchAndValidationState() {
        var day = START.atOffset(ZoneOffset.UTC).toLocalDate();
        var bounds = new SyncWindowPolicy.Bounds(HEART.windowTimeDomain(), START, START.plus(Duration.ofDays(1)));
        simulation.transaction(() -> simulation.repository.createWork(HistoricalHeartRateImportSimulation.ACCOUNT,
                HEART, day, "HISTORY", List.of(bounds)));
        var parent = simulation.repository.dueWork(HistoricalHeartRateImportSimulation.ACCOUNT, HEART, "HISTORY", END).orElseThrow();
        var child = simulation.repository.children(parent.id()).getFirst();
        Map<String, String> fingerprints = Map.of("a".repeat(64), "a".repeat(64));
        simulation.transaction(() -> {
            simulation.repository.guardWork(parent, child);
            simulation.repository.importHistoricalHeartRate(List.of(sample('a', START.plusSeconds(1))));
            simulation.repository.checkpointHistoricalChild(child, "next", 1, 1, 1, 1, fingerprints);
        });
        assertThatThrownBy(() -> simulation.transaction(() -> {
            simulation.repository.guardWork(parent, child);
            simulation.repository.importHistoricalHeartRate(List.of(sample('b', START.plusSeconds(2))));
            simulation.repository.checkpointHistoricalChild(child, "next", 1, 1, 1, 2, Map.of());
        })).isInstanceOf(SyncValidationException.class);
        assertThat(simulation.jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample", Long.class)).isEqualTo(1);
        assertThat(simulation.repository.queryFingerprints(child)).isEqualTo(fingerprints);
        assertThat(simulation.repository.children(parent.id()).getFirst().pages()).isEqualTo(1);
    }

    @Test
    void finalizationFailureLeavesTheDayUnmarkedUntilItsWorkCompletes() {
        var day = START.atOffset(ZoneOffset.UTC).toLocalDate();
        var bounds = new SyncWindowPolicy.Bounds(HEART.windowTimeDomain(), START, START.plus(Duration.ofDays(1)));
        simulation.transaction(() -> simulation.repository.createWork(HistoricalHeartRateImportSimulation.ACCOUNT,
                HEART, day, "HISTORY", SyncWindowPolicy.cells(HEART, bounds)));
        var work = simulation.repository.dueWork(HistoricalHeartRateImportSimulation.ACCOUNT, HEART, "HISTORY", END).orElseThrow();
        for (var child : simulation.repository.children(work.id())) {
            simulation.transaction(() -> simulation.repository.checkpointHistoricalChild(child, null, 0, 0, 0, 1, Map.of()));
        }
        assertThatThrownBy(() -> simulation.transaction(() -> {
            simulation.repository.completeHistoricalImport(work);
            throw new IllegalStateException("Synthetic finalization rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(simulation.repository.isFinal(HistoricalHeartRateImportSimulation.ACCOUNT, HEART, day)).isFalse();
        assertThat(simulation.repository.dueWork(HistoricalHeartRateImportSimulation.ACCOUNT, HEART, "HISTORY", END)).isPresent();
        simulation.transaction(() -> simulation.repository.completeHistoricalImport(work));
        assertThat(simulation.repository.isFinal(HistoricalHeartRateImportSimulation.ACCOUNT, HEART, day)).isTrue();
    }

    private static SyntheticGoogleHealthProvider provider(boolean names, List<SyntheticGoogleHealthProvider.Gap> gaps) {
        var dataset = new SyntheticGoogleHealthProvider.Dataset(START, END, Duration.ofMinutes(1), 2, 42,
                ZoneOffset.UTC, names, gaps);
        return new SyntheticGoogleHealthProvider(dataset, 100);
    }

    private static void pauseAfterFirstPage(SyntheticGoogleHealthProvider provider) {
        provider.beforeRead(request -> {
            if (request.purpose().equals("HISTORICAL_FINAL_CHECK") && request.policy().mode() == HEART.mode()
                && request.pageToken() != null) throw new GoogleHealthProvider.QuotaPause();
        });
    }

    private static HeartRateSample sample(char key, Instant time) {
        String identity = Character.toString(key).repeat(64);
        return new HeartRateSample(null, time, time.toString(), null, null, 61,
                null, null, identity, identity, null, "{}");
    }
}
