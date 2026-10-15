package com.x8ing.mtl.server.mtlserver.googlehealth.exercise;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiClient;
import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiException;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthException;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.AccountConnection;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.ExercisePoint;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncCoordinator;
import com.x8ing.mtl.server.mtlserver.gpx.GpxProcessingWorker;
import com.x8ing.mtl.server.mtlserver.gpx.SupportedTrackFormat;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerPathMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ExerciseRouteServiceTest {

    private static final Instant START = Instant.parse("2026-08-28T10:00:00Z");
    private static final Instant END = Instant.parse("2026-08-28T11:00:00Z");
    private static final String RESOURCE = "users/me/dataTypes/exercise/dataPoints/route-1";

    @TempDir
    Path tempDirectory;

    private Path watchDirectory;
    private Path stagingDirectory;

    private final IndexerRepository indexerRepository = mock(IndexerRepository.class);
    private final GpxProcessingWorker gpxProcessingWorker = mock(GpxProcessingWorker.class);

    @BeforeEach
    void setUp() throws Exception {
        watchDirectory = Files.createDirectory(tempDirectory.resolve("watch"));
        stagingDirectory = tempDirectory.resolve("staging");
    }

    @Test
    void preparesManagedDirectoriesBeforeTheLiveWatcherStarts() {
        ConnectorProperties properties = configuredProperties();
        ExerciseRouteService service = new ExerciseRouteService(
                properties, new MtlAppProperties(), mock(ApiClient.class), new TcxValidator(),
                mock(MirrorRepository.class), indexerRepository, gpxProcessingWorker,
                watchDirectory.toString());

        service.prepareManagedDirectories();

        assertThat(watchDirectory.resolve("google-health")).isDirectory();
        assertThat(stagingDirectory).isDirectory();
        assertThat(stagingDirectory.toAbsolutePath().normalize().startsWith(
                watchDirectory.toAbsolutePath().normalize())).isFalse();
    }

    @Test
    void defaultStagingStaysOnTheRouteMountAndCannotBeIndexedBeforePublication() throws Exception {
        ConnectorProperties properties = configuredProperties();
        properties.setStagingDirectory(null);
        MirrorRepository repository = connectedRepository();
        ApiClient apiClient = mock(ApiClient.class);
        ExercisePoint point = exercisePoint("fingerprint-default-staging");
        when(repository.findExerciseRouteCandidates(START, END)).thenReturn(List.of(point));
        Path routeDirectory = watchDirectory.resolve("google-health");
        doAnswer(invocation -> {
            Path temporaryFile = invocation.getArgument(1);
            assertThat(temporaryFile.getParent()).isEqualTo(routeDirectory.resolve(".staging"));
            assertThat(Files.getFileStore(temporaryFile)).isEqualTo(Files.getFileStore(routeDirectory));
            assertThat(temporaryFile.toString()).doesNotMatch(SupportedTrackFormat.inclusionRegex());
            assertThat(IndexerPathMatchers.standardExclusions().stream()
                    .anyMatch(matcher -> matcher.matches(temporaryFile))).isTrue();
            Files.writeString(temporaryFile, validTcx());
            return null;
        }).when(apiClient).downloadExerciseTcx(eq(RESOURCE), any(Path.class));
        ExerciseRouteService service = new ExerciseRouteService(
                properties, new MtlAppProperties(), apiClient, new TcxValidator(), repository,
                indexerRepository, gpxProcessingWorker, watchDirectory.toString());

        service.prepareManagedDirectories();
        service.refreshRoutes(START, END, null);

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(repository).markExerciseRoutePublished(eq(point.id()), path.capture(), eq(point.fingerprint()),
                any(Instant.class), org.mockito.ArgumentMatchers.anyLong());
        assertThat(Files.readString(watchDirectory.resolve(path.getValue()))).isEqualTo(validTcx());
        try (var stagingFiles = Files.list(routeDirectory.resolve(".staging"))) {
            assertThat(stagingFiles).isEmpty();
        }
    }

    @Test
    void rejectsAStagingDirectoryInsideTheWatchedDirectory() {
        ConnectorProperties properties = configuredProperties();
        properties.setStagingDirectory(watchDirectory.resolve(".staging").toString());
        ExerciseRouteService service = new ExerciseRouteService(
                properties, new MtlAppProperties(), mock(ApiClient.class), new TcxValidator(),
                mock(MirrorRepository.class), indexerRepository, gpxProcessingWorker,
                watchDirectory.toString());

        assertThatThrownBy(service::prepareManagedDirectories)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the GPS watch directory");
    }

    @Test
    void validatesAndAtomicallyPublishesTcxWithoutLeavingPartialFiles() throws Exception {
        MirrorRepository repository = connectedRepository();
        ApiClient apiClient = mock(ApiClient.class);
        ExercisePoint point = exercisePoint("fingerprint-1");
        when(repository.findExerciseRouteCandidates(START, END)).thenReturn(List.of(point));
        doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(1), validTcx());
            return null;
        }).when(apiClient).downloadExerciseTcx(eq(RESOURCE), any(Path.class));
        ExerciseRouteService service = service(repository, apiClient);

        service.refreshRoutes(START, END, null);

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        var publicationOrder = inOrder(repository);
        publicationOrder.verify(repository).markExerciseRoutePublicationStarted(point.id());
        publicationOrder.verify(repository)
                .markExerciseRoutePublished(eq(point.id()), path.capture(), eq(point.fingerprint()), any(Instant.class), org.mockito.ArgumentMatchers.anyLong());
        assertThat(path.getValue()).startsWith("google-health/").endsWith(".tcx");
        assertThat(Files.readString(watchDirectory.resolve(path.getValue()))).isEqualTo(validTcx());
        try (var stagingFiles = Files.list(stagingDirectory)) {
            assertThat(stagingFiles).isEmpty();
        }
    }

    @Test
    void malformedReplacementLeavesPublishedRouteUnchanged() throws Exception {
        MirrorRepository repository = connectedRepository();
        ApiClient apiClient = mock(ApiClient.class);
        ExercisePoint point = exercisePoint("fingerprint-1");
        when(repository.findExerciseRouteCandidates(START, END)).thenReturn(List.of(point));
        doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(1), validTcx());
            return null;
        }).when(apiClient).downloadExerciseTcx(eq(RESOURCE), any(Path.class));
        ExerciseRouteService service = service(repository, apiClient);
        service.refreshRoutes(START, END, null);
        ArgumentCaptor<String> publishedPath = ArgumentCaptor.forClass(String.class);
        verify(repository).markExerciseRoutePublicationStarted(point.id());
        verify(repository).markExerciseRoutePublished(eq(point.id()), publishedPath.capture(), eq(point.fingerprint()), any(Instant.class), org.mockito.ArgumentMatchers.anyLong());
        Path route = watchDirectory.resolve(publishedPath.getValue());

        org.mockito.Mockito.reset(apiClient);
        doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(1), "<broken>");
            return null;
        }).when(apiClient).downloadExerciseTcx(eq(RESOURCE), any(Path.class));
        service.refreshRoutes(START, END, null);

        assertThat(Files.readString(route)).isEqualTo(validTcx());
        verify(repository).recordExerciseRouteFailure(
                eq(point.id()), eq(false), any(Instant.class), eq("The exported route is invalid."));
    }

    @Test
    void missingRemoteExerciseRemovesOnlyItsManagedRoute() throws Exception {
        MirrorRepository repository = mock(MirrorRepository.class);
        ApiClient apiClient = mock(ApiClient.class);
        Path managed = watchDirectory.resolve("google-health/managed.tcx");
        Path manual = watchDirectory.resolve("manual.tcx");
        Files.createDirectories(managed.getParent());
        Files.writeString(managed, validTcx());
        Files.writeString(manual, validTcx());
        ExercisePoint missing = new ExercisePoint(
                9L, RESOURCE, START, END, "fingerprint", "{}", "google-health/managed.tcx",
                ExercisePoint.RouteState.IMPORTED, 20L, 30L);
        IndexedFile indexedFile = new IndexedFile();
        indexedFile.setId(20L);
        when(indexerRepository.findById(20L)).thenReturn(Optional.of(indexedFile));
        service(repository, apiClient).removeManagedRoutes(List.of(missing));

        assertThat(managed).doesNotExist();
        assertThat(manual).exists();
        verify(gpxProcessingWorker).processDelete("GPS", 20L);
    }

    @Test
    void orphanCleanupTouchesOnlyTheDedicatedManagedDirectory() throws Exception {
        MirrorRepository repository = mock(MirrorRepository.class);
        Path referenced = watchDirectory.resolve("google-health/referenced.tcx");
        Path orphan = watchDirectory.resolve("google-health/orphan.tcx");
        Path staging = stagingDirectory.resolve("pending.part");
        Path manual = watchDirectory.resolve("manual.tcx");
        Files.createDirectories(referenced.getParent());
        Files.createDirectories(staging.getParent());
        Files.writeString(referenced, validTcx());
        Files.writeString(orphan, validTcx());
        Files.writeString(staging, "partial");
        Files.writeString(manual, validTcx());
        when(repository.findAllManagedRoutePaths()).thenReturn(List.of("google-health/referenced.tcx"));

        service(repository, mock(ApiClient.class)).removeOrphanedManagedRoutes();

        assertThat(referenced).exists();
        assertThat(orphan).doesNotExist();
        assertThat(staging).exists();
        assertThat(manual).exists();
    }

    @Test
    void retriesOldFailuresWithoutRevisitingTheirDataWindowAndContinuesAfterOneFailure() throws Exception {
        MirrorRepository repository = connectedRepository();
        ApiClient api = mock(ApiClient.class);
        ExercisePoint first = exercisePoint("first");
        ExercisePoint second = new ExercisePoint(6L, RESOURCE + "2", START, END, "second", "{}", null,
                ExercisePoint.RouteState.FAILED, null, null);
        when(repository.findDueExerciseRouteRetries(25)).thenReturn(List.of(first, second));
        org.mockito.Mockito.doThrow(new ApiException("provider failure", 503, true))
                .when(api).downloadExerciseTcx(eq(RESOURCE), any(Path.class));
        doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(1), validTcx());
            return null;
        }).when(api).downloadExerciseTcx(eq(RESOURCE + "2"), any(Path.class));

        var result = service(repository, api).retryDueRoutes(null);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.published()).isEqualTo(1);
        verify(repository, never()).prepareExerciseRoutes(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(repository).recordExerciseRouteFailure(eq(5L), eq(true), any(Instant.class),
                eq("Google route download failed (HTTP 503)."));
    }

    @Test
    void cancellationAfterDownloadDoesNotPublishOrBecomeAnOrdinaryFailure() throws Exception {
        MirrorRepository repository = connectedRepository();
        ApiClient api = mock(ApiClient.class);
        SyncCoordinator.Progress progress = mock(SyncCoordinator.Progress.class);
        when(repository.findDueExerciseRouteRetries(25)).thenReturn(List.of(exercisePoint("first")));
        doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(1), validTcx());
            when(progress.isCancelled()).thenReturn(true);
            return null;
        }).when(api).downloadExerciseTcx(eq(RESOURCE), any(Path.class));

        assertThatThrownBy(() -> service(repository, api).retryDueRoutes(progress))
                .isInstanceOf(CancellationException.class);

        verify(repository, never()).markExerciseRoutePublicationStarted(org.mockito.ArgumentMatchers.anyLong());
        verify(repository, never()).markExerciseRouteState(org.mockito.ArgumentMatchers.anyLong(), any(), any());
        try (var files = Files.list(stagingDirectory)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void authorizationFailureEscapesTheRouteBatch() throws Exception {
        MirrorRepository repository = connectedRepository();
        ApiClient api = mock(ApiClient.class);
        when(repository.findDueExerciseRouteRetries(25)).thenReturn(List.of(exercisePoint("first")));
        org.mockito.Mockito.doThrow(new OAuthException("stale authorization", true))
                .when(api).downloadExerciseTcx(eq(RESOURCE), any(Path.class));

        assertThatThrownBy(() -> service(repository, api).retryDueRoutes(null))
                .isInstanceOf(OAuthException.class);
    }

    @Test
    void unchangedExportReusesTheAlreadyIndexedFileVersion() throws Exception {
        MirrorRepository repository = connectedRepository();
        ApiClient api = mock(ApiClient.class);
        ExercisePoint point = exercisePoint("first");
        when(repository.findExerciseRouteCandidates(START, END)).thenReturn(List.of(point));
        doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(1), validTcx());
            return null;
        }).when(api).downloadExerciseTcx(eq(RESOURCE), any(Path.class));
        ExerciseRouteService routes = service(repository, api);
        routes.refreshRoutes(START, END, null);
        ArgumentCaptor<String> publishedPath = ArgumentCaptor.forClass(String.class);
        verify(repository).markExerciseRoutePublished(eq(point.id()), publishedPath.capture(), eq(point.fingerprint()),
                any(Instant.class), org.mockito.ArgumentMatchers.anyLong());
        Path target = watchDirectory.resolve(publishedPath.getValue());
        Instant version = Files.getLastModifiedTime(target).toInstant();

        when(repository.findExerciseRouteCandidates(START, END)).thenReturn(List.of(exercisePoint("metadata-update")));
        routes.refreshRoutes(START, END, null);

        assertThat(Files.getLastModifiedTime(target).toInstant()).isEqualTo(version);
        verify(repository).markExerciseRoutePublicationStarted(point.id());
        verify(repository).markExerciseRoutePublished(eq(point.id()),
                eq(publishedPath.getValue()), eq("metadata-update"), eq(version), eq(Files.size(target)));
    }

    @Test
    void deniedLocationProducesActionableFailureWithoutStoppingTheBatch() throws Exception {
        MirrorRepository repository = connectedRepository();
        ApiClient api = mock(ApiClient.class);
        when(repository.findDueExerciseRouteRetries(25)).thenReturn(List.of(exercisePoint("first")));
        org.mockito.Mockito.doThrow(new ApiException("permission denied", 403, false))
                .when(api).downloadExerciseTcx(eq(RESOURCE), any(Path.class));

        assertThat(service(repository, api).retryDueRoutes(null).failed()).isEqualTo(1);
        verify(repository).recordExerciseRouteFailure(eq(5L), eq(false), any(Instant.class),
                eq("Google denied route export. Check activity and location access in Settings."));
    }

    @Test
    void unavailableStagingDirectoryIsRetryableAndDoesNotCallGoogle() throws Exception {
        Files.writeString(stagingDirectory, "a file blocks this directory");
        MirrorRepository repository = connectedRepository();
        ApiClient api = mock(ApiClient.class);
        when(repository.findDueExerciseRouteRetries(25)).thenReturn(List.of(exercisePoint("first")));

        assertThat(service(repository, api).retryDueRoutes(null).failed()).isEqualTo(1);
        org.mockito.Mockito.verifyNoInteractions(api);
    }

    @Test
    void disabledDemoAndDisconnectedConfigurationsDoNotRunRouteRetries() {
        for (int mode = 0; mode < 3; mode++) {
            MirrorRepository repository = connectedRepository();
            ApiClient api = mock(ApiClient.class);
            ConnectorProperties properties = configuredProperties();
            MtlAppProperties app = new MtlAppProperties();
            if (mode == 0) properties.setEnabled(false);
            if (mode == 1) app.setDemoMode(true);
            if (mode == 2) when(repository.findConnection()).thenReturn(Optional.empty());
            ExerciseRouteService service = new ExerciseRouteService(properties, app, api, new TcxValidator(),
                    repository, indexerRepository, gpxProcessingWorker, watchDirectory.toString());

            assertThat(service.retryDueRoutes(null)).isEqualTo(ExerciseRouteService.RouteRefreshResult.empty());
            verify(repository, never()).findDueExerciseRouteRetries(org.mockito.ArgumentMatchers.anyInt());
            org.mockito.Mockito.verifyNoInteractions(api);
        }
    }

    private ExerciseRouteService service(
            MirrorRepository repository,
            ApiClient apiClient) {
        ConnectorProperties properties = configuredProperties();
        return new ExerciseRouteService(
                properties, new MtlAppProperties(), apiClient, new TcxValidator(), repository,
                indexerRepository, gpxProcessingWorker, watchDirectory.toString());
    }

    private ConnectorProperties configuredProperties() {
        ConnectorProperties properties = new ConnectorProperties();
        properties.setRouteDirectory("google-health");
        properties.setStagingDirectory(stagingDirectory.toString());
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRedirectUri("http://localhost/mtl/api/google-health/oauth/callback");
        return properties;
    }

    private static MirrorRepository connectedRepository() {
        MirrorRepository repository = mock(MirrorRepository.class);
        when(repository.findConnection()).thenReturn(Optional.of(new AccountConnection(
                "me", Set.of(OAuthScopes.ACTIVITY_AND_FITNESS, OAuthScopes.LOCATION), "encrypted",
                AccountConnection.ConnectionState.CONNECTED, null,
                Instant.now(), null, Instant.now())));
        return repository;
    }

    private static ExercisePoint exercisePoint(String fingerprint) {
        return new ExercisePoint(
                5L, RESOURCE, START, END, fingerprint, "{}", null,
                ExercisePoint.RouteState.PENDING, null, null);
    }

    private static String validTcx() {
        return """
                <?xml version="1.0"?>
                <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
                  <Activities><Activity Sport="Running"><Id>2026-08-28T10:00:00Z</Id><Lap StartTime="2026-08-28T10:00:00Z"><Track>
                    <Trackpoint><Time>2026-08-28T10:01:00Z</Time><Position><LatitudeDegrees>47.1</LatitudeDegrees><LongitudeDegrees>8.1</LongitudeDegrees></Position></Trackpoint>
                    <Trackpoint><Time>2026-08-28T10:02:00Z</Time><Position><LatitudeDegrees>47.2</LatitudeDegrees><LongitudeDegrees>8.2</LongitudeDegrees></Position></Trackpoint>
                  </Track></Lap></Activity></Activities>
                </TrainingCenterDatabase>
                """;
    }
}
