package com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import com.x8ing.mtl.server.mtlserver.energy.EnergyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ActivityTypeClassifierJob {

    private final GpsTrackRepository gpsTrackRepository;
    private final GpsTrackVariantSelector gpsTrackVariantSelector;
    private final ActivityTypeAutoClassifier activityTypeAutoClassifier;
    private final EnergyService energyService;
    private final SystemLogService systemLogService;

    public ActivityTypeClassifierJob(GpsTrackRepository gpsTrackRepository, GpsTrackVariantSelector gpsTrackVariantSelector, ActivityTypeAutoClassifier activityTypeAutoClassifier, EnergyService energyService, SystemLogService systemLogService) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.gpsTrackVariantSelector = gpsTrackVariantSelector;
        this.activityTypeAutoClassifier = activityTypeAutoClassifier;
        this.energyService = energyService;
        this.systemLogService = systemLogService;
    }

    public void run() {
        long t0 = System.currentTimeMillis();
        log.debug("Start find activity classifier job");


        List<GpsTrack> tracks = gpsTrackRepository.findByActivityTypeSourceNull();
        if (!tracks.isEmpty()) {

            // ignore the once which are not yet duplicate check. do them later, as they might not have all the merged data
            List<GpsTrack> tracksFiltered = tracks.stream()
                    .filter(gpsTrack -> gpsTrack.getDuplicateStatus() != null && gpsTrack.getDuplicateStatus() != GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET)
                    .toList();

            if (!tracksFiltered.isEmpty()) {

                log.info("Activity type classifier job has pending work. Starting now");

                tracksFiltered.forEach(track -> {
                    try {
                        // Classify on the full-density cleaned variant (was: SIMPLIFIED@1m)
                        // so dense 1-Hz sections aren't downsampled before activity detection.
                        GpsTrack.ACTIVITY_TYPE previousType = track.getActivityType();
                        List<GpsTrackDataPoint> trackPoints = gpsTrackVariantSelector.pointsForMetrics(track.getId());

                        // classifyActivity commits in its own REQUIRES_NEW transaction.
                        GpsTrack.ACTIVITY_TYPE determinedType = activityTypeAutoClassifier.classifyActivity(track, trackPoints);

                        // Now the classification is committed and visible to new transactions.
                        // Trigger energy recalc if the activity type actually changed (covers null → X).
                        if (determinedType != null && determinedType != previousType) {
                            try {
                                energyService.recalculateEnergyForTrack(track.getId(), energyService.getDefaultParameters());
                            } catch (Exception e) {
                                log.warn("Energy recalc after classification failed for trackId={}: {}", track.getId(), e.getMessage(), e);
                            }
                        }
                    } catch (Exception e) {
                        String errMsg = "Unexpected exception while classifying activity. e=" + e.toString();
                        log.error(errMsg, e);
                        systemLogService.saveLogForException(this.getClass(), "ClassifierException", errMsg, e);
                    }

                });
                log.info(String.format("Completed ActivityTypeClassifier job in %d seconds for %d tracks.", (System.currentTimeMillis() - t0) / 1000, tracks.size()));

            }


        }
    }


}
