package com.x8ing.mtl.server.mtlserver.googlehealth.exercise;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.ExercisePoint;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GpxTrackReplacementEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.*;

class TrackReplacementListenerTest {

    @Test
    void swapsExerciseAssociationBeforeThePreviousTrackIsRemoved() {
        MirrorRepository repository = mock(MirrorRepository.class);
        ExerciseAssociationService associationService = mock(ExerciseAssociationService.class);
        ExercisePoint exercise = new ExercisePoint(
                1L, "exercise", Instant.now(), Instant.now(), "fingerprint", "{}", "route.tcx",
                ExercisePoint.RouteState.IMPORTED, 2L, 20L);
        when(repository.findExercisesAssociatedWithTracks(Set.of(20L))).thenReturn(List.of(exercise));
        TrackReplacementListener listener = new TrackReplacementListener(repository, associationService);
        GpxTrackReplacementEvent event = new GpxTrackReplacementEvent(Map.of(10L, 20L));

        listener.replaceAssociations(event);

        verify(repository).replaceExerciseTrackAssociations(Map.of(10L, 20L));
        verify(repository).findExercisesAssociatedWithTracks(Set.of(20L));
        verify(associationService).applyExerciseType(exercise);
    }
}
