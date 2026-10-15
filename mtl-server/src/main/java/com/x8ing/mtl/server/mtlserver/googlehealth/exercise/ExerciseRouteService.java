package com.x8ing.mtl.server.mtlserver.googlehealth.exercise;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiException;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthException;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.AccountConnection;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.ExercisePoint;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.provider.ExerciseTcxProvider;
import com.x8ing.mtl.server.mtlserver.googlehealth.provider.GoogleHealthProvider;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.StableFingerprint;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncBudget;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncCoordinator;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.WorkRetryPolicy;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.gpx.GpxProcessingWorker;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExerciseRouteService implements ExerciseRouteWork {

    private static final String TCX_EXTENSION = ".tcx";
    private static final String STAGING_EXTENSION = ".part";
    private static final String ATOMIC_MOVE_PROBE_PREFIX = ".mtl-google-health-atomic-";
    private static final String ATOMIC_MOVE_PROBE_SUFFIX = ".probe";
    private static final String DEFAULT_STAGING_DIRECTORY = ".staging";
    private static final String STAGING_FILE_PREFIX = ".exercise-";
    private static final int ROUTE_FILE_HASH_LENGTH = 32;
    private static final int ROUTE_RETRY_BATCH_SIZE = 25;
    private static final long NEXT_FILE_MODIFICATION_MILLIS = 1;
    private static final String STORAGE_ERROR = "Could not store the route. Check the server route and staging directories.";

    private final ConnectorProperties properties;
    private final MtlAppProperties appProperties;
    private final ExerciseTcxProvider apiClient;
    private final TcxValidator tcxValidator;
    private final MirrorRepository repository;
    private final IndexerRepository indexerRepository;
    private final GpxProcessingWorker gpxProcessingWorker;
    private final Path watchDirectory;

    public ExerciseRouteService(
            ConnectorProperties properties,
            MtlAppProperties appProperties,
            ExerciseTcxProvider apiClient,
            TcxValidator tcxValidator,
            MirrorRepository repository,
            IndexerRepository indexerRepository,
            GpxProcessingWorker gpxProcessingWorker,
            @Value("${mtl.gpx-watch-directory}") String watchDirectory) {
        this.properties = properties;
        this.appProperties = appProperties;
        this.apiClient = apiClient;
        this.tcxValidator = tcxValidator;
        this.repository = repository;
        this.indexerRepository = indexerRepository;
        this.gpxProcessingWorker = gpxProcessingWorker;
        this.watchDirectory = Path.of(watchDirectory).toAbsolutePath().normalize();
    }

    @PostConstruct
    void prepareManagedDirectories() {
        if (appProperties.isDemoMode() || !properties.isConfigured()) {
            return;
        }
        Path routeDirectory = managedRouteDirectory();
        Path stagingDirectory = stagingDirectory();
        try {
            Files.createDirectories(routeDirectory);
            Files.createDirectories(stagingDirectory);
            verifyAtomicMoveSupport(stagingDirectory, routeDirectory);
            log.info("Google Health exercise-route storage is ready: routeDirectory={}, stagingDirectory={}",
                    routeDirectory, stagingDirectory);
        } catch (IOException exception) {
            log.warn("Google Health route staging is unavailable; route imports will report a failure: "
                     + "routeDirectory={}, stagingDirectory={}",
                    routeDirectory, stagingDirectory, exception);
        }
    }

    public RouteRefreshResult refreshRoutes(Instant start, Instant end, SyncCoordinator.Progress progress) {
        checkCancelled(progress);
        if (!remoteAccessEnabled()) {
            return RouteRefreshResult.empty();
        }
        boolean locationGranted = hasLocationGrant();
        repository.prepareExerciseRoutes(start, end, locationGranted);
        if (!locationGranted) {
            log.info("Google Health exercise-route import skipped because location access was not granted: "
                     + "start={}, end={}", start, end);
            return RouteRefreshResult.empty();
        }
        return importRoutes(repository.findExerciseRouteCandidates(start, end), progress);
    }

    /**
     * Runs in the existing sync worker, so disconnect/delete cancellation also covers route retries.
     */
    public RouteRefreshResult retryDueRoutes(SyncCoordinator.Progress progress) {
        checkCancelled(progress);
        if (!remoteAccessEnabled() || !hasLocationGrant()) {
            return RouteRefreshResult.empty();
        }
        return importRoutes(repository.findDueExerciseRouteRetries(ROUTE_RETRY_BATCH_SIZE), progress);
    }

    private boolean remoteAccessEnabled() {
        return !appProperties.isDemoMode() && properties.isConfigured()
               && repository.findConnection()
                       .filter(connection -> connection.state() == AccountConnection.ConnectionState.CONNECTED)
                       .isPresent();
    }

    private boolean hasLocationGrant() {
        return repository.findConnection()
                .filter(connection -> connection.state() == AccountConnection.ConnectionState.CONNECTED)
                .map(AccountConnection::grantedScopes)
                .map(scopes -> scopes.contains(OAuthScopes.LOCATION))
                .orElse(false);
    }

    private RouteRefreshResult importRoutes(List<ExercisePoint> candidates, SyncCoordinator.Progress progress) {
        int published = 0;
        int noRoute = 0;
        int failed = 0;
        long started = System.nanoTime();
        log.info("Google Health exercise-route batch started: candidates={}", candidates.size());
        for (ExercisePoint point : candidates) {
            checkCancelled(progress);
            SyncBudget.require(properties.getRequestTimeout().plusSeconds(1));
            switch (importRoute(point, progress)) {
                case PUBLISHED -> published++;
                case NO_ROUTE -> noRoute++;
                case FAILED -> failed++;
            }
        }
        log.info("Google Health exercise-route batch completed: candidates={}, published={}, noRoute={}, "
                 + "failed={}, durationMs={}", candidates.size(), published, noRoute, failed,
                (System.nanoTime() - started) / 1_000_000);
        return new RouteRefreshResult(published, noRoute, failed);
    }

    public void removeManagedRoutes(List<ExercisePoint> points) {
        for (ExercisePoint point : points) {
            deleteManagedRoute(point.tcxRelativePath(), point.indexedFileId());
        }
        if (!points.isEmpty()) {
            log.info("Google Health managed exercise routes removed: requested={}", points.size());
        }
    }

    public void removeOrphanedManagedRoutes() {
        Path managedDirectory = managedRouteDirectory();
        if (!Files.isDirectory(managedDirectory)) {
            return;
        }
        Set<Path> referencedRoutes = repository.findAllManagedRoutePaths().stream()
                .map(this::resolveManagedPath)
                .collect(Collectors.toUnmodifiableSet());
        try (var files = Files.list(managedDirectory)) {
            List<Path> orphanedRoutes = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(TCX_EXTENSION))
                    .filter(path -> !referencedRoutes.contains(path.toAbsolutePath().normalize()))
                    .toList();
            for (Path route : orphanedRoutes) {
                deleteManagedRoute(watchDirectory.relativize(route).toString(), null);
            }
            if (!orphanedRoutes.isEmpty()) {
                log.info("Google Health orphaned managed exercise routes removed: count={}",
                        orphanedRoutes.size());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not clean up Google Health route files", exception);
        }
    }

    public void removeAllManagedRoutes() {
        removeManagedRoutes(repository.findAllManagedExerciseRoutes());
    }

    private RouteImportResult importRoute(ExercisePoint point, SyncCoordinator.Progress progress) {
        Path temporaryFile = null;
        try {
            Path routeDirectory = managedRouteDirectory();
            Path stagingDirectory = stagingDirectory();
            Files.createDirectories(routeDirectory);
            Files.createDirectories(stagingDirectory);
            requireSameFileStore(stagingDirectory, routeDirectory);

            temporaryFile = Files.createTempFile(stagingDirectory, STAGING_FILE_PREFIX, STAGING_EXTENSION);
            apiClient.downloadExerciseTcx(point.resourceName(), temporaryFile);
            checkCancelled(progress);
            TcxValidator.ValidationResult validation;
            try {
                validation = tcxValidator.validate(temporaryFile, point.startTime(), point.endTime());
            } catch (TcxValidator.MissingRouteException exception) {
                throw exception;
            } catch (IOException exception) {
                log.warn("Google Health route validation failed: type={}", exception.getClass().getSimpleName());
                throw new IllegalArgumentException("Invalid exported route", exception);
            }

            String relativePath = stableRelativePath(point.resourceName());
            Path target = resolveManagedPath(relativePath);
            checkCancelled(progress);
            if (Files.isRegularFile(target) && Files.mismatch(temporaryFile, target) == -1) {
                // Metadata changed, but the already indexed file still represents this publication.
                repository.markExerciseRoutePublished(point.id(), relativePath, point.fingerprint(),
                        Files.getLastModifiedTime(target).toInstant().truncatedTo(ChronoUnit.MILLIS), Files.size(target));
                log.debug("Google Health exercise route reused unchanged file");
                return RouteImportResult.PUBLISHED;
            }
            // The indexer detects versions by size and millisecond mtime. Ensure a replacement
            // with the same length still gets a distinct, durable file version.
            long previousModified = Files.exists(target) ? Files.getLastModifiedTime(target).toMillis() : 0;
            long modified = Math.max(System.currentTimeMillis(), previousModified + NEXT_FILE_MODIFICATION_MILLIS);
            Files.setLastModifiedTime(temporaryFile, FileTime.fromMillis(modified));
            repository.markExerciseRoutePublicationStarted(point.id());
            try {
                Files.move(temporaryFile, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Atomic TCX publication is not supported on the configured filesystem", exception);
            }
            temporaryFile = null;
            repository.markExerciseRoutePublished(point.id(), relativePath, point.fingerprint(),
                    Files.getLastModifiedTime(target).toInstant().truncatedTo(ChronoUnit.MILLIS), Files.size(target));
            log.debug("Google Health exercise route published");
            return RouteImportResult.PUBLISHED;
        } catch (SyncBudget.Yield | GoogleHealthProvider.QuotaPause exception) {
            throw exception;
        } catch (CancellationException exception) {
            log.info("Google Health exercise-route import cancelled");
            throw exception;
        } catch (OAuthException exception) {
            log.warn("Google Health route authorization failed: type={}", exception.getClass().getSimpleName());
            throw exception;
        } catch (TcxValidator.MissingRouteException exception) {
            repository.markExerciseRouteState(point.id(), ExercisePoint.RouteState.NO_ROUTE, null);
            log.debug("Google Health exercise has no usable route");
            return RouteImportResult.NO_ROUTE;
        } catch (ApiException exception) {
            checkCancelled(progress);
            if (exception.getReason() == ApiException.Reason.AUTHORIZATION || exception.getReason() == ApiException.Reason.MISSING_SCOPE) throw exception;
            if (exception.getStatusCode() == 404) {
                repository.markExerciseRouteState(point.id(), ExercisePoint.RouteState.NO_ROUTE, null);
                log.debug("Google Health exercise has no exported route");
                return RouteImportResult.NO_ROUTE;
            } else {
                repository.recordExerciseRouteFailure(point.id(), WorkRetryPolicy.isRecoverable(exception), Instant.now(), safeError(exception));
                log.warn("Google Health exercise-route download failed: status={}, reason={}", exception.getStatusCode(), exception.getReason());
                log.debug("Google Health route download failure type={}", exception.getClass().getSimpleName());
                if (exception.getStatusCode() == 401 || exception.getReason() == ApiException.Reason.AUTHORIZATION) {
                    throw exception;
                }
                return RouteImportResult.FAILED;
            }
        } catch (RuntimeException | IOException exception) {
            checkCancelled(progress);
            repository.recordExerciseRouteFailure(point.id(), WorkRetryPolicy.isRecoverable(exception), Instant.now(), safeError(exception));
            log.warn("Google Health exercise-route import failed: type={}", exception.getClass().getSimpleName());
            log.debug("Google Health route import failure type={}", exception.getClass().getSimpleName());
            return RouteImportResult.FAILED;
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException exception) {
                    log.warn("Could not remove a Google Health TCX staging file: path={}",
                            temporaryFile, exception);
                }
            }
        }
    }

    private void deleteManagedRoute(String relativePath, Long indexedFileId) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        Path route = resolveManagedPath(relativePath);
        try {
            boolean deleted = Files.deleteIfExists(route);
            if (deleted) {
                log.info("Google Health managed exercise-route file removed");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not remove a Google Health route file", exception);
        }
        resolveIndexedFile(relativePath, indexedFileId)
                .map(IndexedFile::getId)
                .filter(Objects::nonNull)
                .ifPresent(id -> gpxProcessingWorker.processDelete(GPXDirectoryWatcherService.INDEX_GPS, id));
    }

    private Optional<IndexedFile> resolveIndexedFile(String relativePath, Long indexedFileId) {
        if (indexedFileId != null) {
            return indexerRepository.findById(indexedFileId);
        }
        Path relative = Path.of(relativePath).normalize();
        Path parent = relative.getParent();
        String indexedPath = parent == null ? "" : parent.toString().replace('\\', '/');
        return indexerRepository.findByIndexAndNameAndPath(
                GPXDirectoryWatcherService.INDEX_GPS,
                relative.getFileName().toString(),
                indexedPath);
    }

    private Path managedRouteDirectory() {
        Path managedDirectory = watchDirectory.resolve(properties.getRouteDirectory()).normalize();
        if (managedDirectory.equals(watchDirectory) || !managedDirectory.startsWith(watchDirectory)) {
            throw new IllegalArgumentException("Google Health route directory must be a subdirectory of the GPS watch directory");
        }
        return managedDirectory;
    }

    private Path stagingDirectory() {
        String configuredDirectory = properties.getStagingDirectory();
        Path stagingDirectory;
        if (configuredDirectory != null && !configuredDirectory.isBlank()) {
            stagingDirectory = Path.of(configuredDirectory).toAbsolutePath().normalize();
        } else {
            // Keep atomic publication on the route mount, including a bind-mounted GPS directory.
            // Hidden .part files are excluded by the GPS indexer's name and format matchers.
            return managedRouteDirectory().resolve(DEFAULT_STAGING_DIRECTORY);
        }
        if (stagingDirectory.startsWith(watchDirectory)) {
            throw new IllegalArgumentException(
                    "Google Health staging directory must be outside the GPS watch directory");
        }
        return stagingDirectory;
    }

    private String stableRelativePath(String resourceName) {
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("Google Health exercise has no resource name");
        }
        String fileName = StableFingerprint
                                  .of(resourceName)
                                  .substring(0, ROUTE_FILE_HASH_LENGTH) + TCX_EXTENSION;
        return Path.of(properties.getRouteDirectory()).resolve(fileName).toString().replace('\\', '/');
    }

    private Path resolveManagedPath(String relativePath) {
        Path resolved = watchDirectory.resolve(relativePath).normalize();
        Path managedDirectory = managedRouteDirectory();
        if (!resolved.startsWith(managedDirectory) || !resolved.getFileName().toString().endsWith(TCX_EXTENSION)) {
            throw new IllegalArgumentException("Invalid Google Health route path");
        }
        return resolved;
    }

    private static void requireSameFileStore(Path stagingDirectory, Path routeDirectory) throws IOException {
        FileStore stagingStore = Files.getFileStore(stagingDirectory);
        FileStore routeStore = Files.getFileStore(routeDirectory);
        if (!stagingStore.equals(routeStore)) {
            throw new IOException("Google Health TCX staging and route directories are on different filesystems");
        }
    }

    private static void verifyAtomicMoveSupport(Path stagingDirectory, Path routeDirectory) throws IOException {
        requireSameFileStore(stagingDirectory, routeDirectory);
        Path source = Files.createTempFile(
                stagingDirectory, ATOMIC_MOVE_PROBE_PREFIX, ATOMIC_MOVE_PROBE_SUFFIX);
        Path target = routeDirectory.resolve(source.getFileName());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic TCX publication is not supported on the configured filesystem", exception);
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
        }
    }

    private static void checkCancelled(SyncCoordinator.Progress progress) {
        if (Thread.currentThread().isInterrupted() || (progress != null && progress.isCancelled())) {
            throw new CancellationException("Google Health route import was cancelled");
        }
    }

    private static String safeError(Throwable throwable) {
        if (throwable instanceof ApiException exception) {
            if (exception.getStatusCode() == 403) {
                return "Google denied route export. Check activity and location access in Settings.";
            }
            if (exception.getStatusCode() == 401) {
                return "Google Health authorization needs attention. Check the connection in Settings.";
            }
            return "Google route download failed (HTTP " + exception.getStatusCode() + ").";
        }
        if (throwable instanceof IllegalArgumentException) {
            return "The exported route is invalid.";
        }
        return STORAGE_ERROR;
    }

    private enum RouteImportResult {
        PUBLISHED,
        NO_ROUTE,
        FAILED
    }
}
