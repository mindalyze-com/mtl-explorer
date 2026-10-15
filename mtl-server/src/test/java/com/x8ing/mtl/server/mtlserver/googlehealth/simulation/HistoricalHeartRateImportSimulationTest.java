package com.x8ing.mtl.server.mtlserver.googlehealth.simulation;

import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiException;
import com.x8ing.mtl.server.mtlserver.googlehealth.provider.GoogleHealthProvider;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MTL_HEALTH_TEST_DB_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistoricalHeartRateImportSimulationTest {
    private static final DataTypePolicy.Policy HEART = DataTypePolicy.all().stream()
            .filter(policy -> policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE).findFirst().orElseThrow();
    private static final Instant START = Instant.parse("2025-12-30T00:00:00Z");
    private static final Instant END = Instant.parse("2026-01-01T00:00:00Z");
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
            if (!Boolean.getBoolean("mtl.health.simulation.keep-data")) simulation.clear();
        } finally {
            simulation.close();
        }
    }

    @Test
    void standardThreeSecondDataRunsThroughRealMappingStorageAndFinalization() {
        var provider = SyntheticGoogleHealthProvider.standard();
        var cycle = simulation.runCycle(provider, START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertSuccess(cycle);
        assertThat(cycle.finalizedDays()).isEqualTo(2);
        assertThat(cycle.rows()).isEqualTo(provider.expectedSamples(START, END));
        assertThat(cycle.providerTime()).isPositive();
        assertThat(cycle.transactionTime()).isPositive();
        assertThat(cycle.readsByPurpose()).containsKey("HISTORICAL_FINAL_CHECK");
        var rerun = simulation.runCycle(provider, START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertThat(rerun.rows()).isEqualTo(cycle.rows());
        assertThat(rerun.result().statistics().stamped()).isZero();
    }

    @Test
    void quotaPauseKeepsCheckpointAndANewWorkerResumesItsPageToken() {
        var provider = smallProvider(false);
        pauseOnSecondHistoricalPage(provider);
        var paused = simulation.runCycle(provider, START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertThat(paused.result().success()).isFalse();
        assertThat(paused.rows()).isEqualTo(100);
        assertThat(simulation.jdbc.queryForObject("SELECT count(*) FROM google_health.history_child WHERE page_token IS NOT NULL", Integer.class)).isEqualTo(1);
        assertThat(simulation.jdbc.queryForObject("SELECT sum(attempts) FROM google_health.history_work", Long.class)).isZero();
        AtomicReference<String> resumedToken = new AtomicReference<>();
        provider.beforeRead(request -> {
            if (historicalHeart(request)) resumedToken.compareAndSet(null, request.pageToken());
        });
        var resumed = simulation.runCycle(provider, START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertThat(resumedToken.get()).isNotBlank();
        assertSuccess(resumed);
        assertThat(resumed.rows()).isEqualTo(provider.expectedSamples());
    }

    @Test
    void anExpiredHistoricalTokenSplitsTheWindowAndCompletesWithoutLosingRows() {
        var provider = smallProvider(false);
        pauseOnSecondHistoricalPage(provider);
        simulation.runCycle(provider, START.atOffset(ZoneOffset.UTC).toLocalDate());
        AtomicBoolean rejected = new AtomicBoolean();
        provider.beforeRead(request -> {
            if (historicalHeart(request) && request.pageToken() != null && rejected.compareAndSet(false, true)) {
                throw new ApiException("Synthetic expired token", 400, false, ApiException.Reason.INVALID_PAGE_TOKEN);
            }
        });
        var cycle = simulation.runCycle(provider, START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertThat(rejected).isTrue();
        assertSuccess(cycle);
        assertThat(cycle.rows()).isEqualTo(provider.expectedSamples());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reloadAppliesCorrectionsAndRemovesMissingSamplesWithBothIdentityPolicies(boolean names) {
        var provider = smallProvider(names);
        var initial = simulation.runCycle(provider, START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertSuccess(initial);
        Instant removalEnd = START.plus(Duration.ofHours(2));
        long removed = provider.expectedSamples(START, removalEnd);
        provider.transformSamples(point -> {
            var heart = (tools.jackson.databind.node.ObjectNode) point.path("heartRate");
            Instant time = Instant.parse(heart.at("/sampleTime/physicalTime").asText());
            if (time.isBefore(removalEnd)) return null;
            heart.put("beatsPerMinute", Integer.toString(Integer.parseInt(heart.path("beatsPerMinute").asText()) + 5));
            return point;
        });
        simulation.transaction(() -> simulation.repository.reloadDays(HistoricalHeartRateImportSimulation.ACCOUNT, List.of(HEART),
                START.atOffset(ZoneOffset.UTC).toLocalDate(), END.atOffset(ZoneOffset.UTC).toLocalDate(), END.atOffset(ZoneOffset.UTC).toLocalDate(), false));
        var corrected = simulation.runCycle(provider, START.atOffset(ZoneOffset.UTC).toLocalDate());
        assertSuccess(corrected);
        assertThat(corrected.rows()).isEqualTo(initial.rows() - removed);
        assertThat(corrected.result().statistics().deleted()).isPositive();
        assertThat(simulation.jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample WHERE sample_time < ?", Long.class, Timestamp.from(removalEnd))).isZero();
    }

    private static SyntheticGoogleHealthProvider smallProvider(boolean names) {
        var gap = new SyntheticGoogleHealthProvider.Gap(START.plus(Duration.ofHours(22)), START.plus(Duration.ofHours(27)));
        var dataset = new SyntheticGoogleHealthProvider.Dataset(START, END, Duration.ofMinutes(1), 2, 42,
                ZoneOffset.UTC, names, List.of(gap));
        return new SyntheticGoogleHealthProvider(dataset, 100);
    }

    private static boolean historicalHeart(SyntheticGoogleHealthProvider.Request request) {
        return request.purpose().equals("HISTORICAL_FINAL_CHECK") && request.policy().mode() == HEART.mode();
    }

    private static void pauseOnSecondHistoricalPage(SyntheticGoogleHealthProvider provider) {
        provider.beforeRead(request -> {
            if (historicalHeart(request) && request.pageToken() != null) throw new GoogleHealthProvider.QuotaPause();
        });
    }

    private static void assertSuccess(HistoricalHeartRateImportSimulation.Cycle cycle) {
        assertThat(cycle.result().errors()).isEmpty();
        assertThat(cycle.result().success()).isTrue();
    }
}
