package com.x8ing.mtl.server.mtlserver.jobs.duplicate;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class DuplicateDetectorJob {

    private final GpsTrackRepository gpsTrackRepository;

    private final DuplicateDetectorAtomicWorker duplicateDetectorAtomicWorker;

    @Value("${mtl.duplicate.time-tolerance}")
    private Duration timeTolerance;

    @Value("${mtl.duplicate.distance-tolerance}")
    private Double distanceTolerance;

    public DuplicateDetectorJob(GpsTrackRepository gpsTrackRepository, DuplicateDetectorAtomicWorker duplicateDetectorAtomicWorker) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.duplicateDetectorAtomicWorker = duplicateDetectorAtomicWorker;
    }

    public void run() {
        long t0 = System.currentTimeMillis();
        log.debug("Start find duplicate job");

        if (timeTolerance == null) {
            String configError = "TimeTolerance not configured for DuplicateDetector (mtl.duplicate.time-tolerance).";
            throw new RuntimeException(configError);
        }

        if (distanceTolerance == null || distanceTolerance <= 0) {
            String configError = "distanceTolerance not configured for DuplicateDetector (mtl.duplicate.distance-tolerance).";
            throw new RuntimeException(configError);
        }

        List<GpsTrack> tracks = gpsTrackRepository.findByDuplicateStatusExcludingPlanned(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET);
        if (!tracks.isEmpty()) {
            log.info("Need to check for duplicates. tracksToBeChecked=" + tracks.size() + " timeTolerance: " + timeTolerance);

            for (GpsTrack track : tracks) {
                duplicateDetectorAtomicWorker.processOne(track.getId(), timeTolerance, distanceTolerance);
            }

            log.info(String.format("Completed found duplicates in %d seconds for %d tracks.", (System.currentTimeMillis() - t0) / 1000, tracks.size()));
        }
    }


}
