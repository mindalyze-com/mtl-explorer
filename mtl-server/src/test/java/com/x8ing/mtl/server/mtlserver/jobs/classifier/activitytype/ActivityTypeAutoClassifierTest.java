package com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
}