package com.x8ing.mtl.server.mtlserver.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackProcessingStatusDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService.INDEX_GPS;
import static com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackProcessingStatusDto.Stage.*;
import static com.x8ing.mtl.server.mtlserver.web.services.track.entity.TrackProcessingStatusDto.State.*;

/**
 * Shared snapshot: map lookups never query processing progress.
 */
@Service
@Slf4j
public class TrackProcessingStatusService {
    public static final long REFRESH_INTERVAL_MS = 8_000;
    private static final Duration MAX_SNAPSHOT_AGE = Duration.ofMillis(REFRESH_INTERVAL_MS * 3);
    private static final List<IndexedFile.IndexerStatus> PENDING_STATUSES =
            List.of(IndexedFile.IndexerStatus.SCHEDULED, IndexedFile.IndexerStatus.PROCESSING, IndexedFile.IndexerStatus.RESCHEDULED);

    private final IndexerRepository indexerRepository;
    private final GpsTrackRepository gpsTrackRepository;
    private final Clock clock;
    // Until the GPS watcher registers, initial discovery may not have scheduled its files yet.
    private volatile BooleanSupplier gpsScanActive = () -> true;
    private volatile TrackProcessingStatusDto snapshot = new TrackProcessingStatusDto(UNAVAILABLE, List.of(), 0, null);
    private long workGeneration;

    @Autowired
    public TrackProcessingStatusService(IndexerRepository indexerRepository, GpsTrackRepository gpsTrackRepository) {
        this(indexerRepository, gpsTrackRepository, Clock.systemUTC());
    }

    TrackProcessingStatusService(IndexerRepository indexerRepository, GpsTrackRepository gpsTrackRepository, Clock clock) {
        this.indexerRepository = indexerRepository;
        this.gpsTrackRepository = gpsTrackRepository;
        this.clock = clock;
    }

    public void registerGpsScanState(BooleanSupplier scanActive) {
        gpsScanActive = scanActive;
        markGpsProcessingStarted();
    }

    /**
     * Called before scheduling work, including initial discovery and rescans. Memory only.
     */
    public synchronized void markGpsProcessingStarted() {
        workGeneration++;
        var stages = new ArrayList<>(snapshot.stages());
        if (!stages.contains(GPS_INDEXING)) stages.addFirst(GPS_INDEXING);
        publish(new TrackProcessingStatusDto(RUNNING, stages, snapshot.failedGpsFiles(), clock.instant()));
    }

    public TrackProcessingStatusDto getSnapshot() {
        var current = snapshot;
        if (current.checkedAt() == null || Duration.between(current.checkedAt(), clock.instant()).compareTo(MAX_SNAPSHOT_AGE) > 0) {
            return new TrackProcessingStatusDto(UNAVAILABLE, List.of(), current.failedGpsFiles(), current.checkedAt());
        }
        return current;
    }

    @Scheduled(fixedDelay = REFRESH_INTERVAL_MS)
    public void refresh() {
        long generation;
        synchronized (this) {
            generation = workGeneration;
        }
        try {
            long pending = indexerRepository.countByIndexAndIndexerStatusIn(INDEX_GPS, PENDING_STATUSES);
            long failed = indexerRepository.countByIndexAndIndexerStatus(INDEX_GPS, IndexedFile.IndexerStatus.FAILED);
            boolean duplicates = gpsTrackRepository.existsByTrackSourceAndDuplicateStatus(
                    GpsTrack.TRACK_SOURCE.IMPORTED, GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET);
            long activities = gpsTrackRepository.countActivityTypePending();
            var stages = new ArrayList<TrackProcessingStatusDto.Stage>();
            synchronized (this) {
                // A new scan/file appearing during the DB reads must not be overwritten by READY.
                if (pending > 0 || gpsScanActive.getAsBoolean() || generation != workGeneration) stages.add(GPS_INDEXING);
                if (duplicates) stages.add(DUPLICATE_CHECKING);
                if (activities > 0) stages.add(ACTIVITY_CLASSIFICATION);
                publish(new TrackProcessingStatusDto(stages.isEmpty() ? READY : RUNNING, stages, failed, clock.instant()));
            }
        } catch (Exception e) {
            log.warn("Track processing status refresh failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            log.debug("Track processing status refresh failure", e);
            synchronized (this) {
                publish(new TrackProcessingStatusDto(UNAVAILABLE, List.of(), snapshot.failedGpsFiles(), clock.instant()));
            }
        }
    }

    private void publish(TrackProcessingStatusDto next) {
        var previous = snapshot;
        snapshot = next;
        if (previous.state() != next.state() || !previous.stages().equals(next.stages()) || previous.failedGpsFiles() != next.failedGpsFiles()) {
            log.info("Track processing status: state={} stages={} failedGpsFiles={}", next.state(), next.stages(), next.failedGpsFiles());
        }
    }
}
