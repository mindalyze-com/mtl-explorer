package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiClient;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.exercise.ExerciseRouteService;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.*;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Real page transactions with entirely synthetic provider data.
 */
@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.media-correlation.initial-delay=PT1H",
        "mtl.media-correlation.run-schedule=PT1H", "mtl.indexer.gps.live-watch-enabled=false",
        "mtl.indexer.media.live-watch-enabled=false"})
class MalformedPointPageDatabaseIntegrationTest {
    @Autowired
    MirrorRepository repository;
    @Autowired
    PlatformTransactionManager transactionManager;
    @MockitoBean
    GPXDirectoryWatcherService watcher;
    @MockitoBean
    MediaIndexerService media;

    @BeforeEach
    @AfterEach
    void clearSyntheticMirror() {
        repository.disconnect();
        repository.deleteMirrorRows();
    }

    @Test
    void malformedTimeRollsBackItsWholePageAndKeepsWindowAndExistingRows() {
        repository.saveConnected("synthetic", Set.of(OAuthScopes.SLEEP), "synthetic", null);
        var policy = DataTypePolicy.all().stream().filter(value -> value.dataType().equals("sleep"))
                .findFirst().orElseThrow();
        PointMapper mapper = new PointMapper(new DataSourceResolver(repository));
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        StoredPoint existing = mapper.mapPoint(policy, sleep("existing", start), null);
        repository.upsertDataPoints(List.of(existing), UUID.randomUUID());
        ApiClient client = mock(ApiClient.class);
        when(client.listRaw(any(), any(), any(), any())).thenReturn(new ApiClient.Page(List.of(
                sleep("new", start.plus(1, ChronoUnit.HOURS)),
                new ObjectMapper().readTree("{\"name\":\"synthetic/malformed\",\"sleep\":{}}")), null));
        ConnectorProperties properties = new ConnectorProperties();
        properties.setHistoryStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(7));
        properties.setClientId("synthetic");
        properties.setClientSecret("synthetic");
        SyncService service = new SyncService(properties, repository, client, mapper,
                mock(ExerciseRouteService.class), transactionManager);

        var result = service.run(SyncCoordinator.JobKind.REGULAR, mock(SyncCoordinator.Progress.class));

        assertThat(result.success()).isFalse();
        assertThat(result.continueImmediately()).isFalse();
        assertThat(repository.findRecords("sleep", "RAW", null, null, null, null, null, null, 50))
                .singleElement().satisfies(row -> assertThat(repository.findRecord(row.kind(), row.id())
                        .orElseThrow().rawJson()).contains("synthetic/existing"));
        var status = repository.synchronizationStatus(properties, Instant.now());
        assertThat(status.streams().getFirst().failures()).isNotEmpty();
        assertThat(repository.recentPass("synthetic", policy)).isPresent();
        assertThat(repository.findWindowProgress("sleep", "RAW")).isEmpty();

    }

    private static JsonNode sleep(String name, Instant start) {
        return new ObjectMapper().readTree("""
                {"name":"synthetic/%s","sleep":{"interval":{"startTime":"%s","endTime":"%s"}}}
                """.formatted(name, start, start.plus(30, ChronoUnit.MINUTES)));
    }
}
