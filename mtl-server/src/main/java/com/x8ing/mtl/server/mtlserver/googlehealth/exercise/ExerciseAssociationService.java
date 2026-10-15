package com.x8ing.mtl.server.mtlserver.googlehealth.exercise;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.energy.EnergyRecalculationQueue;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.ExercisePoint;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

@Service
@Slf4j
public class ExerciseAssociationService {

    static final int BATCH_SIZE = 100;
    private static final String TYPE_SOURCE_PREFIX = "Google Health exercise type: ";

    private final ConnectorProperties properties;
    private final MtlAppProperties appProperties;
    private final MirrorRepository repository;
    private final GpsTrackRepository gpsTrackRepository;
    private final ExerciseTypeMapper exerciseTypeMapper;
    private final EnergyRecalculationQueue energyQueue;
    private final ShutdownSignal shutdownSignal;
    private final BackgroundJobRunner backgroundJobs;
    private final TransactionTemplate transactionTemplate;

    public ExerciseAssociationService(
            ConnectorProperties properties,
            MtlAppProperties appProperties,
            MirrorRepository repository,
            GpsTrackRepository gpsTrackRepository,
            ExerciseTypeMapper exerciseTypeMapper,
            EnergyRecalculationQueue energyQueue,
            ShutdownSignal shutdownSignal, BackgroundJobRunner backgroundJobs,
            TransactionTemplate transactionTemplate) {
        this.properties = properties;
        this.appProperties = appProperties;
        this.repository = repository;
        this.gpsTrackRepository = gpsTrackRepository;
        this.exerciseTypeMapper = exerciseTypeMapper;
        this.energyQueue = energyQueue;
        this.shutdownSignal = shutdownSignal;
        this.backgroundJobs = backgroundJobs;
        this.transactionTemplate = new TransactionTemplate(transactionTemplate.getTransactionManager());
        // Each batch commits before its continuation, even when the caller has an open transaction.
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${mtl.google-health.exercise-association-interval:PT5S}")
    public void associateCompletedImports() {
        backgroundJobs.run(ExerciseAssociationService.class, () -> {
            if (shutdownSignal.isStopRequested() || appProperties.isDemoMode() || !properties.isConfigured()) {
                return false;
            }
            return transactionTemplate.execute(status -> associateBatch());
        });
    }

    private boolean associateBatch() {
        if (shutdownSignal.isStopRequested()) return false;
        var exercises = repository.findPublishedExercisesAwaitingAssociation(BATCH_SIZE);
        int associated = 0;
        int processed = 0;
        for (ExercisePoint exercise : exercises) {
            if (shutdownSignal.isStopRequested()) break;
            if (repository.associateExerciseRoute(exercise.id(), exercise.indexedFileId(),
                    exercise.gpsTrackId(), exercise.fingerprint()) > 0) {
                // Keep the matched metadata revision locked until its type and energy request commit.
                applyExerciseType(exercise);
                associated++;
            }
            processed++;
        }
        if (!exercises.isEmpty()) {
            log.info("Google Health exercise-route association completed: associated={}, superseded={}, remaining={}",
                    associated, processed - associated, exercises.size() - processed);
        }
        return !shutdownSignal.isStopRequested() && exercises.size() == BATCH_SIZE && associated > 0;
    }

    void applyExerciseType(ExercisePoint exercise) {
        if (exercise == null || exercise.gpsTrackId() == null) {
            return;
        }
        exerciseTypeMapper.map(exercise.rawJson()).ifPresent(mapped -> {
            // Compare under the same row lock used by energy calculation and user edits.
            var track = gpsTrackRepository.findByIdForUpdate(exercise.gpsTrackId()).orElse(null);
            if (track == null || track.getActivityTypeSource() == GpsTrack.ACTIVITY_TYPE_SOURCE.USER_SET) {
                return;
            }
            boolean typeChanged = track.getActivityType() != mapped.activityType();
            boolean firstClassification = track.getActivityTypeSource() == null;
            String sourceDetails = TYPE_SOURCE_PREFIX + mapped.googleType();
            if (!typeChanged && track.getActivityTypeSource() == GpsTrack.ACTIVITY_TYPE_SOURCE.GOOGLE_HEALTH
                && Objects.equals(track.getActivityTypeSourceDetails(), sourceDetails)) {
                return;
            }
            int updated = gpsTrackRepository.updateActivityClassificationUnlessUserSet(
                    exercise.gpsTrackId(),
                    mapped.activityType(),
                    GpsTrack.ACTIVITY_TYPE_SOURCE.GOOGLE_HEALTH,
                    sourceDetails);
            // Source attribution and aliases do not change the energy model or a pending retry.
            if (updated > 0 && (typeChanged || firstClassification)) {
                energyQueue.request(exercise.gpsTrackId());
            }
        });
    }
}
