package com.x8ing.mtl.server.mtlserver.googlehealth.exercise;

import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.energy.EnergyRecalculationQueue;
import com.x8ing.mtl.server.mtlserver.energy.EnergyService;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.ExercisePoint;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype.ActivityTypeAutoClassifier;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.testsupport.BackgroundJobTestSupport.directRunner;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "mtl.planner.enabled=false", "mtl.google-health.enabled=false",
        "mtl.energy.recalculation-interval=PT1H", "mtl.media-correlation.initial-delay=PT1H",
        "mtl.media-correlation.run-schedule=PT1H", "mtl.indexer.gps.live-watch-enabled=false",
        "mtl.indexer.media.live-watch-enabled=false"
})
class ExerciseEnergyDatabaseIntegrationTest {
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ExerciseAssociationService association;
    @Autowired
    ActivityTypeAutoClassifier classifier;
    @Autowired
    EnergyRecalculationQueue queue;
    @Autowired
    EnergyService energy;
    @Autowired
    GpsTrackRepository tracks;
    @Autowired
    GpsTrackDataPointRepository points;
    @Autowired
    PlatformTransactionManager transactionManager;
    @MockitoBean
    GPXDirectoryWatcherService watcher;
    @MockitoBean
    MediaIndexerService media;
    private final List<Long> createdTracks = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long id : createdTracks) {
            jdbc.update("DELETE FROM gps_track_data_points WHERE gps_track_data_id IN (SELECT id FROM gps_track_data WHERE gps_track_id = ?)", id);
            jdbc.update("DELETE FROM gps_track_data WHERE gps_track_id = ?", id);
            jdbc.update("DELETE FROM gps_track WHERE id = ?", id);
        }
    }

    @Test
    void shutdownCommitsTheActiveEnergyTrackAndRestartProcessesTheRemainingRequest() {
        long first = syntheticTrack();
        long second = syntheticTrack();
        googleType(first, "RUNNING");
        googleType(second, "RUNNING");
        var signal = new ShutdownSignal();
        var calculation = Mockito.mock(EnergyService.class);
        Mockito.when(calculation.recalculateEnergyForTrack(
                ArgumentMatchers.eq(first), ArgumentMatchers.isNull())).thenAnswer(call -> {
            signal.onContextClosed();
            return energy.recalculateEnergyForTrack(first, null);
        });
        var stoppingQueue = new EnergyRecalculationQueue(jdbc, calculation, signal, directRunner());
        stoppingQueue.processPending();
        stoppingQueue.processPending();
        assertThat(pending(first)).isZero();
        assertThat(pending(second)).isEqualTo(1);
        assertThat(tracks.findById(first).orElseThrow().getEnergyNetTotalWh()).isPositive();
        Mockito.verify(calculation, Mockito.never()).recalculateEnergyForTrack(second, null);
        new EnergyRecalculationQueue(jdbc, energy, new ShutdownSignal(), directRunner()).processPending();
        assertThat(pending(second)).isZero();
        assertThat(tracks.findById(second).orElseThrow().getEnergyNetTotalWh()).isPositive();
    }

    @Test
    void googleBeforeAutomaticClassifierStillCalculatesCanonicalEnergyAndPower() {
        long id = syntheticTrack();
        googleType(id, "RUNNING");
        assertThat(classifier.classifyActivity(id, canonicalPoints(id)).updated()).isFalse();
        queue.processPending();
        assertEnergyMatchesManualCalculation(id, GpsTrack.ACTIVITY_TYPE.RUNNING);
        assertThat(pending(id)).isZero();
    }

    @Test
    void googleAfterAnAlreadyCalculatedAutomaticTypeReplacesItsEnergyModel() {
        long id = syntheticTrack();
        classifier.classifyActivity(id, canonicalPoints(id));
        queue.processPending();
        Double automaticEnergy = tracks.findById(id).orElseThrow().getEnergyNetTotalWh();
        googleType(id, "RUNNING");
        queue.processPending();
        assertEnergyMatchesManualCalculation(id, GpsTrack.ACTIVITY_TYPE.RUNNING);
        assertThat(tracks.findById(id).orElseThrow().getEnergyNetTotalWh()).isNotEqualTo(automaticEnergy);
        googleType(id, "BIKING");
        queue.processPending();
        assertEnergyMatchesManualCalculation(id, GpsTrack.ACTIVITY_TYPE.BICYCLE);
    }

    @Test
    void repeatedGoogleTypeDoesNotRequeueCalculatedEnergy() {
        long id = syntheticTrack();
        googleType(id, "RUNNING");
        queue.processPending();
        var before = tracks.findById(id).orElseThrow();
        var powerBefore = canonicalPoints(id).stream().map(GpsTrackDataPoint::getPowerWatts).toList();

        googleType(id, "RUNNING");

        assertThat(pending(id)).isZero();
        var after = tracks.findById(id).orElseThrow();
        assertThat(after.getEnergyNetTotalWh()).isEqualTo(before.getEnergyNetTotalWh());
        assertThat(after.getMetabolicEnergy()).isEqualTo(before.getMetabolicEnergy());
        assertThat(canonicalPoints(id).stream().map(GpsTrackDataPoint::getPowerWatts).toList()).isEqualTo(powerBefore);
    }

    @Test
    void googleProvenanceForTheSameAutomaticTypeDoesNotRecalculate() {
        long id = syntheticTrack();
        classifier.classifyActivity(id, canonicalPoints(id));
        queue.processPending();
        var before = tracks.findById(id).orElseThrow();
        assertThat(before.getActivityType()).isEqualTo(GpsTrack.ACTIVITY_TYPE.BICYCLE);

        googleType(id, "BIKING");

        assertThat(pending(id)).isZero();
        var after = tracks.findById(id).orElseThrow();
        assertThat(after.getActivityTypeSource()).isEqualTo(GpsTrack.ACTIVITY_TYPE_SOURCE.GOOGLE_HEALTH);
        assertThat(after.getActivityTypeSourceDetails()).isEqualTo("Google Health exercise type: BIKING");
        assertThat(after.getEnergyNetTotalWh()).isEqualTo(before.getEnergyNetTotalWh());
        assertThat(after.getMetabolicEnergy()).isEqualTo(before.getMetabolicEnergy());
    }

    @Test
    void googleTypeAliasUpdatesDetailsWithoutRecalculatingTheSameModel() {
        long id = syntheticTrack();
        googleType(id, "BIKING");
        queue.processPending();

        googleType(id, "CYCLING");

        assertThat(pending(id)).isZero();
        var after = tracks.findById(id).orElseThrow();
        assertThat(after.getActivityType()).isEqualTo(GpsTrack.ACTIVITY_TYPE.BICYCLE);
        assertThat(after.getActivityTypeSourceDetails()).isEqualTo("Google Health exercise type: CYCLING");
    }

    @Test
    void repeatedGoogleTypePreservesThePendingGenerationAndRetryDelay() {
        long id = syntheticTrack();
        googleType(id, "RUNNING");
        jdbc.update("""
                UPDATE track_energy_recalculation
                   SET next_attempt_at = now() + interval '1 hour', last_error = 'SyntheticFailure'
                 WHERE track_id = ?
                """, id);
        var before = jdbc.queryForMap("SELECT * FROM track_energy_recalculation WHERE track_id = ?", id);

        googleType(id, "RUNNING");

        assertThat(jdbc.queryForMap("SELECT * FROM track_energy_recalculation WHERE track_id = ?", id)).isEqualTo(before);
    }

    @Test
    void firstClassificationStillQueuesEnergyWhenTheTrackAlreadyHasTheSameType() {
        long id = syntheticTrack();
        jdbc.update("UPDATE gps_track SET activity_type = 'RUNNING' WHERE id = ?", id);

        googleType(id, "RUNNING");

        assertThat(pending(id)).isEqualTo(1);
        queue.processPending();
        assertEnergyMatchesManualCalculation(id, GpsTrack.ACTIVITY_TYPE.RUNNING);
    }

    @Test
    void userClassificationIsPreservedAndANewReplacementCanBeCalculated() {
        long id = syntheticTrack();
        inTransaction(() -> {
            jdbc.update("UPDATE gps_track SET activity_type = 'HIKING', activity_type_source = 'USER_SET' WHERE id = ?", id);
            queue.request(id);
        });
        googleType(id, "RUNNING");
        queue.processPending();
        assertEnergyMatchesManualCalculation(id, GpsTrack.ACTIVITY_TYPE.HIKING);
    }

    @Test
    void calculationFailureRemainsDurableAndLaterRetries() {
        long id = syntheticTrack();
        inTransaction(() -> queue.request(id));
        queue.processPending();
        assertThat(pending(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_error FROM track_energy_recalculation WHERE track_id = ?", String.class, id))
                .isEqualTo("IllegalStateException");
        googleType(id, "RUNNING");
        queue.processPending();
        assertEnergyMatchesManualCalculation(id, GpsTrack.ACTIVITY_TYPE.RUNNING);
        assertThat(pending(id)).isZero();
    }

    @Test
    void savedBodyWeightSurvivesAutomaticRecalculationAndActivityChanges() {
        long id = syntheticTrack();
        googleType(id, "RUNNING");
        queue.processPending();
        energy.recalculateEnergyForTrack(id, energy.getDefaultParameters().toBuilder().riderWeightKg(90).build());
        googleType(id, "BIKING");
        queue.processPending();
        var track = tracks.findById(id).orElseThrow();
        assertThat(track.getMetabolicEnergy().getBodyWeightKg()).isEqualTo(90);
        assertThat(track.getEnergyWeightKgUsed()).isEqualTo(100);
    }

    @Test
    void rolledBackClassificationDoesNotPublishAnEnergyRequest() {
        long id = syntheticTrack();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            association.applyExerciseType(exercise(id, "RUNNING"));
            status.setRollbackOnly();
        });
        assertThat(pending(id)).isZero();
        assertThat(tracks.findById(id).orElseThrow().getActivityType()).isNull();
    }

    @Test
    void aConcurrentClassificationCannotBeAcknowledgedByAnOlderCalculation() {
        long id = syntheticTrack();
        inTransaction(() -> queue.request(id));
        EnergyService runningCalculation = Mockito.mock(EnergyService.class);
        Mockito.when(runningCalculation.recalculateEnergyForTrack(
                        ArgumentMatchers.eq(id), ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    googleType(id, "RUNNING");
                    return true;
                });
        new EnergyRecalculationQueue(jdbc, runningCalculation, new ShutdownSignal(), directRunner()).processPending();

        assertThat(pending(id)).isEqualTo(1);
        queue.processPending();
        assertEnergyMatchesManualCalculation(id, GpsTrack.ACTIVITY_TYPE.RUNNING);
        assertThat(pending(id)).isZero();
    }

    @Test
    void interruptedCalculationKeepsItsRequestAndThreadInterrupt() {
        long id = syntheticTrack();
        inTransaction(() -> queue.request(id));
        EnergyService interrupted = Mockito.mock(EnergyService.class);
        Mockito.when(interrupted.recalculateEnergyForTrack(
                        ArgumentMatchers.eq(id), ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("Calculation interrupted", new InterruptedException("Stopping")));
        try {
            new EnergyRecalculationQueue(jdbc, interrupted, new ShutdownSignal(), directRunner()).processPending();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThat(pending(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_error FROM track_energy_recalculation WHERE track_id = ?", String.class, id))
                .isNull();
    }

    private void assertEnergyMatchesManualCalculation(long id, GpsTrack.ACTIVITY_TYPE expected) {
        GpsTrack track = tracks.findById(id).orElseThrow();
        assertThat(track.getActivityType()).isEqualTo(expected);
        assertThat(track.getEnergyNetTotalWh()).isPositive();
        assertThat(track.getMetabolicEnergy()).isNotNull();
        assertThat(track.getMetabolicEnergy().getActiveKcal()).isPositive();
        assertThat(track.getMetabolicEnergy().getCoveredSeconds()).isEqualTo(70);
        assertThat(canonicalPoints(id)).anySatisfy(point -> assertThat(point.getPowerWatts()).isPositive());
        double queued = track.getEnergyNetTotalWh();
        List<Double> queuedPower = canonicalPoints(id).stream().map(GpsTrackDataPoint::getPowerWatts).toList();
        assertThat(energy.recalculateEnergyForTrack(id, energy.getDefaultParameters())).isTrue();
        assertThat(tracks.findById(id).orElseThrow().getEnergyNetTotalWh()).isEqualTo(queued);
        assertThat(canonicalPoints(id).stream().map(GpsTrackDataPoint::getPowerWatts).toList()).isEqualTo(queuedPower);
    }

    private long syntheticTrack() {
        long id = jdbc.queryForObject("""
                INSERT INTO gps_track (track_name, load_status, duplicate_status, track_source)
                VALUES ('Cycling synthetic test', 'SUCCESS', 'UNIQUE', 'IMPORTED') RETURNING id
                """, Long.class);
        createdTracks.add(id);
        long dataId = jdbc.queryForObject("INSERT INTO gps_track_data (gps_track_id, track_type) VALUES (?, 'RAW_OUTLIER_CLEANED') RETURNING id", Long.class, id);
        for (int i = 0; i < 8; i++) {
            jdbc.update("""
                    INSERT INTO gps_track_data_points
                        (id, gps_track_data_id, moving_window_in_sec, point_index, point_index_max, point_long_lat, point_altitude, point_timestamp,
                         distance_in_meter_between_points, distance_in_meter_since_start,
                         duration_between_points_in_sec, duration_since_start,
                         ascent_in_meter_between_points, speed_in_kmh_moving_window, interval_status, motion_state)
                    VALUES (nextval('gps_track_data_points_id_seq'), ?, 30, ?, 7, ST_SetSRID(ST_Point(8, 47), 4326), ?,
                            timestamp '2026-08-28 10:00:00' + (? * interval '10 seconds'), ?, ?, ?, ?, ?, 18, 'OBSERVED', 'MOVING')
                    """, dataId, i, 500 + i, i, i == 0 ? 0 : 50, i * 50, i == 0 ? 0 : 10, i * 10, i == 0 ? 0 : 1);
        }
        return id;
    }

    private List<GpsTrackDataPoint> canonicalPoints(long id) {
        long dataId = jdbc.queryForObject("SELECT id FROM gps_track_data WHERE gps_track_id = ?", Long.class, id);
        return points.findAllByGpsTrackDataIdOrderByPointIndexAsc(dataId);
    }

    private void googleType(long id, String type) {
        inTransaction(() -> association.applyExerciseType(exercise(id, type)));
    }

    private ExercisePoint exercise(long trackId, String type) {
        return new ExercisePoint(1L, "synthetic", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), "fp",
                "{\"exercise\":{\"exerciseType\":\"" + type + "\"}}", "synthetic.tcx",
                ExercisePoint.RouteState.IMPORTED, 1L, trackId);
    }

    private void inTransaction(Runnable operation) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> operation.run());
    }

    private long pending(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM track_energy_recalculation WHERE track_id = ?", Long.class, id);
    }
}
