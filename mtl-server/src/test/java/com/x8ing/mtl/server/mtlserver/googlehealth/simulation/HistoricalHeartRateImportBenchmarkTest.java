package com.x8ing.mtl.server.mtlserver.googlehealth.simulation;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A complete fixed workload, with a one-minute limit and no automatic time-based dataset resizing.
 */
@EnabledIfEnvironmentVariable(named = "MTL_HEALTH_TEST_DB_URL", matches = ".+")
@EnabledIfSystemProperty(named = "mtl.health.simulation.benchmark", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistoricalHeartRateImportBenchmarkTest {
    private static final Duration IMPORT_BUDGET = Duration.ofSeconds(57);
    private HistoricalHeartRateImportSimulation simulation;

    @BeforeAll
    void connect() throws Exception {
        simulation = new HistoricalHeartRateImportSimulation();
    }

    @AfterAll
    void close() {
        if (simulation == null) return;
        try {
            if (!Boolean.getBoolean("mtl.health.simulation.keep-data")) simulation.clearForBenchmark();
        } finally {
            simulation.close();
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void benchmarkHistoricalHeartRateImport() throws Exception {
        int historyDays = Integer.getInteger("mtl.health.simulation.history-days", SyntheticGoogleHealthProvider.BENCHMARK_HISTORY_DAYS);
        var provider = SyntheticGoogleHealthProvider.benchmark(historyDays);
        simulation.resetForBenchmark();
        var cycle = simulation.runCycle(provider, provider.dataset().start().atOffset(ZoneOffset.UTC).toLocalDate(), IMPORT_BUDGET);
        long expectedRows = provider.expectedSamples();
        boolean completed = cycle.result().success() && cycle.finalizedDays() == historyDays && cycle.rows() == expectedRows;
        var report = new Report(provider.workloadFingerprint(), provider.dataset().start().toString(), provider.dataset().end().toString(),
                historyDays, provider.dataset().seed(), provider.dataset().sampleInterval().toSeconds(), provider.pageSize(),
                provider.dataset().gaps().size(), expectedRows, cycle.rows(), cycle.finalizedDays(), cycle.elapsed().toMillis(),
                cycle.providerTime().toMillis(), cycle.transactionTime().toMillis(), cycle.commits(), cycle.result().statistics().requests().attempts(), completed);
        String json = new ObjectMapper().writeValueAsString(report);
        System.out.println("MTL Explorer heart-rate benchmark: " + json);
        String destination = System.getProperty("mtl.health.simulation.report");
        if (destination != null) Files.writeString(Path.of(destination), json + System.lineSeparator());
        assertThat(cycle.result().errors()).isEmpty();
        assertThat(cycle.finalizedDays()).as("Every historical day must complete; a partial import is not a benchmark result").isEqualTo(historyDays);
        assertThat(cycle.rows()).isEqualTo(expectedRows);
        assertThat(cycle.elapsed()).isLessThan(Duration.ofMinutes(1));
    }

    record Report(String workloadFingerprint, String start, String endExclusive, int historyDays,
                  long seed, long sampleSeconds, int pageSize, int gaps, long expectedRows, long rows,
                  long finalizedDays, long elapsedMillis, long providerMillis, long transactionMillis,
                  long commits, long requests, boolean completed) {
    }
}
