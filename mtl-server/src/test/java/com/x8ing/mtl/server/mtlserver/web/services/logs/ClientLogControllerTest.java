package com.x8ing.mtl.server.mtlserver.web.services.logs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.SystemLog;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.WebClientLogin;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.WebClientLoginRepository;
import com.x8ing.mtl.server.mtlserver.web.security.ClientDiagnosticRequestSizeFilter;
import com.x8ing.mtl.server.mtlserver.web.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientLogControllerTest {
    private final SystemLogService service = mock(SystemLogService.class);
    private final WebClientLoginRepository logins = mock(WebClientLoginRepository.class);
    private MockMvc mvc;
    private Logger logger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void setup() {
        when(service.saveClientLog(any(SystemLog.class))).thenReturn(true);
        logger = (Logger) LoggerFactory.getLogger(ClientLogController.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        mvc = MockMvcBuilders.standaloneSetup(new ClientLogController(service, JsonMapper.builder().build(), logins))
                .addFilters(new ClientDiagnosticRequestSizeFilter()).build();
    }

    @AfterEach
    void detachLogCapture() {
        logger.detachAppender(events);
        logger.setLevel(previousLevel);
        events.stop();
    }

    @Test
    void receivedWarningsAndExceptionsWriteBoundedInfoSummariesWithoutServerErrorStacks() throws Exception {
        for (String severity : new String[]{"WARN", "ERROR"}) {
            mvc.perform(post("/api/logs/client").servletPath("/api/logs/client")
                            .requestAttr(JwtUtil.CLIENT_LOGIN_REF, "synthetic-login")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Synthetic\\n" + "x".repeat(300) +
                                                                             "\",\"buildId\":\"synthetic-build\",\"severity\":\"" + severity +
                                                                             "\",\"stackTrace\":\"Error: synthetic\\n at original (app.js:1:2)\"}"))
                    .andExpect(status().isNoContent());
        }
        assertThat(events.list).hasSize(2).allSatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).startsWith("Client problem:").contains("Synthetic ")
                    .doesNotContain("\n", "at original");
            assertThat(event.getFormattedMessage().length()).isLessThan(500);
            assertThat(event.getThrowableProxy()).isNull();
        });
        assertThat(events.list.getLast().getFormattedMessage()).contains("clientSeverity=ERROR");
    }

    @Test
    void infoSummaryIncludesSanitizedExceptionTypeAndFirstDescriptionLineWithoutTraceFrames() throws Exception {
        mvc.perform(post("/api/logs/client").servletPath("/api/logs/client")
                .requestAttr(JwtUtil.CLIENT_LOGIN_REF, "synthetic-login")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"message":"Map rendering failed", "buildId":"synthetic-build", "severity":"ERROR",
                         "detail":"  Cannot load source token=synthetic-secret\\r\\n at detailFrame (app.js:3:4)",
                         "stackTrace":"TypeError: Cannot load source\\n at stackFrame (app.js:5:6)",
                         "context":{"errorType":"TypeError\\n at typeFrame (app.js:7:8)"}}
                        """)).andExpect(status().isNoContent());
        assertThat(events.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).contains("message=Map rendering failed",
                            "exception=TypeError, description=Cannot load source token=<redacted>")
                    .doesNotContain("synthetic-secret", "detailFrame", "stackFrame", "typeFrame", "\n", "\r");
            assertThat(event.getThrowableProxy()).isNull();
        });
        var stored = ArgumentCaptor.forClass(SystemLog.class);
        verify(service).saveClientLog(stored.capture());
        assertThat(stored.getValue().getDetail()).contains("at detailFrame");
        assertThat(stored.getValue().getStackTrace()).contains("at stackFrame");
    }

    @Test
    void boundsExceptionSummaryAndHandlesMissingOrNonStringExceptionMetadata() throws Exception {
        var mapper = JsonMapper.builder().build();
        String body = mapper.writeValueAsString(Map.of("message", "Synthetic", "buildId", "synthetic-build",
                "detail", "d".repeat(300), "context", Map.of("errorType", "t".repeat(100))));
        mvc.perform(post("/api/logs/client").requestAttr(JwtUtil.CLIENT_LOGIN_REF, "synthetic-login")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        assertThat(events.list.getFirst().getFormattedMessage())
                .endsWith("exception=" + "t".repeat(64) + "…, description=" + "d".repeat(256) + "…");
        for (String metadata : new String[]{"", ",\"detail\":\" \",\"context\":{}",
                ",\"context\":{\"errorType\":503}", ",\"context\":{\"errorType\":null}"}) {
            mvc.perform(post("/api/logs/client").requestAttr(JwtUtil.CLIENT_LOGIN_REF, "synthetic-login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"Synthetic\",\"buildId\":\"synthetic-build\"" + metadata + "}"))
                    .andExpect(status().isNoContent());
            assertThat(events.list.getLast().getFormattedMessage()).endsWith("exception=unspecified, description=unspecified");
        }
    }

    @Test
    void redactsUrlCredentialsAndRelativeQueriesWithoutRemovingFrameLocations() {
        assertThat(DiagnosticSanitizer.text("Failed at https://synthetic-user:synthetic-password@example.test/api"))
                .isEqualTo("Failed at https://<redacted>@example.test/api");
        assertThat(DiagnosticSanitizer.text("at x (/mtl/app.js?search=synthetic-private:7:3)"))
                .isEqualTo("at x (/mtl/app.js?<redacted>:7:3)");
    }

    @Test
    void permitsNullableClassificationAndTraceAndAssignsAuthenticatedSource() throws Exception {
        for (String optional : new String[]{"", ", \"severity\":null, \"stackTrace\":null", ", \"severity\":\"WARN\""}) {
            mvc.perform(post("/api/logs/client").servletPath("/api/logs/client")
                            .requestAttr(JwtUtil.CLIENT_LOGIN_REF, "synthetic-login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"Synthetic\",\"buildId\":\"synthetic-build\"" + optional + "}"))
                    .andExpect(status().isNoContent());
        }
        var events = ArgumentCaptor.forClass(SystemLog.class);
        verify(service, times(3)).saveClientLog(events.capture());
        assertThat(events.getAllValues()).allSatisfy(event -> {
            assertThat(event.getSource()).isEqualTo(SystemLog.Source.CLIENT);
            assertThat(event.getClientLoginRef()).isEqualTo("synthetic-login");
            assertThat(event.getStackTrace()).isNull();
            assertThat(event.getStackFingerprint()).isNull();
        });
        assertThat(events.getAllValues().getFirst().getSeverity()).isNull();
    }

    @Test
    void preservesSanitizedFramesAndStructuredSafeContext() throws Exception {
        mvc.perform(post("/api/logs/client").servletPath("/api/logs/client")
                .requestAttr(JwtUtil.CLIENT_LOGIN_REF, "synthetic-login")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"message":"password=synthetic-secret", "buildId":"synthetic-build", "severity":"ERROR",
                         "stackTrace":"Error: token=synthetic-secret\\n at a (app.js:1:7)\\n at b (app.js:2:9)",
                         "context":{"method":"get","status":503,"body":"private","health":"private"}}
                        """)).andExpect(status().isNoContent());
        var events = ArgumentCaptor.forClass(SystemLog.class);
        verify(service).saveClientLog(events.capture());
        assertThat(events.getValue().getStackTrace()).isEqualTo("Error: token=<redacted>\n at a (app.js:1:7)\n at b (app.js:2:9)");
        assertThat(events.getValue().getMessage()).isEqualTo("password=<redacted>");
        assertThat(DiagnosticSanitizer.text("{\"password\":\"synthetic private secret\"}")).isEqualTo("{\"password=<redacted>}");
        assertThat(DiagnosticSanitizer.text("at x (https://example.test/app.js?token=secret:4:5)"))
                .isEqualTo("at x (https://example.test/app.js?<redacted>:4:5)");
        assertThat(events.getValue().getContext()).containsEntry("status", 503).doesNotContainKeys("body", "health");
    }

    @Test
    void calculatesFingerprintFromSanitizedStackIndependentlyOfOtherReportFields() throws Exception {
        var mapper = JsonMapper.builder().build();
        for (int index = 0; index < 2; index++) {
            String body = mapper.writeValueAsString(Map.of("message", "Report " + index, "detail", "Reason " + index,
                    "buildId", "build-" + index, "severity", index == 0 ? "WARN" : "ERROR",
                    "stackTrace", "Error: reason " + index + "\n at a (https://example.test/app.js?token=value-" + index + ":1:2)\n at b (app.js:7:4)",
                    "context", Map.of("errorType", "Type" + index, "status", index == 0 ? 401 : 500)));
            mvc.perform(post("/api/logs/client").requestAttr(JwtUtil.CLIENT_LOGIN_REF, "login-" + index)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        }
        var stored = ArgumentCaptor.forClass(SystemLog.class);
        verify(service, times(2)).saveClientLog(stored.capture());
        assertThat(stored.getAllValues()).allSatisfy(event -> {
            assertThat(event.getStackFingerprint()).matches("[a-f0-9]{64}")
                    .isEqualTo(StackFingerprint.of("at a (https://example.test/app.js?<redacted>:1:2)\nat b (app.js:7:4)"));
            assertThat(event.getStackTrace()).contains("?<redacted>:1:2");
        });
    }

    @Test
    void rejectsInvalidAndOversizedFieldsWithoutSaving() throws Exception {
        for (String body : new String[]{
                "{\"message\":\" \",\"buildId\":\"build\"}",
                "{\"message\":\"ok\",\"buildId\":\" \"}",
                "{\"message\":\"ok\",\"buildId\":\"build\",\"severity\":\"INFO\"}",
                "{\"message\":\"ok\",\"buildId\":\"build\",\"topic1\":\"" + "x".repeat(256) + "\"}",
                "{\"message\":\"ok\",\"buildId\":\"build\",\"topic1\":\"" + "x".repeat(247) + " token=x\"}",
                "{\"message\":\"ok\",\"buildId\":\"" + "x".repeat(120) + " token=x\"}",
                "{\"message\":\"ok\",\"buildId\":\"build\",\"stackTrace\":\"" + "x".repeat(ClientDiagnosticLimits.STACK_CHARS + 1) + "\"}",
                "{\"message\":\"ok\",\"buildId\":\"build\",\"context\":{\"operation\":\"" + "x".repeat(ClientDiagnosticLimits.CONTEXT_BYTES) + "\"}}"}) {
            mvc.perform(post("/api/logs/client").servletPath("/api/logs/client")
                    .requestAttr(JwtUtil.CLIENT_LOGIN_REF, "synthetic-login")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/logs/client").servletPath("/api/logs/client").contentType(MediaType.APPLICATION_JSON)
                .content("x".repeat(ClientDiagnosticLimits.REQUEST_BYTES + 1))).andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(service);
    }

    @Test
    void missingValidatedLoginDoesNotInsertAndPersistenceFailureReturnsServiceUnavailable() throws Exception {
        String body = "{\"message\":\"ok\",\"buildId\":\"build\"}";
        mvc.perform(post("/api/logs/client").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        doThrow(new IllegalStateException("Synthetic database failure")).when(service).saveClientLog(any(SystemLog.class));
        mvc.perform(post("/api/logs/client").requestAttr(JwtUtil.CLIENT_LOGIN_REF, "synthetic-login")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isServiceUnavailable());
        verify(service, times(1)).saveClientLog(any(SystemLog.class));
    }

    @Test
    void preservesOccurrenceTimeAndAcceptsDuplicateAcknowledgmentsWithoutAnotherFileSummary() throws Exception {
        String body = """
                {"message":"Delayed", "buildId":"synthetic", "clientEventId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                 "occurredAt":"2026-10-01T12:00:00Z", "lastOccurredAt":"2026-10-01T12:00:30Z", "occurrenceCount":5}
                """;
        mvc.perform(post("/api/logs/client").requestAttr(JwtUtil.CLIENT_LOGIN_REF, "synthetic-login")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        var stored = ArgumentCaptor.forClass(SystemLog.class);
        verify(service).saveClientLog(stored.capture());
        assertThat(stored.getValue().getOccurredAt()).isEqualTo(java.time.Instant.parse("2026-10-01T12:00:00Z"));
        assertThat(stored.getValue().getOccurrenceCount()).isEqualTo(5);
        when(service.saveClientLog(any())).thenReturn(false);
        mvc.perform(post("/api/logs/client").requestAttr(JwtUtil.CLIENT_LOGIN_REF, "synthetic-login")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        assertThat(events.list).hasSize(1);
    }

    @Test
    void validatesHistoricalSessionOwnershipAndEventTimeRange() throws Exception {
        var historical = new com.x8ing.mtl.server.mtlserver.db.entity.logs.WebClientLogin();
        historical.setUserName("synthetic");
        when(logins.findByClientLoginRef("old-login")).thenReturn(java.util.Optional.of(historical));
        String body = "{\"message\":\"Delayed\",\"buildId\":\"synthetic\",\"originalClientLoginRef\":\"old-login\"}";
        mvc.perform(post("/api/logs/client").principal(() -> "synthetic").requestAttr(JwtUtil.CLIENT_LOGIN_REF, "new-login")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        mvc.perform(post("/api/logs/client").principal(() -> "another-account").requestAttr(JwtUtil.CLIENT_LOGIN_REF, "new-login")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        for (String fields : new String[]{"\"occurrenceCount\":0", "\"occurredAt\":\"invalid\"",
                "\"occurredAt\":\"2026-10-02T00:00:00Z\",\"lastOccurredAt\":\"2026-10-01T00:00:00Z\""}) {
            mvc.perform(post("/api/logs/client").requestAttr(JwtUtil.CLIENT_LOGIN_REF, "synthetic-login")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Delayed\",\"buildId\":\"synthetic\"," + fields + "}"))
                    .andExpect(status().isBadRequest());
        }
        verify(service, times(1)).saveClientLog(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"synthetic", "Synthetic", "SYNTHETIC"})
    void acceptsHistoricalSessionWithDifferentUsernameCasing(String historicalUsername) throws Exception {
        var historical = new WebClientLogin();
        historical.setUserName(historicalUsername);
        when(logins.findByClientLoginRef("old-login")).thenReturn(Optional.of(historical));
        String body = "{\"message\":\"Delayed\",\"buildId\":\"synthetic\",\"originalClientLoginRef\":\"old-login\"}";
        mvc.perform(post("/api/logs/client").principal(() -> "synthetic").requestAttr(JwtUtil.CLIENT_LOGIN_REF, "new-login")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        verify(service).saveClientLog(any());
    }
}
