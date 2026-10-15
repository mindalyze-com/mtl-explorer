package com.x8ing.mtl.server.mtlserver.googlehealth.simulation;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.demo.DemoHealthAccess;
import com.x8ing.mtl.server.mtlserver.googlehealth.demo.DemoHealthProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.demo.DemoHealthRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.demo.DemoHealthWorker;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.RecordRow;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.TrackHealthRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.GHGrouping;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.GHGroupedMetrics;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.HealthGroupingService;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.HealthQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in read measurements against a retained, disposable synthetic mirror. */
@EnabledIfEnvironmentVariable(named = "MTL_HEALTH_TEST_DB_URL", matches = ".+")
@EnabledIfSystemProperty(named = "mtl.health.read.benchmark", matches = "true")
class HealthReadBenchmarkTest {
    private static final int PAGE_SIZE_WITH_LOOKAHEAD = 51;
    private static final int REPETITIONS = 5;
    private static final String ZONE = "Europe/Zurich";
    private static final Instant END = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDate END_DATE = LocalDate.of(2026, 1, 1);

    @Test
    @EnabledIfSystemProperty(named = "mtl.health.read.seed-demo", matches = "true")
    void seedDailyDemoRecords() throws Exception {
        try (var simulation = new HistoricalHeartRateImportSimulation()) {
            simulation.jdbc.update("UPDATE google_health.connection SET google_user_id=NULL, "
                    + "refresh_token_ciphertext=NULL,state='DISCONNECTED'");
            simulation.jdbc.update("UPDATE google_health.data_source SET platform='SYNTHETIC_DEMO'");
            var settings = new DemoHealthProperties();
            settings.setEnabled(true);
            var app = new MtlAppProperties();
            app.setDemoMode(true);
            var repository = new DemoHealthRepository(simulation.jdbc, simulation.repository, new ObjectMapper(), settings);
            var worker = new DemoHealthWorker(new DemoHealthAccess(app, settings), settings, repository);
            // An older period generates real demo rollups without adding a second dense sample timeline.
            for (LocalDate day = END_DATE.minusDays(448); day.isBefore(END_DATE); day = day.plusDays(1)) {
                LocalDate date = day;
                simulation.transaction(() -> assertThat(worker.day(date, END_DATE.plusYears(1))).isTrue());
            }
            assertThat(simulation.jdbc.queryForObject("SELECT count(*) FROM google_health.data_point", Long.class))
                    .isEqualTo(448L * 7);
        }
    }

    @Test
    void measureRetainedSyntheticMirror() throws Exception {
        Path output = Path.of(System.getProperty("mtl.health.read.report"));
        ObjectMapper json = new ObjectMapper();
        try (var simulation = new HistoricalHeartRateImportSimulation()) {
            var capture = new CapturingDataSource(simulation.jdbc.getDataSource());
            var jdbc = new JdbcTemplate(capture);
            var repository = new MirrorRepository(jdbc, json);
            var grouping = new HealthGroupingService(repository, json);
            var queries = new HealthQueryService(new ConnectorProperties(), repository, null, null, null, json, null);
            var tracks = new TrackHealthRepository(jdbc);
            long samples = jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample", Long.class);
            long points = jdbc.queryForObject("SELECT count(*) FROM google_health.data_point", Long.class);
            assertThat(samples).isPositive();
            var scenarios = new LinkedHashMap<String, Supplier<Object>>();
            scenarios.put("mirror-summary", repository::findMirrorSummary);
            scenarios.put("type-summaries", repository::findTypeSummaries);
            scenarios.put("source-counts", repository::findDataSourceSummaries);
            scenarios.put("records-all-first", () -> repository.findRecords(null, null, null, null,
                    null, null, null, null, PAGE_SIZE_WITH_LOOKAHEAD));
            scenarios.put("records-heart-first", () -> repository.findRecords("heart-rate", "HEART_RATE_SAMPLE",
                    null, null, null, null, null, null, PAGE_SIZE_WITH_LOOKAHEAD));
            scenarios.put("records-weight", () -> repository.findRecords("weight", null, null, null,
                    null, null, null, null, PAGE_SIZE_WITH_LOOKAHEAD));
            scenarios.put("records-source-8", () -> repository.findRecords(null, null, null, null,
                    8L, null, null, null, PAGE_SIZE_WITH_LOOKAHEAD));
            scenarios.put("records-sleep-source-8", () -> repository.findRecords("sleep", "RAW", null, null,
                    8L, null, null, null, PAGE_SIZE_WITH_LOOKAHEAD));
            var firstPage = repository.findRecords(null, null, null, null, null, null, null, null,
                    PAGE_SIZE_WITH_LOOKAHEAD);
            RecordRow cursor = firstPage.get(49);
            scenarios.put("records-next", () -> repository.findRecords(null, null, null, null, null,
                    cursor.recordTime(), cursor.kind() == RecordRow.RecordKind.DATA_POINT ? 0 : 1,
                    cursor.id(), PAGE_SIZE_WITH_LOOKAHEAD));
            scenarios.put("metrics-latest-day", () -> repository.findMetricRecords(END.minusSeconds(86_400), END));
            scenarios.put("metrics-30-days", () -> repository.findMetricRecords(END.minusSeconds(30 * 86_400L), END));
            scenarios.put("grouped-30-days", () -> repository.findGroupedMetricRecords(END_DATE.minusDays(30), END_DATE, ZONE));
            scenarios.put("grouped-two-years", () -> repository.findGroupedMetricRecords(END_DATE.minusYears(2), END_DATE, ZONE));
            scenarios.put("heart-buckets-one-day", () -> repository.findHeartRateBuckets(END.minusSeconds(86_400), END, 900));
            scenarios.put("heart-buckets-30-days", () -> repository.findHeartRateBuckets(END.minusSeconds(30 * 86_400L), END, 900));
            scenarios.put("heart-days-30-days", () -> repository.findHeartRateDays(END.minusSeconds(30 * 86_400L), END, ZONE));
            scenarios.put("overview-auto-30-days", () -> grouping.grouped(END_DATE.minusDays(30).toString(),
                    END_DATE.toString(), GHGrouping.AUTO, ZONE));
            scenarios.put("overview-day-30-days", () -> grouping.grouped(END_DATE.minusDays(30).toString(),
                    END_DATE.toString(), GHGrouping.DAY, ZONE));
            scenarios.put("latest-values-one-day", () -> queries.metrics(END.minusSeconds(86_400), END));
            scenarios.put("track-summary-two-hours", () -> tracks.findHeartRateSummary(END.minusSeconds(7_200), END));
            scenarios.put("catalogue-refresh", () -> new TreeMap<>(Map.of("mirror", repository.findMirrorSummary(),
                    "types", repository.findTypeSummaries(), "sources", repository.findDataSourceSummaries())));
            var results = new LinkedHashMap<String, Object>();
            for (var scenario : scenarios.entrySet()) {
                var millis = new ArrayList<Double>();
                String checksum = null;
                String sql = null;
                int resultRows = 0;
                for (int repeat = 0; repeat < REPETITIONS; repeat++) {
                    long started = System.nanoTime();
                    Object result = scenario.getValue().get();
                    millis.add((System.nanoTime() - started) / 1_000_000d);
                    sql = capture.lastSql;
                    if (result instanceof GHGroupedMetrics grouped) {
                        assertThat(grouped.series()).extracting(series -> series.dataType())
                                .contains("steps", "sleep", "heart-rate");
                    }
                    resultRows = result instanceof List<?> list ? list.size() : 1;
                    String nextChecksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(json.writeValueAsBytes(result)));
                    if (checksum != null) assertThat(nextChecksum).as(scenario.getKey()).isEqualTo(checksum);
                    checksum = nextChecksum;
                }
                var sorted = millis.stream().skip(1).sorted().toList();
                var measurement = new LinkedHashMap<String, Object>();
                measurement.put("milliseconds", millis);
                measurement.put("firstMillis", millis.getFirst());
                measurement.put("repeatMedianMillis", (sorted.get(1) + sorted.get(2)) / 2);
                measurement.put("resultRows", resultRows);
                measurement.put("resultSha256", checksum);
                measurement.put("sql", sql);
                measurement.put("plan", json.readTree(jdbc.queryForObject(
                        "EXPLAIN (ANALYZE, BUFFERS, SETTINGS, TIMING OFF, FORMAT JSON) " + sql, String.class)));
                results.put(scenario.getKey(), measurement);
                System.out.println("Health read " + scenario.getKey() + ": " + millis);
                // Preserve completed measurements if a later scenario fails.
                Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                        "samples", samples, "points", points, "repetitions", REPETITIONS,
                        "settings", jdbc.queryForList("SELECT name,setting,unit FROM pg_settings WHERE name IN "
                                + "('shared_buffers','work_mem','jit','effective_cache_size','max_parallel_workers_per_gather')"),
                        "results", results)));
            }
        }
    }

    /** Save the driver's rendered SQL after binding, including dynamic record filters. */
    private static final class CapturingDataSource extends DelegatingDataSource {
        private String lastSql;

        CapturingDataSource(DataSource target) {
            super(target);
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        try {
                            Object result = method.invoke(connection, args);
                            if (method.getName().equals("prepareStatement") && result instanceof PreparedStatement statement) {
                                return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                                        new Class<?>[]{PreparedStatement.class}, (statementProxy, statementMethod, statementArgs) -> {
                                            try {
                                                Object value = statementMethod.invoke(statement, statementArgs);
                                                if (statementMethod.getName().equals("executeQuery")) {
                                                    lastSql = statement.unwrap(org.postgresql.PGStatement.class).toString();
                                                }
                                                return value;
                                            } catch (InvocationTargetException exception) {
                                                throw exception.getCause();
                                            }
                                        });
                            }
                            return result;
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
        }
    }
}
