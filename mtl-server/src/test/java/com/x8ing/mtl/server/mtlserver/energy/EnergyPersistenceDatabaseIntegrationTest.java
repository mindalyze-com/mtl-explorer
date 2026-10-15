package com.x8ing.mtl.server.mtlserver.energy;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype.ActivityTypeAutoClassifier;
import com.x8ing.mtl.server.mtlserver.jobs.duplicate.DuplicateDetectorAtomicWorker;
import com.x8ing.mtl.server.mtlserver.jobs.exploration.ExplorationScoreAtomicWorker;
import com.x8ing.mtl.server.mtlserver.jobs.exploration.ExplorationScoreQueryRepository;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.testsupport.NoScheduledJobs;
import com.x8ing.mtl.server.mtlserver.web.services.track.TracksController;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * Opt-in real PostGIS checks; uses only synthetic coordinates in a disposable database.
 */
@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.google-health.enabled=false",
        "mtl.garmin-sync.enabled=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
@Import(NoScheduledJobs.class)
@EnabledIfSystemProperty(named = "mtl.test.energy.jdbc-url", matches = "jdbc:postgresql:.*")
class EnergyPersistenceDatabaseIntegrationTest {
    private static final int POINT_COUNT = 1_001;
    private static final int CONCURRENT_WORK_TIMEOUT_SECONDS = 20;
    private static final double STORED_POWER_TOLERANCE_WATTS = 0.005;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EnergyService energy;
    @Autowired
    TracksController controller;
    @Autowired
    EnergyRecalculationQueue energyQueue;
    @Autowired
    ExplorationScoreAtomicWorker exploration;
    @Autowired
    DuplicateDetectorAtomicWorker duplicates;
    @MockitoSpyBean
    GpsTrackRepository tracks;
    @MockitoSpyBean
    ExplorationScoreQueryRepository explorationQueries;
    @Autowired
    GpsTrackDataPointRepository points;
    @Autowired
    EntityManager em;
    @Autowired
    EntityManagerFactory entityManagerFactory;
    @Autowired
    PlatformTransactionManager transactionManager;
    @MockitoBean
    GPXDirectoryWatcherService watcher;
    @MockitoBean
    MediaIndexerService mediaIndexer;
    private final List<Long> trackIds = new ArrayList<>();
    private long trackId;
    private long dataId;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getProperty("mtl.test.energy.jdbc-url"));
        properties.add("spring.datasource.username", () -> "review");
        properties.add("spring.datasource.password", () -> "review");
        properties.add("mtl.db-readonly.username", () -> "review");
        properties.add("mtl.db-readonly.password", () -> "review");
    }

    @BeforeEach
    void seed() {
        dataId = createData("RAW_OUTLIER_CLEANED");
        trackId = trackIds.getLast();
        jdbc.update("""
                INSERT INTO gps_track_data_points(gps_track_data_id,point_index,point_index_max,moving_window_in_sec,
                    point_long_lat,point_xy,point_altitude,point_timestamp,interval_status,motion_state,motion_reason,
                    distance_in_meter_between_points,distance_in_meter_since_start,duration_between_points_in_sec,
                    duration_since_start,ascent_in_meter_between_points,speed_in_kmh_moving_window)
                SELECT ?,i,?,90,ST_SetSRID(ST_MakePoint(8+i*0.000001,47),4326),
                    ST_SetSRID(ST_MakePoint(890000+i,5900000),3857),500,
                    timestamp '2020-01-01 10:00:00'+i*interval '1 second','OBSERVED','MOVING',NULL,
                    1,i,1,i,0,3.6 FROM generate_series(0,?) i
                """, dataId, POINT_COUNT - 1, POINT_COUNT - 1);
        clearQueue();
    }

    @AfterEach
    void cleanup() {
        for (long id : trackIds) {
            jdbc.update("DELETE FROM gps_track_data_points WHERE gps_track_data_id IN (SELECT id FROM gps_track_data WHERE gps_track_id=?)", id);
            jdbc.update("DELETE FROM gps_track_data WHERE gps_track_id=?", id);
            jdbc.update("DELETE FROM gps_track WHERE id=?", id);
        }
        clearQueue();
        trackIds.clear();
    }

    @Test
    void writesAllEightEnergyFieldsAcrossChunksWithoutFlushingSourceChanges() {
        var before = sourceValues();
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var inputs = points.findEnergyInputPoints(dataId);
            for (int i = 0; i < inputs.size(); i++) {
                var point = inputs.get(i);
                point.setPointAltitude(999d); // Read-only input changes must never leak into a flush.
                point.setEnergyGravitationalWh(i == 0 ? null : -0.125);
                point.setEnergyAeroDragWh(0.25);
                point.setEnergyRollingResistanceWh(0.5);
                point.setEnergyKineticWh(-0.75);
                point.setEnergyTotalWh(1.125);
                point.setEnergyCumulativeWh(i * 1.125);
                point.setPowerWatts(i == 0 ? null : 4050d);
                point.setEnergyLimited(i == 0 ? null : i % 2 == 0);
            }
            points.saveEnergyPoints(dataId, inputs);
            em.flush();
        });
        assertThat(sourceValues()).isEqualTo(before);
        assertThat(stats.getEntityStatistics(GpsTrackDataPoint.class.getName()).getUpdateCount()).isZero();
        var stored = points.findAllByGpsTrackDataIdOrderByPointIndexAsc(dataId);
        assertThat(stored).hasSize(POINT_COUNT);
        for (int i = 0; i < stored.size(); i++) {
            var point = stored.get(i);
            assertThat(point.getEnergyGravitationalWh()).isEqualTo(i == 0 ? null : -0.125);
            assertThat(point.getEnergyAeroDragWh()).isEqualTo(0.25);
            assertThat(point.getEnergyRollingResistanceWh()).isEqualTo(0.5);
            assertThat(point.getEnergyKineticWh()).isEqualTo(-0.75);
            assertThat(point.getEnergyTotalWh()).isEqualTo(1.125);
            assertThat(point.getEnergyCumulativeWh()).isEqualTo(i * 1.125);
            assertThat(point.getPowerWatts()).isEqualTo(i == 0 ? null : 4050d);
            assertThat(point.getEnergyLimited()).isEqualTo(i == 0 ? null : i % 2 == 0);
        }
        assertThat(queued()).isZero();
    }

    @Test
    void lateChunkFailureRollsBackEarlierPointWrites() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var inputs = points.findEnergyInputPoints(dataId);
            inputs.forEach(point -> point.setEnergyTotalWh(5d));
            var missing = new GpsTrackDataPoint();
            missing.setId(-1L);
            missing.setEnergyTotalWh(5d);
            inputs.set(inputs.size() - 1, missing);
            points.saveEnergyPoints(dataId, inputs);
        })).isInstanceOf(RuntimeException.class).hasMessageContaining("update count mismatch");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gps_track_data_points WHERE gps_track_data_id=? AND energy_total_wh IS NOT NULL", Long.class, dataId)).isZero();
    }

    @Test
    void serviceCommitsSummaryAndPointsWithoutHibernatePointUpdates() {
        var before = trackValuesExceptEnergy();
        var expected = energy.calculateAndPopulatePoints(points.findEnergyInputPoints(dataId),
                GpsTrack.ACTIVITY_TYPE.BICYCLE, energy.parametersForTrack(tracks.findById(trackId).orElseThrow()));
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        assertThat(energy.recalculateEnergyForTrack(trackId, null)).isTrue();
        assertThat(jdbc.queryForObject("SELECT energy_net_total_wh FROM gps_track WHERE id=?", Double.class, trackId)).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gps_track_data_points WHERE gps_track_data_id=? AND energy_total_wh IS NOT NULL", Long.class, dataId)).isEqualTo(POINT_COUNT);
        assertThat(stats.getEntityStatistics(GpsTrackDataPoint.class.getName()).getUpdateCount()).isZero();
        assertThat(stats.getEntityStatistics(GpsTrack.class.getName()).getUpdateCount()).isZero();
        assertThat(trackValuesExceptEnergy()).isEqualTo(before);
        var stored = tracks.findById(trackId).orElseThrow();
        assertThat(stored.getMetabolicEnergy()).isEqualTo(expected.getMetabolicEnergy());
        assertThat(stored.getEnergyWeightKgUsed()).isEqualTo(expected.getWeightKgUsed());
        assertThat(stored.getPowerWattsMovingAvg()).isCloseTo(expected.getPowerWattsMovingAvg(), within(STORED_POWER_TOLERANCE_WATTS));
        assertThat(stored.getPowerWatts30sMax()).isCloseTo(expected.getPowerWatts30sMax(), within(STORED_POWER_TOLERANCE_WATTS));
        assertThat(stored.getNormalizedPowerWatts()).isCloseTo(expected.getNormalizedPowerWatts(), within(STORED_POWER_TOLERANCE_WATTS));
        assertThat(queued()).isZero();
    }

    @Test
    void explorationCannotEraseEnergyCommittedAfterItsTrackWasLoaded() throws Exception {
        CountDownLatch explorationLoaded = new CountDownLatch(1);
        CountDownLatch releaseExploration = new CountDownLatch(1);
        doAnswer(invocation -> {
            explorationLoaded.countDown();
            assertThat(releaseExploration.await(CONCURRENT_WORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            return new ExplorationScoreQueryRepository.ExplorationResult(100, 70, 0.7);
        }).when(explorationQueries).calculateExplorationScore(eq(trackId), any(), anyDouble(), anyInt());

        try (var executor = Executors.newSingleThreadExecutor()) {
            var calculation = executor.submit(() -> exploration.processOne(trackId));
            try {
                assertThat(explorationLoaded.await(CONCURRENT_WORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
                assertThat(energy.recalculateEnergyForTrack(trackId, null)).isTrue();
                var committedEnergy = energyValues();
                assertThat(committedEnergy.get("energy_net_total_wh")).isNotNull();
                jdbc.update("UPDATE gps_track SET track_name='Concurrent synthetic metadata' WHERE id=?", trackId);
                var beforeExploration = trackValuesExcept("exploration_status", "exploration_score", "exploration_calc_date");
                releaseExploration.countDown();
                assertThat(calculation.get(CONCURRENT_WORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
                assertThat(energyValues()).isEqualTo(committedEnergy);
                assertThat(trackValuesExcept("exploration_status", "exploration_score", "exploration_calc_date"))
                        .isEqualTo(beforeExploration);
                assertThat(jdbc.queryForObject("SELECT track_name FROM gps_track WHERE id=?", String.class, trackId))
                        .isEqualTo("Concurrent synthetic metadata");
                assertThat(jdbc.queryForObject("SELECT exploration_score FROM gps_track WHERE id=?", Double.class, trackId))
                        .isEqualTo(0.7);
            } finally {
                releaseExploration.countDown();
            }
        }
    }

    @Test
    void duplicateDetectionCannotEraseResultsCommittedAfterItsTrackWasLoaded() throws Exception {
        jdbc.update("UPDATE gps_track SET duplicate_status='NOT_CHECKED_YET' WHERE id=?", trackId);
        CountDownLatch duplicateLoaded = new CountDownLatch(1);
        CountDownLatch releaseDuplicate = new CountDownLatch(1);
        doAnswer(invocation -> {
            duplicateLoaded.countDown();
            assertThat(releaseDuplicate.await(CONCURRENT_WORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            return List.of();
        }).when(tracks).findSimilarTracksWithinTimeRangeForTrack(any(), any(), anyDouble(), eq(trackId));

        try (var executor = Executors.newSingleThreadExecutor()) {
            var detection = executor.submit(() -> duplicates.processOne(trackId, Duration.ofMinutes(20), 1000d));
            try {
                assertThat(duplicateLoaded.await(CONCURRENT_WORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
                assertThat(energy.recalculateEnergyForTrack(trackId, null)).isTrue();
                jdbc.update("UPDATE gps_track SET track_name='Concurrent synthetic metadata',exploration_score=0.7,exploration_status='CALCULATED' WHERE id=?", trackId);
                var beforeDuplicate = trackValuesExcept("duplicate_status", "duplicate_of");
                releaseDuplicate.countDown();
                detection.get(CONCURRENT_WORK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                assertThat(trackValuesExcept("duplicate_status", "duplicate_of")).isEqualTo(beforeDuplicate);
                assertThat(jdbc.queryForObject("SELECT duplicate_status FROM gps_track WHERE id=?", String.class, trackId))
                        .isEqualTo("UNIQUE");
            } finally {
                releaseDuplicate.countDown();
            }
        }
    }

    @Test
    void duplicateResultDoesNotReplaceANewerExclusion() {
        jdbc.update("UPDATE gps_track SET duplicate_status='EXCLUDED' WHERE id=?", trackId);
        var before = trackValuesExcept();
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThat(tracks.updateDuplicateDetection(trackId, GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET,
                        GpsTrack.DUPLICATE_CHECK_STATUS.UNIQUE, null)).isZero());
        assertThat(trackValuesExcept()).isEqualTo(before);
    }

    @Test
    void staleMetadataRequestCannotReplaceCompletedBackgroundResults() {
        var staleRequest = tracks.findById(trackId).orElseThrow();
        staleRequest.setTrackName("Edited synthetic metadata");
        staleRequest.setDuplicateStatus(GpsTrack.DUPLICATE_CHECK_STATUS.EXCLUDED);
        staleRequest.setExplorationScore(0.1);
        staleRequest.setNumberOfTrackPoints(-1);
        assertThat(energy.recalculateEnergyForTrack(trackId, null)).isTrue();
        jdbc.update("UPDATE gps_track SET duplicate_status='UNIQUE',exploration_score=0.7,exploration_status='CALCULATED' WHERE id=?", trackId);
        var beforeMetadata = trackValuesExcept("track_name", "track_description", "track_type", "meta_name",
                "meta_description", "meta_link", "highlight_exclusion_reason", "statistics_exclusion_reason");

        var saved = controller.saveTrack(staleRequest);

        assertThat(saved.getTrackName()).isEqualTo("Edited synthetic metadata");
        assertThat(trackValuesExcept("track_name", "track_description", "track_type", "meta_name",
                "meta_description", "meta_link", "highlight_exclusion_reason", "statistics_exclusion_reason"))
                .isEqualTo(beforeMetadata);
    }

    @Test
    void sourceDeletionCleanupChangesOnlyRelationshipsOnSurvivingTracks() {
        createData("RAW_OUTLIER_CLEANED");
        long childId = trackIds.getLast();
        jdbc.update("UPDATE gps_track SET duplicate_status='DUPLICATE',duplicate_of=?,source_parent_track_id=?,energy_net_total_wh=10,exploration_score=0.7,track_name='Synthetic child' WHERE id=?",
                trackId, trackId, childId);
        var before = trackValuesExcept(childId, "duplicate_status", "duplicate_of", "source_parent_track_id");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(tracks.releaseDuplicateReferences(trackId)).isEqualTo(1);
            assertThat(tracks.clearSegmentParentReferences(trackId)).isEqualTo(1);
        });

        assertThat(trackValuesExcept(childId, "duplicate_status", "duplicate_of", "source_parent_track_id")).isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT duplicate_status,duplicate_of,source_parent_track_id FROM gps_track WHERE id=?", childId))
                .containsEntry("duplicate_status", "NOT_CHECKED_YET")
                .containsEntry("duplicate_of", null)
                .containsEntry("source_parent_track_id", null);
    }

    @Test
    void unavailableAndFailedExplorationKeepAllOtherFields() {
        assertThat(energy.recalculateEnergyForTrack(trackId, null)).isTrue();
        jdbc.update("UPDATE gps_track SET exploration_score=0.8 WHERE id=?", trackId);
        var before = trackValuesExcept("exploration_status", "exploration_score", "exploration_calc_date");
        doAnswer(invocation -> null).when(explorationQueries)
                .calculateExplorationScore(eq(trackId), any(), anyDouble(), anyInt());
        assertThat(exploration.processOne(trackId)).isTrue();
        assertThat(trackValuesExcept("exploration_status", "exploration_score", "exploration_calc_date")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT exploration_score FROM gps_track WHERE id=?", Double.class, trackId)).isNull();

        jdbc.update("UPDATE gps_track SET exploration_score=0.8 WHERE id=?", trackId);
        doAnswer(invocation -> { throw new IllegalStateException("synthetic calculation failure"); })
                .when(explorationQueries).calculateExplorationScore(eq(trackId), any(), anyDouble(), anyInt());
        assertThat(exploration.processOne(trackId)).isFalse();
        assertThat(trackValuesExcept("exploration_status", "exploration_score", "exploration_calc_date")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT exploration_score FROM gps_track WHERE id=?", Double.class, trackId)).isEqualTo(0.8);
        assertThat(jdbc.queryForObject("SELECT exploration_status FROM gps_track WHERE id=?", String.class, trackId))
                .isEqualTo("NEEDS_RECALCULATION");
    }

    @Test
    void energySummaryExcludesPlansWithoutHidingImportedMissingActivity() {
        long planId = jdbc.queryForObject("""
                INSERT INTO gps_track(load_status,track_source,timing_unreliable)
                VALUES('SUCCESS','PLANNED',false) RETURNING id
                """, Long.class);
        trackIds.add(planId);
        jdbc.update("UPDATE gps_track SET activity_type=NULL,activity_type_source='USER_SET' WHERE id=?", trackId);
        var expected = new EnergyRecalculationQueue.SummaryCounts(0, 0, 0, 0, 1, 0);

        assertThat(energyQueue.summaryCounts()).isEqualTo(expected);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> energyQueue.request(planId));
        assertThat(energyQueue.summaryCounts()).isEqualTo(expected);
        jdbc.update("DELETE FROM track_energy_recalculation WHERE track_id=?", planId);
        jdbc.update("UPDATE gps_track SET energy_net_total_wh=0 WHERE id=?", planId);
        assertThat(energyQueue.summaryCounts()).isEqualTo(expected);
        jdbc.update("UPDATE gps_track SET timing_unreliable=true WHERE id=?", planId);
        assertThat(energyQueue.summaryCounts()).isEqualTo(expected);
    }

    @Test
    void energySummaryWaitsForClassificationWithoutDemandingUserAction() {
        jdbc.update("UPDATE gps_track SET activity_type_source=NULL,activity_type=NULL WHERE id=?", trackId);
        var summary = energyQueue.summaryCounts();
        assertThat(summary.waiting()).isEqualTo(1);
        assertThat(summary.missingActivity()).isZero();
        assertThat(summary.missingCalculation()).isZero();
        assertThat(summary.attentionRequired()).isZero();
        jdbc.update("UPDATE gps_track SET activity_type='BICYCLE' WHERE id=?", trackId);
        assertThat(energyQueue.summaryCounts().waiting()).isEqualTo(1);
    }

    @Test
    void energySummarySeparatesExpectedSourceLimitsFromActionableMissingResults() {
        assertThat(energyQueue.summaryCounts()).isEqualTo(new EnergyRecalculationQueue.SummaryCounts(0, 0, 0, 0, 0, 1));
        assertThat(energyQueue.summaryCounts().attentionRequired()).isEqualTo(1);
        jdbc.update("UPDATE gps_track SET energy_net_total_wh=0 WHERE id=?", trackId);
        assertThat(energyQueue.summaryCounts()).isEqualTo(new EnergyRecalculationQueue.SummaryCounts(0, 1, 0, 0, 0, 0));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> energyQueue.request(trackId));
        assertThat(energyQueue.summaryCounts()).isEqualTo(new EnergyRecalculationQueue.SummaryCounts(1, 0, 0, 0, 0, 0));
        jdbc.update("DELETE FROM track_energy_recalculation WHERE track_id=?", trackId);
        jdbc.update("UPDATE gps_track SET timing_unreliable=true WHERE id=?", trackId);
        assertThat(energyQueue.summaryCounts()).isEqualTo(new EnergyRecalculationQueue.SummaryCounts(0, 0, 0, 1, 0, 0));
        assertThat(energyQueue.summaryCounts().attentionRequired()).isZero();
        jdbc.update("UPDATE gps_track SET timing_unreliable=false,activity_type=NULL,activity_type_source=NULL,energy_net_total_wh=NULL WHERE id=?", trackId);
        // Classification has not run yet; it queues the calculation once it assigns an activity.
        assertThat(energyQueue.summaryCounts()).isEqualTo(new EnergyRecalculationQueue.SummaryCounts(0, 0, 0, 0, 0, 0, 1, 0, null));
        assertThat(energyQueue.summaryCounts().attentionRequired()).isZero();
        jdbc.update("UPDATE gps_track SET activity_type_source='USER_SET' WHERE id=?", trackId);
        assertThat(energyQueue.summaryCounts()).isEqualTo(new EnergyRecalculationQueue.SummaryCounts(0, 0, 0, 0, 1, 0));
        assertThat(energyQueue.summaryCounts().attentionRequired()).isEqualTo(1);
        jdbc.update("UPDATE gps_track SET activity_type_source='FAILED',activity_type_source_details=? WHERE id=?",
                ActivityTypeAutoClassifier.NO_DATA_POINTS_MESSAGE,
                trackId);
        assertThat(energyQueue.summaryCounts()).isEqualTo(new EnergyRecalculationQueue.SummaryCounts(0, 0, 1, 0, 0, 0));
        assertThat(energyQueue.summaryCounts().attentionRequired()).isZero();
        assertThat(energyQueue.summaryCounts().unavailable()).isEqualTo(1);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> energyQueue.request(trackId));
        assertThat(energyQueue.summaryCounts()).isEqualTo(new EnergyRecalculationQueue.SummaryCounts(1, 0, 0, 0, 0, 0));
    }

    @Test
    void correlationTriggerKeepsEveryInterpolationInputAndIgnoresEnergyAndUnrelatedMetrics() {
        jdbc.update("UPDATE gps_track_data_points SET energy_total_wh=1, speed_in_kmh_moving_window=2,point_xy=NULL WHERE gps_track_data_id=?", dataId);
        assertThat(queued()).isZero();
        List<String> changes = List.of("point_timestamp=point_timestamp+interval '1 second'", "point_index=point_index+2000",
                "point_long_lat=ST_Translate(point_long_lat,0.000001,0)", "point_altitude=point_altitude+1",
                "interval_status='UNKNOWN'", "distance_in_meter_since_start=distance_in_meter_since_start+1",
                "duration_since_start=duration_since_start+1", "point_timestamp=NULL");
        for (String change : changes) {
            clearQueue();
            jdbc.update("UPDATE gps_track_data_points SET " + change + " WHERE id=(SELECT min(id) FROM gps_track_data_points WHERE gps_track_data_id=?)", dataId);
            assertThat(queued()).as(change).isEqualTo(1);
        }
        clearQueue();
        jdbc.update("UPDATE gps_track_data_points SET point_index=point_index,point_long_lat=point_long_lat WHERE gps_track_data_id=?", dataId);
        assertThat(queued()).isZero();
    }

    @Test
    void movingPointsQueuesBothCanonicalParentsAndRawOnlyUpdatesDoNotQueue() {
        long second = createData("RAW_OUTLIER_CLEANED");
        jdbc.update("UPDATE gps_track_data_points SET gps_track_data_id=? WHERE id=(SELECT min(id) FROM gps_track_data_points WHERE gps_track_data_id=?)", second, dataId);
        assertThat(queued()).isEqualTo(2);
        clearQueue();
        long raw = createData("RAW");
        jdbc.update("UPDATE gps_track_data_points SET gps_track_data_id=? WHERE gps_track_data_id=?", raw, second);
        clearQueue();
        jdbc.update("UPDATE gps_track_data_points SET point_altitude=1 WHERE gps_track_data_id=?", raw);
        assertThat(queued()).isZero();
    }

    @Test
    void trackSummaryWritesDoNotQueuePhotosButDatesAndStatusChangesStillDo() {
        jdbc.update("UPDATE gps_track SET energy_weight_kg_used=80,start_date=start_date WHERE id=?", trackId);
        assertThat(queued()).isZero();
        for (String change : List.of("start_date=timestamp '2020-01-01'", "end_date=timestamp '2020-01-02'",
                "load_status='EMPTY_FILE'", "duplicate_status='UNIQUE'", "track_source='PLANNED'")) {
            clearQueue();
            jdbc.update("UPDATE gps_track SET " + change + " WHERE id=?", trackId);
            assertThat(queued()).as(change).isEqualTo(1);
        }
    }

    @Test
    void canonicalInsertAndDeleteStillQueueCorrelation() {
        jdbc.update("DELETE FROM gps_track_data_points WHERE gps_track_data_id=?", dataId);
        assertThat(queued()).isEqualTo(1);
        clearQueue();
        jdbc.update("INSERT INTO gps_track_data_points(gps_track_data_id,point_index,point_index_max,moving_window_in_sec,point_long_lat) VALUES(?,0,0,90,ST_SetSRID(ST_MakePoint(8,47),4326))", dataId);
        assertThat(queued()).isEqualTo(1);
    }

    private long createData(String type) {
        long id = jdbc.queryForObject("INSERT INTO gps_track(load_status,activity_type,activity_type_source,timing_unreliable) VALUES('SUCCESS','BICYCLE','USER_SET',false) RETURNING id", Long.class);
        trackIds.add(id);
        return jdbc.queryForObject("INSERT INTO gps_track_data(gps_track_id,track_type,precision_in_meter) VALUES(?,?,0) RETURNING id", Long.class, id, type);
    }

    private List<String> sourceValues() {
        return jdbc.queryForList("SELECT ROW(id,gps_track_data_id,point_index,point_timestamp,point_altitude,ST_AsEWKB(point_long_lat),ST_AsEWKB(point_xy),interval_status,motion_state,distance_in_meter_since_start,duration_since_start)::text FROM gps_track_data_points WHERE gps_track_data_id=? ORDER BY id", String.class, dataId);
    }

    private Map<String, Object> energyValues() {
        return jdbc.queryForMap("""
                SELECT energy_gravitational_total_wh,energy_gravitational_descent_wh,energy_aero_drag_total_wh,
                       energy_rolling_resistance_total_wh,energy_kinetic_positive_total_wh,energy_net_total_wh,
                       energy_weight_kg_used,power_watts_avg,power_watts_max
                  FROM gps_track WHERE id=?
                """, trackId);
    }

    private Map<String, Object> trackValuesExcept(String... ownedColumns) {
        return trackValuesExcept(trackId, ownedColumns);
    }

    private Map<String, Object> trackValuesExcept(long id, String... ownedColumns) {
        var values = new HashMap<>(jdbc.queryForMap("SELECT * FROM gps_track WHERE id=?", id));
        values.remove("version"); // The database increments the revision on every targeted update.
        for (String column : ownedColumns) values.remove(column);
        return values;
    }

    private Map<String, Object> trackValuesExceptEnergy() {
        var values = trackValuesExcept();
        values.keySet().removeIf(column -> column.startsWith("energy_") || column.startsWith("metabolic_")
                || column.startsWith("power_watts_") || column.equals("normalized_power_watts"));
        return values;
    }

    private void clearQueue() {
        jdbc.update("DELETE FROM media_correlation_track_work WHERE track_id IN (" + placeholders() + ")", trackIds.toArray());
    }

    private long queued() {
        return jdbc.queryForObject("SELECT count(*) FROM media_correlation_track_work WHERE track_id IN (" + placeholders() + ")", Long.class, trackIds.toArray());
    }

    private String placeholders() {
        return String.join(",", java.util.Collections.nCopies(trackIds.size(), "?"));
    }
}
