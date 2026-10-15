package com.x8ing.mtl.server.mtlserver.googlehealth.exercise;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GpxTrackReplacementEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;

@Component
@Slf4j
public class TrackReplacementListener {

    private final MirrorRepository repository;
    private final ExerciseAssociationService associationService;

    public TrackReplacementListener(
            MirrorRepository repository,
            ExerciseAssociationService associationService) {
        this.repository = repository;
        this.associationService = associationService;
    }

    @EventListener
    public void replaceAssociations(GpxTrackReplacementEvent event) {
        repository.replaceExerciseTrackAssociations(event.replacementTrackIds());
        var exercises = repository.findExercisesAssociatedWithTracks(
                new LinkedHashSet<>(event.replacementTrackIds().values()));
        exercises.forEach(associationService::applyExerciseType);
        if (!event.replacementTrackIds().isEmpty()) {
            log.info("Google Health exercise-track associations replaced: trackReplacements={}, "
                     + "affectedExercises={}",
                    event.replacementTrackIds().size(), exercises.size());
        }
    }
}
