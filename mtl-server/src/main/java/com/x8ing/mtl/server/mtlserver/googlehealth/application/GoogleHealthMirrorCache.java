package com.x8ing.mtl.server.mtlserver.googlehealth.application;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.GHDataSourceSummary;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.GHTypeSummary;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorSummaryRow;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncCoordinator;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncWindowPolicy;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.GHSynchronizationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Slf4j
public class GoogleHealthMirrorCache {

    private static final Duration ACTIVE_CATALOGUE_REFRESH = Duration.ofMinutes(1);
    private final MirrorRepository repository;
    private final Clock clock;
    private final SyncCoordinator syncCoordinator;
    // Only one full-mirror aggregate at a time on small database installations.
    private final Object aggregateLock = new Object();
    private ConnectorProperties properties = new ConnectorProperties();
    private volatile GHSynchronizationStatus cachedSynchronization;
    private volatile String synchronizationScope;

    @Autowired
    public void configureSynchronization(ConnectorProperties properties) {
        this.properties = properties;
    }

    public GHSynchronizationStatus synchronizationStatus() {
        var now = clock.instant();
        String key = properties.getHistoryStartDate() + "|" + SyncWindowPolicy.cutoff(properties, now)
                     + "|" + syncCoordinator.lastCompletedJobId() + "|" + repository.findConnection().map(c -> c.googleUserId() + ":" + c.authorizationGeneration()).orElse("");
        synchronized (aggregateLock) {
            if (cachedSynchronization == null || !Objects.equals(key, synchronizationScope)
                || !now.isBefore(cachedSynchronization.snapshotAt().plus(ACTIVE_CATALOGUE_REFRESH))) {
                cachedSynchronization = repository.synchronizationStatus(properties, now);
                synchronizationScope = key;
            }
            return cachedSynchronization;
        }
    }

    private volatile CachedAggregate<MirrorSummaryRow> cachedMirrorSummary;
    private volatile CachedAggregate<List<GHTypeSummary>> cachedTypeSummaries;
    private volatile CachedAggregate<List<GHDataSourceSummary>> cachedDataSources;

    @Autowired
    public GoogleHealthMirrorCache(MirrorRepository repository, SyncCoordinator syncCoordinator) {
        this(repository, syncCoordinator, Clock.systemUTC());
    }

    GoogleHealthMirrorCache(MirrorRepository repository, SyncCoordinator syncCoordinator, Clock clock) {
        this.clock = clock;
        this.repository = repository;
        this.syncCoordinator = syncCoordinator;
    }

    public MirrorSummaryRow mirrorSummary(SyncCoordinator.JobSnapshot job) {
        UUID completedJobId = completedJobId(job);
        CachedAggregate<MirrorSummaryRow> current = cachedMirrorSummary;
        if (usable(current, completedJobId, isActive(job))) {
            return current.value();
        }
        synchronized (aggregateLock) {
            current = cachedMirrorSummary;
            if (usable(current, completedJobId, isActive(job))) {
                return current.value();
            }
            cachedMirrorSummary = load("mirror", completedJobId, () -> {
                MirrorSummaryRow loaded = repository.findMirrorSummary();
                return loaded == null ? MirrorSummaryRow.empty() : loaded;
            });
            return cachedMirrorSummary.value();
        }
    }

    public List<GHTypeSummary> typeSummaries() {
        SyncCoordinator.JobSnapshot job = syncCoordinator.status();
        UUID completedJobId = completedJobId(job);
        CachedAggregate<List<GHTypeSummary>> current = cachedTypeSummaries;
        if (catalogueUsable(current, completedJobId, isActive(job))) {
            return current.value();
        }
        synchronized (aggregateLock) {
            current = cachedTypeSummaries;
            if (catalogueUsable(current, completedJobId, isActive(job))) {
                return current.value();
            }
            cachedTypeSummaries = load("types", completedJobId, () -> List.copyOf(repository.findTypeSummaries()));
            return cachedTypeSummaries.value();
        }
    }

    public List<GHDataSourceSummary> dataSources() {
        SyncCoordinator.JobSnapshot job = syncCoordinator.status();
        UUID completedJobId = completedJobId(job);
        CachedAggregate<List<GHDataSourceSummary>> current = cachedDataSources;
        if (catalogueUsable(current, completedJobId, isActive(job))) {
            return current.value();
        }
        synchronized (aggregateLock) {
            current = cachedDataSources;
            if (catalogueUsable(current, completedJobId, isActive(job))) {
                return current.value();
            }
            cachedDataSources = load("sources", completedJobId, () -> List.copyOf(repository.findDataSourceSummaries()));
            return cachedDataSources.value();
        }
    }

    public void clear() {
        synchronized (aggregateLock) {
            cachedSynchronization = null;
            synchronizationScope = null;
            cachedMirrorSummary = null;
            cachedTypeSummaries = null;
            cachedDataSources = null;
        }
    }

    private <T> CachedAggregate<T> load(String aggregate, UUID completedJobId, Supplier<T> loader) {
        Instant startedAt = clock.instant();
        log.info("Google Health aggregate snapshot started: aggregate={}, completedJobId={}", aggregate, completedJobId);
        try {
            T value = loader.get();
            log.info("Google Health aggregate snapshot completed: aggregate={}, durationMs={}",
                    aggregate, Duration.between(startedAt, clock.instant()).toMillis());
            return new CachedAggregate<>(value, completedJobId, clock.instant());
        } catch (RuntimeException exception) {
            log.error("Google Health aggregate snapshot failed: aggregate={}, durationMs={}, errorType={}",
                    aggregate, Duration.between(startedAt, clock.instant()).toMillis(),
                    exception.getClass().getSimpleName(), exception);
            throw exception;
        }
    }

    public Instant mirrorSnapshotAt() {
        CachedAggregate<MirrorSummaryRow> current = cachedMirrorSummary;
        return current == null ? null : current.loadedAt();
    }

    private <T> boolean catalogueUsable(CachedAggregate<T> cached, UUID completedJobId, boolean active) {
        return usable(cached, completedJobId, active)
               && (!active || clock.instant().isBefore(cached.loadedAt().plus(ACTIVE_CATALOGUE_REFRESH)));
    }

    private static <T> boolean usable(CachedAggregate<T> cached, UUID completedJobId, boolean active) {
        // Totals are a verified snapshot, not live progress. Clock-based expiry
        // makes polling repeatedly scan the full mirror even when nothing changed.
        // Keep the snapshot through continuations; refresh once the chain stops.
        return cached != null
               && (active || Objects.equals(completedJobId, cached.completedJobId()));
    }

    private boolean isActive(SyncCoordinator.JobSnapshot job) {
        return syncCoordinator.hasActiveJob()
               || (job != null && (job.state() == SyncCoordinator.JobState.RUNNING
                                   || job.state() == SyncCoordinator.JobState.QUEUED));
    }

    private UUID completedJobId(SyncCoordinator.JobSnapshot job) {
        return job != null && job.completedAt() != null ? job.id() : syncCoordinator.lastCompletedJobId();
    }

    private record CachedAggregate<T>(T value, UUID completedJobId, Instant loadedAt) {
    }
}
