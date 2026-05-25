package com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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
@JsonPropertyOrder({
        "gpsTrackRepository",
        "gpsTrackVariantSelector",
        "activityTypeAutoClassifier",
        "energyService",
        "systemLogService"
})
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


        List<Long> trackIds = gpsTrackRepository.findIdsPendingActivityClassification();
        if (!trackIds.isEmpty()) {
            log.info("Activity type classifier job has pending work. Starting now");

            trackIds.forEach(trackId -> {
                try {
                    // Classify on the full-density cleaned variant (was: SIMPLIFIED@1m)
                    // so dense 1-Hz sections aren't downsampled before activity detection.
                    List<GpsTrackDataPoint> trackPoints = gpsTrackVariantSelector.pointsForMetrics(trackId);

                    ActivityTypeAutoClassifier.ClassificationResult result = activityTypeAutoClassifier.classifyActivity(trackId, trackPoints);

                    // Now the classification is committed and visible to new transactions.
                    // Trigger energy recalc if the activity type actually changed (covers null → X).
                    if (result.updated() && result.determinedType() != null && result.determinedType() != result.previousType()) {
                        try {
                            energyService.recalculateEnergyForTrack(trackId, energyService.getDefaultParameters());
                        } catch (Exception e) {
                            log.warn("Energy recalc after classification failed for trackId={}: {}", trackId, e.getMessage(), e);
                        }
                    }
                } catch (Exception e) {
                    String errMsg = "Unexpected exception while classifying activity. e=" + e;
                    log.error(errMsg, e);
                    systemLogService.saveLogForException(this.getClass(), "ClassifierException", errMsg, e);
                }

            });
            log.info(String.format("Completed ActivityTypeClassifier job in %d seconds for %d tracks.", (System.currentTimeMillis() - t0) / 1000, trackIds.size()));
        }
    }


}
