package com.x8ing.mtl.server.mtlserver.web.services.logs;

import com.x8ing.mtl.server.mtlserver.MtlServerApplication;
import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.config.SystemLogProperties;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.SystemLog;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogsRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.testsupport.NoScheduledJobs;
import com.x8ing.mtl.server.mtlserver.web.security.JwtUtil;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfSystemProperty(named = "mtl.test.logs.jdbc-url", matches = "jdbc:postgresql:.*")
@SpringBootTest(classes = MtlServerApplication.class, properties = {"mtl.planner.enabled=false", "mtl.google-health.enabled=false", "mtl.garmin-sync.enabled=false", "mtl.user.login=synthetic", "mtl.user.password=synthetic-password"})
@AutoConfigureMockMvc
@Import(NoScheduledJobs.class)
class SystemLogDatabaseIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DataSource dataSource;
    @Autowired
    ObjectMapper json;
    @Autowired
    SystemLogsRepository repository;
    @Autowired
    SystemLogService service;
    @Autowired
    MtlAppProperties appProperties;
    @Autowired
    SystemLogProperties logProperties;
    @Autowired
    JwtUtil jwt;
    @MockitoBean
    GPXDirectoryWatcherService watcher;
    @MockitoBean
    MediaIndexerService media;
    private String token;
    private String loginRef;
    private final String build = "synthetic-diagnostics-" + UUID.randomUUID();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("mtl.test.logs.jdbc-url"));
    }

    @BeforeEach
    void authenticate() throws Exception {
        authenticate("synthetic");
    }

    private void authenticate(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", "synthetic-password"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = json.readTree(response).path("token").asText();
        loginRef = jwt.getClientLoginRefFromToken(token);
    }

    @AfterEach
    void cleanFixtures() {
        appProperties.setLogViewerEnabled(true);
        logProperties.setCleanupBatchSize(SystemLogProperties.DEFAULT_CLEANUP_BATCH_SIZE);
        jdbc.update("DELETE FROM system_log WHERE build_id=?", build);
    }

    @Test
    void authenticatedRequestRoundTripsStackContextClassificationAndLogin() throws Exception {
        String stack = "Error: password=synthetic-secret\n at a (app.js:1:2)\n at b (app.js:7:4)";
        mvc.perform(post("/api/logs/client").servletPath("/api/logs/client").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "severity", "ERROR", "topic1", build, "message", "Synthetic error", "buildId", build,
                                "stackTrace", stack, "context", Map.of("method", "get", "status", 503, "token", "private")))))
                .andExpect(status().isNoContent());
        Long id = jdbc.queryForObject("SELECT id FROM system_log WHERE build_id=?", Long.class, build);
        String fingerprint = StackFingerprint.of(stack.replace("synthetic-secret", "<redacted>"));
        assertThat(jdbc.queryForObject("SELECT stack_fingerprint FROM system_log WHERE id=?", String.class, id)).isEqualTo(fingerprint);
        assertThat(jdbc.queryForObject("SELECT jsonb_typeof(context_json) FROM system_log WHERE id=?", String.class, id)).isEqualTo("object");
        mvc.perform(get("/api/admin/system-logs/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.event.source").value("CLIENT"))
                .andExpect(jsonPath("$.event.severity").value("ERROR"))
                .andExpect(jsonPath("$.event.stackFingerprint").value(fingerprint))
                .andExpect(jsonPath("$.clientLoginRef").value(loginRef))
                .andExpect(jsonPath("$.stackTrace").value(stack.replace("synthetic-secret", "<redacted>")))
                .andExpect(jsonPath("$.context.status").value(503)).andExpect(jsonPath("$.context.token").doesNotExist());
        mvc.perform(get("/api/admin/system-logs").header("Authorization", "Bearer " + token).param("topic1", build))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].stackFingerprint").value(fingerprint));
    }

    @Test
    void delayedReportsKeepEventTimeAndHistoricalSessionAndAreDeduplicatedAfterAnotherLogin() throws Exception {
        String originalLoginRef = loginRef;
        String eventId = UUID.randomUUID().toString();
        String body = json.writeValueAsString(Map.of("message", "Delayed synthetic", "buildId", build,
                "clientEventId", eventId, "occurredAt", "2026-10-01T12:00:00Z", "lastOccurredAt", "2026-10-01T12:00:20Z",
                "occurrenceCount", 7, "originalClientLoginRef", originalLoginRef, "topic1", build));
        authenticate();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/logs/client").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM system_log WHERE build_id=?", Integer.class, build)).isEqualTo(1);
        Long id = jdbc.queryForObject("SELECT id FROM system_log WHERE build_id=?", Long.class, build);
        mvc.perform(get("/api/admin/system-logs/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.event.occurredAt").value("2026-10-01T12:00:00Z"))
                .andExpect(jsonPath("$.event.occurrenceCount").value(7))
                .andExpect(jsonPath("$.originalClientLoginRef").value(originalLoginRef))
                .andExpect(jsonPath("$.clientLoginRef").value(loginRef));
        assertThat(repository.findById(id).orElseThrow().getCreateDate().toInstant())
                .isAfter(java.time.Instant.parse("2026-10-01T12:00:20Z"));
        mvc.perform(get("/api/admin/system-logs").header("Authorization", "Bearer " + token).param("topic1", build)
                        .param("from", "2026-10-01T00:00:00Z").param("to", "2026-10-02T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void delayedReportsAcceptCaseVariantsAndInvalidHistoricalSessionsDoNotPreventLaterReports() throws Exception {
        authenticate("SyNtHeTiC");
        String originalLoginRef = loginRef;
        authenticate("SYNTHETIC");
        String delayed = json.writeValueAsString(Map.of("message", "Delayed case variant", "buildId", build,
                "originalClientLoginRef", originalLoginRef, "clientEventId", UUID.randomUUID().toString()));
        mvc.perform(post("/api/logs/client").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(delayed)).andExpect(status().isNoContent());
        String invalid = json.writeValueAsString(Map.of("message", "Invalid historical session", "buildId", build,
                "originalClientLoginRef", "missing-login"));
        mvc.perform(post("/api/logs/client").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isForbidden());
        String current = json.writeValueAsString(Map.of("message", "Current session", "buildId", build,
                "originalClientLoginRef", loginRef));
        mvc.perform(post("/api/logs/client").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(current)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM system_log WHERE build_id=?", Integer.class, build)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT original_client_login_ref FROM system_log WHERE build_id=? AND message=?",
                String.class, build, "Delayed case variant")).isEqualTo(originalLoginRef);
    }

    @Test
    void concurrentRetriesInsertOneRow() throws Exception {
        UUID eventId = UUID.randomUUID();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var attempts = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int attempt = 0; attempt < 2; attempt++) {
                attempts.add(executor.submit(() -> {
                    var event = new SystemLog();
                    event.setClientEventId(eventId);
                    event.setMessage("Synthetic concurrent retry");
                    event.setBuildId(build);
                    return service.saveClientLog(event);
                }));
            }
            assertThat(java.util.List.of(attempts.get(0).get(), attempts.get(1).get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM system_log WHERE client_event_id=?", Integer.class, eventId)).isEqualTo(1);
    }

    @Test
    void omittedAndNullSeverityAndStackRemainValidAndServerServiceAcceptsNullSource() throws Exception {
        for (String optional : new String[]{"", ",\"severity\":null,\"stackTrace\":null", ",\"severity\":\"WARN\"", ",\"severity\":\"ERROR\",\"stackTrace\":null"}) {
            mvc.perform(post("/api/logs/client").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"Synthetic\",\"buildId\":\"" + build + "\"" + optional + "}"))
                    .andExpect(status().isNoContent());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM system_log WHERE build_id=? AND stack_trace IS NULL", Integer.class, build)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM system_log WHERE build_id=? AND severity IS NULL", Integer.class, build)).isEqualTo(2);
        var event = new SystemLog();
        event.setBuildId(build);
        event.setMessage("Unspecified synthetic record");
        service.saveLog(event);
        var stored = repository.findById(event.getId()).orElseThrow();
        assertThat(stored.getSource()).isNull();
        assertThat(stored.getSeverity()).isNull();
        assertThat(stored.getTopic1()).isNull();
    }

    @Test
    void invalidAndExpiredAuthenticationInsertNoClientRows() throws Exception {
        String body = json.writeValueAsString(Map.of("message", "Synthetic", "buildId", build));
        mvc.perform(post("/api/logs/client").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        String expired = jwt.generateToken("synthetic", loginRef, new Date(1), new Date(2));
        mvc.perform(post("/api/logs/client").header("Authorization", "Bearer " + expired)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM system_log WHERE build_id=?", Integer.class, build)).isZero();
    }

    @Test
    void filteringPaginationOrderingAndViewerDisableWorkWithoutDisablingIngestion() throws Exception {
        for (int index = 0; index < 3; index++) {
            var event = new SystemLog();
            event.setBuildId(build);
            event.setTopic1(build);
            event.setMessage("Synthetic " + index);
            if (index > 0) {
                event.setSource(SystemLog.Source.CLIENT);
                event.setSeverity(SystemLog.Severity.WARN);
            }
            service.saveLog(event);
        }
        jdbc.update("UPDATE system_log SET create_date=TIMESTAMP '2026-01-01 12:00:00' WHERE build_id=?", build);
        mvc.perform(get("/api/admin/system-logs").header("Authorization", "Bearer " + token)
                        .param("topic1", build).param("size", "2").param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/admin/system-logs").header("Authorization", "Bearer " + token)
                        .param("topic1", build).param("source", "CLIENT").param("severity", "WARN")
                        .param("from", "2026-01-01T00:00:00Z").param("to", "2026-01-02T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        var ordered = jdbc.queryForList("SELECT id FROM system_log WHERE build_id=? ORDER BY id DESC", Long.class, build);
        mvc.perform(get("/api/admin/system-logs").header("Authorization", "Bearer " + token).param("topic1", build).param("size", "2"))
                .andExpect(jsonPath("$.items[0].id").value(ordered.getFirst().intValue()));
        mvc.perform(get("/api/admin/system-logs").header("Authorization", "Bearer " + token).param("size", "201"))
                .andExpect(status().isBadRequest());
        appProperties.setLogViewerEnabled(false);
        mvc.perform(get("/api/admin/system-logs").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/system-logs/{id}", ordered.getFirst()).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/logs/client").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("message", "Synthetic", "buildId", build)))).andExpect(status().isNoContent());
    }

    @Test
    void retentionDeletesOnlyExpiredRowsInBoundedBatches() {
        for (int index = 0; index < 4; index++) {
            var event = new SystemLog();
            event.setMessage("Synthetic retention");
            event.setBuildId(build);
            service.saveLog(event);
            if (index < 3) jdbc.update("UPDATE system_log SET create_date=TIMESTAMP '2000-01-01 00:00:00' WHERE id=?", event.getId());
        }
        logProperties.setCleanupBatchSize(2);
        service.cleanupExpired();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM system_log WHERE build_id=?", Integer.class, build)).isEqualTo(2);
        service.cleanupExpired();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM system_log WHERE build_id=?", Integer.class, build)).isEqualTo(1);
    }

    @Test
    void additiveLiquibaseChangePreservesPopulatedLegacyRowsAndTimestampType() throws Exception {
        String schema = "synthetic_logs_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            jdbc.execute("CREATE TABLE " + schema + ".system_log(id BIGSERIAL PRIMARY KEY, create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, topic1 VARCHAR(255) NOT NULL, topic2 VARCHAR(255), topic3 VARCHAR(255), message TEXT, detail TEXT)");
            jdbc.execute("INSERT INTO " + schema + ".system_log(id,create_date,topic1,topic2,message,detail) VALUES(42,TIMESTAMP '2000-01-01 12:34:56','SECURITY','legacy','Synthetic legacy','Existing detail')");
            for (String change : new String[]{"106", "107", "109"}) {
                try (var connection = dataSource.getConnection()) {
                    var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                    database.setDefaultSchemaName(schema);
                    try (var migration = new Liquibase("db/changelog/changes/" + change + ".xml", new ClassLoaderResourceAccessor(), database)) {
                        migration.update(new Contexts(), new LabelExpression());
                    }
                }
            }
            var legacy = jdbc.queryForMap("SELECT * FROM " + schema + ".system_log WHERE id=42");
            assertThat(legacy).containsEntry("topic1", "SECURITY").containsEntry("message", "Synthetic legacy").containsEntry("detail", "Existing detail");
            for (String column : new String[]{"source", "severity", "stack_trace", "stack_fingerprint", "context_json", "build_id", "client_login_ref", "occurred_at", "last_occurred_at", "occurrence_count", "client_event_id", "original_client_login_ref"})
                assertThat(legacy.get(column)).isNull();
            assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname=? AND indexname='system_log_stack_fingerprint_ix'", String.class, schema))
                    .contains("(stack_fingerprint)").doesNotContain("UNIQUE");
            assertThat(legacy.get("create_date").toString()).isEqualTo("2000-01-01 12:34:56.0");
            assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema=? AND table_name='system_log' AND column_name='create_date'", String.class, schema)).isEqualTo("timestamp without time zone");
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
