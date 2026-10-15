package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiClient;
import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiException;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.exercise.ExerciseRouteService;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.AccountConnection;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SyncValidationDiagnosticsTest {
    private static final String PRIVATE_MARKER = "synthetic-private-provider-value";
    private final MirrorRepository repository = mock(MirrorRepository.class);
    private final ApiClient client = mock(ApiClient.class);
    private final ConnectorProperties properties = new ConnectorProperties();
    private final ObjectMapper json = new ObjectMapper();
    private final SyncCoordinator.Progress progress = mock(SyncCoordinator.Progress.class);
    private final Logger logger = (Logger) LoggerFactory.getLogger(SyncService.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Level previousLevel;
    private SyncService service;

    @BeforeEach
    void setup() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logs.start();
        logger.addAppender(logs);
        properties.setClientId("synthetic");
        properties.setClientSecret("synthetic");
        properties.setHistoryStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(1));
        var connection = new AccountConnection("synthetic", Set.of(OAuthScopes.SLEEP), "cipher",
                AccountConnection.ConnectionState.CONNECTED, null, Instant.now(), null, Instant.now());
        when(repository.findConnection()).thenReturn(Optional.of(connection));
        when(repository.recentPass(anyString(), any())).thenReturn(Optional.of(new MirrorRepository.RecentPass(
                UUID.randomUUID(), Instant.now(), Instant.now().minusSeconds(1), Instant.now(), Instant.now(),
                "COMPLETED", Instant.now().plusSeconds(3600), 1, 0, 0)));
        when(client.listRaw(any(), any(), any(), any())).thenReturn(new ApiClient.Page(List.of(), null));
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new SyncService(properties, repository, client, new PointMapper(new DataSourceResolver(repository)),
                mock(ExerciseRouteService.class), manager);
    }

    @AfterEach
    void restoreLogger() {
        logger.detachAppender(logs);
        logs.stop();
        logger.setLevel(previousLevel);
    }

    @ParameterizedTest
    @EnumSource(value = SyncValidationException.Reason.class, names = {
            "DUPLICATE_QUERY_IDENTITY", "QUERY_RANGE_MISMATCH", "INVALID_TIMESTAMP", "MISSING_QUERY_TIME"})
    void recentFailuresIdentifyTheirReasonWithoutExposingProviderData(SyncValidationException.Reason reason) {
        when(client.listRaw(any(), any(), any(), any())).thenAnswer(call -> {
            Instant start = call.getArgument(1);
            Instant end = call.getArgument(2);
            var first = sleep(start, end.minusNanos(1000));
            return switch (reason) {
                case DUPLICATE_QUERY_IDENTITY -> {
                    var second = first.deepCopy();
                    second.withObject("sleep").put("syntheticDifference", true);
                    yield new ApiClient.Page(List.of(first, second), null);
                }
                case QUERY_RANGE_MISMATCH -> new ApiClient.Page(List.of(sleep(start, end)), null);
                case INVALID_TIMESTAMP -> {
                    first.withObject("sleep").withObject("interval").put("startTime", PRIVATE_MARKER);
                    yield new ApiClient.Page(List.of(first), null);
                }
                case MISSING_QUERY_TIME -> new ApiClient.Page(List.of(json.readTree("{\"sleep\":{}}")), null);
                default -> throw new AssertionError(reason);
            };
        });

        var result = service.run(SyncCoordinator.JobKind.REGULAR, progress);

        assertThat(result.success()).isFalse();
        assertDiagnostic("recent chunk failed", reason.name(), reason.message());
        assertThat(result.errors()).allSatisfy(error -> assertThat(error)
                .contains("Health data validation failed.")
                .doesNotContain(PRIVATE_MARKER));
        verify(repository, never()).upsertDataPoints(any(), any());
        verify(repository, never()).replaceRecent(any(), any(), any(), any());
    }

    @Test
    void historicalFailuresUseTheSameReasonAndPreserveRetryBehavior() {
        properties.setHistoryStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(14));
        when(repository.isFinal(any(), any(), any())).thenReturn(true);
        LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(10);
        when(repository.isFinal(any(), any(), eq(day))).thenReturn(false);
        var parent = new MirrorRepository.HistoryWork(UUID.randomUUID(), day, "HISTORY", 0, "ACTIVE", 0, null);
        Instant start = day.atStartOfDay().toInstant(ZoneOffset.UTC);
        var child = new MirrorRepository.HistoryChild(UUID.randomUUID(), UUID.randomUUID(), start,
                start.plusSeconds(86400), "ACTIVE", null, 0, 0);
        var pending = new AtomicBoolean(true);
        when(repository.dueWork(any(), any(), eq("HISTORY"), any()))
                .thenAnswer(call -> pending.get() ? Optional.of(parent) : Optional.empty());
        when(repository.children(parent.id())).thenReturn(List.of(child));
        when(client.listRaw(any(), any(), any(), any())).thenReturn(new ApiClient.Page(
                List.of(sleep(start, child.end())), null));
        doAnswer(call -> {
            pending.set(false);
            return null;
        }).when(repository).recordWorkFailure(eq(parent), anyBoolean(), any(), any());

        var result = service.run(SyncCoordinator.JobKind.REGULAR, progress);

        assertThat(result.success()).isFalse();
        assertDiagnostic("historical work failed", "QUERY_RANGE_MISMATCH",
                SyncValidationException.Reason.QUERY_RANGE_MISMATCH.message());
        verify(repository).recordWorkFailure(eq(parent), eq(false), any(),
                eq("Health data validation failed. Reason: QUERY_RANGE_MISMATCH."));
        verify(repository, never()).checkpointChild(any(), any(), anyLong(), anyLong(), anyLong(), anyInt());
        verify(repository, never()).upsertDataPoints(any(), any());
        verify(repository, never()).importHistoricalPoints(any());
    }

    @Test
    void unknownValidationMessagesCausesAndSuppressedValuesStayOutOfDebugTraces() {
        var failure = new IllegalArgumentException(PRIVATE_MARKER + " token=synthetic-secret",
                new IllegalStateException(PRIVATE_MARKER + " raw health value"));
        failure.addSuppressed(new RuntimeException(PRIVATE_MARKER + " resource name"));
        when(client.listRaw(any(), any(), any(), any())).thenThrow(failure);

        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isFalse();

        assertDiagnostic("recent chunk failed", "UNCLASSIFIED_VALIDATION",
                "Health data validation failed.");
    }

    @ParameterizedTest
    @EnumSource(value = SyncValidationException.Reason.class, names = {
            "DUPLICATE_TRAVERSAL_IDENTITY", "PAGE_CHECKPOINT_REJECTED"})
    void wrappedRepositoryFailuresKeepTheirReasonWithoutExposingExceptionData(SyncValidationException.Reason reason) {
        var validation = new SyncValidationException(reason, new IllegalArgumentException(PRIVATE_MARKER));
        var failure = new InvalidDataAccessApiUsageException(PRIVATE_MARKER,
                new IllegalStateException(PRIVATE_MARKER, validation));
        failure.addSuppressed(new RuntimeException(PRIVATE_MARKER));
        when(client.listRaw(any(), any(), any(), any())).thenThrow(failure);

        var result = service.run(SyncCoordinator.JobKind.REGULAR, progress);

        assertThat(result.success()).isFalse();
        assertDiagnostic("recent chunk failed", reason.name(), reason.message());
        assertThat(result.errors()).allSatisfy(error -> assertThat(error)
                .contains("Health data validation failed.")
                .doesNotContain(PRIVATE_MARKER, "Local database work failed"));
    }

    @ParameterizedTest
    @EnumSource(value = ApiException.Reason.class, names = {"SERVER", "TRANSPORT", "INVALID_RESPONSE", "INVALID_PAGE_TOKEN"})
    void apiFailuresKeepTheirSafeReasonWithoutExposingProviderData(ApiException.Reason reason) {
        var failure = new ApiException("Google Health request failed", 500, true, reason,
                new IllegalStateException(PRIVATE_MARKER));
        failure.addSuppressed(new RuntimeException(PRIVATE_MARKER));
        when(client.listRaw(any(), any(), any(), any())).thenThrow(failure);

        assertThat(service.run(SyncCoordinator.JobKind.REGULAR, progress).success()).isFalse();

        assertDiagnostic("recent chunk failed", "API_" + reason.name(), "Google Health API request failed.");
    }

    private ObjectNode sleep(Instant start, Instant end) {
        var point = json.createObjectNode();
        point.put("name", "users/" + PRIVATE_MARKER + "/dataTypes/sleep/dataPoints/" + PRIVATE_MARKER);
        point.putObject("sleep").putObject("interval")
                .put("startTime", start.toString()).put("endTime", end.toString());
        return point;
    }

    private void assertDiagnostic(String operation, String reason, String detail) {
        assertThat(logs.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .isNotEmpty().allSatisfy(event -> {
                    assertThat(event.getFormattedMessage()).contains(operation, "reason=" + reason, "detail=" + detail);
                    assertThat(event.getThrowableProxy()).isNull();
                });
        assertThat(logs.list).filteredOn(event -> event.getLevel() == Level.DEBUG)
                .isNotEmpty().allSatisfy(event -> {
                    var trace = event.getThrowableProxy();
                    assertThat(trace).isNotNull();
                    assertThat(trace.getMessage()).contains("reason=" + reason).doesNotContain(PRIVATE_MARKER);
                    assertThat(trace.getCause()).isNull();
                    assertThat(trace.getSuppressed()).isNullOrEmpty();
                    assertThat(trace.getStackTraceElementProxyArray()).isNotEmpty();
                });
        assertThat(logs.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(PRIVATE_MARKER));
    }
}
