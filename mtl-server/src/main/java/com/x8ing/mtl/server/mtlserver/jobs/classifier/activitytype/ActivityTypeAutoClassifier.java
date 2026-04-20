package com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
public class ActivityTypeAutoClassifier {

    private final GpsTrackRepository gpsTrackRepository;

    public ActivityTypeAutoClassifier(GpsTrackRepository gpsTrackRepository) {
        this.gpsTrackRepository = gpsTrackRepository;
    }

    /**
     * Classify the activity type for the given track and commit the result in its own
     * transaction. Returns the determined activity type (may be null if classification
     * failed), so the caller can decide whether to trigger a follow-up energy recalc
     * AFTER this transaction has committed and the new type is visible to subsequent reads.
     */
    @SneakyThrows
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GpsTrack.ACTIVITY_TYPE classifyActivity(GpsTrack gpsTrack, List<GpsTrackDataPoint> gpsTrackDataPointList) {

        if (gpsTrack == null) {
            String msg = "GPS Track was null. That's invalid. Can't store";
            log.error(msg);
            throw new IllegalArgumentException(msg);
        }

        if (CollectionUtils.isEmpty(gpsTrackDataPointList)) {
            String msg = ("GPS track had no data points.  gpsTrack=%s, gpsTrackDataPointList=%s".formatted(gpsTrack, gpsTrackDataPointList));
            log.warn(msg);
            gpsTrack.setActivityTypeSource(GpsTrack.ACTIVITY_TYPE_SOURCE.FAILED);
            gpsTrack.setActivityTypeSourceDetails(msg);
        } else {
            guessActivity(gpsTrack, gpsTrackDataPointList);
        }
        gpsTrackRepository.save(gpsTrack);
        return gpsTrack.getActivityType();
    }

    private static void guessActivity(GpsTrack gpsTrack, List<GpsTrackDataPoint> gpsTrackDataPointList) {


        GpsTrack.ACTIVITY_TYPE activityType = null;
        StringBuilder typeSourceDetails = new StringBuilder();
        typeSourceDetails.append("Start Auto Guessing Activity Type. \n");

        // check if we get something out of the filename
        activityType = guessBasedOnText(activityType, gpsTrack.getTrackName(), typeSourceDetails, "trackName");
        activityType = guessBasedOnText(activityType, gpsTrack.getTrackDescription(), typeSourceDetails, "trackDescription");
        activityType = guessBasedOnText(activityType, gpsTrack.getIndexedFile().getName(), typeSourceDetails, "fileName");
        activityType = guessBasedOnText(activityType, gpsTrack.getTrackType(), typeSourceDetails, "trackType"); // not reliable for me especially for older tracks

        // try enum mapping
        //noinspection ConstantValue
        if (activityType == null && gpsTrack.getTrackType() != null) {
            try {
                activityType = GpsTrack.ACTIVITY_TYPE.valueOf(StringUtils.trim(StringUtils.upperCase(gpsTrack.getTrackType())));
                typeSourceDetails.append("Found a matching enum through the activity type info. \n");
            } catch (Exception e) {
                // ignore
            }
        }

        if (activityType == null) {

            typeSourceDetails.append("Activity type not found in name or text fields. Try using speed. \n");

            double[] speed = gpsTrackDataPointList.stream()
                    .filter(p -> p.getSpeedInKmhMovingWindow() != null && p.getSpeedInKmhMovingWindow() > 0)
                    .mapToDouble(GpsTrackDataPoint::getSpeedInKmhMovingWindow)
                    .toArray();

            if (speed != null) {
                Percentile percentile = new Percentile();
                double speed66p = percentile.evaluate(speed, 66.0);
                double speed95p = percentile.evaluate(speed, 95);

                typeSourceDetails.append(String.format("Speed percentiles: 66%%-percentile: %.1f km/h, 95%%-percentile: %.1f km/h. \n", speed66p, speed95p));

                //noinspection ConstantValue
                if (activityType == null && (speed66p > 200 || speed95p > 1236)) {
                    activityType = GpsTrack.ACTIVITY_TYPE.SUPER_SONIC;
                    typeSourceDetails.append("Guessed based on speed to be super fast like super-sonic. This can indicate an data issue, e.g. GPS signal weak or old trackers. \n");
                }
                if (activityType == null && (speed66p > 150 || speed95p > 200)) {
                    activityType = GpsTrack.ACTIVITY_TYPE.AIRPLANE;
                    typeSourceDetails.append("Guessed based on speed to be type airplane. \n");
                }
                if (activityType == null && (speed66p > 50 || speed95p > 80)) {
                    activityType = GpsTrack.ACTIVITY_TYPE.CAR;
                    typeSourceDetails.append("Guessed based on speed to be type car. \n");
                }

                if (activityType == null && (speed66p < 9 || speed95p < 15)) {
                    activityType = GpsTrack.ACTIVITY_TYPE.WALKING;
                    typeSourceDetails.append("Guessed based on speed to be type walking. \n");
                }

                if (activityType == null) {
                    activityType = GpsTrack.ACTIVITY_TYPE.BICYCLE;
                    typeSourceDetails.append("Guessed based on speed to be type bicycle. \n");
                }

            } else {
                typeSourceDetails.append("Did not find speed info. Fallback to default type! \n");
                activityType = GpsTrack.ACTIVITY_TYPE.WALKING;
            }

        }

        gpsTrack.setActivityType(activityType);
        gpsTrack.setActivityTypeSourceDetails(typeSourceDetails.toString());
        gpsTrack.setActivityTypeSource(GpsTrack.ACTIVITY_TYPE_SOURCE.AUTO_GUESS);

    }

    static GpsTrack.ACTIVITY_TYPE guessBasedOnText(GpsTrack.ACTIVITY_TYPE currentType, String text, StringBuilder typeSourceDetails, String source) {

        // if we already know one, then return
        if (currentType != null) {
            return currentType;
        }

        GpsTrack.ACTIVITY_TYPE found = null;

        if (text != null) {
            text = StringUtils.lowerCase(text);
            text = StringUtils.trim(text);

            //noinspection ConstantValue
            if (found == null && text.matches(".*walking.*") || text.matches(".*spazieren.*")) {
                found = GpsTrack.ACTIVITY_TYPE.WALKING;
            }
            if (found == null && text.matches(".*langlauf.*") || text.matches(".*skiing.*")) {
                found = GpsTrack.ACTIVITY_TYPE.SKIING;
            }
            if (found == null && text.matches(".*hiking.*") || text.matches(".*wandern.*")) {
                found = GpsTrack.ACTIVITY_TYPE.HIKING;
            }
            if (found == null && text.matches(".*running.*") || text.matches(".*rennen.*") || text.matches(".*joggen.*")) {
                found = GpsTrack.ACTIVITY_TYPE.RUNNING;
            }
            if (found == null && text.matches(".*mountain biking.*") || text.matches(".*mountain_biking.*") || text.matches(".*\\s*mtb([^a-z]|$).*")) {
                found = GpsTrack.ACTIVITY_TYPE.MOUNTAIN_BIKING;
            }
            if (found == null && text.matches(".*cycling") || text.matches(".*cycle") || text.matches("velo") || text.matches("fahrrad")) {
                found = GpsTrack.ACTIVITY_TYPE.BICYCLE;
            }
            if (found == null && text.matches("car([^a-z]|$).*") || text.matches("\\s*auto([^a-z]|$).*") || text.matches(".*\\s*motor([^a-z]|$).*") || text.matches(".*\\s*driving([^a-z]|$).*")) {
                found = GpsTrack.ACTIVITY_TYPE.CAR;
            }
            // SUP is part of too many other words, hence only if a text starts with it
            if (found == null && text.matches(".*\\s*sup([^a-z]|$).*") || text.matches(".*stand up paddle.*")) {
                found = GpsTrack.ACTIVITY_TYPE.STAND_UP_PADDLE;
            }
        }
        if (found != null) {
            typeSourceDetails.append("Did find the activity based on fuzzy text fragments found in '").append(source).append("'. \n");
        }

        return found;
    }

}
