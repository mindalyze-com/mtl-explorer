package com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ActivityTypeAutoClassifierTest {


    @Test
    void testTextBasedMatching() {


        assertGuess("nothing", null);
        assertGuess("", null);
        assertGuess(null, null);

        assertGuess("hiking", GpsTrack.ACTIVITY_TYPE.HIKING);
        assertGuess("Hiking in the woods", GpsTrack.ACTIVITY_TYPE.HIKING);
        assertGuess("Was hiking in the woods", GpsTrack.ACTIVITY_TYPE.HIKING);

        assertGuess("MTB", GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING);
        assertGuess("MTB:", GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING);
        assertGuess("MTB: alone", GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING);
        assertGuess("MTB : alone", GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING);
        assertGuess("Riding my MTB:", GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING);
        assertGuess("Riding my MTB around the lake", GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING);

        assertGuess("SUP: Alone on lake zurich", GpsTrack.ACTIVITY_TYPE.STAND_UP_PADDLE);
        assertGuess("SUP : Alone on lake zurich", GpsTrack.ACTIVITY_TYPE.STAND_UP_PADDLE);
        assertGuess("SUP Alone on lake zurich", GpsTrack.ACTIVITY_TYPE.STAND_UP_PADDLE);
        assertGuess("Going with my SUP", GpsTrack.ACTIVITY_TYPE.STAND_UP_PADDLE);

        // Special case: Should not recognize SUP
        assertGuess("Was a super trip. ", null); // contains the word "SUP" which could wrongly get SUP (Standup Paddle)
        assertGuess("SUPER trip ", null); // contains the word "SUP" which could wrongly get SUP (Standup Paddle)
        assertGuess("this was a SUPER hiking trip ", GpsTrack.ACTIVITY_TYPE.HIKING); // contains the word "SUP" which could wrongly get SUP (Standup Paddle)
        assertGuess("hiking was just super", GpsTrack.ACTIVITY_TYPE.HIKING); // contains the word "SUP" which could wrongly get SUP (Standup Paddle)

    }

    private static void assertGuess(String text, GpsTrack.ACTIVITY_TYPE expected) {
        GpsTrack.ACTIVITY_TYPE guessed = ActivityTypeAutoClassifier.guessBasedOnText(null, text, new StringBuilder(), "source1");
        Assertions.assertEquals(expected, guessed);
        System.out.printf("Did guess type '%s' based on text '%s'%n", expected, text);
    }

    @Test
    void classifyActivityUsesGuardedUpdateAndDoesNotSaveFullTrack() {
        GpsTrackRepository gpsTrackRepository = mock(GpsTrackRepository.class);
        ActivityTypeAutoClassifier classifier = new ActivityTypeAutoClassifier(gpsTrackRepository);
        GpsTrack track = new GpsTrack();
        track.setId(42L);
        track.setTrackName("MTB: local loop");
        track.setDuplicateStatus(GpsTrack.DUPLICATE_CHECK_STATUS.DUPLICATE);
        track.setDuplicateOf(100L);
        when(gpsTrackRepository.findById(42L)).thenReturn(Optional.of(track));
        when(gpsTrackRepository.updateActivityClassificationIfPending(
                eq(42L),
                eq(GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING),
                eq(GpsTrack.ACTIVITY_TYPE_SOURCE.AUTO_GUESS),
                contains("trackName"))).thenReturn(1);

        ActivityTypeAutoClassifier.ClassificationResult result =
                classifier.classifyActivity(42L, List.of(new GpsTrackDataPoint()));

        assertTrue(result.updated());
        assertEquals(GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING, result.determinedType());
        assertEquals(GpsTrack.DUPLICATE_CHECK_STATUS.DUPLICATE, track.getDuplicateStatus());
        assertEquals(100L, track.getDuplicateOf());
        verify(gpsTrackRepository, never()).save(any(GpsTrack.class));
        verify(gpsTrackRepository).updateActivityClassificationIfPending(
                eq(42L),
                eq(GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING),
                eq(GpsTrack.ACTIVITY_TYPE_SOURCE.AUTO_GUESS),
                contains("trackName"));
    }

    @Test
    void classifyActivitySkipsTrackThatWasAlreadyClassifiedByAnotherWorker() {
        GpsTrackRepository gpsTrackRepository = mock(GpsTrackRepository.class);
        ActivityTypeAutoClassifier classifier = new ActivityTypeAutoClassifier(gpsTrackRepository);
        GpsTrack track = new GpsTrack();
        track.setId(43L);
        track.setActivityType(GpsTrack.ACTIVITY_TYPE.WALKING);
        track.setActivityTypeSource(GpsTrack.ACTIVITY_TYPE_SOURCE.AUTO_GUESS);
        when(gpsTrackRepository.findById(43L)).thenReturn(Optional.of(track));

        ActivityTypeAutoClassifier.ClassificationResult result =
                classifier.classifyActivity(43L, List.of(new GpsTrackDataPoint()));

        assertFalse(result.updated());
        assertEquals(GpsTrack.ACTIVITY_TYPE.WALKING, result.determinedType());
        verify(gpsTrackRepository, never()).updateActivityClassificationIfPending(any(), any(), any(), any());
        verify(gpsTrackRepository, never()).save(any(GpsTrack.class));
    }
}
