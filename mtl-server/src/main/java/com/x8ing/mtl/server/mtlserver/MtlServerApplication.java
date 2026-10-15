package com.x8ing.mtl.server.mtlserver;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype.ActivityTypeClassifierJob;
import com.x8ing.mtl.server.mtlserver.jobs.duplicate.DuplicateDetectorJob;
import com.x8ing.mtl.server.mtlserver.jobs.exploration.ExplorationScoreJob;
import com.x8ing.mtl.server.mtlserver.jobs.garminexport.GarminExporter;
import com.x8ing.mtl.server.mtlserver.jobs.sqlformat.LiquibaseIndentFixerJob;
import com.x8ing.mtl.server.mtlserver.trackreview.GpsQualityModules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.EnableTransactionManagement;


@SpringBootApplication
@EnableAsync
@EnableTransactionManagement
@EnableScheduling
@Slf4j
@EnableConfigurationProperties
@JsonPropertyOrder({
        "duplicateDetectorJob",
        "activityTypeClassifierJob",
        "liquibaseIndentFixerJob",
        "garminExporter",
        "explorationScoreJob",
        "backgroundJobs"
})
public class MtlServerApplication {

    private final DuplicateDetectorJob duplicateDetectorJob;

    private final ActivityTypeClassifierJob activityTypeClassifierJob;



    private final LiquibaseIndentFixerJob liquibaseIndentFixerJob;

    private final GarminExporter garminExporter;

    private final ExplorationScoreJob explorationScoreJob;
    private final ShutdownSignal shutdownSignal;
    private final BackgroundJobRunner backgroundJobs;


    public MtlServerApplication(DuplicateDetectorJob duplicateDetectorJob,
                                ActivityTypeClassifierJob activityTypeClassifierJob,
                                LiquibaseIndentFixerJob liquibaseIndentFixerJob,
                                GarminExporter garminExporter,
                                ExplorationScoreJob explorationScoreJob,
                                ShutdownSignal shutdownSignal, BackgroundJobRunner backgroundJobs) {
        this.duplicateDetectorJob = duplicateDetectorJob;
        this.activityTypeClassifierJob = activityTypeClassifierJob;
        this.liquibaseIndentFixerJob = liquibaseIndentFixerJob;
        this.garminExporter = garminExporter;
        this.explorationScoreJob = explorationScoreJob;
        this.shutdownSignal = shutdownSignal;
        this.backgroundJobs = backgroundJobs;
    }

    public static void main(String[] args) {
        GpsQualityModules.verify();
        SpringApplication.run(MtlServerApplication.class, args);
    }

    @Scheduled(fixedDelayString = "PT20S", initialDelayString = "PT5S")
    public void findDuplicates() {
        backgroundJobs.runReported(duplicateDetectorJob, duplicateDetectorJob::reportedBatch);
    }

    @Scheduled(fixedDelayString = "PT600S", initialDelayString = "PT3S")
    public void scheduleLiquibaseIndentFixerJob() {
        if (shutdownSignal.isStopRequested()) return;
        liquibaseIndentFixerJob.run();
    }

    @Scheduled(fixedDelayString = "PT20S", initialDelayString = "PT5S")
    public void scheduleJobClassifyActivityType() {
        backgroundJobs.runReported(activityTypeClassifierJob, activityTypeClassifierJob::reportedBatch);
    }

    /**
     * Garmin export job runs every 3 days at 3:30 AM.
     * The GarminExporter will check if the GPS indexer has completed its initial scan
     * before running to avoid downloading files that are already indexed locally.
     */
    @Scheduled(cron = "0 30 3 */3 * ?")
    public void scheduleGarminJob() throws Exception {
        if (shutdownSignal.isStopRequested()) return;
        garminExporter.run();
    }

    /**
     * Idle checks retain the configured interval; pending batches yield and continue immediately.
     */
    @Scheduled(fixedDelayString = "${mtl.exploration.run-schedule:PT120S}", initialDelayString = "PT30S")
    public void scheduleExplorationScoreJob() {
        backgroundJobs.runReported(explorationScoreJob, explorationScoreJob::reportedBatch);
    }

}
