package com.x8ing.mtl.server.mtlserver;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.jobs.ResourceIntensiveJobGuard;
import com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype.ActivityTypeClassifierJob;
import com.x8ing.mtl.server.mtlserver.jobs.demo.DemoPhotoGenerationStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.duplicate.DuplicateDetectorJob;
import com.x8ing.mtl.server.mtlserver.jobs.exploration.ExplorationScoreJob;
import com.x8ing.mtl.server.mtlserver.jobs.garminexport.GarminExporter;
import com.x8ing.mtl.server.mtlserver.jobs.sqlformat.LiquibaseIndentFixerJob;
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
        "resourceIntensiveJobGuard",
        "demoPhotoGenerationStatusService",
        "liquibaseIndentFixerJob",
        "garminExporter",
        "explorationScoreJob"
})
public class MtlServerApplication {

    private final DuplicateDetectorJob duplicateDetectorJob;

    private final ActivityTypeClassifierJob activityTypeClassifierJob;

    private final ResourceIntensiveJobGuard resourceIntensiveJobGuard;

    private final DemoPhotoGenerationStatusService demoPhotoGenerationStatusService;

    private final LiquibaseIndentFixerJob liquibaseIndentFixerJob;

    private final GarminExporter garminExporter;

    private final ExplorationScoreJob explorationScoreJob;


    public MtlServerApplication(DuplicateDetectorJob duplicateDetectorJob,
                                ActivityTypeClassifierJob activityTypeClassifierJob,
                                ResourceIntensiveJobGuard resourceIntensiveJobGuard,
                                DemoPhotoGenerationStatusService demoPhotoGenerationStatusService,
                                LiquibaseIndentFixerJob liquibaseIndentFixerJob,
                                GarminExporter garminExporter,
                                ExplorationScoreJob explorationScoreJob) {
        this.duplicateDetectorJob = duplicateDetectorJob;
        this.activityTypeClassifierJob = activityTypeClassifierJob;
        this.resourceIntensiveJobGuard = resourceIntensiveJobGuard;
        this.demoPhotoGenerationStatusService = demoPhotoGenerationStatusService;
        this.liquibaseIndentFixerJob = liquibaseIndentFixerJob;
        this.garminExporter = garminExporter;
        this.explorationScoreJob = explorationScoreJob;
    }

    public static void main(String[] args) {
        SpringApplication.run(MtlServerApplication.class, args);
    }

    @Scheduled(fixedDelayString = "PT20S", initialDelayString = "PT5S")
    public void findDuplicates() {
        runResourceIntensiveJob(DuplicateDetectorJob.class, duplicateDetectorJob::run);
    }

    @Scheduled(fixedDelayString = "PT600S", initialDelayString = "PT3S")
    public void scheduleLiquibaseIndentFixerJob() {
        liquibaseIndentFixerJob.run();
    }

    @Scheduled(fixedDelayString = "PT20S", initialDelayString = "PT5S")
    public void scheduleJobClassifyActivityType() {
        runResourceIntensiveJob(
                ActivityTypeClassifierJob.class,
                activityTypeClassifierJob::run);
    }

    /**
     * Garmin export job runs every 3 days at 3:30 AM.
     * The GarminExporter will check if the GPS indexer has completed its initial scan
     * before running to avoid downloading files that are already indexed locally.
     */
    @Scheduled(cron = "0 30 3 */3 * ?")
    public void scheduleGarminJob() throws Exception {
        garminExporter.run();
    }

    /**
     * Exploration score job runs every 2 minutes. If a full batch was processed
     * (more work pending), the job immediately re-runs without waiting.
     */
    @Scheduled(fixedDelayString = "${mtl.exploration.run-schedule:PT120S}", initialDelayString = "PT30S")
    public void scheduleExplorationScoreJob() {
        runResourceIntensiveJob(ExplorationScoreJob.class, () -> {
            boolean moreWork = true;
            while (moreWork) {
                moreWork = explorationScoreJob.run();
            }
        });
    }

    private void runResourceIntensiveJob(Class<?> jobType, Runnable action) {
        if (demoPhotoGenerationStatusService.isGenerationInProgress()) {
            log.debug("Skipping {} while demo photo generation is running", jobType.getSimpleName());
            return;
        }
        resourceIntensiveJobGuard.runIfAvailable(jobType, action);
    }

}
