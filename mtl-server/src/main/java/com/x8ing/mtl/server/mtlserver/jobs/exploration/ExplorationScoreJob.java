package com.x8ing.mtl.server.mtlserver.jobs.exploration;

import com.x8ing.mtl.server.mtlserver.db.entity.config.ConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.config.ConfigRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class ExplorationScoreJob {

    // Config table keys for storing the last-used algorithm parameters.
    // If these values change between runs, all CALCULATED tracks are reset for recalculation.
    private static final String CONFIG_DOMAIN1 = "exploration";
    private static final String CONFIG_DOMAIN2 = "algo";
    private static final String CONFIG_DOMAIN3_CORRIDOR = "corridorWidthM";
    private static final String CONFIG_DOMAIN3_PRECISION = "useTrackPrecision";

    private final GpsTrackRepository gpsTrackRepository;
    private final ExplorationScoreAtomicWorker atomicWorker;
    private final ConfigRepository configRepository;
    private final IndexerStatusService indexerStatusService;
    private final TransactionTemplate transactionTemplate;

    /**
     * Radius in meters defining "known territory" around prior tracks.
     * Changing this value invalidates all previously computed scores (detected via config table snapshot).
     *
     * @see ExplorationScoreAtomicWorker#corridorWidthM
     */
    @Value("${mtl.exploration.corridor-width-m:25.0}")
    private double corridorWidthM;

    /**
     * Simplified track precision level in meters (maps to gps_track_data.precision_in_meter).
     * Changing this value invalidates all previously computed scores.
     *
     * @see ExplorationScoreAtomicWorker#useTrackPrecision
     */
    @Value("${mtl.exploration.use-track-precision:10}")
    private int useTrackPrecision;

    /**
     * Maximum number of tracks to process in a single job run. Prevents the scheduler thread
     * from being blocked too long during initial backfill. If the batch is full, the scheduler
     * immediately re-runs (while loop in MtlServerApplication) without waiting for the next interval.
     */
    @Value("${mtl.exploration.max-tracks-per-run:20}")
    private int maxTracksPerRun;

    public ExplorationScoreJob(GpsTrackRepository gpsTrackRepository,
                               ExplorationScoreAtomicWorker atomicWorker,
                               ConfigRepository configRepository,
                               IndexerStatusService indexerStatusService,
                               TransactionTemplate transactionTemplate) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.atomicWorker = atomicWorker;
        this.configRepository = configRepository;
        this.indexerStatusService = indexerStatusService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Main entry called by the scheduler. Returns true if a full batch was processed
     * (meaning there may be more work to do immediately).
     * <p>
     * Internally manages its own transaction boundaries:
     * <ol>
     *   <li>Transaction 1: guard check, config change detection, bulk invalidation of stale tracks → commit (releases row locks)</li>
     *   <li>Transaction 2: fetch pending batch and process each track (atomicWorker uses its own REQUIRES_NEW per track)</li>
     * </ol>
     */
    public boolean run() {
        // Transaction 1: preparation — guard + invalidation, committed before processing starts
        Boolean shouldProceed = transactionTemplate.execute(status -> {
            if (indexerStatusService.hasIndexPendingWork(GPXDirectoryWatcherService.INDEX_GPS)) {
                log.info("Exploration score job: GPS indexing still active, skipping this run.");
                return false;
            }

            checkConfigChange();

            int invalidated = gpsTrackRepository.invalidateCalculatedTracksOverlappingScheduled();
            if (invalidated > 0) {
                log.info("Exploration score job: invalidated {} calculated tracks overlapping newly scheduled tracks.", invalidated);
            }
            return true;
        });

        if (!Boolean.TRUE.equals(shouldProceed)) {
            return false;
        }

        // Transaction 2: short read-only transaction to fetch the pending batch.
        // Deliberately closed before processing begins so the DB connection is not held
        // idle-in-transaction across the (potentially 100+ second) processing loop.
        // Each track is processed by atomicWorker.processOne with its own REQUIRES_NEW transaction.
        List<GpsTrack> tracks = transactionTemplate.execute(status -> {
            List<GpsTrack> result = gpsTrackRepository.findByExplorationStatusIn(
                    List.of(GpsTrack.EXPLORATION_STATUS.SCHEDULED, GpsTrack.EXPLORATION_STATUS.NEEDS_RECALCULATION),
                    PageRequest.of(0, maxTracksPerRun)
            );
            // Detach all entities so the outer Hibernate session releases them cleanly.
            // processOne will re-read each track inside its own REQUIRES_NEW transaction.
            return result;
        });

        if (tracks == null || tracks.isEmpty()) {
            log.debug("Exploration score job: no pending tracks.");
            return false;
        }

        log.info("Exploration score job: processing {} tracks (max={})", tracks.size(), maxTracksPerRun);

        long t0 = System.currentTimeMillis();
        for (GpsTrack track : tracks) {
            atomicWorker.processOne(track.getId());
        }
        long durationMs = System.currentTimeMillis() - t0;
        log.info("Exploration score job completed: {} tracks in {}ms", tracks.size(), durationMs);

        return tracks.size() >= maxTracksPerRun;
    }

    /**
     * Compare current algorithm config against stored snapshot. If changed, reset all
     * CALCULATED tracks to NEEDS_RECALCULATION and update the stored snapshot.
     */
    private void checkConfigChange() {
        String currentCorridorStr = String.valueOf(corridorWidthM);
        String currentPrecisionStr = String.valueOf(useTrackPrecision);

        String storedCorridor = getConfigValue(CONFIG_DOMAIN3_CORRIDOR);
        String storedPrecision = getConfigValue(CONFIG_DOMAIN3_PRECISION);

        boolean changed = false;
        if (storedCorridor == null || !storedCorridor.equals(currentCorridorStr)) {
            changed = true;
        }
        if (storedPrecision == null || !storedPrecision.equals(currentPrecisionStr)) {
            changed = true;
        }

        if (changed) {
            int reset = gpsTrackRepository.resetAllExplorationScores();
            log.info("Exploration config changed (corridor={}→{}, precision={}→{}). Reset {} tracks for recalculation.",
                    storedCorridor, currentCorridorStr, storedPrecision, currentPrecisionStr, reset);
            storeConfigValue(CONFIG_DOMAIN3_CORRIDOR, currentCorridorStr);
            storeConfigValue(CONFIG_DOMAIN3_PRECISION, currentPrecisionStr);
        }
    }

    private String getConfigValue(String domain3) {
        List<ConfigEntity> configs = configRepository.findConfigEntitiesByDomain1AndDomain2AndDomain3(
                CONFIG_DOMAIN1, CONFIG_DOMAIN2, domain3);
        return configs.isEmpty() ? null : configs.getFirst().getValue();
    }

    private void storeConfigValue(String domain3, String value) {
        List<ConfigEntity> configs = configRepository.findConfigEntitiesByDomain1AndDomain2AndDomain3(
                CONFIG_DOMAIN1, CONFIG_DOMAIN2, domain3);
        ConfigEntity config;
        if (configs.isEmpty()) {
            config = new ConfigEntity();
            config.domain1 = CONFIG_DOMAIN1;
            config.domain2 = CONFIG_DOMAIN2;
            config.domain3 = domain3;
        } else {
            config = configs.getFirst();
        }
        config.value = value;
        config.updateDate = new Date();
        configRepository.save(config);
    }

}
