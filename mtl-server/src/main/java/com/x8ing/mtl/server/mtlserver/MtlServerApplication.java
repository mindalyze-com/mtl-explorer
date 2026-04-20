package com.x8ing.mtl.server.mtlserver;

import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype.ActivityTypeClassifierJob;
import com.x8ing.mtl.server.mtlserver.jobs.duplicate.DuplicateDetectorJob;
import com.x8ing.mtl.server.mtlserver.jobs.exploration.ExplorationScoreJob;
import com.x8ing.mtl.server.mtlserver.jobs.garminexport.GarminExporter;
import com.x8ing.mtl.server.mtlserver.jobs.sqlformat.LiquibaseIndentFixerJob;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.CoordinateXY;
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
public class MtlServerApplication {

    private final DuplicateDetectorJob duplicateDetectorJob;

    private final ActivityTypeClassifierJob activityTypeClassifierJob;

    private final LiquibaseIndentFixerJob liquibaseIndentFixerJob;

    private final GarminExporter garminExporter;

    private final ExplorationScoreJob explorationScoreJob;


    public MtlServerApplication(DuplicateDetectorJob duplicateDetectorJob, ActivityTypeClassifierJob activityTypeClassifierJob, LiquibaseIndentFixerJob liquibaseIndentFixerJob, GarminExporter garminExporter, ExplorationScoreJob explorationScoreJob) {
        this.duplicateDetectorJob = duplicateDetectorJob;
        this.activityTypeClassifierJob = activityTypeClassifierJob;
        this.liquibaseIndentFixerJob = liquibaseIndentFixerJob;
        this.garminExporter = garminExporter;
        this.explorationScoreJob = explorationScoreJob;
    }

    public static void main(String[] args) {
        SpringApplication.run(MtlServerApplication.class, args);
    }

    @Scheduled(fixedDelayString = "PT20S", initialDelayString = "PT5S")
    public void findDuplicates() {
        duplicateDetectorJob.run();
    }

    @Scheduled(fixedDelayString = "PT600S", initialDelayString = "PT3S")
    public void scheduleLiquibaseIndentFixerJob() {
        liquibaseIndentFixerJob.run();
    }

    @Scheduled(fixedDelayString = "PT20S", initialDelayString = "PT5S")
    public void scheduleJobClassifyActivityType() {
        activityTypeClassifierJob.run();
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
        boolean moreWork = true;
        while (moreWork) {
            moreWork = explorationScoreJob.run();
        }
    }

    @PostConstruct
    public void warmUp() {
        // really weired error. not sure if it helps, only happens on NAS
        // : Servlet.service() for servlet [dispatcherServlet] in context with path [/mtl] threw exception [Handler dispatch failed: java.lang.NoClassDefFoundError: Could not initialize class org.geotools.referencing.crs.DefaultGeographicCRS] with root cause
        //java.lang.ExceptionInInitializerError: Exception java.lang.ExceptionInInitializerError [in thread "ForkJoinPool.commonPool-worker-2"]

        double sillyCalc = GPXReader.getDistanceBetweenTwoWGS84(new CoordinateXY(1, 2), new CoordinateXY(2, 3));
        log.info("Completed warmup for Geotools caching. sillyCalc=%f".formatted(sillyCalc));
    }

}
